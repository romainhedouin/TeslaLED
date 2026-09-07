package com.teslaowls.teslaled.data;

import com.teslaowls.teslaled.Settings;
import com.teslaowls.teslaled.model.LiveDataSource;
import com.teslaowls.teslaled.model.PanelMessage;
import com.teslaowls.teslaled.render.PixelFontRenderer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TimeDataSource implements LiveDataSource {

    private static final int WHITE = 0xFFFFFF;
    private static final int BLACK = 0x000000;

    private final PixelFontRenderer renderer;
    private final Settings settings;
    private final SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public TimeDataSource(PixelFontRenderer renderer, Settings settings) {
        this.renderer = renderer;
        this.settings = settings;
    }

    @Override
    public byte[] renderFrame() {
        String label = PanelMessage.LANGUAGE_FR.equals(settings.getDisplayLanguage()) ? "Heure:" : "Time:";
        String value = format.format(new Date());
        return renderer.renderTwoLine(label, value, WHITE, BLACK);
    }
}
