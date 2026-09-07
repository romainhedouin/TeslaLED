#!/usr/bin/env python3
"""Generates speed-limit sign PPMs for the 64x32 panel:
- European style: red ring, white interior, black number.
- US style: white rectangle, black border, "SPEED LIMIT" header, black number.

Uses the same BDF-glyph-blit approach as the Android app's
PixelFontRenderer, just done offline since these are static/deterministic
assets - no need for on-device generation. Re-run this whenever you want to
add more signs or tweak the look; it's checked into the repo for that.

Usage: python3 generate_speed_signs.py
(paths below assume it's run from within a checkout that also has the
rpi-rgb-led-matrix fonts/ directory two levels up - adjust FONT_DIR if not)
"""

import os

WIDTH = 64
HEIGHT = 32

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
FONT_DIR = os.path.join(SCRIPT_DIR, "fonts")
OUT_DIR = os.path.join(SCRIPT_DIR, "..", "app", "src", "main", "assets")

BLACK = (0, 0, 0)
RED = (200, 0, 0)
WHITE = (255, 255, 255)

CENTER_X = WIDTH // 2
CENTER_Y = HEIGHT // 2


def parse_bdf(path):
    glyphs = {}
    with open(path) as f:
        lines = f.read().splitlines()

    i = 0
    current = None
    while i < len(lines):
        line = lines[i]
        if line.startswith("STARTCHAR"):
            current = {}
        elif line.startswith("ENCODING"):
            current["encoding"] = int(line.split()[1])
        elif line.startswith("DWIDTH"):
            current["dwidth"] = int(line.split()[1])
        elif line.startswith("BBX"):
            parts = line.split()
            current["bbw"] = int(parts[1])
            current["bbh"] = int(parts[2])
            current["bbxoff"] = int(parts[3])
            current["bbyoff"] = int(parts[4])
        elif line.startswith("BITMAP"):
            rows = []
            for _ in range(current["bbh"]):
                i += 1
                rows.append(int(lines[i], 16))
            current["rows"] = rows
        elif line.startswith("ENDCHAR"):
            glyphs[current["encoding"]] = current
            current = None
        i += 1
    return glyphs


def measure_width(text, glyphs):
    return sum(glyphs[ord(c)]["dwidth"] for c in text if ord(c) in glyphs)


def blit_line(pixels, text, glyphs, color, font_ascent, font_descent, band_top, band_height):
    """Draws one horizontally-centered line, vertically centered within [band_top, band_top+band_height)."""
    total_width = measure_width(text, glyphs)
    start_x = max(0, (WIDTH - total_width) // 2)
    baseline_from_top = band_top + (band_height - (font_ascent + font_descent)) // 2 + font_ascent

    pen_x = start_x
    for c in text:
        glyph = glyphs.get(ord(c))
        if glyph is None:
            continue
        bbw, bbh, bbxoff, bbyoff = glyph["bbw"], glyph["bbh"], glyph["bbxoff"], glyph["bbyoff"]
        bytes_per_row = (bbw + 7) // 8
        bits_per_row = bytes_per_row * 8
        for row in range(bbh):
            height_above_baseline = bbyoff + (bbh - 1 - row)
            y = baseline_from_top - height_above_baseline
            if y < band_top or y >= band_top + band_height:
                continue
            row_bits = glyph["rows"][row]
            for col in range(bbw):
                on = (row_bits >> (bits_per_row - 1 - col)) & 1
                if not on:
                    continue
                x = pen_x + bbxoff + col
                if x < 0 or x >= WIDTH:
                    continue
                pixels[y * WIDTH + x] = color
        pen_x += glyph["dwidth"]


def generate_eu_sign(speed, glyphs):
    outer_radius = 16
    border_thickness = 3
    font_ascent, font_descent = 12, 3

    pixels = [BLACK] * (WIDTH * HEIGHT)
    for y in range(HEIGHT):
        for x in range(WIDTH):
            dx = x - CENTER_X + 0.5
            dy = y - CENTER_Y + 0.5
            dist = (dx * dx + dy * dy) ** 0.5
            if dist <= outer_radius:
                pixels[y * WIDTH + x] = WHITE if dist <= outer_radius - border_thickness else RED
    blit_line(pixels, str(speed), glyphs, BLACK, font_ascent, font_descent, 0, HEIGHT)
    return pixels


def generate_us_sign(speed, header_glyphs, number_glyphs):
    # Real US speed-limit signs are portrait (taller than wide) - inset the
    # rectangle horizontally instead of spanning the full 64px width, so it
    # isn't a wide short banner on this landscape panel.
    margin_x = 12
    border = 2
    line_height = 6
    header_ascent, header_descent = 5, 1
    number_ascent, number_descent = 12, 3

    pixels = [BLACK] * (WIDTH * HEIGHT)
    for y in range(HEIGHT):
        for x in range(margin_x, WIDTH - margin_x):
            if border <= y < HEIGHT - border:
                pixels[y * WIDTH + x] = WHITE
    blit_line(pixels, "SPEED", header_glyphs, BLACK, header_ascent, header_descent, border, line_height)
    blit_line(pixels, "LIMIT", header_glyphs, BLACK, header_ascent, header_descent, border + line_height, line_height)
    blit_line(pixels, str(speed), number_glyphs, BLACK, number_ascent, number_descent,
              border + 2 * line_height, HEIGHT - 2 * border - 2 * line_height)
    return pixels


def write_ppm(path, pixels):
    with open(path, "wb") as f:
        header = "P6\n%d %d\n255\n" % (WIDTH, HEIGHT)
        f.write(header.encode("ascii"))
        for (r, g, b) in pixels:
            f.write(bytes([r, g, b]))


if __name__ == "__main__":
    number_glyphs = parse_bdf(os.path.join(FONT_DIR, "9x15B.bdf"))
    header_glyphs = parse_bdf(os.path.join(FONT_DIR, "tom-thumb.bdf"))

    for speed in [30, 50, 70, 80, 90, 110, 130]:
        pixels = generate_eu_sign(speed, number_glyphs)
        out_path = os.path.join(OUT_DIR, "speed_eu_%d.ppm" % speed)
        write_ppm(out_path, pixels)
        print("wrote", out_path)

    for speed in range(10, 86, 5):
        pixels = generate_us_sign(speed, header_glyphs, number_glyphs)
        out_path = os.path.join(OUT_DIR, "speed_us_%d.ppm" % speed)
        write_ppm(out_path, pixels)
        print("wrote", out_path)
