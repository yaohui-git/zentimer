# -*- coding: utf-8 -*-
"""
生成 v2.2 全套启动图标与商店图标。

用户最终选定 **方案 B · 圆环计时** 作为 APK 启动图标：
  - 青绿打坐人形从用户提供的 AI 生成图里按颜色抠出（保留原明暗渐变）
  - 细金环 / 进度弧 / 端点光点 / 12 刻度为纯自绘，与 App 内计时圆环同构
  - 不含任何第三方实物照片，开源与商店审核无图标版权风险
方案 A（用户原图适配）与方案 C（圆相一息）仍生成在 store/ 留作对照，不进 APK。

【2026-11-12 注 · 别误跑】本脚本产出的是 **v2.31 及以前的冷墨蓝底旧资产**。
v2.32 起 APK 图标按用户选型改为「丁 · 仅换底色」（宣纸暖白），生成入口是
`tools/apply_icon_d.py`；重跑本脚本会把五档 mipmap 与 store/icon-512.png
**覆盖回旧近黑底**。只有需要复现旧资产时才跑，改图标请走 apply_icon_d.py。

依赖：Pillow + numpy（仅生成期使用，不是 App 运行时依赖）。
"""
import math
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = r"C:/Users/thinkpad/.workbuddy/clipboard-images/clipboard-2026-11-07T14-09-14-551Z-fe28deba.jpg"
RES = os.path.join(ROOT, "ZenTimer", "app", "src", "main", "res")
STORE = os.path.join(ROOT, "store")
os.makedirs(STORE, exist_ok=True)

BG = (8, 13, 20)          # #080D14 冷墨蓝
GOLD = (232, 207, 160)    # #E8CFA0 暖米金
GOLD_DIM = (140, 126, 99)
TEAL = (56, 124, 123)     # 用户原图里的青绿
IVORY = (243, 240, 233)


def font(size):
    for p in (r"C:/Windows/Fonts/msyh.ttc", r"C:/Windows/Fonts/simhei.ttf", r"C:/Windows/Fonts/arial.ttf"):
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                pass
    return ImageFont.load_default()


# ---------- 方案 A：用户原图适配 ----------

def load_plate():
    src = Image.open(SRC).convert("RGB")
    # 水印兜底：右下角整块填成原图底色
    ImageDraw.Draw(src).rectangle([1530, 1690, 1920, 1920], fill=(252, 252, 252))
    cx, cy, half = 962, 966, 575
    disc = src.crop((cx - half, cy - half, cx + half, cy + half)).convert("RGBA")
    s = disc.size[0]
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).ellipse([5, 5, s - 5, s - 5], fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(1.5))
    disc.putalpha(mask)
    return disc


