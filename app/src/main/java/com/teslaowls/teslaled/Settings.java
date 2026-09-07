package com.teslaowls.teslaled;

import android.content.Context;
import android.content.SharedPreferences;

import com.teslaowls.teslaled.model.PanelMessage;

/** Small persisted app preferences. */
public class Settings {

    private static final String PREFS_NAME = "settings";
    private static final String KEY_BRIGHTNESS = "brightness";
    private static final String KEY_DISPLAY_LANGUAGE = "display_language";
    private static final String KEY_EMOJI_DURATION_SECONDS = "emoji_duration_seconds";
    private static final int DEFAULT_BRIGHTNESS = 90;
    private static final String DEFAULT_DISPLAY_LANGUAGE = PanelMessage.LANGUAGE_EN;
    private static final int DEFAULT_EMOJI_DURATION_SECONDS = 3;
    public static final int MIN_EMOJI_DURATION_SECONDS = 1;
    public static final int MAX_EMOJI_DURATION_SECONDS = 30;

    private final SharedPreferences prefs;

    public Settings(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 1-100. */
    public int getBrightness() {
        return prefs.getInt(KEY_BRIGHTNESS, DEFAULT_BRIGHTNESS);
    }

    public void setBrightness(int brightness) {
        prefs.edit().putInt(KEY_BRIGHTNESS, brightness).apply();
    }

    /**
     * Language used for labels on dynamically-generated content (e.g. the
     * "Data" category's live displays), as opposed to the language filter
     * used to browse the fixed message library - "fr" or "en".
     */
    public String getDisplayLanguage() {
        return prefs.getString(KEY_DISPLAY_LANGUAGE, DEFAULT_DISPLAY_LANGUAGE);
    }

    public void setDisplayLanguage(String language) {
        prefs.edit().putString(KEY_DISPLAY_LANGUAGE, language).apply();
    }

    /** How long a picked emoji stays on the panel, in seconds. */
    public int getEmojiDurationSeconds() {
        return prefs.getInt(KEY_EMOJI_DURATION_SECONDS, DEFAULT_EMOJI_DURATION_SECONDS);
    }

    public void setEmojiDurationSeconds(int seconds) {
        int clamped = Math.max(MIN_EMOJI_DURATION_SECONDS, Math.min(MAX_EMOJI_DURATION_SECONDS, seconds));
        prefs.edit().putInt(KEY_EMOJI_DURATION_SECONDS, clamped).apply();
    }
}
