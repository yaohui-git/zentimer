#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""三语真机文案对账：表格里是源码文字，这里读的是**屏幕上的文字**。

做法：`adb shell uiautomator dump` 取真实控件树（Compose 语义已透出），
      逐个语言走 主屏顶部 / 主屏滚动区 / 设置 / 关于 / 间隔铃选择器 五个表面。
三条断言：
  1. 屏幕上没有旧文案（`UI文案复核表_豆包回填.csv` 的第 6/7/8 列就是复核前的原值）；
  2. 屏幕上没有禁用词（冥想 / meditation / mindfulness…）；
  3. 屏幕文字能在 `Lang.kt` / `SoundCatalog.kt` 找到出处，找不到的列出来人工判
     （动态拼接的「5 分钟」「×3」「晚安 · 11月9日 星期一」会落在这里，属正常）。

顺便验 1.3 倍系统字体：只截两张图存盘，肉眼看过才写结论，脚本不假装能判截断。

用法：python tools/check_screen_copy.py [--adb <adb.exe>]
前提：手机已连、USB 调试已授权、装的是待测包。
"""
from __future__ import annotations

import argparse
import csv
import io
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "ZenTimer/app/src/main/java/com/yaohui/zentimer"
QC = ROOT / ".craft/video/qc/screencopy"
REPORT = ROOT / "三语真机文案对账.md"
DEFAULT_ADB = ("D:/Users/thinkpad/workbuddy_data/workbuddy/2026-11-06-07-12-10/"
               ".buildtools/android-sdk/platform-tools/adb.exe")
PKG = "com.yaohui.zentimer"
ACT = f"{PKG}/.MainActivity"
LANGS = [("hans", 207, "简体"), ("hant", 540, "繁體"), ("en", 872, "English")]
BANNED = ("冥想", "meditation", "Meditation", "meditating", "mindfulness")


def sh(adb: str, *a: str, timeout: int = 40) -> str:
    return subprocess.run([adb, *a], capture_output=True, text=True,
                          timeout=timeout, encoding="utf-8", errors="replace").stdout


def tap(adb: str, x: int, y: int, pause: float = 0.8) -> None:
    sh(adb, "shell", "input", "tap", str(x), str(y))
    time.sleep(pause)


def texts(adb: str, tag: str) -> list[str]:
    sh(adb, "shell", "uiautomator", "dump", "/sdcard/zt_copy.xml")
    xml = sh(adb, "shell", "cat", "/sdcard/zt_copy.xml")
    return [m for m in re.findall(r'text="([^"]*)"', xml) if m.strip()]


def tap_text(adb: str, labels: tuple[str, ...], pause: float = 1.0) -> bool:
    """按控件树文字定位，不硬编码 y 坐标——换语言 / 加大字体后抽屉项会挪位。"""
    sh(adb, "shell", "uiautomator", "dump", "/sdcard/zt_nav.xml")
    xml = sh(adb, "shell", "cat", "/sdcard/zt_nav.xml")
    for m in re.finditer(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        if m.group(1) in labels:
            l, t, r, b = map(int, m.groups()[1:])
            tap(adb, (l + r) // 2, (t + b) // 2, pause)
            return True
    return False


def screen(adb: str, name: str) -> None:
    QC.mkdir(parents=True, exist_ok=True)
    data = subprocess.run([adb, "exec-out", "screencap", "-p"],
                          capture_output=True, timeout=30).stdout
    (QC / f"{name}.png").write_bytes(data)


def parse_lang_values() -> dict[str, set[str]]:
    src = (SRC / "Lang.kt").read_text(encoding="utf-8")
    out: dict[str, set[str]] = {}
    for marker, which in (("private val HANS = mapOf(", "hans"),
                          ("private val EXTRA_HANS = mapOf(", "hans"),
                          ("private val HANT = mapOf(", "hant"),
                          ("private val EXTRA_HANT = mapOf(", "hant"),
                          ("private val EN = mapOf(", "en"),
                          ("private val EXTRA_EN = mapOf(", "en")):
        seg = src.split(marker, 1)[-1].split("\n    )", 1)[0]
        out.setdefault(which, set()).update(re.findall(r'to\s+"([^"]*)"', seg))
    # 音色名：每行 Sound(...) 的最后三个字符串依次是 简 / 繁 / 英
    cat = (SRC / "SoundCatalog.kt").read_text(encoding="utf-8")
    for ln in cat.splitlines():
        if "Sound(" not in ln or ln.lstrip().startswith("//"):
            continue
        vals = re.findall(r'"([^"]*)"', ln)
        if len(vals) >= 4:
            out["hans"].add(vals[-3]); out["hant"].add(vals[-2]); out["en"].add(vals[-1])
    # 发布常量（邮箱 / 微信 / 公众号 / 仓库 / 许可 / 署名）
    info = (SRC / "AppInfo.kt").read_text(encoding="utf-8")
    for v in re.findall(r'=\s*"([^"]+)"', info):
        for k in out:
            out[k].add(v)
    return out


def old_values() -> dict[str, set[str]]:
    raw = (ROOT / "UI文案复核表_豆包回填.csv").read_bytes()
    enc = "utf-8-sig" if raw[:3] == b"\xef\xbb\xbf" else "utf-8"
    rows = list(csv.reader(io.StringIO(raw.decode(enc))))
    out = {"hans": set(), "hant": set(), "en": set()}
    for r in rows[1:]:
        sug = r[-1].strip()
        if not sug.startswith("改：") or r[0] == "L030":
            # 只有「真的采纳并改过值」的旧值才算残留；
            # 标 OK 的行旧值 == 新值，L030 被否决，都不该报。
            continue
        parts = [p.strip() for p in sug[2:].split("|")]
        if len(parts) != 3:
            continue
        for key, col, new in (("hans", 5, parts[0]), ("hant", 6, parts[1]), ("en", 7, parts[2])):
            old = r[col].strip() if len(r) > col else ""
            if old and old != new:
                out[key].add(old)
    return out


def collect(adb: str, lang: str, x: int) -> dict[str, list[str]]:
    sh(adb, "shell", "am", "force-stop", PKG)
    sh(adb, "shell", "am", "start", "-n", ACT)
    time.sleep(1.8)
    tap(adb, 970, 210)          # 右上角菜单
    tap(adb, 700, 625)          # 设置
    tap(adb, x, 530)            # 语言按钮
    time.sleep(1.0)
    got = {"设置": texts(adb, f"{lang}_settings")}
    sh(adb, "shell", "input", "keyevent", "4")
    time.sleep(0.8)
    got["主屏顶部"] = texts(adb, f"{lang}_home")
    sh(adb, "shell", "input", "swipe", "540", "1900", "540", "900", "400")
    time.sleep(0.8)
    got["主屏设置区"] = texts(adb, f"{lang}_home2")
    tap(adb, 528, 1517, 1.0)   # 间隔铃声行（三种语言位置一致）
    got["间隔铃选择器"] = texts(adb, f"{lang}_picker")
    sh(adb, "shell", "input", "keyevent", "4")
    time.sleep(0.6)
    sh(adb, "shell", "input", "swipe", "540", "700", "540", "1900", "400")
    time.sleep(0.6)
    tap(adb, 970, 210)
    tap(adb, 700, 773, 1.0)    # 关于
    got["关于"] = texts(adb, f"{lang}_about")
    return got


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--adb", default=DEFAULT_ADB)
    ap.add_argument("--font-scale", action="store_true", help="顺带把系统字体调到 1.3 截图")
    ap.add_argument("--font-only", action="store_true", help="只跑字体截图，跳过三语对账")
    args = ap.parse_args()
    adb = args.adb
    if not Path(adb).exists():
        print("adb 不存在：" + adb)
        return 2
    dev = sh(adb, "devices", "-l")
    if "product:" not in dev:
        print("没有可用设备：\n" + dev)
        return 2

    expected = parse_lang_values()
    stale = old_values()
    # 改后仍等于改前的值不算残留
    raw = (ROOT / "UI文案复核表_豆包回填.csv").read_bytes()
    rows = list(csv.reader(io.StringIO(raw.decode("utf-8-sig" if raw[:3] == b"\xef\xbb\xbf" else "utf-8"))))
    for r in rows[1:]:
        if r[-1].strip().startswith("改："):
            parts = [p.strip() for p in r[-1][2:].split("|")]
            if len(parts) == 3:
                for k, i in (("hans", 0), ("hant", 1), ("en", 2)):
                    stale[k].discard(parts[i])

    lines = ["# 三语真机文案对账（屏幕文字 vs 源码文字）", "",
             "> 由 `tools/check_screen_copy.py` 生成。表格是源码文字，这里读的是"
             "`uiautomator` 控件树里的**屏幕文字**。",
             "> 设备：" + " / ".join(sh(adb, "shell", "getprop", k).strip()
                                  for k in ("ro.product.model", "ro.build.version.release")), ""]
    def open_drawer():
        # 右缘左滑：与语言、字体大小、控件坐标都无关，比点右上角图标稳
        sh(adb, "shell", "input", "swipe", "1060", "900", "300", "900", "300")
        time.sleep(0.9)

    def restore_chinese():
        sh(adb, "shell", "am", "force-stop", PKG)
        sh(adb, "shell", "am", "start", "-n", ACT)
        time.sleep(1.6)
        open_drawer()
        if tap_text(adb, ("设置", "設定", "Settings"), 1.0):
            tap_text(adb, ("简体", "简体中文", "簡體"), 0.8)
        sh(adb, "shell", "am", "force-stop", PKG)

    total_old, total_banned, total_unknown = 0, 0, 0
    if not args.font_only:
        for lang, x, label in LANGS:
            got = collect(adb, lang, x)
            seen = {s for group in got.values() for s in group}
            old_hits = sorted(seen & stale[lang])
            banned_hits = sorted(s for s in seen if any(b in s for b in BANNED))
            unknown = sorted(s for s in seen if s not in expected[lang]
                             and not re.fullmatch(r"[0-9]+\s*(分钟|分鐘|秒|min|s|h)", s)
                             and s not in {"›", "↺ 跟随系统", "✓", "×1", "×2", "×3"})
            total_old += len(old_hits)
            total_banned += len(banned_hits)
            total_unknown += len(unknown)
            lines += [f"## {label}（{lang}）", "",
                      f"- 走过 {len(got)} 个表面，抓到 {len(seen)} 条独立文字；",
                      f"- 旧文案残留：**{len(old_hits)}**" + ("（" + "、".join(old_hits) + "）" if old_hits else ""),
                      f"- 禁用词：**{len(banned_hits)}**" + ("（" + "、".join(banned_hits) + "）" if banned_hits else ""),
                      f"- 源码里找不到出处的 {len(unknown)} 条（多为动态拼接，需人看一眼）：",
                      "  " + ("、".join(unknown[:28]) if unknown else "（无）"), ""]
            for surface, vals in got.items():
                lines.append(f"  - {surface}：{' / '.join(vals[:12])}")
            lines.append("")
        restore_chinese()   # 三语切换是副作用，走完必须把 App 还给用户的简体

    if args.font_scale or args.font_only:
        before = sh(adb, "shell", "settings", "get", "system", "font_scale").strip() or "1.0"
        shots = []
        try:
            sh(adb, "shell", "settings", "put", "system", "font_scale", "1.3")
            time.sleep(1.2)
            for key, lang_labels in (("hans", ("简体", "简体中文", "簡體")),
                                    ("en", ("English",))):
                sh(adb, "shell", "am", "force-stop", PKG)
                sh(adb, "shell", "am", "start", "-n", ACT)
                time.sleep(1.8)
                open_drawer()
                tap_text(adb, ("设置", "設定", "Settings"), 1.1)
                tap_text(adb, lang_labels, 1.0)
                screen(adb, f"font13_{key}_settings")
                shots.append(f"font13_{key}_settings")
                sh(adb, "shell", "input", "keyevent", "4")
                time.sleep(0.7)
                open_drawer()
                tap_text(adb, ("关于", "關於", "About"), 1.2)
                screen(adb, f"font13_{key}_about")
                shots.append(f"font13_{key}_about")
        finally:
            # 系统级设置必须还原，哪怕中途抛错
            sh(adb, "shell", "settings", "put", "system", "font_scale", before)
            sh(adb, "shell", "am", "force-stop", PKG)
            restore_chinese()
        lines += ["## 1.3 倍系统字体", "",
                  f"- 原值 `{before}`，截图后已还原；App 语言已还原为简体。",
                  f"- 截了 {len(shots)} 张：{'、'.join(shots)}，在 `.craft/video/qc/screencopy/`。",
                  "- 截断与否需肉眼看图，脚本不下结论。", ""]

    lines += ["## 结论", "",
              f"旧文案 {total_old} 条 / 禁用词 {total_banned} 条 / 待人工判读 {total_unknown} 条。",
              "", "> 注意：控件树给的是完整 text，视觉上被省略号截掉的字这里仍会显示全，"
              "所以「大字截断」必须另看截图，不能拿本表代替。"]
    target = ROOT / "大字模式截图记录.md" if args.font_only else REPORT
    target.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"旧文案 {total_old} / 禁用词 {total_banned} / 待人工判读 {total_unknown} → {target.name}")
    return 1 if (total_old or total_banned) else 0


if __name__ == "__main__":
    sys.exit(main())
