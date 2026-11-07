# -*- coding: utf-8 -*-
"""
生成一个「可以直接 git init + push」的干净开源目录 stilltimer/oss/。

    python tools/asset_privacy.py strip   # 先净化素材
    python tools/make_oss_repo.py         # 再打包

只复制白名单文件，从源头排除：成品 APK、签名密钥与密码、本机 SDK 路径、
构建产物、逆向证据 .craft/、内部交付与分析文档、上游交接包。
若发现 res/raw 里仍是原版大体积 mp3（说明没跑 strip），默认中止，避免误传。
"""
import glob
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "oss")
RAW = os.path.join(ROOT, "StillTimer", "app", "src", "main", "res", "raw")

ROOT_DOCS = [
    "README.md", "LICENSE", "NOTICE.md", "CONTRIBUTING.md",
    "PRIVACY-POLICY.md", "CHANGELOG.md", "ASSETS-NOTICE.md",
    "STORE-LISTING.md", "交付说明.md",
]
DIRS = ["StillTimer", "tools", "store", "assets", "screenshots", "releases", "docs", ".github"]

EXCLUDE_DIRS = {"build", ".gradle", "assets-private", "__pycache__", ".git"}
EXCLUDE_FILES = {
    "stilltimer.jks", "keystore.properties", "local.properties",
    "craft.zip", ".DS_Store",
}
EXCLUDE_SUFFIX = (".apk", ".jks", ".zip")

OSS_GITIGNORE = """# Gradle / Android
.gradle/
build/
.kotlin/
.cxx/
captures/
*.iml
.idea/
*.log
output-metadata.json

# 本机配置与签名（绝不入库）
local.properties
*.jks
*.keystore
keystore.properties
assets-private/

# 正式发布 APK 例外：项目所有者明确要求随仓库提供
*.apk
*.aab
!releases/
!releases/*.apk
"""


def ignore(dirpath, names):
    drop = set()
    # releases/ 下的正式签名 APK 是公开交付物，其余位置的 .apk 一律排除
    in_releases = os.path.basename(dirpath) == "releases"
    for n in names:
        full = os.path.join(dirpath, n)
        release_apk = in_releases and n.endswith(".apk")
        if os.path.isdir(full) and n in EXCLUDE_DIRS:
            drop.add(n)
        elif not release_apk and (n in EXCLUDE_FILES or n.endswith(EXCLUDE_SUFFIX)):
            drop.add(n)
    return drop


def main():
    force = "--force" in sys.argv
    big = [p for p in glob.glob(os.path.join(RAW, "*.mp3")) if os.path.getsize(p) > 20000]
    if big and not force:
        print("⚠ 检测到 res/raw/ 里仍有", len(big), "段大体积 mp3（疑似原版实物录音）。")
        print("  公开仓库前请先运行：python tools/asset_privacy.py strip")
        print("  确认素材授权无问题、就是要原样公开时，加 --force。")
        sys.exit(1)

    # 不做整目录 rmtree：oss/ 里常有上百个素材文件，整批删除会触发批量删除保护，
    # 也可能在中断时把生成目录删成半截。改为同名覆盖，源目录是唯一事实来源。
    os.makedirs(OUT, exist_ok=True)

    for name in ROOT_DOCS:
        src = os.path.join(ROOT, name)
        if os.path.exists(src):
            shutil.copy2(src, os.path.join(OUT, name))

    for name in DIRS:
        src = os.path.join(ROOT, name)
        if os.path.isdir(src):
            shutil.copytree(src, os.path.join(OUT, name), ignore=ignore, dirs_exist_ok=True)

    with open(os.path.join(OUT, ".gitignore"), "w", encoding="utf-8", newline="\n") as f:
        f.write(OSS_GITIGNORE)

    files = [os.path.join(dp, f) for dp, _, fs in os.walk(OUT) for f in fs]
    size = sum(os.path.getsize(p) for p in files)
    print(f"oss/ 已生成：{len(files)} 个文件，{size / 1048576:.1f} MB")
    print("检查清单：")
    for p in files:
        base = os.path.basename(p)
        norm = p.replace("\\", "/")
        is_release_apk = p.endswith(".apk") and "/releases/" in norm
        sensitive = (p.endswith(".jks") or (p.endswith(".apk") and not is_release_apk)) \
            or base in ("keystore.properties", "local.properties") \
            or ("assets-private" in p.split(os.sep))
        if sensitive:
            print("  !! 不该出现：", p)
    print("可直接：cd oss && git init && git add . && git commit")


if __name__ == "__main__":
    main()
