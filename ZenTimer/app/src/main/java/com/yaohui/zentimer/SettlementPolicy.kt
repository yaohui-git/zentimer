package com.yaohui.zentimer

/**
 * 「这一轮练习该不该结算」的唯一判定规则，纯 Kotlin、零 Android 依赖，可离线单测。
 *
 * 病根（2026-11-08 C 组真机坐实）：进程被强杀后，冷启动 restore() 自己判「时间过了」
 * 就补一声结束铃。原来有三处判定、没有「这轮响过没有」的凭据。
 * 现在所有入口（前台服务心跳、冷启动补算、暂停后恢复）都只问这里，
 * 再由 TimerEngine.settleIfDue() 统一执行。
 *
 * [allowBell] 只有前台服务的心跳才传 true；冷启动补算一律 false：
 * 只显示「已完成」，绝不补响。宁可漏响一次，也不重复响（§5.2 原则 3）。
 */
object SettlementPolicy {

    enum class Outcome {
        /** 还没到点，或当前不是正式计时。 */
        NONE,
        /** 这一轮已结算过（磁盘凭据命中），只保证界面是完成态，不发声。 */
        ALREADY_SETTLED,
        /** 到点，正常结算并发声。 */
        SETTLE_WITH_BELL,
        /** 到点，但属于冷启动补算：结算并静默，只显示完成态。 */
        SETTLE_SILENT
    }

    fun decide(
        phase: String,
        endAt: Long,
        now: Long,
        runId: String,
        firedFor: String,
        allowBell: Boolean
    ): Outcome {
        if (phase != "RUNNING") return Outcome.NONE
        if (endAt > now) return Outcome.NONE
        if (runId.isNotEmpty() && firedFor == runId) return Outcome.ALREADY_SETTLED
        return if (allowBell) Outcome.SETTLE_WITH_BELL else Outcome.SETTLE_SILENT
    }
}
