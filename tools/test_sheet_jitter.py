#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""真机回归闸门：声音选择弹层在「猛滑」时会不会整张卡片上下抖。

背景（11-09 22:24 事故）：
  SoundPicker 用 ModalBottomSheet + LazyVerticalGrid。Material3 1.4.0 给弹层装的
  ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection 会把内层网格吃剩的
  位移 / 惯性直接 dispatchRawDelta 给 sheet，整张卡片被拽下再弹回。
  v2.18 误判成「主页面 overscroll 拉伸」，改错地方；v2.19 用
  sheetGesturesEnabled = false 去掉这条 nestedScroll 连接。

这个脚本做三件事：
  1. 用 uiautomator 文字定位打开三个铃声弹层（不硬编码坐标）；
  2. 系统录屏 + 连续猛滑，逐帧量「拖拽把手 / 确定按钮」的 y 位移；
  3. 再往上甩一次，确认网格还能正常滚（别把滚动一起关掉）。

判据：把手 y 的极差 <= 4 像素（四分之一缩尺，即真机 16px）视为 PASS。
输出：`tools/out/sheet_jitter_<label>.json` 与逐帧数据，供修前 / 修后 A/B 对照。

用法：
  python tools/test_sheet_jitter.py --label after
  python tools/test_sheet_jitter.py --label before --apk <旧包路径>   # 先 adb install -r
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import time
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "tools" / "out"
TMP = OUT / "_jit"
PKG = "com.yaohui.zentimer"
ACT = f"{PKG}/.MainActivity"
DEFAULT_ADB = (
    "D:/Users/thinkpad/workbuddy_data/workbuddy/2026-11-06-07-12-10/"
    ".buildtools/android-sdk/platform-tools/adb.exe"
)
PICKERS = ["开始铃声", "间隔铃声", "结束铃声"]
# 设备 1080x2340：手势区避开底部三大金刚键与顶部状态栏
SWIPE_X, SWIPE_Y0, SWIPE_Y1 = 540, 760, 1960
UP_Y0, UP_Y1 = 1960, 700


def sh(adb: str, *args: str, timeout: int = 40) -> str:
    return subprocess.run(
        [adb, *args], capture_output=True, text=True, timeout=timeout,
        encoding="utf-8", errors="replace",
    ).stdout.strip()


def dump_ui(adb: str) -> str:
    sh(adb, "shell", "uiautomator", "dump", "/sdcard/zt_ui.xml")
    return sh(adb, "shell", "cat", "/sdcard/zt_ui.xml")


