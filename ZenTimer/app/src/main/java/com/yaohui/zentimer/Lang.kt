package com.yaohui.zentimer

import android.content.Context
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

    fun init(ctx: Context) {
        val prefs = ctx.prefs()
        val saved = prefs.getString(KEY_LANG, null)
        val manual = prefs.getBoolean(KEY_LANG_MANUAL, false)
        _mode.value = if (manual && saved != null) {
            // 用户手动选过：以选择为准，系统语言变了也不覆盖
            when (saved) {
                "hans", "zh" -> Mode.HANS
                "hant" -> Mode.HANT
                "en" -> Mode.EN
                else -> defaultFromSystem(ctx)
            }
        } else {
            // 没手动选过：每次启动都按当前系统语言重算，真正做到「默认跟随系统」
            defaultFromSystem(ctx).also { prefs.edit().putString(KEY_LANG, it.code).apply() }
        }
    }

    /**
     * 默认语言跟随系统：简体中文 → HANS，繁体中文 → HANT，
     * 其余所有语言（English、日语、法语、韩语……）一律 EN，让国际友人开箱即用。
     */
    private fun defaultFromSystem(ctx: Context): Mode {
        // 用 App 自己的 resources 配置，而不是 Resources.getSystem()：
        // 后者的 locale 在部分 ROM / 冷启动早期并不可靠，会出现中文系统被判成英文。
        val locales = ctx.resources.configuration.locales
        val l = if (locales.isEmpty) Locale.getDefault() else locales[0]
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
            .apply()
    }

    /** 设置页「跟随系统」：清掉手动标记，立即按系统语言重新判定。 */
    fun followSystem(ctx: Context) {
        val m = defaultFromSystem(ctx)
        _mode.value = m
        ctx.prefs().edit()
            .putBoolean(KEY_LANG_MANUAL, false)
            .putString(KEY_LANG, m.code)
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
                    h < 18 -> "Good afternoon"
                    else -> "Good evening"
                }
                "$hi · " + d.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
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
                    h < 11 -> "早上好"
                    h < 14 -> "中午好"
                    h < 18 -> "下午好"
                    else -> "晚上好"
                }
                val week = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA)
                "$hi · " + d.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)) + " $week"
            }
        }
    }

    fun minutes(n: Int): String {
        if (n < 0) return t("infinite")
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
    fun infinite(): String = t("infinite")

    private val HANS = mapOf(
        "app_title" to "静息",
        "state_idle" to "准备好了，随时开始",
        "state_prep" to "准备中",
        "state_running" to "冥想中",
        "state_paused" to "已暂停",
        "state_done" to "本次练习完成",
        "state_stopped" to "已结束本次练习",
        "hint_tap_ring" to "轻触圆环开始",
        "hint_tap_again" to "轻触圆环可再来一次",
        "btn_start" to "开始冥想",
        "btn_pause" to "暂停",
        "btn_resume" to "继续",
        "btn_stop" to "结束",
        "section_duration" to "时长",
        "section_interval" to "间隔铃",
        "section_prep" to "准备时间",
        "label_start_bell" to "开始铃声",
        "label_interval_bell" to "间隔铃声",
        "label_end_bell" to "结束铃声",
        "label_ambience" to "背景音",
        "off" to "关",
        "cancel" to "取消",
        "toast_running" to "练习进行中，结束后再调整",
        "picker_start" to "选择开始铃声",
        "picker_interval" to "选择间隔铃声",
        "picker_end" to "选择结束铃声",
        "picker_ambience" to "选择背景音",
        "infinite" to "无限",
        "state_infinite" to "无时限 · 自由冥想",
        "catchup" to "本次练习已在后台结束，铃声补响",
        // 菜单与三个子页
        "menu_presets" to "预设",
        "menu_settings" to "设置",
        "menu_about" to "关于",
        "title_presets" to "预设组合",
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
        "settings_language_note" to "首次默认跟随系统：简体 / 繁体 / 其他语言为 English；切换不打断练习",
        "settings_bell_times" to "铃声右侧的 ×N 可轻触切换响铃次数",
        "settings_notify" to "计时通知",
        "notify_on" to "通知已开启",
        "notify_off" to "通知已关闭，锁屏与状态栏都不会显示倒计时",
        "notify_channel_off" to "计时通知渠道被关闭",
        "notify_hint" to "点此打开系统通知设置，可开启锁屏通知",
        "notify_live_off" to "系统的「实时动态」未开启，状态栏倒计时胶囊不会出现",
        // 预设
        "preset_empty" to "还没有保存的组合",
        "preset_empty_hint" to "调好时长与铃声后，点下方按钮保存",
        "preset_save" to "保存当前组合",
        "preset_dialog_title" to "给这个组合起个名字",
        "preset_name_hint" to "例如：晨间 20 分钟",
        "preset_max" to "最多保存 6 个组合",
        "preset_running_block" to "练习进行中，结束后再切换组合",
        "preset_applied" to "已应用",
        "preset_deleted" to "已删除",
        "preset_saved" to "已保存",
        "preset_prep" to "准备",
        "preset_times" to "响铃",
        // 关于
        "about_tagline" to "若人静坐一须臾，胜造恒沙七宝塔",
        "about_version" to "版本",
        "about_contact" to "联系作者",
        "about_repo" to "开源地址",
        "about_license" to "开源许可",
        "about_privacy" to "隐私政策",
        "about_assets" to "素材说明",
        "about_contact_body" to "反馈、建议与合作，欢迎来信",
        "about_license_body" to "自写代码采用 Apache-2.0 许可。应用内图片与声音为独立素材，不在开源许可范围内。",
        "about_privacy_body" to "不联网、不收集任何数据；所有设置与预设只存在本机。",
        "about_assets_body" to "声音与图片素材仅用于本机播放，不含任何在线内容服务。",
        "about_offline_note" to "点按上方条目会在系统应用中打开；离线时内容仍可在此阅读。",
        // 通知
        "notif_body" to "冥想进行中 · "
    )

    private val HANT = mapOf(
        "app_title" to "靜息",
        "state_idle" to "準備好了，隨時開始",
        "state_prep" to "準備中",
        "state_running" to "冥想中",
        "state_paused" to "已暫停",
        "state_done" to "本次練習完成",
        "state_stopped" to "已結束本次練習",
        "hint_tap_ring" to "輕觸圓環開始",
        "hint_tap_again" to "輕觸圓環可再來一次",
        "btn_start" to "開始冥想",
        "btn_pause" to "暫停",
        "btn_resume" to "繼續",
        "btn_stop" to "結束",
        "section_duration" to "時長",
        "section_interval" to "間隔鈴",
        "section_prep" to "準備時間",
        "label_start_bell" to "開始鈴聲",
        "label_interval_bell" to "間隔鈴聲",
        "label_end_bell" to "結束鈴聲",
        "label_ambience" to "背景音",
        "off" to "關",
        "cancel" to "取消",
        "toast_running" to "練習進行中，結束後再調整",
        "picker_start" to "選擇開始鈴聲",
        "picker_interval" to "選擇間隔鈴聲",
        "picker_end" to "選擇結束鈴聲",
        "picker_ambience" to "選擇背景音",
        "infinite" to "無限",
        "state_infinite" to "無時限 · 自由冥想",
        "catchup" to "本次練習已在背景結束，鈴聲補響",
        "menu_presets" to "預設",
        "menu_settings" to "設定",
        "menu_about" to "關於",
        "title_presets" to "預設組合",
        "title_settings" to "設定",
        "title_about" to "關於",
        "btn_back" to "返回",
        "btn_confirm" to "儲存",
        "btn_delete" to "刪除",
        "settings_language" to "語言",
        "lang_hans" to "簡體",
        "lang_hant" to "繁體",
        "lang_en" to "English",
        "settings_language_note" to "首次預設跟隨系統：簡體 / 繁體 / 其他語言為 English；切換不中斷練習",
        "settings_bell_times" to "鈴聲右側的 ×N 可輕觸切換響鈴次數",
        "settings_notify" to "計時通知",
        "notify_on" to "通知已開啟",
        "notify_off" to "通知已關閉，鎖定畫面與狀態列都不會顯示倒數",
        "notify_channel_off" to "計時通知管道被關閉",
        "notify_hint" to "點此開啟系統通知設定，可開啟鎖屏通知",
        "notify_live_off" to "系統的「即時動態」未開啟，狀態列倒數標籤不會出現",
        "preset_empty" to "還沒有儲存的組合",
        "preset_empty_hint" to "調好時長與鈴聲後，點下方按鈕儲存",
        "preset_save" to "儲存目前組合",
        "preset_dialog_title" to "為這個組合命名",
        "preset_name_hint" to "例如：晨間 20 分鐘",
        "preset_max" to "最多儲存 6 個組合",
        "preset_running_block" to "練習進行中，結束後再切換組合",
        "preset_applied" to "已套用",
        "preset_deleted" to "已刪除",
        "preset_saved" to "已儲存",
        "preset_prep" to "準備",
        "preset_times" to "響鈴",
        "about_tagline" to "若人靜坐一須臾，勝造恆沙七寶塔",
        "about_version" to "版本",
        "about_contact" to "聯絡作者",
        "about_repo" to "開源位址",
        "about_license" to "開源授權",
        "about_privacy" to "隱私權政策",
        "about_assets" to "素材說明",
        "about_contact_body" to "回饋、建議與合作，歡迎來信",
        "about_license_body" to "自撰程式碼採用 Apache-2.0 授權。應用內圖片與聲音為獨立素材，不在開源授權範圍內。",
        "about_privacy_body" to "不連網、不蒐集任何資料；所有設定與預設只存在本機。",
        "about_assets_body" to "聲音與圖片素材僅供本機播放，不含任何線上內容服務。",
        "about_offline_note" to "點按上方條目會在系統應用中開啟；離線時內容仍可在此閱讀。",
        "notif_body" to "冥想進行中 · "
    )

    private val EN = mapOf(
        "app_title" to "zentimer",
        "state_idle" to "Ready when you are",
        "state_prep" to "Get ready",
        "state_running" to "Meditating",
        "state_paused" to "Paused",
        "state_done" to "Session complete",
        "state_stopped" to "Session ended",
        "hint_tap_ring" to "Tap the ring to begin",
        "hint_tap_again" to "Tap the ring to go again",
        "btn_start" to "Start",
        "btn_pause" to "Pause",
        "btn_resume" to "Resume",
        "btn_stop" to "End",
        "section_duration" to "Duration",
        "section_interval" to "Interval bell",
        "section_prep" to "Preparation",
        "label_start_bell" to "Start bell",
        "label_interval_bell" to "Interval bell sound",
        "label_end_bell" to "End bell",
        "label_ambience" to "Ambient sound",
        "off" to "Off",
        "cancel" to "Cancel",
        "toast_running" to "Adjust after this session ends",
        "picker_start" to "Start bell",
        "picker_interval" to "Interval bell",
        "picker_end" to "End bell",
        "picker_ambience" to "Ambient sound",
        "infinite" to "Endless",
        "state_infinite" to "Open-ended · free meditation",
        "catchup" to "Session ended in the background · bell replayed",
        "menu_presets" to "Presets",
        "menu_settings" to "Settings",
        "menu_about" to "About",
        "title_presets" to "Saved presets",
        "title_settings" to "Settings",
        "title_about" to "About",
        "btn_back" to "Back",
        "btn_confirm" to "Save",
        "btn_delete" to "Delete",
        "settings_language" to "Language",
        "lang_hans" to "简体中文",
        "lang_hant" to "繁體中文",
        "lang_en" to "English",
        "settings_language_note" to "Follows the system on first launch; other locales default to English. Switching never interrupts a session.",
        "settings_bell_times" to "Tap ×N next to a bell to change how many times it rings",
        "settings_notify" to "Timer notification",
        "notify_on" to "Notifications are on",
        "notify_off" to "Notifications are off — the countdown won't appear on the lock screen or status bar",
        "notify_channel_off" to "The timer notification channel is off",
        "notify_hint" to "Tap to open system notification settings and allow lock screen alerts",
        "notify_live_off" to "System Live Updates are off, so the status bar chip won't appear",
        "preset_empty" to "No saved combinations yet",
        "preset_empty_hint" to "Set a duration and bells, then save below",
        "preset_save" to "Save current setup",
        "preset_dialog_title" to "Name this preset",
        "preset_name_hint" to "e.g. Morning 20 min",
        "preset_max" to "Up to 6 presets",
        "preset_running_block" to "Switch presets after this session ends",
        "preset_applied" to "Applied",
        "preset_deleted" to "Deleted",
        "preset_saved" to "Saved",
        "preset_prep" to "Prep",
        "preset_times" to "Rings",
        "about_tagline" to "A moment of still sitting surpasses seven-jewelled pagodas beyond count.",
        "about_version" to "Version",
        "about_contact" to "Contact",
        "about_repo" to "Source code",
        "about_license" to "License",
        "about_privacy" to "Privacy",
        "about_assets" to "Assets notice",
        "about_contact_body" to "Feedback, ideas and collaboration are welcome",
        "about_license_body" to "Our own code is Apache-2.0 licensed. Images and sounds are separate assets, not covered by the code license.",
        "about_privacy_body" to "No network access, no data collection. Settings and presets stay on your device.",
        "about_assets_body" to "Sounds and images are played locally only. No online content service is included.",
        "about_offline_note" to "Tapping a row opens the system app; everything is readable here offline too.",
        "notif_body" to "Meditating · "
    )

    // ---------- v2.3 新增：三张表 key 严格一一对应 ----------
    // 打赏按用户决定简化为「加微信打赏」：不内嵌支付、不放收款码，只复制微信号 + 唤起微信。

    private val EXTRA_HANS = mapOf(
        "back_again" to "再按一次退出",
        "preset_quick" to "常用预设",
        "preset_manage" to "管理",
        "preset_add" to "保存当前",
        "preset_quick_empty" to "调好一套后保存，以后一点就能用",
        "picker_hint" to "点一下试听，可反复比较；按「确定」才生效",
        "picker_playing" to "试听中",
        "picker_selected" to "已选",
        "btn_apply" to "确定",
        "about_author" to "作者",
        "about_email" to "邮箱",
        "about_wechat" to "微信",
        "about_official" to "公众号",
        "about_repo_pending" to "开源地址待定",
        "copied" to "已复制",
        "donate_entry" to "打赏作者",
        "donate_title" to "加微信打赏",
        "donate_body" to "想打赏的话，加微信就好，金额随意、完全自愿。",
        "donate_open_wechat" to "打开微信",
        "donate_copy_wechat" to "复制微信号",
        "donate_note" to "打赏完全自愿，不解锁任何功能；本应用不处理任何付款。",
        "donate_open_failed" to "没有找到微信，微信号已复制",
        "donate_hint" to "微信号已复制，打开微信粘贴搜索即可添加",
        "preset_delete_confirm" to "删除这个组合？",
        "btn_delete_ok" to "删除",
        "settings_theme" to "主题色",
        "theme_system" to "跟随系统",
        "theme_dark" to "深色",
        "theme_light" to "浅色",
        "theme_note" to "跟随系统时随手机深浅模式自动切换；切换不打断练习",
        "lang_follow_system" to "跟随系统",
        "about_custom" to "定制与合作",
        "about_custom_body" to "寺院、道观等非营利组织可优惠或免费定制；其他用途另议，欢迎来信。"

    )

    private val EXTRA_HANT = mapOf(
        "back_again" to "再按一次退出",
        "preset_quick" to "常用預設",
        "preset_manage" to "管理",
        "preset_add" to "儲存目前",
        "preset_quick_empty" to "調好一套後儲存，以後一點就能用",
        "picker_hint" to "點一下試聽，可反覆比較；按「確定」才生效",
        "picker_playing" to "試聽中",
        "picker_selected" to "已選",
        "btn_apply" to "確定",
        "about_author" to "作者",
        "about_email" to "信箱",
        "about_wechat" to "微信",
        "about_official" to "公眾號",
        "about_repo_pending" to "開源位址待定",
        "copied" to "已複製",
        "donate_entry" to "贊助作者",
        "donate_title" to "加微信贊助",
        "donate_body" to "想贊助的話，加微信就好，金額隨意、完全自願。",
        "donate_open_wechat" to "開啟微信",
        "donate_copy_wechat" to "複製微信號",
        "donate_note" to "贊助完全自願，不解鎖任何功能；本應用不處理任何付款。",
        "donate_open_failed" to "找不到微信，微信號已複製",
        "donate_hint" to "微信號已複製，開啟微信貼上搜尋即可加入",
        "preset_delete_confirm" to "刪除這個組合？",
        "btn_delete_ok" to "刪除",
        "settings_theme" to "主題色",
        "theme_system" to "跟隨系統",
        "theme_dark" to "深色",
        "theme_light" to "淺色",
        "theme_note" to "跟隨系統時隨手機深淺模式自動切換；切換不中斷練習",
        "lang_follow_system" to "跟隨系統",
        "about_custom" to "客製與合作",
        "about_custom_body" to "寺院、道觀等非營利組織可優惠或免費客製；其他用途另議，歡迎來信。"

    )

    private val EXTRA_EN = mapOf(
        "back_again" to "Press back again to exit",
        "preset_quick" to "Quick presets",
        "preset_manage" to "Manage",
        "preset_add" to "Save current",
        "preset_quick_empty" to "Save a setup once, then recall it with one tap",
        "picker_hint" to "Tap to preview. Compare freely, then tap Apply.",
        "picker_playing" to "Playing",
        "picker_selected" to "Selected",
        "btn_apply" to "Apply",
        "about_author" to "Author",
        "about_email" to "Email",
        "about_wechat" to "WeChat",
        "about_official" to "Official Account",
        "about_repo_pending" to "Source code · to be announced",
        "copied" to "Copied",
        "donate_entry" to "Support the author",
        "donate_title" to "Tip via WeChat",
        "donate_body" to "Want to leave a tip? Add the WeChat ID below. Any amount, entirely optional.",
        "donate_open_wechat" to "Open WeChat",
        "donate_copy_wechat" to "Copy WeChat ID",
        "donate_note" to "Entirely optional. Unlocks nothing. This app never handles a payment.",
        "donate_open_failed" to "WeChat not found — the ID has been copied",
        "donate_hint" to "WeChat ID copied — paste it in WeChat to add the author",
        "preset_delete_confirm" to "Delete this preset?",
        "btn_delete_ok" to "Delete",
        "settings_theme" to "Theme",
        "theme_system" to "System",
        "theme_dark" to "Dark",
        "theme_light" to "Light",
        "theme_note" to "Follows your device dark mode. Switching never interrupts a session.",
        "lang_follow_system" to "Follow system",
        "about_custom" to "Custom builds",
        "about_custom_body" to "Temples, monasteries and other non-profits: discounted or free. Other uses quoted individually."

    )
}
