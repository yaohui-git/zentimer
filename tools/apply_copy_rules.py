#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""术语统一：把用户可见文案里的「冥想 / meditation」类词替换成「静坐 / Zen」。

11-09 迭代计划第 2 条。声明式规则、逐条精确匹配、可反复跑（第二次应报 0 处）。
故意只改「当前对外物料」与 App 内文案，**不改历史台账**：
CHANGELOG 旧条目 / 证据核查 / 复盘 / HANDOFF / FeatureMap / 交付说明 /
迭代计划（需求原话）都保留原貌，按「结论变了别删旧版」的口径走。

用法：
  python tools/apply_copy_rules.py --dry     # 预览每处替换
  python tools/apply_copy_rules.py --apply   # 落盘
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# (文件, [(旧, 新), ...])，顺序敏感：长词必须先于短词
RULES: dict[str, list[tuple[str, str]]] = {
    # ---------- App 内用户可见文案 ----------
    "ZenTimer/app/src/main/java/com/yaohui/zentimer/Lang.kt": [
        ('"btn_start" to "开始冥想"', '"btn_start" to "开始静坐"'),
        ('"section_duration" to "时长"', '"section_duration" to "静坐时长"'),
        ('"section_interval" to "间隔铃"', '"section_interval" to "间隔铃长"'),
        ('"state_infinite" to "无时限 · 自由冥想"', '"state_infinite" to "无时限 · 自由静坐"'),
        ('"notif_body" to "冥想进行中 · "', '"notif_body" to "静坐中 · "'),
        ('"btn_start" to "開始冥想"', '"btn_start" to "開始靜坐"'),
        ('"section_duration" to "時長"', '"section_duration" to "靜坐時長"'),
        ('"section_interval" to "間隔鈴"', '"section_interval" to "間隔鈴長"'),
        ('"state_infinite" to "無時限 · 自由冥想"', '"state_infinite" to "無時限 · 自由靜坐"'),
        ('"notif_body" to "冥想進行中 · "', '"notif_body" to "靜坐中 · "'),
        ('"section_duration" to "Duration"', '"section_duration" to "Zen sitting time"'),
        # 这一行选的是「间隔多久」，不是选铃声，英文原译名也一并对齐
        ('"section_interval" to "Interval bell"', '"section_interval" to "Interval length"'),
        ('"state_infinite" to "Open-ended · free meditation"', '"state_infinite" to "Open-ended · Zen sitting"'),
        ('"notif_body" to "Meditating · "', '"notif_body" to "Zen sitting · "'),
    ],
    "ZenTimer/app/src/main/java/com/yaohui/zentimer/MainActivity.kt": [
        ("只写「开始冥想」会", "只写「开始静坐」会"),
    ],
    "ZenTimer/app/src/main/AndroidManifest.xml": [
        ("离线冥想闹钟", "离线静坐计时器"),
    ],
    # ---------- 当前对外说明 ----------
    "README.md": [
        ("极简禅意冥想计时器", "极简禅意静坐计时器"),
        ("Minimal Zen Meditation Timer", "Minimal Zen Sitting Timer"),
        ("冥想计时工具", "静坐计时工具"),
        ("Zen meditation timer", "Zen sitting timer"),
        ("冥想", "静坐"),
    ],
    "STORE-LISTING.md": [
        ("极简禅意冥想计时器", "极简禅意静坐计时器"),
        ("Minimal Zen Meditation Timer", "Minimal Zen Sitting Timer"),
        ("冥想课程", "静坐课程"),
        ("自由冥想", "自由静坐"),
        ("冥想计时 /", "静坐计时 /"),
        ("冥想计时播放", "静坐计时播放"),
        ("Zen meditation timer", "Zen sitting timer"),
        ("meditation timer", "Zen timer"),
        ("mindfulness", "Zen"),
        ("冥想", "静坐"),
    ],
    "PRIVACY-POLICY.md": [
        ("极简禅意冥想计时器", "极简禅意静坐计时器"),
        ("Zen meditation timer", "Zen sitting timer"),
        ("冥想计时播放", "静坐计时播放"),
        ("冥想", "静坐"),
    ],
    # ---------- 当前传播物料 ----------
    "video-release/发布配文.md": [
        ("冥想计时器", "静坐计时器"),
        ("市面上的冥想 App", "市面上的静坐 App"),
        ("#冥想", "#静坐"),
        ("冥想", "静坐"),
    ],
    "video-release/图片提示词-豆包.md": [
        ("像冥想时", "像静坐时"),
    ],
}

# 明确不动的地方：值本身是系统约定 / 历史证据
KEEP = [
    "AndroidManifest.xml 的 meditation_timer_playback（系统前台服务子类型标识，改了会影响声明）",
    "迭代计划.md 的需求原话（反馈台账，不改写）",
    "CHANGELOG / 证据核查 / 复盘 / HANDOFF / FeatureMap / 交付说明 的历史条目",
]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--dry", action="store_true")
    args = ap.parse_args()
    if not (args.apply or args.dry):
        args.dry = True

    total, missing = 0, 0
    for rel, pairs in RULES.items():
        p = ROOT / rel
        if not p.exists():
            print(f"!! 缺文件 {rel}")
            missing += 1
            continue
        text = p.read_text(encoding="utf-8")
        orig = text
        changed = []
        for old, new in pairs:
            n = text.count(old)
            if n == 0:
                # 已经替换过也算正常（脚本可重复跑），只是报告出来便于核对
                continue
            text = text.replace(old, new)
            changed.append(f"{old!r} ×{n}")
            total += n
        if changed and args.apply:
            p.write_text(text, encoding="utf-8")
        if changed:
            print(f"{'[APPLY]' if args.apply else '[DRY]  '} {rel}: {', '.join(changed)}")
        elif text != orig:
            print(f"!! {rel}: 有变化但没记录到规则")
            missing += 1
    print(f"\n共 {total} 处替换；{'已落盘' if args.apply else '仅预览（加 --apply 生效）'}")
    print("刻意保留：")
    for k in KEEP:
        print("  ·", k)
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
