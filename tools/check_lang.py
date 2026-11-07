#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""三语文案对账：简 / 繁 / 英 key 必须一一对应，且代码里引用的 key 都有定义。

用法：python tools/check_lang.py
任何一条不一致都以 exit 1 结束，方便放进验收清单。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "StillTimer" / "app" / "src" / "main" / "java" / "com" / "stilltimer" / "personal"
lang = (ROOT / "Lang.kt").read_text(encoding="utf-8")


def keys_of(name: str) -> set:
    m = re.search(r"private val " + name + r" = mapOf\((.*?)\n    \)", lang, re.S)
    if not m:
        raise SystemExit(f"!! 找不到文案表 {name}")
    return set(re.findall(r'^\s*"([a-z_0-9]+)"\s+to\s+', m.group(1), re.M))


hans = keys_of("HANS") | keys_of("EXTRA_HANS")
hant = keys_of("HANT") | keys_of("EXTRA_HANT")
en = keys_of("EN") | keys_of("EXTRA_EN")

print(f"简体 {len(hans)} 条 / 繁体 {len(hant)} 条 / English {len(en)} 条")
ok = True
for label, other in (("繁体", hant), ("English", en)):
    missing = sorted(hans - other)
    extra = sorted(other - hans)
    if missing or extra:
        ok = False
        print(f"!! {label} 缺 {missing} 多 {extra}")

# 代码里所有字面引用（含动态 state key / picker key 的字面量）
refs = set()
for f in sorted(ROOT.glob("*.kt")):
    t = f.read_text(encoding="utf-8")
    refs |= set(re.findall(r'Lang\.t\("([a-z_0-9]+)"\)', t))
    refs |= set(re.findall(r'"(state_[a-z_0-9]+)"', t))
    refs |= set(re.findall(r'"(picker_[a-z_0-9]+)"', t))

missing_refs = sorted(refs - hans)
print(f"代码引用 {len(refs)} 个 key，未定义 {len(missing_refs)} 个")
if missing_refs:
    ok = False
    print("!! 未定义：", missing_refs)

print("OK · 三语 key 零差异，引用全部有定义" if ok else "FAIL · 请补齐上列差异")
sys.exit(0 if ok else 1)
