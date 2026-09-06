package com.teslaowls.teslaled.render;

import com.teslaowls.teslaled.ppm.PpmCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a long message into word-boundary chunks a driver can actually read
 * as separate static frames - deliberately not scrolling text, which is hard
 * to read from a following car. Each chunk is capped at both a target word
 * count and the panel's pixel width, whichever is hit first, so a handful of
 * long words never gets crushed together or clipped off-panel.
 */
public class TextChunker {

    private static final int DEFAULT_MAX_WORDS_PER_CHUNK = 5;

    private TextChunker() {
    }

    public static List<String> chunk(String text, PixelFontRenderer renderer) {
        return chunk(text, renderer, DEFAULT_MAX_WORDS_PER_CHUNK);
    }

    public static List<String> chunk(String text, PixelFontRenderer renderer, int maxWordsPerChunk) {
        List<String> chunks = new ArrayList<>();
        String[] words = text.trim().split("\\s+");

        StringBuilder currentChunk = new StringBuilder();
        int wordsInCurrentChunk = 0;

        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = currentChunk.length() == 0 ? word : currentChunk + " " + word;
            boolean tooManyWords = wordsInCurrentChunk >= maxWordsPerChunk;
            boolean tooWide = wordsInCurrentChunk > 0 && renderer.measureWidth(candidate) > PpmCodec.PANEL_WIDTH;

            if (tooManyWords || tooWide) {
                chunks.add(currentChunk.toString());
                currentChunk = new StringBuilder(word);
                wordsInCurrentChunk = 1;
            } else {
                currentChunk = new StringBuilder(candidate);
                wordsInCurrentChunk++;
            }
        }
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }
        return chunks;
    }
}
