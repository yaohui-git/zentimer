# 更新日志 / Changelog

> **协议与仓库地址**：v2.3 发布起采用 **Apache-2.0**（`LICENSE` 标准全文 + `NOTICE.md` 素材例外），仓库 <https://github.com/yaohui-git/zentimer>。下方早期记录中提到 MIT、开源地址「待定」处均为当时状态，**以当前 `LICENSE` / `NOTICE.md` / `AppInfo.kt` 为准**。

> ## 09:25 稳定版发布到 GitHub（叠加在下方全部记录之上，**发布状态以本条为准**）
>
> 用户逐字：「**可以了，作为一个稳定版本发布到github吧。用一周左右，看看有没有需要修补的，再考虑广普**」。
> **v2.17 定为本轮稳定版**：发布到 <https://github.com/yaohui-git/zentimer> 的 `main` 分支并打 `v2.17` tag，`releases/静息计时-v2.17.apk` 随仓库提供（sha256 `aafc3d8dbf706b217cf00f4298b063be288fffee1dd11794f129750cf8f3f185`，证书 `64047d93…`，可覆盖升级）。
> 同步面：公开 `README.md` 的安装入口改指 v2.17、真机验收段补上四个显示表面的 v2.17 结论并标注 v2.3 旧段为历史；`STORE-LISTING.md` 版本字段同步为 2.17，但**明确保持「未实际提交」的草稿状态**。
> **观察期约一周**：只收集 GitHub Issues 反馈、按需修补；不做商店上架或其它推广动作，是否广泛推广等观察期结束后再定。
> 发布链路（SSH Deploy key、现成工作区 `zentimer/.craft/tmp_remote`、`tools/make_oss_repo.py` 白名单）已补进工作区根 `本地工具清单.md` 第三节与第五节第 10 条，避免下次重新摸认证。

> ## 09:00 最终定稿（叠加在下方全部记录之上，**以本条为准**）
>
> 用户逐字：「算了，如果这一轮还搞不定，就不折腾了。基于目前模型的能力，做到就行，不求美观了。发布一版吧」「**"冥想中"这三个字仅限充电锁屏界面哈。顶栏和通知栏下拉都必须有倒计时**」「（充电锁屏）显示充电进度和USB连接的那个锁屏界面，这个界面。和系统闹钟一样，只显示时钟LOGO+静坐中」。
> **最终发布版：`releases/静息计时-v2.17.apk`，v2.17 / versionCode 18，sha256 `aafc3d8dbf706b217cf00f4298b063be288fffee1dd11794f129750cf8f3f185`。**
> 四个显示表面的最终语义（用户给的名词，不要混）：
>
> | 显示表面 | 定稿内容 | 真机像素证据 |
> |---|---|---|
> | **顶栏**（桌面状态栏中央黑色胶囊） | 时钟 LOGO + **实时倒计时数字**，每秒刷新 | `out/v217/v217_home.png` = `2:54` |
> | **通知栏下拉**（白色卡片） | **只一组**系统走秒数字 `02:48` + 中间一行「静坐中」+ 横向「暂停 结束」 | `out/v217/v217_shade.png` |
> | **类通知栏下拉锁屏**（亮屏锁屏卡片） | 同下拉：`02:43` +「静坐中」+「暂停 结束」 | `out/v217/v217_lock.png` |
> | **充电锁屏**（息屏 AOD，显示充电百分比与 USB 充电的那屏） | **时钟 LOGO +「静坐中」，不给任何数字**，与系统闹钟同款 | `out/v217/aod_final_12s.png` |
>
> 状态词从「冥想中」统一改为**「静坐中」**，并且「静坐中」不再只限充电锁屏：用户后续要求下拉与亮屏锁屏的中间一行也是这三个字。下方 08:15 定稿条、v2.15 一节、`FeatureMap` 与 `证据核查` E19 里「v2.15 是最终发布版」「AOD 是『冥想中』」「下拉两组数字可接受」等说法**原文一律保留不删，但全部以本条为准**；v2.15 / v2.16 都是中间候选包，不拿去安装。详见 `证据核查-2026-11-08.md` E20 / E21。

