# 参与贡献 / Contributing

谢谢你愿意花时间在这个小工具上。它是个人作品，目标始终是**克制**：一只计时器、一声钵，不加课程、不加社区、不加账号、不联网。提改动前请先确认它不破坏这个前提。

## 开工前

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
cd ZenTimer
cp keystore.properties.example keystore.properties   # 填自己的签名；文件不入库
gradle assembleDebug
```

## 几条硬规矩

1. **三语文案必须同步。** 所有界面文字在 `Lang.kt` 的 `HANS / HANT / EN`（以及 `EXTRA_*`）三张表里各有一份，key 必须一一对应。改完跑：
   ```bash
   python tools/check_lang.py
   ```
   缺任何一条都会 `exit 1`。新增文案请三语同时加，不要留英文占位。
2. **改了行为就要上真机验。** 计时状态机（`TimerEngine.kt`）涉及准备 → 正式计时 → 暂停 → 恢复 → 结束，以及锁屏 / 后台 / 进程被回收后的补算。请用 adb 实机走一遍，别只靠编译通过。命令见 [`README.md`](README.md)「真机验收」。
3. **不要提交密钥与本机路径。** `zentimer.jks`、`keystore.properties`、`local.properties`、`assets-private/`、`*.apk` 都在忽略名单里（`releases/` 下的正式发布包除外）。
4. **不要往公开仓库提交第三方素材原件。** `res/raw/*.mp3` 与 `res/drawable-nodpi/*.png` 的授权状态见 [`NOTICE.md`](NOTICE.md) 与 [`ASSETS-NOTICE.md`](ASSETS-NOTICE.md)。要替换素材，请用自有授权或自绘内容，并保持资源名不变以便零改动编译。
5. **保持离线。** 不引入网络依赖、统计 SDK、广告 SDK 或内购。`<queries>` 只是 Android 11+ 包可见性声明，不是权限。
6. **打赏不做内嵌支付。** 理由见本地交付记录，别把收款码或支付接口塞回来。
7. **别扩大权限面。** 目前只向系统请求一条 `POST_NOTIFICATIONS`（Android 13+ 首次进入时一次，用于常驻倒计时与锁屏 / 状态栏实时信息）；不要新增其它运行时权限、网络依赖或第三方 SDK。另有一项「静坐时免打扰」的系统「特殊访问权限」：它在系统设置页授权，不是弹框，且开关默认关闭、不授权不影响其它功能。主题色与语言的「跟随系统」默认不能被写死覆盖；改纸色请改 `Season.kt` 里的四时 `paper / accent` token，派生色板由 `SeasonTheme.palette()` 平移，不要再复制一套 `StillColors`。
8. **别碰全局勿扰状态。** 免打扰只允许激活 / 停用属于本应用的那一条 `AutomaticZenRule`；`setInterruptionFilter` / `setNotificationPolicy` 在源码里被静态闸门禁用。结束、冷启动、开机广播、兜底闹钟四条停用路径一条都不能少——「程序退出后系统配置必须复原」是用户定的底线。

## 提交

- 提交信息写「为什么」，不写「改了什么」——后者看 diff 就知道。
- 一个 PR 只解决一件事；交互改动请附 1~2 张真机截图。
- 大改之前先开 Issue 说清楚场景，避免白做。

## 定制与合作

寺院、道观等非营利组织可享优惠或免费定制，其他用途另议；可定制名称、图标、配色、铃声、默认时长方案与文案。完整说明见 [`README.md`](README.md)「定制与合作」。定制版同样要保持离线、无授权请求、无广告的极简禅意底线。

## 版本号规则

- **唯一来源**是 `ZenTimer/app/build.gradle.kts` 的 `versionCode` / `versionName`。界面通过 `BuildConfig` 读取，**不要**再到 Kotlin 里手写一份版本常量。
- **每次出包都要 +1 `versionCode`**，哪怕只改一行；`versionName` 按「主.次」递增。理由很实际：商店上传要求 `versionCode` 单调递增，同号会被判重复包；版本号不变，出问题时也无法判断测试机装的是哪一份。
- 产物文件名带上版本号，**GitHub 仓库内一律 `zentimer-vX.X.apk`（纯 ASCII；中文文件名在 GitHub 上不可用）**；仓库 `releases/` 只留一个与代码同步的最新包，历史版本改由 GitHub Release 附件提供，不留在目录里。

## 报告问题

请用 Issue 模板，至少带上：手机型号、Android 版本、复现步骤、是否开了准备时间 / 间隔铃、以及 `adb logcat -d | grep -i zentimer` 的相关片段。

---

*本项目为个人独立作品。*
