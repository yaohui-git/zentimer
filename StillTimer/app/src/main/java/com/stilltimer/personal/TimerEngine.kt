package com.stilltimer.personal

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 计时状态机（进程内单例）。
 * 运行时用 elapsedRealtime / wall clock 锚点，切后台、锁屏、Activity 被回收后自动补算；
 * 前台服务 TimerService 在后台持续驱动 tick()，所以铃声不依赖界面是否活着。
 * 支持原版的无限时长模式（durationMin = -1），此时圆环每分钟转一圈、时间正向累计。
 * 不含统计、记录、账号、内容库、联网逻辑。
 *
 * v2.3 时序修订：
 * - 开始铃改在「准备倒计时归零、正式计时开始」的那一刻响；准备阶段只放背景音。
 * - 准备阶段可以暂停 / 继续，按钮不再点了没反应。
 * - 准备阶段圆环按准备进度走，不再一直显示满环。
 * - 冷启动时若准备时间已在后台走完，按预算的结束时刻续算正式计时，不再把人踢回 IDLE。
 */
object TimerEngine {

    enum class Phase { IDLE, PREP, RUNNING, PAUSED, DONE }

    data class Ui(
        val phase: Phase = Phase.IDLE,
        val remainingMs: Long = 10 * 60_000L,
        val totalMs: Long = 10 * 60_000L,
        val prepLeftMs: Long = 0L,
        val durationMin: Int = 10,
        val intervalMin: Int = 0,
        val prepSec: Int = 10,
        val startBell: String = "b_basu",
        val intervalBell: String = "b_wood",
        val endBell: String = "b_gong",
        val ambience: String = "none",
        val startTimes: Int = 1,
        val intervalTimes: Int = 1,
        val endTimes: Int = 1,
        val catchUp: Boolean = false
    ) {
        val running: Boolean get() = phase == Phase.PREP || phase == Phase.RUNNING || phase == Phase.PAUSED
        val infinite: Boolean get() = durationMin < 0
        val progress: Float
            get() {
                val prepTotal = prepSec * 1000L
                // 准备阶段（含准备中的暂停）用准备进度画环；v2.2 之前这里恒为满环，看不出准备在走
                val inPrep = prepTotal > 0L &&
                    (phase == Phase.PREP || (phase == Phase.PAUSED && prepLeftMs > 0L))
                return when {
                    inPrep -> ((prepTotal - prepLeftMs).toFloat() / prepTotal.toFloat()).coerceIn(0f, 1f)
                    infinite -> ((remainingMs % 60_000L).toFloat() / 60_000f).coerceIn(0f, 1f)
                    totalMs <= 0L -> 0f
                    else -> (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                }
            }
    }

    private lateinit var app: Context
    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui

    private var endAt = 0L              // elapsedRealtime
    private var prepEndAt = 0L
    private var nextIntervalAt = 0L
    private var startedWall = 0L        // 无限模式的 wall-clock 起点
    private var pausedLeftMs = 0L
    private var pausedFrom = Phase.RUNNING
    /** start() 时预算的「正式计时结束时刻」（wall clock）；无限模式为 0，仅供冷启动补算用。 */
    private var plannedEndWall = 0L

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        Lang.init(app)
        restore()
    }

    // ---------- 读 / 写 ----------

    private fun readSettings(): Ui {
        val p = app.prefs()
        val d = p.getInt("duration_min", 10)
        val total = if (d < 0) 0L else d * 60_000L
        return Ui(
            durationMin = d,
            totalMs = total,
            remainingMs = total,
            intervalMin = p.getInt("interval_min", 0),
            prepSec = p.getInt("prep_sec", 10),
            startBell = p.getString("start_bell", "b_basu") ?: "b_basu",
            intervalBell = p.getString("interval_bell", "b_wood") ?: "b_wood",
            endBell = p.getString("end_bell", "b_gong") ?: "b_gong",
            ambience = p.getString("ambience", "none") ?: "none",
            startTimes = p.getInt("start_times", 1).coerceIn(1, 3),
            intervalTimes = p.getInt("interval_times", 1).coerceIn(1, 3),
            endTimes = p.getInt("end_times", 1).coerceIn(1, 3)
        )
    }

