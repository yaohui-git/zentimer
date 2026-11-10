package com.yaohui.zentimer

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * 「四时」，不是「四季配色」。
 *
 * 依据《四季配色：会不会破坏禅意，以及一个可行的版本》（11-10）的判断：
 * 会破坏禅意的不是四季，是四套颜色。所以全年只动两个变量 —— 纸色（明度 / 色温）
 * 与强调色（同一条极窄的近无彩色相弧），文字与图标全年不变；四个色相轮换、
 * 季节插画、渐变、换色动画、节气文案一律不做，也不给四选一的手动季节开关。
 *
 * 四条硬规矩落在这里：
 * ① 按公历近似分段，一年四次，不引农历与精确节气；
 * ② 只在进程启动时算一次快照（[snapshot]），运行中、切后台、深浅模式切换都不重算；
 * ③ 静坐中不换色 —— 开关在计时态置灰，见 MainActivity.SettingsPage；
 * ④ 无 Toast、无动画、无节气文案，冷启动直接就是那套色。
 *
 * 色值取自方案里已经算过对比度的表：浅色正文 ≥14.77:1、强调 ≥4.12:1，
 * 深色正文 ≥14.69:1、强调 ≥5.66:1，八组全部过门槛，不需要再调。
 */
internal enum class Season(val code: String) {
    SPRING("spring"), SUMMER("summer"), AUTUMN("autumn"), WINTER("winter");

    companion object {
        /** 3–5 春 / 6–8 夏 / 9–11 秋 / 12–2 冬。 */
        fun of(month: Int): Season = when (month) {
            3, 4, 5 -> SPRING
            6, 7, 8 -> SUMMER
            9, 10, 11 -> AUTUMN
            else -> WINTER
        }
    }
}

/** 四时真正允许变化的只有这两个 token；其余字段一律从基准色板派生。 */
internal data class SeasonTokens(val paper: Color, val accent: Color)

internal object SeasonTheme {

    const val KEY_FINE_TUNE = "season_fine_tune"

    /**
     * 仅供真机验收的月份注入钩子：adb shell settings put global zentimer_season_month 4。
     * 只读、不写、不进任何界面；生产用户没有这个 global 值时按真实月份走。
     * 不引它就得改系统日期，而未 root 的真机 `date -s` 不生效，四档纸色便无法取证。
     * 用完 `settings delete global zentimer_season_month` 即回到真实日期。
     */
    private const val TEST_MONTH_KEY = "zentimer_season_month"

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled

    /** 进程内快照：整个进程生命周期只读它，绝不在 Composable 里读当前日期。 */
    @Volatile
    private var snapshot: Season = Season.AUTUMN

    val current: Season get() = snapshot

    /** 只在 Application.onCreate 调一次。关掉开关时固定为秋（最接近纸的暖），不是黑白。 */
    fun init(ctx: Context) {
        _enabled.value = ctx.prefs().getBoolean(KEY_FINE_TUNE, true)
        snapshot = if (_enabled.value) Season.of(monthFor(ctx)) else Season.AUTUMN
    }

    /** 用户主动改设置：允许本次进程内立即重算，这不等于「随时间自动切换」。 */
    fun setEnabled(ctx: Context, on: Boolean) {
        // 先改快照再发状态：StateFlow 收到通知时重组读到的已经是新档，不会有一帧旧纸色。
        snapshot = if (on) Season.of(monthFor(ctx)) else Season.AUTUMN
        ctx.prefs().edit().putBoolean(KEY_FINE_TUNE, on).apply()
        _enabled.value = on
    }

    private fun monthFor(ctx: Context): Int {
        val override = runCatching {
            Settings.Global.getString(ctx.contentResolver, TEST_MONTH_KEY)?.toIntOrNull()
        }.getOrNull()
        return if (override != null && override in 1..12) override else LocalDate.now().monthValue
    }

    private fun tokens(season: Season, dark: Boolean): SeasonTokens = if (dark) when (season) {
        Season.SPRING -> SeasonTokens(Color(0xFF14150F), Color(0xFF8E988A))
        Season.SUMMER -> SeasonTokens(Color(0xFF15150F), Color(0xFF9A968B))
        Season.AUTUMN -> SeasonTokens(Color(0xFF15130E), Color(0xFF9C8F7C))
        Season.WINTER -> SeasonTokens(Color(0xFF131416), Color(0xFF8A8F96))
    } else when (season) {
        Season.SPRING -> SeasonTokens(Color(0xFFF6F5EF), Color(0xFF6F7A6A))
        Season.SUMMER -> SeasonTokens(Color(0xFFFAF9F4), Color(0xFF7A776E))
        Season.AUTUMN -> SeasonTokens(Color(0xFFF7F2E8), Color(0xFF7C7061))
        Season.WINTER -> SeasonTokens(Color(0xFFF4F4F3), Color(0xFF70747A))
    }

    /**
     * 以现有两套色板为基准，把「基准底色 → 四时纸色」的差值平移到所有纸色系字段，
     * 层次（浮起 / 卡片 / 轨道 / 描边 / 分隔）一点不改，只是整体色温跟着走。
     * text / secondary 属 ink，全年不动；深色文字按方案统一为 #E8E6E1。
     */
    fun palette(dark: Boolean, season: Season, base: StillColors): StillColors {
        val t = tokens(season, dark)
        val dR = t.paper.red - base.bg.red
        val dG = t.paper.green - base.bg.green
        val dB = t.paper.blue - base.bg.blue

        fun shift(c: Color): Color = Color(
            red = (c.red + dR).coerceIn(0f, 1f),
            green = (c.green + dG).coerceIn(0f, 1f),
            blue = (c.blue + dB).coerceIn(0f, 1f),
            alpha = c.alpha
        )

        fun mix(a: Color, b: Color, f: Float): Color = Color(
            red = a.red + (b.red - a.red) * f,
            green = a.green + (b.green - a.green) * f,
            blue = a.blue + (b.blue - a.blue) * f,
            alpha = a.alpha
        )

        return base.copy(
            bg = t.paper,
            elevated = shift(base.elevated),
            card = shift(base.card),
            track = shift(base.track),
            stroke = shift(base.stroke),
            divider = shift(base.divider),
            accent = t.accent,
            accentDim = mix(t.accent, t.paper, 0.34f),
            text = if (dark) Color(0xFFE8E6E1) else base.text
        )
    }
}
