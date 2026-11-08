package com.yaohui.zentimer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 发布信息与联系方式的唯一配置入口。界面不写死任何一处，改这里即可。
 *
 * 打赏刻意不做内嵌支付：按用户决定改为「提示添加微信打赏」——点一下复制微信号
 * 并唤起微信，钱由用户在微信里自行转账，本应用不展示收款码、不接任何支付接口、
 * 不碰付款流程。这样既省掉了收款码素材，也避开了个人静态收款码用于经营性收款 /
 * 远程非面对面收款的合规问题（央行银发〔2021〕259 号）与商店「虚拟打赏」资质要求。
 * 公域调研原文见 交付说明.md「附录 · 打赏方案调研」。
 */
object AppInfo {
    /**
     * 版本号不在代码里另写一份——直接从构建产物读，避免「gradle 里是 2.4、界面上还是 2.3」。
     * 要改版本，只改 app/build.gradle.kts 的 versionCode / versionName。
     */
    val VERSION: String = BuildConfig.VERSION_NAME
    val VERSION_CODE: Int = BuildConfig.VERSION_CODE
    const val AUTHOR = "耀慧"
    const val EMAIL = "luoxu_disk@163.com"
    const val WECHAT = "pingyishufang"
    const val OFFICIAL_ACCOUNT = "何处青山不道场"
    const val REPO = "https://github.com/yaohui-git/zentimer"  // 仓库已由 zeatimer 改名为 zentimer
    const val LICENSE = "Apache-2.0"

    val repoIsUrl: Boolean get() = REPO.startsWith("http://") || REPO.startsWith("https://")

    /** 用系统应用打开链接；本应用无 INTERNET 权限，浏览器 / 邮件应用自己具备联网能力。 */
    fun open(ctx: Context, url: String): Boolean = try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Throwable) {
        false
    }

    fun mail(ctx: Context): Boolean = open(ctx, "mailto:$EMAIL")
    fun repo(ctx: Context): Boolean = if (repoIsUrl) open(ctx, REPO) else false

    /** 微信没有公开的个人收款 scheme，这里只唤起微信主界面；微信号已提前复制到剪贴板。 */
    fun openWechat(ctx: Context): Boolean = try {
        val i = ctx.packageManager.getLaunchIntentForPackage("com.tencent.mm") ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    } catch (_: Throwable) {
        false
    }

    fun copy(ctx: Context, text: String): Boolean = try {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("zentimer", text))
        true
    } catch (_: Throwable) {
        false
    }
}
