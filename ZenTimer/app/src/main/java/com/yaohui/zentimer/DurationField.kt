package com.yaohui.zentimer

import android.graphics.Color as AndroidColor
import android.os.Build
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/**
 * 三个时长设置共用的就地滚轮控件。
 *
 * 基线来源见 `证据核查-时长控件与打赏-2026-11-10.md`：系统时钟「计时」页三列横排、
 * 选中行亮、上下行暗、无选中色块、无分隔线、无加减箭头；Insight Timer 静态
 * 证据 `ListWheelChildManager` / `timerDurationSaveClicked`（编辑与显示分离）。
 *
 * ── v2.22 重新评估（推翻 v2.21 的取舍，旧结论保留作对照）──────────────────
 * v2.21 在这里写过「不用 AndroidView(NumberPicker)：去掉原生分隔线要靠 API 29+ 的
 * setSelectionDividerHeight 或反射，minSdk 26 会引入兼容分叉」。这个判断被用户 11-10
 * 07:54 的真机手感反馈推翻，而且经 SDK 产物核对，事实层面也站不住：
 *   · platforms/android-36/data/api-versions.xml：NumberPicker since 11；
 *     setMinValue / setMaxValue / setValue / setDisplayedValues / setFormatter /
 *     setWrapSelectorWheel / setOnValueChangedListener 均是 API 11~21，无分叉；
 *     只有 setSelectionDividerHeight / setTextColor / setTextSize 是 API 29+。
 *   · 真机 M2003J15SC 是 API 31，三个装饰 API 全部可用；minSdk 26 只需一处
 *     `if (SDK_INT >= 29)` 守卫，低版本退化为系统默认外观，功能完全不分叉。
 *   · 更关键：手写 detectVerticalDragGestures 逐 38dp 累加，没有 VelocityTracker /
 *     fling / snap / nestedScroll 协商，真机实测快甩 120ms 与慢拖 800ms 同样 300px
 *     都只走 2 格（inertia = 1.0）；列间空白处一甩，整页被父滚动拖走 716px。
 *     这是「拿架构洁癖压手感」，不是取舍。
 * 现改为原生 NumberPicker：用它自带的 Scroller / computeScroll / onInterceptTouchEvent
 * 滚动链，惯性、吸附、长按连续滚动都是系统行为。装饰差异用公开 API 收口，不反射。
 *
 * ── v2.23（11-10 下午四项拍板）──────────────────────────────────────────
 * 用户实测又指出三个真问题，逐条改：
 *  ① 三个组件各自 remember 自己的 editing → 能同时展开两组滚轮（v2.21 验收截图里
 *     那张「两组滚轮」被误判成取证残留，其实是真 BUG）。现把编辑目标上提到主页面，
 *     全局同一时刻只有一个 editing=true，天然互斥。
 *  ② 编辑期冻结整页滚动（v2.22 为修手感加的）→ 展开靠下的「准备时间」时，滚轮和
 *     取消/确定被顶出屏幕，页面又滚不动，形成死锁。现恢复整页可滚，并在展开时把
 *     整个编辑区 bringIntoView；滚轮仍有 requestDisallowInterceptTouchEvent 独占手势。
 *  ③ 退出只能靠「取消 / 确定 / 返回键」→ 现补两条：再点该行收回（等同取消），以及
 *     主页面点任意空白即取消（由 MainActivity 统一处理，只认 tap、不拦截拖拽）。
 * 另外「无时限」按用户拍板连整套无限计时一起删除：入口、内部 -1、三语文案、
 * 计时/通知/结算分支全部清零；旧设置里残留的 -1 由 TimerEngine 归一为出厂默认。
 *
 * ── v2.24（11-10 21:27 四项拍板，旧结论一律保留作对照）────────────────────
 * ① **撤回「系统计时器不循环」**：上一轮证据表只记了「单次短滑未触发改值」，根本
 *    没测边界就写进不变量。21:33 真机 `timer_tab.png` 实拍：秒列 `58/59/00/01/02`、
 *    时列 `22/23/00/01/02` → 系统确实循环，且小时列就是 0~23。现改为
 *    `wrapSelectorWheel = true`，甩到头不再有硬边界（用户说的「阻力」主要来自这里）。
 * ② **尺寸对齐系统**：真机控件树三列各 332×901px、5 行可见、铺满 1080px 屏宽；
 *    我们原为 96×120dp ≈ 264×330px、3 行、两列居中只占 583px，两侧各空 250px，
 *    手一落空就变成整页滚动。现把每列改成 `weight(1f)` 铺满可用宽度、高 200dp
 *    （约 5 行），滚轮行内不再留任何空白，整块都归滚轮，列间 Spacer 删除。
 * ③ **小时上限 0~9 → 0~23**，最长 23:59；`valid()` 与 `TimerEngine` 共用
 *    `Defaults.DURATION_MAX_MIN = 1439` 一个数字，不再各写一份 599。
 * ④ **四档快捷值**：静坐 15/30/45/60 分 + 自定义、准备 1/3/5/10 分 + 自定义；
 *    点行或点圆环先出档位，固定值即点即生效，自定义才展开滚轮。当前值不在四档里
 *    时「自定义」高亮并回显当前值；上次自定义值按 mode 分开记。提醒间隔不加档位。
 *
 * 显示态 → 编辑态就地切换，不弹面板、不新开页面；draft 只在「确定」或点固定档时写回。
 */
