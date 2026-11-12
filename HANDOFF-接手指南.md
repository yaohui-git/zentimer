# HANDOFF · 静息 zentimer 接手指南（细节手册）

> **2026-11-11 22:xx 降级说明**：本文件是 v2.25 时代的 33KB 细节手册。**新任务请先读 `HANDOFF-接手入口.md`（约 10KB）**，只在需要开发环境 / 代码地图 / 旧 TC 全量时按索引回来查本文件。基线已推进到 **v2.29 / versionCode 30**，下方「基线 v2.25」是当时的历史口径，按「结论变了别删旧版」保留不改。
>
> **受众**：接手继续优化改进的执行者（可能是 AI，例如豆包），与本项目**共用同一个 git 库**。
> **基线**：v2.25 / versionCode 26；本文件写于 2026-11-11。
> **立场**：这是一份「怎么接着干」的指南，不是历史对话记录。历史推演在 `FeatureMap-TestCase-整体设计.md`、
> `CHANGELOG.md`、`.workbuddy/memory/` 里，按需查，不必通读。

---

## 0. 开工第 0 步（照这个顺序，别跳）

1. **读工作区根 `本地工具清单.md`**（在 `D:/Users/thinkpad/workbuddy_data/workbuddy/2026-10-17-20-51-50/`）。
   本机已装好的运行时 / SDK / adb / skill / 构建链一律复用，**禁止重复安装、禁止全盘重搜**。
   清单说「没有」时只当线索，现场 `ls` 核一次；发现错就当场改清单，别只记在日志里。
2. **读项目记忆**：`.workbuddy/memory/MEMORY.md`，以及最近的 `memory/YYYY-MM-DD.md`。
3. **读本文件 §1 环境 / §3 硬约束**，再读 `FeatureMap-TestCase-整体设计.md` 的反馈表尾部与 §12.7（最近一轮决策）。
4. **读 §9**：用户已下令暂停所有新 feature 开发，待办清单与开工顺序都在那里；别跳过直接动 UI 结构。
5. **先跑一遍静态闸门**看当前基线，别上来就改代码：
   ```
   python tools/check_ui_copy.py
   python tools/check_lang.py
   python tools/check_evidence_invariants.py
   ```
   任何 `FAIL` 都挡出包。`WARN` 是要人看一眼，不是错。
6. 用户历史上反复强调：**需求不清楚要确认，不要猜**；给效果目标时自己定方案并执行完再交付；
   断言外部系统行为，证据只认对方产物或可复现实验，本项目脚本 / 台账 / 记忆只是线索。

---

## 1. 开发环境（本机实测，换机按版本准备即可）

| 项 | 值 |
|---|---|
| 主库（工作区） | `D:/Users/thinkpad/workbuddy_data/workbuddy/2026-10-17-20-51-50/`（branch `master`） |
| 本工程 | `zentimer/`，Android 工程在 `zentimer/ZenTimer/` |
| JDK | `D:/Users/thinkpad/workbuddy_data/workbuddy/2026-11-06-07-12-10/.buildtools/jdk/jdk-17.0.20.1+1`（Java 17） |
| Android SDK | 同目录 `.buildtools/android-sdk/`，含 `platform-tools/adb.exe` |
| `local.properties` | `sdk.dir=D:/Users/thinkpad/workbuddy_data/workbuddy/2026-11-06-07-12-10/.buildtools/android-sdk`（**不入库**） |
| Gradle | wrapper `gradle-9.6.0-bin.zip`，用 `ZenTimer/gradlew`，不要另装 Gradle |
| 编译配置 | `compileSdk 37 / minSdk 26 / targetSdk 37`，Kotlin + Compose，`jvmTarget 17` |
| 签名 | `ZenTimer/zentimer.jks`（**本机保管，不入库**）+ `ZenTimer/keystore.properties`（**不入库**）；模板 `keystore.properties.example` 可入库 |
| adb | `.../.buildtools/android-sdk/platform-tools/adb.exe`，版本 1.0.41 / 37.0.1 |
| Python 工具链 | 系统 Python 3，`tools/*.py` 零第三方依赖，只有截图取色用 `PIL`（Pillow） |
| 构建命令 | 在 `zentimer/ZenTimer/` 下 `./gradlew assembleRelease`，产物 `app/build/outputs/apk/release/app-release.apk` |
| 出包规则 | **任何一次出包（哪怕只改一行）`versionCode` 都要 +1**；`versionName` 按主.次递增。唯一产物复制到 `releases/zentimer-vX.X.apk`，文件名纯 ASCII |
| 发布链 | 主库 `master` **没有 origin**；公开发布走 `zentimer/.craft/tmp_remote/`（独立 git 库，branch `main`，origin `git@github.com:yaohui-git/zentimer.git`，SSH）。生成工具 `tools/make_oss_repo.py` |

