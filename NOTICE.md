# NOTICE

**静息 · zentimer**
Copyright 2026 耀慧 (Yaohui) and contributors

本文件是 Apache License 2.0 第 4(d) 条所要求的归属与例外声明。**它与 [LICENSE](LICENSE) 一起阅读**；两者冲突时，以本文件对「哪些文件不在授权范围内」的界定为准。

---

## 1. Apache-2.0 覆盖的部分

[`LICENSE`](LICENSE)（Apache License, Version 2.0）适用于本项目的原创代码与自写内容：

- `ZenTimer/app/src/main/java/com/yaohui/zentimer/` 下全部 Kotlin 源码（状态机、音频、语言、预设、前台服务、单 Activity UI）
- Gradle 构建脚本 `*.gradle.kts`、`settings.gradle.kts`
- 本项目自写的 XML 资源：`colors.xml`、`themes.xml`、三语 `strings.xml` 等
- 配色、圆环绘制、排版与三语文案
- `tools/` 下的生成期脚本、`docs/` 下的分析文档、`README.md` 等项目文档

## 2. 不在 Apache-2.0 范围内的第三方素材

以下文件为了保持工程可编译、完整还原计时体验而随仓库提供，**它们不是本项目的原创作品，不适用 Apache License 2.0，也未获得上游授权**：

| 文件 | 数量 | 来源 |
| --- | --- | --- |
| `ZenTimer/app/src/main/res/raw/*.mp3` | 23 段（9 种颂钵、寺院大锣、木鱼、静音 / 提示、13 种环境音） | 随包内置的第三方实物录音，**不在 Apache-2.0 授权范围内** |
| `ZenTimer/app/src/main/res/drawable-nodpi/*.png`（启动图标除外） | 36 张（乐器实拍、环境音封面、控件图标、星空 / 海洋背景） | 随包内置的第三方图片素材，**不在 Apache-2.0 授权范围内** |

项目所有者判断这些内容为「实物相关」，按个人自用复用。**个人自用与公开分发是两回事**：`git push` 到公开仓库、上传到应用商店、或任何形式向第三方再分发，都需要另行确认权利状态。若你 fork 本仓库并再分发，请先自行处理这些素材的授权，或运行：

```bash
python tools/asset_privacy.py strip    # 原件移到本地 assets-private/，生成同名自绘占位，工程仍可编译
```

更完整的逐项清单、三种处置方式与证据链见 [`ASSETS-NOTICE.md`](ASSETS-NOTICE.md)。

## 3. 启动图标

`mipmap-*/ic_launcher.png` 与 `drawable-nodpi/ic_launcher_foreground.png` 为本项目自绘（方案 B「圆环计时」：细金环、进度弧、端点光点、12 刻度）；其中**青绿打坐人形剪影**由项目所有者提供其自有 AI 生成图后抠出。图标不含任何第三方实物照片，不含任何品牌字标。

## 4. 打赏

「加微信打赏」只在本地复制微信号 `pingyishufang` 并唤起微信客户端。仓库与 APK 内**不含收款码图片、支付页面、支付 SDK 或任何个人收款凭证**；本应用不处理金额、订单或付款结果。

## 5. 与上游的关系

本项目为**个人独立作品**。应用名称「静息 / zentimer」、图标与描述文案均为自有内容，不含任何第三方品牌字样。

第三方商标、产品名称归各自权利人所有。本文件不构成任何法律意见；涉及权利判断请自行咨询专业人士。

---

联系方式：耀慧 · <luoxu_disk@163.com> · 微信 `pingyishufang` · 公众号「何处青山不道场」
开源地址：<https://github.com/yaohui-git/zentimer>
