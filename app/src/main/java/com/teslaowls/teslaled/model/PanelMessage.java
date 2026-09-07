package com.teslaowls.teslaled.model;

import java.util.Collections;
import java.util.List;

public class PanelMessage {

    public static final String CATEGORY_GREETINGS = "Greetings";
    public static final String CATEGORY_COURTESY = "Courtesy";
    public static final String CATEGORY_TRAFFIC_SAFETY = "Traffic-safety";
    public static final String CATEGORY_FUN = "Fun";
    public static final String CATEGORY_DATA = "Data";
    public static final String CATEGORY_CUSTOM = "Custom";
    public static final String CATEGORY_EMOJI = "Emoji";

    /** Language-neutral, e.g. an emoji or a universally-understood image. */
    public static final String LANGUAGE_NONE = "";
    public static final String LANGUAGE_FR = "fr";
    public static final String LANGUAGE_EN = "en";

    public final String id;
    public final String label;
    public final String category;
    public final String language;
    public final List<Frame> frames;
    public final boolean builtIn;
    /** Non-null means this is a continuously-updating stream, not a fixed sequence - see LiveDataSource. */
    public final LiveDataSource liveDataSource;

    public PanelMessage(String id, String label, String category, String language,
                         List<Frame> frames, boolean builtIn) {
        this(id, label, category, language, frames, builtIn, null);
    }

    /** For a live message, pass a representative one-off preview frame (e.g. current value at creation time) for thumbnails. */
    public PanelMessage(String id, String label, String category, String language,
                         Frame previewFrame, boolean builtIn, LiveDataSource liveDataSource) {
        this(id, label, category, language, Collections.singletonList(previewFrame), builtIn, liveDataSource);
    }

    private PanelMessage(String id, String label, String category, String language,
                         List<Frame> frames, boolean builtIn, LiveDataSource liveDataSource) {
        this.id = id;
        this.label = label;
        this.category = category;
        this.language = language;
        this.frames = frames;
        this.builtIn = builtIn;
        this.liveDataSource = liveDataSource;
    }
}
