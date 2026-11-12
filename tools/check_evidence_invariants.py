#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""把《证据核查》§9 的 E11~E14 变成静态断言，和 UI 术语闸门一起跑。

事故原型（11-09 上午）：§9.2 白纸黑字写「系统闹钟从不写 shortCriticalText，
AOD 只给时钟 LOGO + 状态词」，v2.16 却为顶栏数字无条件写了这个字段，
充电锁屏因此冒出倒计时；等用户 08:31 截图补位，v2.17 才用 SCREEN_ON/OFF 广播修掉。
**答案一直在台账里，缺的是把它当闸门。** 这个脚本就是那道闸门。

基线表（每显示表面 × 每状态，来源 `证据核查-2026-11-08.md` §9 E11~E14）：
  充电锁屏 / AOD  必须：aodTitle=状态词、aodPic 图标      必须不：任何数字、shortCriticalText
  顶栏胶囊 / 亮屏  必须：shortCriticalText=实时倒计时        必须不：状态词冒充数字
  通知栏下拉      必须：一组走秒数字 + 中间行「静坐中」   必须不：两组数字
  miui.focus.param 必须：param_island 在 param_v2 内 + 三个 timer 字段
  系统闹钟（标杆）用了什么 / **没用什么**同样重要，逐条列在报告里。

用法：python tools/check_evidence_invariants.py
      （check_ui_copy.py 会自动调用 run()，与本轮术语检查合流）
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "ZenTimer/app/src/main/java/com/yaohui/zentimer"
REPORT = ROOT / "证据不变量检查报告.md"

BASELINE = [
    ("充电锁屏 / AOD", "时钟 LOGO + 状态词「静坐中」", "任何倒计时数字、shortCriticalText",
     "E11 充电锁屏截图、E12 结论 5（aodTitle + aodPic）"),
    ("状态栏顶栏胶囊", "左侧沙漏 + 右侧实时走秒", "只给状态词、不给数字",
     "E11 黑色胶囊 12:58；用户 07:54 裁决"),
    ("通知栏下拉", "一组系统走秒数字 + 中间行「静坐中」", "两组数字、状态词顶替数字",
     "E11 白色卡片 13:01；v2.17 真机 13 PASS"),
    ("miui.focus.param", "param_island 嵌在 param_v2 内；timerWhen / timerSystemCurrent / timerTotal 齐备",
     "param_island 放顶层（v2.13 的错）", "E12 一手 dumpsys JSON"),
    ("系统闹钟（标杆）", "miui.focus.param 私有通道；沙漏/进度环由 SystemUI 渲染",
     "**从不写 shortCriticalText**；不带 PROMOTED_ONGOING；不用 AOSP ProgressStyle",
     "E12 结论 1/2/6、E13 已合并补丁、E14 反汇编"),
    ("标准通道资格", "标准模板 + chronometer 由平台驱动",
     "RemoteViews（E1：会被 promoted 资格排除）", "E1 官方文档、E13 提交信息"),
]


def read(name: str) -> str:
    return (SRC / name).read_text(encoding="utf-8")