**真机**（2026-11-11 在用手台）：`Redmi Note 15 Pro / 25080RABDC (lapis) / Android 16 / SDK 36 / HyperOS 3.0 V816 / 1280×2772 @520dpi`。
adb 序列号 `emzly9scts658tt8`。**手机必须开 USB 调试并在弹窗点「一律允许」**，否则 Windows 只认到 MTP 接口（`VID_2717&PID_FF40`）、`adb devices` 是空表；ADB 接口是 `PID_FF48`。

连本地端口 / CDP 前记得 `export NO_PROXY=127.0.0.1,localhost,::1`；Windows 下 `.bat/.cmd` 必须 CRLF + 纯 ASCII。
Git Bash 里 `uiautomator dump /sdcard/x.xml` 会被 MSYS 改路径，统一用 `exec-out uiautomator dump /dev/tty > 文件`。

---

## 2. 代码地图

### 2.1 目录

```
zentimer/
├── ZenTimer/app/src/main/java/com/yaohui/zentimer/   ← 全部 Kotlin（19 个文件）
├── ZenTimer/app/src/main/res/                       ← drawable-nodpi 素材 / raw 录音 / mipmap 图标
├── ZenTimer/app/src/main/AndroidManifest.xml
├── ZenTimer/app/build.gradle.kts                    ← 版本号唯一来源
├── tools/                ← 静态闸门、文案导出/回填、真机验收、开源打包（进 git）
├── .craft/rea/v2*/       ← 真机证据（截图 / 控件树 / dumpsys / 验收日志），**被 gitignore，不入库**
├── releases/             ← 成品 APK（被 gitignore；公开库另有白名单例外）
├── screenshots/ assets/ store/                     ← 上架素材（图片有 gitignore 例外）
├── FeatureMap-TestCase-整体设计.md                  ← 功能 / 反馈 / 测试用例总台账
├── CHANGELOG.md RELEASE-NOTES.md STORE-LISTING.md  ← 版本与对外口径
├── UI文案复核表.csv UI文案复核决定.md              ← 文案复核流水
└── HANDOFF-接手指南.md（本文件）
```

### 2.2 模块职责（按依赖层次，不是按字母）

