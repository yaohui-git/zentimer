## 为什么改

<!-- 说明动机，不要只复述 diff -->

## 改了什么

<!-- 简短列点 -->

## 自查

- [ ] `python tools/check_lang.py` 通过（三语 key 零差异）
- [ ] `gradle assembleRelease` 通过
- [ ] 交互 / 计时状态机改动已在真机走通过（附截图或 adb 输出）
- [ ] 没有引入网络依赖、统计 / 广告 / 支付 SDK
- [ ] 没有提交 `.jks` / `keystore.properties` / `local.properties` / 第三方素材原件
- [ ] 提交信息写的是「为什么」

## 素材提醒

`res/raw/*.mp3` 与 `res/drawable-nodpi/*.png` 不在 Apache-2.0 授权范围内，见 `NOTICE.md`。若本 PR 动了这些文件，请说明来源与授权。