> ## 08:15 定稿（叠加在下方全部记录之上，**已被上方 09:00 条取代，原文保留作过程**）
>
> 用户逐字：「算了，如果这一轮还搞不定，就不折腾了。基于目前模型的能力，做到就行，不求美观了。发布一版吧」「"冥想中"这三个字仅限充电锁屏界面哈。顶栏和通知栏下拉都必须有倒计时」。
> **最终发布版：`releases/静息计时-v2.15.apk`，v2.15 / versionCode 16，sha256 `f6d273733937e67ac72ee61a1be089d64d716f22bf7d2be553793b45fe1faa19`。**
> 三个显示表面的最终语义：充电锁屏 AOD 只给 `aodTitle="冥想中"`、不给数字；顶栏胶囊显示倒计时 `0:50`；下拉与锁屏卡片显示系统走秒 `00:38` + 标题数字 `0:40`。「冥想中」不再出现在顶栏与下拉。
> 下方 07:45 修正标注里「v2.13 仍是当前发布包」这句**已被本条取代**；v2.14 是中间候选包，不作发布版。详见 `证据核查-2026-11-08.md` E19。

> ## 07:45 修正标注（叠加在 v2.12 / v2.13 之上，**以本条为准**）
>
> 同机对照截图 `.craft/demo2/out/cmp2_home.png` 直接可见：**静息 v2.13 计时中的黑色胶囊显示的是时钟图标 +「冥想中」，不是走秒数字**；`00:48` 这类走秒数字出现在下拉卡片的应用名旁边。
> 已验的只有三条：`android.shortCriticalText` 字段确实不在、`PROMOTED_ONGOING` 确实生效、胶囊确实出现。**「不设 shortCriticalText ⇒ 数字能进胶囊」这句预期被截图证伪，此前当作结论写过，属于措辞过强，现作废。**
> 下方 v2.12 第 1 条末句「会把系统走秒数字从胶囊里挤掉」、v2.13 末段「§5.6 的裁决成立」，以及 `证据核查` E16 / E17、`FeatureMap` §5.6 / §7 第 2 问里同类句子 **原文保留不删，但不再作为「胶囊显示什么」的依据**；「计时中一律不设 `shortCriticalText`」这条**代码裁决本身不变**，变的只是对它的效果描述。
> 为什么胶囊显示状态词，**原因未证实**（可能是 `focusType=PARAMS` 私有通道覆盖官方 chip 文案，也可能是缺 `shortCriticalText` 时 SystemUI 回落到 `contentTitle`），不猜、不冒充已达成。详见 `证据核查-2026-11-08.md` E18、`豆包Demo评估-2026-11-09.md` §6。**产品代码本轮未改，v2.13 仍是当前发布包。**

## v2.17（versionCode 18，2026-11-09 08:37，**最终发布版**）

`releases/静息计时-v2.17.apk`，sha256 `aafc3d8dbf706b217cf00f4298b063be288fffee1dd11794f129750cf8f3f185`，`build-v217.log` 为 `BUILD SUCCESSFUL`。**只改通知字段与屏幕状态联动，计时 / 结算 / 响铃 / 档位 / 完成态文案一行未动。**

用户 08:31 逐字点名的 BUG：「（充电锁屏）显示充电进度和USB连接的那个锁屏界面，这个界面。和系统闹钟一样，只显示时钟LOGO+静坐中」。v2.16 那屏的 AOD 胶囊实际显示 `时钟 LOGO + 2:42` / `2:13`（`out/v216/aod_15s.png` / `aod_42s.png`），是倒计时数字，不符合。