| 文件 | 职责 |
|---|---|
| `ZenApp.kt` | `Application`。只做两件「进程启动一次」：`SeasonTheme.init()` 取四时快照、`DndGuard.reconcileOnStartup()` 无条件停用残留勿扰规则。刻意不调 `TimerEngine.init()`，避免多一个写状态机的人 |
| `TimerEngine.kt` | **计时状态机单例**。`Defaults`（默认 15 分 / 上限 1439 分 / 准备上限 659 秒）、`formatTime`、暂停恢复、结算入口。`start/stop/settleIfDue` 是免打扰激活 / 停用的唯一挂载点 |
| `SettlementPolicy.kt` | 「这一轮该不该结算」的唯一判定，纯 Kotlin 零 Android 依赖，可离线单测。修的是「进程被强杀后冷启动自己补一声结束铃」 |
| `TimerService.kt` | 前台服务，后台持续驱动 `tick()`，铃声不依赖界面活着 |
| `NotificationContract.kt` | 一条计时通知按三段组装（base / 厂商焦点 / 私有通道），任一段缺席只影响显示 |
| `StyleSelector.kt` | 通知显示能力决策表，不 import `android.*`，可在 JVM 跑 12 组跨厂商用例 |
| `FocusCapability.kt` | HyperOS 焦点通知能力自检，依据对 `com.android.deskclock` 的反汇编 |
| `Audio.kt` | 铃声播放，**`USAGE_MEDIA`**（这是免打扰规则必须 `allowMedia(true)` 的原因） |
| `SoundCatalog.kt` | 随包实物录音与图片目录，三语名称；第三方资产，不在代码开源授权范围 |
| `Presets.kt` | 整套组合存本机 SharedPreferences，上限 6 个，用平台 `org.json`，不引第三方 |
| `Store.kt` | **单一 SharedPreferences 入口** `Context.prefs()`（`zentimer`） |
| `Lang.kt` | **三语文案唯一事实源**。`Lang.t(key)`；改文案只改这里，再重跑 `export_ui_copy.py` |
| `AppInfo.kt` | 发布信息 / 联系方式唯一入口。**含不可变串**：微信号、邮箱、仓库、许可名、署名、公众号名 |
| `Theme.kt` | 跟随系统 / 深色 / 浅色，StateFlow 驱动，切换只重组界面不打断练习 |
| `Season.kt` | 「四时」。只动 `paper` + `accent` 两个 token，公历四段，**只在 `ZenApp.onCreate` 读一次日期**；派生完整色板 |
| `DndGuard.kt` | 「静坐时免打扰」。**绝不改全局勿扰状态**，只激活 / 停用自有的一条 `AutomaticZenRule`；四道停用兜底 + 起跑挂 `AlarmManager` |
| `DndRestoreReceiver.kt` | 收 `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / `DND_RESTORE`，只做停用一件事 |
| `DurationField.kt` | **唯一的时长编辑组件**，三处复用（静坐 / 间隔 / 准备）。原生 `NumberPicker` + 档位层 + 就地编辑、点空白取消、`bringIntoView` |
| `MainActivity.kt` | 全部 Compose UI：`StillColors` / 深浅色板、`StillTheme`、`RingBlock`、`SettingsCard`、`SettingsPage`、`AboutPage`、`PresetsPage`、`SettingToggle`、抽屉与导航 |

### 2.3 单一事实源（改东西前先找对地方）

- 版本号 → `ZenTimer/app/build.gradle.kts`（唯一）
- 时长上限 / 默认值 / 倒计时格式 → `TimerEngine.Defaults` / `TimerEngine.formatTime`
- 文案 → `Lang.kt`（三语必须同时改）
- 发布常量 / 不可变串 → `AppInfo.kt`
- 本地存储 → `Store.kt` 的 `prefs()`
- 色板 → `MainActivity.kt` 的 `DarkPalette` / `LightPalette`，四时增量在 `Season.kt`
- 功能与测试用例 → `FeatureMap-TestCase-整体设计.md`

### 2.4 静态闸门（改完必跑）

| 脚本 | 查什么 |
|---|---|
| `tools/check_ui_copy.py` | 总闸门：禁用术语、三语 key 对齐、硬编码、文案质量、默认值、资源、版本登记、无障碍、打赏、时长控件、v2.25 两个 feature 的 8 组不变量，并内置调用证据不变量 |
| `tools/check_lang.py` | 三语各多少条、代码引用多少 key、有无未定义 |
| `tools/check_evidence_invariants.py` | 《证据核查》§9 E11~E14 的通知四表面基线（顶栏数字 / 下拉「静坐中」 / AOD 不给数字） |
| `tools/check_screen_copy.py` | 三语真机屏幕文案对账（需连设备） |
| `tools/export_ui_copy.py` | 从源码重刷 `UI文案复核表.csv`（UTF-8 BOM，含「AI 建议」空列） |
| `tools/apply_ui_review.py` | 从回填 CSV 生成 `Lang.kt` 补丁，可复跑 |
| `tools/verify_device.py` | **分辨率无关的真机主验收**（见 §5） |

> 这些闸门是历史事故的防复发装置：改断言前先读断言上方注释里写的事故原型，**别为了让自己过而删断言**。

---

## 3. 不许推翻的硬约束

| 约束 | 为什么 |
|---|---|
| 中文一律「静坐」，禁 冥想 / 禅修 / 打坐 / 静心 / 正念；英文禁 `meditation / meditating / mindfulness` | 定稿口径，`check_ui_copy.py` 的 `BANNED` 会挡。唯一豁免是 Manifest 里系统约定的前台服务子类型 `meditation_timer_playback` |
| 产品名：简「静息」/ 繁「靜息」/ English `zentimer` | 别把定位描述当产品名 |
| 简繁逐字对等；English 不出现汉字（`lang_hans` / `lang_hant` 语言自称豁免） | 上一轮复核定稿 |
| 不可变串逐字复制，禁规范化 / 重打 | 微信号、邮箱、仓库、许可名、署名、法律文本 |
| 通知四表面语义：顶栏实时数字、下拉卡片中间「静坐中」、AOD **不给任何数字** | 证据核查 §9 E11~E14。曾因台账写了不变量、代码没闸门，出过「AOD 冒数字」 |
| 「程序退出后系统配置必须复原」 | **用户定的底线**。零调用 `setInterruptionFilter` / `setNotificationPolicy`；不许「存原值用完写回」；只激活 / 停用自有的一条 `AutomaticZenRule`；四道兜底：正常/提前结束、冷启动自愈、开机与包替换广播、起跑挂兜底闹钟；停用不以当前有无权限为前置条件 |
| 结束铃先起响再停用规则 | 顺序反了会绕过 D1 验收 |
| 一个 `DurationSetting` 组件三处复用 | 历史反复踩过「长成两套」的坑，闸门有断言 |
| 极简克制禅意：无 onboarding / 教程气泡 / 教练遮罩 / 卡片式档位 / 渐变阴影 / 季节提示与动画 | 用户多次明确；闸门把 `Tutorial / Onboarding / coachMark` 列为禁止项 |
| 离线、无 `INTERNET`、无第三方 SDK、无广告统计账号内购 | 产品与隐私政策硬承诺 |
| 四时只在进程启动取一次日期；静坐中绝不换色；关开关固定秋档；无手动四选一 | 方案硬约束，闸门 8 组不变量 |
| 签名 `keyAlias` 不改 | 改 alias = 换证书，已发布版本无法覆盖升级 |

**对外授权口径（2026-11-11 修正）**：只请求一项**通知权限**——Android 13+ 首次进入时弹一次系统框，用于常驻倒计时与锁屏 / 状态栏实时信息，拒绝只影响通知显示；清单 7 条权限，其余 5 条安装即授予；另「静坐时免打扰」需用户在**系统设置里亲手授权一次**，默认关闭、不用就不必授权。
README / `PRIVACY-POLICY.md` / `STORE-LISTING.md` / `CONTRIBUTING.md` 已按此同步，别改回「无任何授权请求」「连通知权限也不弹」「只 3 条权限」这类旧说法。

---

## 4. Feature sets（v2.25 当前口径）

| 域 | 当前实现 | 入口 / 关键代码 |
|---|---|---|
| 计时 | 默认 15 分；上限 23 时 59 分（1439 分）；准备默认 3 分、上限 10 分 59 秒；暂停 / 继续 / 结束；`H:MM:SS` 自动降字号 | `TimerEngine.kt`、`MainActivity.RingBlock` |
| 时长编辑 | **先出四档**（静坐 `15/30/45/60`、准备 `1/3/5/10`）即点即生效，「自定义」才展开原生滚轮；提醒间隔无档位、直接滚轮；滚轮循环、铺满命中区；点标题行或点圆环都能进编辑；点空白取消、全局 `editingTarget` 互斥 | `DurationField.kt`（唯一组件）、`MainActivity.SettingsCard` |
| 铃声 | 开始 / 间隔 / 结束三种铃 × 响铃次数、背景音；实物录音随包；`USAGE_MEDIA` | `SoundCatalog.kt`、`Audio.kt` |
| 预设 | 整套组合存本机，上限 6 个，顶栏书签 + 底部面板 | `Presets.kt` |
| 主题 / 四时 | 跟随系统 / 深色 / 浅色；「随节气换肤」只改纸色与强调色，公历 3–5 春 / 6–8 夏 / 9–11 秋 / 12–2 冬，进程启动取一次快照；静坐中开关置灰；关掉固定秋档 | `Theme.kt`、`Season.kt`、`MainActivity.StillTheme` |
| 免打扰 | 「静坐时免打扰」默认关、需一次系统授权；起跑激活自有 `AutomaticZenRule`，结束 / 强杀自愈 / 重启广播 / 兜底闹钟四路停用；放行闹钟、媒体声、收藏联系人；圆环下显示「免打扰中」 | `DndGuard.kt`、`DndRestoreReceiver.kt`、`ZenApp.kt` |
| 通知 | 一条常驻通知按三段组装；HyperOS 焦点通知能力自检；AOD 不给数字 | `NotificationContract.kt`、`StyleSelector.kt`、`FocusCapability.kt` |
| 语言 | 简 / 繁 / English，跟随系统默认；切换只重组、不打断静坐 | `Lang.kt`、`Theme.kt` |
| 关于 / 打赏 | 只留一行文字，点击复制纯微信号；不唤起微信、不接支付、不展示收款码 | `AppInfo.kt`、`MainActivity.AboutPage` |
| 隐私 | 完全离线，设置只存本机，卸载即清除 | `PRIVACY-POLICY.md` |

**发放口径**：`README.md` / `STORE-LISTING.md` 是与用户对话的第一口径，改功能要同步；
`CHANGELOG.md` 保留全部历史版本原文（**结论变了别删旧版**，新版叠加在前并注明以哪版为准）。

---

## 5. TC 说明（测试与验收）

### 5.1 三层验收

1. **静态闸门**（无需设备）：§2.4 的四个 `check_*.py`。`0 FAIL` 是出包前提。
2. **真机主验收**（需设备）：`python tools/verify_device.py`。
   - 分辨率无关：所有点击按 `uiautomator` 控件树定位，取色按屏幕比例，切到 English / 繁体后仍能导航（按组内顺序，不认具体词）。
   - 可用环境变量：`ZENTIMER_ADB` / `ZENTIMER_SERIAL` / `ZENTIMER_OUT`。
   - **每次起跑都复查真的进了计时态**，起不来就拉掉 `harness_ok`，全绿也不算通过 —— 这是被两轮「空跑恒真」教训逼出来的闸门。
   - 分组：L 组四时、M 组免打扰与复原底线、N 组新机专项（Android 15+ 环境覆盖、1.3 倍字体、深浅亮度分离、
     首屏无越界 + 圆环到时长行距离量化、设备级勿扰过滤回到基线、ZenPolicy 放行闹钟与媒体）；
     **合计 31 项，以结果 JSON 为准**。
   - 结果写 `ZENTIMER_OUT/verify_note15_result.json`，日志 `verify_note15.log`。
3. **三语屏幕文案对账**：`python tools/check_screen_copy.py`（需设备），出 `三语真机文案对账.md`。

### 5.2 取证口径（**不认脚本自称成功，只认对方产物**）

| 要证的事 | 只认这个 |
|---|---|
| 勿扰规则状态 / ZenPolicy | `dumpsys notification` 的 `mConfig` 段 `ZenRule.state` / `zenPolicy` |
| 设备勿扰 | `settings get global zen_mode` + `dumpsys` 的 `mInterruptionFilter` / `mZenMode` |
| 四时纸色 | `screencap` **原始像素**，与方案色逐一精确比对，不读代码里写的值 |
| 界面文字 / 布局 | `uiautomator dump` 的 `text` / `content-desc` / `bounds` |
| 版本号 | `dumpsys package com.yaohui.zentimer` |

脚本会临时改动勿扰授权（`cmd notification allow_dnd` / `disallow_dnd`）、月份注入钩子
（`settings put global zentimer_season_month`）、外观、语言、字体缩放，**退出前一律还原并停掉计时态**。
月份钩子是只读测试钩子，不进任何界面，生产用户无该 global 值时按真实日期走。

### 5.3 新增 feature 的规矩（用户反复要求）

- **先写 feature list 和 TC，再动代码**；验收通过后把 feature / TC 回填进
  `FeatureMap-TestCase-整体设计.md`（反馈表 + 新章节 + TC 表），同步 `CHANGELOG.md` / `RELEASE-NOTES.md` /
  `迭代计划.md` / `交付说明.md` / `UI文案复核表.csv`。
- 每轮结论要写清**「未验」**，不冒充已验收。当前仍未验：
  - 真实微信 / 短信 / 陌生来电 / 收藏联系人 / 重复来电 / 系统闹钟在免打扰下的行为；
  - 结束铃与间隔铃在免打扰生效下的**实听**、状态栏胶囊与 AOD 实拍、锁屏 30 分钟走时；
  - TalkBack 读原生 `NumberPicker` 当前值；
  - ColorOS / One UI / 双卡 / 非小米 Android 8~15；
  - 四时深浅四档对比度的人眼判读（脚本只验了亮度分离）；
  - TC-I10 完整一轮短计时回归（会真响铃）。
- **改完必须 grep 同一件事的旧说法**：文档里「20~30 分钟」「串行」「只看总数」「不循环」「无任何授权请求」
  都是这么被用户在不同角落发现的。

---

## 6. 已踩过的坑（别重犯）

1. **验收脚本空跑恒真**：完成态按钮叫「再来一次」不叫「开始静坐」；强杀后没重新拉起 App 就在桌面盲点。
   现在 `start_sitting()` 每次复查计时态 + `harness_ok` 闸门。前两轮 `22/24` 与 `harness_ok=False` 都**不作依据**。
2. **固定像素坐标跨机失效**：换 1280×2772 新机后旧脚本全歪。现在一律按控件树定位、按比例取色。
3. **判据自己写错却当成产品缺陷**：秋档方案色 `#F7F2E8` 的 B=232，旧脚本 `min(c) >= 235` 把它判失败。
   改成与方案色逐一精确比对，反而更严。
