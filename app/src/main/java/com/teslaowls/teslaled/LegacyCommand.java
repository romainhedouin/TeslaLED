package com.teslaowls.teslaled;

import android.content.res.AssetManager;

public class LegacyCommand extends PanelCommand {

    private String scriptName;

    public LegacyCommand(String scriptName, int sleepDurationMs) {
        super(sleepDurationMs);

        this.scriptName = scriptName;
    }

    public String getScriptName() {
        return scriptName;
    }

    public void setScriptName(String scriptName) {
        this.scriptName = scriptName;
    }

    @Override
    public boolean sendCommand(BluetoothClient bluetoothClient, AssetManager assetManager) {
        return bluetoothClient.sendCommand(BluetoothClient.COMMAND_LEGACY, this.scriptName.getBytes());
    }
}
