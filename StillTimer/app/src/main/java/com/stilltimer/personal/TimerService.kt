package com.stilltimer.personal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * 原版 Insight Timer 的 TimerPlayer / SleepMixerForegroundService 是前台服务，
 * 所以锁屏、切后台、Activity 被回收时铃声仍然准点。
 * 这版做同样的事，但只保留一件事：驱动计时锚点 + 一条常驻通知。
 * Activity 在前台时也在 tick，两者都调 TimerEngine.tick()，状态机是幂等的。
 */
class TimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var alive = false

    private val tickTask = object : Runnable {
        override fun run() {
            if (!alive) return
            TimerEngine.tick()
            val s = TimerEngine.ui.value
            if (!s.running) {
                stopSelf()
                return
            }
            try {
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTI_ID, buildNotification(s))
            } catch (_: Throwable) {
                // 用户未授予通知权限时通知不显示，但服务保活与铃声不受影响
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        TimerEngine.init(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTI_ID, buildNotification(TimerEngine.ui.value))
        if (!alive) {
            alive = true
            handler.removeCallbacks(tickTask)
            handler.post(tickTask)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        alive = false
        handler.removeCallbacks(tickTask)
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
            val ch = NotificationChannel(
                CHANNEL,
                "Timer Player",   // 与原版 timer_notification_channel_name 同名
                NotificationManager.IMPORTANCE_LOW
            )
            ch.setSound(null, null)
            ch.enableVibration(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(s: TimerEngine.Ui): Notification {
        val left = if (s.phase == TimerEngine.Phase.PREP) s.prepLeftMs else s.remainingMs
        val body = Lang.t("notif_body") + TimerEngine.formatTime(left)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(Lang.t("app_title"))
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    companion object {
        private const val CHANNEL = "timer_player"
        private const val NOTI_ID = 1001
        private const val TICK_MS = 250L
        const val ACTION_STOP = "com.stilltimer.personal.STOP"

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
