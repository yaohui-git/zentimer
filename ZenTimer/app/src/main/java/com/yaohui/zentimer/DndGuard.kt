package com.yaohui.zentimer

import android.app.AlarmManager
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 静坐时免打扰：只激活与停用「属于本应用的一条 AutomaticZenRule」。
 *
 * 底线（用户 11-10 23:20 原话）：「要避免程序退出后，系统的静音等配置没有复原，影响正常使用。
 * 这个是底线」。因此本文件从头到尾没有「把原值存下来、用完写回去」这条必然漏的路径：
 *
 *  - 绝不调用 setInterruptionFilter / setNotificationPolicy 改全局勿扰状态。用户原本开着
 *    勿扰也好、静坐中手动关掉也好，系统按「最严格政策优先」合并规则，我们只管自己这条；
 *  - 规则激活状态存在系统侧，进程被杀不会自动消失，所以做了四道复原：
 *      ① 正常结算 / 提前结束 —— TimerEngine 两条路径都调 onSittingEnd；
 *      ② 进程被杀且用户没再打开 App —— 起跑时挂一条 AlarmManager 兜底广播，到点无条件停用；
 *      ③ 冷启动 / 强杀后重新打开 —— ZenApp.onCreate 无条件停用一次；
 *      ④ 手机重启（用户不打开 App 也算）—— DndRestoreReceiver 收 BOOT_COMPLETED / 包替换，
 *         同样无条件停用。停用是幂等的，多跑几次不会替用户打开任何东西。
 *
 * API 边界（本机 android.jar javap 实证，非凭印象）：setAutomaticZenRuleState(String, Condition)
 * 自 API 29 起可用，ZenPolicy / Condition 同代；本机真机 Android 12 = API 31 支持。
 * minSdk 26 的 26–28 没有这套接口，功能整体降级为「开关置灰」，不碰任何系统状态。
 * Android 15（targetSdk 35+）起全局勿扰 API 会退化成隐式规则，正好是这里禁用的两个方法，
 * 所以不存在版本分叉。
 *
 * 任何失败（无权限、OEM 拦截、规则创建失败、被撤销权限）都只记日志并继续计时，**静坐优先**。
 */
object DndGuard {

    private const val TAG = "ZT_DND"

    const val KEY_ENABLED = "dnd_enabled"
    private const val KEY_RULE_ID = "dnd_rule_id"
    private const val KEY_ACTIVE = "dnd_rule_active"
    const val ACTION_RESTORE = "com.yaohui.zentimer.DND_RESTORE"
    private const val CONDITION_PATH = "sitting"

    /** 在正常结束时刻之后再留 5 分钟，给结算与响铃让路，同时兜住进程被杀。 */
    private const val ALARM_GRACE_MS = 5 * 60 * 1000L
    private const val ALARM_MIN_AHEAD_MS = 60 * 1000L

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    /** API 29 以下没有激活自有规则的公开接口，整块功能不可用。 */
    fun available(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun granted(ctx: Context): Boolean = available() && runCatching {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.isNotificationPolicyAccessGranted() == true
    }.getOrElse {
        Log.w(TAG, "policy access check failed: ${it.javaClass.simpleName}")
        false
    }

    /** 默认关闭：是否替自己关门，由用户自己决定。 */
    fun enabled(ctx: Context): Boolean = ctx.prefs().getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.prefs().edit().putBoolean(KEY_ENABLED, on).apply()
        if (!on) deactivate(ctx)          // 静坐中主动关掉也立刻复原，但不阻断计时
    }

    /** 与设置页开关状态对账：撤销权限后开关弹回关闭。 */
    fun effectiveOn(ctx: Context): Boolean = enabled(ctx) && granted(ctx)

