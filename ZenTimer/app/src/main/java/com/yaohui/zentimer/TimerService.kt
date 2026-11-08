package com.yaohui.zentimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * 前台服务驱动计时锚点 + 一条常驻通知：锁屏、切后台、Activity 被回收时铃声仍然准点。
 * 计时期间持有 PARTIAL_WAKE_LOCK，避免息屏后 CPU 进入休眠导致到点不响、亮屏才补响。
 *
 * 通知按**系统闹钟**的做法组织（不是媒体播放器那套 MediaSession 卡片）：
 * 1. 走独立的「闹钟」通知渠道（IMPORTANCE_HIGH、静音、锁屏可见），并声明为
 *    Android 的 **alarm clock** 类型通知——这是系统闹钟在锁屏、状态栏、息屏界面上
 *    被优先展示的同一机制；剩余时间用系统计时器（chronometer）自己走秒，
 *    不解锁就能看到倒数，不用本进程每秒重画。
 * 2. 通知本体可点，直接回到计时页；右侧一个「结束」按钮，锁屏上也能收工。
 * 3. Android 16+ 再额外申请状态栏「实时动态」胶囊（系统闹钟同款）。
 *
 * 若机型不允许把前台服务通知声明成闹钟（少数 ROM 会抛异常），自动退回普通高优先级通知，
 * 功能不受影响——所以每一步都带兜底。
 */
class TimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var alive = false
    private var wakeLock: PowerManager.WakeLock? = null
    /** 上一次发通知的指纹：文案没变就不重发，避免 4 次/秒的通知风暴。 */
    private var lastNoteKey = ""
    /** 本机是否吃「闹钟置顶」这一套；第一次被拒后置 false，避免每秒重试。 */
    private var decorateOk = true

    private val tickTask = object : Runnable {
        override fun run() {
            if (!alive) return
            TimerEngine.tick()
            val s = TimerEngine.ui.value
            if (!s.running) {
                stopSelf()
                return
            }
            val left = if (s.phase == TimerEngine.Phase.PREP) s.prepLeftMs else s.remainingMs
            val key = s.phase.name + "|" + TimerEngine.formatTime(left) + "|" + Lang.current.code
            if (key != lastNoteKey) {
                lastNoteKey = key
                push(s)
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    /**
     * 先按「闹钟通知」发；ROM 若不接受（抛异常）就退回普通高优先级通知，
     * 并把 [alarmOk] 关掉，后续不再试——否则每秒都会抛一次。
     */
    private fun push(s: TimerEngine.Ui) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val ok = runCatching { nm.notify(NOTI_ID, buildNotification(s, decorateIt = true)) }.isSuccess
        if (!ok) {
            decorateOk = false
            runCatching { nm.notify(NOTI_ID, buildNotification(s, decorateIt = false)) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        TimerEngine.init(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // 来自通知 / 锁屏的「结束」：连状态一起收干净。
            // 只 stopSelf 会把 phase 留在 RUNNING，下次打开会被 restore() 当成「还在练」续上。
            TimerEngine.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        // 启前台服务先用不装饰的版本：闹钟置顶属性留给随后的 notify 补，
        // 个别 ROM 会拒绝 startForeground 带这类属性。
        startForeground(NOTI_ID, buildNotification(TimerEngine.ui.value, decorateIt = false))
        lastNoteKey = ""
        acquireWakeLock()
        if (!alive) {
            alive = true
            handler.removeCallbacks(tickTask)
            handler.post(tickTask)
        }
        return START_STICKY
    }

    /**
     * 计时期间持有 PARTIAL_WAKE_LOCK：息屏后 CPU 不进入深睡，到点那一刻立刻响铃，
     * 不会出现「息屏不响、亮屏才补一声」。6 小时是防泄漏上限，正常结束或停止即释放。
     */
    private fun acquireWakeLock() {
        if (wakeLock != null) return
        try {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zentimer:timer").apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L)
            }
        } catch (_: Throwable) {
            // 个别 ROM 拒绝唤醒锁时静默降级，仍靠前台服务 tick
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Throwable) {
        }
        wakeLock = null
    }

    override fun onDestroy() {
        alive = false
        handler.removeCallbacks(tickTask)
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            // 旧渠道（LOW）只出现在内测包里，留着会在系统设置里多出一条无意义的条目
            runCatching { nm.deleteNotificationChannel(CHANNEL) }
            val ch = NotificationChannel(
                CHANNEL_ALARM,
                Lang.t("app_title"),
                NotificationManager.IMPORTANCE_HIGH   // 与系统闹钟同级：锁屏置顶、可展开
            )
            ch.setSound(null, null)                   // 到点铃声由 BellPlayer 放，这里保持静音
            ch.enableVibration(false)
            ch.setShowBadge(false)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(s: TimerEngine.Ui, decorateIt: Boolean): Notification {
        val left = if (s.phase == TimerEngine.Phase.PREP) s.prepLeftMs else s.remainingMs
        val body = Lang.t("notif_body") + TimerEngine.formatTime(left)
        // 只有「准备中 / 冥想中」才让系统走秒；暂停时把时间定格成静态文字
        val ticking = s.phase == TimerEngine.Phase.PREP || s.phase == TimerEngine.Phase.RUNNING
        val nowWall = System.currentTimeMillis()

        val b = NotificationCompat.Builder(this, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(Lang.t("app_title"))
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openIntent())
            .addAction(0, Lang.t("btn_stop"), stopIntent())
            .setUsesChronometer(ticking)
            .setChronometerCountDown(ticking && !s.infinite)
        if (ticking) {
            // 有限时长：倒数到「现在 + 剩余」；无限时长：正向累计，不倒数
            b.setWhen(if (s.infinite) nowWall - left else nowWall + left)
        } else {
            b.setWhen(0L)
        }

        val plain = b.build()
        // 暂停态 / 无限计时不需要「闹钟置顶」；本机已确认不支持时也不再折腾
        val want = ticking && !s.infinite
        if (!decorateIt || !want || !decorateOk) return plain
        return decorate(plain, left)
    }

    /**
     * 按系统闹钟的方式把这条通知「顶上去」，锁屏不解锁也能看到倒计时：
     * - Android 16+：官方实时动态（promoted ongoing）+ 状态栏胶囊，正是新版系统时钟的计时器用法；
     * - Android 12~15：老版本系统闹钟靠的是 alarm clock 类型通知，锁屏与状态栏会优先展示，
     *   但 compileSdk 37 的公开 SDK 已删掉 setAlarmClock / AlarmClockInfo，只能按名字反射。
     * 任一步失败就原样返回普通通知，并记下本机不吃这一套，之后不再重试。
     */
    private fun decorate(n: Notification, left: Long): Notification {
        return try {
            // 不能用 Builder.rebuild()——那是 @hide，公开 SDK 里只有 build()。
            val pb = Notification.Builder.recoverBuilder(this, n)
            if (Build.VERSION.SDK_INT >= 36) {
                pb.setRequestPromotedOngoing(true)
                pb.setShortCriticalText(TimerEngine.formatTime(left))
            } else {
                attachAlarmClock(pb, left)
            }
            pb.build()
        } catch (_: Throwable) {
            decorateOk = false
            n
        }
    }

    /** 反射补一条 `setAlarmClock`：用「现在 + 剩余」作为闹钟时刻，点开即回计时页。 */
    private fun attachAlarmClock(pb: Notification.Builder, left: Long) {
        val show = openIntent()
        val infoCls = Class.forName("android.app.Notification\$AlarmClockInfo")
        val info = infoCls
            .getConstructor(java.lang.Long.TYPE, PendingIntent::class.java)
            .newInstance(System.currentTimeMillis() + left, show)
        pb.javaClass
            .getMethod("setAlarmClock", infoCls, PendingIntent::class.java)
            .invoke(pb, info, show)
    }

    private fun pendingFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

    /** 点通知 / 状态栏胶囊：回到计时页。singleTop 已声明，不会叠出第二个 Activity。 */
    private fun openIntent(): PendingIntent {
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(this, REQ_OPEN, i, pendingFlags())
    }

    private fun stopIntent(): PendingIntent {
        val i = Intent(this, TimerService::class.java).setAction(ACTION_STOP)
        return PendingIntent.getService(this, REQ_STOP, i, pendingFlags())
    }

    companion object {
        /** 旧的低优先级渠道，仅用于升级时清理内测版残留。 */
        private const val CHANNEL = "timer_player"
        private const val CHANNEL_ALARM = "timer_alarm"
        private const val NOTI_ID = 1001
        private const val TICK_MS = 250L
        private const val REQ_OPEN = 1
        private const val REQ_STOP = 2
        const val ACTION_STOP = "com.yaohui.zentimer.STOP"

        fun start(ctx: Context) {
            val i = Intent(ctx, TimerService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (_: Throwable) {
                // 极少数 ROM 在后台限制启动前台服务，退化为 Activity 内心跳
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, TimerService::class.java))
        }
    }
}
