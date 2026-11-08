package com.stilltimer.personal

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 应用主题色：跟随系统 / 深色 / 浅色。
 *
 * 与 Lang 一样用 StateFlow 驱动：切换只重组界面，不重建 Activity、不打断正在进行的练习。
 * 「跟随系统」由 Compose 的 isSystemInDarkTheme() 判定，系统切换深浅模式会自动重组。
 * 偏好只写本机 SharedPreferences，不申请任何权限、不联网。
 */
object Theme {

    enum class Mode(val code: String) { SYSTEM("system"), DARK("dark"), LIGHT("light") }

    const val KEY = "theme_mode"

    private val _mode = MutableStateFlow(Mode.SYSTEM)
    val mode: StateFlow<Mode> = _mode
    val current: Mode get() = _mode.value

    fun init(ctx: Context) {
        _mode.value = when (ctx.prefs().getString(KEY, "system")) {
            "dark" -> Mode.DARK
            "light" -> Mode.LIGHT
            else -> Mode.SYSTEM
        }
    }

    fun set(ctx: Context, m: Mode) {
        _mode.value = m
        ctx.prefs().edit().putString(KEY, m.code).apply()
    }
}