4. **`addAutomaticZenRule` 隐藏硬校验**：不设配置 Activity 会抛 `Lacking enabled CPS or config activity`，规则根本建不出来。
   必须 `setConfigurationActivity(...)` + Manifest 的 `AUTOMATIC_ZEN_RULE` intent-filter。
5. **停用路径把「当前有无权限」当前置条件**：权限被撤销就跳过复原，直接违背底线。现在先用本地 id 无条件试停用，再扫规则表兜底。
6. **自绘控件没有开 / 关语义**：TalkBack 与自动验收都看不见。用 `clearAndSetSemantics { role = Role.Switch; ... }` 补。
7. **原生滚轮手感被「架构洁癖」否决过**：自绘手势又脆弱又 low。结论已反转，必须用原生 `NumberPicker`，
   且 `wrapSelectorWheel = true`（真机证明系统计时器是循环的）。
8. **档位层与圆环状态词打架**：档位层没有「确定」按钮，圆环却写「调好后点确定」。
9. **新 pref 键名撞上老包遗留值**：自定义时长读到旧的 1 时 13 分。键名带版本号（如 `v224_custom_duration_min`）。
10. **连本地端口被代理劫持**：`http_proxy` 指向 127.0.0.1 且 `no_proxy` 空时，连本地服务走代理返 502，被误判「服务没起来」。
11. **`.bat/.cmd` 用 LF 行尾**会被 cmd 拼成一行报「不是内部或外部命令」，中文还乱码。一律 CRLF + 纯 ASCII。
12. **结论变了别删旧版推演**：保留原文，新版叠加在前并注明以哪版为准。

