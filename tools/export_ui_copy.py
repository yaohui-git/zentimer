#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 App 内全部用户可见文案导出成一张可交给 AI 复核的表。

来源（不手写第二份，全从源码解析）：
  · Lang.kt 六张表（HANS / HANT / EN / EXTRA_*）
  · Lang.greetingLine() / minutes() / seconds() 里的动态文案（手工登记，带行号）
  · SoundCatalog.kt 的铃声 / 背景音名称
  · AppInfo.kt 的署名、公众号、联系方式
  · 代码里对 Lang.t / Lang.tx 的引用次数

输出 zentimer/UI文案复核表.csv（UTF-8 BOM，Excel 直接开）。
每行自带「分组 / 场景 / 来源行号 / 引用次数 / 本轮状态 / 复核标注 / 待办」，
AI 可以直接按这张表逐条给修改意见，改完回填「建议」列即可。

「本轮状态」不写死：拿当前源码和已发布基线（公开库镜像 .craft/tmp_remote）逐条比对，
值不同才标「本轮已改」，相同标「未改（原样）」，基线缺失时标「未判定（无发布基线）」，
不再靠一张手写的 key 白名单猜。

用法：python tools/export_ui_copy.py
"""
from __future__ import annotations

import csv
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "ZenTimer" / "app" / "src" / "main" / "java" / "com" / "yaohui" / "zentimer"
OUT = ROOT / "UI文案复核表.csv"
# 已发布版本的源码基线：公开库镜像（发布时由 tools/sync_public.py 生成）。
# 只用来判断「本轮状态」，不存在时不影响导出，只把状态标成「未判定」。
BASELINE_SRC = ROOT / ".craft" / "tmp_remote" / "ZenTimer" / "app" / "src" / "main" / "java" / "com" / "yaohui" / "zentimer"

BANNED = ("冥想", "meditation", "Meditation", "meditating", "Meditating", "mindfulness")
# 繁体里不该出现的简体字（够用即可，不追求覆盖全部简繁差异）
SIMPLE_IN_HANT = set("静时铃练习准备开关连续网际资讯开启关闭声钟点后里几为与从众个门间问闻间")

GROUPS = [
    (r"^btn_|^back_again$|^cancel$|^off$", "按钮 / 操作", "主屏、对话框、子页"),
    (r"^state_|^hint_|^catchup$|^toast_", "状态 / 提示", "圆环内、Toast、完成态"),
    (r"^section_|^label_|^picker_|^settings_|^theme_|^lang_|^notify_|^notif_|^infinite$|^copied$", "设置 / 通知", "设置卡、选择器、系统通知"),
    (r"^preset_", "预设", "预设页、底部面板"),
    (r"^about_|^donate_", "关于 / 打赏", "关于页、打赏弹窗"),
    (r"^menu_|^title_", "导航", "抽屉、子页标题"),
]


def group_of(key: str) -> tuple[str, str]:
    for pat, g, scene in GROUPS:
        if re.search(pat, key):
            return g, scene
    return "其它", "待定位"


def read_lines(name: str) -> list[str]:
    return (SRC / name).read_text(encoding="utf-8").splitlines()


def parse_lang(src: Path = SRC):
    """返回 {key: {hans/hant/en: (value, line)}}，以及每个 key 的行号。"""
    text = (src / "Lang.kt").read_text(encoding="utf-8")
    lines = text.splitlines()
    tables: dict[str, dict[str, tuple[str, int]]] = {}
    for m in re.finditer(r"private val (\w+) = mapOf\((.*?)\n    \)", text, re.S):
        name, body = m.group(1), m.group(2)
        lang = {"HANS": "hans", "HANT": "hant", "EN": "en",
                "EXTRA_HANS": "hans", "EXTRA_HANT": "hant", "EXTRA_EN": "en"}.get(name)
        if not lang:
            continue
        bucket = tables.setdefault(lang, {})
        for km in re.finditer(r'^\s*"([a-z_0-9]+)"\s+to\s+"((?:[^"\\]|\\.)*)"', body, re.M):
            key, val = km.group(1), km.group(2)
            # 找源码行号：以 key 首次出现在 map 内的那一行为准
            offset = text[: m.start(2) + km.start()].count("\n")
            line = offset + 1
            for i in range(offset, min(offset + 3, len(lines))):
                if f'"{key}"' in lines[i]:
                    line = i + 1
                    break
            bucket[key] = (val, line)
    return tables, lines


def count_refs() -> Counter:
    refs: Counter = Counter()
    for f in SRC.glob("*.kt"):
        t = f.read_text(encoding="utf-8")
        for key in re.findall(r'\b(?:Lang\.)?t(?:x)?\("([a-z_0-9]+)"\)', t):
            refs[key] += 1
        for key in re.findall(r'"(state_[a-z_0-9]+|picker_[a-z_0-9]+|menu_[a-z_0-9]+|section_[a-z_0-9]+|label_[a-z_0-9]+)"', t):
            refs[key] += 1
    return refs


def parse_sounds(src: Path = SRC):
    text = (src / "SoundCatalog.kt").read_text(encoding="utf-8")
    pat = re.compile(
        r'Sound\(\s*"([^"]+)"\s*,\s*([^,]+?)\s*,\s*([^,]+?)\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*\)'
    )
    out = []
    for m in pat.finditer(text):
        line = text[: m.start()].count("\n") + 1
        out.append((m.group(1), m.group(4), m.group(5), m.group(6), line))
    return out


def dynamic_rows():
    """Lang.kt 里不落在 map 中、但用户看得见的动态串。"""
    return [
        ("greet.hans.night", "其它", "首页问候（简体）", "动态文案", "夜深了 / 早上好 / 中午好 / 下午好 / 晚上好", "", "", "Lang.kt:188-195"),
        ("greet.hant", "其它", "首页问候（繁体）", "动态文案", "", "夜深了 / 早安 / 午安 / 午後好 / 晚安", "", "Lang.kt:177-185"),
        ("greet.en", "其它", "首页问候（English）", "动态文案", "", "", "Still awake / Good morning / Good afternoon / Good evening", "Lang.kt:168-174"),
        ("date.hans", "其它", "首页日期（简体）", "动态格式", "M月d日 星期X", "", "MMM d", "Lang.kt:174,196"),
        ("unit.hour", "其它", "时长单位", "动态单位", "2 小时", "2 小時", "2 h", "Lang.kt:203-207"),
        ("unit.min", "其它", "时长单位", "动态单位", "N 分钟", "N 分鐘", "N min", "Lang.kt:208-212"),
        ("unit.sec", "其它", "准备时间单位", "动态单位", "N 秒", "N 秒", "N s", "Lang.kt:215-225"),
        ("unit.times", "其它", "响铃次数", "动态单位", "×N", "×N", "×N", "Lang.kt:228"),
        ("ui.chevron", "其它", "关于页行尾箭头", "硬编码符号", "›", "›", "›", "MainActivity.kt:655"),
    ]


def appinfo_rows():
    text = (SRC / "AppInfo.kt").read_text(encoding="utf-8")
    out = []
    for m in re.finditer(r'const val (\w+) = "([^"]*)"', text):
        line = text[: m.start()].count("\n") + 1
        out.append((m.group(1), m.group(2), line))
    return out


def status_of(key: str, vals: list[str], base: dict | None) -> str:
    """本轮状态＝当前三语值 vs 已发布基线；不再维护手写 key 白名单。

    base 与 parse_lang() 同构：{lang: {key: (value, line)}}。
    """
    if base is None:
        return "未判定（无发布基线）"
    hit, same = False, True
    for lang, v in zip(("hans", "hant", "en"), vals):
        entry = base.get(lang, {}).get(key)
        if entry is None:
            continue
        hit = True
        if entry[0] != v:
            same = False
    if not hit:
        return "本轮新增"
    return "未改（原样）" if same else "本轮已改"


def sound_status(sid: str, vals: tuple[str, str, str], base: dict | None) -> str:
    if base is None:
        return "未判定（无发布基线）"
    old = base.get(sid)
    if old is None:
        return "本轮新增"
    return "未改（原样）" if old == vals else "本轮已改"


def notes_for(key, vals, refs, status) -> tuple[str, str, str]:
    """返回 (本轮状态, 复核标注, 待办)。"""
    hans, hant, en = vals
    joined = " ".join(vals)
    flags = []
    if any(b in joined for b in BANNED):
        flags.append("⚠ 命中禁用术语")
    if refs == 0 and not key.startswith(("ui.", "unit.", "greet.", "date.")):
        # picker_ / state_ 这类常由 "picker_" + key 动态拼出来，静态扫不到不等于没用
        flags.append("· 静态未见字面引用（可能由变量拼接），请确认是否死文案")
    bad = sorted({c for c in hant if c in SIMPLE_IN_HANT})
    if bad:
        flags.append(f"⚠ 繁体混入简体字：{''.join(bad)}")
    if re.search(r"[\u4e00-\u9fff]", en):
        flags.append("⚠ 英文串里出现汉字")
    if len(hans) > 24:
        flags.append("⚠ 简体长文案，检查窄屏换行")
    if len(en) > 62:
        flags.append("⚠ 英文长文案，检查窄屏换行")
    ends = [v.rstrip(" ·…")[-1:] if v.rstrip(" ·…") else "" for v in vals]
    if any(e in "。.!?！？" for e in ends) and len({e in "。.!?！？" for e in ends}) > 1:
        flags.append("· 三语句末标点不齐")
    if key.startswith("btn_") and len(hans.replace(" ", "")) > 5:
        flags.append("· 按钮文字偏长，动词是否统一请复核")
    if not flags:
        flags.append("合规 · 请 AI 复核语气与简繁对等")
    if refs == 0 and not key.startswith(("ui.", "unit.", "greet.", "date.")):
        status = "静态未见引用（待确认）"
    return status, "；".join(flags), ""


def main():
    tables, _ = parse_lang()
    base_lang = parse_lang(BASELINE_SRC)[0] if (BASELINE_SRC / "Lang.kt").exists() else None
    base_sounds = ({sid: (h, ht, e) for sid, h, ht, e, _ in parse_sounds(BASELINE_SRC)}
                   if (BASELINE_SRC / "SoundCatalog.kt").exists() else None)
    refs = count_refs()
    keys = sorted(set().union(*[set(d) for d in tables.values()]))
    rows = []

    for key in keys:
        vals, lines = [], []
        for lang in ("hans", "hant", "en"):
            v, ln = tables.get(lang, {}).get(key, ("", 0))
            vals.append(v)
            lines.append(ln)
        grp, scene = group_of(key)
        status, note, todo = notes_for(key, vals, refs[key], status_of(key, vals, base_lang))
        rows.append([
            f"L{len(rows)+1:03d}", grp, scene, "三语文案", key,
            vals[0], vals[1], vals[2],
            f"Lang.kt:{lines[0]}/{lines[1]}/{lines[2]}",
            refs[key], status, note, todo,
        ])

    for sid, hans, hant, en, ln in parse_sounds():
        grp = "音色 / 背景音" if sid.startswith("amb_") or sid == "none" else "音色 / 铃声"
        vals = (hans, hant, en)
        flag = []
        if any(b in " ".join(vals) for b in BANNED):
            flag.append("⚠ 命中禁用术语")
        if sid == "none":
            flag.append("v2.18 新增：间隔铃默认「无」，res=0 不排铃")
        if re.search(r"[\u4e00-\u9fff]", en):
            flag.append("⚠ 英文名里出现汉字")
        rows.append([
            f"S{len(rows)+1:03d}", grp, "铃声 / 背景音选择器", "素材名称", sid,
            hans, hant, en, f"SoundCatalog.kt:{ln}", "—",
            sound_status(sid, vals, base_sounds),
            "；".join(flag) or "合规 · 请 AI 复核音色命名是否统一", "",
        ])

    for key, grp, scene, kind, a, b, c, src in dynamic_rows():
        rows.append([
            f"D{len(rows)+1:03d}", grp, scene, kind, key, a, b, c, src, "—",
            "未改（原样）", "动态拼接，需 AI 复核数字与单位之间的空格、简繁日期格式", "",
        ])

    for name, val, ln in appinfo_rows():
        note = "署名 / 联系信息；法名按上次口径只用于版权与法律文本，界面作者栏是否需要统一称呼请人确认"
        if name in ("WECHAT", "EMAIL", "REPO", "LICENSE"):
            note = "不可变串，禁止改动"
        rows.append([
            f"A{len(rows)+1:03d}", "发布信息", "关于页 / 打赏", "常量", name,
            val, "", "", f"AppInfo.kt:{ln}", "—", "未改（原样）", note, "",
        ])

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(["ID", "分组", "场景 / 位置", "文案类型", "key / 标识",
                    "简体", "繁體", "English", "来源文件:行号", "代码引用次数",
                    "本轮状态", "复核标注", "AI 建议（留空待填）"])
        w.writerows(rows)

    print(f"导出 {len(rows)} 行 → {OUT}")
    print("本轮状态基线：", "已发布镜像 .craft/tmp_remote" if base_lang else "缺失（状态列标为未判定）")
    print("分组：", dict(Counter(r[1] for r in rows)))
    print("状态：", dict(Counter(r[10] for r in rows)))
    print("未引用 key：", [r[4] for r in rows if r[9] == 0 and r[3] == "三语文案"])


if __name__ == "__main__":
    main()
