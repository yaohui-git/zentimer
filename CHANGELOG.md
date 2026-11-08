# 更新日志 / Changelog

> **协议与仓库地址**：v2.3 发布起采用 **Apache-2.0**（`LICENSE` 标准全文 + `NOTICE.md` 素材例外），仓库 <https://github.com/yaohui-git/zentimer>。下方早期记录中提到 MIT、开源地址「待定」处均为当时状态，**以当前 `LICENSE` / `NOTICE.md` / `AppInfo.kt` 为准**。

## v2.9（versionCode 10，2026-11-08，Android 16 实机定位：通知权限 + 隐藏 API 崩溃）

用户反馈（**Redmi Note 15 Pro / Android 16 / HyperOS**）：「锁屏时的计时界面（类似系统闹钟）看不到」「切到后台顶层看不到计时插件」；另一台 **Redmi 10X 4G（Android 12）**「通知栏能看到，但按钮只有 End」。

接上真机后，前两版关于锁屏的推断（系统锁屏通知开关、厂商限制）**都不是主因**，真实原因有三层：

1. **一开计时就闪退**——这才是这台机器「什么都看不到」的直接原因。日志原文：
   ```
   hiddenapi: Accessing hidden method Landroid/app/Notification$Builder;->setRequestPromotedOngoing(Z)…(api=blocked) … denied
   FATAL EXCEPTION: java.lang.NoSuchMethodError: No virtual method setRequestPromotedOngoing(Z)…
       at com.yaohui.zentimer.TimerService.buildNotification
   ```
   该机 Android 16 里这两个方法是 **@hide**：compileSdk 37 的公开 SDK 有，设备上没有。v2.4~v2.8 直接调用，于是每次开始计时都崩进程。现改为「反射取方法 → 取不到就走 extras 通道 → 两条都不行就退回普通通知」，任何一步失败都不会影响计时与铃声。
2. **通知权限从来没申请过**。`dumpsys notification` 里写着 `AppSettings: com.yaohui.zentimer importance=NONE userSet=false`：Android 13 起 `POST_NOTIFICATIONS` 默认不授予，而本应用为守住「零授权」一直没申请，所以**这台机器的通知栏里一条通知都没有**。10X 是 Android 12，不需要该权限，因此那边一直能看见。现在首次进入时申请一次（本应用唯一的系统授权框，只与计时可见性有关），设置页「计时通知」里也能再次发起或跳系统设置。
3. **extras 通道实测可用**：`android.requestPromotedOngoing=true` + `android.shortCriticalText=4:07`（两个 key 取自 `NotificationCompat`），实机通知标志里出现 `PROMOTED_ONGOING`，状态栏出现倒计时胶囊、锁屏顶部出现卡片，带「暂停 / 结束」。

另：通知动作按系统闹钟补齐为 **暂停 ⇄ 继续 + 结束**（原来是只有结束）。

**已知限制（写清，不再反复猜）**：HyperOS 对「实时动态」的刷新有节流，胶囊里的数字不是每秒跳，暂停/恢复、跨阶段会立即刷新；胶囊与锁屏卡片都要求通知权限已授予。

## v2.5（versionCode 6，2026-11-08，锁屏与状态栏计时可见性，按 AOSP Clock 实现重做）

> **本节关于锁屏/胶囊的结论已被 v2.9 取代。** v2.5 读到的是「实现写法」层面的正确（AOSP Clock 确实用 `setRequestPromotedOngoing` 而不是 `setAlarmClock`），但漏掉了两件更要命的事：设备上这两个方法是 @hide（直接调用崩进程）、以及通知权限根本没授予。实现细节保留在下方作对照，结论以 v2.9 为准。

**用户反馈**：「锁屏时的计时界面（类似系统闹钟）看不到」「Note 15 Pro 里切到后台，顶层看不到计时插件」。

按 AOSP `packages/apps/DeskClock` 的计时器实现核对后，发现 v2.4 的做法从根上偏了：