1. **定位**：这台 HyperOS 的**顶栏胶囊与 AOD 胶囊共用同一个 `android.shortCriticalText`**。v2.16 为了让顶栏实时走秒而写了这个字段，亮屏达标，息屏时同一个字段被 AOD 胶囊读取，就把数字带到了充电锁屏。系统闹钟不写该字段，所以它的 AOD 回落 `param_v2.aodTitle`，只显示 LOGO + 状态词（E11 截图 / E12 反汇编）。这是「同一字段、两个表面」的冲突，不是两个独立通知。
2. **修法**：`TimerService` 注册 `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF` 广播维护 `screenOn`，收到切换即清 `lastNoteKey` 让下一拍（250ms）立刻重发。亮屏 → `NotificationContract.applyFocusLayer()` 写 `shortCriticalText = TimerEngine.formatTime(left)`（每秒重发，顶栏实时）；息屏 → **不写这个字段**，AOD 胶囊回落 `aodTitle="静坐中"`。`push()` / `startForeground()` 都把 `screenOn` 传进 `build()`；`probePromotable()` 的自检对象同步带 `focusOn` / `screenOn`，延续 E17「自检对象必须和正式发布对象完全一致」的教训，避免再造一次自证循环。
3. **成本边界**：每秒重发只在 `FocusCapability.available() && screenOn && 计时中 && 非无限` 时启用；其它 ROM、息屏、暂停 / 完成态一律零重发，保持「一秒都不重建」。`SCREEN_ON/OFF` 是系统广播，`onDestroy()` 里 `unregisterReceiver()`。

**真机验收（NotePro15，两轮）**：

- `.craft/testcases/tc_v217.py`：**13 PASS / 0 FAIL**，字段层。亮屏 `title='静坐中'`、`chip='2:59'`，3 秒后 `2:56`（证明顶栏实时）；息屏 `has_chip_field=False`、`aodTitle:"静坐中"`、`title` 不变；亮屏恢复 `chip='2:22'`。`PROMOTED_ONGOING` 与两条 chronometer 字段全程在。
- `.craft/testcases/tc_v217_aod.py`：**14 PASS / 0 FAIL**，AOD 专项。**先截图、后取字段**（顺序反了会把 AOD 打灭，这是上一轮全黑的原因），同轮证明设备 `mWakefulness=Dozing`、计时通知仍在、chip 字段已去掉、AOD 文案无数字。

**四表面像素判读**（人眼看图，脚本不冒充 OCR）：

| 表面 | 截图 | 逐字判读 | 达标 |
|---|---|---|---|
| 顶栏 | `out/v217/v217_home.png` | 黑色胶囊 = 时钟图标 + `2:54` | ✅ |
| 通知栏下拉 | `out/v217/v217_shade.png` | `静息 • 02:48`（唯一一组数字）+ 中间行「静坐中」+ 横向「暂停 结束」；无「冥想中」、无第二组数字 | ✅ |
| 类通知栏下拉锁屏 | `out/v217/v217_lock.png` | `静息 • 02:43` +「静坐中」+「暂停 结束」 | ✅ |
| 充电锁屏 AOD | `out/v217/aod_final_12s.png` | 黑色胶囊 = **时钟图标 +「静坐中」**，无任何倒计时数字；画面上的 `08:56` / `94%` 是系统 AOD 自带的时钟与充电进度 | ✅ |

**修前 / 修后 A/B 对照**：v2.16 `out/v216/aod_42s.png` = 时钟图标 + `2:13` → v2.17 `out/v217/aod_final_12s.png` = 时钟图标 +「静坐中」。同一台机器、同一个 3 分钟计时、同一位置，只有胶囊文案变了。

**上一轮「AOD 全黑」的更正，如实写**：`tc_v217.py` 的 `v217_aod.png` 全黑，是脚本先跑 `dumpsys notification --noredact` 再 `screencap`，大开销命令把 AOD 从浅睡打进深睡。随后手工重拍的 `aod_clean_18s.png` 其实**拍到了「时钟 LOGO + 静坐中」**，但那一轮被误判成「没启动计时、不能当证据」——真相是计时正跑着（08:44 起 3 分钟，08:46:50 拍的还在计时内），所以界面上已经没有「3 分钟 / 开始冥想」按钮，脚本才打出 `MISS`。`aod_clean_30s/45s.png` 全黑是这台设备 AOD 约 15 秒后熄灭，属设备行为，不是 App 撤了通知。本轮用 `tc_v217_aod.py` 把截图和字段绑在同一轮，才算闭环。

