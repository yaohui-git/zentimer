package com.yaohui.zentimer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * HyperOS「焦点通知 / 灵动岛」能力自检。
 *
 * 依据（2026-11-09，对方产物）：设备上 `com.android.deskclock` 的
 * `NotificationUtil.hasFocusPermission()` 反汇编，它用**自己的包名**调用
 * `content://miui.statusbar.notification.public` 的 `canShowFocus` 方法，
 * 读返回 Bundle 里的布尔值，默认 false。这是公开 ContentProvider 查询，
 * 不需要额外权限；provider 会校验传入包名必须属于调用方 uid。
 *
 * 用途：通知能力声明式探测。返回 false 时只走 Android 官方通道，
 * 绝不塞 `miui.focus.param`。探测结果缓存起来供 StyleSelector 决策；
 * 缓存默认 false，未探测 / 探测失败都等价于「没有这个能力」，安全降级。
 */
object FocusCapability {
    private const val TAG = "ZT_FOCUS"
    private const val URI = "content://miui.statusbar.notification.public"
    private const val METHOD = "canShowFocus"
    private const val KEY = "canShowFocus"

    @Volatile
    private var cached: Boolean = false

    /** 只有 probe() 真正通过才为 true；不主动发起查询，避免主线程 binder 调用。 */
    fun available(): Boolean = cached

    fun probe(context: Context): Boolean {
        val result = runCatching {
            val extras = Bundle().apply { putString("package", context.packageName) }
            context.contentResolver.call(Uri.parse(URI), METHOD, null, extras)
                ?.getBoolean(KEY, false) == true
        }.getOrElse { e ->
            Log.w(TAG, "probe failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
        cached = result
        Log.i(TAG, "pkg=${context.packageName} $KEY=$result")
        return result
    }
}
