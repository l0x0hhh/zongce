# -*- coding: utf-8 -*-
# 同步生成两个组件的 PNG 预览，供不渲染 previewLayout 的桌面选择器使用。
"""按现有组件尺寸、配色和图标生成静态预览；成果内容仅为示例。

运行：python tools/gen_widget_previews.py
依赖：Pillow。中文字体默认使用 Windows 微软雅黑，也可用 --font 指定。
快速录入为 180×110dp，成果卡片为默认 250×250dp，均按 3 倍像素输出。
调整组件外观时，应同时检查本脚本与 res/layout/widget_preview_*.xml。
"""

import argparse
import os
from pathlib import Path
import re

from PIL import Image, ImageDraw, ImageFont
from PIL.PngImagePlugin import PngInfo

ROOT = Path(__file__).resolve().parents[1]
SCALE = 3
RES = ROOT / "app" / "src" / "main" / "res"


def palette_color(name: str) -> str:
    """直接读取组件色板，避免 PNG 的色值与运行时逐渐漂移。"""
    source = (ROOT / "app/src/main/java/com/zongce/app/widget/WidgetPalette.kt").read_text(
        encoding="utf-8"
    )
    match = re.search(rf"val {name}\s*=\s*Color\(0xFF([0-9A-Fa-f]{{6}})\)", source)
    if match is None:
        raise ValueError(f"WidgetPalette 中没有找到颜色：{name}")
    return "#" + match.group(1)


CARD = palette_color("Card")
INK = palette_color("Ink")
PRIMARY = palette_color("Primary")
LIGHT = palette_color("StatTile")
SECONDARY = palette_color("SecondaryText")
DIVIDER = palette_color("Divider")


def rect(draw: ImageDraw.ImageDraw, bounds: tuple, fill: str, radius: float = 0) -> None:
    box = tuple(round(value * SCALE) for value in bounds)
    if radius:
        draw.rounded_rectangle(box, radius=round(radius * SCALE), fill=fill)
    else:
        draw.rectangle(box, fill=fill)


def font_path(override: str | None, bold: bool = False) -> Path:
    if override:
        return Path(override)
    windows_fonts = Path(os.environ.get("WINDIR", "C:/Windows")) / "Fonts"
    candidates = (
        windows_fonts / ("msyhbd.ttc" if bold else "msyh.ttc"),
        windows_fonts / "msyh.ttc",
        Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
        Path("/System/Library/Fonts/PingFang.ttc"),
    )
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise FileNotFoundError("没有找到中文字体，请用 --font 指定字体文件。")


def text(draw: ImageDraw.ImageDraw, value: str, x: float, y: float, size: int,
         color: str, font_override: str | None, bold: bool = False,
         anchor: str = "lm") -> None:
    face = ImageFont.truetype(str(font_path(font_override, bold)), size * SCALE)
    # 按字形边界居中，使图标和中文标签组成的整体与 Glance 的居中排列一致。
    bounds = draw.textbbox((0, 0), value, font=face)
    width = bounds[2] - bounds[0]
    height = bounds[3] - bounds[1]
    left = x * SCALE - bounds[0]
    if anchor == "mm":
        left -= width / 2
    elif anchor == "rm":
        left -= width
    top = y * SCALE - height / 2 - bounds[1]
    draw.text((round(left), round(top)), value, font=face, fill=color)


def icon(draw: ImageDraw.ImageDraw, kind: str, cx: float, cy: float,
         color: str, background: str) -> None:
    """复现 ic_widget_camera / ic_widget_gallery 的 24 单位 Material 图标。"""
    factor = 20 / 24
    left, top = cx - 10, cy - 10

    def box(x1, y1, x2, y2, fill, radius=0):
        rect(draw, (left + x1 * factor, top + y1 * factor,
                    left + x2 * factor, top + y2 * factor), fill, radius * factor)

    def polygon(points, fill):
        draw.polygon([(round((left + x * factor) * SCALE),
                       round((top + y * factor) * SCALE)) for x, y in points], fill=fill)

    if kind == "camera":
        box(2, 4, 22, 20, color, 2)
        polygon(((7.17, 4), (9, 2), (15, 2), (16.83, 4)), color)
        draw.ellipse(tuple(round(value * SCALE) for value in (
            left + 7 * factor, top + 7 * factor,
            left + 17 * factor, top + 17 * factor)), fill=background)
    else:
        box(6, 2, 22, 18, color, 2)
        polygon(((11, 12), (13.03, 14.71), (16, 11), (20, 16), (8, 16)), background)
        polygon(((2, 6), (2, 20), (4, 22), (18, 22), (18, 20), (4, 20), (4, 6)), color)