enum class DurationMode {
    /** 静坐时长：时 / 分，内部单位是分钟，1..Defaults.DURATION_MAX_MIN（23:59）。 */
    HOUR_MINUTE,

    /** 准备时间：分 / 秒，内部单位是秒，0..659（10 分 59 秒，见 valid 注释）。 */
    MINUTE_SECOND,

    /** 提醒间隔：只有分，内部单位是分钟，0..120；0 显示「关」。 */
    MINUTES_ONLY
}

private const val KEY_DURATION_HINT = "duration_hint_seen"

private data class WheelCol(val label: String, val value: Int, val range: IntProgression)

/** 一个固定档位：value 是内部单位（静坐=分、准备=秒），label 是屏幕上那一小行字。 */
private data class PresetChoice(val value: Int, val label: String)

/**
 * v2.24：只给「静坐时长」和「准备时间」两处加快捷档。提醒间隔的常用值（10 分、
 * 关）与这四档不是一套，且主界面已有七行设置，三处全铺会把克制的界面冲散。
 * 准备时间的档位按用户 21:27 追加的原话定为 1 / 3 / 5 / 10 分。
 */
private fun presetsOf(mode: DurationMode): List<PresetChoice> = when (mode) {
    DurationMode.HOUR_MINUTE -> listOf(15, 30, 45, 60).map {
        PresetChoice(it, "$it ${Lang.t("unit_min_short")}")
    }
    DurationMode.MINUTE_SECOND -> listOf(1, 3, 5, 10).map {
        PresetChoice(it * 60, "$it ${Lang.t("unit_min_short")}")
    }
    DurationMode.MINUTES_ONLY -> emptyList()
}

/**
 * 上次自定义值按 mode 分开记，互不覆盖；提醒间隔没有自定义这一层。
 * 键名带版本号：真机验收 11-10 22:xx 发现老包遗留的同名 pref 会让「自定义」
 * 一进去就跳到陈年旧值（1 时 13 分），新功能不该继承来路不明的历史数据。
 */
private fun customKeyOf(mode: DurationMode): String? = when (mode) {
    DurationMode.HOUR_MINUTE -> "v224_custom_duration_min"
    DurationMode.MINUTE_SECOND -> "v224_custom_prep_sec"
    DurationMode.MINUTES_ONLY -> null
}

/**
 * v2.22：所有列一律 step 1。v2.21 的秒列是 0..59 step 5，遇到 217 秒这种非整 5 的值，
 * 滚轮中心显示 37、上下却是 32 / 42，值与栅格对不上；原生滚轮的下标必须是连续整数，
 * 顺手把这个隐患一起收掉。三列全 step 1 也跟系统计时器 00~59 一致，配合 fling 不慢。
 * v2.24：小时列上限由 9 放宽到 23（用户「最多可以 23 小时」）。
 */