---

## 7. git 与发布规矩

- **有库才提交，绝不新 init**；是库且有代码 / 配置 / 文档改动才 `git add` + `commit -m "为什么"`（写原因，不写「更新」）。
  纯内容任务不提交；禁 git hook 自动触发。
- 主库 `master` **没有 origin**，别对着它 `git push`。
- 公开发布链：`zentimer/.craft/tmp_remote/` 是独立 git 库（branch `main`，origin 为 SSH 的 `git@github.com:yaohui-git/zentimer.git`）。
  公开库历史 tag：`v2.3 / v2.4 / v2.9 / v2.17`；主库本地稳定 tag `v2.24-basic-stable-2` / `v2.25-basic-stable-3`。
  2026-11-11 已完成一次 `git push origin main --tags`：公开库 `main` 由 `740e8c7 → dd5aa13`，
  远端 tag `v2.25-basic-stable-3` 可见；**这次是本轮唯一一次推送，后续别主动再推**。
  用 `tools/make_oss_repo.py` 生成干净公开目录，`ROOT_DOCS` 是白名单，**外部文档要进公开库得加进白名单**。
- `releases/` 只留一个与代码同步的最新 APK，文件名纯 ASCII；历史版本走 GitHub Release 附件，说明在 `RELEASE-NOTES.md`。
- `.gitignore` 排除：`.craft/`（真机证据）、构建产物、`local.properties`、`*.jks`、`keystore.properties`、
  `releases/*.apk`、全部图片 / 音视频 / 压缩包（但 `ZenTimer/app/src/main/res/` 的工程素材、`screenshots/`、`assets/`、`store/` 有例外）。
