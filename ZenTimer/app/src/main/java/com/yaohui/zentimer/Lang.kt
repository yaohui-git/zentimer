package com.yaohui.zentimer

import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 应用内三语（简体 / 繁体 / English）。
 * 切换只重刷文案，不重建 Activity，计时状态完全不受影响。
 * 首次安装按系统语言定默认值，之后一律以用户在「设置」里的选择为准。
 * 兼容 v2.1 的旧偏好值 "zh" / "en"。
 *
 * v2.3 起新增文案集中在 EXTRA_* 三张表里，t() 先查主表再查新增表；
 * 三张表的 key 必须一一对应，用 tools/check_lang.py 对账，缺一条就报错。
 */
object Lang {

    enum class Mode(val code: String) { HANS("hans"), HANT("hant"), EN("en") }

    private val _mode = MutableStateFlow(Mode.HANS)
    val mode: StateFlow<Mode> = _mode
    val current: Mode get() = _mode.value
    val isEn: Boolean get() = _mode.value == Mode.EN

    const val KEY_LANG = "lang"
    private const val KEY_LANG_MANUAL = "lang_manual"
    /** 存储格式版本：v2.10 起语言判定改为多来源，旧版留下的键需要一次性校正。 */
    private const val KEY_LANG_VER = "lang_ver"
    private const val LANG_VER = 1

    fun init(ctx: Context) {
        val prefs = ctx.prefs()
        val saved = prefs.getString(KEY_LANG, null)
        val manual = prefs.getBoolean(KEY_LANG_MANUAL, false)
        val ver = prefs.getInt(KEY_LANG_VER, 0)
        val sys = defaultFromSystem(ctx)

        // v2.9 及以前有个自锁缺陷：一旦某次把中文系统判成 en 并盖上「手动」戳，
        // 之后每次启动都照抄这个 en，永远不会再跟随系统 —— 系统中文、界面英文就是这么来的。
        // 只在「存的是 en、系统其实是中文、且是本版之前写下的」这一种矛盾上翻案；
        // 用户自己选的简/繁一律不动，避免把繁体用户误翻成简体。
        val stale = ver < LANG_VER && manual && saved == "en" && sys != Mode.EN

        _mode.value = when {
            stale -> sys
            // 用户手动选过：以选择为准，系统语言变了也不覆盖
            manual && saved != null -> when (saved) {
                "hans", "zh" -> Mode.HANS
                "hant" -> Mode.HANT
                "en" -> Mode.EN
                else -> sys
            }
            // 没手动选过：每次启动都按当前系统语言重算，真正做到「默认跟随系统」
            else -> sys
        }

        val e = prefs.edit()
            .putString(KEY_LANG, _mode.value.code)
            .putInt(KEY_LANG_VER, LANG_VER)
        if (stale) e.putBoolean(KEY_LANG_MANUAL, false)
        e.apply()
    }

