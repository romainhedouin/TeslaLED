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

    /**
     * @param gammaFactor power-curve adjustment applied per RGB channel
     *                    before encoding: value' = 255*(value/255)^(1/gammaFactor).
     *                    1.0 leaves colors unchanged; >1.0 brightens
     *                    midtones, <1.0 darkens them - some emoji are hard
     *                    to tell apart on the actual LED panel at full
     *                    brightness, so this is tuned low by default.
     */
    public static byte[] render(String emoji, float gammaFactor) {
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
            pixels[i] = applyGamma(pixels[i], gammaFactor);
        }
        return PpmCodec.encode(pixels, width, height);
    }

    private static int applyGamma(int argb, float gammaFactor) {
        int r = gammaChannel((argb >> 16) & 0xFF, gammaFactor);
        int g = gammaChannel((argb >> 8) & 0xFF, gammaFactor);
        int b = gammaChannel(argb & 0xFF, gammaFactor);
        return (r << 16) | (g << 8) | b;
    }

    private static int gammaChannel(int value, float gammaFactor) {
        double normalized = value / 255.0;
        double adjusted = Math.pow(normalized, 1.0 / gammaFactor);
        int result = (int) Math.round(adjusted * 255);
        return Math.max(0, Math.min(255, result));
    }
}
