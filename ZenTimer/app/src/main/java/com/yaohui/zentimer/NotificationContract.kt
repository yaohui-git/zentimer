package com.yaohui.zentimer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.json.JSONObject

/**
 * 一条计时通知的完整组装，按「三段」拆开，任何一段缺席都只影响显示：
 *
 *  1. base     —— 所有安卓手机都拿到的完整标准通知（标题 / 系统走秒 / 锁屏可见 / 暂停·继续·结束）
 *  2. official —— Android 16 官方「实时动态」请求（状态栏胶囊 / 通知栏置顶）
 *  3. focus    —— 仅运行时自检通过时**追加**的 HyperOS 私有参数（动画沙漏 / AOD 状态词）
 *
 * 关键：第 3 段只是在 extras 里多放一个字符串，前两段一个字段都不会少。
 * 塞了系统不认，这条通知仍然是一条合法的标准通知 —— 这是跨厂商兼容的物理保障。
 *
 * shortCriticalText 按层裁决（08:18 修订，用户要求顶栏实时倒计时）：
 *  - L0 / L1：计时中一律不设。官方规定它优先级最高，会盖掉系统 chronometer 的走秒数字
 *    （E7 / E13）；遵守 AOSP 路径的 ROM 由 when + chronometer 自己驱动 chip。
 *  - L2（本机自检通过）：必须设。v2.14 对照证实这台 HyperOS 的胶囊不认 chronometer，
 *    只认 contentTitle / shortCriticalText；用户要「顶栏实时数字 + 卡片正文静坐中」，
 *    两者不能共用 contentTitle，只能由 applyFocusLayer() 每秒把 chip 字段写成剩余时间。
 * 这是按能力分层，不是推翻跨厂商裁决；StyleSelector.Decision 仍恒 false。
 * 暂停态的定格值走 contentText。
 */
object NotificationContract {

    private const val TAG = "ZenTimer"
    private const val REQ_OPEN = 1
    private const val REQ_STOP = 2
    private const val REQ_PAUSE = 3
    private const val REQ_RESUME = 4
    private const val KEY_FOCUS = "miui.focus.param"
    /** 官方 chip 文本字段；只在这台「胶囊不认 chronometer」的 ROM 上按能力启用。 */
    private const val KEY_SHORT_CRITICAL = "android.shortCriticalText"

    /** 顶栏胶囊要的实时倒计时文本（如 0:48）；暂停 / 完成态不给。 */
    private fun chipText(s: TimerEngine.Ui): String? {
        val ticking = s.phase == TimerEngine.Phase.RUNNING || s.phase == TimerEngine.Phase.PREP
        if (!ticking) return null
        val left = if (s.phase == TimerEngine.Phase.PREP) s.prepLeftMs else s.remainingMs
        return TimerEngine.formatTime(left)
    }

    /**
     * @param screenOn 亮屏才写 shortCriticalText（顶栏实时数字）。息屏时不写，
     *                 AOD 胶囊回落 param_v2.aodTitle「静坐中」。由 TimerService 的
     *                 SCREEN_ON/OFF 广播维护，两个胶囊共用同一 chip 字段所致。
     */
    fun build(ctx: Context, s: TimerEngine.Ui, caps: StyleSelector.Caps, screenOn: Boolean = true): Notification {
        // 现场自检：直接问「这条通知有没有被提升的资格」，不延时回读已发布的 flag。
        // 低版本 / @hide / 任何异常一律 false（H6、H12）。
        val promotable = probePromotable(ctx, s, caps.focusAvailable, screenOn)

        val decision = StyleSelector.pick(caps.copy(hasPromotableCharacteristics = promotable))

        // 用干净的 builder 重新组装：三段按顺序叠加，第 1 段永远完整。
        val b = buildBase(ctx, s)
        applyOfficialLayer(ctx, b, s, decision)
        applyFocusLayer(b, s, decision, screenOn)

        if (!decisionLogged) {
            decisionLogged = true
            Log.i(
                TAG,
                "notice layer=${decision.layer} promoted=${decision.requestPromoted} " +
                    "alarmFallback=${decision.useAlarmClockFallback} focus=${decision.appendFocusExtras} " +
                    "suppressed=${decision.displaySuppressed} sdk=${Build.VERSION.SDK_INT} " +
                    "reason=${decision.reason}"
            )
        }
        return b.build()
    }

    @Volatile
    private var decisionLogged = false

    // ---------- 第 1 段：基础字段，全机型一致 ----------

    private fun stateWord(s: TimerEngine.Ui): String = when (s.phase) {
        TimerEngine.Phase.PREP -> Lang.t("state_prep")
        TimerEngine.Phase.PAUSED -> Lang.t("state_paused")
        TimerEngine.Phase.RUNNING -> Lang.t("state_running")
        else -> Lang.t("state_idle")
    }