- 推送前按规矩预告：SSH push 可能弹出授权确认框，**需要人本人点同意**，执行者代点不了。

---

## 8. 交接时的真实状态（2026-11-11 收尾）

### 8.1 已发布 / 已验收（可作为基线，别推翻）

| 项 | 事实 |
|---|---|
| 稳定版 | **v2.25 / versionCode 26 / versionName `2.25`** |
| 主库本地 tag | `v2.25-basic-stable-3` → `291ebd6`（第三个稳定可 release 版本） |
| 公开发布库 | `.craft/tmp_remote/` commit `dd5aa13`；`git push origin main --tags` 成功，远端 `main` `740e8c7..dd5aa13`，远端 tag `v2.25-basic-stable-3` 可见。公开库已含本文件与 `releases/zentimer-v2.25.apk` |
| 新机真机验收 | `Redmi Note 15 Pro / Android 16 / SDK 36 / HyperOS 3`，**31/31 PASS + `harness_ok=True`**。结果 JSON 在 `.craft/rea/v225/note15/verify_note15_result.json`（**该目录被 gitignore，公开库看不到**） |
| 关键证据 | `mInterruptionFilter` 起跑中 `1→2`、结束回 `1 / ZEN_MODE_OFF`；ZenPolicy `alarms=allow, media=allow`；四时四档像素与方案色逐一相等；圆环到时长行 `936px ≈ 288dp` |
| 静态闸门 | `47 PASS / 3 WARN / 0 FAIL`；三语各 122 条、代码引用 116 key、未定义 0；证据不变量 `8 PASS / 0 FAIL` |

### 8.2 工作树里压着的改动（**未出包、未真机回归、未进公开库**）

| 文件 | 改了什么 | 状态 |
|---|---|---|
| `Lang.kt` | 四条文案三语：`随节气换肤` / `随四季更替，自动更换主题色` / `静坐中不更换主题色` / `静坐时不被通知与来电打断，结束后自动恢复`（含繁体、English） | 静态闸门过；真机未验 |
| `DurationField.kt` | 档位 `13→15.sp`、`padding(vertical = 10.dp) → 14.dp`（整格约 48dp）、「自定义」回显 `10→11.sp` | 静态闸门过；真机未验 |
| `tools/verify_device.py` | 新建的分辨率无关真机主验收脚本；`EXPECT` 字典已同步上面四条新文案 | 尚未在新文案的安装包上跑过 |
| `UI文案复核表.csv` | 从源码重刷 **160 行**，含「AI 建议（留空待填）」空列 | 已导出，见 §9.1 |
| `UI设计评审-苹果设计师视角-2026-11-11.md` | 我方初评 + 字号落地状态补记 | 新建 |
| `交付-豆包设计评审-提示词.md` | 终版提示词（改成读同一个 git 库、截图自行捕捉） | 新建 |
| `HANDOFF-接手指南.md` | 本文件 | 扩充 |

**这批改动没有 bump `versionCode` / `versionName`，没有 `assembleRelease`，没有新 APK，没有安装真机，
没有三语屏幕对账，没有再 push。** 别把它当成已发布行为，也别在评审里写成「已支持」。

### 8.3 已按用户指示暂停的事项

- 全量界面截图采集脚本与截图打包（zip）：**暂停，不采集、不打包**，由豆包自行捕捉。
  `tools/capture_screens.py` 与 `.craft/rea/v225/review_pack/30_截图/` 已删除。
