# Insight Timer 20.40.0 · Feature Map 与裁剪决策

> 目标：`com.spotlightsix.zentimerlite2` / 20.40.0 / versionCode 58447 / minSdk 32 / targetSdk 36
> 方法：REA（`rea-agents@6.5.0` MCP）+ craft anything v0.3.0 CLI，全部结论来自**原版交付物本体**，不是猜测。
> 结论口径：静态清点只能证明「包里有什么、声明了什么」，不能证明运行时行为；运行时推断一律标注为推断。

---

## 0. 证据链（可复核）

| 步骤 | 工具 | 产出 | Evidence ID |
| --- | --- | --- | --- |
| 打开 xapk | REA `open_binary` | zip，7 节点，sha256 `51815511…f31` | — |
| 清点 xapk | REA `inspect_artifact` | 7 文件 / 0 完整性矛盾 | `ev_7604e92a…5e5` |
| 打开主包 | REA `open_binary` | apk，sha256 `e0c6a840…e3a` | — |
| **全量清点主包** | REA `inspect_artifact` | **14,279 项检查 / 4,638 节点 / 4,821 文件 / 0 完整性矛盾** | `ev_cc6a3c17…fdf` |
| 抽查关键资源 | REA `inspect_analysis_view` | `AndroidManifest.xml` 85,528 B、`resources.arsc` 950,520 B，hash verified | `ev_96a36019…886` |
| 目标分诊 | craft `core-target-triage` | Android APK，91,911,251 B，推荐 android 工具集 | `ev_a50ee707…8e0` |
| **包解码** | craft `android-package-show` | 包名/版本/SDK/权限/123 组件/5 DEX/签名，内置解析器 | `ev_afcbabb4…20c` |
| **应用图谱** | craft `android-app-graph-build` | DEX、资源、Flutter 运行时识别 | `ev_d8f9a6da…634` |
| 资源表解码 | craft `android-resources-read` | 11,328 条目 / 3,661 字符串 / 1,195 默认串 | `ev_391e9a6c…18d` |
| Dart 快照 | craft `android-dart-snapshot-show` | **失败**（见第 5 节），如实记录 | `ev_bef05cd2…a9e` |
| 裁剪对比 | craft `core-archives-compare` | 原版 → v2.0 逐文件差异 | `ev_a538fae6…3e0` |
| 声明验收 | craft `app-rebuild-claim-check` | **5/5 expectations satisfied, verified** | `ev_222c7c47…b4b` |
| 证据归档 | craft `core-evidence-export` | 1.2 MB / 5 records | `.craft/investigation/evidence-bundle.json` |

---

## 1. 原版体量（这是「高仿为什么要先画地图」的理由）

| 维度 | 原版实测 | v2.0 | v2.1 |
| --- | --- | --- | --- |
| 包内文件 | **4,820** | 146 | ~170 |
| DEX | 5 个 / 约 40 MB / 约 2.7 万个类 | 单 Kotlin 模块 | 单 Kotlin 模块 |
| 资源 | 2,455 `res/` + 2,038 `assets/` | 11 mp3 + 6 图标 | 23 mp3 + 36 实物图 |
| Flutter 资产 | 1,510 图 / 342 SVG / 30 mp3 / 133 JPG / 19 JSON / 12 字体 | — | 只取计时器相关 |
| 声明组件 | **123**（62 activity / 24 service / 27 receiver / 9 provider / 1 alias） | 1 activity | 1 activity + 1 service |
| 权限 | **43 项** | 0 | **3 项**（仅前台保活） |
| 三方 SDK | Firebase / Facebook / Stripe / Billing / Agora RTC / Health Connect / Branch / Crashlytics / Media3 | 无 | 无 |

原版不是「一个计时器」，是一个内容平台，计时器只是其中一个模块。不做 feature map 直接照着 demo 做，必然把模块边界搞错——这正是上一轮的问题。

---

## 2. Feature Map（11 个模块 + 裁剪决策）