    /**
     * 默认语言跟随系统：简体中文 → HANS，繁体中文 → HANT，
     * 其余所有语言（English、日语、法语、韩语……）一律 EN，让国际友人开箱即用。
     */
    private fun defaultFromSystem(ctx: Context): Mode {
        // ① Android 13+ 的「应用级语言」（系统设置里单独给本应用指定的语言）是明确选择，最高优先。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val app = runCatching {
                ctx.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales
            }.getOrNull()
            if (app != null && !app.isEmpty) return modeOf(app[0])
        }

        // ② 否则看系统语言。这里要三个来源一起看：只看 app 自己的 configuration 不够——
        //   部分 ROM 在应用没有对应语言资源时会把自己的配置回落到 en（实测 Android 12 的 MIUI 有这毛病），
        //   而 getSystem() 与 Locale.getDefault() 才是系统真值。任一来源说是中文，就按中文。
        val candidates = listOfNotNull(
            ctx.resources.configuration.locales.let { if (it.isEmpty) null else it[0] },
            Resources.getSystem().configuration.locales.let { if (it.isEmpty) null else it[0] },
            Locale.getDefault()
        )
        val zh = candidates.firstOrNull { it.language == "zh" }
        return modeOf(zh ?: candidates.first())
    }

    private fun modeOf(l: Locale): Mode {
        if (l.language != "zh") return Mode.EN
        val tag = l.toLanguageTag().lowercase(Locale.ROOT)
        val script = l.script.lowercase(Locale.ROOT)
        val country = l.country.uppercase(Locale.ROOT)
        return when {
            script == "hant" || tag.contains("hant") || country in setOf("TW", "HK", "MO") -> Mode.HANT
            else -> Mode.HANS
        }
    }

    fun set(ctx: Context, m: Mode) {
        _mode.value = m
        ctx.prefs().edit()
            .putString(KEY_LANG, m.code)
            .putBoolean(KEY_LANG_MANUAL, true)
            .putInt(KEY_LANG_VER, LANG_VER)
            .apply()
    }

    /** 设置页「跟随系统」：清掉手动标记，立即按系统语言重新判定。 */
    fun followSystem(ctx: Context) {
        val m = defaultFromSystem(ctx)
        _mode.value = m
        ctx.prefs().edit()
            .putBoolean(KEY_LANG_MANUAL, false)
            .putString(KEY_LANG, m.code)
            .putInt(KEY_LANG_VER, LANG_VER)
            .apply()
    }

    fun t(key: String): String {
        val primary = when (_mode.value) {
            Mode.HANS -> HANS[key] ?: EXTRA_HANS[key]
            Mode.HANT -> HANT[key] ?: EXTRA_HANT[key]
            Mode.EN -> EN[key] ?: EXTRA_EN[key]
        }
        return primary ?: HANS[key] ?: EXTRA_HANS[key] ?: key
    }

    /**
     * 在一个 Composable 里建立「依赖语言」的订阅；返回值不用也必须调一次。
     * 必须真的读 .value——只调 collectAsState() 不读 value，等于没订阅，
     * 语言切换后这个 Composable 会被 Compose 跳过，残留旧语言（实测：设置页标题一直写「设置」）。
     */
    @Composable
    fun watch(): Mode = _mode.collectAsState().value

    /**
     * Compose 专用取词：内部订阅语言状态，切换语言后调用方一定重组。
     * 侧滑抽屉、顶栏这类独立 Composable 如果只调 [t]，会被 Compose 跳过而残留旧语言。
     */
    @Composable
    fun tx(key: String): String {
        val m by _mode.collectAsState()
        val primary = when (m) {
            Mode.HANS -> HANS[key] ?: EXTRA_HANS[key]
            Mode.HANT -> HANT[key] ?: EXTRA_HANT[key]
            Mode.EN -> EN[key] ?: EXTRA_EN[key]
        }
        return primary ?: HANS[key] ?: EXTRA_HANS[key] ?: key
    }

    /** 顶部一行：问候 + 日期。三种语言各自独立排版。 */
    fun greetingLine(): String {
        val h = LocalTime.now().hour
        val d = LocalDate.now()
        return when (_mode.value) {
            Mode.EN -> {
                val hi = when {
                    h < 5 -> "Still awake"
                    h < 11 -> "Good morning"
                    h < 14 -> "Good day"
                    h < 18 -> "Good afternoon"
                    else -> "Good evening"
                }
                "$hi · " + d.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH))
            }
            Mode.HANT -> {
                val hi = when {
                    h < 5 -> "夜深了"
                    h < 11 -> "早安"
                    h < 14 -> "午安"
                    h < 18 -> "午後好"
                    else -> "晚安"
                }
                val week = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.TAIWAN)
                "$hi · " + d.format(DateTimeFormatter.ofPattern("M月d日", Locale.TAIWAN)) + " $week"
            }
            else -> {
                val hi = when {
                    h < 5 -> "夜深了"
                    h < 11 -> "早安"
                    h < 14 -> "午安"
                    h < 18 -> "下午好"
                    else -> "晚安"
                }
                val week = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA)
                "$hi · " + d.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)) + " $week"
            }
        }
    }

    /**
     * v2.21 时长显示态：返回「大数字」与「单位小字」。口径只在这里定义一次，
     * 三个时长设置、预设摘要共用；不在 UI 里各拼一遍，免得 1:20 又被写成 80 分。
     */
    fun durationParts(total: Int, mode: DurationMode): Pair<String, String> {
        if (total == 0 && mode != DurationMode.HOUR_MINUTE) return t("off") to ""
        val pad = { n: Int -> if (n < 10) "0$n" else n.toString() }
        return when (mode) {
            DurationMode.HOUR_MINUTE ->
                if (total < 60) total.toString() to t("unit_min_short")
                else "${total / 60}:${pad(total % 60)}" to
                    "${t("unit_hour_short")} ${t("unit_min_short")}"
            DurationMode.MINUTE_SECOND ->
                if (total < 60) total.toString() to t("unit_sec_short")
                else "${total / 60}:${pad(total % 60)}" to
                    "${t("unit_min_short")} ${t("unit_sec_short")}"
            DurationMode.MINUTES_ONLY -> total.toString() to t("unit_min_short")
        }
    }

    /** 预设摘要、完成态提示里的一行式时长：37 分 / 2:00。 */
    fun durationLabel(n: Int): String {
        if (n < 60) return "$n ${t("unit_min_short")}"
        val p = durationParts(n, DurationMode.HOUR_MINUTE)
        return p.first
    }

    /**
     * 设置行里的一行式时长（v2.22）。与 durationLabel 的区别是场景：这里是与铃声
     * `SettingRow` 同一行的右对齐值，必须能一眼读全、且不撑破 12sp 的版式；
     * 圆环 / 预设摘要继续用更紧凑的 durationLabel。口径都只在本文件定义一次。
     */
    fun durationSettingLabel(total: Int, mode: DurationMode): String {
        if (total == 0 && mode != DurationMode.HOUR_MINUTE) return t("off")
        val pad = { n: Int -> if (n < 10) "0$n" else n.toString() }
        return when (mode) {
            DurationMode.HOUR_MINUTE ->
                if (total < 60) "$total ${t("unit_min_short")}"
                else "${total / 60} ${t("unit_hour_short")} ${pad(total % 60)} ${t("unit_min_short")}"
            DurationMode.MINUTE_SECOND ->
                if (total < 60) "$total ${t("unit_sec_short")}"
                else "${total / 60} ${t("unit_min_short")} ${pad(total % 60)} ${t("unit_sec_short")}"
            DurationMode.MINUTES_ONLY -> "$total ${t("unit_min_short")}"
        }
    }

    fun minutes(n: Int): String {
        // v2.24：上限放到 23:59 后，「再来一次 · 1439 分钟」这种写法不能留。
        // 超过 2 小时改说「几小时几分」，与设置行 / 圆环的读法一致。
        if (n > 120) return when (_mode.value) {
            Mode.HANS -> "${n / 60} 小时 ${n % 60} 分"
            Mode.HANT -> "${n / 60} 小時 ${n % 60} 分"
            Mode.EN -> "${n / 60} h ${n % 60} min"
        }
        if (n == 120) return when (_mode.value) {
            Mode.HANS -> "2 小时"
            Mode.HANT -> "2 小時"
            Mode.EN -> "2 h"
        }
        return when (_mode.value) {
            Mode.HANS -> "$n 分钟"
            Mode.HANT -> "$n 分鐘"
            Mode.EN -> "$n min"
        }
    }

    fun seconds(n: Int): String {
        if (n < 60) return when (_mode.value) {
            Mode.EN -> "$n s"
            else -> "$n 秒"
        }
        val m = n / 60
        return when (_mode.value) {
            Mode.HANS -> "$m 分钟"
            Mode.HANT -> "$m 分鐘"
            Mode.EN -> "$m min"
        }
    }

    fun times(n: Int): String = "×$n"
    fun off(): String = t("off")

    private val HANS = mapOf(
        "app_title" to "静息",
        "state_idle" to "准备好了，随时开始",
        "state_prep" to "准备中",
        // 08:18 用户裁决：状态词统一「静坐中」，充电锁屏（AOD）与卡片正文都不给数字。
        "state_running" to "静坐中",
        "state_paused" to "已暂停",
        "state_done" to "本次静坐完成",
        "state_stopped" to "本次静坐已结束",
        "hint_tap_ring" to "轻触圆环改时长",
        "hint_tap_again" to "下方按钮可再来一次",
        "btn_start" to "开始静坐",
        "btn_again" to "再来一次",
        "btn_pause" to "暂停",
        "btn_resume" to "继续",
        "btn_stop" to "结束",
        "section_duration" to "静坐时长",
        "section_interval" to "提醒间隔",
        "section_prep" to "准备时间",
        "label_start_bell" to "开始铃声",
        "label_interval_bell" to "间隔铃声",
        "label_end_bell" to "结束铃声",
        "label_ambience" to "背景音",
        "off" to "关",
        "cancel" to "取消",
        "toast_running" to "静坐进行中，结束后再调整",
        "picker_start" to "选择开始铃声",
        "picker_interval" to "选择间隔铃声",
        "picker_end" to "选择结束铃声",
        "picker_ambience" to "选择背景音",
        "catchup" to "本次静坐已在后台结束，铃声未补响",
        // 菜单与三个子页
        "menu_presets" to "预设",
        "menu_settings" to "设置",
        "menu_about" to "关于",
        "title_presets" to "预设",
        "title_settings" to "设置",
        "title_about" to "关于",
        "btn_back" to "返回",
        "btn_confirm" to "保存",
        "btn_delete" to "删除",
        // 设置
        "settings_language" to "语言",
        "lang_hans" to "简体",
        "lang_hant" to "繁体",
        "lang_en" to "English",
        "settings_language_note" to "首次跟随系统，其他语言用 English，切换不打断静坐",
        "settings_bell_times" to "铃声右侧的 ×N 可轻触切换响铃次数",
        "settings_notify" to "计时通知",
        "notify_on" to "通知已开启",
        "notify_off" to "通知已关闭，锁屏与状态栏都不显示倒计时",
        "notify_channel_off" to "计时通知渠道已关闭",
        "notify_hint" to "点此打开系统通知设置，可开启锁屏通知",
        "notify_live_off" to "未开启系统「实时动态」，状态栏胶囊不会显示",
        // 预设
        "preset_empty" to "还没有保存的预设",
        "preset_empty_hint" to "调好时长与铃声后，点下方按钮保存",
        "preset_save" to "保存当前预设",
        "preset_dialog_title" to "为这个预设命名",
        "preset_name_hint" to "例如：晨间 20 分钟",
        "preset_max" to "最多保存 6 个预设",
        "preset_running_block" to "静坐进行中，结束后再切换预设",
        "preset_applied" to "已应用",
        "preset_deleted" to "已删除",
        "preset_saved" to "已保存",
        "preset_prep" to "准备",
        "preset_times" to "响铃",
        // 关于
        "about_tagline" to "若人静坐一须臾，胜造恒沙七宝塔",
        "about_version" to "版本",
        "about_contact" to "联系作者",
        "about_repo" to "代码仓库",
        "about_license" to "开源许可",
        "about_privacy" to "隐私政策",
        "about_assets" to "素材说明",
        "about_contact_body" to "反馈、建议与合作，欢迎来信",
        "about_license_body" to "代码采用 Apache-2.0；图片与声音为独立素材，另计授权",
        "about_privacy_body" to "不联网，不收集数据，设置与预设只存在本机",
        "about_assets_body" to "声音与图片只在本机播放，不含在线内容",
        "about_offline_note" to "点按条目会复制内容或用系统应用打开，离线也可在此阅读",
        // 通知
        "notif_body" to "静坐中 · "
    )

    private val HANT = mapOf(
        "app_title" to "靜息",
        "state_idle" to "準備好了，隨時開始",
        "state_prep" to "準備中",
        "state_running" to "靜坐中",
        "state_paused" to "已暫停",
        "state_done" to "本次靜坐完成",
        "state_stopped" to "本次靜坐已結束",
        "hint_tap_ring" to "輕觸圓環改時長",
        "hint_tap_again" to "下方按鈕可再來一次",
        "btn_start" to "開始靜坐",
        "btn_again" to "再來一次",
        "btn_pause" to "暫停",
        "btn_resume" to "繼續",
        "btn_stop" to "結束",
        "section_duration" to "靜坐時長",
        "section_interval" to "提醒間隔",
        "section_prep" to "準備時間",
        "label_start_bell" to "開始鈴聲",
        "label_interval_bell" to "間隔鈴聲",
        "label_end_bell" to "結束鈴聲",
        "label_ambience" to "背景音",
        "off" to "關",
        "cancel" to "取消",
        "toast_running" to "靜坐進行中，結束後再調整",
        "picker_start" to "選擇開始鈴聲",
        "picker_interval" to "選擇間隔鈴聲",
        "picker_end" to "選擇結束鈴聲",
        "picker_ambience" to "選擇背景音",
        "catchup" to "本次靜坐已在背景結束，鈴聲未補響",
        "menu_presets" to "預設",
        "menu_settings" to "設定",
        "menu_about" to "關於",
        "title_presets" to "預設",
        "title_settings" to "設定",
        "title_about" to "關於",
        "btn_back" to "返回",
        "btn_confirm" to "儲存",
        "btn_delete" to "刪除",
        "settings_language" to "語言",
        "lang_hans" to "簡體",
        "lang_hant" to "繁體",
        "lang_en" to "English",
        "settings_language_note" to "首次跟隨系統，其他語言用 English，切換不中斷靜坐",
        "settings_bell_times" to "鈴聲右側的 ×N 可輕觸切換響鈴次數",
        "settings_notify" to "計時通知",
        "notify_on" to "通知已開啟",
        "notify_off" to "通知已關閉，鎖定畫面與狀態列都不顯示倒數",
        "notify_channel_off" to "計時通知管道已關閉",
        "notify_hint" to "點此開啟系統通知設定，可開啟鎖屏通知",
        "notify_live_off" to "未開啟系統「即時動態」，狀態列膠囊不會顯示",
        "preset_empty" to "還沒有儲存的預設",
        "preset_empty_hint" to "調好時長與鈴聲後，點下方按鈕儲存",
        "preset_save" to "儲存目前預設",
        "preset_dialog_title" to "為這個預設命名",
        "preset_name_hint" to "例如：晨間 20 分鐘",
        "preset_max" to "最多儲存 6 個預設",
        "preset_running_block" to "靜坐進行中，結束後再切換預設",
        "preset_applied" to "已套用",
        "preset_deleted" to "已刪除",
        "preset_saved" to "已儲存",
        "preset_prep" to "準備",
        "preset_times" to "響鈴",
        "about_tagline" to "若人靜坐一須臾，勝造恆沙七寶塔",
        "about_version" to "版本",
        "about_contact" to "聯絡作者",
        "about_repo" to "程式碼倉庫",
        "about_license" to "開源授權",
        "about_privacy" to "隱私權政策",
        "about_assets" to "素材說明",
        "about_contact_body" to "回饋、建議與合作，歡迎來信",
        "about_license_body" to "程式碼採用 Apache-2.0；圖片與聲音為獨立素材，另計授權",
        "about_privacy_body" to "不連網，不蒐集資料，設定與預設只存在本機",
        "about_assets_body" to "聲音與圖片只在本機播放，不含線上內容",
        "about_offline_note" to "點按條目會複製內容或用系統應用開啟，離線也可在此閱讀",
        "notif_body" to "靜坐中 · "
    )

    private val EN = mapOf(
        "app_title" to "zentimer",
        "state_idle" to "Ready when you are",
        "state_prep" to "Preparing",
        "state_running" to "Sitting",
        "state_paused" to "Paused",
        "state_done" to "Session complete",
        "state_stopped" to "Session ended",
        "hint_tap_ring" to "Tap the ring to set the time",
        "hint_tap_again" to "Use the button below to go again",
        "btn_start" to "Start",
        "btn_again" to "Go again",
        "btn_pause" to "Pause",
        "btn_resume" to "Resume",
        "btn_stop" to "End",
        "section_duration" to "Sitting time",
        "section_interval" to "Interval",
        "section_prep" to "Preparation",
        "label_start_bell" to "Start bell",
        "label_interval_bell" to "Interval bell",
        "label_end_bell" to "End bell",
        "label_ambience" to "Ambient sound",
        "off" to "Off",
        "cancel" to "Cancel",
        "toast_running" to "Adjust once this sitting ends",
        "picker_start" to "Start bell",
        "picker_interval" to "Interval bell",
        "picker_end" to "End bell",
        "picker_ambience" to "Ambient sound",
        "catchup" to "Session ended in the background; the bell was not replayed",
        "menu_presets" to "Presets",
        "menu_settings" to "Settings",
        "menu_about" to "About",
        "title_presets" to "Presets",
        "title_settings" to "Settings",
        "title_about" to "About",
        "btn_back" to "Back",
        "btn_confirm" to "Save",
        "btn_delete" to "Delete",
        "settings_language" to "Language",
        "lang_hans" to "简体中文",
        "lang_hant" to "繁體中文",
        "lang_en" to "English",
        "settings_language_note" to "System language first; others use English; switching never interrupts",
        "settings_bell_times" to "Tap ×N beside a bell to change the ring count",
        "settings_notify" to "Timer notification",
        "notify_on" to "Notifications are on",
        "notify_off" to "Notifications are off; no countdown on lock screen or status bar",
        "notify_channel_off" to "Timer notification channel is off",
        "notify_hint" to "Tap to open notification settings; allow lock-screen alerts",
        "notify_live_off" to "Live Updates are off; no status bar chip",
        "preset_empty" to "No saved presets yet",
        "preset_empty_hint" to "Set a duration and bells, then save with the button below",
        "preset_save" to "Save this preset",
        "preset_dialog_title" to "Name this preset",
        "preset_name_hint" to "e.g. Morning 20 min",
        "preset_max" to "Up to 6 presets",
        "preset_running_block" to "Switch presets after this sitting ends",
        "preset_applied" to "Applied",
        "preset_deleted" to "Deleted",
        "preset_saved" to "Saved",
        "preset_prep" to "Prep",
        "preset_times" to "Rings",
        "about_tagline" to "One moment of sitting still outweighs countless seven-jewel pagodas",
        "about_version" to "Version",
        "about_contact" to "Contact",
        "about_repo" to "Repository",
        "about_license" to "License",
        "about_privacy" to "Privacy",
        "about_assets" to "Assets notice",
        "about_contact_body" to "Feedback, ideas and collaboration welcome",
        "about_license_body" to "Code is Apache-2.0; images and sounds are licensed separately",
        "about_privacy_body" to "No network, no data collection; settings stay on your device",
        "about_assets_body" to "Sounds and images play locally only; no online content",
        "about_offline_note" to "Tapping a row copies text or opens a system app; the text is here offline too",
        "notif_body" to "Zen sitting · "
    )

    // ---------- v2.3 新增：三张表 key 严格一一对应 ----------
    // 支持作者（2026-11-10 v2.21 修订）：不内嵌支付、不放收款码、不唤起微信；
    // 关于页留一张带细边框的区域框，标题直接写「打赏作者」，点微信号那行复制纯 ID；
    // 反馈用行尾小字，不弹自定义气泡。v2.20 曾压成一行，因「一眼看不出是打赏」被推翻。

    private val EXTRA_HANS = mapOf(
        "back_again" to "再按一次退出",
        "preset_quick" to "常用预设",
        "preset_manage" to "管理",
        "preset_add" to "保存当前",
        "preset_quick_empty" to "调好一次后保存，下次一点即用",
        "picker_hint" to "点一下试听，可反复比较，按「确定」才生效",
        "picker_playing" to "试听中",
        "picker_selected" to "已选",
        "btn_apply" to "确定",
        "about_author" to "作者",
        "about_email" to "邮箱",
        "about_wechat" to "微信",
        "about_official" to "公众号",
        "about_repo_pending" to "代码仓库待定",
        "copied" to "已复制",
        "action_copy" to "复制",
        // v2.21：时长滚轮专用短单位与新人提示；打赏标题恢复，让这块区域一眼看懂是干嘛的。
        "unit_hour_short" to "时",
        "unit_min_short" to "分",
        "unit_sec_short" to "秒",
        // v2.22：显示态不再是大数字，改成整行可点；旧值「点数字可改」保留在
        // v2.21 的 UI 文案复核表 / 证据文档里，便于对照，不在这里改写历史。
        "duration_tap_hint" to "点这一行可改",
        "duration_editing" to "调好后点确定",
        // v2.24：四档快捷值之外的第五格，点它才展开滚轮。
        "duration_custom" to "自定义",
        "donate_title" to "打赏作者",
        "donate_body" to "若愿意，可加微信，金额随意，完全自愿",
        "donate_note" to "打赏完全自愿，不解锁任何功能，本应用不处理付款",
        "donate_hint" to "微信「+」→ 添加朋友 → 粘贴搜索",
        "preset_delete_confirm" to "删除这个预设？",
        "btn_delete_ok" to "删除",
        "settings_theme" to "外观",
        "theme_system" to "跟随系统",
        "theme_dark" to "深色",
        "theme_light" to "浅色",
        "theme_note" to "跟随系统时随手机深浅模式切换，切换不打断静坐",
        "lang_follow_system" to "跟随系统",
        "about_custom" to "定制与合作",
        "about_custom_body" to "寺院、道观等非营利组织可优惠或免费，其他用途另议",
        // v2.25 · 四时：不新增顶级设置项，并进「外观」组；文案里不出现季节名与节气名。
        "season_fine_tune" to "随节气微调",
        "season_note" to "只在下次打开时微调纸色，静坐中不改变",
        "season_note_running" to "静坐中不改变纸色",
        // v2.25 · 静坐时免打扰：统一说「勿扰权限」，不出现系统黑话。
        "dnd_title" to "静坐时免打扰",
        "dnd_sub" to "需一次系统授权，结束后自动停用",
        "dnd_denied" to "未获得勿扰权限，本次静坐不会屏蔽通知",
        "dnd_unsupported" to "当前系统版本不支持，开关不可用",
        "dnd_active" to "免打扰中",
        "dnd_rule_name" to "静坐",
        "switch_on" to "开",
        "switch_off" to "关"

    )

    private val EXTRA_HANT = mapOf(
        "back_again" to "再按一次退出",
        "preset_quick" to "常用預設",
        "preset_manage" to "管理",
        "preset_add" to "儲存目前",
        "preset_quick_empty" to "調好一次後儲存，下次一點即用",
        "picker_hint" to "點一下試聽，可反覆比較，按「確定」才生效",
        "picker_playing" to "試聽中",
        "picker_selected" to "已選",
        "btn_apply" to "確定",
        "about_author" to "作者",
        "about_email" to "信箱",
        "about_wechat" to "微信",
        "about_official" to "公眾號",
        "about_repo_pending" to "程式碼倉庫待定",
        "copied" to "已複製",
        "action_copy" to "複製",
        "unit_hour_short" to "時",
        "unit_min_short" to "分",
        "unit_sec_short" to "秒",
        "duration_tap_hint" to "點這一行可改",
        "duration_editing" to "調好後點確定",
        "duration_custom" to "自訂",
        "donate_title" to "贊助作者",
        "donate_body" to "若願意，可加微信，金額隨意，完全自願",
        "donate_note" to "贊助完全自願，不解鎖任何功能，本應用不處理付款",
        "donate_hint" to "微信「+」→ 新增朋友 → 貼上搜尋",
        "preset_delete_confirm" to "刪除這個預設？",
        "btn_delete_ok" to "刪除",
        "settings_theme" to "外觀",
        "theme_system" to "跟隨系統",
        "theme_dark" to "深色",
        "theme_light" to "淺色",
        "theme_note" to "跟隨系統時隨手機深淺模式切換，切換不中斷靜坐",
        "lang_follow_system" to "跟隨系統",
        "about_custom" to "客製與合作",
        "about_custom_body" to "寺院、道觀等非營利組織可優惠或免費，其他用途另議",
        "season_fine_tune" to "隨節氣微調",
        "season_note" to "只在下次開啟時微調紙色，靜坐中不改變",
        "season_note_running" to "靜坐中不改變紙色",
        "dnd_title" to "靜坐時免打擾",
        "dnd_sub" to "需一次系統授權，結束後自動停用",
        "dnd_denied" to "未取得勿擾權限，本次靜坐不會屏蔽通知",
        "dnd_unsupported" to "目前系統版本不支援，開關無法使用",
        "dnd_active" to "免打擾中",
        "dnd_rule_name" to "靜坐",
        "switch_on" to "開",
        "switch_off" to "關"

    )

    private val EXTRA_EN = mapOf(
        "back_again" to "Press back again to exit",
        "preset_quick" to "Quick presets",
        "preset_manage" to "Manage",
        "preset_add" to "Save current",
        "preset_quick_empty" to "Save a setup once; recall it with one tap",
        "picker_hint" to "Tap to preview, compare, then tap Apply",
        "picker_playing" to "Playing",
        "picker_selected" to "Selected",
        "btn_apply" to "Apply",
        "about_author" to "Author",
        "about_email" to "Email",
        "about_wechat" to "WeChat",
        "about_official" to "Official Account",
        "about_repo_pending" to "Repository · to be announced",
        "copied" to "Copied",
        "action_copy" to "Copy",
        "unit_hour_short" to "hr",
        "unit_min_short" to "min",
        "unit_sec_short" to "s",
        "duration_tap_hint" to "Tap the row to change it",
        "duration_editing" to "Set, then tap Apply",
        "duration_custom" to "Custom",
        "donate_title" to "Tip the author",
        "donate_body" to "If you wish, add me on WeChat; any amount, entirely optional",
        "donate_note" to "Entirely optional; unlocks nothing; this app never handles payment",
        "donate_hint" to "In WeChat: + → Add Contacts → paste and search",
        "preset_delete_confirm" to "Delete this preset?",
        "btn_delete_ok" to "Delete",
        "settings_theme" to "Appearance",
        "theme_system" to "System",
        "theme_dark" to "Dark",
        "theme_light" to "Light",
        "theme_note" to "Follows your device's light/dark mode; switching never interrupts",
        "lang_follow_system" to "Follow system",
        "about_custom" to "Custom builds",
        "about_custom_body" to "Non-profits free or discounted; other uses quoted separately",
        "season_fine_tune" to "Seasonal paper tone",
        "season_note" to "Paper tone changes only at next launch, never mid-sitting",
        "season_note_running" to "Paper tone stays put while sitting",
        "dnd_title" to "Do not disturb while sitting",
        "dnd_sub" to "Needs one system permission; off again when the sitting ends",
        "dnd_denied" to "No Do Not Disturb access; notifications stay on",
        "dnd_unsupported" to "Not available on this system version",
        "dnd_active" to "Do not disturb",
        "dnd_rule_name" to "Sitting",
        "switch_on" to "On",
        "switch_off" to "Off"

    )
}
