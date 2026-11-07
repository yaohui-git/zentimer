# -*- coding: utf-8 -*-
"""
开源前的素材隐私开关（一个脚本两个方向）：

    python tools/asset_privacy.py strip     # 移出原版实物素材，生成同名占位，公开仓库仍可编译
    python tools/asset_privacy.py restore  # 把实物素材搬回，恢复完整高仿版（本地构建用）

为什么需要：
  本应用为了「界面、声音高仿」复用了原版包里的 23 段 mp3 与 36 张图片。
  个人自用没问题，但 **push 到公开 GitHub 仓库 / 提交应用商店就是分发行为**，
  这些素材的授权状态需要自行确认。strip 后仓库里只剩自绘占位素材（同名、同格式），
  工程照样能编译，任何人 clone 下来都能 build；本地再 restore 即得完整版。

占位音频用 ffmpeg 生成 1 秒静音 mp3（保持 .mp3 后缀，R.raw 名字不变，代码零改动）。
占位图片用 Pillow 生成深色圆环。原件一律移到 assets-private/（.gitignore 已排除）。

依赖：Pillow + ffmpeg（仅脚本运行期，不是 App 运行时依赖）。
"""
import glob
import os
import shutil
import subprocess
import sys
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROJ = os.path.join(ROOT, "StillTimer")
RES = os.path.join(PROJ, "app", "src", "main", "res")
RAW = os.path.join(RES, "raw")
DRAW = os.path.join(RES, "drawable-nodpi")
PRIVATE = os.path.join(PROJ, "assets-private")
AUDIO = os.path.join(PRIVATE, "audio")
IMAGES = os.path.join(PRIVATE, "images")
# 启动图标 B 是自绘 + 用户自有 AI 图生成的人形，不属于原版素材，不参与剥离
KEEP_IMAGES = {"ic_launcher_foreground.png"}

BG = (8, 13, 20)
GOLD = (232, 207, 160)


def ffmpeg():
    exe = shutil.which("ffmpeg")
    if exe:
        return exe
    for p in (r"C:/ffmpeg/bin/ffmpeg.exe",
              r"C:/Users/thinkpad/AppData/Local/Microsoft/WinGet/Packages/Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe/ffmpeg-9.0.2-full_build/bin/ffmpeg.exe"):
        if os.path.exists(p):
            return p
    raise RuntimeError("找不到 ffmpeg：请安装并把 ffmpeg 加入 PATH，或改用自备的静音 mp3 覆盖 res/raw/")


def placeholder_png(path):
    """深色底 + 暖金细环 + 中心一点，纯自绘，无任何第三方素材。"""
    s = 320
    im = Image.new("RGB", (s, s), BG)
    dr = ImageDraw.Draw(im)
    dr.ellipse([44, 44, s - 44, s - 44], outline=GOLD, width=4)
    dr.ellipse([s // 2 - 9, s // 2 - 9, s // 2 + 9, s // 2 + 9], fill=GOLD)
    im.save(path, optimize=True)


def silent_mp3(path):
    subprocess.run(
        [ffmpeg(), "-loglevel", "error", "-f", "lavfi", "-i", "anullsrc=r=44100:cl=mono",
         "-t", "1", "-c:a", "libmp3lame", "-q:a", "9", "-y", path],
        check=True
    )


def strip():
    os.makedirs(AUDIO, exist_ok=True)
    os.makedirs(IMAGES, exist_ok=True)
    exe = ffmpeg()  # 提前失败，避免搬走一半才发现没工具
    print("ffmpeg:", exe)

    mp3s = sorted(glob.glob(os.path.join(RAW, "*.mp3")))
    for src in mp3s:
        name = os.path.basename(src)
        dest = os.path.join(AUDIO, name)
        if not os.path.exists(dest):
            shutil.copy2(src, dest)
        os.remove(src)
        silent_mp3(src)
    print(f"音频：{len(mp3s)} 段原件已移入 assets-private/audio/，同名静音占位已生成")

    pngs = sorted(p for p in glob.glob(os.path.join(DRAW, "*.png"))
                  if os.path.basename(p) not in KEEP_IMAGES)
    for src in pngs:
        name = os.path.basename(src)
        dest = os.path.join(IMAGES, name)
        if not os.path.exists(dest):
            shutil.copy2(src, dest)
        os.remove(src)
        placeholder_png(src)
    print(f"图片：{len(pngs)} 张原件已移入 assets-private/images/，同名自绘占位已生成")
    print(f"启动图标 {', '.join(sorted(KEEP_IMAGES))} 保留（自绘，无版权风险）")
    print("\n现在可以安全 push。本地要恢复完整版：python tools/asset_privacy.py restore")


def restore():
    if not os.path.isdir(PRIVATE):
        raise RuntimeError("assets-private/ 不存在，没有可恢复的原件")
    n = 0
    for src in sorted(glob.glob(os.path.join(AUDIO, "*.mp3"))):
        shutil.copy2(src, os.path.join(RAW, os.path.basename(src)))
        n += 1
    for src in sorted(glob.glob(os.path.join(IMAGES, "*.png"))):
        shutil.copy2(src, os.path.join(DRAW, os.path.basename(src)))
        n += 1
    print(f"已恢复 {n} 个实物素材到 res/。注意：这些文件受版本控制，恢复后不要 commit 到公开仓库。")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "strip":
        strip()
    elif cmd == "restore":
        restore()
    else:
        print(__doc__)
        sys.exit(1)