    /**
     * 起跑：创建 / 复用规则 → 激活 → 挂兜底闹钟。
     * [plannedEndWall] 由 TimerEngine 在 start() 里一次算好（含准备时间）。
     */
    fun onSittingStart(ctx: Context, plannedEndWall: Long) {
        if (!available() || !enabled(ctx) || !granted(ctx)) return
        val id = ensureRule(ctx) ?: return
        if (!setState(ctx, id, true)) {
            Log.w(TAG, "activate failed; sitting continues without DND")
            return
        }
        ctx.prefs().edit().putBoolean(KEY_ACTIVE, true).apply()
        _active.value = true
        armFallback(ctx, plannedEndWall)
    }

    /** 结束：正常结算、提前结束、用户关开关、启动自愈、闹钟兜底，全走这一个入口。 */
    fun onSittingEnd(ctx: Context) = deactivate(ctx)

    /** 启动自愈：与 onSittingEnd 同一条路径，语义上单列一个名字便于闸门与验收指认。 */
    fun reconcileOnStartup(ctx: Context) = deactivate(ctx)

    private fun deactivate(ctx: Context) {
        cancelFallback(ctx)
        if (available()) {
            // 不把「有没有勿扰权限」当成跳过复原的理由：用户在静坐中撤销授权时，
            // 我们仍要尽最大努力把规则停掉。先用本地存的 id 无条件试一次（撤销权限后
            // 系统仍可能允许所有者操作自己的规则），再扫一遍系统规则表兜漏网的那条。
            ctx.prefs().getString(KEY_RULE_ID, null)?.takeIf { it.isNotEmpty() }?.let {
                setState(ctx, it, false)
            }
            findRuleId(ctx)?.let { setState(ctx, it, false) }
        }
        ctx.prefs().edit().putBoolean(KEY_ACTIVE, false).apply()
        _active.value = false
    }

    /**
     * 规则只建一条。先认本地存的 id（要确认系统里还在），否则按 owner 包名字扫一遍复用，
     * 都没有才 addAutomaticZenRule。这样「结束立刻再开始」「卸载重装」都不会叠出第二条。
     */
    @Suppress("DEPRECATION")
    // 5 参构造从 API 24 就有，虽在 API 37 标了 deprecated，却是 29~34 上唯一可用的构造；
    // 含 ZenPolicy 的 7 参构造是 API 35+，直接用会在旧机上 NoSuchMethodError。
    // 所以保持「5 参构造 + setZenPolicy」这一组，兼容性与新策略都拿到。
    private fun ensureRule(ctx: Context): String? {
        findRuleId(ctx)?.let { return it }
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return null
        val rule = runCatching {
            AutomaticZenRule(
                Lang.t("dnd_rule_name"),
                ComponentName(ctx, MainActivity::class.java),
                conditionUri(ctx),
                NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                true
            ).apply {
                setZenPolicy(policy())
                // API 29 起 ConditionProviderService 已废弃，系统改为要求规则绑定一个
                // 可用的配置 Activity，否则 addAutomaticZenRule 直接抛
                // IllegalArgumentException: Lacking enabled CPS or config activity。
                // 真机 11-11 00:12 实测踩到；这条不是可选项，是系统的硬校验。
                // 指向主界面而不是新建页面，用户在系统「模式」列表点「静坐」就回到 App 设置。
                setConfigurationActivity(ComponentName(ctx, MainActivity::class.java))
            }
        }.getOrElse {
            Log.w(TAG, "build rule failed: ${it.javaClass.simpleName}: ${it.message}")
            return null
        }
        val id = runCatching { nm.addAutomaticZenRule(rule) }.getOrElse {
            // message 必须打出来：只打异常类名时，OEM 校验规则失败只能看到
            // IllegalArgumentException，根本定位不到是 conditionId、owner 还是 policy。
            Log.w(TAG, "add rule failed: ${it.javaClass.simpleName}: ${it.message}")
            return null
        }
        if (id.isNullOrEmpty()) return null
        ctx.prefs().edit().putString(KEY_RULE_ID, id).apply()
        return id
    }

