package com.teslaowls.teslaled.render;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.teslaowls.teslaled.ppm.PpmCodec;

/**
 * Renders a single emoji onto the panel via Android's own text layout
 * (Canvas/Paint) instead of PixelFontRenderer's hand-blitted BDF glyphs -
 * BDF fonts here only carry plain Latin glyphs, not color emoji, so this is
 * the one rendering path in the app that goes through android.graphics
 * rather than a manual bitmap-font blit.
 */
public class EmojiRenderer {

    private static final int BACKGROUND_COLOR = 0xFF000000;

    private EmojiRenderer() {
    }

    public static byte[] render(String emoji) {
        int width = PpmCodec.PANEL_WIDTH;
        int height = PpmCodec.PANEL_HEIGHT;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(BACKGROUND_COLOR);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(height * 0.9f);
        paint.setTextAlign(Paint.Align.CENTER);

        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baselineY = height / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(emoji, width / 2f, baselineY, paint);

        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int i = 0; i < pixels.length; i++) {
            // Bitmap.getPixels() returns ARGB; the panel only wants RGB.
            pixels[i] &= 0xFFFFFF;
        }
        return PpmCodec.encode(pixels, width, height);
    }
}
