package com.teslaowls.teslaled;

import android.content.res.AssetManager;

public abstract class PanelCommand {

    private final int sleepDurationMs;

    public PanelCommand(int sleepDurationMs) {
        this.sleepDurationMs = sleepDurationMs;
    }

    public abstract boolean sendCommand(BluetoothClient bluetoothClient, AssetManager assetManager);

    public void kill(BluetoothClient bluetoothClient) {
        bluetoothClient.sendCommand(BluetoothClient.COMMAND_KILL, new byte[0]);
    }

    public int getSleepDurationMs() {
        return this.sleepDurationMs;
    }
}
