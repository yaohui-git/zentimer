package com.stilltimer.personal

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

private fun attrs(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    .build()

/**
 * 一次性铃声：开始 / 间隔 / 结束 / 试听。
 * 支持连响 N 次（1~5）；次间留 320ms，让颂钵余音自然落下再起下一声，听感不像是机械连击。
 * generation 令牌保证「换铃 / 结束 / 释放」时旧的连响回调立即失效，不会串音。
 */
object BellPlayer {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var generation = 0L

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
                        } catch (_: Exception) {
                            release()
                        }
                    }
                }
                pending = next
                handler.postDelayed(next, 320L)
            } else {
                release()
            }
        }
        try {
            mp.start()
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