**未验边界不变**：所有像素结论只覆盖这一台 HyperOS / Android 16 的 NotePro15；非小米 Android 16 的 L1 观感、Android 8~15 的 L0 观感仍缺设备，本机无 emulator / AVD / 系统镜像，不冒充全机型通过。AOD 只在息屏后约 10~13 秒能稳定截到，其它 ROM 的 AOD 行为未验。单行横向卡片（系统闹钟私有模板）按用户「做不到，现在的也及格」授权仍不做。

## v2.16（versionCode 17，2026-11-09 08:26，**中间候选包，不发布**）

`releases/静息计时-v2.16.apk`，sha256 `05448788fe4336da03f6729cec15342525d98794cbad8b00749d10a4e79420df`。修用户 08:18 点名的三个 BUG：下拉**两组数字**（`00:38` 与 `0:40`）、两组描述不同 / 不同行字体不同、中间一行显示「冥想中」而非数字或状态词；顶栏倒计时必须实时，不能 10 秒一跳。

1. `contentTitle` 从 `formatTime(left)` **改回状态词**，三语言统一为「静坐中 / 靜坐中 / Sitting」，移除 `notify_ticking`（`tools/check_lang.py`：**110 / 110 / 110，零差异**）。下拉 / 亮屏锁屏因此只剩系统 chronometer 一组数字，不再有两组。
2. 顶栏数字改用 `android.shortCriticalText` **单独喂**：`NotificationContract.applyFocusLayer()` 只在 L2 能力自检通过时写，`TimerService` 指纹桶从 `left / 10_000L` 改成 `left / 1_000L`，**每秒**重发喂新数字。这推翻了 v2.12 起的「计时中一律不设 shortCriticalText」跨厂商裁决，因此加了两道限制：只在 `FocusCapability.available()` 的机器上、只在 L2 分支，`StyleSelector.pick(caps)` 这个纯函数仍输出 `setShortCriticalText = false`，160 组不变量与「决策输入零厂商名」不变（24 PASS / 21 PASS 重跑仍全绿）。
3. `param_v2.aodTitle` 从「冥想中」改成「静坐中」，补 `aodPic` / `picFunction` / `hourglass_big` 时钟资源字段。

`.craft/testcases/tc_v216.py` **11 PASS / 0 FAIL**：`android.title=静坐中` 且正文无数字、`android.shortCriticalText` `0:57 → 0:54`（3 秒内实时）、`aodTitle="静坐中"`、`PROMOTED_ONGOING` 与基础 chronometer 仍在。`out/v216/v216_home.png` = 胶囊 `0:52`；`v216_shade.png` = `静息 • 00:46` +「静坐中」+ 横向「暂停 结束」，**只有一组数字**；`v216_lock.png` 同款。**但 `out/v216/aod_15s.png` / `aod_42s.png` 的 AOD 胶囊显示 `2:42` / `2:13`**，被用户 08:31 点名，修在 v2.17。

## v2.15（versionCode 16，2026-11-09 08:12，**中间候选包，不发布；原「本轮最终发布版」已被 09:00 定稿取代，原文保留**）

只改显示文案，计时 / 结算 / 响铃 / 档位一行未动。解决 07:54 用户裁决「顶栏和通知栏下拉都必须有倒计时，『冥想中』仅限充电锁屏」。

1. **v2.14 定位到真因**：把 `contentTitle` 从「冥想中」改成「倒计时中」，真机胶囊文字同步从「冥想中」变成「倒计时中」——**这台 HyperOS 的胶囊文案取的是 `contentTitle`**，不是 chronometer，也不是私有 `sameWidthDigitInfo`（E12 里那套数字字段对第三方不渲染）。E18 里「原因未证实」的第二个假设由此坐实一半：缺 `shortCriticalText` 时 SystemUI 回落到 `contentTitle`。
2. **v2.15 据此改两处**：`NotificationContract.buildBase()` 在倒计时中把标题写成 `TimerEngine.formatTime(left)`（如 `0:50`）；`TimerService` 的指纹 key 加一个 `left / 10_000L` 桶，**只在 `FocusCapability.available()` 的机器上**每 10 秒重发一次把新数字喂给胶囊。其它 ROM 仍由系统 chronometer 驱动官方 chip，保持「一秒都不重发」。
3. **AOD 不动**：`param_v2.aodTitle` 仍是「冥想中」，充电锁屏不给数字，逐字对上用户要求。
4. L2 私有 JSON 按 E12 一手产物修正层级：把误放顶层的 `param_island` 移进 `param_v2`，补 `timerWhen / timerSystemCurrent / timerTotal`、`sameWidthDigitInfo.timerInfo`、`smallIslandArea`。**这几字段对本机胶囊没起作用**，仍保留作可选增益（只追加、不替代，系统忽略时通知仍合法）。
5. 三语新增 `notify_ticking`（倒计时中 / 倒數計時中 / Countdown），`tools/check_lang.py` 对账 **111 / 111 / 111，零差异**。