- **AOSP 的计时器不使用 `setAlarmClock`。** 它用的是「常驻通知 + `CATEGORY_ALARM` + `VISIBILITY_PUBLIC` + `setUsesChronometer` + `setChronometerCountDown` + `setRequestPromotedOngoing(true)`（`data/TimerNotificationBuilder.java`，Android 16 分支）。`setAlarmClock` 是**闹钟**（Alarm）那条线用的，套到计时器上会让系统误以为设了一个闹钟——状态栏多出闹钟图标、锁屏「下个闹钟」被改写，而且 Android 16 起该 API 已不在公开 SDK 里。本版删除。
- **状态栏胶囊不出来的直接原因**：`setRequestPromotedOngoing(true)` 必须配合清单里的 `POST_PROMOTED_NOTIFICATIONS`（非运行时权限），缺了它系统会**静默忽略**提升请求。本版补上。
- 补齐 AOSP 同款的通知属性：`setShowWhen(false)`（`when` 只作倒计时基准，不显示成时间戳）、`setLocalOnly(true)`、`setAutoCancel(false)`。
- 前台服务通知默认会被系统压 10 秒才露出，补 `FOREGROUND_SERVICE_IMMEDIATE`。

其余两条配套改动：

- **锁屏点通知直接看倒计时**：计时进行中时界面允许显示在锁屏之上（`setShowWhenLocked`，系统闹钟响铃页同一做法），不必先解锁；没在计时就不放行。
- **设置页新增「计时通知」自检**：读出通知总开关、计时渠道是否被关、实时动态是否可用，整块可点，一键跳到本应用的系统通知设置。

**能力边界（写清楚，避免再误判）**：状态栏那枚倒计时胶囊是 Android 16「实时动态」的能力，Android 15 及以下第三方应用**没有**对应接口，只能有通知栏里的常驻通知（含系统自己走秒的倒数）。锁屏上能否看到卡片，还受系统「锁屏通知」开关控制。

## v2.4（versionCode 5，2026-11-08，版本号治理 + 本轮修复定版）

**这一版首先修的是「版本号一直没变」这个流程问题。** 前几轮（修订六~九）每次出包都把 `versionCode` 留在 4、`versionName` 留在 2.3，代价是：

- 安装器与商店看不出这是新版本。商店上传要求 `versionCode` 单调递增，照原样继续传会被判重复包或被拒。
- 手机上「关于」永远显示 2.3。响铃先后出现过 320 ms、起点对起点 1.8 秒、播完再静置 1.8 秒三种行为，全都自称 2.3，出问题无法用版本号区分。
- GitHub 上同名文件 `releases/静息计时-v2.3.apk` 被反复覆盖，早下载的人以为拿到的是同一份。

现在的做法：

- **版本号只有一个来源**：`app/build.gradle.kts` 的 `versionCode` / `versionName`。界面通过 `BuildConfig` 读取（`AppInfo.VERSION` 不再手写一份），两边不可能再对不上。
- 本轮累积改动（修订六~九）正式定版为 **v2.4 / versionCode 5**，产物名 `静息计时-v2.4.apk`；v2.3 的包保留在 `releases/` 里作为历史，不再被覆盖。
- 规则：以后每次出包都 +1 `versionCode`，哪怕只改一行；`versionName` 按「主.次」递增。

> 下方「修订六~九」的记录保留原文（当时确实都标着 versionCode 4），内容已全部包含在 v2.4 里。

## v2.3 修订九（versionCode 4，2026-11-08，响铃口径纠正 / 锁屏按系统闹钟）

**响铃间隔：口径纠正，改为「上一声结束之后 1.8 秒」**

- 修订八写成「起点对起点至少 1.8 秒」，对短铃没问题，但有长余音的颂钵 / 铜锣会被截断：余音还在响，下一声就起了，听起来仍偏挤。用户原意是**第一声完全响完，再静置 1.8 秒**才起第二声。
- 现在的排程：`等待 = (音频声明时长 - 已经过的时长) + 1.8 秒`，正常情况前一项为 0，即「播完 + 1.8 秒」；个别 ROM 提前回调「播完」时按声明时长补齐，仍落在真实结束之后 1.8 秒。
- 因此两声**起点**之间的距离 = 这一声自身长度 + 1.8 秒：木鱼（0.45 秒）约 2.2 秒，铜锣（约 18 秒可听段）约 20 秒。常量只有一个 `TAIL_GAP_MS`。

**锁屏倒计时：改按系统自带闹钟的做法（不用媒体卡片）**

