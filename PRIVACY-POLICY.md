# 隐私政策 / Privacy Policy

**生效日期 / Effective date:** 2026-11-08
**适用 / Applies to:** 静息 · Still（`com.stilltimer.personal`），Android 版 · v2.3

---

## 中文

### 一句话
**本应用不联网、不收集、不上传、不共享任何个人信息。**

### 我们申请的权限及用途

| 权限 | 用途 | 是否必需 |
| --- | --- | --- |
| `FOREGROUND_SERVICE` | 计时期间保持前台服务运行，让铃声在锁屏、切后台时准点响起 | 是（计时功能本身） |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ 声明前台服务类型为「冥想计时播放」 | 是（系统要求） |
| `POST_NOTIFICATIONS` | 显示一条常驻的计时进度通知 | 否；拒绝后计时与铃声仍正常，仅通知不显示 |

应用 **没有** `INTERNET` 权限，因此技术上无法把任何数据发送到网络。也没有位置、通讯录、相机、麦克风、短信、存储读写、健康数据、广告 ID、内购或账号权限。

清单里的 `<queries>` 只声明「本应用需要看见系统里是否安装了邮件、浏览器、微信客户端」，属于 Android 11+ 的包可见性声明，**不是权限**，不授予任何设备能力。

### 本机存储的数据
以下数据只写入本应用的私有 `SharedPreferences`，不备份到云端、不共享给其他应用、不用于分析：

- 语言偏好（简体 / 繁体 / English）
- 时长、准备时间、间隔铃、三种铃声、背景音、响铃次数
- 用户保存的预设组合（名称与上述设置值）
- 最近应用的一套预设 ID（仅用于在预设面板里高亮「已选」，不含任何个人内容）
- 计时锚点（用于锁屏 / 进程重启后补算剩余时间与补响结束铃）

卸载应用即全部删除。应用内「结束」一次练习不会清除设置与预设。

### 第三方与外部应用
无第三方 SDK、无广告、无统计分析、无崩溃上报、无账号体系、无内容下载。「关于」页的邮箱、开源地址通过系统邮件 / 浏览器应用打开，微信、公众号与微信号可复制到系统剪贴板；这些动作由对应应用自行处理，本应用不接收任何回传。

**加微信打赏**：点击打赏卡片会把微信号复制进系统剪贴板并唤起微信客户端。本应用**不展示收款码、不内嵌支付页面、不接任何支付接口、不处理金额或订单、不接收付款结果**，付款完全发生在微信内、由用户自行决定。若未安装微信，只复制微信号并给出提示。

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

The app has **no `INTERNET` permission**, so it technically cannot transmit data. It also has no location, contacts, camera, microphone, SMS, external storage, health, advertising ID, billing, or account permissions.

The `<queries>` entries in the manifest only declare package visibility (mail, browser, WeChat) on Android 11+. They are **not permissions** and grant no device capabilities.

### On-device data
Only these values are stored in the app's private `SharedPreferences`, never synced or shared:

- Language preference (Simplified Chinese / Traditional Chinese / English)
- Duration, preparation time, interval, the three bell choices, ambient sound, ring counts
- Saved presets (name + the settings above)
- The ID of the most recently applied preset (only to highlight it in the preset sheet; contains no personal content)
- Timing anchors (used to restore remaining time and replay the end bell after a cold start)

All of it is removed when the app is uninstalled.

### Third parties & external apps
No third-party SDKs, ads, analytics, crash reporting, accounts, or content downloads. Email and source-code links in the About page open in the system mail / browser apps; the WeChat ID and Official Account name can be copied to the system clipboard. Those apps handle the action independently — this app receives nothing back.

**"Tip via WeChat"**: tapping the card copies the WeChat ID to the system clipboard and launches WeChat. This app **shows no QR code, embeds no payment page, integrates no payment SDK, handles no amount or order, and receives no payment result**. Any payment happens entirely inside WeChat at the user's discretion. If WeChat is not installed, only the ID is copied and a hint is shown.

### Children
No data is collected from anyone, including children. No registration or personal details are required.

### Changes & contact
Updates to this policy ship with the app version. Contact: Yaohui <luoxu_disk@163.com>, WeChat: pingyishufang.