**真机验收（NotePro15，`.craft/testcases/tc_v215.py`，8 PASS / 0 FAIL）**：装机 `versionCode=16`；`android.title` 从 `1:00` → `0:50`（12 秒间隔，证明重发真发生）；`PROMOTED_ONGOING` 仍在、`android.shortCriticalText` 仍不设、两条 chronometer 字段仍在、`aodTitle:"冥想中"` 仍在。三张截图 `.craft/testcases/out/v215/`：桌面黑色胶囊 = 时钟图标 + **`0:50`**；下拉 = `静息 • 00:38` + `0:40` + 横向「暂停 结束」，**没有「冥想中」**；锁屏卡片同样有数字。

**已知观感瑕疵，如实写**：下拉第一行是系统 chronometer 每秒走的数字（`00:38`），正文是 10 秒一跳的标题数字（`0:40`），两者最多差 10 秒。要消掉得改成每秒重建通知（豆包 demo 的做法，已在 `豆包Demo评估-2026-11-09.md` 判定为不抄：费电、依赖进程），或改成用 `shortCriticalText` 单独喂胶囊（会推翻跨厂商「计时中不设」的裁决）。用户 07:53 明确「不求美观了」，本轮按此收口，不为观感再动内核。

**按钮横向布局（用户 07:58 澄清后裁决）**：要的是系统闹钟那种「沙漏 + 大数字 + 小标签 + 右侧两个圆钮」全在同一行。当前主界面 `ActionRow` 与通知里的「暂停 / 结束」本来就是左右并排，但卡片整体仍是三行。单行版式由 SystemUI 按 `miui.focus.action_1/2` 私有模板渲染（E12 结论 4，系统闹钟 `actions=1` 却有两个圆钮），第三方改不了标准模板里 action 的位置；上 `RemoteViews` 会丢 promoted 胶囊（E1 官方硬禁）且在 HyperOS 被折叠进「更多通知」（豆包第二版实测）。用户原话「做不到，现在的也及格」，**本轮不做，不算未完成项**。

**未验边界不变**：非小米 Android 16 的 L1 观感、非小米 Android 8~15 的 L0 观感（H1/H2）仍缺设备；本机无 emulator / AVD / 系统镜像，不冒充全机型通过。`SystemUI` 是否为第三方渲染 `miui.focus.param` 仍未证实。

## v2.14（versionCode 15，2026-11-09 08:02，**中间候选包，不作发布版**）

`releases/静息计时-v2.14.apk`，sha `3da26893…`。做了 L2 JSON 层级修正 + 三语 `notify_ticking` + 标题改「倒计时中」。真机 `.craft/testcases/tc_v214.py` **10 PASS / 0 FAIL**（字段全对），但截图 `out/v214/v214_home.png` 里胶囊显示的是「倒计时中」而不是数字 —— **功能目标未达成**。这次失败反而提供了关键对照，定位到胶囊取 `contentTitle`，修在 v2.15。包保留作证据，不要拿去安装。

## v2.12（versionCode 13，2026-11-09，通知三段拆分 + 唯一结算入口）

**这一版改的是 v2.11 第 24 行点名「本版未改响铃逻辑」的那件事**，以及 11-09 03:52 用户硬约束「最后的APP是要兼容各种安卓手机类型的。可不能仅仅只能在小米指定机型使用哈」。两条一起落地，不再分开打补丁。

