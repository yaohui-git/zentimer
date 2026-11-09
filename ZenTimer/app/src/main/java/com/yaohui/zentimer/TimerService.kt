package com.yaohui.zentimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log

/**
 * 前台服务：驱动计时锚点 + 发布一条常驻通知。
 *
 * 职责边界（11-09 整体重构）：
 * - 计时、结算、响铃全在 TimerEngine，服务只负责按 250ms 心跳调 TimerEngine.tick()；
 * - 界面不再自己驱动状态机（MainActivity 的周期 tick 已删），避免「一个状态三个写入者」；
 * - 通知组装全部交给 NotificationContract，服务只提供运行时能力探测结果并发布。
 * 通知能力缺失、胶囊没上去、私有参数被忽略，都不影响到点响铃。
 *
 * 计时期间持有 PARTIAL_WAKE_LOCK，避免息屏后 CPU 深睡导致到点不响。
 */
class TimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var alive = false
    private var wakeLock: PowerManager.WakeLock? = null
    /** 上一次发通知的指纹：文案没变就不重发，避免无谓的通知风暴。 */
    private var lastNoteKey = ""
    /**
     * 亮屏才把 shortCriticalText 写成实时数字（顶栏胶囊）。
     * 息屏时去掉该字段，AOD 胶囊才会回落到 param_v2.aodTitle「静坐中」，
     * 而不是显示倒计时数字 —— 这台 ROM 两个胶囊共用同一个 chip 文本字段。
     */
    @Volatile
    private var screenOn = true
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val on = intent.action == Intent.ACTION_SCREEN_ON
            if (screenOn == on) return
            screenOn = on
            lastNoteKey = ""       // 下一拍（250ms）立刻用新形态重发
            Log.i(TAG, "screen on=$on")
        }
    }

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
            // 只有暂停时文案里才带一个定格的时间，那就必须重发。
            // 08:18 用户裁决：顶栏倒计时必须实时。v2.14 对照证实这台 HyperOS 的胶囊
            // 不认系统 chronometer，只认 shortCriticalText，所以文本得每秒更新。
            // 仅在有焦点能力的机器上每秒重发；其它 ROM 仍由系统 chronometer 驱动 chip，
            // 保持「一秒都不重发」，不把本机的适配成本摊到所有机型。
            val focusChip = screenOn && FocusCapability.available() &&
                (s.phase == TimerEngine.Phase.RUNNING || s.phase == TimerEngine.Phase.PREP) &&
                !s.infinite
            val bucket = if (focusChip) (left / 1_000L).toString() else ""
            val key = s.phase.name + "|" + Lang.current.code + "|" + bucket +
                if (s.phase == TimerEngine.Phase.PAUSED) "|" + TimerEngine.formatTime(left) else ""
            if (key != lastNoteKey) {
                lastNoteKey = key
                push(s)
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun push(s: TimerEngine.Ui) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.notify(NOTI_ID, NotificationContract.build(this, s, currentCaps(nm), screenOn)) }
    }

    /**
     * 只收集「系统给什么」，不猜机型、不维护白名单。
     * hasPromotableCharacteristics 的真值由 NotificationContract 对当前通知产物现场自检后填入，
     * 这里传 false 只是占位（H7 / H8：伪造厂商名在这里连入口都没有）。
     */
    private fun currentCaps(nm: NotificationManager): StyleSelector.Caps = StyleSelector.Caps(
        sdkInt = Build.VERSION.SDK_INT,
        canPostPromoted = canPromote(nm),
        hasPromotableCharacteristics = false,
        focusAvailable = FocusCapability.available(),
        notificationEnabled = runCatching { nm.areNotificationsEnabled() }.getOrDefault(true),
        promoteFailedBefore = false
    )

    private fun canPromote(nm: NotificationManager): Boolean = runCatching {
        Build.VERSION.SDK_INT >= 36 && nm.canPostPromotedNotifications()
    }.getOrDefault(false)

    override fun onCreate() {
        super.onCreate()
        TimerEngine.init(this)
        createChannel()
        // 焦点能力自检：非小米 ROM 上 provider 不存在会抛异常，probe 内部吞掉并记 false。
        FocusCapability.probe(this)
        runCatching {
            registerReceiver(
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                }
            )
        }
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
        val nm = getSystemService(NotificationManager::class.java)
        if (nm != null) {
            startForeground(NOTI_ID, NotificationContract.build(this, TimerEngine.ui.value, currentCaps(nm), screenOn))
        }
        lastNoteKey = ""
        acquireWakeLock()
        if (!alive) {
            alive = true
            Log.i(TAG, "start sdk=${Build.VERSION.SDK_INT} focus=${FocusCapability.available()}")
            handler.removeCallbacks(tickTask)
            handler.post(tickTask)
        }
        return START_STICKY
    }

    /** 计时期间持有 PARTIAL_WAKE_LOCK，6 小时是防泄漏上限。 */
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
        runCatching { unregisterReceiver(screenReceiver) }
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

    companion object {
        private const val TAG = "ZenTimer"
        /** 旧的低优先级渠道，仅用于升级时清理内测版残留。 */
        private const val CHANNEL = "timer_player"
        /** 计时通知渠道；设置页要用它读渠道是否被用户关掉。 */
        const val CHANNEL_ALARM = "timer_alarm"
        private const val NOTI_ID = 1001
        private const val TICK_MS = 250L
        const val ACTION_STOP = "com.yaohui.zentimer.STOP"
        const val ACTION_PAUSE = "com.yaohui.zentimer.PAUSE"
        const val ACTION_RESUME = "com.yaohui.zentimer.RESUME"

        fun start(ctx: Context) {
            val i = Intent(ctx, TimerService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (_: Throwable) {
                // 极少数 ROM 在后台限制启动前台服务；此时不另起一套 UI 心跳，
                // 因为那会把「到点」重新变成两个判定入口。下次打开 App 由 restore() 静默补算。
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, TimerService::class.java))
        }
    }
}
