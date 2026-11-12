# 隐私政策 / Privacy Policy

**生效日期 / Effective date:** 2026-11-11
**适用 / Applies to:** 静息 · zentimer（一款极简禅意静坐计时器 / A minimal Zen sitting timer，`com.yaohui.zentimer`），Android 版 · **v2.25 及之后版本**（当前本地稳定版 v2.29 / versionCode 30）

---

## 中文

### 一句话
**本应用不联网、不收集、不上传、不共享任何个人信息。**

### 我们申请的权限及用途

| 权限 | 用途 | 是否必需 |
| --- | --- | --- |
| `FOREGROUND_SERVICE` | 计时期间保持前台服务运行，让铃声在锁屏、切后台时准点响起 | 是（计时功能本身） |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ 声明前台服务类型为「静坐计时播放」 | 是（系统要求） |
| `POST_NOTIFICATIONS` | 显示一条常驻的计时进度通知 | 否；拒绝后计时与铃声仍正常，仅通知不显示 |
| `WAKE_LOCK` | 计时期间保持 CPU 唤醒，避免息屏后不响铃 | 是（普通权限，安装即授予） |
| `POST_PROMOTED_NOTIFICATIONS` | Android 16+ 状态栏倒计时与锁屏实时信息 | 是（系统要求，不声明会被静默忽略） |
| `ACCESS_NOTIFICATION_POLICY` | v2.25「静坐时免打扰」：创建并停用**属于本应用的一条**系统勿扰模式 | **否，且开关默认关闭**；只有用户主动打开时才需在系统「勿扰权限」页亲手授权一次 |
| `RECEIVE_BOOT_COMPLETED` | 手机重启后收干净上一条规则的残留激活状态 | 是（复原底线，不打开应用也生效） |

应用 **没有** `INTERNET` 权限，因此技术上无法把任何数据发送到网络。也没有位置、通讯录、相机、麦克风、短信、存储读写、健康数据、广告 ID、内购或账号权限。

**应用只请求一项通知权限**：Android 13 及以上首次进入时会请求 `POST_NOTIFICATIONS`，用于显示常驻倒计时、状态栏实时动态与锁屏卡片；拒绝或忽略只是看不见通知，不影响计时与铃声。除此之外，应用不会请求网络、位置、通讯录、相机、麦克风、存储、账号或登录授权。上表中其余 5 项只是清单声明、安装即授予。另一项需要用户亲手授权的是「静坐时免打扰」：它是系统的「特殊访问权限」，不是弹框授权，只在用户主动打开该开关时才跳一次系统设置页；不授权时该开关弹回关闭，静坐照常进行。本应用**不读取通知内容、不改动系统全局勿扰开关、不改写用户已有的勿扰规则**，只激活与停用自己那条名为「静坐」的模式；结束、进程重启、手机重启与兜底闹钟都会把它停用。

清单里的 `<queries>` 只声明本应用会用系统邮件应用打开邮箱地址、用浏览器打开网页，属于 Android 11+ 的包可见性声明，**不是权限**，不授予任何设备能力，也不含微信或其他应用包名。

### 本机存储的数据
以下数据只写入本应用的私有 `SharedPreferences`，不备份到云端、不共享给其他应用、不用于分析：

- 语言偏好（简体 / 繁体 / English）与「是否手动选择过语言」的标记（用于默认跟随系统）
- 主题色偏好（跟随系统 / 深色 / 浅色）与「随节气微调」开关（只影响纸色，不记录日期或位置）
- 「静坐时免打扰」开关，以及系统侧那条自有规则的 ID 与当前激活标记（仅用于结束时停用；不含通知内容、不含联系人）
- 时长、准备时间、间隔铃、三种铃声、背景音、响铃次数
- 用户保存的预设组合（名称与上述设置值）
- 最近应用的一套预设 ID（仅用于在预设面板里高亮「已选」，不含任何个人内容）
- 计时锚点（用于锁屏 / 进程重启后补算剩余时间与补响结束铃）

卸载应用即全部删除。应用内「结束」一次练习不会清除设置与预设。

### 第三方与外部应用
无第三方 SDK、无广告、无统计分析、无崩溃上报、无账号体系、无内容下载。「关于」页的邮箱、开源地址通过系统邮件 / 浏览器应用打开，微信、公众号与微信号可复制到系统剪贴板；这些动作由对应应用自行处理，本应用不接收任何回传。

