#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""静息 zentimer · 真机主验收（分辨率无关版）。

从 v2.25 的 .craft/rea/v225/verify_v225.py 迁移而来，改动动机：11-11 换了
Redmi Note 15 Pro（1280x2772 @520dpi / Android 16 / SDK 36 / HyperOS 3），
旧脚本里所有固定像素坐标全部失效。这一版**所有点击都按 uiautomator 控件树
定位**，取色点按屏幕比例计算，因而换机不用再改坐标。

跑法：
    python tools/verify_device.py
可选环境变量：
    ZENTIMER_ADB     adb 绝对路径
    ZENTIMER_SERIAL  设备序列号（多机时必填；单机可留空用默认设备）
    ZENTIMER_OUT     证据输出目录（默认 .craft/rea/v225/note15）

取证口径（不认脚本自称成功，只认对方产物）：
  - 规则状态 / ZenPolicy：dumpsys notification 的 mConfig 段 ZenRule
  - 设备勿扰：settings get global zen_mode，以及 dumpsys 的 mInterruptionFilter
  - 四时纸色：screencap 原始像素，与《四季配色》方案色逐一比对
  - 界面文字与布局：uiautomator dump 的 text / content-desc / bounds

EXPECT 里的文案对应「当前 Lang.kt」。改文案后请同步改这里，否则 M16 会误报。
历史 tag 复跑时，把 EXPECT 改成那一版 Lang.kt 的文案即可。

脚本会临时改动勿扰授权、月份注入钩子、外观、语言、字体缩放，
退出前一律还原，并停掉计时态。
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import time
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADB = os.environ.get(
    "ZENTIMER_ADB",
    "D:/Users/thinkpad/workbuddy_data/workbuddy/2026-11-06-07-12-10/"
    ".buildtools/android-sdk/platform-tools/adb.exe",
)
SERIAL = os.environ.get("ZENTIMER_SERIAL", "")
OUT = Path(os.environ.get("ZENTIMER_OUT", ROOT / ".craft/rea/v225/note15"))
OUT.mkdir(parents=True, exist_ok=True)
PKG = "com.yaohui.zentimer"

# 方案「四季配色」纸色表逐字抄录；验收拿截图像素和它逐一比对，不读代码里写的值。
PLAN_PAPER = {
    "spring": (0xF6, 0xF5, 0xEF),
    "summer": (0xFA, 0xF9, 0xF4),
    "autumn": (0xF7, 0xF2, 0xE8),
    "winter": (0xF4, 0xF4, 0xF3),
}

# v2.26 文案（用户 11-11 口径）；改 Lang.kt 后同步这里。
# 四条改动项按三语齐备：season_fine_tune / season_note / dnd_title / dnd_sub。
EXPECT = {
    "season_title": "随节气换肤",
    "season_note": "随四季更替，自动更换主题色",
    "season_running_note": "静坐中不更换主题色",
    "dnd_title": "静坐时免打扰",
    "dnd_sub": "静坐时不被通知与来电打断，结束后自动恢复",
    "dnd_denied": "未获得勿扰权限",
    "dnd_active": "免打扰中",
    "en_season_title": "Seasonal theme",
    "en_season_note": "Theme colors change automatically with the seasons",
    "en_dnd_title": "Do not disturb while sitting",
    "en_dnd_sub": "Notifications and calls stay quiet; back to normal afterwards",
    "hant_season_title": "隨節氣換膚",
    "hant_season_note": "隨四季更替，自動更換主題色",
    "hant_dnd_title": "靜坐時免打擾",
    "hant_dnd_sub": "靜坐時不被通知與來電打斷，結束後自動恢復",
    # v2.26 方案 D：首页「声音」折叠头。可见文字只有「声音」，
    # 两个状态词与整个摘要由 clearAndSetSemantics 拼进 content-desc，用 cd_list 取。
    "sound_group": "铃声与背景",
    "fold_collapsed": "已收起",
    "fold_expanded": "已展开",
    "label_ambience": "背景音",
}

