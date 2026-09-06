package com.teslaowls.teslaled.model;

import java.util.List;

public class PanelMessage {

    public static final String CATEGORY_GREETINGS = "Greetings";
    public static final String CATEGORY_COURTESY = "Courtesy";
    public static final String CATEGORY_TRAFFIC_SAFETY = "Traffic-safety";
    public static final String CATEGORY_FUN = "Fun";
    public static final String CATEGORY_CUSTOM = "Custom";

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

    public PanelMessage(String id, String label, String category, String language,
                         List<Frame> frames, boolean builtIn) {
        this.id = id;
        this.label = label;
        this.category = category;
        this.language = language;
        this.frames = frames;
        this.builtIn = builtIn;
    }
}
