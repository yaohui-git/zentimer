#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""UI 文案与工程口径的批量检查（可复跑，适合挂到每次出包前）。

跑法：python tools/check_ui_copy.py
产出：UI文案检查报告.md；有硬错误时 exit 1。

和 tools/check_lang.py 分工：那个只做三语 key 对账，这个扫术语、引用、
硬编码、默认值漂移、资源缺失、版本口径、发布文件名等一整圈。
"""
from __future__ import annotations

import re
import sys
from collections import Counter
from pathlib import Path

from export_ui_copy import ROOT, SRC, count_refs, parse_lang
from check_evidence_invariants import run as run_evidence_invariants

REPORT = ROOT / "UI文案检查报告.md"
RES = ROOT / "ZenTimer" / "app" / "src" / "main" / "res"
# v2.33（用户 11-12 09:58 授权）：meditation 词族不再列为禁用词。
# 11-09 那轮的「禁用词」不是长期铁律，用户原话：「之前的未必是规则，也许只是一次
# 需求更新而已（暂时的，一次性的）」。佛光山 / 法鼓山公开英文材料用 sitting meditation，
# 本轮英文文案按该路线统一。中文「冥想」与 mindfulness（另一个概念）仍保持原约束。
BANNED = ("冥想", "mindfulness")
SIMPLE_IN_HANT = set("静时铃练习准备开关连续网际资讯开启关闭声钟点后里几为与从众个门间问闻")
# 历史台账 / 需求原话：按「结论变了别删旧版」不参与术语扫描
HISTORY = {
    "CHANGELOG.md", "证据核查-2026-11-08.md", "复盘-REA与需求补位.md",
    "HANDOFF-通知与锁屏.md", "FeatureMap-TestCase-整体设计.md",
    "交付说明.md", "迭代计划.md", "豆包Demo评估-2026-11-09.md",
}
VISIBLE_DOCS = ["README.md", "STORE-LISTING.md", "PRIVACY-POLICY.md",
                "video-release/发布配文.md", "video-release/图片提示词-豆包.md"]
OPS = []       # (级别, 检查项, 结论)
HARD_FAIL = 0


def add(level: str, item: str, msg: str):
    global HARD_FAIL
    OPS.append((level, item, msg))
    if level == "FAIL":
        HARD_FAIL += 1


def strip_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return "\n".join(line.split("//")[0] for line in text.splitlines())


def check_terms():
    # App 内三语文案
    tables, _ = parse_lang()
    hits = []
    for lang, d in tables.items():
        for k, (v, ln) in d.items():
            if any(b in v for b in BANNED):
                hits.append(f"Lang.kt:{ln} {lang}/{k} = {v}")
    # 当前对外文档
    for rel in VISIBLE_DOCS:
        p = ROOT / rel
        if not p.exists():
            continue
        for i, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
            if any(b in line for b in BANNED):
                hits.append(f"{rel}:{i} {line.strip()[:70]}")
    # 系统标识与历史裁决注释：显式豁免
    exempt = [
        "AndroidManifest.xml: meditation_timer_playback（前台服务子类型，系统约定）",
        "NotificationContract.kt:204: 07:54 历史裁决原话（不改写证据）",
    ]
    if hits:
        add("FAIL", "禁用术语", "；".join(hits))
    else:
        add("PASS", "禁用术语", f"App 可见文案 + {len(VISIBLE_DOCS)} 份对外文档零命中；豁免 {len(exempt)} 处：" + "、".join(exempt))


def check_keys():
    tables, _ = parse_lang()
    sizes = {k: len(v) for k, v in tables.items()}
    if len(set(sizes.values())) != 1:
        add("FAIL", "三语 key 对齐", str(sizes))
    else:
        add("PASS", "三语 key 对齐", f"简 / 繁 / 英各 {sizes.get('hans')} 条，零差异")
    refs = count_refs()
    defined = set(tables.get("hans", {}))
    undefined = sorted(k for k in refs if k not in defined)
    if undefined:
        add("FAIL", "引用未定义", str(undefined))
    else:
        add("PASS", "引用未定义", f"静态扫到 {len(refs)} 个 key 全部有定义")
    unused = sorted(k for k in defined if refs[k] == 0)
    add("WARN", "静态未见引用", f"{len(unused)} 条（可能由变量拼接，需人工确认）：" + "、".join(unused))


def check_hardcoded():
    allowed = {"Lang.kt", "SoundCatalog.kt", "AppInfo.kt"}
    zh = re.compile(r"[\u4e00-\u9fff]")
    strpat = re.compile(r'"((?:[^"\\]|\\.)*)"')
    hits = []
    for f in sorted(SRC.glob("*.kt")):
        if f.name in allowed:
            continue
        for i, line in enumerate(strip_comments(f.read_text(encoding="utf-8")).splitlines(), 1):
            for m in strpat.finditer(line):
                if zh.search(m.group(1)):
                    hits.append(f"{f.name}:{i} {m.group(1)[:40]}")
    if hits:
        add("WARN", "硬编码中文", f"{len(hits)} 处（应迁到 Lang.kt）：" + "；".join(hits[:8]))
    else:
        add("PASS", "硬编码中文", "Lang.kt / SoundCatalog.kt / AppInfo.kt 之外零硬编码中文")


def check_quality():
    tables, _ = parse_lang()
    hans, hant, en = (tables.get(k, {}) for k in ("hans", "hant", "en"))
    bad_simp, bad_en, long_rows = [], [], []
    for k, (v, ln) in hant.items():
        found = sorted({c for c in v if c in SIMPLE_IN_HANT})
        if found:
            bad_simp.append(f"{k}:{''.join(found)}")
    # 语言选择器里「简体中文 / 繁體中文」是各语言自称，必须保留汉字
    self_names = {"lang_hans", "lang_hant"}
    for k, (v, ln) in en.items():
        if k not in self_names and re.search(r"[\u4e00-\u9fff]", v):
            bad_en.append(k)
    for k, (v, ln) in hans.items():
        if len(v) > 24:
            long_rows.append(f"{k}({len(v)}字)")
    add("FAIL" if bad_simp else "PASS", "繁体混简体", "；".join(bad_simp) if bad_simp else "零命中")
    add("FAIL" if bad_en else "PASS", "英文混汉字",
        "、".join(bad_en) if bad_en else "零命中（lang_hans / lang_hant 是语言自称，豁免）")
    add("WARN", "窄屏长文案", f"{len(long_rows)} 条超过 24 字，需看换行：" + "、".join(long_rows[:10]))


def check_defaults():
    eng = (SRC / "TimerEngine.kt").read_text(encoding="utf-8")
    pre = (SRC / "Presets.kt").read_text(encoding="utf-8")
    defaults = dict(re.findall(r"const val (\w+) = (\S+)", eng.split("object Defaults {")[1].split("}")[0]))
    wanted = {
        "DURATION_MIN": "15", "PREP_SEC": "180", "START_BELL": '"b_wood"',
        "INTERVAL_BELL": '"none"', "END_BELL": '"b_kangse"', "START_TIMES": "3",
    }
    wrong = [f"{k}={defaults.get(k)}（要 {v}）" for k, v in wanted.items() if defaults.get(k) != v]
    if wrong:
        add("FAIL", "默认值口径", "；".join(wrong))
    else:
        add("PASS", "默认值口径", "六项与 11-10 17:4x 拍板逐项一致：" + "，".join(f"{k}={v}" for k, v in wanted.items()))
    # 回落值必须都读 Defaults，不许再写死数字
    drift = []
    for name, body in (("TimerEngine.readSettings", eng.split("private fun readSettings()")[1].split("private fun persist")[0]),
                       ("Preset.fromJson", pre.split("fun fromJson")[1])):
        for bad in ('"b_basu"', '"b_gong"', '"b_wood"', "optInt(\"durationMin\", 10)", "optInt(\"prepSec\", 10)"):
            if bad in body:
                drift.append(f"{name}: {bad}")
    add("FAIL" if drift else "PASS", "默认值单一事实源", "；".join(drift) if drift else "两处回落全部引用 TimerEngine.Defaults，零漂移")
    # 「无」铃声必须 res=0 才会真的不响
    cat = (SRC / "SoundCatalog.kt").read_text(encoding="utf-8")
    has_none = re.search(r'Sound\("none",\s*0,', cat) is not None
    guard = "if (resId == 0) return" in (SRC / "Audio.kt").read_text(encoding="utf-8")
    add("PASS" if (has_none and guard) else "FAIL", "「无」铃声真的不响",
        "res=0 + BellPlayer.play 早退" if (has_none and guard) else "缺 res=0 条目或播放端早退")


def check_resources():
    have = set()
    for sub in ("drawable", "drawable-nodpi", "raw", "mipmap-hdpi"):
        for p in (RES / sub).glob("*"):
            have.add(p.stem)
    cat = (SRC / "SoundCatalog.kt").read_text(encoding="utf-8")
    need = set(re.findall(r"R\.(?:raw|drawable)\.(\w+)", cat))
    missing = sorted(need - have)
    add("FAIL" if missing else "PASS", "素材资源引用", f"{len(need)} 个引用，缺 {len(missing)} 个" + ("：" + "、".join(missing) if missing else ""))


def check_version():
    g = (ROOT / "ZenTimer" / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    code = re.search(r"versionCode = (\d+)", g)
    name = re.search(r'versionName = "([\d.]+)"', g)
    ver = name.group(1) if name else "?"
    change = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    logged = f"v{ver}" in change or ver in change
    add("PASS" if logged else "WARN", "版本口径",
        f"versionName {ver} / versionCode {code.group(1) if code else '?'}；CHANGELOG {'有' if logged else '未'}登记")
    # 发布文件名必须纯 ASCII
    bad = [p.name for p in (ROOT / "releases").glob("*") if p.is_file() and not p.name.isascii()]
    add("PASS" if not bad else "FAIL", "发布文件名", "releases/ 全为 ASCII" if not bad else "中文名：" + "、".join(bad))


def check_a11y():
    main = strip_comments((SRC / "MainActivity.kt").read_text(encoding="utf-8"))
    null_desc = len(re.findall(r"contentDescription\s*=\s*null", main))
    labels = len(re.findall(r"onClickLabel\s*=", main))
    add("WARN", "无障碍描述", f"contentDescription=null {null_desc} 处（装饰图可以，按钮不行）；onClickLabel {labels} 处，建议逐个人工过一遍")


NEED_HUMAN = [
    "窄屏 / 系统字体放大到 1.3 倍后，按钮与设置行是否截断（需真机截图矩阵）",
    "三语各截一屏比对：简繁字形、英文断行、日期格式",
    "深浅两套主题的文字对比度（WCAG AA 4.5:1），尤其 accentDim 小字",
    "TalkBack 朗读顺序与 onClickLabel 是否自然",
    "通知四表面（顶栏胶囊 / 下拉卡片 / 锁屏 / AOD 充电）实拍基线比对",
    "覆盖安装实测：旧偏好已改过的用户不被迁移覆盖，没改过的升级到新默认",
    "切换语言 / 主题不打断正在进行的练习",
    "商店文案是否含疗效、医疗暗示等敏感表述",
]
# 11-10 校正：原「还能自动化」前三条在 v2.19 已落地（check_screen_copy.py /
# apply_ui_review.py / check_evidence_invariants.py），不再当待办挂在报告里；
# 原 NEED_HUMAN 的「设置页猛滑抖动」是 v2.18 的错误归因，抖动真身是声音选择弹层，
# 已由 tools/test_sheet_jitter.py 做成可复跑 A/B 闸门，整条撤掉。
CAN_AUTOMATE_NEXT = [
    "string resource 化：本轮主动不做，理由与触发条件见 UI文案复核决定.md 第七节",
    "版本号 / CHANGELOG / README / Release Notes 四处自动同步（现在靠人）",
    "第三方素材清单与 NOTICE.md 自动对账，防漏标授权",
]


def check_support_author():
    """11-10 裁决的不变量：支持作者只复制纯 ID，不唤起微信、不放二维码、不删合规句。

    这条是「文档里的现状描述错了，容易照着改回去」的防复发闸门——上一轮 AOD 冒数字
    就是台账写了不变量、代码没闸门，成本最低的做法是把它写成静态断言。
    """
    kotlin = ROOT / "ZenTimer" / "app" / "src" / "main" / "java" / "com" / "yaohui" / "zentimer"
    kt = "\n".join(p.read_text(encoding="utf-8") for p in sorted(kotlin.glob("*.kt")))
    lang = (kotlin / "Lang.kt").read_text(encoding="utf-8")
    manifest = (ROOT / "ZenTimer" / "app" / "src" / "main" / "AndroidManifest.xml").read_text(encoding="utf-8")

    # ① 不碰微信私有跳转：无唤起、无 scheme、无为检测微信而存在的包可见性声明
    bad = [k for k in ("openWechat", "getLaunchIntentForPackage", "weixin://", "com.tencent.mm")
           if k in kt or k in manifest]
    add("PASS" if not bad else "FAIL", "支持作者 · 不碰微信跳转",
        "零唤起 / 零 scheme / 零包声明" if not bad else "残留：" + "、".join(bad))

    # ② 复制的必须是那串不可变串本身，不带任何前缀（带前缀粘进微信搜索就搜不到人）
    pure = ("AppInfo.copy(ctx, AppInfo.WECHAT)" in kt
            and 'const val WECHAT = "pingyishufang"' in kt)
    prefixed = bool(re.search(r'["\']微信号[:：]', kt))
    add("PASS" if pure and not prefixed else "FAIL", "支持作者 · 复制纯 ID",
        "整行复制 pingyishufang，无前缀" if pure and not prefixed
        else f"copy 命中={pure}；带前缀={prefixed}")

    # ③ v2.27 口径（用户 11-11 定）：关于页恢复 v2.25 的打赏说法，三语正文都要带
    #    「完全自愿」的免责句；v2.26 那套「捐助 / Donate」措辞必须清零，避免两套并存。
    hints = re.findall(r'"donate_hint" to "([^"]+)"', lang)
    bodies = re.findall(r'"donate_body" to "([^"]+)"', lang)
    ok_hint = len(hints) == 3 and all("+" in h for h in hints)
    ok_body = len(bodies) == 3 and all(
        ("完全自愿" in b or "完全自願" in b or "entirely optional" in b.lower())
        for b in bodies)
    legacy = [w for w in ("捐助", "Donate") if w in lang]
    add("PASS" if ok_hint else "FAIL", "支持作者 · 下一步三语写死",
        f"{len(hints)} 语：{' / '.join(hints)}")
    add("PASS" if ok_body and not legacy else "FAIL", "打赏 · 免责句三语齐备、旧说法清零",
        f"{len(bodies)} 语均含免责句" if ok_body and not legacy
        else f"免责句 {len(bodies)} 语；残留旧说法={legacy}")


def check_duration_field():
    """11-10：把《证据核查-时长控件与打赏》§4 的不变量写成静态断言。

    防三类复发：① 「减法」把标题也减没了，用户 07:1x 纠偏「重点是加微信而不是打赏」；
    ② 时长控件又分叉成两套（主屏一套、预设一套），正是 11-09 台账反复踩的坑；
    ③ v2.22 新增：手感与字号。系统控件不许再被架构洁癖否决，自绘手势不许回来，
    新控件不许越过既有字号阶梯，编辑期不许有第二个手势接收者。
    """
    kotlin = ROOT / "ZenTimer" / "app" / "src" / "main" / "java" / "com" / "yaohui" / "zentimer"
    java = {p.name: p.read_text(encoding="utf-8") for p in kotlin.glob("*.kt")}
    allkt = "\n".join(java.values())
    main = java.get("MainActivity.kt", "")
    field = java.get("DurationField.kt", "")
    lang = java.get("Lang.kt", "")

    # ① v2.27 口径（用户 11-11 定）：三语标题回到「打赏作者 / 贊助作者 / Tip the author」，
    #    捐助区仍是带边框的区域框，布局不许因为改文案而散架
    titles = re.findall(r'"donate_title" to "([^"]+)"', lang)
    readable = len(titles) == 3 and all(
        any(k in t for k in ("打赏", "贊助", "Tip the author")) for t in titles)
    pos = main.find('Lang.t("donate_title")')
    carded = pos >= 0 and "Card(" in main[max(0, pos - 900):pos]
    add("PASS" if readable and carded else "FAIL", "打赏 · 标题一眼看懂 + 区域框",
        f"三语标题 {' / '.join(titles)}；带边框卡片={carded}")

    # ② 一个组件三处复用，旧固定档位控件零残留
    #    v2.31：三处 = 提醒间隔 / 准备时间 / 圆环下沿的临时编辑层。静坐时长那行已从
    #    设置卡移除；提示回到圆环内常驻，档位层由 AnimatedVisibility 控制、只在编辑时
    #    出现，展开时下方整体让位（不再是 2.30 那块常驻「零位移」槽位）。
    defs = field.count("fun DurationSetting(")
    calls = len(re.findall(r"\bDurationSetting\(", main))
    no_old = "OptionRow(" not in allkt
    layer_only = ("DurationEditLayer(" in main
                  and "AnimatedVisibility(" in main
                  and "chrome = false" in main
                  and "DurationFootSlot(" not in main
                  and 'onEditingChange("duration", it)' not in main)
    hint_in_ring = ('Lang.t("hint_tap_ring")' in main
                    and "if (hint.isNotEmpty()) {" in main
                    and 'if (hint.isNotEmpty() && s.phase != TimerEngine.Phase.IDLE)' not in main)
    add("PASS" if defs == 1 and calls == 3 and no_old and layer_only and hint_in_ring else "FAIL",
        "时长 · 一组件三处复用 / 提示回圈内 / 档位只在编辑层",
        f"定义 {defs} 处 / 调用 {calls} 处 / 旧档位残留={not no_old} / 编辑层唯一入口={layer_only} / 提示在圈内={hint_in_ring}")

    # --- v2.33 视觉可供性三条硬断言（真机取证见 design/视觉可供性审计-2026-11-12.md） ---
    # ① 关了手势的铃声弹层不许再画 drag handle：M3 默认会画粗横线并给读屏报
    #    「拖动手柄」，10X 实测下滑 1100px 弹层纹丝不动 —— 视觉与无障碍双重误导。
    gi = main.find("sheetGesturesEnabled = false")
    near = main[max(0, gi - 200): gi + 400] if gi >= 0 else ""
    no_fake_handle = (gi >= 0 and "dragHandle = { }" in near
                      and main.count("dragHandle = { }") == 1)
    add("PASS" if no_fake_handle else "FAIL",
        "铃声弹层 · 关手势就撤掉假拖动手柄",
        f"手势关闭={gi >= 0}；同一弹层内空 handle={no_fake_handle}")

    # ② 档位层展开后圆环已变成 toggle，圈内提示不能还说「轻触改时长」。
    hint_close = ('Lang.t("hint_tap_ring_close")' in main
                  and "editing && s.phase == TimerEngine.Phase.IDLE" in main)
    add("PASS" if hint_close else "FAIL",
        "圆环 · 编辑态提示与 toggle 动作一致",
        f"编辑态改说收回={hint_close}")

    # ③ 三行设置卡贴左：旧版为对齐展开态图标列留的 50dp 空位，在默认收起态
    #    只是一块怪异留白。展开态由子行图标形成缩进层级，不再强行七行同竖线。
    no_50 = ("Spacer(Modifier.width(50.dp))" not in main
             and "Spacer(Modifier.width(50.dp))" not in field)
    add("PASS" if no_50 else "FAIL",
        "设置卡 · 三行贴左，收起态不留空图标列",
        f"主屏/时长组件均无 50dp 占位={no_50}")

    # ③ 系统形态的「必须不出现」 + v2.22 反转后的「必须出现」
    # v2.21 这里断言「不许出现 NumberPicker / AndroidView」。11-10 用户实测手感后重新
    # 评估，SDK api-versions.xml 证明那三条装饰 API 只是 API 29+、真机 API 31 全可用，
    # 于是断言反过来写：必须用原生滚轮，且必须把它收住（无分隔线 / 有手势归属）。
    # v2.24：「不循环」这条也反转——上一轮是没测边界就下的结论，真机截图证明系统循环。
    hour_branch = field.split("DurationMode.HOUR_MINUTE -> listOf(", 1)[-1] \
                       .split("DurationMode.MINUTE_SECOND ->", 1)[0]
    no_sec = "unit_sec_short" not in hour_branch
    # 剥掉注释再判：DurationField.kt 的文件头正好写了 v2.21 那段旧取舍说明，
    # 直接搜全文件会把自己写的注释当成真代码，跟 11-10 那次 param_island 误报同源。
    code_only = re.sub(r"/\*.*?\*/", "", allkt, flags=re.S)
    code_only = "\n".join(re.sub(r"//.*$", "", ln) for ln in code_only.splitlines())
    field_code = re.sub(r"/\*.*?\*/", "", field, flags=re.S)
    field_code = "\n".join(re.sub(r"//.*$", "", ln) for ln in field_code.splitlines())
    main_code = re.sub(r"/\*.*?\*/", "", main, flags=re.S)
    main_code = "\n".join(re.sub(r"//.*$", "", ln) for ln in main_code.splitlines())
    eng_code = re.sub(r"/\*.*?\*/", "", java.get("TimerEngine.kt", ""), flags=re.S)
    eng_code = "\n".join(re.sub(r"//.*$", "", ln) for ln in eng_code.splitlines())
    native = "NumberPicker(" in code_only and "AndroidView(" in code_only
    tamed = all(k in field_code for k in (
        "wrapSelectorWheel = true", "setSelectionDividerHeight(0)",
        "Build.VERSION.SDK_INT >= 29", "requestDisallowInterceptTouchEvent",
        "FOCUS_BLOCK_DESCENDANTS"))
    # 自绘手势必须彻底退场：逐格累加 / 手写 pointerInput 是这次手感事故的根因。
    no_handrolled = not any(k in field_code for k in (
        "detectVerticalDragGestures", "dispatchRawDelta", "pointerInput", "38.dp"))
    no_coach = not any(k in code_only for k in ("Tutorial", "Onboarding", "coachMark"))
    ok3 = no_sec and native and tamed and no_handrolled and no_coach
    add("PASS" if ok3 else "FAIL", "时长 · 原生滚轮受控 / 无秒列 / 无自绘手势",
        f"无秒列={no_sec}；原生滚轮={native}；装饰与手势收口={tamed}；"
        f"自绘手势清零={no_handrolled}；无教程遮罩={no_coach}")

    # ③b v2.23 反转：冻结滚动被用户定性为 BUG（展开「准备时间」时取消/确定被顶出
    # 屏幕且页面滚不动）。现在要求整页可滚恢复、点空白即取消、编辑目标全局互斥、
    # 展开自动 bringIntoView；滚轮仍自己声明手势归属，不再靠关掉页面滚动躲冲突。
    frozen_gone = "enabled = !durationEditing" not in main_code
    scrollable = "verticalScroll(pageScroll" in main_code
    # 点空白取消必须带「滚动位置没变」的判据。真机 11-10 20:1x 实测：只写
    # onTap = { editingTarget = null } 时，编辑态里向上滑 480px 会被同一次手势
    # 当成点击，编辑态当场收起——v2.21 就因这个删过功能，不能靠删回避。
    tap_outside = ("tapDownScroll = pageScroll.value" in main_code
                   and "pageScroll.value == tapDownScroll" in main_code)
    exclusive = all(f'editingTarget == "{k}"' in main_code for k in ("duration", "interval", "prep"))
    bring = "bringIntoViewRequester" in field_code and "bringIntoView.bringIntoView(" in field_code
    # 自动滚动必须带底部余量：只把编辑区本身滚进视口时，「取消/确定」会正好
    # 贴在视口底边，真机实测只剩 30px 可见高度（按钮一半被裁）。
    bring_margin = "editorH.toFloat() + 88f" in field_code and "onSizeChanged" in field_code
    handoff = "requestDisallowInterceptTouchEvent" in field_code
    ok_b = frozen_gone and scrollable and tap_outside and exclusive and bring and bring_margin and handoff
    add("PASS" if ok_b else "FAIL",
        "时长 · 可滚/互斥/点空白取消/自动可见/手势归属",
        f"冻结已撤={frozen_gone}；整页可滚={scrollable}；点空白取消(带滚动判据)={tap_outside}；"
        f"三处互斥={exclusive}；自动可见={bring}；滚动留余量={bring_margin}；滚轮独占={handoff}")

    # ③c 字号一致性：新控件不得越过既有字号阶梯（用户反馈 34）
    field_fonts = re.findall(r"fontSize = (\d+)\.sp", field_code)
    no_oversize = all(int(n) <= 15 for n in field_fonts)
    aligned = "fontSize = 15.sp" in field_code and "fontSize = 12.sp" in field_code
    no_mono = "FontFamily.Monospace" not in field_code
    # v2.33 反转：旧版为对齐展开态图标列留 50dp 空位，默认收起态只剩一块怪异留白
    # （用户 10:03 反馈）。现三行统一贴左，展开态由子行图标形成缩进层级。
    # 字号阶梯/15-12sp/禁等宽大数字三条仍是硬约束，一条没松。
    left_flush = field_code.count("Spacer(Modifier.width(50.dp))") == 0
    add("PASS" if no_oversize and aligned and no_mono and left_flush else "FAIL",
        "时长 · 字号阶梯与三行贴左",
        f"字号={sorted(set(field_fonts))}；15/12sp 齐备={aligned}；"
        f"等宽大数字清零={no_mono}；三行贴左={left_flush}")

    # ④ 就地编辑 + 取消/返回回滚 + 只有「确定」写回
    # v2.24：写回统一走 commit()（固定档即点即写、自定义走确定），但只有这两个
    # 调用点允许触发 onCommit，取消 / 返回 / 点空白一律只回滚。
    rollback = all(k in field for k in (
        "BackHandler(enabled = editing)", "fun cancel()", "onCommit(v)",
        "{ commit(draft) }", "{ commit(choice.value) }"))
    no_dialog = "ModalBottomSheet" not in field and "AlertDialog" not in field
    add("PASS" if rollback and no_dialog else "FAIL", "时长 · 就地编辑与回滚",
        f"返回/取消/确定齐备={rollback}；不弹面板={no_dialog}")

    # ⑤ 运行中不可编辑；出厂默认没被设计文档的「15 分钟」带偏
    guarded = len(re.findall(r"enabled = !s\.running", main))
    defaults = all(k in allkt for k in (
        "const val DURATION_MIN = 15", "const val PREP_SEC = 180", 'END_BELL = "b_kangse"'))
    add("PASS" if guarded >= 3 and defaults else "FAIL", "时长 · 运行禁编辑 + 默认未漂移",
        f"三处 enabled={guarded}；15 分 / 3 分 / Kangse={defaults}")

    # ⑦ 用户 11-10 拍板「连无限计时一起删」：入口、内部 -1、三语文案、计时/通知/
    # 结算分支全部清零；旧设置里残留的 -1 必须安全归一为出厂默认，不能算成负数圆环。
    no_infinite = not any(k in code_only for k in (
        '"infinite"', "state_infinite", "duration_finite", "s.infinite", "durationMin < 0"))
    legacy_safe = "if (min < 1) Defaults.DURATION_MIN" in eng_code
    add("PASS" if no_infinite and legacy_safe else "FAIL",
        "时长 · 无时限清零 + 旧 -1 安全归一",
        f"源码无无限态={no_infinite}；旧 -1 归一到出厂={legacy_safe}")

    # ⑧ 圆环点击改为展开静坐时长，但计时中仍走暂停/继续、沉浸式全屏不受影响。
    #    v2.32：再点圆环即收回档位。写法从单行赋值变成 toggle，断言同步改认
    #    「入口 + 展开分支 + 收起分支 + 成对清 wheelShowing」，比旧串匹配更严：
    #    收起时不清子状态，下一次展开会直接跳到 252dp 滚轮层（已取证的真问题）。
    ring_anchor = main_code.find("onEditDuration = {")
    ring_block = main_code[ring_anchor:ring_anchor + 900] if ring_anchor >= 0 else ""
    ring_edit = ("onEditDuration" in main_code
                 and ring_anchor >= 0
                 and 'editingTarget == "duration"' in ring_block
                 and 'editingTarget = "duration"' in ring_block
                 and "editingTarget = null" in ring_block
                 and "wheelShowing = false" in ring_block)
    ring_running = "TimerEngine.mainAction()" in main_code
    hint_ok = all(v in lang for v in ("轻触圆环改时长", "輕觸圓環改時長", "Tap the ring to set the time"))
    add("PASS" if ring_edit and ring_running and hint_ok else "FAIL",
        "圆环 · 静止态改时长 / 再点收回 / 计时中仍是主动作",
        f"圆环接编辑与收回={ring_edit}；计时中主动作保留={ring_running}；三语提示同步={hint_ok}")

    # ⑥ 一次性提示确实会消失，且提示文案只在一处、不常驻
    hint = "KEY_DURATION_HINT" in field and "putBoolean(KEY_DURATION_HINT, true)" in field
    hint_n = allkt.count('Lang.t("duration_tap_hint")')
    add("PASS" if hint and hint_n == 1 else "FAIL", "时长 · 一次性提示会消失",
        f"flag 持久化={hint}；提示文案出现 {hint_n} 处")

    # ⑨ v2.24 四项拍板（11-10 21:27 用户逐项点选）
    # ① 循环 + 尺寸：真机 `timer_tab.png` 秒列 58/59/00/01/02、时列 22/23/00/01/02
    #    坐实系统循环；控件树三列各 332×901px、5 行、铺满屏宽。上一轮「不循环 /
    #    96dp×120dp / 列间留 20dp」三条一起撤，别让任何一条悄悄回来。
    v224_wrap = ("wrapSelectorWheel = true" in field_code
                 and "wrapSelectorWheel = false" not in field_code)
    v224_size = ".height(200.dp)" in field_code and "Modifier.weight(1f)" in field_code
    v224_nogap = "Spacer(Modifier.width(20.dp))" not in field_code
    add("PASS" if v224_wrap and v224_size and v224_nogap else "FAIL",
        "v2.24 · 滚轮循环 / 5 行可见 / 铺满命中区",
        f"循环={v224_wrap}；200dp + weight={v224_size}；列间空白已撤={v224_nogap}")

    # ② 23 小时：上限只认 Defaults.DURATION_MAX_MIN 一个数字；599 全清零；
    #    formatTime 必须有 H:MM:SS 分支，圆环对 8 字符倒计时要降字号。
    pre_code = re.sub(r"//.*$", "", java.get("Presets.kt", ""), flags=re.M)
    v224_max = ("const val DURATION_MAX_MIN = 1439" in eng_code
                and "coerceIn(1, Defaults.DURATION_MAX_MIN)" in eng_code
                and "0..23" in field_code
                and "1..TimerEngine.Defaults.DURATION_MAX_MIN" in field_code
                and "599" not in field_code and "599" not in eng_code
                and "599" not in pre_code)
    v224_fmt = ('"%d:%02d:%02d".format(h, m, sec)' in eng_code
                and "totalSec / 3600L" in eng_code)
    v224_ring = "timeText.count { it == ':' } > 1" in main_code
    add("PASS" if v224_max and v224_fmt and v224_ring else "FAIL",
        "v2.24 · 23:59 上限 / 时:分:秒 / 圆环不撑破",
        f"上限单一事实源={v224_max}；倒计时格式={v224_fmt}；圆环自适应字号={v224_ring}")

    # ③ 两组四档 + 自定义：档位值不许漂；提醒间隔不许被塞档位；必须先出档位、
    #    点自定义才进滚轮；「自定义」三语齐备，上次自定义值按两处分别持久化。
    v224_chips = all(k in field_code for k in (
        "listOf(15, 30, 45, 60)", "listOf(1, 3, 5, 10)",
        "DurationMode.MINUTES_ONLY -> emptyList()", 'Lang.t("duration_custom")',
        "v224_custom_duration_min", "v224_custom_prep_sec"))
    v224_stage = ("val showWheel = editing && (presets.isEmpty() || wheelRequested)" in field_code
                  and "wheelRequested = true" in field_code
                  and "draft = startCustom()" in field_code)
    v224_tri = all(v in lang for v in (
        '"duration_custom" to "自定义"', '"duration_custom" to "自訂"',
        '"duration_custom" to "Custom"'))
    # 键名必须带版本号：老包遗留的同名 pref 会让「自定义」一进去就跳到陈年旧值。
    v224_fresh = ("\"custom_duration_min\"" not in field_code
                  and "\"custom_prep_sec\"" not in field_code)
    add("PASS" if v224_chips and v224_stage and v224_tri and v224_fresh else "FAIL",
        "v2.24 · 两组四档 / 自定义才展开滚轮 / 三语齐备",
        f"档位值与范围={v224_chips}；两级入口={v224_stage}；三语自定义={v224_tri}；"
        f"记忆键带版本号={v224_fresh}")

    # ④ 23 小时下「再来一次」不能写成「1439 分钟」
    v224_long = "if (n > 120) return when (_mode.value)" in lang
    add("PASS" if v224_long else "FAIL", "v2.24 · 长时长的再练一次文案",
        f"Lang.minutes 有小时分支={v224_long}")

    # ⑤ 档位层根本没有「确定」，圆环状态行不能再写「调好后点确定」。
    #    真机探针 P2_chips 当场抓到这处自造文案缺陷，故写成断言防复发。
    v224_stageword = ("wheelShowing = wheelShowing" in main_code
                      and "editing && wheelShowing" in main_code
                      and "onStageChange(editing && showWheel)" in field_code
                      and "onStageChange = onStageChange" in main_code)
    add("PASS" if v224_stageword else "FAIL", "v2.24 · 圆环状态词只跟滚轮层绑定",
        f"层级如实上报且状态词带层级判据={v224_stageword}")


def check_v225_features():
    """v2.25：四时 + 静坐时免打扰的不变量闸门。

    最要紧的是用户 11-10 23:20 那条底线「程序退出后系统配置必须复原」。
    把它拆成可静态验证的几件事：禁用改全局状态的 API、自有规则单条复用、
    四条停用路径齐备、结束铃先起响再停用、启动/重启/闹钟三道兜底各就各位。
    """
    kotlin = ROOT / "ZenTimer" / "app" / "src" / "main" / "java" / "com" / "yaohui" / "zentimer"
    java = {p.name: p.read_text(encoding="utf-8") for p in kotlin.glob("*.kt")}
    manifest = (ROOT / "ZenTimer" / "app" / "src" / "main" / "AndroidManifest.xml").read_text(encoding="utf-8")

    def code(name: str) -> str:
        # 注释里会写到被禁用的方法名做说明，直接搜全文必然误报，先剥注释。
        t = re.sub(r"/\*.*?\*/", "", java.get(name, ""), flags=re.S)
        return "\n".join(re.sub(r"//.*$", "", ln) for ln in t.splitlines())

    all_code = "\n".join(code(n) for n in java)
    dnd, season, app, recv = (code(n) for n in
                              ("DndGuard.kt", "Season.kt", "ZenApp.kt", "DndRestoreReceiver.kt"))
    eng, main, lang = (java.get(n, "") for n in
                       ("TimerEngine.kt", "MainActivity.kt", "Lang.kt"))
    eng_code, main_code = code("TimerEngine.kt"), code("MainActivity.kt")

    # ① 底线之一：绝不改设备全局勿扰状态。Android 15 起这两个调用还会退化成隐式规则。
    banned = [k for k in ("setInterruptionFilter(", "setNotificationPolicy(") if k in all_code]
    add("PASS" if not banned else "FAIL", "v2.25 · 禁用全局勿扰 API",
        "全源码零调用（含注释剥离后）" if not banned else "残留：" + "、".join(banned))

    # ② 自有规则：单条复用、激活/停用都走 Condition，例外清单不自伤。
    rule = all(k in dnd for k in (
        "addAutomaticZenRule(", "setAutomaticZenRuleState(", "getAutomaticZenRules()",
        "Condition.STATE_TRUE", "Condition.STATE_FALSE", "containsKey(saved)",
        # 真机 11-11 00:12：不绑配置 Activity 会抛
        # IllegalArgumentException: Lacking enabled CPS or config activity，规则根本建不出来。
        "setConfigurationActivity(")) and "AUTOMATIC_ZEN_RULE" in manifest
    safe = all(k in dnd for k in (
        "allowAlarms(true)", "allowMedia(true)", "ZenPolicy.PEOPLE_TYPE_STARRED",
        "showInNotificationList(true)", "showStatusBarIcons(true)"))
    no_override = "allowRepeatCallers(" not in dnd
    add("PASS" if (rule and safe and no_override) else "FAIL",
        "v2.25 · 自有规则与例外清单",
        f"单条复用+Condition={rule}；闹钟/媒体/收藏/通知可见={safe}；重复来电沿用用户设置={no_override}")

    # ③ 底线之二：四条停用路径一条不少。
    settle_seg = eng_code.split("private fun settleIfDue", 1)[-1].split("private fun clearRunState", 1)[0]
    stop_seg = eng_code.split("fun stop()", 1)[-1].split("// finish() 已删除", 1)[0]
    normal_end = "DndGuard.onSittingEnd(app)" in settle_seg
    early_end = "DndGuard.onSittingEnd(app)" in stop_seg
    startup = "DndGuard.reconcileOnStartup(this)" in app
    receiver = "DndGuard.reconcileOnStartup(" in recv
    boot = all(k in manifest for k in (
        "BOOT_COMPLETED", "MY_PACKAGE_REPLACED", "DND_RESTORE",
        ".DndRestoreReceiver", ".ZenApp", "RECEIVE_BOOT_COMPLETED"))
    alarm = "setAndAllowWhileIdle(" in dnd and "cancelFallback(ctx)" in dnd
    rest = "DndGuard.onSittingStart(app, plannedEndWall)" in eng_code
    paths = (normal_end, early_end, startup, receiver, boot, alarm, rest)
    add("PASS" if all(paths) else "FAIL", "v2.25 · 复原四道兜底 + 起跑挂闹钟",
        f"正常结算={normal_end} 提前结束={early_end} 冷启动自愈={startup} "
        f"重启/升级/闹钟广播={receiver and boot} 闹钟与取消={alarm} 起跑激活={rest}")

    # ④ 结束铃不许被自己静掉：起响必须排在停用规则之前。
    bell_seg = settle_seg.split("SettlementPolicy.Outcome.SETTLE_WITH_BELL,", 1)[-1]
    p, e = bell_seg.find("BellPlayer.playOnce("), bell_seg.find("DndGuard.onSittingEnd(app)")
    order = 0 <= p < e
    add("PASS" if order else "FAIL", "v2.25 · 结束铃先响再停用",
        f"响铃位置 {p} < 停用位置 {e}" if order else f"顺序错了：{p} / {e}")

    # ⑤ 四时：只有两个变量、按公历分段、日期只在进程启动时读一次、无手动四选一。
    two = "data class SeasonTokens(val paper: Color, val accent: Color)" in season
    months = all(k in season for k in (
        "3, 4, 5 -> SPRING", "6, 7, 8 -> SUMMER", "9, 10, 11 -> AUTUMN", "else -> WINTER"))
    once = season.count("LocalDate.now()") == 1 and "SeasonTheme.init(this)" in app
    snap = "@Volatile" in season and "private var snapshot" in season
    no_picker = not any(k in main_code for k in (
        "Season.SPRING", "season_spring", "Season.entries.forEach", "Season.values().forEach"))
    ok_season = two and months and once and snap and no_picker
    add("PASS" if ok_season else "FAIL", "v2.25 · 四时只动纸色与强调色",
        f"两 token={two}；公历四段={months}；日期只读一次={once}；进程快照={snap}；无四选一={no_picker}")

    # ⑥ 静坐中绝不换色：设置页开关置灰，色板只在主题总入口读一次快照。
    guard = 'enabled = !inSitting' in main_code and 'val inSitting = timerUi.running' in main_code
    single = main_code.count("SeasonTheme.current") == 1
    add("PASS" if (guard and single) else "FAIL", "v2.25 · 静坐中不换纸色",
        f"开关置灰={guard}；快照只在主题入口读一次={single}")

    # ⑦ 三语文案与规则名；规则名不得写产品名。
    names = re.findall(r'"dnd_rule_name" to "([^"]+)"', lang)
    keys = ("season_fine_tune", "season_note", "season_note_running", "dnd_title",
            "dnd_sub", "dnd_denied", "dnd_unsupported", "dnd_active", "dnd_rule_name")
    three = all(lang.count(f'"{k}" to') == 3 for k in keys)
    ok_names = names == ["静坐", "靜坐", "Meditation"]
    add("PASS" if (three and ok_names) else "FAIL", "v2.25 · 新增文案三语齐备",
        f"{len(keys)} 组 × 3 语={three}；规则名 {' / '.join(names) if names else '缺失'}")

    # ⑧ 默认关闭 + 授权走系统页 + 返回后现读真实权限，不把「点了开关」当「已授权」。
    off = 'getBoolean(KEY_ENABLED, false)' in dnd
    auth = all(k in main_code for k in (
        "ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS", "DndGuard.granted(ctx)",
        "DndGuard.setEnabled(ctx, ok)"))
    avail = "Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q" in dnd
    add("PASS" if (off and auth and avail) else "FAIL", "v2.25 · 默认关闭与授权闭环",
        f"默认关={off}；系统授权页+现读权限={auth}；API 29 守卫={avail}")


def check_v229_scroll():
    """D03 必须事件驱动，禁止回退到「猜帧数」。

    TRAE v2.28 用 while + withFrameNanos 等测量 / 等 maxValue，帧数在
    30 / 60 / 120Hz 机器上代表的时间完全不同，是时序赌博；v2.29 换
    snapshotFlow + withTimeoutOrNull。这里把不变量钉死，防回潮。
    """
    code = strip_comments((SRC / "MainActivity.kt").read_text(encoding="utf-8"))
    wait_measure = "snapshotFlow { foldBottomPx to viewportBottomPx }" in code
    wait_max = "snapshotFlow { pageScroll.maxValue }" in code
    timeout = code.count("withTimeoutOrNull(1000L)") >= 2
    guess_frames = re.search(
        r"while\s*\([^)]*\)\s*\{[^}]*withFrameNanos", code, re.S)
    pos = "positionInWindow()" in code
    d09 = "foldHeightPx" in code and "animateScrollTo" in code
    add("PASS" if (wait_measure and wait_max and timeout) else "FAIL",
        "D03 · 等测量 / 等 maxValue 走 snapshotFlow + 超时",
        f"等测量={wait_measure}；等 maxValue={wait_max}；1s 超时兜底={timeout}")
    add("PASS" if not guess_frames else "FAIL", "D03 · 禁止 while+withFrameNanos 猜帧",
        "已清零" if not guess_frames else "仍有忙等帧循环")
    add("PASS" if pos else "FAIL", "D03 · 折叠区用 positionInWindow 避裁剪",
        f"positionInWindow={pos}")
    add("PASS" if d09 else "FAIL", "D09 · 收起按高度回退仍在",
        f"foldHeightPx 与 animateScrollTo 均在={d09}")
    probe = ROOT / "tools" / "probe_d03.py"
    add("PASS" if probe.exists() else "FAIL", "D03 · 专项验收脚本在库内",
        probe.name if probe.exists() else "缺失")


def main():
    check_terms()
    check_keys()
    check_hardcoded()
    check_quality()
    check_defaults()
    check_resources()
    check_version()
    check_a11y()
    check_support_author()
    check_duration_field()
    check_v225_features()
    check_v229_scroll()

    # 11-09 22:5x：把《证据核查》§9 E11~E14 的标杆基线接进同一道闸门。
    # 上午 AOD 冒数字就是台账写了不变量、代码没闸门，成本最低的防复发是静态断言。
    ev, _deviations = run_evidence_invariants()
    for level, name, detail in ev:
        add("PASS" if level == "PASS" else "FAIL", f"证据不变量 · {name}", detail)

    counts = Counter(o[0] for o in OPS)
    lines = [
        "# UI 文案 / 工程口径批量检查报告",
        "",
        f"> 生成：`python tools/check_ui_copy.py` ｜ 结论：**{counts['PASS']} PASS / {counts['WARN']} WARN / {counts['FAIL']} FAIL**",
        "> 硬错误（FAIL）会挡出包；WARN 是需要人看一眼、不是错。",
        "",
        "## 一、本次自动检查",
        "",
        "| 级别 | 检查项 | 结论 |",
        "|---|---|---|",
    ]
    for lv, item, msg in OPS:
        lines.append(f"| {lv} | {item} | {msg.replace('|', '/')} |")
    lines += ["", "## 二、还能继续自动化的（本次未做）", ""]
    lines += [f"{i+1}. {x}" for i, x in enumerate(CAN_AUTOMATE_NEXT)]
    lines += ["", "## 三、只能真机 / 人判断（自动化替代不了）", ""]
    lines += [f"{i+1}. {x}" for i, x in enumerate(NEED_HUMAN)]
    lines += [
        "",
        "## 四、刻意不改的地方",
        "",
        "- `CHANGELOG.md` 历史条目、`证据核查-2026-11-08.md`、`复盘-REA与需求补位.md`、"
        "`HANDOFF-通知与锁屏.md`、`FeatureMap-TestCase-整体设计.md`、`交付说明.md`、"
        "`迭代计划.md` 里的旧词是历史证据与需求原话，按「结论变了别删旧版」保留。",
        "- `AndroidManifest.xml` 的 `meditation_timer_playback` 是系统前台服务子类型标识，改了会影响声明。",
        "- `AppInfo.kt` 的 `AUTHOR = 耀慧` 是署名不是微信名，按 11-09 上午的口径保留；法律文本同样保留。",
        "",
    ]
    REPORT.write_text("\n".join(lines), encoding="utf-8")
    print(f"{counts['PASS']} PASS / {counts['WARN']} WARN / {counts['FAIL']} FAIL → {REPORT}")
    for lv, item, msg in OPS:
        if lv == "FAIL":
            print(f"  FAIL {item}: {msg}")
    return 1 if HARD_FAIL else 0


if __name__ == "__main__":
    sys.exit(main())
