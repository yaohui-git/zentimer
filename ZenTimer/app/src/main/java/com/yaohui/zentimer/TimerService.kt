package com.yaohui.zentimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log

/**
 * 前台服务驱动计时锚点 + 一条常驻通知：锁屏、切后台、Activity 被回收时铃声仍然准点。
 * 计时期间持有 PARTIAL_WAKE_LOCK，避免息屏后 CPU 进入休眠导致到点不响、亮屏才补响。
 *
 * 通知形态对齐系统时钟（AOSP DeskClock）的计时器通知，而不是媒体播放器那套 MediaSession：
 * `setOngoing(true)` + `CATEGORY_ALARM` + `VISIBILITY_PUBLIC` + `setUsesChronometer(true)`
 * + `setChronometerCountDown(true)` + `setRequestPromotedOngoing(true)`。
 * 前四项让锁屏按闹钟类通知展示，倒计时由系统自己走秒；最后一项是 Android 16 的「实时动态」，
 * 系统会把这条通知顶到通知栏和锁屏顶部，并在状态栏挂一枚倒计时胶囊——系统时钟的计时器
 * 用的就是同一个接口（需要清单里声明 POST_PROMOTED_NOTIFICATIONS）。
 *
 * 每一步能力都独立判断，不被支持时只降级该项，不影响计时与铃声。
 */
class TimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var alive = false
    private var wakeLock: PowerManager.WakeLock? = null
    /** 上一次发通知的指纹：文案没变就不重发，避免无谓的通知风暴。 */
    private var lastNoteKey = ""
    /** 是否已经确认过「实时动态」的最终态，只需确认一次。 */
    private var promotedChecked = false
    private var promotedOk = false
    /** 装饰能力只记一次日志，避免刷屏。 */
    private var decorLogged = false
    /** 实时动态确认无效后，改用闹钟式通知。 */
    private var forceAlarm = false

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

    private fun push(s: TimerEngine.Ui) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.notify(NOTI_ID, buildNotification(s)) }
        if (!promotedChecked) {
            // 发出去之后再回读一次：系统到底有没有把这条通知提升为「实时动态」，
            // 只认 FLAG_PROMOTED_ONGOING，避免「设了属性但系统没认」这种静默失效。
            promotedChecked = true
            handler.postDelayed({ verifyPromoted(nm) }, 1500L)
        }
    }

    private fun verifyPromoted(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < 36) return
        val n = runCatching {
            nm.activeNotifications.firstOrNull { it.id == NOTI_ID }?.notification
        }.getOrNull() ?: return
        promotedOk = (n.flags and Notification.FLAG_PROMOTED_ONGOING) != 0
        Log.i(TAG, "promoted=$promotedOk canPost=${canPromote()} sdk=${Build.VERSION.SDK_INT}")
        if (!promotedOk && !forceAlarm) {
            // 提升没生效：下一拍改用闹钟式通知兜底，至少让锁屏/状态栏按闹钟展示。
            forceAlarm = true
            lastNoteKey = ""
        }
    }

    private fun canPromote(): Boolean = runCatching {
        Build.VERSION.SDK_INT >= 36 &&
            getSystemService(NotificationManager::class.java)?.canPostPromotedNotifications() == true
    }.getOrDefault(false)

    override fun onCreate() {
        super.onCreate()
        TimerEngine.init(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // 来自通知 / 锁屏的「结束」：连状态一起收干净。
                // 只 stopSelf 会把 phase 留在 RUNNING，下次打开会被 restore() 当成「还在练」续上。
                TimerEngine.stop()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> TimerEngine.pause()
            ACTION_RESUME -> TimerEngine.resume()
        }
        startForeground(NOTI_ID, buildNotification(TimerEngine.ui.value))
        lastNoteKey = ""
        acquireWakeLock()
        if (!alive) {
            alive = true
            Log.i(TAG, "start sdk=${Build.VERSION.SDK_INT} canPost=${canPromote()}")
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
                NotificationManager.IMPORTANCE_HIGH
            )
            ch.setSound(null, null)                   // 到点铃声由 BellPlayer 放，这里保持静音
            ch.enableVibration(false)
            ch.setShowBadge(false)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(s: TimerEngine.Ui): Notification {
        val left = if (s.phase == TimerEngine.Phase.PREP) s.prepLeftMs else s.remainingMs
        // 只有「准备中 / 冥想中」才让系统走秒；暂停时把时间定格成静态文字
        val ticking = s.phase == TimerEngine.Phase.PREP || s.phase == TimerEngine.Phase.RUNNING
        val countdown = ticking && !s.infinite
        val now = System.currentTimeMillis()

        val b = Notification.Builder(this, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(Lang.t("app_title"))
            .setContentText(Lang.t("notif_body") + TimerEngine.formatTime(left))
            .setContentIntent(openIntent())
            .setOngoing(true)                         // 实时动态的必要条件
            .setAutoCancel(false)
            .setLocalOnly(true)
            .setShowWhen(false)                       // when 只作倒计时基准，不显示成时间戳
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_ALARM) // 锁屏按闹钟类通知展示
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setUsesChronometer(ticking)              // 系统自己走秒，不靠进程重画
            .setChronometerCountDown(countdown)
            .setWhen(if (ticking) (if (s.infinite) now - left else now + left) else 0L)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 前台服务通知默认会被系统压 10 秒才露出；倒计时一开始就看不见等于没有。
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        // 与系统闹钟一致：计时中给「暂停」，暂停中给「继续」，右侧常驻「结束」
        if (s.phase == TimerEngine.Phase.PAUSED) {
            b.addAction(R.drawable.ic_stat_timer, Lang.t("btn_resume"), actionIntent(ACTION_RESUME, REQ_RESUME))
        } else if (ticking) {
            b.addAction(R.drawable.ic_stat_timer, Lang.t("btn_pause"), actionIntent(ACTION_PAUSE, REQ_PAUSE))
        }
        b.addAction(R.drawable.ic_stat_timer, Lang.t("btn_stop"), actionIntent(ACTION_STOP, REQ_STOP))

        // 锁屏 / 状态栏「被顶上去」的两条路，按可用性逐级尝试：
        // ① Android 16 实时动态（状态栏胶囊 + 锁屏置顶）；② 老式 alarm clock 通知。
        // 两者都取不到时就是一条普通常驻通知，计时与铃声完全不受影响。
        if (ticking || s.phase == TimerEngine.Phase.PAUSED) {
            val live = if (Build.VERSION.SDK_INT >= 36 && !forceAlarm) applyLiveUpdate(b, left) else false
            val alarm = if (!live && countdown) applyAlarmClock(b, left) else false
            if (!decorLogged) {
                decorLogged = true
                Log.i(TAG, "decorate live=$live alarm=$alarm sdk=${Build.VERSION.SDK_INT}")
            }
        }

        return b.build()
    }

    /**
     * 实时动态（状态栏倒计时胶囊 + 通知栏/锁屏置顶）。
     * API 36 起进公开 SDK，但**部分 Android 16 出厂版本里仍是 @hide**：
     * 实测 Redmi Note 15 Pro（SDK 36）直接调用会
     * `NoSuchMethodError … setRequestPromotedOngoing` 崩掉整个进程。
     * 因此一律按名字反射取，取不到就当没有，绝不能影响计时。
     */
    private fun applyLiveUpdate(b: Notification.Builder, left: Long): Boolean = runCatching {
        // 路线一：公开方法（部分 ROM 有）。取不到就换路线二，不能直接调，否则 NoSuchMethodError 崩进程。
        val byMethod = runCatching {
            b.javaClass
                .getMethod("setRequestPromotedOngoing", java.lang.Boolean.TYPE)
                .invoke(b, true)
            b.javaClass
                .getMethod("setShortCriticalText", String::class.java)
                .invoke(b, TimerEngine.formatTime(left))
            true
        }.getOrDefault(false)
        if (byMethod) return@runCatching true

        // 路线二：官方 extras 通道。两个 key 都是编译期字符串常量，
        // 不需要运行时存在任何方法，只要系统认这两个 key 就能提升并把剩余时间挂到胶囊上。
        val ex = Bundle()
        ex.putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true)
        ex.putString("android.shortCriticalText", TimerEngine.formatTime(left))
        b.setExtras(ex)
        true
    }.getOrDefault(false)

    /**
     * 老式 alarm clock 通知：系统闹钟在 Android 12~15 靠它上锁屏、进状态栏。
     * 实时动态不可用时兜底，同样反射调用（API 36 起该类已从公开 SDK 移除）。
     */
    private fun applyAlarmClock(b: Notification.Builder, left: Long): Boolean = runCatching {
        val infoCls = Class.forName("android.app.Notification\$AlarmClockInfo")
        val show = openIntent()
        val info = infoCls
            .getConstructor(java.lang.Long.TYPE, PendingIntent::class.java)
            .newInstance(System.currentTimeMillis() + left, show)
        b.javaClass
            .getMethod("setAlarmClock", infoCls, PendingIntent::class.java)
            .invoke(b, info, show)
        true
    }.getOrDefault(false)

    private fun pendingFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

    /** 点通知 / 状态栏胶囊：回到计时页。singleTop 已声明，不会叠出第二个 Activity。 */
    private fun openIntent(): PendingIntent {
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(this, REQ_OPEN, i, pendingFlags())
    }

    /** 通知上的「暂停 / 继续 / 结束」都走同一条服务指令通道。 */
    private fun actionIntent(action: String, req: Int): PendingIntent {
        val i = Intent(this, TimerService::class.java).setAction(action)
        return PendingIntent.getService(this, req, i, pendingFlags())
    }

    companion object {
        private const val TAG = "ZenTimer"
        /** 旧的低优先级渠道，仅用于升级时清理内测版残留。 */
        private const val CHANNEL = "timer_player"
        /** 计时通知渠道；设置页要用它读渠道是否被用户关掉。 */
        const val CHANNEL_ALARM = "timer_alarm"
        private const val NOTI_ID = 1001
        private const val TICK_MS = 250L
        private const val REQ_OPEN = 1
        private const val REQ_STOP = 2
        private const val REQ_PAUSE = 3
        private const val REQ_RESUME = 4
        const val ACTION_STOP = "com.yaohui.zentimer.STOP"
        const val ACTION_PAUSE = "com.yaohui.zentimer.PAUSE"
        const val ACTION_RESUME = "com.yaohui.zentimer.RESUME"

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