    private fun buildBase(ctx: Context, s: TimerEngine.Ui): Notification.Builder {
        val left = if (s.phase == TimerEngine.Phase.PREP) s.prepLeftMs else s.remainingMs
        val ticking = s.phase == TimerEngine.Phase.PREP || s.phase == TimerEngine.Phase.RUNNING
        val countdown = ticking
        val now = System.currentTimeMillis()

        // 计时中正文留空，数字只由系统那一份提供；暂停时才把定格值写进正文。
        val body = if (!ticking) TimerEngine.formatTime(left) else ""

        // 08:18 用户裁决：卡片正文只显示状态词「静坐中」，不再放第二组数字。
        // 顶栏的实时倒计时改由 shortCriticalText 单独喂（见 applyFocusLayer）：
        // 这台 ROM 的胶囊取 contentTitle / shortCriticalText，而 contentTitle 同时又是
        // 下拉卡片正文 —— 同一个字段做不到「胶囊是实时数字、正文是文字」。
        val b = Notification.Builder(ctx, TimerService.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(stateWord(s))
            .setContentText(body)
            .setContentIntent(openIntent(ctx))
            .setOngoing(true)
            .setAutoCancel(false)
            .setLocalOnly(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setUsesChronometer(ticking)
            .setChronometerCountDown(countdown)
            .setWhen(if (ticking) now + left else 0L)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        if (s.phase == TimerEngine.Phase.PAUSED) {
            b.addAction(R.drawable.ic_stat_timer, Lang.t("btn_resume"), actionIntent(ctx, TimerService.ACTION_RESUME, REQ_RESUME))
        } else if (ticking) {
            b.addAction(R.drawable.ic_stat_timer, Lang.t("btn_pause"), actionIntent(ctx, TimerService.ACTION_PAUSE, REQ_PAUSE))
        }
        b.addAction(R.drawable.ic_stat_timer, Lang.t("btn_stop"), actionIntent(ctx, TimerService.ACTION_STOP, REQ_STOP))
        return b
    }

    // ---------- 第 2 段：Android 16 官方实时动态 ----------

    /**
     * 自检必须拿「已经申请过 promotion 的通知」去问，否则问的是一条从来没申请过的通知，
     * 恒为 false —— v2.12 真机日志 `notification not promotable` 就是这个自证循环。
     * 同时打一组对照（裸通知 / 已申请），把结论留在 logcat 里可复核。
     */
    private fun probePromotable(ctx: Context, s: TimerEngine.Ui, focusOn: Boolean, screenOn: Boolean): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < 36) return@runCatching false
        // 不直接调方法：出厂版本里可能是 @hide，直接调会 NoSuchMethodError 崩进程（v2.9 实事故）。
        val m = Notification::class.java.getMethod("hasPromotableCharacteristics")
        fun check(withRequest: Boolean): Boolean = runCatching {
            val b = buildBase(ctx, s)
            if (withRequest) requestPromotion(b)
            // E17 的教训：自检对象必须和真正发布的对象一致，chip 文本也要一起带上。
            if (focusOn && screenOn) chipText(s)?.let { b.extras.putString(KEY_SHORT_CRITICAL, it) }
            m.invoke(b.build()) as? Boolean ?: false
        }.getOrDefault(false)
        val plain = check(false)
        val requested = check(true)
        if (!probeLogged) {
            probeLogged = true
            Log.i(TAG, "promotable probe plain=$plain requested=$requested")
        }
        requested
    }.getOrDefault(false)

    @Volatile
    private var probeLogged = false

