"""Builds src/main/resources/assets/gtnhkanban/textures/gui/frame.png from art/frame-mockup.png and art/card-mockup.png.

The mockups are pixel art drawn at 2 image pixels per art pixel. This script cuts it into pieces the mod stretches at
runtime, so panels and tabs can be any size while staying pixel-exact:

  (0, 0)    panel 9-slice source, 17x17: 8x8 corners, 1px edges, 1px centre
  (32, 0)   card fill 9-slice, 17x17, same layout: the card mockup's inner fill and inner shadow only
  (64, 0)   card border 9-slice: the mockup's bevel, brightened so its lightest grey is white; the mod tints it
            with the card type's color (multiplying back by the original grey gives the untinted look)
  (0, 32)   tab, selected (teal), 13x21: 6px left cap, 1px fill, 6px right cap
  (16, 32)  tab, normal (orange)
  (32, 32)  tab, hover (placeholder: normal, lightened)
  (48, 32)  tab, pressed (placeholder: normal, darkened)
  (80, 32)  tab, purple normal / (96, 32) hover / (112, 32) pressed: the orange set, hue-shifted (Projects tab)
  (96, 0)   column header 9-slice, 17x17, same layout (art/column-header.png, drawn at 1x)
  (0, 64)   priority icons, 16x16 each: low, medium, high (art/priority-*.png, drawn at 1x)
  (64, 32)  cog glyph, 12x12 (11x11 gear plus a 1px drop shadow), transparent background

Usage (from the repository root, needs Pillow):  python art/build_frame_atlas.py
To restyle hover or pressed tabs, replace the generated pieces here with hand-drawn ones.
"""

import colorsys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
MOCKUP = ROOT / "art" / "frame-mockup.png"
CARD_MOCKUP = ROOT / "art" / "card-mockup.png"
PRIORITIES = ["low", "medium", "high"]
CARD_FILL = {(0x3C, 0x3C, 0x3C), (0x23, 0x23, 0x23)}
CARD_OUTLINE = (0x30, 0x30, 0x30)
CARD_BORDER_LIGHT = 0x9A
ATLAS = ROOT / "src" / "main" / "resources" / "assets" / "gtnhkanban" / "textures" / "gui" / "frame.png"

# Art-pixel coordinates in the mockup (inclusive).
PANEL = (8, 40, 387, 288)
SELECTED_TAB = (32, 19, 95, 39)
NORMAL_TAB = (104, 19, 167, 39)
PURPLE_HUE = 275
ORANGE_OUTLINE = (44, 26, 2)
ORANGE_TEXT = (225, 170, 86)
ORANGE_HOVER_TEXT = (255, 196, 106)
COG_LIGHT = (255, 196, 106, 255)
COG_SHADOW = (44, 26, 2, 255)
# Drawn by hand: the mockup's 8px cog reads as a blob at game scale. Symmetric so it centres on whole pixels.
COG = [
    "....###....",
    ".#..###..#.",
    ".#########.",
    "..#######..",
    "####...####",
    "####...####",
    "####...####",
    "..#######..",
    ".#########.",
    ".#..###..#.",
    "....###....",
]