def run() -> tuple[list[tuple[str, str, str]], list[str]]:
    """返回 (检查结果[(状态, 名称, 详情)], 已知偏离说明)。"""
    out: list[tuple[str, str, str]] = []
    nc = read("NotificationContract.kt")
    ts = read("TimerService.kt")
    lang = read("Lang.kt")
    kt_files = list(SRC.glob("*.kt"))

    def add(ok: bool, name: str, detail: str) -> None:
        out.append(("PASS" if ok else "FAIL", name, detail))

    # 1. shortCriticalText 的每一次写入都必须挂在亮屏条件上
    writes = [ln.strip() for ln in nc.splitlines()
              if "putString(KEY_SHORT_CRITICAL" in ln]
    unguarded = [w for w in writes if "screenOn" not in w]
    add(bool(writes) and not unguarded,
        "shortCriticalText 只在亮屏写",
        f"{len(writes)} 处写入，未挂 screenOn 的 {len(unguarded)} 处"
        + ("" if not unguarded else "：" + " / ".join(unguarded)))

    # 2. 息屏要真的能把这个字段摘掉：广播必须成对注册
    add("ACTION_SCREEN_ON" in ts and "ACTION_SCREEN_OFF" in ts,
        "SCREEN_ON / SCREEN_OFF 成对注册",
        "息屏去掉 chip 字段，AOD 才会回落到 aodTitle")

    # 3. AOD 标题只能是状态词，三语都不能含数字
    states = {}
    for marker, which in (("private val HANS = mapOf(", "hans"),
                          ("private val HANT = mapOf(", "hant"),
                          ("private val EN = mapOf(", "en")):
        seg = lang.split(marker, 1)[-1].split("\n    )", 1)[0]
        m = re.search(r'"state_running"\s+to\s+"([^"]*)"', seg)
        states[which] = m.group(1) if m else None
    bad = [f"{k}={v}" for k, v in states.items() if not v or re.search(r"\d", v)]
    add(not bad and all(states.values()),
        "aodTitle 状态词零数字（三语）",
        " / ".join(f"{k}:{v}" for k, v in states.items()) + (f" ⚠ {bad}" if bad else ""))

    # 4. aodTitle 绑定的是 state_running，不是硬编码或倒计时
    add('.put("aodTitle", Lang.t("state_running"))' in nc and '.put("aodPic", "miui.focus.pic_ticker")' in nc,
        "AOD 只给 LOGO + 状态词", "aodTitle / aodPic 两个字段齐全，值来自三语状态词")

    # 5. param_island 必须写在 param_v2 之后（v2.13 的错是把它放到了顶层 JSON 里）
    #    源码里 put( 与 key 分了两行，所以按「带引号的键」定位；先剔注释防误命中。
    code = re.sub(r"/\*.*?\*/", "", nc, flags=re.S)
    code = "\n".join(ln.split("//")[0] for ln in code.splitlines())
    i_v2 = code.find('"param_v2"')
    islands = [m.start() for m in re.finditer(r'"param_island"', code)]
    ok = i_v2 >= 0 and bool(islands) and all(i > i_v2 for i in islands)
    add(ok, "param_island 嵌在 param_v2 内",
        f"param_v2@{i_v2} → param_island@{islands}（E12 层级，v2.13 曾放错顶层）")

    # 6. 三个 timer 字段齐备（SystemUI 自己走秒，App 不用每秒重建）
    needed = ("timerWhen", "timerSystemCurrent", "timerTotal")
    miss = [k for k in needed if f'"{k}"' not in nc]
    add(not miss, "三个 timer 字段齐备", "缺：" + "、".join(miss) if miss else "E12 结论 3")

    # 7. 标准通道资格：禁止 RemoteViews / AOSP 样式
    forbidden = []
    for p in kt_files:
        t = p.read_text(encoding="utf-8")
        for token in ("RemoteViews", "Notification.ProgressStyle", "Notification.MetricStyle"):
            if token in t:
                forbidden.append(f"{p.name}:{token}")
    add(not forbidden, "不碰 RemoteViews / AOSP 样式",
        "、".join(forbidden) if forbidden else "E1 promoted 资格、E12 结论 6")

    # 8. 通知中间行的状态词仍是「静坐中」（中文/繁体一条没动）
    # v2.33 英文术语统一到 meditation 词族（用户授权），只改 EN 表 value；
    # 四表面的中文语义与字段结构不变，故判据仍只看中繁，描述不再写「notif_body 未改动」。
    add(states.get("hans") == "静坐中" and states.get("hant") == "靜坐中",
        "通知四表面语义未被文案修订带偏",
        "中繁状态词仍为「静坐中 / 靜坐中」；英文术语修订见 v2.33")

    deviations = [
        "我们**有意**在亮屏 L2 分支写 `android.shortCriticalText`（系统闹钟从不写）。"
        "理由：这台 HyperOS 的胶囊不认系统 chronometer，官方 chip 字段是唯一能把实时数字送进顶栏的通道；"
        "价格是 AOD 与顶栏共用同一字段，所以必须靠 SCREEN_ON/OFF 在息屏时摘掉。"
        "这不是照抄标杆，是把「标杆没用某字段」当约束后做的能力降级，见 §5.6。",
        "我们**有意**请求 `PROMOTED_ONGOING`（系统闹钟没请求）。理由：L2 能力自检通过时，"
        "标准通道是第三方唯一可用的 promoted 路径；自检不过自动降级到 L0/L1。",
    ]
    return out, deviations


def main() -> int:
    results, deviations = run()
    lines = [
        "# 证据不变量检查报告（E11~E14 → 静态断言）",
        "",
        "> 由 `tools/check_evidence_invariants.py` 生成，`tools/check_ui_copy.py` 会自动合流。",
        "> 起因：§9 早写明「系统闹钟从不写 shortCriticalText、AOD 不给数字」，"
        "v2.16 却无条件写了该字段，等用户 08:31 截图才补位。**台账写了不变量、代码没闸门。**",
        "",
        "## 一、标杆基线表（每显示表面 × 每状态）",
        "",
        "| 显示表面 | 必须出现 | 必须不出现 | 证据 |",
        "|---|---|---|---|",
    ]
    lines += [f"| {a} | {b} | {c} | {d} |" for a, b, c, d in BASELINE]
    lines += ["", "## 二、静态断言", "", "| 状态 | 检查项 | 详情 |", "|---|---|---|"]
    lines += [f"| {s} | {n} | {d} |" for s, n, d in results]
    fails = [n for s, n, _ in results if s == "FAIL"]
    lines += ["", f"**结果：{len(results) - len(fails)} PASS / {len(fails)} FAIL**",
              "", "## 三、已知偏离（不是缺陷，是能力降级）", ""]
    lines += [f"- {d}" for d in deviations]
    lines += [
        "",
        "## 四、这条闸门拦不住的（必须真机）",
        "",
        "- SystemUI 是否真的为第三方渲染 `miui.focus.param`（§9.3 仍未证实）；",
        "- 四个显示表面的实拍像素：顶栏数字、下拉一行「静坐中」、亮屏锁屏、充电锁屏 AOD；",
        "- 大字模式下的截断、深浅主题对比度、TalkBack 朗读。",
        "",
        "> 静态断言只保证「代码没写错字段」，不保证「系统照着渲染」。两者别混。",
    ]
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    for s, n, d in results:
        print(f"{s:4} {n} — {d}")
    print(f"\n{len(results) - len(fails)} PASS / {len(fails)} FAIL → {REPORT.name}")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