    private fun persist() {
        val s = _ui.value
        val nowEl = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        app.prefs().edit()
            .putString("phase", s.phase.name)
            .putLong("total_ms", s.totalMs)
            .putInt("duration_min", s.durationMin)
            .putInt("interval_min", s.intervalMin)
            .putInt("prep_sec", s.prepSec)
            .putString("start_bell", s.startBell)
            .putString("interval_bell", s.intervalBell)
            .putString("end_bell", s.endBell)
            .putString("ambience", s.ambience)
            .putInt("start_times", s.startTimes)
            .putInt("interval_times", s.intervalTimes)
            .putInt("end_times", s.endTimes)
            .putLong("paused_left", pausedLeftMs)
            .putString("paused_from", pausedFrom.name)
            .putLong("start_wall", if (s.infinite) startedWall else 0L)
            .putLong("end_wall", if (s.phase == Phase.RUNNING && !s.infinite) nowWall + (endAt - nowEl) else 0L)
            .putLong("prep_wall", if (s.phase == Phase.PREP) nowWall + (prepEndAt - nowEl) else 0L)
            .putLong("planned_end_wall", plannedEndWall)
            .apply()
    }

    private fun restore() {
        val p = app.prefs()
        val base = readSettings()
        val nowEl = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        plannedEndWall = p.getLong("planned_end_wall", 0L)
        when (p.getString("phase", "IDLE")) {
            "RUNNING" -> {
                if (base.infinite) {
                    startedWall = p.getLong("start_wall", nowWall)
                    endAt = Long.MAX_VALUE / 4
                    nextIntervalAt = nextIntervalFrom(nowEl, base.intervalMin)
                    _ui.value = base.copy(
                        phase = Phase.RUNNING,
                        remainingMs = (nowWall - startedWall).coerceAtLeast(0L)
                    )
                    startAmbience(base.ambience)
                    TimerService.start(app)
                } else {
                    val left = p.getLong("end_wall", 0L) - nowWall
                    if (left <= 0L) {
                        _ui.value = base.copy(phase = Phase.DONE, remainingMs = 0L, catchUp = true)
                        BellPlayer.play(app, SoundCatalog.bell(base.endBell).res, 1f, base.endTimes)
                        clearRunState()
                    } else {
                        endAt = nowEl + left
                        nextIntervalAt = nextIntervalFrom(nowEl, base.intervalMin)
                        _ui.value = base.copy(phase = Phase.RUNNING, remainingMs = left)
                        startAmbience(base.ambience)
                        TimerService.start(app)
                    }
                }
            }
            "PREP" -> {
                val prepWall = p.getLong("prep_wall", 0L)
                val left = prepWall - nowWall
                if (left > 0L) {
                    prepEndAt = nowEl + left
                    _ui.value = base.copy(phase = Phase.PREP, prepLeftMs = left, remainingMs = base.totalMs)
                    TimerService.start(app)
                } else if (base.infinite) {
                    // 准备已在后台走完：从准备结束那一刻续算无限计时，不补响开始铃打扰
                    startedWall = if (prepWall > 0L) prepWall else nowWall
                    endAt = Long.MAX_VALUE / 4
                    nextIntervalAt = nextIntervalFrom(nowEl, base.intervalMin)
                    _ui.value = base.copy(
                        phase = Phase.RUNNING,
                        remainingMs = (nowWall - startedWall).coerceAtLeast(0L)
                    )
                    startAmbience(base.ambience)
                    TimerService.start(app)
                } else {
                    val runLeft = if (plannedEndWall > 0L) plannedEndWall - nowWall else base.totalMs
                    if (plannedEndWall > 0L && runLeft <= 0L) {
                        _ui.value = base.copy(phase = Phase.DONE, remainingMs = 0L, catchUp = true)
                        BellPlayer.play(app, SoundCatalog.bell(base.endBell).res, 1f, base.endTimes)
                        clearRunState()
                    } else {
                        // v2.3：以前这里直接回 IDLE，等于把一次正在进行的练习悄悄丢掉
                        val safeLeft = runLeft.coerceAtLeast(0L)
                        endAt = nowEl + safeLeft
                        nextIntervalAt = nextIntervalFrom(nowEl, base.intervalMin)
                        _ui.value = base.copy(phase = Phase.RUNNING, remainingMs = safeLeft)
                        startAmbience(base.ambience)
                        TimerService.start(app)
                    }
                }
            }
            "PAUSED" -> {
                pausedLeftMs = p.getLong("paused_left", base.totalMs)
                pausedFrom = runCatching { Phase.valueOf(p.getString("paused_from", "RUNNING") ?: "RUNNING") }
                    .getOrDefault(Phase.RUNNING)
                startedWall = p.getLong("start_wall", nowWall)
                _ui.value = base.copy(
                    phase = Phase.PAUSED,
                    remainingMs = pausedLeftMs,
                    prepLeftMs = if (pausedFrom == Phase.PREP) pausedLeftMs else 0L
                )
                TimerService.start(app)
            }
            else -> _ui.value = base
        }
    }