- 通知渠道改为独立的 `timer_alarm`（`IMPORTANCE_HIGH`、静音、`lockscreenVisibility = PUBLIC`），旧的低优先级渠道 `timer_player` 在升级时删除，避免系统设置里留下无意义条目。
- Android 12~15：把通知声明成 **alarm clock 类型**（`setAlarmClock`）。这是系统闹钟在锁屏、息屏、状态栏被优先展示的同一机制，未解锁即可看到倒计时。注意 **compileSdk 37 的公开 SDK 已删掉 `setAlarmClock` / `AlarmClockInfo`**（`javap` 核对 api 36/37 均无），只能按名字反射调用。
- Android 16+：改用官方「实时动态」（`setRequestPromotedOngoing` + `setShortCriticalText`）。这两个方法在 API 36/37 已转正，不再反射。
- 前台服务启动时仍用不带闹钟属性的通知；闹钟属性留给随后的 `notify` 补。ROM 若拒绝，捕获后自动退回普通高优先级通知并把开关关掉（避免每秒重试），铃声与计时不受影响。
- 说明：是否最终显示在锁屏，仍受系统「锁屏通知」总开关约束，应用侧无法绕过。

## v2.3 修订八（versionCode 4，2026-11-08，语言刷新 / 响铃间隔 / 通知与锁屏）**（响铃口径已被修订九取代，见上）**

**修 bug：切 EN 后抽屉菜单还是中文**

- 根因：`Lang.t()` 是普通函数，读 `_mode.value` 但不建立快照订阅。`DrawerContent`、`SubPageBar` 这类独立 Composable 只调 `t()`，Compose 判定「参数没变」直接跳过，于是停在旧语言。
- 新增 `@Composable Lang.tx(key)`：内部 `collectAsState()` **并真的读 `.value`** 后再取词，调用方一定重组。抽屉 6 处文案改用它。
- 同类根因第二处：主页根组件原有 `@Suppress("UNUSED_VARIABLE") val langRev by Lang.mode.collectAsState()`，值从未被读取 —— 等于没订阅。实测表现就是设置页正文已变英文、顶部标题仍写「设置」。新增 `Lang.watch()`，根组件每次组合真读一次 value，标题/顶栏文案随语言即时更新。
- 教训记一条：`collectAsState()` 只是起了个收集器，**不读 `.value` 就不算依赖**。这个坑先踩了两次。

**响铃间隔：起点对起点至少 1.8 秒**

- 旧逻辑是「上一声**播完** + 320 ms」。短铃（木鱼 0.45 秒）算下来两声起点只隔 0.77 秒，连成一片；机型解码器若提前上报「播完」，间隔还会更短。
- 现在加硬下限 `MIN_ONSET_MS = 1800`：取 `max(余音后静置 1 秒, 1.8 秒 - 上一声已过时间)`，**与机型解码行为无关**，任何情况两声起点都 ≥ 1.8 秒。

**通知：系统自己走秒 + 可点 + 可结束**

- 改用 `setUsesChronometer` + `setChronometerCountDown` + `setWhen(现在 + 剩余)`：通知栏与锁屏上的时间由系统每秒自己翻，不依赖本进程重画（无限时长模式改为正向累计）。
- 通知每秒最多重发一次（原来 250 ms 一拍、4 次/秒），文案没变就不重发。
- 点通知回到计时页（`contentIntent`，`singleTop` 不叠实例）；右侧加「结束」动作，锁屏/通知栏直接收工。`ACTION_STOP` 走 `TimerEngine.stop()` 而不是只 `stopSelf()`，否则状态会留在「进行中」，下次打开被 `restore()` 当成还在练。
- Android 16+ 状态栏「实时动态」胶囊（系统闹钟同款）：`Notification.Builder.recoverBuilder` 拿回平台 Builder，再反射调 `setRequestPromotedOngoing` / `setShortCriticalText`。注意 `Builder.rebuild()` 是 `@hide`（公开 SDK 里编译不过），改用公开的 `build()`。方法不存在、ROM 不支持、权限没给，一律静默退回普通通知。

**实测（连着 adb 的是 Redmi `M2003J15SC` / MIUI 13 / Android 12 / API 31；**不是**那台华为折叠机）**

- 抽屉、设置页标题、主页文案均已随语言切换为英文（截图 `screenshots/13-drawer-en.png`、`14-settings-en-title.png`）。
- 通知实测字段：`when = 当前时刻 + 剩余`、`showChronometer=true`、`chronometerCountDown=true`、`vis=PUBLIC`、`actions=1("End")`、`contentIntent` 指向 `MainActivity`。
- **该机锁屏不显示任何通知**：用 `cmd notification post` 发的系统对照通知（importance=3、PRIVATE）同样不显示，`lock_screen_show_notifications=1`。结论是与本应用无关，属该机「锁屏通知」设置；如需锁屏可见，要在系统设置里放开。
- 状态栏胶囊需 Android 16，本机无法验证。

