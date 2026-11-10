package com.yaohui.zentimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 系统状态复原的第三、第四道兜底，所有触发源只做同一件事：停用本应用那条勿扰规则。
 *
 *  - BOOT_COMPLETED：手机在静坐中重启，用户根本不打开 App 也要复原；
 *  - MY_PACKAGE_REPLACED：覆盖升级后旧规则可能还留在系统里；
 *  - DND_RESTORE：起跑时挂的 AlarmManager 兜底，覆盖「进程被强杀且无人再打开 App」。
 *
 * 不申请悬浮窗、不代点系统开关、不读用户隐私。停用幂等，重复触发没有任何副作用。
 * 收到广播后不启动界面、不弹通知、不打断任何正在进行的计时。
 */
class DndRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        DndGuard.reconcileOnStartup(ctx.applicationContext)
    }
}