private fun colsOf(mode: DurationMode, total: Int): List<WheelCol> = when (mode) {
    DurationMode.HOUR_MINUTE -> listOf(
        WheelCol(Lang.t("unit_hour_short"), total / 60, 0..23),
        WheelCol(Lang.t("unit_min_short"), total % 60, 0..59)
    )
    DurationMode.MINUTE_SECOND -> listOf(
        WheelCol(Lang.t("unit_min_short"), total / 60, 0..10),
        WheelCol(Lang.t("unit_sec_short"), total % 60, 0..59)
    )
    DurationMode.MINUTES_ONLY -> listOf(
        WheelCol(Lang.t("unit_min_short"), total, 0..120)
    )
}

private fun encode(mode: DurationMode, values: List<Int>): Int = when (mode) {
    DurationMode.HOUR_MINUTE, DurationMode.MINUTE_SECOND -> values[0] * 60 + values[1]
    DurationMode.MINUTES_ONLY -> values[0]
}

/**
 * v2.24：静坐上限与 TimerEngine 共用 Defaults.DURATION_MAX_MIN，只定义一次。
 * 准备时间原写 0..600，但滚轮是「分 0..10 / 秒 0..59」，10 分 30 秒本来就能滚到，
 * 却因 600 被判非法而把「确定」置灰——开了循环后更容易撞上，索性把上限对齐到
 * 轮盘真实可达的 659 秒（10:59），不留死路。
 */
private fun valid(mode: DurationMode, total: Int): Boolean = when (mode) {
    DurationMode.HOUR_MINUTE -> total in 1..TimerEngine.Defaults.DURATION_MAX_MIN
    DurationMode.MINUTE_SECOND -> total in 0..659
    DurationMode.MINUTES_ONLY -> total in 0..120
}

private fun format(total: Int): String = if (total < 10) "0$total" else total.toString()