---

## v2.3 修订七（versionCode 4，2026-11-08，工程与包名统一）

- 项目根 `stilltimer/` → **`zentimer/`**，Gradle 工程 `StillTimer/` → **`ZenTimer/`**（`rootProject.name` 同步）
- 包名 `com.stilltimer.personal` → **`com.yaohui.zentimer`**（`namespace`、`applicationId`、Java 包路径三处一起改）
- 签名密钥文件 `stilltimer.jks` → `zentimer.jks`；主题 `Theme.StillTimer` → `Theme.ZenTimer`；`SharedPreferences` 名 → `"zentimer"`
- **⚠️ 断覆盖升级**：旧包名 / 旧证书的安装包都无法覆盖升级，需先卸载再装；旧设置与预设不迁移
- **签名密钥一并重做**（内测阶段，用户明确不纠结覆盖安装）：新库 `zentimer.jks`、alias `zentimer`、证书主体 `CN=zentimer`，SHA-256 `64047d93…80d9`；旧库备份在 `ZenTimer/assets-private/`（不入库）。此前所有安装包（含旧包名）均不可覆盖升级
- **响铃间隔**：连响次间隔由 320 ms 调整为 **1000 ms**，准备结束那几声不再挤在一起
  （此条已被修订八取代：改成「起点对起点 ≥ 1.8 秒」的下限算法，**以修订八为准**）
- **息屏不响**：计时期间持有 `PARTIAL_WAKE_LOCK`（普通权限、不弹框），修复息屏后到点不响、亮屏才补响
- **锁屏倒计时**：通知为 `VISIBILITY_PUBLIC`，锁屏可直接看到剩余时间；是否显示取决于系统「锁屏通知」开关
  （修订八补上了系统走秒的实时倒数，并实测确认本机锁屏通知被系统关掉了，见修订八）
- `meditation-timer/` 交接 demo 保留原貌（包名仍为 `com.stilltimer.personal`），不参与构建，与新包名不再冲突
- 31 文件 192 处替换，无残留；`check_lang.py` 拆分拼接的包路径已手工修正，三语对账 103/103/103

---

## v2.3 修订六（versionCode 4，2026-11-08，英文应用名统一）

- 用户拍板「改」：英文 locale 下的应用名由 `Still` 统一为 **`zentimer`**，与 README / 商店文案已有的口径一致
- 改动 2 处：`values-en/strings.xml` 的 `app_name`（系统桌面图标名）、`Lang.kt` 的 EN `app_title`（App 内顶栏 / 抽屉头）
- 中文「静息」、繁体「靜息」不变；英文问候语 `"Still awake"`、工程目录 `ZenTimer`、包名 `com.yaohui.zentimer` 均不是应用名，保留
- 版本仍 2.3 / code 4，签名证书不变，可直接覆盖升级；重编译后重新出包并同步仓库
- **修订四、修订五里「App 名称未改动」自本节起失效**，原文保留作对照

---

## v2.3 修订五（versionCode 4，2026-11-08，仓库改名）

- GitHub 仓库 `zeatimer` → **`zentimer`**，新地址 <https://github.com/yaohui-git/zentimer>（旧地址 301 重定向）
- `AppInfo.REPO` 同步，重编译后关于页开源地址显示新链接；README / NOTICE / ASSETS-NOTICE / CHANGELOG / 商店文案 / Pages URL 全部统一
- **App 名称未改动**（工程内 `app_name` 资源仍为「静息 / 靜息 / Still」）

---

## v2.3 修订四（versionCode 4，2026-11-08，主题 / 尺寸 / 说明 / 国际友人）

**新增配置**
- 主题色三档：跟随系统 / 深色 / 浅色。新增 `Theme.kt`（StateFlow + SharedPreferences），`MainActivity.kt` 增加 `StillTheme` 与深浅两套色板，色板经 `CompositionLocal` 注入，140 处 `C.xxx` 调用点零改动；浅色同步翻转状态栏图标、不铺星空底图
- 设置页新增主题三档按钮；三语文案 95 → **103 条**，`check_lang.py` 对账通过

