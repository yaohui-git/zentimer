#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""把豆包回填的 UI 文案建议落成源码改动（带最终裁决表）。

输入：`UI文案复核表_豆包回填.csv`（原样归档，不改写）
输出：改 `Lang.kt` 三语值 / `greetingLine()` / `SoundCatalog.kt` / `SoundPicker`，
      并生成 `UI文案复核决定.md`，逐条写明采纳 / 否决及理由。

原则（这轮由 WorkBuddy 拍板，目标 = 让 APP 更好用）：
  · 只改 value，不动 key、不动不可变串（AppInfo / 邮箱 / 微信 / 仓库 / 许可）。
  · 通知四表面语义一个字不动：state_running / notif_body 均标 OK。
  · 用户 22:16 给的是「最终决定权」，所以不是照单全收：该否决的否决，
    该改结构的改结构，全部写进决定表。
  · 脚本可复跑；任何一处预期替换没命中就报错退出，绝不静默跳过。

用法：
  python tools/apply_ui_review.py --dry
  python tools/apply_ui_review.py --apply
"""
from __future__ import annotations

import argparse
import csv
import io
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "ZenTimer/app/src/main/java/com/yaohui/zentimer"
CSV = ROOT / "UI文案复核表_豆包回填.csv"
DECISION = ROOT / "UI文案复核决定.md"

# ---- 最终裁决 ---------------------------------------------------------------
# 否决：主屏空闲态只有一个大按钮，和「暂停 / 继续 / 结束」不同时出现在同一排，
#      「一排按钮右缘不齐」这个理由不成立；「开始静坐」点明这是静坐计时器，更好用。
REJECT = {
    "L030": "主屏空闲态只有这一颗大按钮，不与「暂停/继续/结束」同排，不存在对齐问题；"
            "「开始静坐」比「开始」更点题，保留。",
    "D139": "这是表格里描述模板的记号（N=数字），不是可替换的字面值；"
            "代码 `Lang.minutes(120)` 已经输出「2 小时 / 2 小時 / 2 h」，无需改动。",
}
# 结构上处理：不把 b_silent 改名成第二条「无」，而是从展示列表移除并保留老 id 别名。
STRUCTURAL = {
    "S121": "采纳「只留一个无」的问题，但不按建议改名：把 b_silent 整条从展示列表移除，"
            "新增 canonicalBell 别名，老用户已存的 b_silent 仍解析到「无」(res=0)，"
            "既消除重复卡片，也不会回落成 Basu 或改写用户设置。",
}
# 需要单独说明的采纳项
NOTES = {
    "L089": "采纳。「间隔铃长」是 21:1x 用户逐字指定的标签；豆包这轮里用户又澄清"
            "「指每隔多久响一次」并定稿「提醒间隔」。按较新口径执行，"
            "旧说法在 CHANGELOG 留痕，要退回只需改 Lang.kt 一行。",
    "L010": "采纳。法律全文在 NOTICE.md，关于页这句只是摘要；「另计授权」与"
            "英文 licensed separately 等价，缩短后不再折到第三行。",
    "L098": "采纳。与 L045 的「无时限 / No limit」保持同一套词。",
    "L095": "采纳。这一项控制的是深浅模式，不是配色；「外观 / Appearance」更准确。",
    "D135": "采纳。英文原本只有 4 档，中午 11–14 点误显示 Good afternoon；"
            "补第 5 档 Good day，与中文五档对齐。",
    "D136": "采纳（繁体五档本就一致，仅随 D135 一并核对）。",
    "D137": "采纳。见 D135。",
    "D138": "采纳。英文日期补星期，与中文「M月d日 星期X」对齐。",
}
# 动态文案不走三语 map，单独按代码块处理
DYNAMIC = {"D135", "D136", "D137", "D138"}

MAP_MARKERS = {
    "private val HANS = mapOf(": "hans",
    "private val EXTRA_HANS = mapOf(": "hans",
    "private val HANT = mapOf(": "hant",
    "private val EXTRA_HANT = mapOf(": "hant",
    "private val EN = mapOf(": "en",
    "private val EXTRA_EN = mapOf(": "en",
}
LINE_RE = re.compile(r'^(\s*"(?P<key>[a-z_0-9]+)"\s+to\s+)"(?P<val>(?:[^"\\]|\\.)*)"(?P<tail>.*)$')


def read_csv() -> list[list[str]]:
    raw = CSV.read_bytes()
    enc = "utf-8-sig" if raw[:3] == b"\xef\xbb\xbf" else "utf-8"
    return list(csv.reader(io.StringIO(raw.decode(enc))))


def kt_escape(s: str) -> str:
    return s.replace("\\", "\\\\").replace('"', '\\"')


def build_changes(rows: list[list[str]]) -> dict[str, tuple[str, str, str]]:
    out: dict[str, tuple[str, str, str]] = {}
    for r in rows[1:]:
        if not r or not r[0].startswith("L"):
            continue
        sug = r[-1].strip()
        if not sug.startswith("改："):
            continue
        if r[0] in REJECT:
            continue
        parts = [p.strip() for p in sug[2:].split("|")]
        if len(parts) != 3:
            raise SystemExit(f"{r[0]} 建议不是三段三语：{sug}")
        out[r[4]] = (parts[0], parts[1], parts[2])
    return out


def patch_lang(text: str, changes: dict[str, tuple[str, str, str]]) -> tuple[str, dict]:
    hits: dict[str, list[str]] = {}
    lang = None
    out = []
    for line in text.splitlines(keepends=True):
        stripped = line.strip()
        for marker, which in MAP_MARKERS.items():
            if stripped.startswith(marker):
                lang = which
                break
        m = LINE_RE.match(line.rstrip("\r\n"))
        if m and lang and m.group("key") in changes:
            key = m.group("key")
            idx = {"hans": 0, "hant": 1, "en": 2}[lang]
            new = kt_escape(changes[key][idx])
            eol = "\r\n" if line.endswith("\r\n") else "\n"
            line = f'{m.group(1)}"{new}"{m.group("tail")}{eol}'
            hits.setdefault(key, []).append(lang)
        out.append(line)
    missing = [k for k in changes if sorted(hits.get(k, [])) != ["en", "hans", "hant"]]
    if missing:
        raise SystemExit("Lang.kt 未三语齐全命中：" + ", ".join(
            f"{k}({','.join(hits.get(k, []))})" for k in missing))
    return "".join(out), hits


def replace_once(text: str, old: str, new: str, tag: str) -> str:
    n = text.count(old)
    if n != 1:
        raise SystemExit(f"[{tag}] 预期命中 1 处，实际 {n} 处：{old[:60]!r}")
    return text.replace(old, new, 1)


def patch_dynamic_lang(text: str) -> str:
    # 简体问候五档
    text = replace_once(text,
        '                    h < 11 -> "早上好"\n'
        '                    h < 14 -> "中午好"\n'
        '                    h < 18 -> "下午好"\n'
        '                    else -> "晚上好"\n',
        '                    h < 11 -> "早安"\n'
        '                    h < 14 -> "午安"\n'
        '                    h < 18 -> "下午好"\n'
        '                    else -> "晚安"\n',
        "greet.hans")
    # 英文补中午一档
    text = replace_once(text,
        '                    h < 11 -> "Good morning"\n'
        '                    h < 18 -> "Good afternoon"\n',
        '                    h < 11 -> "Good morning"\n'
        '                    h < 14 -> "Good day"\n'
        '                    h < 18 -> "Good afternoon"\n',
        "greet.en")
    # 英文日期补星期
    text = replace_once(text,
        'DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)',
        'DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)',
        "date.en")
    return text


def patch_sound_catalog(text: str) -> str:
    text = replace_once(text, '"木魚",            "Wood Block"', '"木魚",            "Woodblock"', "S120")
    old_comment = (
        '        // 「无」= 这一项根本不响（res=0，BellPlayer.play 直接 return）。\n'
        '        // 与「静音」区分：静音是一段可播放的空白音轨，无是压根不排铃。\n'
    )
    text = replace_once(text, old_comment,
        '        // 「无」= 这一项根本不响（res=0，BellPlayer.play 直接 return）。\n'
        '        // v2.19：旧的 b_silent（空白音轨）不再单列，避免选择器里出现两张「无声」卡。\n',
        "S121-comment")
    text = replace_once(text,
        '        Sound("b_silent",  R.raw.b_silent,  R.drawable.ic_no_music,  "静音",            "靜音",            "Silent")\n',
        '', "S121-entry")
    # 上一行现在成了列表末项，去掉结尾逗号
    text = replace_once(text,
        '        Sound("b_wood",    R.raw.b_wood,    R.drawable.ic_b_wood,    "木鱼",            "木魚",            "Woodblock"),\n    )',
        '        Sound("b_wood",    R.raw.b_wood,    R.drawable.ic_b_wood,    "木鱼",            "木魚",            "Woodblock")\n    )',
        "S121-trailing-comma")
    text = replace_once(text,
        '    // 未知 id（旧预设里残留的名字）仍回落到 Basu，不能因为「无」排到了首位就静音。\n'
        '    fun bell(id: String): Sound =\n'
        '        bells.firstOrNull { it.id == id } ?: bells.first { it.id == "b_basu" }\n',
        '    /** 内测期「不响铃」曾存成 b_silent；v2.19 起只留一个「无」。\n'
        '     *  不改写用户已存设置，只在查找时把老 id 认回来，避免回落成 Basu。 */\n'
        '    fun canonicalBell(id: String): String = if (id == "b_silent") "none" else id\n\n'
        '    // 未知 id（旧预设里残留的名字）仍回落到 Basu，不能因为「无」排到了首位就静音。\n'
        '    fun bell(id: String): Sound =\n'
        '        bells.firstOrNull { it.id == canonicalBell(id) } ?: bells.first { it.id == "b_basu" }\n',
        "S121-alias")
    return text


def patch_main(text: str) -> str:
    old = ('    val current = when (key) {\n'
           '        "start" -> s.startBell\n'
           '        "interval" -> s.intervalBell\n'
           '        "end" -> s.endBell\n'
           '        else -> s.ambience\n'
           '    }\n')
    new = ('    val current = SoundCatalog.canonicalBell(when (key) {\n'
           '        "start" -> s.startBell\n'
           '        "interval" -> s.intervalBell\n'
           '        "end" -> s.endBell\n'
           '        else -> s.ambience\n'
           '    })\n')
    return replace_once(text, old, new, "SoundPicker-current")


def write_decision(rows: list[list[str]], changes: dict) -> None:
    lines = [
        "# UI 文案复核 · 最终决定（2026-11-09 22:5x）",
        "",
        "> 输入：`UI文案复核表_豆包回填.csv`（豆包回填，原样归档）、`UI文案复核报告_豆包.md`。",
        "> 用户原话：「豆包给个UI文案修改意见，基于让APP更好用的目标，你有最终决定权。我暂时不发表意见」。",
        "> 所以不是照单全收：50 条建议里 **否决 2 条、1 条改结构处理、其余采纳**。",
        "> 改动由 `tools/apply_ui_review.py` 从 CSV 生成，可复跑；`UI文案复核表.csv` 改完由 `export_ui_copy.py` 重刷。",
        "",
        "## 一、不采纳（2 条）",
        "",
    ]
    for rid, reason in REJECT.items():
        row = next((r for r in rows[1:] if r[0] == rid), None)
        lines.append(f"- **{rid} · {row[4] if row else ''}**：{reason}")
    lines += ["", "## 二、采纳问题但换修法（1 条）", ""]
    for rid, reason in STRUCTURAL.items():
        lines.append(f"- **{rid}**：{reason}")
    lines += [
        "",
        "## 三、需要说明的采纳项",
        "",
    ]
    for rid in ("L089", "L010", "L095", "L098", "D135", "D136", "D137", "D138"):
        lines.append(f"- **{rid}**：{NOTES[rid]}")
    lines += [
        "",
        "## 四、硬口径（一条没破）",
        "",
        "- 只改 value，不动 key；`AppInfo.kt` 的 AUTHOR / EMAIL / WECHAT / OFFICIAL_ACCOUNT / REPO / LICENSE 一字未动。",
        "- 通知四表面语义不变：`state_running`「静坐中」、`notif_body` 均标 OK，不参与本轮。",
        "- `AndroidManifest.xml` 的 `meditation_timer_playback`、历史台账（CHANGELOG / 证据核查 / 复盘 / 迭代计划 / FeatureMap）原文不改写。",
        "- 6 条「静态未见引用」按豆包要求保留，不当死文案删。",
        "- `lang_hans` / `lang_hant` 的英文列仍是「简体中文 / 繁體中文」——语言自称，按口径豁免。",
        "",
        "## 五、真机验收",
        "",
        "- 改完跑 `tools/check_lang.py` 与 `tools/check_ui_copy.py`；三语 110 key 必须仍然一一对齐。",
        "- 重点看 1.3 倍系统字体下 L002 / L010 / L060 / L093 四条是否还折到第三行。",
        "- 声音选择器只应有一张「无」卡；老预设里的 `b_silent` 打开后应显示「无」而不是 Basu。",
        "",
        "## 六、逐条裁决表",
        "",
        "| ID | key | 裁决 | 说明 |",
        "|---|---|---|---|",
    ]
    for r in rows[1:]:
        rid, key = r[0], r[4]
        sug = r[-1].strip()
        if rid in REJECT:
            verdict, note = "否决", REJECT[rid].split("；")[0]
        elif rid in STRUCTURAL:
            verdict, note = "改结构", "移除重复卡片 + 老 id 别名"
        elif rid in DYNAMIC or (rid.startswith("L") and sug.startswith("改：")):
            verdict = "采纳"
            note = NOTES.get(rid, "按三语回填")
        elif sug.startswith("改："):
            verdict, note = "采纳", "按建议值回填"
        else:
            verdict, note = "OK", "保持原样"
        lines.append(f"| {rid} | {key} | {verdict} | {note} |")
    lines += [
        "", "---", "",
        "## 七、下一轮的取舍：`string resource` 化——本轮决定不做", "",
        "不是遗漏，是权衡：`Lang.kt` 的纯 Kotlin 三语 map + `StateFlow` 支持应用内即时切语言、",
        "不重建 Activity、不打断静坐；换成 `strings.xml` 要靠 `setApplicationLocales` / `LocaleManager`，",
        "通常触发 Activity 重建，计时中的一轮会被打断，四个通知显示表面全部要重测。",
        "对当前「完全离线 / 单 Activity / 三语 / 110 条」的应用，收益主要是符合惯例，",
        "而用户这轮的目标是「更好用」，不是「更好维护」。",
        "",
        "**触发条件（任一满足再开一轮）**：接第四种语言或翻译平台；文案要交外部译者；",
        "出现「系统语言切了 App 没跟上」的真实反馈；某次改动确实被即时切语言卡住。",
        "",
        "其余三条这轮已做且不需要用户动手：① 证据不变量 8 条静态断言合进术语闸门；",
        "② 三语 × 五个表面真机屏幕对账 + 1.3 倍字体实拍；③ CSV → `Lang.kt` 补丁脚本 + 命中校验。",
    ]
    DECISION.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--dry", action="store_true", help="只校验补丁是否命中，默认行为")
    args = ap.parse_args()
    rows = read_csv()
    changes = build_changes(rows)
    print(f"豆包建议 50 条：采纳三语 map {len(changes)} 条，否决 {len(REJECT)} 条，"
          f"改结构 {len(STRUCTURAL)} 条，动态文案 {len(DYNAMIC)} 条")

    lang_p = SRC / "Lang.kt"
    sound_p = SRC / "SoundCatalog.kt"
    main_p = SRC / "MainActivity.kt"
    new_lang = patch_dynamic_lang(patch_lang(lang_p.read_text(encoding="utf-8"), changes)[0])
    new_sound = patch_sound_catalog(sound_p.read_text(encoding="utf-8"))
    new_main = patch_main(main_p.read_text(encoding="utf-8"))

    if not args.apply:
        print("--dry：四处补丁全部命中，未写盘。")
        return 0
    lang_p.write_text(new_lang, encoding="utf-8")
    sound_p.write_text(new_sound, encoding="utf-8")
    main_p.write_text(new_main, encoding="utf-8")
    write_decision(rows, changes)
    print(f"已写盘：{lang_p.name} / {sound_p.name} / {main_p.name}；决定表 {DECISION.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