    private fun clearRunState() {
        app.prefs().edit().putString("phase", "IDLE").apply()
    }

    private fun nextIntervalFrom(nowEl: Long, intervalMin: Int): Long =
        if (intervalMin > 0) nowEl + intervalMin * 60_000L else 0L

    private fun startAmbience(id: String) {
        val res = SoundCatalog.ambience(id).res
        if (res != 0) AmbiencePlayer.start(app, res, 0.5f)
    }

    private fun fireIntervals(nowEl: Long, intervalMin: Int, bellId: String, times: Int) {
        if (intervalMin <= 0 || nextIntervalAt <= 0L || nextIntervalAt > nowEl) return
        BellPlayer.play(app, SoundCatalog.bell(bellId).res, 0.7f, times)
        var next = nextIntervalAt + intervalMin * 60_000L
        while (next <= nowEl) next += intervalMin * 60_000L
        nextIntervalAt = next
    }

    // ---------- 对外动作 ----------

    fun setDuration(min: Int) {
        if (_ui.value.running) return
        val total = if (min < 0) 0L else min * 60_000L
        _ui.value = _ui.value.copy(
            durationMin = min, totalMs = total, remainingMs = total,
            phase = Phase.IDLE, catchUp = false
        )
        persist()
    }

    fun setInterval(min: Int) {
        if (_ui.value.running) return
        _ui.value = _ui.value.copy(intervalMin = min)
        persist()
    }

    fun setPrep(sec: Int) {
        if (_ui.value.running) return
        _ui.value = _ui.value.copy(prepSec = sec)
        persist()
    }

    fun setSound(key: String, id: String) {
        val s = _ui.value
        _ui.value = when (key) {
            "start" -> s.copy(startBell = id)
            "interval" -> s.copy(intervalBell = id)
            "end" -> s.copy(endBell = id)
            "ambience" -> s.copy(ambience = id)
            else -> s
        }
        persist()
    }

    /** 轻触铃声行右侧 ×N，在 1 / 2 / 3 次间循环。上限 3 是刻意克制，不做数字键盘。 */
    fun cycleTimes(key: String) {
        val s = _ui.value
        val cur = when (key) {
            "start" -> s.startTimes
            "interval" -> s.intervalTimes
            "end" -> s.endTimes
            else -> return
        }
        val next = if (cur >= 3) 1 else cur + 1
        _ui.value = when (key) {
            "start" -> s.copy(startTimes = next)
            "interval" -> s.copy(intervalTimes = next)
            else -> s.copy(endTimes = next)
        }
        persist()
    }