1. **通知拆成三段，做成不用真机就能验的决策表。**
   新增 `StyleSelector.pick(caps)`（纯 Kotlin、零 android import）：输入只有「系统版本 / 是否允许实时动态 / 当前通知是否可提升 / 焦点能力自检 / 通知开关 / 上次是否被拒」，**没有厂商名、没有机型名、没有白名单**。输出 BASE / OFFICIAL_CHIP / HYPEROS_FOCUS 三层之一。
   新增 `NotificationContract`：第 1 段 `buildBase()` 给**所有安卓手机**完整标准通知（标题、系统 chronometer 走秒、`CATEGORY_ALARM` 锁屏可见、暂停/继续/结束 actions）；第 2 段按决策请求 Android 16 官方胶囊，反射调用 `setRequestPromotedOngoing`、失败退回 extras 常量；第 3 段只在运行时 `canShowFocus` 自检通过时**额外追加** `miui.focus.param` 一个字符串，前两段一个字段都不少。
   **跨厂商统一裁决：计时中一律不设 `shortCriticalText`**，否则它优先级最高，会把系统走秒数字从胶囊里挤掉。暂停态的定格值走 `contentText`。
   > **（07:45 修正标注，见文首）**：前半句的裁决不变；后半句「会把走秒数字从胶囊里挤掉」是据官方优先级做的**推断**，这台机器上**不设也没让数字进胶囊**——胶囊显示「冥想中」。这句不再作为观感结论使用，原文保留。
2. **「回主界面突然嘣一声」从根上修掉。**
   病根是三处都能宣布结束、都自己播结束铃，且没有「这一轮响过没有」的凭据（C 组真机坐实：进程被强杀后冷启动 `restore()` 补响）。现在：
   - 全 App 只有 `TimerEngine.settleIfDue(now, allowBell)` 能写完成态并播结束铃，判定规则抽到纯函数 `SettlementPolicy.decide()`；
   - 每轮 `start()` 发唯一 `runId` 并**同步 `commit()` 落盘**；结算时**先写 `endBellFiredFor = runId` 凭据，再发声**，进程在两行之间被杀只会漏响、绝不会下次回来再响；
   - `restore()` 两处补算一律 `allowBell = false`：**只显示「已完成」，绝不补响**；重复冷启动命中凭据只摆完成态，一个音符都不发；
   - `MainActivity` 的 200ms 周期 `TimerEngine.tick()` 删除，界面只读 StateFlow，不再当第二个状态机写入者。前台服务 250ms 心跳是唯一允许发声的调用方。
3. **三语言完成态文案同步**：`catchup` 从「铃声补响」改为「已在后台结束，未补响铃」/「未補響鈴」/「no bell replay」，界面不再暗示补响过。
4. **档位不变**：仍是单版本 `1 / 3 / 5 / 10 / 15 / 20 / 30 / 45 / 60 / 90 / 120 / 无限`；滑动任意时长按 04:03 裁决暂缓，未实现。

**离线验收（跑了产品源码，不是复刻）**：`.craft/testcases/TcMain.kt` 直接编译 `StyleSelector.kt` + `SettlementPolicy.kt` 后在 JVM 上跑 —— TC-H 决策表 16 条、160 组全组合不变量穷举、结算 9 条，**24 PASS / 0 FAIL**；`.craft/testcases/tc_static.py` 对生产源码做 H8~H12 与唯一结算入口的代码审查断言，**21 PASS / 0 FAIL**。
**没验的如实说**：本机无 emulator / AVD / 系统镜像，非小米真机 H1/H2 仍是「未验」；NotePro15 只能覆盖官方胶囊与 HyperOS 焦点两格，**不得写成「全机型已验」**。`SystemUI` 是否真为第三方渲染 `miui.focus.param` 仍未证实，所以私有通道只是可选增益，系统忽略它时这条通知仍是合法标准通知。