**语言默认跟随系统（支持国际友人）**
- `Lang.defaultFromSystem()` 旧逻辑把非中文系统也归到简体；改为简体→HANS、繁体→HANT、**其余语言一律 EN**
- 新增 `lang_manual` 标记：未手动选过时每次启动重算系统默认；手动选过则尊重用户选择
- 设置页说明行右侧新增「↺ 跟随系统」小入口（不新增常驻控件层级），点了清标记并即时重判

**屏幕尺寸轻量适配（未做真机专项测试）**
- 内容列 `.widthIn(max = 560.dp)` + 居中；圆环 `ringSize()` 按最短边与横竖屏收敛（常规竖屏仍 268dp）
- 抽屉宽度按屏宽 84% 且不超过 300dp；铃声网格 `GridCells.Adaptive(150dp)`
- 折叠屏铰链感知 / 双栏布局复杂度高，按项目所有者判断不做

**说明文件与关于页**
- **App 名称未改动**（工程资源零改动）；说明文件应用名称统一「静息 / zentimer」，「极简禅意冥想计时器 / Minimal Zen Meditation Timer」降为定位描述；补进极简风、**从无任何运行时授权请求**、主题三档、尺寸适配、语言默认
- 新增「定制与合作」：寺院、道观等非营利组织优惠或免费，其他另议；README / 商店文案 / 关于页 / CONTRIBUTING 同步
- 隐私政策补记主题色与语言偏好为纯本机数据

---

## v2.3 修订（versionCode 4，2026-11-08，开源发布版）

**预设层级**（用户反馈「预设控件挤占时间设置区」）
- 主屏「常用预设」整块删除：标题 / 管理 / chip 行 / 保存 / 空态提示全部移出主屏
- 顶栏新增自绘书签图标（与菜单圆钮同尺寸、共用一行，不新增常驻高度）→ 点开底部面板
- 面板内：预设一点即应用并收起、显示配置摘要；「保存当前」「管理」收进面板；最近应用的一套高亮「已选」
- `PresetStore.lastId()` / `markApplied()` 记录最近应用的一套，删除该预设时同步清除
- 主屏因此把**时长 / 间隔 / 准备**完整还给首屏；抽屉「预设」入口保留作管理页落点

**开源发布**
- 协议 MIT → **Apache-2.0**；`NOTICE.md` 逐项声明 23 段录音 / 36 张图片不在授权范围内
- `AppInfo.REPO` 填入 <https://github.com/yaohui-git/zentimer>，关于页显示可点链接；三语许可文案同步
- 新增 `README.md`（含打赏与联系方式）、`CONTRIBUTING.md`、Issue / PR 模板、logo、真机截图、`releases/` APK
- 真机 M2003J15SC（Android 12 / SDK 31）验证：安装、抽屉、返回栈、试听、计时、关于页、截图、崩溃日志

**修订二 · 关于页（用户反馈）**
- 软件介绍（三语 `about_tagline`）由「一只需要计时和一声钵响的闹钟」换成 **「若人静坐一须臾，胜造恒沙七宝塔」**；繁体 / English 同步，抽屉头与关于页共用，抽屉头放行两行避免长译文被截
- 关于页作者 / 邮箱 / 微信 / 公众号由**四张独立卡片合成一张 2×2 信息栏**（`ContactGrid` + `RowScope.ContactCell`），点单项仍保留复制 / 发信动作；「反馈、建议与合作」收进同一栏底部
- 打赏卡片上移到信息栏正下方、开源地址之前，首屏即可完整看见；`README` 一句话介绍与 `STORE-LISTING` 三处简介同步换句

---

## v2.3（versionCode 4，2026-11-08）

按真机体验反馈重做交互，重点是「行为对了」而不只是「控件摆对了」。

**新增**
- **加微信打赏**：关于页一张卡片，点一下复制微信号 `pingyishufang` 并唤起微信。不做内嵌支付、不放收款码、不接任何支付接口（调研过程留在本地交付记录）
- **预设快捷条**：预设从抽屉里拿到主页，横向 chip 一点即应用，末尾「+ 保存当前」，右侧「管理」进完整页
- **侧滑抽屉**：居中弹窗换成 M3 `ModalNavigationDrawer`，从侧边滑入、支持边缘手势、点遮罩或返回收起
- **统一返回栈**：系统返回键逐层关闭浮层 → 抽屉 → 子页 → 主页；主页再按一次才退出，不再一按就退应用
- **试听确认**：铃声选择器改成底部 sheet，点卡片只试听并高亮、可反复比较，按「确定」才写回设置；图片区统一正方形，加「试听中 / 已选」状态
- 关于页补齐作者 耀慧、邮箱、微信、公众号、开源地址（待定），微信/公众号/邮箱点一下即复制或打开
- 保存预设对话框弹出自动聚焦；删除预设加二次确认
- `tools/check_lang.py`：三语 key 与代码引用的自动对账脚本

