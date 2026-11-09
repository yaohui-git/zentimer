package com.yaohui.zentimer

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

private fun attrs(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    .build()

/**
 * 一次性铃声：开始 / 间隔 / 结束 / 试听。
 * 支持连响 N 次（1~3）；每两声之间留气口，让余音自然落下再起下一声，听感不像是机械连击。
 *
 * 间隔口径（用户明确）：**上一声完全响完之后，再静置 [TAIL_GAP_MS] = 1.8 秒**，才起下一声。
 * 所以实际两声「起点」之间的距离 = 这一声自身的长度 + 1.8 秒；短铃（0.4 秒木鱼）约 2.2 秒，
 * 长铃（颂钵 / 铜锣带长余音）就更长。不设「起点对起点」的截断，否则余音还在响就叠上下一声。
 *
 * generation 令牌保证「换铃 / 结束 / 释放」时旧的连响回调立即失效，不会串音。
 */
object BellPlayer {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var generation = 0L
    private var lastStartAt = 0L        // 上一声起响时刻（uptimeMillis）
    /** 进程内的第二道保险：同一轮练习的结束铃只播一次。真正的凭据在磁盘（TimerEngine）。 */
    private var lastOnceToken: String? = null

    /** 上一声彻底播完之后，再静置多久才起下一声。 */
    private const val TAIL_GAP_MS = 1800L

    /** 带一次性令牌的播放；token 为空或重复时直接忽略。 */
    fun playOnce(token: String, context: Context, resId: Int, volume: Float, times: Int = 1) {
        if (token.isEmpty() || token == lastOnceToken) return
        lastOnceToken = token
        play(context, resId, volume, times)
    }

    fun play(context: Context, resId: Int, volume: Float, times: Int = 1) {
        if (resId == 0) return
        release()
        val count = times.coerceIn(1, 5)
        val token = ++generation
        val mp = MediaPlayer.create(context.applicationContext, resId) ?: return
        player = mp
        mp.setAudioAttributes(attrs())
        mp.setVolume(volume, volume)
        var left = count - 1
        mp.setOnCompletionListener { p ->
            if (token != generation) return@setOnCompletionListener
            if (left > 0) {
                left--
                val next = Runnable {
                    if (token == generation && player === p) {
                        try {
                            p.seekTo(0)
                            p.start()
                            lastStartAt = SystemClock.uptimeMillis()
                        } catch (_: Exception) {
                            release()
                        }
                    }
                }
                pending = next
                // 从「上一声真正结束」起算 1.8 秒。
                // 正常情况 elapsed ≈ 音频总长；个别 ROM 会提前回调「播完」，
                // 这时按声明时长把差额补上，下一声仍然落在真实结束之后 1.8 秒。
                val elapsed = SystemClock.uptimeMillis() - lastStartAt
                val declared = p.duration.toLong().coerceAtLeast(0L)
                val gap = if (declared > elapsed) declared - elapsed + TAIL_GAP_MS else TAIL_GAP_MS
                handler.postDelayed(next, gap)
            } else {
                release()
            }
        }
        try {
            mp.start()
            lastStartAt = SystemClock.uptimeMillis()
        } catch (_: Exception) {
            release()
        }
    }

    fun release() {
        generation++
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        player?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) {
            }
        }
        player = null
    }
}

/** 背景音无缝循环：开始自动播放，暂停 / 结束自动停止。 */
object AmbiencePlayer {
    private var player: MediaPlayer? = null

    fun start(context: Context, resId: Int, volume: Float) {
        stop()
        if (resId == 0) return
        player = MediaPlayer.create(context.applicationContext, resId)?.apply {
            setAudioAttributes(attrs())
            isLooping = true
            setVolume(volume, volume)
            start()
        }
    }

    fun stop() {
        player?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) {
            }
        }
        player = null
    }
}