- 所有新 feature 开发：**暂停**。含「两个时长入口只留一个」「点圆环就地展开」「首屏布局重构」
  「档位选中态语义」「三处时长层级统一」等结构性改动。
- 因此**不生成 zip**：资料改为「同一个 git 库内直接读」，提示词第二节已列库内真实文件路径。
- 编译新包、真机回归、再次 push：**都没做**。用户本轮只要求 push 一次，已完成。

### 8.4 改文案后的连带动作（别漏）

改 `Lang.kt` → 重跑 `tools/export_ui_copy.py` → 同步 `tools/verify_device.py` 顶部 `EXPECT` 字典
（否则 M16 三语检查会误报）→ 重跑三道静态闸门 → 出包后跑 `tools/check_screen_copy.py`。

---

## 9. 豆包接手：暂停令、用户原话、待办清单

### 9.1 用户最新指令（逐字，2026-11-11 07:25）

```
再导出全量文案给豆包。>此类任务暂停
所有新feature的开发都暂停，都写到交接文档里，交给豆包接着干。在HY4免费额度用光前
```

同一口径的前一条指令（逐字）：

```
现在写全量界面截图的采集脚本，此类任务暂停
豆包接手后，ta自己捕捉把，你提供提示词就可以了。
```

**执行口径（不是替用户改话，是把「做到了哪一步」写清）**：

- 「再导出全量文案给豆包」**已完成** → `UI文案复核表.csv`，160 行，由 `tools/export_ui_copy.py` 从 `Lang.kt` 自动重刷。
- 「此类任务暂停」延续上一轮：截图采集与打包暂停；本轮顺带不生成 zip、不编译新包、不真机回归、不再 push。
- 「所有新 feature 的开发都暂停」：结构性 UI 改动一律留给评审结论，先给建议、后拍板、再动代码。
- 「都写到交接文档里」：即本 §9 的待办清单。
- 「交给豆包接着干（在同一个 git 库里）」：资料全部放在 **git 跟踪路径**，
  `.craft/` 被 gitignore，豆包在库里看不到，别指望它。

### 9.2 本轮用户主消息（逐字，需求来源，未改写）

```
* 红米Note15Pro已连接，要不要重做一轮测试？
* 稳定后，再次tag，作为第三个稳定可release版本。并push到github一次。然后再开始下面的工作
* 静坐时长点开后，可选项内容的字体是否可以大一些，让点选更从容些呢？ 类似问题从苹果设计师得角度，当前的UI都有那些优化机会呢？包括首屏界面的设计布局，是否还有优化空间？
* 静坐时长的设定，现在有两个入口，要不要只保留一个？
* 点击圆环后，静坐时长的选项“弹出”这事儿有点不显眼。首次使用的用户有可能误以为点了也白点，界面没有变化。因为选项那一行出现的位置距离圆环是有点远的，超出了人的视野。假定人的注意力都放到圆环&附近的话
* 【随节气微调】--【随节气换肤】，文字说明改为：随四季更替，自动更换主题色
* 【静坐时免打扰】的说明要改为功能描述，也即用户可有的预期。而不是当前的莫名其妙的一句话。
* 再次导出全部的UI文案，随后交给豆包统一扫描和给出优化建议
* 类似问题从苹果设计师得角度，当前的UI都有那些优化机会呢？包括首屏界面的设计布局，是否还有优化空间？这一条，可以把全量的界面截图导出打包（zip），也一并交给豆包，让ta给一轮建议
* 是否还有其他豆包可以思考、参与设计建议合集检查的呢？一并给我资料和提示词。注意如果有多项，要汇总，给我一份压缩包和一份提示词
```

补充原话（逐字）：

```
原任务：禅意冥想计时器，做过git push
```

```
USB调试已打开
另外这轮结束后，做下交接文档，包括开发环境、代码说明、feature sets和TC说明等，我可能会交付豆包继续优化改进（在同一个git库里）
```

> 「禅意冥想计时器」是用户对**项目**的口语指代，不是界面文案；界面文案禁「冥想」，口径不变，
> 逐字保留只是不篡改需求来源。
> 主消息里「全量截图导出打包 / 一份压缩包」两条已被后面的暂停令覆盖：**不采集、不打包**。

### 9.3 待办清单（建议顺序；每项写明要不要先确认）

