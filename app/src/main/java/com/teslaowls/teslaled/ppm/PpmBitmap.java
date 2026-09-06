package com.teslaowls.teslaled.ppm;

import android.graphics.Bitmap;

/**
 * Converts a panel PPM into an on-screen Bitmap, upscaled with nearest-
 * neighbor replication (not smoothing) so thumbnails/previews stay crisp
 * and pixelated rather than blurry - matching what the actual panel looks
 * like, not a smoothed guess at it.
 */
public class PpmBitmap {

    private PpmBitmap() {
    }

    public static Bitmap toBitmap(byte[] ppmBytes, int scale) {
        PpmCodec.Decoded decoded = PpmCodec.decode(ppmBytes);
        int scaledWidth = decoded.width * scale;
        int scaledHeight = decoded.height * scale;
        int[] scaledPixels = new int[scaledWidth * scaledHeight];
        for (int y = 0; y < scaledHeight; y++) {
            int srcY = y / scale;
            for (int x = 0; x < scaledWidth; x++) {
                int srcX = x / scale;
                scaledPixels[y * scaledWidth + x] = 0xFF000000 | decoded.rgbPixels[srcY * decoded.width + srcX];
            }
        }
        return Bitmap.createBitmap(scaledPixels, scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
    }
}