RESULTS: list[tuple[str, bool, str]] = []
HARNESS_OK = True
BOUNDS_RE = re.compile(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')


def sh(*a: str, timeout: int = 90) -> str:
    cmd = [ADB]
    if SERIAL:
        cmd += ["-s", SERIAL]
    cmd += list(a)
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout,
                          encoding="utf-8", errors="replace").stdout


def check(name: str, ok: bool, detail: str = "") -> None:
    RESULTS.append((name, bool(ok), detail))
    print(("PASS " if ok else "FAIL ") + name + ("  " + detail if detail else ""), flush=True)


def tap(x: int, y: int, pause: float = 0.9) -> None:
    sh("shell", "input", "tap", str(x), str(y))
    time.sleep(pause)


def dump(tag: str) -> str:
    for _ in range(3):
        xml = sh("exec-out", "uiautomator", "dump", "/dev/tty")
        if "<node" in xml:
            (OUT / f"{tag}.xml").write_text(xml, encoding="utf-8")
            return xml
        time.sleep(0.8)   # MIUI 偶发把 theme_config 异常先吐出来，重试
    return ""


def shot(tag: str) -> Path:
    cmd = [ADB]
    if SERIAL:
        cmd += ["-s", SERIAL]
    cmd += ["exec-out", "screencap", "-p"]
    p = OUT / f"{tag}.png"
    p.write_bytes(subprocess.run(cmd, capture_output=True, timeout=60).stdout)
    return p


def cd_list(xml: str) -> list[str]:
    return re.findall(r'content-desc="([^"]*)"', xml)


def text_list(xml: str) -> list[str]:
    return re.findall(r'text="([^"]*)"', xml)


def find_node(xml: str, needle: str, nth: int = 0,
              pred=None) -> tuple[int, int]:
    """按 text / content-desc 找控件，返回 bounds 中心；找不到返回 (0, 0)。

    pred(x, y, x1, y1, x2, y2) 可用来区分同名控件（例如两个「跟随系统」）。
    """
    hits: list[tuple[int, int]] = []
    for m in re.finditer(r"<node[^>]*>", xml):
        s = m.group(0)
        if needle not in s:
            continue
        b = BOUNDS_RE.search(s)
        if not b:
            continue
        x1, y1, x2, y2 = map(int, b.groups())
        cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
        if pred and not pred(cx, cy, x1, y1, x2, y2):
            continue
        hits.append((cx, cy))
    return hits[nth] if len(hits) > nth else (0, 0)


def tap_node(xml: str, needle: str, nth: int = 0, pred=None,
             pause: float = 1.0) -> bool:
    x, y = find_node(xml, needle, nth=nth, pred=pred)
    if x == 0:
        return False
    tap(x, y, pause)
    return True


# --------------------------------------------------------------------------
#  系统状态取证
# --------------------------------------------------------------------------
def notif_dump() -> str:
    return sh("shell", "dumpsys", "notification")


def zen_cfg() -> str:
    return notif_dump().split("    mConfig=", 1)[-1].split("    mSuppressedEffects", 1)[0]


def rule_entries() -> list[tuple[str, str, str, str]]:
    """按 ZenRule 块切分，返回 (id, state, pkg, 整块文本)。

    不用 `[^]]*?pkg=` 那种正则：ZenPolicy 里的 `priorityCategories=[...]`
    会提前吃掉 `]`，换机/换系统版本时极易漏匹配。
    """
    out = []
    for block in zen_cfg().split("ZenRule[")[1:]:
        m = re.search(r"id=([^,]+),state=([A-Z_]+)", block[:400])
        if not m:
            continue
        pkg = re.search(r"pkg=([^,]+)", block[:400])
        out.append((m.group(1), m.group(2), pkg.group(1) if pkg else "", block))
    return out


def our_rules() -> list[tuple[str, str]]:
    return [(i, s) for i, s, p, _ in rule_entries() if p == PKG]