| # | 事项 | 为什么停在这 | 接手后怎么做 | 验收 | 先确认需求？ |
|---|---|---|---|---|---|
| 1 | **全量 UI 文案复核**（提示词任务 A） | CSV 已导出，未回填 | 逐行只在最后一列填 `OK` / `改：三语` / `问：` → `tools/apply_ui_review.py` 生成补丁 → 三语闸门 | `check_lang.py` 零未定义；`check_ui_copy.py` 0 FAIL；出包后 `check_screen_copy.py` | 不用（规则已定稿）；但改文案必须三语同步 + 重刷 CSV + 同步 `EXPECT` |
| 2 | **界面设计评审**（任务 B / C / D） | 等外部结论 | 豆包按 `交付-豆包设计评审-提示词.md` 第五节**自行捕捉**截图，给 P0/P1/P2、四问明确取舍、八维合集检查 | 每条建议带「现象→依据→建议→代价→可否验证」 | 不用；**但本轮只给结论，不改结构性代码** |
| 3 | **结构性 UI 改动** | 用户已暂停所有新 feature | 两个入口 / 就地展开 / 首屏节奏 / 选中态语义 / 三处层级统一，等评审 + 用户拍板后，**先写 feature list 与 TC 再动代码** | 新 TC 回填 `FeatureMap-TestCase-整体设计.md`，`verify_device.py` 真机回归 | **必须先确认**（用户原话「需求不清楚，要和我确认，不要猜」） |
| 4 | **把 §8.2 的改动做成正式版本** | 未出包 | `versionCode +1` → `assembleRelease` → 复制到 `releases/zentimer-vX.X.apk`（纯 ASCII）→ 真机安装 → 31 项主验收 + 三语对账 + 1.3 倍字体 → 回填 CHANGELOG / RELEASE-NOTES / FeatureMap TC / 交付说明 → 主库 commit | 31/31 + `harness_ok=True`；三语屏幕对账零偏差 | 要不要再 push **必须用户明说**，别自作主张 |
| 5 | **未验项补测**（§5.3 清单） | 本机条件不够 | 真实来电/短信/闹钟、结束铃实听、AOD 实拍、TalkBack 读原生滚轮、ColorOS/One UI/双卡、非小米 Android 8~15、四时对比度人眼判读、TC-I10 短计时回归 | 逐项留证，**别把「未验」写成「已验证」** | 不用 |
| 6 | **对外文档一致性** | 任务 D-3 | 核对 README / 隐私政策 / 商店文案 / CONTRIBUTING 的「唯『静坐时免打扰』需一次系统授权、默认关闭」 | 与 §3 对外授权口径一致 | 不用 |
| 7 | （可选）**生成 zip 资料包** | 暂停令覆盖了「一份压缩包」 | 若用户仍要，按提示词第二节的库内文件清单打包；`*.zip` 被 gitignore，只作本地交付 | 文件齐全、能打开 | 要用户明确说「还是打包吧」 |

### 9.4 交付物位置（同一个 git 库，别去 `.craft/` 找）

| 用途 | 文件（都在 `zentimer/` 根目录，git 跟踪） |
|---|---|
| **唯一提示词** | `交付-豆包设计评审-提示词.md` |
| **全量 UI 文案** | `UI文案复核表.csv` |
| 我方初评（可反驳） | `UI设计评审-苹果设计师视角-2026-11-11.md` |
| 接手指南 | `HANDOFF-接手指南.md`（本文件） |
| 上一轮复核规则 / 报告 / 决定 | `UI文案复核说明.md` / `UI文案复核报告_豆包.md` / `UI文案复核决定.md` |
| 功能与 TC 总台账 | `FeatureMap-TestCase-整体设计.md` |
| 真机主验收脚本 | `tools/verify_device.py` |
| 静态闸门 | `tools/check_ui_copy.py` / `check_lang.py` / `check_evidence_invariants.py` / `check_screen_copy.py` |
| 文案导出 / 回填 | `tools/export_ui_copy.py` / `apply_ui_review.py` |

> `.craft/rea/v225/review_pack/` 下曾有 `00_先读我.md` / `10_用户反馈原话.md` / `60_代码结构摘要.md` /
> `80_真机布局量化证据.md` 等草稿，但 **`.craft/` 被 gitignore**，同一个 git 库里看不到。
> 其要紧内容（用户原话、模块地图、字号阶梯、控件树 bounds 与 288dp 距离）已并入本文件与初评文件，
> **一律以 git 跟踪路径为准**。两份原始方案在用户本机 Downloads，不在库内，需要时索取。

### 9.5 最短开工路径

1. 读本文件 §0 → §3 → §9，跑三道静态闸门看基线。
2. 读 `交付-豆包设计评审-提示词.md`，先完成任务 A / B / C / D 的**评审输出**（含自行捕捉截图）。
3. 拿评审结论找用户拍板；结构性改动一律**先确认需求**，再按 §5.3 写 feature list + TC。
4. 动代码 → 静态闸门 → bump 版本 → 编译 → 真机 31 项 + 三语对账 → 文档回填 → 主库 commit。
5. 是否 push 由用户明说。**改完 grep 同一件事的旧说法**（§5.3 末条），别让文档里留下
   「一份 zip」「截图已打包」「无任何授权请求」「不循环」这类过时口径。

