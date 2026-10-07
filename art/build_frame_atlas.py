"""Builds src/main/resources/assets/gtnhkanban/textures/gui/frame.png from art/frame-mockup.png.

The mockup is pixel art drawn at 2 image pixels per art pixel. This script cuts it into pieces the mod stretches at
runtime, so panels and tabs can be any size while staying pixel-exact:

  (0, 0)    panel 9-slice source, 17x17: 8x8 corners, 1px edges, 1px centre
  (0, 32)   tab, selected (teal), 13x21: 6px left cap, 1px fill, 6px right cap
  (16, 32)  tab, normal (orange)
  (32, 32)  tab, hover (placeholder: normal, lightened)
  (48, 32)  tab, pressed (placeholder: normal, darkened)
  (64, 32)  cog glyph, 8x9, transparent background

Usage (from the repository root, needs Pillow):  python art/build_frame_atlas.py
To restyle hover or pressed tabs, replace the generated pieces here with hand-drawn ones.
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
MOCKUP = ROOT / "art" / "frame-mockup.png"
ATLAS = ROOT / "src" / "main" / "resources" / "assets" / "gtnhkanban" / "textures" / "gui" / "frame.png"

# Art-pixel coordinates in the mockup (inclusive).
PANEL = (8, 40, 387, 288)
SELECTED_TAB = (32, 19, 95, 39)
NORMAL_TAB = (104, 19, 167, 39)
COG_GLYPH = (363, 28, 370, 36)
COG_TAB_FILL = (116, 72, 6)


def main():
    art = Image.open(MOCKUP).convert("RGBA")
    art = art.resize((art.width // 2, art.height // 2), Image.NEAREST)
    atlas = Image.new("RGBA", (256, 256), (0, 0, 0, 0))

    put_nine_slice(atlas, art, PANEL, 0, 0)
    selected = three_slice(art, SELECTED_TAB)
    normal = three_slice(art, NORMAL_TAB)
    atlas.paste(selected, (0, 32))
    atlas.paste(normal, (16, 32))
    atlas.paste(shade(normal, 1.25), (32, 32))
    atlas.paste(shade(normal, 0.8), (48, 32))
    atlas.paste(cog(art), (64, 32))

    ATLAS.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(ATLAS)
    print("wrote", ATLAS)


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


def shade(image, factor):
    out = image.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]
            if a and (r, g, b) != (44, 26, 2):  # keep the dark outline
                pixels[x, y] = tuple(min(255, int(c * factor)) for c in (r, g, b)) + (a,)
    return out


def cog(art):
    glyph = crop(art, *COG_GLYPH)
    pixels = glyph.load()
    for y in range(glyph.height):
        for x in range(glyph.width):
            if pixels[x, y][:3] == COG_TAB_FILL:
                pixels[x, y] = (0, 0, 0, 0)
    return glyph


if __name__ == "__main__":
    main()
