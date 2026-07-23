"""
LearnSpark 图标资源生成脚本
===========================
从 tools/icon-source.png（≥1024×1024，正方形，RGBA 透明背景更佳）一键生成：

  - Android mipmap 五档 ic_launcher.png + 圆形 ic_launcher_round.png
  - Desktop icon.png（Compose Window / 任务栏）
  - Windows .ico（多尺寸打包）
  - macOS .icns（多尺寸打包，macOS 构建用；不强制要求，缺失时 macOS 打包用 1024 png 兜底）

用法（首次会下载 pillow，如已装会自动跳过）：
  python tools/generate_icons.py

输出会覆盖：
  composeApp/src/androidMain/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png
  composeApp/src/androidMain/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_round.png
  composeApp/src/desktopMain/resources/icon.png
  composeApp/src/desktopMain/resources/icon.ico
  composeApp/src/desktopMain/resources/icon.icns   (macOS 用)
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "tools" / "icon-source.png"

# Android mipmap 规范尺寸（dp 24 → px）
ANDROID_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Desktop 多尺寸
DESKTOP_SIZES = [16, 24, 32, 48, 64, 128, 256, 512, 1024]


def make_square(img: Image.Image) -> Image.Image:
    """将源图裁剪为正方形（取中心）。"""
    w, h = img.size
    if w == h:
        return img
    side = min(w, h)
    left = (w - side) // 2
    top = (h - side) // 2
    return img.crop((left, top, left + side, top + side))


def add_padding(img: Image.Image, ratio: float = 0.06) -> Image.Image:
    """
    Adaptive icon 规范要求主图占 108dp 中的 72dp（约 66%），
    外围留出安全区。这里给图标加一圈透明 padding，避免在圆形/圆角遮罩下被裁。
    ratio=0.06 即四周各 6% 透明边，对应 Android adaptive icon ~88% 内容区。
    """
    w, h = img.size
    pad = int(min(w, h) * ratio)
    if pad <= 0:
        return img
    new = Image.new("RGBA", (w + 2 * pad, h + 2 * pad), (0, 0, 0, 0))
    new.paste(img, (pad, pad), img)
    return new


def make_round(img: Image.Image) -> Image.Image:
    """把方形图标裁成圆形（用于 ic_launcher_round）。"""
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    from PIL import ImageDraw
    d = ImageDraw.Draw(mask)
    d.ellipse((0, 0, w, h), fill=255)
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def ensure_dir(p: Path) -> None:
    p.mkdir(parents=True, exist_ok=True)


def main() -> None:
    if not SRC.exists():
        raise SystemExit(f"[ERROR] 找不到源图标：{SRC}\n请先放一张 ≥1024×1024 的 PNG 到该路径。")

    src = Image.open(SRC).convert("RGBA")
    src = make_square(src)
    print(f"[INFO] 源图：{src.size[0]}×{src.size[1]} RGBA（已裁剪为正方形）")

    padded = add_padding(src, ratio=0.06)
    print(f"[INFO] 加 padding 后：{padded.size[0]}×{padded.size[1]}（用于 Android mipmap）")

    # ===== Android mipmap =====
    android_root = ROOT / "composeApp/src/androidMain/res"
    for folder, size in ANDROID_SIZES.items():
        out_dir = android_root / folder
        ensure_dir(out_dir)

        # ic_launcher.png（方形，padded 留安全区）
        out_path = out_dir / "ic_launcher.png"
        resized = padded.resize((size, size), Image.LANCZOS)
        resized.save(out_path, "PNG", optimize=True)
        print(f"  ✓ {out_path.relative_to(ROOT)} ({size}×{size})")

        # ic_launcher_round.png（圆形，外层裁剪）
        out_round = out_dir / "ic_launcher_round.png"
        round_resized = padded.resize((size, size), Image.LANCZOS)
        round_img = make_round(round_resized)
        round_img.save(out_round, "PNG", optimize=True)
        print(f"  ✓ {out_round.relative_to(ROOT)} ({size}×{size}, round)")

    # ===== Desktop =====
    desktop_res_dir = ROOT / "composeApp/src/desktopMain/resources"
    ensure_dir(desktop_res_dir)

    # 1024 png（最高清晰度，macOS .icns 兜底 / Compose Window 任务栏）
    src.resize((1024, 1024), Image.LANCZOS).save(desktop_res_dir / "icon.png", "PNG", optimize=True)
    print(f"  ✓ {Path('composeApp/src/desktopMain/resources/icon.png').as_posix()} (1024×1024)")

    # 256 png（Linux DEB 安装器标准尺寸）
    src.resize((256, 256), Image.LANCZOS).save(desktop_res_dir / "icon-256.png", "PNG", optimize=True)
    print(f"  ✓ {Path('composeApp/src/desktopMain/resources/icon-256.png').as_posix()} (256×256, for DEB)")

    # .ico 多尺寸（Windows 任务栏 + EXE 资源）
    ico_sizes = [(s, s) for s in DESKTOP_SIZES]
    ico_src = src.resize(ico_sizes[-1], Image.LANCZOS)  # 1024
    ico_src.save(
        desktop_res_dir / "icon.ico",
        format="ICO",
        sizes=ico_sizes,
        append_images=[src.resize(sz, Image.LANCZOS) for sz in ico_sizes[:-1]],
    )
    print(f"  ✓ {Path('composeApp/src/desktopMain/resources/icon.ico').as_posix()} (sizes: {DESKTOP_SIZES})")

    # .icns 多尺寸（macOS 打包用）
    # Pillow 自带 ICNS 支持，但仅在 macOS 上才可写；这里尝试写入，失败就跳过（不影响 Windows/Linux）
    try:
        icns_sizes = [(16, 16), (32, 32), (64, 64), (128, 128), (256, 256), (512, 512), (1024, 1024)]
        icns_src = src.resize(icns_sizes[-1], Image.LANCZOS)
        icns_src.save(
            desktop_res_dir / "icon.icns",
            format="ICNS",
            append_images=[src.resize(sz, Image.LANCZOS) for sz in icns_sizes[:-1]],
        )
        print(f"  ✓ {Path('composeApp/src/desktopMain/resources/icon.icns').as_posix()} (macOS)")
    except (OSError, ValueError) as e:
        print(f"  ⚠ .icns 跳过（{e}）。macOS 打包会回退用 icon.png。")

    print()
    print("=" * 60)
    print("所有图标已生成。")
    print("接下来需要修改 3 处配置：")
    print("  1. composeApp/build.gradle.kts")
    print("       nativeDistributions {")
    print("           iconFile.set(project.file(\"src/desktopMain/resources/icon.ico\"))")
    print("           // macOS.iconFile.set(...)  // 可选，指向 .icns")
    print("       }")
    print("  2. composeApp/src/desktopMain/kotlin/com/learnspark/Main.kt")
    print("       Window(icon = painterResource(\"icon.png\").image, ...)")
    print("  3. composeApp/src/androidMain/AndroidManifest.xml")
    print('       <application android:icon="@mipmap/ic_launcher" ...>')
    print("=" * 60)


if __name__ == "__main__":
    main()