**修复**
- **开始铃时序**：改在准备倒计时归零、正式计时开始那一刻响；准备阶段只放背景音
- **准备阶段暂停按钮点了没反应**：`mainAction()` 在 PREP 错落到 `resume()`，现改为 `pause()`，可暂停 / 继续
- **准备阶段圆环恒为满环**：改按准备进度绘制，暂停时也保持
- **冷启动丢练习**：准备时间已在后台走完时，按预算的 `planned_end_wall` 续算正式计时或补结束铃，不再踢回 IDLE
- **恢复时重复走准备**：暂停剩余归零时直接收尾，不重播开始铃
- **退出界面后背景音消失**：`onDestroy()` 只在「主动结束且不在计时中」才释放音频，前台服务继续跑时保留
- 主动「结束」时掐掉仍在连响的铃
- 时长 / 间隔 / 准备三组上移到设置卡顶部，铃声行下移（时间比挑铃更常用）
- 计时中菜单入口改用 `statusBarsPadding()` 精确定位，不再叠加四边 insets

**验收口径更正**
- 旧文档写「本机无真机 / 无 adb」**不准确**：`adb.exe` 一直在 SDK 的 `platform-tools` 里，缺的只是连上的设备。本次已用 adb 做真机安装、控件树、截图与日志验收。

## v2.2（versionCode 3，2026-11-07）

**新增**
- 开始 / 间隔 / 结束铃可分别设置响 **1 / 2 / 3 次**（铃声行右侧 `×N` 轻触循环），次间 320 ms 留余音
- **预设组合**：把时长、准备、间隔、三种铃、背景音、响铃次数整套保存 / 命名 / 应用 / 删除，最多 6 个
- 右上角一个克制的菜单，内含 **预设 / 设置 / 关于** 三个入口
- **设置页**：简体中文 / 繁體中文 / English 三语即时切换，不重建 Activity、不打断计时；首次安装按系统语言定默认
- **关于页**：版本、联系作者、开源地址、MIT 许可、隐私政策、素材说明（离线可读）
- 时长补 **90 分钟 / 2 小时**；准备时间补 **60 秒 / 3 分钟 / 5 分钟**
- 启动图标改为自绘 **方案 B · 圆环计时**（细金环 + 进度弧 + 端点光点 + 12 刻度 + 打坐人形）

**修复**
- 全面屏顶部内容与状态栏 / 挖孔重叠：改用系统 `WindowInsets.safeDrawing` 统一避让，`shortEdges` cutout 模式；背景星空图仍铺满
- 通知栏文案补齐三语

**发布准备**
- 签名密码从 `build.gradle.kts` 移到本机 `keystore.properties`（不入库），提供 example
- `LICENSE` / `README.md` / `PRIVACY-POLICY.md` / `ASSETS-NOTICE.md` / `STORE-LISTING.md`
- `tools/asset_privacy.py`（开源前素材净化 / 恢复）、`tools/make_oss_repo.py`（干净仓库打包）
- `store/` 512 图标、1024×500 宣传图、三套图标方案对比
- versionCode 3 / versionName 2.2，release 与 debug 同签名，可直接覆盖升级

## v2.1（versionCode 2，2026-11-07）

按静态梳理出的功能清单，补齐与参考实现的差距：
- 声音选择改为实物图片卡片网格，设置行带缩略图
- 9 种铃 + 静音轨 + 13 种环境音，共 23 段内置录音；36 张内置界面图
- `TimerService` 前台服务，锁屏 / 切后台准点响铃，通知渠道名 `Timer Player`
- 无限时长模式、星空实景铺底、`close.png` 关闭图标
- 功能清单逐项验收 5/5 通过；分析与证据留在本地，不随公开仓库发布

## v2.0（versionCode 1，2026-11-07）

首个可安装版本：核心计时链路（选时长 → 准备 → 圆环 → 间隔铃 → 结束铃）、中英双语、8 铃 + 3 背景音（后改为内置实物录音）、铜钵启动图标、冷墨蓝 + 暖米金配色。课程 / 社区 / 账号 / 统计全部裁剪。