> **真机回归结果（07:14 补，详见 `证据核查-2026-11-08.md` E16）**：NotePro15 上 **9 PASS / 0 FAIL**。1 分钟准点结束铃恰好一次（`piid:172575`）；`am force-stop` 等过点后回主界面新增音频播放器 **0 个**，界面逐字显示「本次练习已在后台结束，未补响铃」；再冷启动仍 0 声 —— **C 组那声「嘣」已消失**。计时中 `android.shortCriticalText` 已消失，基础走秒字段与 `miui.focus.param` 都在。
> **同一次验收抓到一个必须追的问题**：官方 `PROMOTED_ONGOING` 与 `android.requestPromotedOngoing` 在 v2.12 里不见了（v2.11 有）。设备日志 `reason=... notification not promotable`，权限却 granted。定位为现场自检的自证循环，修在 **v2.13**，见 E17。**v2.12 不作为最终发布版**。

## v2.13（versionCode 14，2026-11-09，修通知能力自检的自证循环）

只改一处：`NotificationContract.probePromotable()` 原来拿「还没申请 promotion 的裸通知」去问
`hasPromotableCharacteristics()`，拿到 false 又据此决定不申请 —— 恒 false。
现在自检探针先走与正式组装共用的 `requestPromotion()`，再问资格；并把 `plain=… requested=…`
这对对照值打进 logcat，结论可复核。计时、结算、响铃、档位一行未动。

**验收（07:21 补，NotePro15，10 PASS / 0 FAIL）**：设备日志逐字 `promotable probe plain=false requested=true`、
`notice layer=HYPEROS_FOCUS promoted=true alarmFallback=false focus=true suppressed=false sdk=36`；
`dumpsys notification --noredact` 里 `flags=…|PROMOTED_ONGOING`、`android.requestPromotedOngoing` 与
`miui.focus.param` 同时出现，`android.shortCriticalText` 仍不在、两条 chronometer 字段仍在。
1 分钟到点结束铃仍恰好一次。**成因证实**：自检必须拿「已申请 promotion」的通知去问，
不是 ROM 加码、不是权限、也不是 `shortCriticalText`。详见 `证据核查-2026-11-08.md` E17。

> **（07:45 修正标注，见文首）**：本节「§5.6 的裁决成立」只指「不设 `shortCriticalText` 与拿到 L1 资格可以共存」——这一条 E17 的 `plain=false requested=true` 对照确实坐实了，**不受 E18 影响**。
> 但「不设 ⇒ 胶囊里就是走秒数字」不成立：07:45 截图里胶囊显示「冥想中」。**资格生效 ≠ 文案达成**，两件事别再混写。

## v2.11（versionCode 12，2026-11-08，测试专用短时长 + 完成态时长歧义）

> v2.10（versionCode 11）在本文件缺一节，属前序记录遗漏；此处不补写未经核实的内容。

两件事，均为当晚用户追加需求；逐字原话见 `FeatureMap-TestCase-整体设计.md` §3 台账 13 / 14 / 15。**第 13 条的初始方案已被第 15 条推翻，以第 15 条为准**。

1. **内测短时长：1 / 3 分钟常驻，只做一个版本。**
   用户定稿原话：「内测期间就保留1分钟和3分钟两档后……总之当前内测期间，增加时长选项，不做两个版本了。太麻烦且容易出问题」。
   时长列表为固定的 `1 / 3 / 5 / 10 / 15 / 20 / 30 / 45 / 60 / 90 / 120 / 无限`，走完全相同的 `setDuration → start → tick → finish` 状态机。1 分钟把一轮真机回归从 5m30s 压到 1m30s。
   **没做 30 秒**：那要把时长单位从整数分钟改成秒，会连带 `Presets.kt` 的 JSON schema、`duration_min` 持久化、`Lang.minutes()`、无限模式 `-1` 哨兵一起迁移，为省 30 秒引入迁移风险不划算。
   **滑动可选任意时长：未决、未实现**。用户原话「但这个和克制、简洁和禅意是否冲突，未知，没想好」，没定之前保持固定选项。
   > **11-09 04:03 裁决（以此为准）**：「结论是暂缓。以后再说」。状态由「未决」升级为**已裁决暂缓**——不实现、不排期、不留待办；日后由用户重新提出才单开一轮评估。发布包 v2.11 与固定档位均不变。上方 22:48 的「未决」原文保留作历史。
   > 22:08 曾做过 `BuildConfig.TEST_DURATIONS` 双包方案（测试包 1/2 分钟、正式包不显示），22:48 已全部撤除：双版本让「装的是哪个包」变成新变量，真机结论与正式行为脱钩，还多一条装错包的风险。Gradle 属性、`buildConfigField`、两版 APK 产物均已清理，全局 grep 零命中。
