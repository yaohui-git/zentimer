package com.stilltimer.personal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 预设：把「时长 / 准备 / 间隔 / 三种铃 / 背景音 / 响铃次数」整套组合存成一个方案。
 * 只存本机 SharedPreferences，用平台自带 org.json，不引入任何第三方依赖、不联网、不上传。
 * 上限 6 个：够用，也不让界面变成配置管理器。
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
    val endTimes: Int
) {
    /** 列表第二行摘要：时长 + 结束铃 + 响铃次数，一眼认出是哪一套。 */
    fun summary(): String = buildString {
        append(Lang.minutes(durationMin))
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
    }

    companion object {
        fun fromJson(o: JSONObject): Preset = Preset(
            id = o.optString("id"),
            name = o.optString("name"),
            durationMin = o.optInt("durationMin", 10),
            intervalMin = o.optInt("intervalMin", 0),
            prepSec = o.optInt("prepSec", 10),
            startBell = o.optString("startBell", "b_basu"),
            intervalBell = o.optString("intervalBell", "b_wood"),
            endBell = o.optString("endBell", "b_gong"),
            ambience = o.optString("ambience", "none"),
            startTimes = o.optInt("startTimes", 1).coerceIn(1, 3),
            intervalTimes = o.optInt("intervalTimes", 1).coerceIn(1, 3),
            endTimes = o.optInt("endTimes", 1).coerceIn(1, 3)
        )
    }
}

object PresetStore {
    const val MAX = 6
    private const val KEY = "presets_v1"
    private const val KEY_LAST = "preset_last_applied"

    fun all(ctx: Context): List<Preset> = try {
        val raw = ctx.prefs().getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            runCatching { Preset.fromJson(arr.getJSONObject(i)) }.getOrNull()
        }.filter { it.id.isNotEmpty() }
    } catch (_: Throwable) {
        emptyList()
    }

    /** 满额时返回 false，界面据此提示，不静默丢数据。 */
    fun save(ctx: Context, p: Preset): Boolean {
        val list = all(ctx).toMutableList()
        val idx = list.indexOfFirst { it.id == p.id }
        if (idx >= 0) list[idx] = p
        else {
            if (list.size >= MAX) return false
            list.add(0, p)
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

    fun markApplied(ctx: Context, id: String) {
        ctx.prefs().edit().putString(KEY_LAST, id).apply()
    }

    /** 默认名：时长 + 开始铃。用户可在保存对话框里改。 */
    fun defaultName(s: TimerEngine.Ui): String =
        "${Lang.minutes(s.durationMin)} · ${SoundCatalog.bell(s.startBell).name}"
}
