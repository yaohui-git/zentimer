# 素材来源与授权说明 · ASSETS NOTICE

> 这份文件只讲一件事：**哪些东西是我们自己的，哪些不是，公开之前该怎么办。**
> 本文是工程事实记录，不是法律意见。涉及权利判断请自行咨询专业人士。
>
> **【2026-11-08 开源发布补充 · 以本节为准】** 项目所有者决定按原样把工程与 APK 公开到 <https://github.com/yaohui-git/zeatimer>，因此本仓库**仍包含**下列 23 段 mp3 与 36 张图；它们**不在 Apache-2.0 授权范围内**，已在 `NOTICE.md` 中逐项声明例外。下方第 3 节「B. 想开源代码」描述的是另一条可选的净化路径（`strip`），不是本次发布采用的做法；第 1、2、5 节的分类与风险判断依然有效。协议由 MIT 改为 **Apache-2.0**，仓库自带 LICENSE 为标准全文，例外声明改放 `NOTICE.md`。

---

## 1. 分类清单

| 类别 | 位置 | 来源 | 是否可随代码开源 / 上架 |
| --- | --- | --- | --- |
| Kotlin 源码、Gradle 脚本、自写 XML、`tools/` 脚本 | `StillTimer/app/src/main/java/`、`*.gradle.kts`、`tools/` | 本项目自写 | ✅ Apache-2.0 |
| 配色、排版、圆环绘制、文案 | `colors.xml` / `MainActivity.kt` / `Lang.kt` | 本项目自写 | ✅ Apache-2.0 |
| **启动图标（方案 B · 圆环计时）** | `mipmap-*/ic_launcher.png`、`drawable-nodpi/ic_launcher_foreground.png` | 细金环 / 进度弧 / 刻度为自绘；**青绿打坐人形**由项目所有者提供其自有 AI 生成图后抠出 | ✅ 可用（归属项目所有者） |
| 商店 512 图标、1024×500 宣传图 | `store/icon-512.png`、`store/feature-graphic-1024x500.png` | 由方案 B 图标与自绘文字合成 | ✅ 可用 |
| **23 段声音**（9 颂钵 / 大锣 / 木鱼 / 静音 / 提示 / 13 环境音） | `res/raw/*.mp3` | 从 Insight Timer 20.40.0 xapk 主包 `assets/flutter_assets/assets/timer/sounds/` **原样提取**，音频未改一个字节 | ⚠️ **需自行确认授权** |
| **36 张界面图片**（乐器实拍、环境音封面、控件图标、星空 / 海洋背景） | `res/drawable-nodpi/*.png`（启动图标除外） | 从同一原版包 `assets/timer/images/` 提取，仅等比缩放 | ⚠️ **需自行确认授权** |
| 三套图标方案对照、`.craft/` 证据 | `store/concept-*.png`、`.craft/` | 用户原图适配 / 自绘 / 逆向工具产物 | ⚠️ `concept-A` 含用户原图；`.craft/` 含原版包元数据，**不建议公开** |

---

## 2. 为什么「个人自用」和「公开分发」不一样

项目所有者已判断铜钵图像与颂钵、锣、木鱼发声属**实物范畴**，按复用处理 —— 这个判断用于**装在本人手机上使用**没有问题。但一旦：

- `git push` 到公开 GitHub 仓库，或
- 提交到小米 / 其他应用商店供不特定用户下载

就构成**分发**。此时对方是否主张权利、平台是否按「仿冒 / 混淆」下架，取决于素材的权利状态与平台规则，不由本项目的判断决定。**这不是能不能做到的问题，是风险由谁承担的问题。**

另需注意：应用名称「静息 / Still」、图标（已换为自绘方案 B）、描述文案均不含原版品牌字样，界面也不含其 logo 字标 —— 这已经降低了「混淆」风险，但**声音和界面图片仍是原版包内实物**。

---

## 3. 三种处置方式

### A. 只在自己手机用（当前交付状态）
什么都不用做。工程、实物素材、成品 APK 全在本机，不公开。

### B. 想开源代码
```bash
python tools/asset_privacy.py strip     # 原件移到 assets-private/，生成同名自绘占位
python tools/make_oss_repo.py           # 生成只含白名单的 oss/ 目录，直接 push
python tools/asset_privacy.py restore  # 本地要打完整版 APK 时恢复实物素材
```

`strip` 会：
- 把 23 段 mp3 换成 **1 秒静音 mp3（同名 `.mp3`）** —— `R.raw` 名字不变，代码零改动
- 把 36 张图换成 **自绘深色圆环占位（同名 `.png`）** —— 布局零改动
- 保留启动图标（自绘，无风险）
- 原件移到 `StillTimer/assets-private/`，该目录已被 `.gitignore` 排除

因此公开仓库 **依然可以完整编译**，只是没有原版音色与图片；任何 clone 者都能 build。本地 `restore` 后就是完整高仿版，但**恢复后的素材不要 commit 回公开仓库**。

### C. 想上应用商店
见 `STORE-LISTING.md`。最稳妥的做法是保持 `strip` 后的状态，自行录制 / 购买可商用授权的颂钵与环境音、重绘界面图片，再打 release 包提交。这样商店审核、后续更新都不会被素材问题卡住。

---

## 4. 与本项目文档的关系

- `LICENSE`（Apache-2.0 标准全文）+ `NOTICE.md` 已明确：**Apache-2.0 只覆盖自写代码与构建脚本，不覆盖 `res/raw/` 与 `res/drawable-nodpi/` 的第三方素材**
- `PRIVACY-POLICY.md` 说明「不含在线内容服务」，与素材来源无关
- 应用内「关于 → 素材说明」只写「声音与图片素材仅用于本机播放，不含任何在线内容服务」，保持中性、离线可读；详细来源放在本文件

---

## 5. 上游参照物

| 项目 | 值 |
| --- | --- |
| 原版包 | `com.spotlightsix.zentimerlite2-20.40.0.xapk` → `com.spotlightsix.zentimerlite2.apk` |
| 版本 | 20.40.0 |
| 静态清点 | REA `rea-agents@6.5.0` 14,279 项检查 / 4,821 文件；craft anything v0.3.0 `core-target-triage` → `android-package-show` → `android-app-graph-build` → `android-resources-read` → `core-archives-compare` → `app-rebuild-claim-check` |
| 证据 | `.craft/investigation/`、`.craft/checks/`、`高仿分析-0*.md` |
| 字节一致性 | craft `core-archives-compare` 判定 v2.0 的 11 段 mp3 为 `moved`（sha256 相同），未 `changed` |

本项目与 Insight Timer / spotlightsix 无任何关联、无授权、无背书。

---

## 6. 打赏不涉及任何素材（v2.3）

「加微信打赏」只把 `AppInfo.WECHAT` 复制进系统剪贴板并唤起微信客户端：**没有收款码图片、没有支付页面、没有第三方 SDK、没有新增权限**。因此 `strip` / `make_oss_repo.py` 不需要为它做任何处理，公开仓库里也不含任何个人收款凭证；微信号本身是公开联系方式，已同时出现在「关于」页与 `PRIVACY-POLICY.md`。

若将来改成内嵌收款码或接入支付接口，必须重新评估本节与第 2 节，并先读 `交付说明.md` 附录 · 打赏方案调研。