/**
 * 一行时长设置。三处（静坐时长 / 提醒间隔 / 准备时间）共用这一个组件，行为不分叉。
 * `editing` 由主页面统一持有：同一时刻只有一个为 true，点开第二个时第一个自动收回
 * 并且 draft 回滚（等同取消）。`onEditingChange` 只表达「请求进入 / 退出」。
 *
 * v2.22 字号：显示态与铃声 `SettingRow` 完全同构（标签 15sp / C.text，值 12sp /
 * C.accent，12dp 行内距、1px 细线），不再出现 30sp 等宽大数字那种「一行独占焦点」。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DurationSetting(
    label: String,
    value: Int,
    mode: DurationMode,
    enabled: Boolean,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onCommit: (Int) -> Unit,
    onStageChange: (Boolean) -> Unit = {}
) {
    val ctx = LocalContext.current
    var draft by remember { mutableIntStateOf(value) }
    // 失去编辑态就回滚：点开另一行、点空白、返回键，都等同「取消」，不写回。
    LaunchedEffect(value, editing) {
        if (!editing) draft = value
    }
    val showHint by remember {
        mutableStateOf(mode == DurationMode.HOUR_MINUTE &&
            !ctx.prefs().getBoolean(KEY_DURATION_HINT, false))
    }
    var hintVisible by remember(showHint) { mutableStateOf(showHint) }
    val bringIntoView = remember { BringIntoViewRequester() }
    // 编辑区实测高度：滚动的目标矩形要比它再高一点，否则「取消/确定」会正好
    // 贴在视口底边被裁掉一半（真机 11-10 20:4x：按钮只有 30px 可见高度）。
    var editorH by remember { mutableIntStateOf(0) }

    // v2.24：档位层与滚轮层。用派生值而不是 LaunchedEffect 写状态，避免进入编辑态
    // 的头一帧先闪一下档位（提醒间隔没有档位，必须直接进滚轮）。
    val presets = presetsOf(mode)
    var wheelRequested by remember { mutableStateOf(false) }
    LaunchedEffect(editing) { if (!editing) wheelRequested = false }
    val showWheel = editing && (presets.isEmpty() || wheelRequested)
    val customSelected = presets.isNotEmpty() && value !in presets.map { it.value }
    // v2.24：圆环状态行只在「滚轮层」说「调好后点确定」；档位层根本没有确定按钮，
    // 照抄会骗人。把当前层级如实报给主页面，不在组件里各写一份状态词。
    LaunchedEffect(editing, showWheel) { onStageChange(editing && showWheel) }

    // 展开后把整块编辑区（含取消/确定，另加 88px 余量）滚进可视区。
    // 这是「看不到确定和取消」的正解，不是把页面滚动关掉。
    // 切到滚轮层时 editorH 会变，effect 重跑再滚一次，按钮不会被 200dp 的滚轮顶出去。
    LaunchedEffect(editing, showWheel, editorH) {
        if (editing && editorH > 0) {
            delay(80)
            runCatching {
                bringIntoView.bringIntoView(
                    Rect(0f, 0f, 1f, editorH.toFloat() + 88f)
                )
            }
        }
    }

    fun cancel() {
        draft = value
        onEditingChange(false)
    }

    fun commit(v: Int) {
        onCommit(v)
        // 只有「自定义」写回的值才记进「上次自定义」；点固定档不覆盖它，
        // 下次从某个固定档点进自定义还能回到上次那个顺手的时长。
        customKeyOf(mode)?.let { key ->
            if (v !in presets.map { it.value }) {
                ctx.prefs().edit().putInt(key, v).apply()
            }
        }
        onEditingChange(false)
    }

    fun startCustom(): Int {
        val key = customKeyOf(mode) ?: return value
        val stored = ctx.prefs().getInt(key, -1)
        return when {
            // 当前值本来就不是固定档：直接从它接着改，别把人扔回某个旧数字。
            value !in presets.map { it.value } -> value
            stored >= 0 && valid(mode, stored) -> stored
            else -> value
        }
    }

    BackHandler(enabled = editing) { cancel() }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!editing) {
            // 显示态：与 SettingRow 同一套字号 / 颜色 / 行距，整行可点。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = enabled,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        draft = value
                        onEditingChange(true)
                        hintVisible = false
                        ctx.prefs().edit().putBoolean(KEY_DURATION_HINT, true).apply()
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 铃声 SettingRow 左边有一枚 38dp 图标 + 12dp 间距。时长行没有图标，
                // 不用硬塞一个装饰，但留同样的空位，七行标签才在同一条竖线上。
                Spacer(Modifier.width(50.dp))
                Text(
                    text = label,
                    color = C.text,
                    fontSize = 15.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = Lang.durationSettingLabel(value, mode),
                    color = C.accent,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider(thickness = 1.dp, color = C.divider)
            if (hintVisible) {
                Spacer(Modifier.height(5.dp))
                Text(text = Lang.t("duration_tap_hint"), color = C.accentDim, fontSize = 11.sp)
            }
        } else {
            // 编辑态：整行再点一次即收回（等同取消）。外层主页面另有「点空白取消」。
            val cols = colsOf(mode, draft)
            // 连续 fling 时回调不随重组重建，必须读「这一刻」的 draft 重算各列。
            val draftNow by rememberUpdatedState(draft)
            val canApply = valid(mode, draft)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { editorH = it.height }
                    .bringIntoViewRequester(bringIntoView)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { cancel() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(50.dp))
                    Text(
                        text = label,
                        color = C.text,
                        fontSize = 15.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = Lang.durationSettingLabel(draft, mode),
                        color = C.accent,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!showWheel) {
                    // v2.24 档位层：五个小字，选中只换颜色，不加卡片、不加边框。
                    // 当前值不是固定档时，「自定义」这一格高亮并在下面回显当前值。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        presets.forEach { choice ->
                            val selected = value == choice.value
                            Text(
                                text = choice.label,
                                color = if (selected) C.accent else C.secondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { commit(choice.value) }
                                    .padding(vertical = 10.dp)
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    draft = startCustom()
                                    wheelRequested = true
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = Lang.t("duration_custom"),
                                color = if (customSelected) C.accent else C.secondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                            if (customSelected) {
                                Text(
                                    text = Lang.durationSettingLabel(value, mode),
                                    color = C.accentDim,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = C.divider)
                } else {
                    // v2.24：每列 weight(1f) 铺满可用宽度，滚轮行内不再有空白区；
                    // 整块竖向手势都归 NumberPicker，手落空拖走整页的老毛病随之消失。
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        cols.forEachIndexed { idx, col ->
                            WheelColumn(
                                col = col,
                                modifier = Modifier.weight(1f),
                                onValue = { nv ->
                                    val vals = colsOf(mode, draftNow).map { it.value }.toMutableList()
                                    vals[idx] = nv
                                    draft = encode(mode, vals)
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = Lang.t("cancel"),
                            color = C.secondary,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { cancel() }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        // 0 时 0 分时「确定」置灰不可点：不 Toast、不红字。
                        Text(
                            text = Lang.t("btn_apply"),
                            color = if (canApply) C.accent else C.stroke,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable(
                                    enabled = canApply,
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { commit(draft) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                    HorizontalDivider(thickness = 1.dp, color = C.divider)
                }
            }
        }
    }
}

/**
 * 一列原生滚轮。NumberPicker 自带 VelocityTracker → fling → 逐格吸附 → 长按连续滚动，
 * 这些不是「自绘」能补的；我们只负责把值域、外观和三处复用接上。
 *
 * v2.24 与系统计时页对齐：循环、5 行可见、铺满列宽、整块可触。
 * 必须不出现：选中色块、分隔线、加减箭头、教程遮罩。
 * `wrapSelectorWheel = true` + `setSelectionDividerHeight(0)` +
 * `setBackgroundColor(TRANSPARENT)` + 关掉 fading edge，正好把这几条做死。
 */