def find_text_center(xml: str, text: str) -> tuple[int, int] | None:
    """取控件树里文字节点的中心；bounds=[l,t][r,b]。"""
    for m in re.finditer(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        if m.group(1) == text:
            l, t, r, b = map(int, m.groups()[1:])
            if r > l and b > t:
                return (l + r) // 2, (t + b) // 2
    return None


def open_picker(adb: str, label: str) -> bool:
    sh(adb, "shell", "am", "force-stop", PKG)
    sh(adb, "shell", "am", "start", "-n", ACT)
    time.sleep(1.8)
    # 主页面是可滚动 Column，铃声行在首屏之下；先甩一次让它进入组合树
    sh(adb, "shell", "input", "swipe", "540", "1900", "540", "900", "400")
    time.sleep(0.8)
    xml = dump_ui(adb)
    pos = find_text_center(xml, label)
    if pos is None:
        print(f"  !! 控件树里找不到「{label}」，跳过")
        return False
    x, y = pos
    sh(adb, "shell", "input", "tap", str(x), str(y))
    time.sleep(1.1)
    xml2 = dump_ui(adb)
    if "选择" not in xml2:
        print(f"  !! 点 ({x},{y}) 后没进选择弹层，跳过")
        return False
    print(f"  打开「{label}」→ 点击 ({x},{y})")
    return True


def record(adb: str, label: str, dest: Path) -> None:
    remote = f"/sdcard/zt_jit_{label}.mp4"
    sh(adb, "shell", "rm", "-f", remote)
    # 用 Popen 起系统录屏，--time-limit 到点自停；期间注入连续猛滑
    p = subprocess.Popen(
        [adb, "shell", "screenrecord", "--bit-rate", "6000000",
         "--time-limit", "6", remote],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    time.sleep(1.0)
    for _ in range(6):
        sh(adb, "shell", "input", "swipe", str(SWIPE_X), str(SWIPE_Y0),
           str(SWIPE_X), str(SWIPE_Y1), "110")
        time.sleep(0.42)
    # 再往上甩：修完网格必须还能滚，用两张截图作证
    before = TMP / f"{label}_grid_before.png"
    with before.open("wb") as f:
        f.write(subprocess.run([adb, "exec-out", "screencap", "-p"],
                               capture_output=True, timeout=30).stdout)
    sh(adb, "shell", "input", "swipe", str(SWIPE_X), str(UP_Y0),
       str(SWIPE_X), str(UP_Y1), "130")
    time.sleep(0.7)
    after = TMP / f"{label}_grid_after.png"
    with after.open("wb") as f:
        f.write(subprocess.run([adb, "exec-out", "screencap", "-p"],
                               capture_output=True, timeout=30).stdout)
    try:
        p.wait(timeout=12)
    except subprocess.TimeoutExpired:
        p.terminate()
    time.sleep(0.4)
    sh(adb, "pull", remote, str(dest))
    return before, after


def grid_scrolled(before: Path, after: Path) -> float:
    try:
        a = np.asarray(Image.open(before).convert("RGB"), dtype=np.float32)
        b = np.asarray(Image.open(after).convert("RGB"), dtype=np.float32)
        if a.shape != b.shape:
            return -1.0
        h = a.shape[0]
        region = (slice(int(h * 0.18), int(h * 0.72)), slice(None))
        return float(np.abs(a[region] - b[region]).mean())
    except Exception:
        return -1.0


def analyse(video: Path, label: str) -> dict:
    frames = TMP / f"{label}_frames"
    frames.mkdir(parents=True, exist_ok=True)
    for f in frames.glob("*.png"):
        f.unlink()
    subprocess.run(
        ["ffmpeg", "-hide_banner", "-loglevel", "error", "-i", str(video),
         "-vf", "fps=30,scale=270:-1", str(frames / "f_%03d.png"), "-y"],
        check=True, capture_output=True,
    )
    handles, buttons = [], []
    for f in sorted(frames.glob("f_*.png")):
        a = np.asarray(Image.open(f).convert("RGB"), dtype=np.float32)
        # 拖拽把手：正中窄带里的浅灰胶囊
        band = a[:220, 115:155, :].mean(axis=(1, 2))
        idx = np.where(band > 95)[0]
        handles.append(round(float(idx.mean()), 1) if len(idx) else None)
        # 确定的亮黄按钮
        m = (a[:, :, 0] > 170) & (a[:, :, 1] > 140) & (a[:, :, 2] < 160)
        yy, _ = np.where(m)
        buttons.append(round(float(yy.mean()), 1) if len(yy) else None)

    def stat(series):
        g = [v for v in series if v is not None]
        return {"n": len(g), "min": min(g) if g else None,
                "max": max(g) if g else None,
                "range": round(max(g) - min(g), 1) if g else None}

    return {"frames": len(handles), "handle": stat(handles),
            "button": stat(buttons),
            "handle_series": handles, "button_series": buttons}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--label", default="after")
    ap.add_argument("--adb", default=DEFAULT_ADB)
    ap.add_argument("--pass-range", type=float, default=4.0)
    ap.add_argument("--pickers", nargs="*", default=PICKERS)
    args = ap.parse_args()
    adb = args.adb
    if not Path(adb).exists():
        print(f"adb 不存在：{adb}")
        return 2
    OUT.mkdir(parents=True, exist_ok=True)
    TMP.mkdir(parents=True, exist_ok=True)

    dev = sh(adb, "devices", "-l")
    if "device" not in dev or "product:" not in dev:
        print("没有可用 adb 设备：\n" + dev)
        return 2
    print("设备：" + " / ".join(
        sh(adb, "shell", "getprop", k) for k in
        ("ro.product.model", "ro.build.version.release")))

    results = []
    for i, name in enumerate(args.pickers):
        print(f"[{i+1}/{len(args.pickers)}] {name}")
        if not open_picker(adb, name):
            results.append({"picker": name, "error": "打开失败"})
            continue
        tag = f"{args.label}_{i}"
        video = TMP / f"{tag}.mp4"
        before, after = record(adb, tag, video)
        if not video.exists() or video.stat().st_size < 10000:
            results.append({"picker": name, "error": "录屏失败"})
            continue
        r = analyse(video, tag)
        r.update({"picker": name,
                  "grid_mae": round(grid_scrolled(before, after), 2),
                  "video": str(video.relative_to(ROOT))})
        rng = r["handle"]["range"]
        r["verdict"] = "PASS" if rng is not None and rng <= args.pass_range else "FAIL"
        results.append(r)
        print(f"  把手 y 极差 {rng} px（阈值 {args.pass_range}）｜"
              f"按钮 y 极差 {r['button']['range']} px｜"
              f"上甩网格变化 {r['grid_mae']} → {r['verdict']}")
        # 关掉弹层，回到主页面
        sh(adb, "shell", "input", "keyevent", "4")
        time.sleep(0.5)

    report = OUT / f"sheet_jitter_{args.label}.json"
    report.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    ok = all(r.get("verdict") == "PASS" for r in results)
    scroll_ok = all(r.get("grid_mae", -1) > 5 for r in results)
    print(f"\n汇总：抖动 {'PASS' if ok else 'FAIL'}｜网格仍可滚 {'PASS' if scroll_ok else 'FAIL'}")
    print("报告：" + str(report.relative_to(ROOT)))
    return 0 if (ok and scroll_ok) else 1


if __name__ == "__main__":
    sys.exit(main())
