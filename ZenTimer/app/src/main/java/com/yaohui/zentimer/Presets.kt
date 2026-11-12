package com.yaohui.zentimer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 预设：把「时长 / 准备 / 间隔 / 三种铃 / 背景音 / 响铃次数」整套组合存成一个方案。
 * 只存本机 SharedPreferences，用平台自带 org.json，不引入任何第三方依赖、不联网、不上传。
 * 上限 10 个（v2.27 从 6 放宽），列表按最近应用时间倒序，常用的自动顶上去。
 */
data class Preset(
    val id: String,
    val name: String,
    val durationMin: Int,
    val intervalMin: Int,
    val prepSec: Int,
    val startBell: String,
    val intervalBell: String,
    val endBell: String,
    val ambience: String,
    val startTimes: Int,
    val intervalTimes: Int,
    val endTimes: Int,
    /** v2.27 增加：上次被应用的时间戳（毫秒），列表默认按最近应用排序。0 表示旧数据无此信息。 */
    val lastAppliedAt: Long = 0L
) {
    /** 列表第二行摘要：时长 + 结束铃 + 响铃次数，一眼认出是哪一套。 */
    fun summary(): String = buildString {
        append(Lang.durationLabel(durationMin))
        append(" · ")
        append(SoundCatalog.bell(endBell).name)
        append(" ").append(Lang.times(endTimes))
        if (prepSec > 0) {
            append(" · ").append(Lang.t("preset_prep")).append(" ").append(Lang.seconds(prepSec))
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("durationMin", durationMin)
        put("intervalMin", intervalMin)
        put("prepSec", prepSec)
        put("startBell", startBell)
        put("intervalBell", intervalBell)
        put("endBell", endBell)
        put("ambience", ambience)
        put("startTimes", startTimes)
        put("intervalTimes", intervalTimes)
        put("endTimes", endTimes)
        put("lastAppliedAt", lastAppliedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Preset = Preset(
            id = o.optString("id"),
            name = o.optString("name"),
            durationMin = o.optInt("durationMin", TimerEngine.Defaults.DURATION_MIN)
                .let {
                    if (it < 1) TimerEngine.Defaults.DURATION_MIN
                    else it.coerceIn(1, TimerEngine.Defaults.DURATION_MAX_MIN)
                },
            intervalMin = o.optInt("intervalMin", TimerEngine.Defaults.INTERVAL_MIN),
            prepSec = o.optInt("prepSec", TimerEngine.Defaults.PREP_SEC)
                .coerceIn(0, TimerEngine.Defaults.PREP_MAX_SEC),
            startBell = o.optString("startBell", TimerEngine.Defaults.START_BELL),
            intervalBell = o.optString("intervalBell", TimerEngine.Defaults.INTERVAL_BELL),
            endBell = o.optString("endBell", TimerEngine.Defaults.END_BELL),
            ambience = o.optString("ambience", TimerEngine.Defaults.AMBIENCE),
            startTimes = o.optInt("startTimes", TimerEngine.Defaults.START_TIMES).coerceIn(1, 3),
            intervalTimes = o.optInt("intervalTimes", TimerEngine.Defaults.BELL_TIMES).coerceIn(1, 3),
            endTimes = o.optInt("endTimes", TimerEngine.Defaults.BELL_TIMES).coerceIn(1, 3),
            lastAppliedAt = o.optLong("lastAppliedAt", 0L)
        )
    }
}

object PresetStore {
    const val MAX = 10
    private const val KEY = "presets_v1"
    private const val KEY_LAST = "preset_last_applied"

    /** v2.27：按最近应用时间倒序，常用的自动顶上去；旧数据没有时间戳则退化为保存顺序。 */
    fun all(ctx: Context): List<Preset> = try {
        val raw = ctx.prefs().getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            runCatching { Preset.fromJson(arr.getJSONObject(i)) }.getOrNull()
        }.filter { it.id.isNotEmpty() }
            .sortedWith(compareByDescending<Preset> { it.lastAppliedAt }.thenBy { it.name })
    } catch (_: Throwable) {
        emptyList()
    }

    /** 满额时返回 false，界面据此提示，不静默丢数据。 */
    fun save(ctx: Context, p: Preset): Boolean {
        val list = all(ctx).toMutableList()
        val idx = list.indexOfFirst { it.id == p.id }
        // 新保存的预设视为"最近用"，默认打上当前时间戳，自动顶到列表顶部
        val fresh = if (idx >= 0) p else p.copy(lastAppliedAt = System.currentTimeMillis())
        if (idx >= 0) list[idx] = fresh
        else {
            if (list.size >= MAX) return false
            list.add(fresh)
        }
        return try {
            ctx.prefs().edit().putString(KEY, JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()).apply()
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun remove(ctx: Context, id: String) {
        val list = all(ctx).filterNot { it.id == id }
        val edit = ctx.prefs().edit()
            .putString(KEY, JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
        if (lastId(ctx) == id) edit.remove(KEY_LAST)
        edit.apply()
    }

    /** v2.3 修订：记住最近应用的一套，底部预设面板据此高亮「已选」。 */
    fun lastId(ctx: Context): String? = try {
        ctx.prefs().getString(KEY_LAST, null)
    } catch (_: Throwable) {
        null
    }

    /** v2.27：除了记住最后 id，也把该预设的时间戳更新到当前时刻，让它排到列表顶部。 */
    fun markApplied(ctx: Context, id: String) {
        val now = System.currentTimeMillis()
        val list = all(ctx).map { if (it.id == id) it.copy(lastAppliedAt = now) else it }
        try {
            ctx.prefs().edit()
                .putString(KEY_LAST, id)
                .putString(KEY, JSONArray().apply { list.forEach { put(it.toJson()) } }.toString())
                .apply()
        } catch (_: Throwable) {
            ctx.prefs().edit().putString(KEY_LAST, id).apply()
        }
    }

    /** 默认名：时长 + 开始铃。用户可在保存对话框里改。 */
    fun defaultName(s: TimerEngine.Ui): String =
        "${Lang.minutes(s.durationMin)} · ${SoundCatalog.bell(s.startBell).name}"
}
