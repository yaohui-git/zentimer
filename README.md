# 静息 · Still / zeatimer

> 一只需要计时和一声钵响的闹钟。A timer and a bowl. Nothing else.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84.svg)](https://developer.android.com/)
[![Offline](https://img.shields.io/badge/Network-none-lightgrey.svg)](PRIVACY-POLICY.md)

一个**完全离线**的冥想计时工具：选时长、准备安顿、听一声钵、坐一会儿、再听一声钵结束。
没有课程、社区、账号、统计、广告，也没有联网。

界面、声音与交互风格高仿 Insight Timer 20.40.0 的核心计时链路；模块边界先用 REA + craft anything 拆出 feature map，再按「只留计时核心」裁剪。中文简体 / 中文繁体 / English 三语，应用内即时切换，不打断正在进行的练习。

**English summary:** A quiet, offline meditation timer for Android. Pick a duration, settle in, hear a bowl at the start and at the end. No courses, no account, no analytics, no network permission. Simplified Chinese, Traditional Chinese and English, switchable in-app. Apache-2.0 for the code only — bundled third-party audio/images are **not** covered (see [NOTICE](NOTICE.md)).

---

## 截图

| 主页 | 侧滑菜单 | 铃声试听 |
| --- | --- | --- |
| ![主页](screenshots/01-home.png) | ![侧滑菜单](screenshots/02-drawer.png) | ![铃声试听](screenshots/03-sound-picker.png) |

| 计时中 | 预设面板 | 关于 / 打赏 |
| --- | --- | --- |
| ![计时中](screenshots/04-running.png) | ![预设面板](screenshots/07-presets.png) | ![关于](screenshots/05-about.png) |

> 预设只占顶栏一个书签图标，点开才是上面第二张的面板——主屏留给时间设置。

启动图标（方案 B「圆环计时」）：[`assets/icon-512.png`](assets/icon-512.png)。完整三方案对比见 [`store/图标方案对比.png`](store/图标方案对比.png)。

---

## 功能

| | |
| --- | --- |
| 计时 | 5 / 10 / 15 / 20 / 30 / 45 / 60 / **90 分钟** / **2 小时** / 无限模式；圆环进度 + 端点光点；轻触圆环即开始 |
| 准备 | 关 / 10 秒 / 30 秒 / **60 秒** / **3 分钟** / **5 分钟**；准备阶段只放背景音，**开始铃在准备结束、正式计时开始时才响**；准备阶段也可暂停 |
| 铃声 | 9 种颂钵实物录音 + 寺院大锣 + 木鱼 + 静音轨；开始 / 间隔 / 结束分别设定，**点一下试听、可反复比较，按「确定」才生效** |
| **响铃次数** | 每个铃声右侧 `×N`，轻触在 **1 / 2 / 3 次**间循环；次间留 320 ms 让余音落下 |
| **预设组合** | 把「时长 + 准备 + 间隔 + 三种铃 + 背景音 + 响铃次数」整套存成一个方案，最多 6 个；**顶栏书签 → 底部面板，一点即应用**；保存 / 管理收在面板内，最近应用的一套高亮「已选」，可命名、可删除（删除有二次确认） |
| 背景音 | 13 种环境音，开始自动循环、暂停 / 结束自动停止 |
| 锁屏 | 前台服务驱动计时锚点，锁屏 / 切后台 / 进程被回收后仍准点响铃，通知栏显示剩余时间 |
| 语言 | 设置页切换简体 / 繁体 / English，StateFlow 重组不重建 Activity、不打断计时；首次安装按系统语言定默认 |
| 交互 | 菜单是**侧滑抽屉**（边缘手势 / 点遮罩 / 返回收起）；系统返回键逐层回退，主页再按一次才退出；时长设置位于卡片顶部、铃声在下 |
| 首屏克制 | 预设**不占主屏常驻空间**——只有顶栏一个书签图标，点开才出现底部面板。选预设是高频、保存 / 管理是低频，两者不放同一层级；时间设置区始终留在首屏 |
| 关于 | 版本、作者、邮箱、微信、公众号、**开源地址**、许可、隐私政策、素材说明（离线可读） |
| **打赏** | 「加微信打赏」：点一下复制微信号并唤起微信。**不内嵌支付、不展示收款码**，本应用不处理任何付款 |
| 全面屏 | 用系统 `WindowInsets.safeDrawing`（statusBars + displayCutout + navigationBars）统一适配挖孔 / 刘海 / 曲面 / 横竖屏，**不按机型硬编码** |

**权限**：主动申请 3 条（`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE`、`POST_NOTIFICATIONS`），全为前台保活所需；打包时 Android 会自动追加 1 条 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`，不是我们申请的。**无 INTERNET**，无位置 / 通讯录 / 相机 / 录音 / 健康数据 / 广告 ID / 内购。manifest 里的 `<queries>` 是 Android 11+ 包可见性声明，不是权限。

---

## 安装

直接装 [`releases/静息计时-v2.3.apk`](releases/静息计时-v2.3.apk)（Android 8.0 / API 26+，约 27 MB，已签名并对齐）。

```bash
adb install -r releases/静息计时-v2.3.apk
```

签名证书 SHA-256：`2fb424a9e98ce5fc4d8b5cb9efed4f20ee003039d1bc2cac97fd70ad01125204`。v2.0 ~ v2.3 共用同一密钥，可直接覆盖升级，设置与预设会保留。

> MIUI / HyperOS 需要在「开发者选项」里同时打开 **USB 调试** 与 **USB 安装（安全设置）**，安装时手机会弹确认框；按钮一开始是灰色的，等倒计时结束或勾选风险提示后才会变亮。

---

## 构建

本机已验证环境：JDK 17、Android SDK 37 + build-tools 36、Gradle 9.6.0、AGP 9.4.0、Kotlin 2.2.10、Compose BOM 2026.06.01。

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
cd StillTimer

# 首次：从 example 复制并填自己的密钥（keystore.properties 与 .jks 均不入库）
cp keystore.properties.example keystore.properties

gradle assembleDebug
gradle assembleRelease
```

`local.properties` 里的 `sdk.dir` 换成自己的 SDK 路径即可。仓库用官方 `dl.google.com/dl/android/maven2` + 阿里云镜像，绕开某些网络环境下 `maven.google.com` 走代理 502 的问题。

提交改动前跑一遍文案对账：

```bash
python tools/check_lang.py   # 三语 key 对账，缺一条就 exit 1
```

---

## 真机验收

SDK 的 `platform-tools/adb.exe` 可直接用：

```bash
adb devices -l
adb shell am start -n com.stilltimer.personal/.MainActivity
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png
adb logcat -d | grep -i stilltimer
```

v2.3 已在 **M2003J15SC（Redmi Note 9 系列，Android 12 / SDK 31，1080×2340 @440dpi）** 上实装验证：启动、控件树、抽屉、返回栈、截图与崩溃日志。**注意**：Android 15+ 才强制 edge-to-edge，低版本设备测不出顶部 insets 的最终效果，目标机红米 15 Pro 上的表现仍需目视确认。

---

## 打赏作者

这个应用是个人作品，完全免费、无广告、无内购，打赏纯属自愿，**不解锁任何功能**。

如果它对你有用，想支持一下：

> **加微信 `pingyishufang`（耀慧）**，金额随意、完全自愿。

应用「关于」页里点一下「打赏作者」会自动复制微信号并唤起微信；你也可以在微信里直接搜索这个号。**本应用不展示收款码、不内嵌支付、不接任何支付 SDK、不处理金额 / 订单 / 付款结果**，付款全程发生在微信里。

之所以刻意不做内嵌支付：微信个人收款码没有公开的一步唤起接口，支付宝个人静态收款码用于经营性 / 远程非面对面收款存在明确合规红线（央行银发〔2021〕259 号），国内商店的「虚拟打赏」还可能要求 ICP 经营许可证。调研过程见 [`交付说明.md`](交付说明.md) 附录。

| | |
| --- | --- |
| 作者 | 耀慧 |
| 邮箱 | <luoxu_disk@163.com> |
| 微信 | `pingyishufang` |
| 公众号 | 何处青山不道场 |
| 开源地址 | <https://github.com/yaohui-git/zeatimer> |

---

## 开源协议与素材授权（务必先读）

- **代码**：`LICENSE` 采用 **Apache License 2.0**。适用于 Kotlin 源码、Gradle 脚本、本项目自写的 XML / 文案 / 绘制逻辑、`tools/` 脚本。
- **素材不在协议范围内**：`NOTICE.md` 与 [`ASSETS-NOTICE.md`](ASSETS-NOTICE.md) 逐项列明。`res/raw/` 的 23 段录音与 `res/drawable-nodpi/` 的 36 张界面图提取自原版应用包，属于第三方资产；**公开分发 / 商用前请自行确认授权或替换**。启动图标（方案 B）为本项目自绘，其中打坐人形剪影由项目所有者提供。
- **一键净化**：`python tools/asset_privacy.py strip` 把实物素材移到本地 `assets-private/`（不入库），生成同名自绘占位，公开仓库仍能编译；本地 `restore` 即恢复完整版。
- **打包干净仓库**：`python tools/make_oss_repo.py` 生成只含白名单文件的 `oss/`，排除签名密钥、密码、构建产物与逆向证据。

> 个人自用与公开分发是两回事。若你 fork 后要再分发，请先处理素材授权，别默认这些音频 / 图片也在 Apache-2.0 里。

---

## 隐私

不联网、不收集任何数据、不含第三方 SDK。所有设置与预设只存在本机 SharedPreferences。完整政策见 [`PRIVACY-POLICY.md`](PRIVACY-POLICY.md)。

---

## 目录

```
StillTimer/            Android 工程（Gradle + Kotlin + Compose，单模块）
  app/src/main/java/   状态机、音频、语言、预设、前台服务、单 Activity UI
  app/src/main/res/    三语 strings、配色、主题、启动图标、声音与图片素材
tools/                 图标生成、素材隐私开关、开源目录打包、三语文案对账
assets/                启动图标 512
screenshots/           真机截图
store/                 商店图标、宣传图、三套图标方案对比
releases/              已签名 APK
docs/                  feature map / 界面交互对照 / 高仿流程与证据
LICENSE / NOTICE.md / CONTRIBUTING.md / PRIVACY-POLICY.md
ASSETS-NOTICE.md / CHANGELOG.md / STORE-LISTING.md / 交付说明.md
```

---

## 参与

见 [`CONTRIBUTING.md`](CONTRIBUTING.md)。问题与建议请走 GitHub Issues。

---

*本项目是学习性质的高仿精简实现，与 Insight Timer / spotlightsix 无任何关联、无任何背书关系。*
