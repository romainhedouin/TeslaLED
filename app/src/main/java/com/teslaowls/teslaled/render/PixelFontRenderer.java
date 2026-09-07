package com.teslaowls.teslaled.render;

import android.content.res.AssetManager;

import com.teslaowls.teslaled.ppm.PpmCodec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders text onto the 64x32 panel using a bitmap (BDF) font, one glyph
 * pixel blit at a time - no android.graphics.Canvas/Paint anywhere in this
 * path, so there's no font hinting or anti-aliasing to fight. Every output
 * pixel is exactly the text color or exactly the background color.
 *
 * BDF is a plain-text format: https://en.wikipedia.org/wiki/Glyph_Bitmap_Distribution_Format
 * We only parse the handful of fields needed to blit a fixed-width bitmap
 * font, not the full spec.
 */
public class PixelFontRenderer {

    private static class Glyph {
        int bbw, bbh, bbxoff, bbyoff;
        int dwidth;
        int[] rows; // one int per bitmap row, bit (bbw-1-c) set means pixel c is "on"
    }

    private final Map<Integer, Glyph> glyphsByCodepoint = new HashMap<>();
    private int fontAscent;
    private int fontDescent;

    public PixelFontRenderer(AssetManager assetManager, String bdfAssetPath) throws IOException {
        try (InputStream in = assetManager.open(bdfAssetPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII))) {
            parse(reader);
        }
    }

    private void parse(BufferedReader reader) throws IOException {
        String line;
        Glyph current = null;
        int bitmapRowsRemaining = 0;

        while ((line = reader.readLine()) != null) {
            if (line.startsWith("FONTBOUNDINGBOX")) {
                String[] parts = line.split("\\s+");
                int fontBbHeight = Integer.parseInt(parts[2]);
                int fontBbYoff = Integer.parseInt(parts[4]);
                this.fontDescent = -fontBbYoff;
                this.fontAscent = fontBbHeight - this.fontDescent;
            } else if (line.startsWith("STARTCHAR")) {
                current = new Glyph();
            } else if (line.startsWith("ENCODING")) {
                int codepoint = Integer.parseInt(line.split("\\s+")[1]);
                if (current != null) {
                    glyphsByCodepoint.put(codepoint, current);
                }
            } else if (line.startsWith("DWIDTH")) {
                current.dwidth = Integer.parseInt(line.split("\\s+")[1]);
            } else if (line.startsWith("BBX")) {
                String[] parts = line.split("\\s+");
                current.bbw = Integer.parseInt(parts[1]);
                current.bbh = Integer.parseInt(parts[2]);
                current.bbxoff = Integer.parseInt(parts[3]);
                current.bbyoff = Integer.parseInt(parts[4]);
                current.rows = new int[current.bbh];
            } else if (line.equals("BITMAP")) {
                bitmapRowsRemaining = current.bbh;
            } else if (bitmapRowsRemaining > 0) {
                int row = current.bbh - bitmapRowsRemaining;
                current.rows[row] = (int) Long.parseLong(line.trim(), 16);
                bitmapRowsRemaining--;
            } else if (line.equals("ENDCHAR")) {
                current = null;
            }
        }
    }

    /** Total on-panel pixel width text would occupy, for chunking/centering decisions. */
    public int measureWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            Glyph glyph = glyphsByCodepoint.get((int) text.charAt(i));
            if (glyph != null) {
                width += glyph.dwidth;
            }
        }
        return width;
    }

    /**
     * Renders text centered on a PANEL_WIDTH x PANEL_HEIGHT canvas, vertically
     * centered on the font's own ascent/descent, and returns it as PPM bytes
     * ready to send to the panel.
     */
    public byte[] renderText(String text, int textColor, int backgroundColor) {
        int width = PpmCodec.PANEL_WIDTH;
        int height = PpmCodec.PANEL_HEIGHT;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, backgroundColor);
        blitLine(pixels, width, text, textColor, 0, height);
        return PpmCodec.encode(pixels, width, height);
    }

    /**
     * Renders two independent lines, each centered within its own half of
     * the panel (rows [0,16) and [16,32)) - used for "label on top, live
     * value on bottom" data displays (current time, current speed, etc).
     */
    public byte[] renderTwoLine(String topText, String bottomText, int textColor, int backgroundColor) {
        int width = PpmCodec.PANEL_WIDTH;
        int height = PpmCodec.PANEL_HEIGHT;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, backgroundColor);
        int halfHeight = height / 2;
        blitLine(pixels, width, topText, textColor, 0, halfHeight);
        blitLine(pixels, width, bottomText, textColor, halfHeight, halfHeight);
        return PpmCodec.encode(pixels, width, height);
    }

    /** Blits one line of text horizontally centered, vertically centered within [bandTop, bandTop+bandHeight). */
    private void blitLine(int[] pixels, int width, String text, int textColor, int bandTop, int bandHeight) {
        int textWidth = measureWidth(text);
        int startX = Math.max(0, (width - textWidth) / 2);
        int baselineFromTop = bandTop + (bandHeight - (fontAscent + fontDescent)) / 2 + fontAscent;

        int penX = startX;
        for (int i = 0; i < text.length(); i++) {
            Glyph glyph = glyphsByCodepoint.get((int) text.charAt(i));
            if (glyph == null) {
                continue;
            }
            for (int row = 0; row < glyph.bbh; row++) {
                int heightAboveBaseline = glyph.bbyoff + (glyph.bbh - 1 - row);
                int y = baselineFromTop - heightAboveBaseline;
                if (y < bandTop || y >= bandTop + bandHeight) {
                    continue;
                }
                int rowBits = glyph.rows[row];
                // BDF pads each bitmap row to a byte boundary, so the MSB
                // corresponds to column 0 only once we account for that
                // padding - not simply bbw-1.
                int bytesPerRow = (glyph.bbw + 7) / 8;
                int bitsPerRow = bytesPerRow * 8;
                for (int col = 0; col < glyph.bbw; col++) {
                    boolean on = ((rowBits >> (bitsPerRow - 1 - col)) & 1) != 0;
                    if (!on) {
                        continue;
                    }
                    int x = penX + glyph.bbxoff + col;
                    if (x < 0 || x >= width) {
                        continue;
                    }
                    pixels[y * width + x] = textColor;
                }
            }
            penX += glyph.dwidth;
        }
    }
}