@Composable
private fun WheelColumn(
    col: WheelCol,
    modifier: Modifier = Modifier,
    onValue: (Int) -> Unit
) {
    val onValueNow = rememberUpdatedState(onValue)
    val textArgb = C.text.toArgb()
    val values = remember(col.range) { col.range.toList() }
    val labels = remember(col.range) { values.map(::format).toTypedArray() }
    val index = values.indexOf(col.value).coerceIn(0, values.lastIndex)

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(
            factory = { c ->
                NumberPicker(c).apply {
                    minValue = 0
                    maxValue = values.lastIndex
                    displayedValues = labels
                    // v2.24：系统计时页实测循环（秒列 58/59/00/01/02），上一轮「不循环」
                    // 是没测边界就下的结论。循环同时消掉 fling 撞边界的硬停，就是用户
                    // 说的「阻力」。原生要求可选值数 >= 可见行数，本文件各列值域都够。
                    wrapSelectorWheel = true
                    // 中间那格内部是 EditText：不挡焦点的话，轻点/滑动偶尔会冒出数字键盘，
                    // 正是用户说的「触碰其他组件」。系统计时器基线也是点数字不弹键盘。
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    isVerticalFadingEdgeEnabled = false
                    // 三个装饰 API 都是 API 29+（api-versions.xml 已核）；真机 API 31 全走这里，
                    // 26~28 保留系统默认外观，功能不受影响，不构成兼容分叉。
                    if (Build.VERSION.SDK_INT >= 29) {
                        setSelectionDividerHeight(0)
                        setTextColor(textArgb)
                        setTextSize(
                            TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_SP, 20f, c.resources.displayMetrics
                            )
                        )
                    }
                    value = index
                    // v2.23：整页滚动已恢复，所以滚轮必须自己把手势归属说清楚——
                    // 按下时告诉父级别拦截，抬起时归还。这样滚轮区快甩不会拖走整页，
                    // 而滚轮之外的空白仍可正常滚动页面。
                    setOnTouchListener { v, ev ->
                        when (ev.actionMasked) {
                            MotionEvent.ACTION_DOWN ->
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        false
                    }
                    setOnValueChangedListener { _, _, newIdx ->
                        onValueNow.value(values[newIdx])
                    }
                }
            },
            update = { picker ->
                // 外部值（取消回滚、另一列联动）变了才回写；等值时不碰，避免打断正在进行的 fling。
                if (picker.value != index) picker.value = index
            },
            modifier = Modifier
                .fillMaxWidth()
                // 真机系统计时页每列 901px ≈ 5 行；原 120dp 只有 330px ≈ 3 行，行程短、
                // fling 一起就到头。200dp ≈ 550px ≈ 5 行，与系统同量级。
                .height(200.dp)
                .semantics { contentDescription = "${col.value} ${col.label}" }
        )
        Text(text = col.label, color = C.secondary, fontSize = 11.sp)
    }
}