    private fun findRuleId(ctx: Context): String? {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return null
        val saved = ctx.prefs().getString(KEY_RULE_ID, null)
        val rules = runCatching { nm.getAutomaticZenRules() }.getOrNull() ?: return null
        if (!saved.isNullOrEmpty() && rules.containsKey(saved)) return saved
        val found = rules.entries.firstOrNull { (_, r) ->
            r.owner?.packageName == ctx.packageName
        }?.key
        if (found == null) ctx.prefs().edit().remove(KEY_RULE_ID).apply()
        else ctx.prefs().edit().putString(KEY_RULE_ID, found).apply()
        return found
    }

    /**
     * 闹钟类别始终保留（用户自己的闹钟 + 永不自伤）；媒体类别放行是因为本应用铃声走
     * USAGE_MEDIA，若在这里静掉媒体，结束铃会跟着不响 —— 方案 D1 明确禁止这种自伤。
     * 收藏联系人保留；重复来电不声明，保持 STATE_UNSET，沿用用户系统勿扰里的设置；
     * 视觉上只关「亮屏 / 悬浮 / 呼吸灯 / 角标 / 全屏」，保留通知列表与状态栏，
     * 这样自己的计时通知仍能点「结束」，胶囊与 AOD 也不会被一刀切掉。
     */
    private fun policy(): ZenPolicy = ZenPolicy.Builder()
        .allowAlarms(true)
        .allowMedia(true)
        .allowCalls(ZenPolicy.PEOPLE_TYPE_STARRED)
        .allowMessages(ZenPolicy.PEOPLE_TYPE_NONE)
        .allowSystem(false)
        .allowReminders(false)
        .allowEvents(false)
        .showLights(false)
        .showPeeking(false)
        .showFullScreenIntent(false)
        .showBadges(false)
        .showInNotificationList(true)
        .showStatusBarIcons(true)
        .showInAmbientDisplay(true)
        .build()

    private fun conditionUri(ctx: Context) =
        Condition.newId(ctx).appendPath(CONDITION_PATH).build()

    private fun setState(ctx: Context, id: String, on: Boolean): Boolean {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        val condition = runCatching {
            Condition(
                conditionUri(ctx),
                Lang.t("dnd_rule_name"),
                if (on) Condition.STATE_TRUE else Condition.STATE_FALSE
            )
        }.getOrNull() ?: return false
        return runCatching {
            nm.setAutomaticZenRuleState(id, condition)
            Log.i(TAG, "rule $id active=$on")
            true
        }.getOrElse {
            Log.w(TAG, "set state failed: ${it.javaClass.simpleName}: ${it.message}")
            false
        }
    }

    /**
     * 兜底闹钟：进程被强杀后，即使没人再打开 App，时间到了也会由系统投递广播停用规则。
     * 用 setAndAllowWhileIdle（非精确 alarm，免 SCHEDULE_EXACT_ALARM 授权），
     * Doze 下允许延迟，但不会永远不投递 —— 底线是「不能永久残留」，不是「一秒不差」。
     */
    private fun armFallback(ctx: Context, plannedEndWall: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val at = (plannedEndWall + ALARM_GRACE_MS)
            .coerceAtLeast(System.currentTimeMillis() + ALARM_MIN_AHEAD_MS)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, restorePending(ctx))
            } else {
                am.set(AlarmManager.RTC_WAKEUP, at, restorePending(ctx))
            }
        }.onFailure { Log.w(TAG, "arm fallback failed: ${it.javaClass.simpleName}") }
    }

    private fun cancelFallback(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { am.cancel(restorePending(ctx)) }
    }

    /** 显式 Intent：PendingIntent 由本进程发出，receiver 不必对外暴露也收得到。 */
    private fun restorePending(ctx: Context): PendingIntent {
        val i = Intent(ctx, DndRestoreReceiver::class.java).setAction(ACTION_RESTORE)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, 1, i, flags)
    }
}
