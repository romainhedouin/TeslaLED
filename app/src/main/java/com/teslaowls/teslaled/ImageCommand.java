package com.teslaowls.teslaled;

import android.content.res.AssetManager;

import java.io.IOException;
import java.io.InputStream;

public class ImageCommand extends PanelCommand {

    private String imageFile;

    public ImageCommand(String imageFile, int sleepDurationMs) {
        super(sleepDurationMs);

        this.imageFile = imageFile;
    }

    public String getImageName() {
        return imageFile;
    }

    public void setImageName(String imageName) {
        this.imageFile = imageName;
    }

    @Override
    public boolean sendCommand(BluetoothClient bluetoothClient, AssetManager assetManager) {
        try {
            InputStream inputStream = assetManager.open(this.imageFile);
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            inputStream.close();

            return bluetoothClient.sendCommand(BluetoothClient.COMMAND_IMAGE, buffer);
        } catch (IOException e) {
            System.out.println("[-] Couldn't read image asset.");
            e.printStackTrace();
            return false;
        }
    }
}