def card(width: int, height: int) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    image = Image.new("RGBA", (width * SCALE, height * SCALE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    rect(draw, (0, 0, width - 1 / SCALE, height - 1 / SCALE), CARD, 22)
    return image, draw


def save(image: Image.Image, output: Path, description: str) -> None:
    metadata = PngInfo()
    metadata.add_text("Description", description)
    image.save(output, pnginfo=metadata, optimize=True)
    print(f"{output.name}: {image.width}x{image.height}, {output.stat().st_size} bytes")


def generate_entry(output_dir: Path, font_override: str | None) -> None:
    image, draw = card(180, 110)
    # 对齐 JicunWidget：14dp 外边距、10dp 间隔、16dp 按钮圆角，无标题行。
    for left, right, label, kind, background, foreground in (
        (14, 85, "拍照", "camera", PRIMARY, CARD),
        (95, 166, "相册", "gallery", LIGHT, PRIMARY),
    ):
        rect(draw, (left, 14, right, 96), background, 16)
        center = (left + right) / 2
        icon(draw, kind, center, 44, foreground, background)
        text(draw, label, center, 68, 13, foreground, font_override, anchor="mm")
    save(image, output_dir / "widget_preview_entry_img.png",
         "快速录入组件预览：拍照、相册双按钮，与当前桌面组件版式一致。")


def generate_achievement(output_dir: Path, font_override: str | None) -> None:
    image, draw = card(250, 250)
    # 对齐 AchievementWidget 默认尺寸：12dp 外边距、48dp 标题与统计入口。
    rect(draw, (12, 12, 238, 60), LIGHT, 12)
    text(draw, "2025-2026 学年", 24, 36, 16, INK, font_override, bold=True)
    text(draw, "切换", 218, 36, 12, PRIMARY, font_override, anchor="rm")
    # 小三角直接绘制，避免中文字体缺少 ▾ 字形时显示成方框。
    draw.polygon([(222 * SCALE, 35 * SCALE), (226 * SCALE, 35 * SCALE),
                  (224 * SCALE, 38 * SCALE)], fill=PRIMARY)
    rect(draw, (12, 64, 238, 65), DIVIDER)

    # 示例记录仅说明列表外观，不读取用户的数据库或照片。
    examples = (
        ("校级优秀学生", "智育 · 2026-05-20 · 一等奖", "#7A5AF8"),
        ("大学生创新创业大赛", "劳育 · 2026-04-12 · 金奖", "#C9912A"),
        ("校园摄影比赛", "美育 · 2026-03-18 · 二等奖", "#E0603C"),
    )
    for index, (title, subtitle, dot) in enumerate(examples):
        y = 65 + index * 40
        rect(draw, (12, y + 16, 20, y + 24), dot, 4)
        text(draw, title, 28, y + 13, 13, INK, font_override, bold=True)
        text(draw, subtitle, 28, y + 30, 11, SECONDARY, font_override)

    rect(draw, (12, 190, 238, 238), LIGHT, 12)
    text(draw, "3 条成果 · 覆盖 3 育", 20, 214, 12, SECONDARY, font_override)
    text(draw, "查看全部 ›", 230, 214, 12, PRIMARY, font_override, anchor="rm")
    save(image, output_dir / "widget_preview_achievement_img.png",
         "我的成果组件预览：学年切换、示例成果列表、统计与查看全部；内容为示例。")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--font", help="自定义中文字体的 TTF/OTF/TTC 路径")
    parser.add_argument("--output-dir", type=Path, default=RES / "drawable-nodpi")
    args = parser.parse_args()
    font_path(args.font)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    generate_entry(args.output_dir, args.font)
    generate_achievement(args.output_dir, args.font)


if __name__ == "__main__":
    main()