**捐助**：应用内只展示 GitHub 项目主页与一行说明，点击只把该地址复制进系统剪贴板；**不唤起浏览器或微信、不展示收款码、不内嵌支付页面、不接任何支付接口、不处理金额或订单、不接收付款结果**。是否捐助完全由用户自行决定，不换取任何功能或服务。

### 儿童
本应用不含面向儿童的内容收集，不要求注册或填写任何个人信息。

### 变更与联系
政策变更会随应用版本更新在本文件与「关于」页体现。问题与建议：耀慧 <luoxu_disk@163.com>；微信 pingyishufang；公众号「何处青山不道场」。

---

## English

### In one sentence
**This app has no network access and collects, uploads, or shares no personal data.**

### Permissions

| Permission | Purpose | Required |
| --- | --- | --- |
| `FOREGROUND_SERVICE` | Keep the timer running so bells ring on time while the screen is locked or the app is in the background | Yes |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ declaration of the foreground service subtype | Yes (system requirement) |
| `POST_NOTIFICATIONS` | Show one ongoing notification with the remaining time | No — declining hides the notification only; timing and bells still work |
| `WAKE_LOCK` | Keep the CPU awake while timing so bells ring on time after the screen turns off | Yes (normal permission, granted at install) |
| `POST_PROMOTED_NOTIFICATIONS` | Android 16+ status-bar countdown and lock-screen live info | Yes (system requirement; without it the request is silently ignored) |
| `ACCESS_NOTIFICATION_POLICY` | v2.25 "Do not disturb while sitting": activate and deactivate **one system mode owned by this app** | **No, and the switch is off by default**; needs one manual grant in system settings only if the user turns it on |
| `RECEIVE_BOOT_COMPLETED` | After a reboot, clear any leftover active state of that one rule | Yes (recovery guarantee, works without opening the app) |

The app has **no `INTERNET` permission**, so it technically cannot transmit data. It also has no location, contacts, camera, microphone, SMS, external storage, health, advertising ID, billing, or account permissions.

**The app requests one notification permission**: on Android 13+ it asks for `POST_NOTIFICATIONS` once on first launch so the ongoing countdown, the status-bar live activity and the lock-screen card can be shown; declining or ignoring it only hides the notification and does not affect the timer or the bells. Beyond that, the app never asks for network, location, contacts, camera, microphone, storage, account or sign-in. The other five manifest entries are granted at install. The one other user grant is for "Do not disturb while sitting": it is a special-access permission handled in system settings, not a permission dialog, and is reached only if the user turns the switch on. Without that grant the switch returns to off and the sitting continues. The app **does not read notification content, does not change the device-wide Do Not Disturb state, and does not modify any of the user's existing rules** — it only activates and deactivates its own mode named "Sitting"; ending the sitting, a cold start, a reboot and a fallback alarm all deactivate it.

The `<queries>` entries in the manifest only declare package visibility for opening an email address in the system mail app and a web page in the browser on Android 11+. They are **not permissions**, grant no device capabilities, and contain no WeChat or other app package names.

### On-device data
Only these values are stored in the app's private `SharedPreferences`, never synced or shared:

- Language preference (Simplified / Traditional Chinese / English) and a flag indicating whether the user chose it manually (used for the follow-system default)
- Theme preference (system / dark / light) and the "Seasonal paper tone" switch (affects paper color only; no date or location is recorded)
- The "Do not disturb while sitting" switch, plus the ID and active flag of that one system rule (used only to deactivate it; no notification content, no contacts)
- Duration, preparation time, interval, the three bell choices, ambient sound, ring counts
- Saved presets (name + the settings above)
- The ID of the most recently applied preset (only to highlight it in the preset sheet; contains no personal content)
- Timing anchors (used to restore remaining time and replay the end bell after a cold start)

All of it is removed when the app is uninstalled.

### Third parties & external apps
No third-party SDKs, ads, analytics, crash reporting, accounts, or content downloads. Email and source-code links in the About page open in the system mail / browser apps; the WeChat ID and Official Account name can be copied to the system clipboard. Those apps handle the action independently — this app receives nothing back.

**Donation**: the About page shows only one line of text and the GitHub project URL; tapping it copies that URL to the system clipboard. The app **does not open a browser or WeChat, shows no QR code, embeds no payment page, integrates no payment SDK, handles no amount or order, and receives no payment result**. Whether to donate is entirely the user's decision and unlocks no features or services.

### Children
No data is collected from anyone, including children. No registration or personal details are required.

### Changes & contact
Updates to this policy ship with the app version. Contact: Yaohui <luoxu_disk@163.com>, WeChat: pingyishufang.
