package com.teslaowls.teslaled.data;

import com.teslaowls.teslaled.Settings;
import com.teslaowls.teslaled.model.LiveDataSource;
import com.teslaowls.teslaled.model.PanelMessage;
import com.teslaowls.teslaled.render.PixelFontRenderer;

public class SpeedDataSource implements LiveDataSource {

    private static final int WHITE = 0xFFFFFF;
    private static final int BLACK = 0x000000;

    private final PixelFontRenderer renderer;
    private final Settings settings;
    private final LocationSpeedProvider speedProvider;

    public SpeedDataSource(PixelFontRenderer renderer, Settings settings, LocationSpeedProvider speedProvider) {
        this.renderer = renderer;
        this.settings = settings;
        this.speedProvider = speedProvider;
    }

    @Override
    public byte[] renderFrame() {
        String label = PanelMessage.LANGUAGE_FR.equals(settings.getDisplayLanguage()) ? "Vitesse:" : "Speed:";
        String value = speedProvider.hasFix() ? Math.round(speedProvider.getSpeedKmh()) + " km/h" : "--";
        return renderer.renderTwoLine(label, value, WHITE, BLACK);
    }
}