2. **完成态「圆圈 0:00、时长高亮 5 分钟、按钮还叫开始冥想」的歧义。**
   先给答案：**点下去是完整 5 分钟，不是 0**（`mainAction()` 的 DONE 分支走 `start()`，`total = durationMin * 60_000L`）。逻辑没错，是视觉三套信号打架。现在 DONE 态按钮改「**再来一次 · 5 分钟**」（三语言新增 `btn_again`），圆环提示也拼上时长；时长选项不禁用 —— 完成态改时长会回 IDLE 且圆环同步变成新时长，所见即所得，禁用反而制造「为什么点不动」的新误会。
   内在逻辑同时收敛：`start()` 开头按当前 `durationMin` 重算并写回 `totalMs / remainingMs / catchUp`。原先「无准备时间」分支不写回、`beginRunning()` 沿用上一次的 `totalMs`，与 `plannedEndWall` 可能不同源；现在 `plannedEndWall`、`endAt`、圆环进度只认同一个 total。

**内测期脏数据也清了**：双包方案期间曾短暂出现过 2 分钟档，磁盘上可能残留 `duration_min=2`，定稿后会出现「圆环 2:00，但时长行没有对应档位」。`TimerEngine.sanitizeDuration()` 把 2 一次性归一到 3 并写回，`readSettings()` 与 `applyPreset()` 两处都走它。1 和 3 都是正式档，不需要任何构建分叉。

**没动的部分**：当晚 C 组真机实验（`证据核查-2026-11-08.md` §8.7）已坐实「回主界面突然响一声」= 冷启动 `restore()` 补响，但本版**未改响铃逻辑**。修法按 `FeatureMap-TestCase-整体设计.md` §5 的整体设计（单一结算入口 + 一次性凭据）统一落地，不在 `restore()` 里加 if 打补丁。

**✅ 2026-11-09 03:26 补验（以此为准，推翻下面 23:00 「UI 验收未完成」的说法）**：用户重连并解锁手机后，定稿 v2.11 单版本包三条 TC 已补验——**F1 通过**（横滑三屏拿全 12 档 `1/3/5/10/15/20/30/45/60/90/2 小时/无限`，时长行无 2 分钟；`2 分钟` 只在间隔铃/准备时间行，是上一轮误判根源）、**F3 通过**（点 3 分钟 → `3:00` → 起跑 4s `2:53` → 点 App 内「结束」回 IDLE `3:00`）、**F4 静态通过／真机未验**（机上 prefs 已是默认 10，旧 `duration_min=2` 残留不存在；release 非 debuggable + 无 root + 备份需人工确认，无法注入，不为一条边界用例编临时夹具包）。详见 `FeatureMap-TestCase-整体设计.md` §9.0.1。真机新口径：控件树必须含包名、横向选项行要滑到底、收尾点 App 内「结束」而非 `force-stop`。

**真机验收口径（22:56 历史记录，原样保留）**：双包阶段的实测（1 分钟准点到点、完成态文案、1/2 档可见性）见 `FeatureMap-TestCase-整体设计.md` §9 过程记录；**定稿后以单版本重新验收为准**（§9.0）。验收中曾把「圆环 1:56」误判为代码缺陷，用户当场澄清是测试期间手动点了「2 分钟」——**真机脚本测到的异常必须先排除人工干预**，这条已写进用例口径。另：设备进入 Dozing 时 `uiautomator dump` 只返回锁屏 XML、脚本点击会落到 `(0,0)`；唤醒顺序 `keyevent 26` → `224` → 上滑。**定稿包 UI 验收未完成**：22:56 设备唤醒后停在「请用数字密码或指纹解锁」的 PIN 锁屏，装包虽成功（`versionCode=12 / 2.11`），TC-F1/F3/F4 需人工解锁后重跑。已验过且定稿版未改代码的两项：1 分钟准点到点、完成态按钮带时长。

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