    /** 把当前整套设置拍成一个预设快照。 */
    fun snapshot(name: String): Preset {
        val s = _ui.value
        return Preset(
            id = System.currentTimeMillis().toString(),
            name = name,
            durationMin = s.durationMin,
            intervalMin = s.intervalMin,
            prepSec = s.prepSec,
            startBell = s.startBell,
            intervalBell = s.intervalBell,
            endBell = s.endBell,
            ambience = s.ambience,
            startTimes = s.startTimes,
            intervalTimes = s.intervalTimes,
            endTimes = s.endTimes
        )
    }

    /** 应用预设；练习进行中拒绝切换，避免圆环和铃声配置中途打架。 */
    fun applyPreset(p: Preset): Boolean {
        if (_ui.value.running) return false
        val total = if (p.durationMin < 0) 0L else p.durationMin * 60_000L
        _ui.value = _ui.value.copy(
            phase = Phase.IDLE, catchUp = false,
            durationMin = p.durationMin, totalMs = total, remainingMs = total,
            intervalMin = p.intervalMin, prepSec = p.prepSec,
            startBell = p.startBell, intervalBell = p.intervalBell,
            endBell = p.endBell, ambience = p.ambience,
            startTimes = p.startTimes.coerceIn(1, 3),
            intervalTimes = p.intervalTimes.coerceIn(1, 3),
            endTimes = p.endTimes.coerceIn(1, 3)
        )
        persist()
        return true
    }

    fun mainAction() {
        when (_ui.value.phase) {
            Phase.IDLE, Phase.DONE -> start()
            // v2.3：PREP 之前错误地落到 resume()，而 resume 只认 PAUSED → 点「暂停」毫无反应
            Phase.RUNNING, Phase.PREP -> pause()
            Phase.PAUSED -> resume()
        }
    }

    fun start() {
        val s = _ui.value
        val now = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val total = if (s.infinite) 0L else s.durationMin * 60_000L
        val prepMs = if (s.prepSec > 0) s.prepSec * 1000L else 0L
        plannedEndWall = if (s.infinite) 0L else nowWall + prepMs + total
        // 准备阶段只放背景音；开始铃移到 beginRunning()，准备结束那一刻才响
        startAmbience(s.ambience)
        TimerService.start(app)
        if (prepMs > 0L) {
            prepEndAt = now + prepMs
            _ui.value = s.copy(
                phase = Phase.PREP, totalMs = total, remainingMs = total,
                prepLeftMs = prepMs, catchUp = false
            )
            persist()
        } else {
            beginRunning()
        }
    }

    private fun beginRunning() {
        val s = _ui.value
        val now = SystemClock.elapsedRealtime()
        BellPlayer.play(app, SoundCatalog.bell(s.startBell).res, 0.7f, s.startTimes)
        nextIntervalAt = nextIntervalFrom(now, s.intervalMin)
        if (s.infinite) {
            startedWall = System.currentTimeMillis()
            endAt = Long.MAX_VALUE / 4
            _ui.value = s.copy(
                phase = Phase.RUNNING, totalMs = 0L,
                remainingMs = 0L, prepLeftMs = 0L, catchUp = false
            )
        } else {
            endAt = now + s.totalMs
            _ui.value = s.copy(
                phase = Phase.RUNNING, remainingMs = s.totalMs,
                prepLeftMs = 0L, catchUp = false
            )
        }
        persist()
    }

