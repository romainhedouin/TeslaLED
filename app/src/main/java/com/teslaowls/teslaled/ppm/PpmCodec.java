package com.teslaowls.teslaled.ppm;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Encodes/decodes the raw P6 (binary) PPM format the panel expects: an ASCII
 * header ("P6\n<width> <height>\n255\n") followed by width*height RGB triplets,
 * row-major, no padding. See https://en.wikipedia.org/wiki/Netpbm for the spec.
 */
public class PpmCodec {

    public static final int PANEL_WIDTH = 64;
    public static final int PANEL_HEIGHT = 32;

    private PpmCodec() {
    }

    /**
     * @param rgbPixels row-major array of 0xRRGGBB ints, length width*height
     */
    public static byte[] encode(int[] rgbPixels, int width, int height) {
        if (rgbPixels.length != width * height) {
            throw new IllegalArgumentException("pixel array length must be width*height");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(rgbPixels.length * 3 + 32);
        String header = "P6\n" + width + " " + height + "\n255\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII), 0, header.length());
        for (int pixel : rgbPixels) {
            out.write((pixel >> 16) & 0xFF);
            out.write((pixel >> 8) & 0xFF);
            out.write(pixel & 0xFF);
        }
        return out.toByteArray();
    }

    public static class Decoded {
        public final int width;
        public final int height;
        public final int[] rgbPixels;

        Decoded(int width, int height, int[] rgbPixels) {
            this.width = width;
            this.height = height;
            this.rgbPixels = rgbPixels;
        }
    }

    /**
     * Parses a minimal subset of P6 PPM: a single whitespace-separated
     * "P6 <width> <height> <maxval>" token sequence (comment lines starting
     * with '#' are skipped, matching what GIMP's PNM exporter and our own
     * encode() above both produce) followed immediately by raw pixel bytes.
     */
    public static Decoded decode(byte[] ppmBytes) {
        int pos = 0;

        String magic = readToken(ppmBytes, pos);
        pos = skipToken(ppmBytes, pos);
        if (!"P6".equals(magic)) {
            throw new IllegalArgumentException("not a P6 PPM file");
        }

        String widthToken = readToken(ppmBytes, pos);
        pos = skipToken(ppmBytes, pos);
        String heightToken = readToken(ppmBytes, pos);
        pos = skipToken(ppmBytes, pos);
        String maxvalToken = readToken(ppmBytes, pos);
        pos = skipToken(ppmBytes, pos);

        int width = Integer.parseInt(widthToken);
        int height = Integer.parseInt(heightToken);
        int maxval = Integer.parseInt(maxvalToken);
        if (maxval != 255) {
            throw new IllegalArgumentException("only maxval=255 PPMs are supported");
        }

        // Exactly one whitespace byte separates the header from pixel data.
        pos += 1;

        int[] rgbPixels = new int[width * height];
        for (int i = 0; i < rgbPixels.length; i++) {
            int r = ppmBytes[pos++] & 0xFF;
            int g = ppmBytes[pos++] & 0xFF;
            int b = ppmBytes[pos++] & 0xFF;
            rgbPixels[i] = (r << 16) | (g << 8) | b;
        }
        return new Decoded(width, height, rgbPixels);
    }

    private static boolean isWhitespace(byte b) {
        return b == ' ' || b == '\n' || b == '\r' || b == '\t';
    }

    private static int skipWhitespaceAndComments(byte[] data, int pos) {
        while (pos < data.length) {
            if (isWhitespace(data[pos])) {
                pos++;
            } else if (data[pos] == '#') {
                while (pos < data.length && data[pos] != '\n') {
                    pos++;
                }
            } else {
                break;
            }
        }
        return pos;
    }

    private static String readToken(byte[] data, int pos) {
        pos = skipWhitespaceAndComments(data, pos);
        int start = pos;
        while (pos < data.length && !isWhitespace(data[pos])) {
            pos++;
        }
        return new String(data, start, pos - start, StandardCharsets.US_ASCII);
    }

    private static int skipToken(byte[] data, int pos) {
        pos = skipWhitespaceAndComments(data, pos);
        while (pos < data.length && !isWhitespace(data[pos])) {
            pos++;
        }
        return pos;
    }
}