    /** 能反射到公开方法就调，拿不到就退回官方 extras 常量通道；chip 文本由各层自己决定。 */
    private fun requestPromotion(b: Notification.Builder) {
        val byMethod = runCatching {
            b.javaClass
                .getMethod("setRequestPromotedOngoing", java.lang.Boolean.TYPE)
                .invoke(b, true)
            true
        }.getOrDefault(false)
        if (!byMethod) {
            b.extras.putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true)
        }
    }

    private fun applyOfficialLayer(ctx: Context, b: Notification.Builder, s: TimerEngine.Ui, d: StyleSelector.Decision) {
        if (d.requestPromoted) requestPromotion(b)
        if (d.useAlarmClockFallback && s.phase == TimerEngine.Phase.RUNNING) {
            applyAlarmClock(ctx, b, s.remainingMs)
        }
    }

    /** 老式 alarm clock 通知：Android 12~15 靠它上锁屏、进状态栏。反射调用，失败静默。 */
    private fun applyAlarmClock(ctx: Context, b: Notification.Builder, left: Long): Boolean = runCatching {
        val infoCls = Class.forName("android.app.Notification\$AlarmClockInfo")
        val show = openIntent(ctx)
        val info = infoCls
            .getConstructor(java.lang.Long.TYPE, PendingIntent::class.java)
            .newInstance(System.currentTimeMillis() + left, show)
        b.javaClass
            .getMethod("setAlarmClock", infoCls, PendingIntent::class.java)
            .invoke(b, info, show)
        true
    }.getOrDefault(false)

    // ---------- 第 3 段：HyperOS 私有增强（只追加，不替代） ----------

    /**
     * 字段与层级全部照 `证据核查-2026-11-08.md` E12 抓到的系统闹钟 `dumpsys` 一手 JSON：
     * `param_island` 是 **param_v2 的子对象**（v2.13 误放在顶层），走秒数字由
     * `timerWhen / timerSystemCurrent / timerTotal` + `sameWidthDigitInfo.timerInfo` 提供，
     * SystemUI 自己按这三个值算秒，App 不需要每秒重建通知（E12 结论 3）。
     *
     * 用户 11-09 07:54 裁决：「冥想中」只留在充电锁屏（AOD 的 aodTitle），
     * 顶栏胶囊与通知栏下拉都必须显示倒计时。所以这里只补数字字段，不动 AOD 文案。
     *
     * **SystemUI 是否真为第三方渲染仍未证实**，这一段仍是可选增益；系统完全忽略时，
     * 前两段照旧是完整合法的通知，计时与响铃不受影响。
     */
    private fun applyFocusLayer(
        b: Notification.Builder,
        s: TimerEngine.Ui,
        d: StyleSelector.Decision,
        screenOn: Boolean
    ) {
        if (!d.appendFocusExtras) return
        // 08:18 用户：「顶栏里显示 OK，但是倒计时不是实时的，不对，要实时的」。
        // 这台 ROM 的胶囊不认系统 chronometer，官方 chip 字段 shortCriticalText 是唯一能把
        // 数字送进胶囊的通道。只在 L2（自检通过）启用；L0 / L1 仍「计时中一律不设」，
        // 让遵守 AOSP 路径的 ROM 继续由 when + chronometer 自己走秒。
        // 08:31 补充：AOD 胶囊与顶栏胶囊共用这个字段，息屏时必须去掉，
        // 否则充电锁屏会显示倒计时数字而不是「时钟 LOGO + 静坐中」。
        if (screenOn) chipText(s)?.let { b.extras.putString(KEY_SHORT_CRITICAL, it) }
        val ticking = s.phase == TimerEngine.Phase.RUNNING
        val now = System.currentTimeMillis()
        val whenEnd = now + s.remainingMs

        fun timerFields(): JSONObject = JSONObject()
            .put("timerWhen", whenEnd)
            .put("timerSystemCurrent", now)
            .put("timerTotal", s.totalMs)

        val json = runCatching {
            JSONObject().apply {
                put("protocol", 1)
                put("scene", "timer")
                if (ticking) {
                    put("timerWhen", whenEnd)
                    put("timerSystemCurrent", now)
                    put("timerTotal", s.totalMs)
                }
                put(
                    "param_v2",
                    JSONObject()
                        // 充电锁屏 / AOD：只给 LOGO + 状态词「静坐中」，不给任何数字（08:18 裁决）。
                        .put("aodTitle", Lang.t("state_running"))
                        .put("aodPic", "miui.focus.pic_ticker")
                        .put("picFunction", "miui.focus.pic_timer")
                        // 沙漏资源名来自 E12 系统闹钟一手 JSON；由 SystemUI 自己播放，App 不画图。
                        .put(
                            "animTextInfo",
                            JSONObject().put(
                                "animIconInfo",
                                JSONObject().put("type", 2).put("src", "hourglass_big").put("autoplay", true)
                            )
                        )
                        .put("business", "countdown")
                        .put("islandFirstFloat", true)
                        .put(
                            "param_island",
                            JSONObject()
                                .put(
                                    "bigIslandArea",
                                    JSONObject().put(
                                        "imageTextInfoLeft",
                                        JSONObject()
                                            .put("type", 1)
                                            .put(
                                                "picInfo",
                                                JSONObject().put("type", 2)
                                                    .put("pic", "hourglass").put("autoplay", true)
                                            )
                                            .apply {
                                                if (ticking) {
                                                    put(
                                                        "sameWidthDigitInfo",
                                                        JSONObject().put(
                                                            "timerInfo",
                                                            timerFields().put("timerType", 0)
                                                        )
                                                    )
                                                }
                                            }
                                    )
                                )
                                .put(
                                    "smallIslandArea",
                                    JSONObject().put(
                                        "picInfo",
                                        JSONObject().put("type", 2)
                                            .put("pic", "hourglass").put("autoplay", true)
                                    )
                                )
                        )
                )
            }.toString()
        }.getOrNull() ?: return
        b.extras.putString(KEY_FOCUS, json)
    }

    // ---------- intents ----------

    private fun pendingFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

    private fun openIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(ctx, REQ_OPEN, i, pendingFlags())
    }

    private fun actionIntent(ctx: Context, action: String, req: Int): PendingIntent {
        val i = Intent(ctx, TimerService::class.java).setAction(action)
        return PendingIntent.getService(ctx, req, i, pendingFlags())
    }
}