def legacy(size, plate, ratio=0.88):
    """传统启动图标：深墨蓝底 + 居中钟面盘（各密度 mipmap / 商店 512 图标）。"""
    canvas = Image.new("RGBA", (size, size), BG + (255,))
    d = int(size * ratio)
    p = plate.resize((d, d), Image.LANCZOS)
    canvas.alpha_composite(p, ((size - d) // 2, (size - d) // 2))
    return canvas.convert("RGB")


def adaptive_foreground(plate, size=1024, ratio=0.61):
    """108dp 画布、图形落在中央 66dp 安全区，系统怎么裁都不切环。"""
    fg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = int(size * ratio)
    p = plate.resize((d, d), Image.LANCZOS)
    fg.alpha_composite(p, ((size - d) // 2, (size - d) // 2))
    return fg


# ---------- 方案 B：与 App 内圆环同构 ----------

def extract_figure():
    """从用户原图里按青绿色抠出打坐人形，保留原有的明暗渐变。"""
    a = np.array(Image.open(SRC).convert("RGB")).astype(int)
    r, g, b = a[:, :, 0], a[:, :, 1], a[:, :, 2]
    mask = (g - r > 35) & (b - r > 35) & (g < 195) & (r < 135)
    h, w = mask.shape
    yy, xx = np.mgrid[0:h, 0:w]
    mask &= (xx > 500) & (xx < 1450) & (yy > 450) & (yy < 1460)   # 排除右下水印
    ys, xs = np.where(mask)
    x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
    rgba = np.zeros((y1 - y0 + 1, x1 - x0 + 1, 4), dtype=np.uint8)
    rgba[:, :, :3] = a[y0:y1 + 1, x0:x1 + 1].astype(np.uint8)
    rgba[:, :, 3] = (mask[y0:y1 + 1, x0:x1 + 1] * 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def concept_b(size, figure, transparent=False):
    """transparent=True 时只画图形，供 adaptive icon 的 foreground 使用。"""
    K = 4
    S = size * K
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0) if transparent else BG + (255,))
    dr = ImageDraw.Draw(im)
    box = [int(S * .17), int(S * .17), int(S * .83), int(S * .83)]
    # 12 个极淡刻度
    for i in range(12):
        a = math.radians(-90 + i * 30)
        r1, r2 = S * .355, S * .375
        cx = cy = S / 2
        dr.line([(cx + r1 * math.cos(a), cy + r1 * math.sin(a)),
                 (cx + r2 * math.cos(a), cy + r2 * math.sin(a))],
                fill=(43, 58, 72, 255), width=3 * K)
    # 细金环 + 一段进度弧（呼应 App 内圆环），弧端点一枚光点
    dr.ellipse(box, outline=GOLD_DIM + (255,), width=3 * K)
    dr.arc(box, start=-90, end=42, fill=GOLD + (255,), width=9 * K)
    ang = math.radians(42)
    rr = S * .33
    ex, ey = S / 2 + rr * math.cos(ang), S / 2 + rr * math.sin(ang)
    pr = 7 * K
    dr.ellipse([ex - pr, ey - pr, ex + pr, ey + pr], fill=IVORY + (255,))
    # 复用原图青绿人形，比自绘几何符号辨识度高得多
    fw = int(S * .45)
    fh = round(figure.height * fw / figure.width)
    fg = figure.resize((fw, fh), Image.LANCZOS)
    im.alpha_composite(fg, ((S - fw) // 2, (S - fh) // 2 + int(S * .012)))
    out = im.resize((size, size), Image.LANCZOS)
    return out if transparent else out.convert("RGB")


# ---------- 方案 C：圆相一息 ----------

def concept_c(size=512):
    K = 4
    S = size * K
    im = Image.new("RGB", (S, S), BG)
    dr = ImageDraw.Draw(im)
    box = [int(S * .20), int(S * .20), int(S * .80), int(S * .80)]
    steps = 46
    a0, a1 = -78, 238
    for i in range(steps):
        t = i / (steps - 1)
        start = a0 + (a1 - a0) * i / steps
        end = a0 + (a1 - a0) * (i + 1) / steps + 1.2
        w = max(2, int((3 + 15 * math.sin(math.pi * t) ** 1.4) * K))
        fade = 0.55 + 0.45 * math.sin(math.pi * t)
        col = tuple(int(BG[j] + (GOLD[j] - BG[j]) * fade) for j in range(3))
        dr.arc(box, start=start, end=end, fill=col, width=w)
    # 一息：中心一点
    r = int(S * .033)
    cx = cy = S // 2
    dr.ellipse([cx - r, cy - r, cx + r, cy + r], fill=GOLD)
    return im.resize((size, size), Image.LANCZOS)


def contact_sheet(images, labels, notes, out):
    cell, pad, top, bottom = 420, 46, 78, 116
    W = pad + len(images) * (cell + pad)
    H = top + cell + bottom
    sheet = Image.new("RGB", (W, H), (16, 23, 32))
    dr = ImageDraw.Draw(sheet)
    f_title = font(34)
    f_lab = font(26)
    f_note = font(19)
    dr.text((pad, 22), "静息 · 启动图标方案对比", font=f_title, fill=IVORY)
    for i, (img, lab, note) in enumerate(zip(images, labels, notes)):
        x = pad + i * (cell + pad)
        sheet.paste(img.resize((cell, cell), Image.LANCZOS), (x, top))
        dr.rounded_rectangle([x, top, x + cell, top + cell], radius=26, outline=(43, 58, 72), width=2)
        dr.text((x + 4, top + cell + 16), lab, font=f_lab, fill=GOLD)
        dr.text((x + 4, top + cell + 56), note, font=f_note, fill=(140, 155, 170))
    sheet.save(out)
    print("对比图", out, sheet.size)


def feature_graphic(icon):
    """小米等应用商店要求的 1024×500 宣传图，纯自绘 + 自绘图标，无第三方素材。"""
    W, H = 1024, 500
    im = Image.new("RGB", (W, H), BG)
    dr = ImageDraw.Draw(im)
    # 极淡的进度弧做背景纹理，呼应 App 内圆环
    dr.arc([-240, -300, 760, 700], start=-90, end=60, fill=(24, 34, 45), width=54)
    dr.arc([-160, -220, 680, 620], start=-90, end=30, fill=(30, 42, 55), width=28)
    side = 330
    im.paste(icon.resize((side, side), Image.LANCZOS), (W - side - 88, (H - side) // 2))
    f_zh = font(58)
    f_en = font(26)
    f_tag = font(21)
    f_small = font(17)
    dr.text((94, 132), "静  息", font=f_zh, fill=IVORY)
    dr.text((96, 208), "zentimer · Meditation Timer", font=f_en, fill=GOLD)
    dr.text((96, 268), "一只需要计时和一声钵响的闹钟", font=f_tag, fill=(178, 191, 203))
    dr.text((96, 300), "A timer and a bowl. Nothing else.", font=f_small, fill=(120, 137, 153))
    dr.line([(96, 352), (W - side - 140, 352)], fill=(43, 58, 72), width=2)
    dr.text((96, 374), "无广告 · 不联网 · 简体 / 繁體 / English", font=f_small, fill=GOLD_DIM)
    out = os.path.join(STORE, "feature-graphic-1024x500.png")
    im.save(out, optimize=True)
    print("宣传图", out)


def main():
    figure = extract_figure()

    # 1. adaptive 前景：透明底 B 图形，圆环约占画布 66%，再留 8% 余量防视差裁切
    fg = concept_b(512, figure, transparent=True)
    inner = int(512 * .92)
    canvas = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    canvas.alpha_composite(fg.resize((inner, inner), Image.LANCZOS),
                           ((512 - inner) // 2, (512 - inner) // 2))
    canvas.save(os.path.join(RES, "drawable-nodpi", "ic_launcher_foreground.png"))

    # 2. 传统 mipmap 五档（B 自带冷墨蓝底）
    for folder, px in (("mipmap-mdpi", 48), ("mipmap-hdpi", 72), ("mipmap-xhdpi", 96),
                       ("mipmap-xxhdpi", 144), ("mipmap-xxxhdpi", 192)):
        d = os.path.join(RES, folder)
        os.makedirs(d, exist_ok=True)
        concept_b(px, figure).save(os.path.join(d, "ic_launcher.png"), optimize=True)

    # 3. 商店 512 图标
    b512 = concept_b(512, figure)
    b512.save(os.path.join(STORE, "icon-512.png"), optimize=True)
    feature_graphic(b512)

    # 4. 三方案对照（A / C 只留图，不进 APK）
    a512 = legacy(512, load_plate())
    a512.save(os.path.join(STORE, "concept-A-原图适配.png"))
    c512 = concept_c()
    c512.save(os.path.join(STORE, "concept-C-圆相一息.png"))
    contact_sheet(
        [a512, b512, c512],
        ["A · 原图适配", "B · 圆环计时（当前 APK 采用）", "C · 圆相一息"],
        ["钟面盘 + 打坐人形，去水印、深墨蓝外圈",
         "自绘圆环 + 原图人形，与 App 内进度弧同构",
         "一笔圆相 + 一点，最克制、最禅意"],
        os.path.join(STORE, "图标方案对比.png"))
    print("B 已写入 APK 资源；A/C 仅在 store/ 留作对照")


if __name__ == "__main__":
    main()