| # | 模块 | 静态证据（原版包内） | 用户可见功能 | 决策 |
| --- | --- | --- | --- | --- |
| 1 | **计时器核心** | `assets/timer/` 125 文件：95 张乐器实物图 / 30 段 mp3 / `TimerIcons.ttf`；`TimerPlayer` service；`SleepMixerForegroundService`；`SCHEDULE_EXACT_ALARM`；`timer_notification_channel_name = Timer Player` | 选时长、准备、圆环倒计时、开始/间隔/结束铃、背景音、锁屏响铃 | ✅ **保留并高仿** |
| 2 | 引导课程 | `beginner_kit.json`、`course_audio_bar.json`、`topic_parents.json` | 新手课、课程播放器 | ❌ 砍（用户明确不要） |
| 3 | 内容库 / 专题 | `topics.json`、`topic_sleep.json`、`ai_tags_{en,de,es,fr,it,ja,pt-BR,ru}.json`（8 语言，最大 1.2 MB）、1,510 张配图 | 冥想曲库、专题、AI 标签检索 | ❌ 砍 |
| 4 | 社区 / 团体冥想 | `group_meditation_{filled,share,scheduled_*}.svg`、`profanity.json`、ShareFileProvider | 团体共修、分享、评论审核 | ❌ 砍 |
| 5 | 睡眠 / 健康 | `topic_sleep_star_twinkling.json`、`animated_reminder_{day,night}.json`、`health.READ_SLEEP / READ_HEART_RATE / READ_HRV / READ_RESPIRATORY_RATE`、Agora RTC 10.6 MB | 睡眠故事、健康数据、实时音频 | ❌ 砍 |
| 6 | 音乐 / 播放列表 | `b2-*.mp3`（7）、`dbs_*.mp3`（3）、`ic_playlist_*.svg`、`MediaSessionService` | 歌单、锁屏媒体控制 | ⚠️ **部分保留**：只取 3 段 dbs 作环境音，歌单/播放列表砍 |
| 7 | 账号 / 付费 | `BILLING`、`ProxyBillingActivity`、`StripeFileProvider`、`firebaseinitprovider`、`FacebookInitProvider`、`AD_ID`、`branch.json` | 登录、订阅、支付、归因 | ❌ 砍（引入联网与隐私权限） |
| 8 | 统计 / 打卡 | `mock_global_stats.json`、6 个 `consecutive/intention` AppWidget receiver | 连续天数、练习记录、桌面小组件 | ❌ 砍（v2.0 曾保留「练习记录」入口，已删） |
| 9 | 提醒 / Widget | 8+ 个 `APPWIDGET_UPDATE` receiver、提醒动画 JSON | 定时提醒、小组件 | ❌ 砍 |
| 10 | 多语言 | 8 语言 AI 标签 + 多 locale 资源表 | 跟随系统语言 | ✅ **改造保留**：只做中/英，且改为**应用内一键切换、不打断计时** |
| 11 | 品牌资产 | `ic_logo_text.png`（Insight Timer 字标）、`icon.png`（铜钵） | 启动图标、字标 | ⚠️ 铜钵图标按用户指示**复用**；字标不用 |

---

## 3. 保留下来的核心链路（裁剪后的完整闭环）

```
启动 → 选时长（含无限）→ 选开始/间隔/结束铃（9 种实物颂钵 + 静音）
     → 选背景音（13 种）→ 准备倒计时 → 圆环计时（沉浸 + 星空底图）
     → 间隔铃（可关）→ 结束铃 → 再来一次
     全程：中英一键切换不打断、屏幕常亮、前台服务锁屏准点响
```

砍掉的是模块，不是链路上的环节。每个保留环节都对照原版实物素材做了补齐（见 `高仿分析-02-界面交互对照.md`）。

---

## 4. 权限裁剪的依据

原版 43 项权限里，与计时核心相关的只有 4 类：`WAKE_LOCK` / `FOREGROUND_SERVICE` / `SCHEDULE_EXACT_ALARM` / `POST_NOTIFICATIONS`。
v2.1 只取其中 3 项：

| 权限 | 为什么必须留 |
| --- | --- |
| `FOREGROUND_SERVICE` | 原版 `TimerPlayer` 是前台服务；没有它，锁屏后 Activity 心跳停止，结束铃会哑 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | targetSdk 34+ 要求声明 foregroundServiceType，计时器属 specialUse |
| `POST_NOTIFICATIONS` | Android 13+ 前台服务通知可见性 |

**仍然没有**：INTERNET、账号、位置、通讯录、日历、相机、录音、蓝牙、NFC、健康数据、内购、广告 ID、精确闹钟（用前台服务锚点替代，避免额外授权）。

---

## 5. 如实标注的边界与未证实项

1. **无真机 / 无 adb**：`adb-screenshot-take`、`runtime-ui-read`、`web-pixels-compare` 这三类逐像素验收跑不了。界面高仿以 demo 预览图 + 原版实物素材 + 资源表为参照，**不是逐像素对比**。
2. **Dart AOT 快照被 strip**：`config.armeabi_v7a.apk` 里的 `libapp.so` 60.3 MB，`dart_symbols` 为空、`scanned_bytes = 0`，`android-dart-snapshot-show` 读不到 package URI / 类名。因此 Flutter 侧的**精确路由与交互链未静态还原**，交互高仿靠组件声明、资源命名与实物图标反推。
3. **REA 的资源解码受限**：`decode_android_resources` 需要服务端环境变量 `REA_APKTOOL_COMMAND` 指向 apktool，当前 MCP server 启动时未配置（且改配置需重启+重新信任）。已用 craft anything 的内置 `resources.arsc` 解析器补齐文案证据；本地 apktool 2.10.0 也已下载备用。
4. **原版 UI 文案主体在 Dart 里**：`android-resources-read` 解出的 1,195 条默认串主要是 AndroidX / Media3 / Stripe 等原生库文案，原版自己的计时器文案（如 `timer_notification_channel_name = Timer Player`）只取到通知渠道与暂停动作两条。**没有把第三方文案当成原版设计稿**。
5. 组件名与权限是静态声明，只能证明「声明了」，不能证明「一定在跑」；本表把它当作功能存在的证据，不当作运行时行为的证据。

---

## 6. 结论

`app-rebuild-claim-check`：**5 of 5 expectations satisfied, verified**。
feature map 已完整覆盖原版 11 个模块，裁剪决策逐项有静态证据，核心链路无缺口。
