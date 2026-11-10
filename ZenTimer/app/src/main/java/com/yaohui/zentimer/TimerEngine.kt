package com.yaohui.zentimer

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 计时状态机（进程内单例）。
 * 运行时用 elapsedRealtime / wall clock 锚点，切后台、锁屏、Activity 被回收后自动补算；
 * 前台服务 TimerService 在后台持续驱动 tick()，所以铃声不依赖界面是否活着。
 * v2.23 起不再有无限时长（旧 -1 由 sanitizeDuration 归一为出厂默认）。
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

    /**
     * 出厂默认（11-10 17:4x 用户拍板「默认值都改回来」）：静坐 15 分钟 /
     * 准备 3 分钟 / 开始铃木鱼 ×3 / 结束铃颂钵 Kangse / 间隔铃无。
     * 单一事实源：Ui 默认值、readSettings 回落、Preset.fromJson 回落都读这里，
     * 不许再各写一份数字，否则「新装是 5 分钟、旧预设读出来是 10 分钟」这类偏差查不出来。
     */
    object Defaults {
        const val DURATION_MIN = 15
        /** v2.24 用户拍板「小时最多 23，不提供 24 小时及以上」：23:59 = 1439 分。 */
        const val DURATION_MAX_MIN = 1439
        /** 准备时间滚轮是「分 0..10 / 秒 0..59」，真实可达 10:59 = 659 秒。 */
        const val PREP_MAX_SEC = 659
        const val PREP_SEC = 180          // 3 分钟
        const val START_BELL = "b_wood"   // 木鱼
        const val INTERVAL_BELL = "none"  // 无
        const val END_BELL = "b_kangse"   // 颂钵 · Kangse
        const val START_TIMES = 3
        const val AMBIENCE = "none"
        const val INTERVAL_MIN = 0
        const val BELL_TIMES = 1
    }

    data class Ui(
        val phase: Phase = Phase.IDLE,
        val remainingMs: Long = Defaults.DURATION_MIN * 60_000L,
        val totalMs: Long = Defaults.DURATION_MIN * 60_000L,
        val prepLeftMs: Long = 0L,
        val durationMin: Int = Defaults.DURATION_MIN,
        val intervalMin: Int = Defaults.INTERVAL_MIN,
        val prepSec: Int = Defaults.PREP_SEC,
        val startBell: String = Defaults.START_BELL,
        val intervalBell: String = Defaults.INTERVAL_BELL,
        val endBell: String = Defaults.END_BELL,
        val ambience: String = Defaults.AMBIENCE,
        val startTimes: Int = Defaults.START_TIMES,
        val intervalTimes: Int = Defaults.BELL_TIMES,
        val endTimes: Int = Defaults.BELL_TIMES,
        val catchUp: Boolean = false
    ) {
        val running: Boolean get() = phase == Phase.PREP || phase == Phase.RUNNING || phase == Phase.PAUSED
        val progress: Float
            get() {
                val prepTotal = prepSec * 1000L
                // 准备阶段（含准备中的暂停）用准备进度画环；v2.2 之前这里恒为满环，看不出准备在走
                val inPrep = prepTotal > 0L &&
                    (phase == Phase.PREP || (phase == Phase.PAUSED && prepLeftMs > 0L))
                return when {
                    inPrep -> ((prepTotal - prepLeftMs).toFloat() / prepTotal.toFloat()).coerceIn(0f, 1f)
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
    private var pausedLeftMs = 0L
    private var pausedFrom = Phase.RUNNING
    /** start() 时预算的「正式计时结束时刻」（wall clock），仅供冷启动补算用。 */
    private var plannedEndWall = 0L

    /** 一次练习的唯一编号，start() 时生成并同步落盘。 */
    private var runId = ""
    /** 已经结算过（响过或静默结算过）结束铃的 runId；与 runId 相等即视为本轮已了结。 */
    private var endBellFiredFor = ""

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        Lang.init(app)
        migrateDefaults()   // 必须在 restore() 之前：restore 会按设置值还原一轮练习
        restore()
    }

    // ---------- 读 / 写 ----------

    /**
     * v2.23：用户拍板「连无限计时一起删」。历史设置里残留的 -1（旧无时限）在这里
     * 一律归一为出厂默认，绝不让它流进圆环算成负数或 0 秒。
     * 读盘、应用预设、setter 三处都走这里，口径只定义一次。
     */
    private fun sanitizeDuration(min: Int): Int =
        if (min < 1) Defaults.DURATION_MIN
        else min.coerceIn(1, Defaults.DURATION_MAX_MIN)

    private const val KEY_DEFAULTS_VER = "defaults_ver"
    private const val DEFAULTS_VER = 2

    /**
     * 已装用户的一次性默认值迁移（v2.17 → v2.18 → v2.23）。
     * 只把「仍然等于旧默认值」的项升级为新默认值，用户手动改过的一律不动；
     * 正在计时时不迁移也不写版本号，等下一次冷启动再试，避免打断一轮练习。
     * v2.23：出厂静坐时长 5 → 15，仍停在旧默认 5 的用户也升级；37 分这类
     * 用户自己设过的值不受影响。
     */
    private fun migrateDefaults() {
        val p = app.prefs()
        if (p.getInt(KEY_DEFAULTS_VER, 0) >= DEFAULTS_VER) return
        val phase = p.getString("phase", "IDLE")
        if (phase in setOf("PREP", "RUNNING", "PAUSED")) return
        val e = p.edit()
        // v2.17 及以前的出厂值：10 分钟 / 准备 10 秒 / Basu / 木鱼 / 大锣 / 响 1 次
        if (p.getInt("duration_min", -1) == 10) e.putInt("duration_min", Defaults.DURATION_MIN)
        if (p.getInt("duration_min", -1) == 5) e.putInt("duration_min", Defaults.DURATION_MIN)
        if (p.getInt("prep_sec", -1) == 10) e.putInt("prep_sec", Defaults.PREP_SEC)
        if (p.getString("start_bell", null) == "b_basu") e.putString("start_bell", Defaults.START_BELL)
        if (p.getString("interval_bell", null) == "b_wood") e.putString("interval_bell", Defaults.INTERVAL_BELL)
        if (p.getString("end_bell", null) == "b_gong") e.putString("end_bell", Defaults.END_BELL)
        if (p.getInt("start_times", -1) == 1) e.putInt("start_times", Defaults.START_TIMES)
        e.putInt(KEY_DEFAULTS_VER, DEFAULTS_VER).apply()
    }

    private fun readSettings(): Ui {
        val p = app.prefs()
        val rawMin = p.getInt("duration_min", Defaults.DURATION_MIN)
        val d = sanitizeDuration(rawMin)
        if (d != rawMin) p.edit().putInt("duration_min", d).apply()
        val total = if (d < 0) 0L else d * 60_000L
        return Ui(
            durationMin = d,
            totalMs = total,
            remainingMs = total,
            intervalMin = p.getInt("interval_min", Defaults.INTERVAL_MIN),
            prepSec = p.getInt("prep_sec", Defaults.PREP_SEC),
            startBell = p.getString("start_bell", Defaults.START_BELL) ?: Defaults.START_BELL,
            intervalBell = p.getString("interval_bell", Defaults.INTERVAL_BELL) ?: Defaults.INTERVAL_BELL,
            endBell = p.getString("end_bell", Defaults.END_BELL) ?: Defaults.END_BELL,
            ambience = p.getString("ambience", Defaults.AMBIENCE) ?: Defaults.AMBIENCE,
            startTimes = p.getInt("start_times", Defaults.START_TIMES).coerceIn(1, 3),
            intervalTimes = p.getInt("interval_times", Defaults.BELL_TIMES).coerceIn(1, 3),
            endTimes = p.getInt("end_times", Defaults.BELL_TIMES).coerceIn(1, 3)
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
            .putLong("start_wall", 0L)
            .putLong("end_wall", if (s.phase == Phase.RUNNING) nowWall + (endAt - nowEl) else 0L)
            .putLong("prep_wall", if (s.phase == Phase.PREP) nowWall + (prepEndAt - nowEl) else 0L)
            .putLong("planned_end_wall", plannedEndWall)
            .putString("run_id", runId)
            .putString("end_bell_fired_for", endBellFiredFor)
            .putBoolean("catch_up", s.catchUp)
            // 同步落盘：进程被强杀时，「这一轮是否已经了结」的凭据不能丢。
            // 这里只在状态变化时调用（不是每 250ms 的 tick），不会卡主线程。
            .commit()
    }

    private fun restore() {
        val p = app.prefs()
        val base = readSettings()
        val nowEl = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        plannedEndWall = p.getLong("planned_end_wall", 0L)
        // 老版本没有 run_id：用两个已存在的墙钟锚点拼一个稳定编号，
        // 这样从旧包升级过来的这一轮，在多次冷启动里仍是同一轮，凭据不会失效。
        runId = p.getString("run_id", null)?.takeIf { it.isNotEmpty() }
            ?: "legacy-${p.getLong("end_wall", 0L)}-${p.getLong("planned_end_wall", 0L)}"
        endBellFiredFor = p.getString("end_bell_fired_for", "") ?: ""
        when (p.getString("phase", "IDLE")) {
            "RUNNING" -> {
                val left = p.getLong("end_wall", 0L) - nowWall
                if (left <= 0L) {
                    // 冷启动补算：只显示「已完成」，绝不补响。
                    // C 组真机坐实的那声「嘣」就是这个分支原来自己播的。
                    endAt = nowEl + left
                    _ui.value = base.copy(phase = Phase.RUNNING, remainingMs = 0L)
                    settleIfDue(nowEl, allowBell = false)
                } else {
                    endAt = nowEl + left
                    nextIntervalAt = nextIntervalFrom(nowEl, base.intervalMin)
                    _ui.value = base.copy(phase = Phase.RUNNING, remainingMs = left)
                    startAmbience(base.ambience)
                    TimerService.start(app)
                }
            }
            "PREP" -> {
                val prepWall = p.getLong("prep_wall", 0L)
                val left = prepWall - nowWall
                if (left > 0L) {
                    prepEndAt = nowEl + left
                    _ui.value = base.copy(phase = Phase.PREP, prepLeftMs = left, remainingMs = base.totalMs)
                    TimerService.start(app)
                } else {
                    val runLeft = if (plannedEndWall > 0L) plannedEndWall - nowWall else base.totalMs
                    if (plannedEndWall > 0L && runLeft <= 0L) {
                        // 准备阶段被杀、正式计时也在后台过完：同样只结算、不补响。
                        endAt = nowEl + runLeft
                        _ui.value = base.copy(phase = Phase.RUNNING, remainingMs = 0L)
                        settleIfDue(nowEl, allowBell = false)
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
                _ui.value = base.copy(
                    phase = Phase.PAUSED,
                    remainingMs = pausedLeftMs,
                    prepLeftMs = if (pausedFrom == Phase.PREP) pausedLeftMs else 0L
                )
                TimerService.start(app)
            }
            "DONE" -> {
                // 结算后磁盘留的是 DONE。重开 App 仍显示完成态：
                // 不响铃、不起服务、不把这一轮悄悄变回「准备好了，随时开始」。
                _ui.value = base.copy(
                    phase = Phase.DONE, remainingMs = 0L,
                    catchUp = p.getBoolean("catch_up", false)
                )
            }
            else -> _ui.value = base
        }
    }

    // ---------- 唯一结算入口 ----------

    private fun newRunId(): String = "r-" + java.util.UUID.randomUUID().toString()

    /**
     * 全 App 唯一能产生「完成态 + 结束铃」的地方。
     * 前台服务心跳、冷启动补算、暂停后恢复都只调它；判定规则在 SettlementPolicy。
     *
     * [allowBell] 只有前台服务心跳传 true。冷启动补算传 false：
     * 进程被杀时宁可这一轮漏响，也不要在用户回到主界面时突然「嘣」一声。
     *
     * 顺序是硬要求：**先同步写凭据，再发声**。进程在两行之间被杀，
     * 结果是「这一轮没响」而不是「下次回来再响一次」。
     */
    private fun settleIfDue(now: Long, allowBell: Boolean): Boolean {
        val s = _ui.value
        val outcome = SettlementPolicy.decide(
            phase = s.phase.name,
            endAt = endAt,
            now = now,
            runId = runId,
            firedFor = endBellFiredFor,
            allowBell = allowBell
        )
        when (outcome) {
            SettlementPolicy.Outcome.NONE -> return false
            SettlementPolicy.Outcome.ALREADY_SETTLED -> {
                // 凭据命中：只把界面摆成完成态，一个音符都不发。
                if (s.phase != Phase.DONE) {
                    _ui.value = s.copy(phase = Phase.DONE, remainingMs = 0L, prepLeftMs = 0L)
                    persist()
                }
                // 冷启动补算发现上一轮其实已了结：规则可能还留在系统里，顺手收掉。
                DndGuard.onSittingEnd(app)
                return true
            }
            SettlementPolicy.Outcome.SETTLE_WITH_BELL,
            SettlementPolicy.Outcome.SETTLE_SILENT -> {
                endBellFiredFor = runId
                app.prefs().edit()
                    .putString("run_id", runId)
                    .putString("end_bell_fired_for", endBellFiredFor)
                    .commit()
                AmbiencePlayer.stop()
                _ui.value = s.copy(
                    phase = Phase.DONE, remainingMs = 0L, prepLeftMs = 0L,
                    catchUp = outcome == SettlementPolicy.Outcome.SETTLE_SILENT
                )
                persist()
                if (outcome == SettlementPolicy.Outcome.SETTLE_WITH_BELL) {
                    BellPlayer.playOnce(
                        runId, app,
                        SoundCatalog.bell(s.endBell).res, 1f, s.endTimes
                    )
                }
                // v2.25：顺序是有意的 —— 先让结束铃在规则仍生效时起响（方案 D1 不许自伤），
                // 铃声已交给 MediaPlayer 后才停用规则，MediaPlayer 不受后续状态变化影响。
                // 若在响铃前就停用，等于绕过了「免打扰下结束铃照常响」这条验收。
                DndGuard.onSittingEnd(app)
                // 服务下一拍检测到 !running 会自行收尾，不在此强杀，避免打断结束铃。
                return true
            }
        }
    }

    /** 主动「结束」：把本轮凭据一并抹掉，让下一轮从干净状态开始。 */
    private fun clearRunState() {
        runId = ""
        endBellFiredFor = ""
        app.prefs().edit()
            .putString("phase", "IDLE")
            .putString("run_id", "")
            .putString("end_bell_fired_for", "")
            .putBoolean("catch_up", false)
            .commit()
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
        val v = sanitizeDuration(min)
        val total = v * 60_000L
        _ui.value = _ui.value.copy(
            durationMin = v, totalMs = total, remainingMs = total,
            phase = Phase.IDLE, catchUp = false
        )
        persist()
    }

    fun setInterval(min: Int) {
        if (_ui.value.running) return
        _ui.value = _ui.value.copy(intervalMin = min.coerceIn(0, 120))
        persist()
    }

    fun setPrep(sec: Int) {
        if (_ui.value.running) return
        _ui.value = _ui.value.copy(prepSec = sec.coerceIn(0, Defaults.PREP_MAX_SEC))
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
        // 老预设里可能带越界值，统一钳到 1..DURATION_MAX_MIN；任意值本身合法，不再按档位归一。
        val min = sanitizeDuration(p.durationMin)
        val total = min * 60_000L
        _ui.value = _ui.value.copy(
            phase = Phase.IDLE, catchUp = false,
            durationMin = min, totalMs = total, remainingMs = total,
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
        // 新一轮：发唯一编号、清掉「已结算」凭据，并在起跑之前同步写盘。
        // 这一步先于任何铃声，冷启动才认得出「还是同一轮」。
        runId = newRunId()
        endBellFiredFor = ""
        app.prefs().edit()
            .putString("run_id", runId)
            .putString("end_bell_fired_for", "")
            .putBoolean("catch_up", false)
            .commit()
        val s0 = _ui.value
        val total = s0.durationMin * 60_000L
        // 完成态（DONE）的 totalMs / remainingMs 属于「上一次练习」，圆环还停在 0:00。
        // 在这里一次性把时长写回，让 plannedEndWall、beginRunning() 的 endAt、
        // 圆环进度三者只认同一个 total；点「再来一次」也不会残留 0:00 的那一拍。
        // 时长永远取当前选中的 durationMin，不会把上一次的 0 当成新的目标。
        val s = s0.copy(totalMs = total, remainingMs = total, catchUp = false)
        _ui.value = s
        val now = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val prepMs = if (s.prepSec > 0) s.prepSec * 1000L else 0L
        plannedEndWall = nowWall + prepMs + total
        // 准备阶段只放背景音；开始铃移到 beginRunning()，准备结束那一刻才响
        startAmbience(s.ambience)
        TimerService.start(app)
        // v2.25：起跑即激活自有勿扰规则，并把含准备时间的预计结束时刻交给兜底闹钟。
        // 全程 runCatching，无权限 / OEM 拦截 / 规则创建失败都只记日志，静坐照常走。
        DndGuard.onSittingStart(app, plannedEndWall)
        if (prepMs > 0L) {
            prepEndAt = now + prepMs
            _ui.value = s.copy(phase = Phase.PREP, prepLeftMs = prepMs)
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
        endAt = now + s.totalMs
        _ui.value = s.copy(
            phase = Phase.RUNNING, remainingMs = s.totalMs,
            prepLeftMs = 0L, catchUp = false
        )
        persist()
    }

    fun pause() {
        val s = _ui.value
        if (s.phase == Phase.RUNNING) {
            pausedLeftMs = (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
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
        if (pausedLeftMs <= 0L) {
            // v2.3：以前这里调 start()，会重新走一遍准备并再响一次开始铃；直接收尾才对。
            // 现在收尾同样只走唯一结算入口，不再另开一个 finish()。
            val due = SystemClock.elapsedRealtime()
            endAt = due
            _ui.value = s.copy(phase = Phase.RUNNING, remainingMs = 0L)
            settleIfDue(due, allowBell = true)
            return
        }
        val now = SystemClock.elapsedRealtime()
        startAmbience(s.ambience)
        if (pausedFrom == Phase.PREP) {
            prepEndAt = now + pausedLeftMs
            _ui.value = s.copy(phase = Phase.PREP, prepLeftMs = pausedLeftMs, remainingMs = s.totalMs)
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
        // v2.25：提前「结束」与正常结算共用同一个停用入口，两条路径都不留残留规则。
        DndGuard.onSittingEnd(app)
        val s = _ui.value
        val total = s.durationMin * 60_000L
        _ui.value = s.copy(
            phase = Phase.IDLE, remainingMs = total,
            totalMs = total, prepLeftMs = 0L, catchUp = false
        )
        clearRunState()
        persist()
    }

    // finish() 已删除：结算权收归 settleIfDue()，全 App 不再有第二个写 DONE / 播结束铃的地方。

    /** 只有前台服务的 250ms 心跳会调用；界面只读 StateFlow，不再驱动状态机。 */
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
                // 到点只由唯一结算入口判定；服务心跳是唯一允许发声的调用方。
                if (settleIfDue(now, allowBell = true)) return
                val left = (endAt - now).coerceAtLeast(0L)
                fireIntervals(now, s.intervalMin, s.intervalBell, s.intervalTimes)
                _ui.value = s.copy(remainingMs = left)
            }
            else -> Unit
        }
    }

    /**
     * v2.24：时长放宽到 23:59 后，原来的「分钟:秒」会把 23 小时显示成 `1439:00`。
     * 圆环、顶栏胶囊、通知正文、通知历史全都走这里，口径只改这一处：
     * 满 1 小时显示 `H:MM:SS`，不满 1 小时仍显示 `M:SS`（15:00 不变）。
     */
    fun formatTime(ms: Long): String {
        val totalSec = (ms + 999L) / 1000L
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val sec = totalSec % 60L
        return if (h > 0L) "%d:%02d:%02d".format(h, m, sec)
        else "%d:%02d".format(m, sec)
    }

    fun releaseAudio() {
        BellPlayer.release()
        AmbiencePlayer.stop()
    }
}
