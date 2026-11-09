package com.yaohui.zentimer

/**
 * 通知显示能力的决策表：只回答「这一条通知该用哪一层显示」，不碰计时、不碰铃声。
 *
 * 刻意不 import 任何 android.* —— 这样它可以跑在普通 JVM 单元测试里，
 * 12 条跨厂商输入组合（TC-H）不用真机就能验。
 *
 * 输入里**没有厂商名、没有机型名**：能力只能由运行时探测结果决定。
 * 伪造 Build.MANUFACTURER 在这里连入口都没有（H7 / H8 结构性成立）。
 *
 * 三层（缺任何一层都只是显示降级，不影响内核）：
 * - BASE：Android 标准常驻通知，minSdk 26 全覆盖。
 * - OFFICIAL_CHIP：Android 16 官方「实时动态」状态栏胶囊。
 * - HYPEROS_FOCUS：在完整基础字段之上**额外追加** miui.focus.param 私有参数。
 */
object StyleSelector {

    enum class Layer { BASE, OFFICIAL_CHIP, HYPEROS_FOCUS }

    /**
     * @param sdkInt 系统版本
     * @param canPostPromoted 系统是否允许本 App 发「实时动态」通知
     * @param hasPromotableCharacteristics 对**当前这条通知产物**现场自检的结果（不是延时回读 flag）
     * @param focusAvailable 运行时查询焦点通知能力的结果；非小米 ROM 上探测抛异常时由调用方吞掉并传 false
     * @param notificationEnabled 通知总开关；只影响「是否显示」，绝不影响计时 / 响铃
     * @param promoteFailedBefore 上一次自检发现系统没给提升，改走老式闹钟兜底
     */
    data class Caps(
        val sdkInt: Int,
        val canPostPromoted: Boolean,
        val hasPromotableCharacteristics: Boolean,
        val focusAvailable: Boolean,
        val notificationEnabled: Boolean = true,
        val promoteFailedBefore: Boolean = false
    )

    data class Decision(
        val layer: Layer,
        val requestPromoted: Boolean,
        val useAlarmClockFallback: Boolean,
        val appendFocusExtras: Boolean,
        /**
         * L0 / L1 计时中一律 false：官方规定它的优先级最高，会挤掉 chronometer 的走秒数字。
         * 例外：L2 那台不认 chronometer 的 ROM 由 NotificationContract.applyFocusLayer()
         * 单独按能力写入 chip 文本（08:18 用户要求顶栏实时倒计时）；本纯函数不破例，
         * 保持「决策输入零厂商名」与 160 组不变量不变。
         */
        val setShortCriticalText: Boolean,
        /** 通知发不出去时只标记为「看不见」，内核照常。 */
        val displaySuppressed: Boolean,
        val reason: String
    )

    fun pick(c: Caps): Decision {
        val eligible = c.sdkInt >= 36 &&
            c.canPostPromoted &&
            c.hasPromotableCharacteristics &&
            !c.promoteFailedBefore

        // 私有通道是「追加」，不是「替代」：哪怕官方胶囊没资格，基础字段也照旧齐全。
        val layer = when {
            c.focusAvailable -> Layer.HYPEROS_FOCUS
            eligible -> Layer.OFFICIAL_CHIP
            else -> Layer.BASE
        }

        val reason = buildList {
            add("sdk=${c.sdkInt}")
            if (!c.canPostPromoted) add("no promoted permission")
            if (!c.hasPromotableCharacteristics) add("notification not promotable")
            if (c.promoteFailedBefore) add("promote rejected before")
            if (c.focusAvailable) add("focus probe passed")
            if (!c.notificationEnabled) add("display suppressed (timer unaffected)")
        }.joinToString("; ")

        return Decision(
            layer = layer,
            requestPromoted = eligible,
            useAlarmClockFallback = !eligible,
            appendFocusExtras = c.focusAvailable,
            setShortCriticalText = false,
            displaySuppressed = !c.notificationEnabled,
            reason = reason
        )
    }
}