def system_rules() -> list[tuple[str, str, str]]:
    return [(i, s, p) for i, s, p, _ in rule_entries() if p != PKG]


def rule_state() -> str:
    r = our_rules()
    return r[0][1] if r else "NONE"


def our_policy() -> dict[str, str]:
    for _, _, p, block in rule_entries():
        if p == PKG:
            m = re.search(r"alarms=(\w+)\s+media=(\w+)", block)
            if m:
                return {"alarms": m.group(1), "media": m.group(2)}
    return {}


def zen_mode() -> str:
    return sh("shell", "settings", "get", "global", "zen_mode").strip()


def interruption_filter() -> str:
    m = re.search(r"mInterruptionFilter=(\d+)", notif_dump())
    return m.group(1) if m else "UNKNOWN"


def zen_mode_sys() -> str:
    m = re.search(r"mZenMode=(\w+)", notif_dump())
    return m.group(1) if m else "UNKNOWN"


def screen_size() -> tuple[int, int]:
    s = sh("shell", "wm", "size")
    m = re.search(r"(\d+)x(\d+)", s)
    return (int(m.group(1)), int(m.group(2))) if m else (0, 0)


def cold(pause: float = 2.2) -> None:
    sh("shell", "am", "force-stop", PKG)
    sh("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(pause)


# --------------------------------------------------------------------------
#  界面导航（全部按控件树，不写死坐标；切到 English / 繁体后同样可用）
# --------------------------------------------------------------------------
def text_nodes(xml: str) -> list[tuple[int, int, str]]:
    """返回 (中心x, 中心y, text)。多语言界面靠「组内顺序」定位，不认具体词。"""
    out = []
    for m in re.finditer(r"<node[^>]*>", xml):
        s = m.group(0)
        t = re.search(r'text="([^"]*)"', s)
        if not t or not t.group(1).strip():
            continue
        b = BOUNDS_RE.search(s)
        if not b:
            continue
        x1, y1, x2, y2 = map(int, b.groups())
        out.append(((x1 + x2) // 2, (y1 + y2) // 2, t.group(1)))
    return out


def open_settings() -> str:
    """冷启动 → 点顶栏右上角图标 → 点抽屉第 2 个条目（设置）。

    不认「设置」这两个字：切到 English / 繁體后抽屉文字会变，但三个条目的
    y 顺序（预设 / 设置 / 关于）不变。顶栏图标按屏幕比例兜底。
    """
    cold()
    xml = dump("nav_home")
    w, h = screen_size()
    hit = (tap_node(xml, 'content-desc="设置"', pause=1.0)
           or tap_node(xml, 'content-desc="Settings"', pause=1.0)
           or tap_node(xml, 'text="设置"', pause=1.0))
    if not hit:
        tap(int(w * 0.90), int(h * 0.105), 1.1)
    drawer = dump("nav_drawer")
    items = sorted(
        [n for n in text_nodes(drawer)
         if 0.15 * h < n[1] < 0.46 * h and n[0] > 0.30 * w and len(n[2].strip()) >= 2],
        key=lambda n: n[1])
    # 抽屉里 y 由小到大是 预设 / 设置 / 关于；单字符的 › 已被 len 过滤。
    if len(items) < 2:
        return dump("nav_settings_failed")
    tap(items[1][0], items[1][1], 1.3)
    return dump("nav_settings")


def back_home() -> None:
    xml = dump("back_probe")
    if tap_node(xml, 'content-desc="返回"', pause=0.9):
        return
    if tap_node(xml, 'content-desc="Back"', pause=0.9):
        return
    w, h = screen_size()
    tap(int(w * 0.10), int(h * 0.10), 0.9)


def _group_options(xml: str, y_lo: float, y_hi: float) -> list[tuple[int, int, str]]:
    """取某一组三档选项，按 x 从左到右排序；排除左侧组标签与长说明行。"""
    w, h = screen_size()
    return sorted(
        [n for n in text_nodes(xml)
         if y_lo * h < n[1] < y_hi * h and n[0] > 0.15 * w and len(n[2].strip()) <= 16],
        key=lambda n: n[0])


def set_appearance(idx: int) -> None:
    """外观组三档：0 跟随系统 / 1 深色 / 2 浅色。按组内顺序，与语言无关。"""
    xml = open_settings()
    opts = _group_options(xml, 0.34, 0.56)
    if len(opts) >= 3:
        tap(opts[idx][0], opts[idx][1], 1.1)
    back_home()


def set_language(idx: int, tag: str) -> str:
    """语言组三档：0 简体 / 1 繁体 / 2 English。按组内顺序，与当前语言无关。"""
    xml = open_settings()
    opts = _group_options(xml, 0.17, 0.30)
    if len(opts) >= 3:
        tap(opts[idx][0], opts[idx][1], 1.2)
    return dump(tag)


def toggle_season() -> str:
    xml = open_settings()
    tap_node(xml, f'{EXPECT["season_title"]}，', pause=1.1)
    return dump("season_toggle")


def dnd_is_on(xml: str) -> bool:
    return any(f'{EXPECT["dnd_title"]}，开，' in c for c in cd_list(xml))


def set_dnd(target: bool, tag: str) -> str:
    """把免打扰开关拨到目标态：先读控件树，差一下才点，点完复查。

    v2.25 第一版验收在这里翻过车：固定坐标盲点，把开着的开关点成关，
    撤销权限时偏好本就是关，说明行当然不出现——不是 App 的缺陷，是脚本的。
    """
    xml = ""
    for _ in range(4):
        xml = dump(tag)
        cd = next((c for c in cd_list(xml)
                   if c.startswith(f'{EXPECT["dnd_title"]}，')), "")
        if not cd:
            time.sleep(0.7)
            continue
        if ("，开，" in cd) == target:
            return xml
        if not tap_node(xml, f'{EXPECT["dnd_title"]}，', pause=1.1):
            time.sleep(0.7)
            continue
    return xml


def stop_sitting() -> None:
    """按控件树上的「结束」按钮停表，点完复查；没有计时态就不动。"""
    for _ in range(3):
        xml = dump("stop_probe")
        if "结束" not in text_list(xml):
            return
        if not tap_node(xml, 'text="结束"', pause=1.6):
            return
    print("  ! 结束按钮点了三下仍在计时态", flush=True)


def start_sitting() -> bool:
    """起跑并**复查真的进了计时态**；起不来就把 HARNESS_OK 拉掉，不许空跑蒙混。

    两个坑：① 完成态的按钮叫「再来一次」不叫「开始静坐」；② 冷启动会把
    没结束的那一轮恢复回来，此时主屏是「结束 / 暂停」，得先停掉再起新的一轮。
    """
    global HARNESS_OK
    for _ in range(4):
        xml = dump("start_probe")
        labels = text_list(xml)
        if "结束" in labels or "暂停" in labels:
            stop_sitting()
            continue
        label = ('text="开始静坐"' if "开始静坐" in labels
                 else 'text="再来一次"' if "再来一次" in labels else None)
        if label is None:
            cold()
            time.sleep(0.6)
            continue
        if not tap_node(xml, label, pause=2.5):
            time.sleep(0.8)
            continue
        after = dump("start_after")
        if "结束" in text_list(after) or "暂停" in text_list(after):
            return True
        time.sleep(0.8)
    print("  ! 起跑没成功，相关用例一律不算通过", flush=True)
    HARNESS_OK = False
    return False


def set_month(m: int) -> None:
    sh("shell", "settings", "put", "global", "zentimer_season_month", str(m))


def bg_color(png: Path) -> tuple[int, int, int]:
    """取主屏左右边缘六个空白点的颜色众数作纸色。

    按屏幕比例取点，避开中心的圆环 / 按钮 / 文字；换分辨率不用改。
    """
    from PIL import Image
    im = Image.open(png).convert("RGB")
    w, h = im.size
    pts = [(int(w * fx), int(h * fy)) for fx, fy in (
        (0.03, 0.15), (0.97, 0.15),
        (0.03, 0.42), (0.97, 0.42),
        (0.03, 0.55), (0.97, 0.55),
    )]
    vals = [im.getpixel(p) for p in pts]
    return Counter(vals).most_common(1)[0][0]


def luminance(c: tuple[int, int, int]) -> float:
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


# --------------------------------------------------------------------------
#  准备：基线 + 干净现场
# --------------------------------------------------------------------------
base_zen = zen_mode()
base_filter = interruption_filter()
base_zen_mode_sys = zen_mode_sys()
base_sys_rules = system_rules()
sdk = int((sh("shell", "getprop", "ro.build.version.sdk").strip() or "0").split()[0] or 0)
release = sh("shell", "getprop", "ro.build.version.release").strip()
model = sh("shell", "getprop", "ro.product.model").strip()
ui = sh("shell", "getprop", "ro.miui.ui.version.name").strip()
w_px, h_px = screen_size()
env = (f"{model} / Android {release} (SDK {sdk}) / HyperOS {ui} / "
       f"{w_px}x{h_px} / font_scale="
       f"{sh('shell', 'settings', 'get', 'system', 'font_scale').strip()}")
print(f"环境：{env}", flush=True)
print(f"基线：zen_mode={base_zen} filter={base_filter} mZenMode={base_zen_mode_sys} "
      f"系统规则 {len(base_sys_rules)} 条\n", flush=True)

# 自己授予临时勿扰权限，别依赖上一轮手工敲过命令。
sh("shell", "cmd", "notification", "allow_dnd", PKG)
time.sleep(0.6)

# 上一轮验收可能把「静坐中」留在机器上；开工先清干净。
cold()
stop_sitting()

set_appearance(2)
cold()
# 干净基线：不管上一轮把免打扰开关留在哪边，都收到「关」，M 组再显式打开。
open_settings()
set_dnd(False, "M_base_switch")
back_home()

# --------------------------------------------------------------------------
#  L 组 · 四时（6 项）
# --------------------------------------------------------------------------
season_colors: dict[str, tuple[int, int, int]] = {}
for month, label in ((4, "spring"), (7, "summer"), (10, "autumn"), (1, "winter")):
    set_month(month)
    cold()
    season_colors[label] = bg_color(shot(f"L_{label}"))

vals = list(season_colors.values())
check("L1 四档纸色互不相同", len(set(vals)) == 4,
      " ".join(f"{k}={v}" for k, v in season_colors.items()))
exact = all(season_colors[k] == v for k, v in PLAN_PAPER.items())
check("L2 四档纸色逐一等于方案色且低彩度",
      exact and all(max(c) - min(c) <= 24 for c in vals),
      f"精确比对={exact} 最大通道差 {max(max(c) - min(c) for c in vals)}")

set_month(4)
cold()
before = bg_color(shot("L_before_sitting"))
start_sitting()
set_month(1)
sh("shell", "input", "keyevent", "3")     # HOME，切后台
time.sleep(1.2)
sh("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
time.sleep(1.5)
during = bg_color(shot("L_during_sitting"))
check("L3 静坐中跨季不换色、切后台回来也不换", before == during,
      f"起手 {before} → 静坐中 {during}")
check("L4 静坐中开关说明改为不改变纸色",
      any(EXPECT["season_running_note"] in c for c in cd_list(open_settings())),
      "设置页开关说明随计时态切换")
back_home()
stop_sitting()

set_month(4)
cold()
toggle_season()
cold()
off_color = bg_color(shot("L_fine_tune_off"))
check("L5 关闭微调后固定秋档，与 10 月一致", off_color == season_colors["autumn"],
      f"关={off_color} 秋={season_colors['autumn']}")
cold()
toggle_season()      # 复原

cold()
xml = dump("L_no_notice")
all_words = text_list(xml) + cd_list(xml)
check("L6 跨季冷启动零提示 / 零节气文案",
      not any(any(k in w for k in ("立春", "已切换", "今日", "节气到了", "秋", "春"))
              for w in all_words),
      f"屏幕文字 {len(all_words)} 条")
sh("shell", "settings", "delete", "global", "zentimer_season_month")

# --------------------------------------------------------------------------
#  M 组 · 静坐时免打扰与复原底线（18 项）
# --------------------------------------------------------------------------
cold()
# v2.26 方案 D：冷启动首页默认收起；折叠头自报「声音, …, 已收起」，
# 4 行里只有「背景音」这行被折掉，用它当收起判据。
fold_home = dump("D0_home_fold")
check("D0 首页声音折叠头常驻且默认收起",
      any(f'{EXPECT["sound_group"]}，' in c and EXPECT["fold_collapsed"] in c
          for c in cd_list(fold_home))
      and EXPECT["label_ambience"] not in text_list(fold_home),
      " | ".join(c for c in cd_list(fold_home) if EXPECT["sound_group"] in c))
open_settings()
xml = dump("M0_settings")
check("M0 设置页两处新开关齐备（标题 + 说明四条）",
      any(EXPECT["season_title"] in c and EXPECT["season_note"] in c for c in cd_list(xml)) and
      any(EXPECT["dnd_title"] in c and EXPECT["dnd_sub"] in c for c in cd_list(xml)),
      " | ".join(c for c in cd_list(xml) if "换肤" in c or "免打扰" in c))
set_dnd(True, "M0_after_on")
back_home()

start_sitting()
xml = dump("M1_running")
filter_during = interruption_filter()
policy_during = our_policy()
check("M1 起跑后规则激活且状态为 STATE_TRUE", rule_state() == "STATE_TRUE",
      f"规则 {our_rules()}")
check("M2 界面出现「免打扰中」小字", EXPECT["dnd_active"] in text_list(xml))
check("M3 系统原有规则未被改写", system_rules() == base_sys_rules,
      f"基线 {len(base_sys_rules)} 条 → 现在 {len(system_rules())} 条")
check("M4 只建一条自有规则", len(our_rules()) == 1, str(our_rules()))
rule_id = our_rules()[0][0] if our_rules() else ""

stop_sitting()
xml = dump("M5_stopped")
filter_after = interruption_filter()
check("M5 提前结束即停用规则", rule_state() == "STATE_FALSE", f"现为 {rule_state()}")
check("M6 界面「免打扰中」消失", EXPECT["dnd_active"] not in text_list(xml))
check("M7 设备勿扰回到用户规则说了算的状态", zen_mode() == base_zen,
      f"基线 {base_zen} → 现在 {zen_mode()}")

start_sitting()
check("M8 强杀前规则确为激活", rule_state() == "STATE_TRUE")
sh("shell", "am", "force-stop", PKG)
time.sleep(1.2)
check("M9 强杀后规则仍在系统侧（预期，靠兜底收）", rule_state() == "STATE_TRUE")
cold()
check("M10 冷启动自愈把残留规则停用", rule_state() == "STATE_FALSE",
      f"现为 {rule_state()}")
check("M11 自愈后设备勿扰仍未被改写", zen_mode() == base_zen)

start_sitting()
sh("shell", "am", "force-stop", PKG)
time.sleep(1.0)
sh("shell", "am", "broadcast", "-a", f"{PKG}.DND_RESTORE",
   "-n", f"{PKG}/.DndRestoreReceiver")
time.sleep(1.5)
check("M12 兜底广播在不打开 App 时也能停用规则", rule_state() == "STATE_FALSE",
      f"现为 {rule_state()}")

cold()
start_sitting()
stop_sitting()
check("M13 反复起停不叠加规则，id 不变",
      len(our_rules()) == 1 and (not rule_id or our_rules()[0][0] == rule_id),
      f"基线 id {rule_id[:8]}… 现在 {our_rules()}")

cold()
open_settings()
set_dnd(False, "M14_switch_off")
back_home()
start_sitting()
check("M14 开关关闭时起跑不改规则状态", rule_state() == "STATE_FALSE")
stop_sitting()

cold()
open_settings()
on_xml = set_dnd(True, "M15_switch_on")
print("  撤销前开关：", next((c for c in cd_list(on_xml)
                          if c.startswith(f'{EXPECT["dnd_title"]}，')), "缺失"), flush=True)
back_home()
sh("shell", "cmd", "notification", "disallow_dnd", PKG)
cold()
open_settings()
# v2.30 复核：说明行确实会出现，但冷启动后 Compose 重组可能晚一帧，
# 首轮 dump 抓不到就误判 FAIL。等它出现，最多 5 次 × 0.7s。
xml = ""
for _ in range(5):
    xml = dump("M15_revoked")
    if EXPECT["dnd_denied"] in " ".join(text_list(xml)):
        break
    time.sleep(0.7)
check("M15 撤销权限后 App 不崩且给说明",
      EXPECT["dnd_title"] in " ".join(cd_list(xml)) and
      not dnd_is_on(xml) and
      EXPECT["dnd_denied"] in " ".join(text_list(xml)),
      "开关弹回关 + 说明行可见")
back_home()

# 三语
sh("shell", "cmd", "notification", "allow_dnd", PKG)
cold()
open_settings()
for lang, idx, expects in (("en", 2, (EXPECT["en_season_title"], EXPECT["en_season_note"],
                                       EXPECT["en_dnd_title"], EXPECT["en_dnd_sub"])),
                           ("hant", 1, (EXPECT["hant_season_title"], EXPECT["hant_season_note"],
                                        EXPECT["hant_dnd_title"], EXPECT["hant_dnd_sub"]))):
    xml = set_language(idx, f"M16_{lang}")
    joined = " ".join(cd_list(xml))
    ok = all(e in joined for e in expects) and "undefined" not in xml.lower()
    check(f"M16 {lang} 四条新文案齐备无截断", ok, " / ".join(expects))
set_language(0, "lang_restore")
back_home()

# --------------------------------------------------------------------------
#  N 组 · 新机专项（补上一轮「未验」清单）
# --------------------------------------------------------------------------
check("N1 本轮覆盖 Android 15+ / HyperOS 行为变更机型",
      sdk >= 35 and bool(ui) and w_px > 0, env)

sh("shell", "settings", "put", "system", "font_scale", "1.3")
cold(2.8)
big = dump("N2_font13_home")
shot("N2_font13_home")
all_bounds = [tuple(map(int, b)) for b in BOUNDS_RE.findall(big)]
bad = [b for b in all_bounds if b[0] < 0 or b[2] > w_px or b[1] < 0 or b[3] > h_px]
big_words = text_list(big)
check("N2 1.3 倍字体下首屏无越界、关键文案完整",
      # v2.30：设置卡已删「静坐时长」行，改认脚边提示 + 清单首行，仍验「首屏关键文案完整」
      # v2.31：提示回到圆环内后被 clearAndSetSemantics 合进圆环读屏名，
      # 控件树里没有独立 text 节点，改从整份 dump（含 content-desc）里认。
      not bad and all(w in big_words for w in ("开始静坐", "提醒间隔"))
      and "轻触圆环改时长" in big,
      f"越界控件 {len(bad)} / 共 {len(all_bounds)}；文字 {len(big_words)} 条")
open_settings()
shot("N2_font13_settings")
big_set = dump("N2_font13_settings")
set_bad = [tuple(map(int, b)) for b in BOUNDS_RE.findall(big_set)
           if int(b[2]) > w_px or int(b[3]) > h_px]
check("N2b 1.3 倍字体下设置页无横向越界", not set_bad,
      f"越界 {len(set_bad)}；开关行 "
      + " | ".join(c for c in cd_list(big_set) if "微调" in c or "免打扰" in c))
back_home()
sh("shell", "settings", "put", "system", "font_scale", "1.0")
cold(2.2)

set_appearance(2)
cold()
light = bg_color(shot("N3_light"))
set_appearance(1)
cold()
dark = bg_color(shot("N3_dark"))
check("N3 深浅两套底色亮度分离、可辨",
      luminance(light) - luminance(dark) > 80,
      f"浅={light} 深={dark} 亮度差={luminance(light) - luminance(dark):.0f}")
set_appearance(0)
cold()

xml = dump("N4_home")
bounds = [tuple(map(int, b)) for b in BOUNDS_RE.findall(xml)]
out_of_screen = [b for b in bounds if b[0] < 0 or b[2] > w_px or b[1] < 0 or b[3] > h_px]
time_nodes = [n for n in text_nodes(xml)
                if re.fullmatch(r"\d{1,2}:\d{2}(:\d{2})?", n[2])]
time_y = time_nodes[0][1] if time_nodes else 0
# v2.31：用户否决 2.30「提示悬在圈外」，提示回到圆环内、被 clearAndSetSemantics
# 合进圆环读屏名，控件树里不再有独立 text 节点。判据因此改成两条可证事实：
# ① 圆环这个节点的 content-desc 含提示；② 全树不存在 text="轻触圆环改时长" 的独立行。
ring_nodes = [m.group(0) for m in re.finditer(r"<node[^>]*>", xml)]
ring_hint = [n for n in ring_nodes
             if "content-desc=" in n and "轻触圆环改时长" in n]
standalone = 'text="轻触圆环改时长"' in xml
check("N4 首屏无控件越界且提示仍在圆环内",
      not out_of_screen and bool(ring_hint) and not standalone,
      f"越界 {len(out_of_screen)} / 共 {len(bounds)}；"
      f"圆环读屏名含提示 {len(ring_hint)} 处 / 圈外独立提示行={standalone}（v2.31 不变量）")

check("N5 结束后设备级勿扰过滤回到基线",
      filter_after == base_filter and zen_mode_sys() == base_zen_mode_sys,
      f"基线 filter={base_filter}/{base_zen_mode_sys} → "
      f"起跑中 filter={filter_during} → 结束 filter={filter_after}/{zen_mode_sys()}")

check("N6 自有规则放行闹钟与媒体声（结束铃不被自伤）",
      policy_during.get("alarms") == "allow" and policy_during.get("media") == "allow",
      f"dumpsys ZenPolicy: {policy_during}")

# --------------------------------------------------------------------------
#  还原现场
# --------------------------------------------------------------------------
sh("shell", "cmd", "notification", "disallow_dnd", PKG)
sh("shell", "settings", "delete", "global", "zentimer_season_month")
sh("shell", "settings", "put", "system", "font_scale", "1.0")
cold()
set_appearance(0)
set_language(0, "lang_final_restore")
back_home()
cold()
stop_sitting()
sh("shell", "input", "keyevent", "3")
print("\n现场还原：勿扰权限已撤、月份钩子已删、外观回跟随系统、"
      "语言回简体、字体缩放 1.0、计时态已停", flush=True)

passed = sum(1 for _, ok, _ in RESULTS if ok)
summary = {
    "device": env, "sdk": sdk, "screen": [w_px, h_px],
    "passed": passed, "total": len(RESULTS), "harness_ok": HARNESS_OK,
    "base_zen": base_zen, "base_filter": base_filter,
    "filter_during": filter_during, "filter_after": filter_after,
    "base_sys_rules": len(base_sys_rules),
    "season_colors": {k: list(v) for k, v in season_colors.items()},
    "ring_to_duration_px": distance,
    "zen_policy_during": policy_during,
    "cases": [{"name": n, "ok": ok, "detail": d} for n, ok, d in RESULTS],
}
(OUT / "verify_note15_result.json").write_text(
    json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"\n{passed}/{len(RESULTS)} PASS   harness_ok={HARNESS_OK}", flush=True)
if not HARNESS_OK:
    print("!! 有起跑没真正发生，检查项全绿也不算通过", flush=True)
sys.exit(0 if passed == len(RESULTS) and HARNESS_OK else 1)