def main():
    art = Image.open(MOCKUP).convert("RGBA")
    art = art.resize((art.width // 2, art.height // 2), Image.NEAREST)
    atlas = Image.new("RGBA", (256, 256), (0, 0, 0, 0))

    put_nine_slice(atlas, art, PANEL, 0, 0)
    card = half(Image.open(CARD_MOCKUP).convert("RGBA"))
    put_nine_slice(atlas, card, (0, 0, card.width - 1, card.height - 1), 32, 0)
    split_card(atlas, 32, 64)
    header = Image.open(ROOT / "art" / "column-header.png").convert("RGBA")
    put_nine_slice(atlas, header, (0, 0, header.width - 1, header.height - 1), 96, 0)
    for i, name in enumerate(PRIORITIES):
        atlas.paste(Image.open(ROOT / "art" / ("priority-%s.png" % name)).convert("RGBA"), (16 * i, 64))
    selected = three_slice(art, SELECTED_TAB)
    normal = three_slice(art, NORMAL_TAB)
    atlas.paste(selected, (0, 32))
    atlas.paste(normal, (16, 32))
    atlas.paste(shade(normal, 1.25), (32, 32))
    atlas.paste(shade(normal, 0.8), (48, 32))
    atlas.paste(cog(), (64, 32))
    purple = hue_shift(normal, PURPLE_HUE)
    atlas.paste(purple, (80, 32))
    atlas.paste(shade(purple, 1.25, outline=hue_shift_color(ORANGE_OUTLINE, PURPLE_HUE)), (96, 32))
    atlas.paste(shade(purple, 0.8, outline=hue_shift_color(ORANGE_OUTLINE, PURPLE_HUE)), (112, 32))
    print("purple label", hex_rgb(hue_shift_color(ORANGE_TEXT, PURPLE_HUE)), "shadow",
          hex_rgb(hue_shift_color(ORANGE_OUTLINE, PURPLE_HUE)), "hover label",
          hex_rgb(hue_shift_color(ORANGE_HOVER_TEXT, PURPLE_HUE)))

    ATLAS.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(ATLAS)
    print("wrote", ATLAS)


def half(image):
    """Samples every other pixel; unlike a resize this keeps an odd-sized mockup's last row."""
    out = Image.new("RGBA", ((image.width + 1) // 2, (image.height + 1) // 2))
    for y in range(out.height):
        for x in range(out.width):
            out.putpixel((x, y), image.getpixel((2 * x, 2 * y)))
    return out


def split_card(atlas, fill_u, border_u):
    """Moves the card slice's bevel from fill_u to border_u, brightened for tinting; the outline stays dark."""
    for y in range(17):
        for x in range(17):
            r, g, b, a = atlas.getpixel((fill_u + x, y))
            if not a or (r, g, b) in CARD_FILL:
                continue
            atlas.putpixel((fill_u + x, y), (0, 0, 0, 0))
            if (r, g, b) != CARD_OUTLINE:
                r, g, b = (min(255, round(c * 255 / CARD_BORDER_LIGHT)) for c in (r, g, b))
            atlas.putpixel((border_u + x, y), (r, g, b, a))


def crop(art, x0, y0, x1, y1):
    """Crop with inclusive art-pixel bounds."""
    return art.crop((x0, y0, x1 + 1, y1 + 1))


def put_nine_slice(atlas, art, box, ax, ay):
    x0, y0, x1, y1 = box
    mid_x, mid_y = (x0 + x1) // 2, (y0 + y1) // 2
    atlas.paste(crop(art, x0, y0, x0 + 7, y0 + 7), (ax, ay))
    atlas.paste(crop(art, x1 - 7, y0, x1, y0 + 7), (ax + 9, ay))
    atlas.paste(crop(art, x0, y1 - 7, x0 + 7, y1), (ax, ay + 9))
    atlas.paste(crop(art, x1 - 7, y1 - 7, x1, y1), (ax + 9, ay + 9))
    atlas.paste(crop(art, mid_x, y0, mid_x, y0 + 7), (ax + 8, ay))
    atlas.paste(crop(art, mid_x, y1 - 7, mid_x, y1), (ax + 8, ay + 9))
    atlas.paste(crop(art, x0, mid_y, x0 + 7, mid_y), (ax, ay + 8))
    atlas.paste(crop(art, x1 - 7, mid_y, x1, mid_y), (ax + 9, ay + 8))
    atlas.paste(crop(art, mid_x, mid_y, mid_x, mid_y), (ax + 8, ay + 8))


def three_slice(art, box):
    x0, y0, x1, y1 = box
    piece = Image.new("RGBA", (13, y1 - y0 + 1), (0, 0, 0, 0))
    piece.paste(crop(art, x0, y0, x0 + 5, y1), (0, 0))
    piece.paste(crop(art, x0 + 5, y0, x0 + 5, y1), (6, 0))  # fill column, left of any label text
    piece.paste(crop(art, x1 - 5, y0, x1, y1), (7, 0))
    return piece


def hue_shift_color(rgb, hue):
    h, s, v = colorsys.rgb_to_hsv(*(c / 255 for c in rgb))
    return tuple(int(c * 255) for c in colorsys.hsv_to_rgb(hue / 360, s, v))


def hue_shift(image, hue):
    """Same brightness and saturation per pixel, new hue: keeps the art's shading structure."""
    out = image.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]
            if a:
                pixels[x, y] = hue_shift_color((r, g, b), hue) + (a,)
    return out


def hex_rgb(rgb):
    return "0x%02X%02X%02X" % rgb


def shade(image, factor, outline=ORANGE_OUTLINE):
    out = image.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]
            if a and (r, g, b) != outline:  # keep the dark outline
                pixels[x, y] = tuple(min(255, int(c * factor)) for c in (r, g, b)) + (a,)
    return out


def cog():
    """The gear in the tab's light text color, with a dark drop shadow down and right, like the tab labels."""
    size = len(COG)
    glyph = Image.new("RGBA", (size + 1, size + 1), (0, 0, 0, 0))
    pixels = glyph.load()
    for y, row in enumerate(COG):
        for x, cell in enumerate(row):
            if cell == "#":
                pixels[x + 1, y + 1] = COG_SHADOW
    for y, row in enumerate(COG):
        for x, cell in enumerate(row):
            if cell == "#":
                pixels[x, y] = COG_LIGHT
    return glyph


if __name__ == "__main__":
    main()
