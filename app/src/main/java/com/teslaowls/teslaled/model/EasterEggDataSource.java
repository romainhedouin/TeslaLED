package com.teslaowls.teslaled.model;

import com.teslaowls.teslaled.render.EmojiRenderer;

import java.util.List;

/**
 * Hidden feature triggered by long-pressing the Emoji category chip: cycles
 * through a fixed, fun set of emoji, one per LiveDataSource tick, until the
 * user hits Stop - the same re-render-and-resend loop MessageSender already
 * drives for Data's Time/Speed sources, just stepping through a fixed list
 * instead of sampling a live value.
 */
public class EasterEggDataSource implements LiveDataSource {

    private final List<String> emojis;
    private final float gamma;
    private int index = 0;

    public EasterEggDataSource(List<String> emojis, float gamma) {
        this.emojis = emojis;
        this.gamma = gamma;
    }

    @Override
    public byte[] renderFrame() {
        String emoji = emojis.get(index);
        index = (index + 1) % emojis.size();
        return EmojiRenderer.render(emoji, gamma);
    }
}