    fun pause() {
        val s = _ui.value
        if (s.phase == Phase.RUNNING) {
            pausedLeftMs = if (s.infinite)
                (System.currentTimeMillis() - startedWall).coerceAtLeast(0L)
            else (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            pausedFrom = Phase.RUNNING
        } else if (s.phase == Phase.PREP) {
            pausedLeftMs = (prepEndAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            pausedFrom = Phase.PREP
        } else return
        AmbiencePlayer.stop()
        _ui.value = s.copy(
            phase = Phase.PAUSED, remainingMs = pausedLeftMs,
            prepLeftMs = if (pausedFrom == Phase.PREP) pausedLeftMs else 0L
        )
        persist()
    }

    fun resume() {
        val s = _ui.value
        if (s.phase != Phase.PAUSED) return
        if (pausedLeftMs <= 0L && !s.infinite) {
            // v2.3：以前这里调 start()，会重新走一遍准备并再响一次开始铃；直接收尾才对
            finish()
            return
        }
        val now = SystemClock.elapsedRealtime()
        startAmbience(s.ambience)
        if (pausedFrom == Phase.PREP) {
            prepEndAt = now + pausedLeftMs
            _ui.value = s.copy(phase = Phase.PREP, prepLeftMs = pausedLeftMs, remainingMs = s.totalMs)
        } else if (s.infinite) {
            startedWall = System.currentTimeMillis() - pausedLeftMs
            endAt = Long.MAX_VALUE / 4
            nextIntervalAt = nextIntervalFrom(now, s.intervalMin)
            _ui.value = s.copy(phase = Phase.RUNNING, remainingMs = pausedLeftMs, prepLeftMs = 0L)
        } else {
            endAt = now + pausedLeftMs
            nextIntervalAt = nextIntervalFrom(now, s.intervalMin)
            _ui.value = s.copy(phase = Phase.RUNNING, remainingMs = pausedLeftMs, prepLeftMs = 0L)
        }
        persist()
    }

    fun stop() {
        // 主动结束时掐掉可能还在连响的铃，避免「结束了还在响」
        BellPlayer.release()
        AmbiencePlayer.stop()
        TimerService.stop(app)
        val s = _ui.value
        val total = if (s.infinite) 0L else s.durationMin * 60_000L
        _ui.value = s.copy(
            phase = Phase.IDLE, remainingMs = total,
            totalMs = total, prepLeftMs = 0L, catchUp = false
        )
        clearRunState()
        persist()
    }

    private fun finish() {
        AmbiencePlayer.stop()
        val s = _ui.value
        BellPlayer.play(app, SoundCatalog.bell(s.endBell).res, 1f, s.endTimes)
        _ui.value = s.copy(phase = Phase.DONE, remainingMs = 0L, prepLeftMs = 0L)
        clearRunState()
        persist()
        // 服务下一拍检测到 !running 会自行收尾，不在此强杀，避免打断结束铃
    }

    /** UI 与前台服务都会调用；状态机幂等，重复 tick 不会重复响铃。 */
    fun tick() {
        val s = _ui.value
        val now = SystemClock.elapsedRealtime()
        when (s.phase) {
            Phase.PREP -> {
                val left = prepEndAt - now
                if (left <= 0L) beginRunning()
                else _ui.value = s.copy(prepLeftMs = left, remainingMs = s.totalMs)
            }
            Phase.RUNNING -> {
                if (s.infinite) {
                    fireIntervals(now, s.intervalMin, s.intervalBell, s.intervalTimes)
                    _ui.value = s.copy(
                        remainingMs = (System.currentTimeMillis() - startedWall).coerceAtLeast(0L)
                    )
                    return
                }
                val left = (endAt - now).coerceAtLeast(0L)
                if (left <= 0L) {
                    finish()
                    return
                }
                fireIntervals(now, s.intervalMin, s.intervalBell, s.intervalTimes)
                _ui.value = s.copy(remainingMs = left)
            }
            else -> Unit
        }
    }

    fun formatTime(ms: Long): String {
        val totalSec = (ms + 999L) / 1000L
        val m = totalSec / 60L
        val sec = totalSec % 60L
        return "%d:%02d".format(m, sec)
    }

    fun releaseAudio() {
        BellPlayer.release()
        AmbiencePlayer.stop()
    }
}
