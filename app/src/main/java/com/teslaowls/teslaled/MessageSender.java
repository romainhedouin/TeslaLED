package com.teslaowls.teslaled;

import android.os.Handler;
import android.os.Looper;

import com.teslaowls.teslaled.model.Frame;
import com.teslaowls.teslaled.model.PanelMessage;

/**
 * Sends a PanelMessage's frames in order, timing each one client-side (the
 * app is the sole thing controlling display duration/kill, so it always
 * knows authoritatively what's currently on the panel - see getCurrentMessage()).
 */
public class MessageSender {

    public interface Listener {
        void onMessageStarted(PanelMessage message);

        void onMessageFinished(PanelMessage message);
    }

    private final BluetoothClient bluetoothClient;
    private final Settings settings;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Listener listener;
    private PanelMessage currentMessage;

    public MessageSender(BluetoothClient bluetoothClient, Settings settings) {
        this.bluetoothClient = bluetoothClient;
        this.settings = settings;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public PanelMessage getCurrentMessage() {
        return currentMessage;
    }

    /** @return true if the first frame sent successfully. */
    public boolean send(PanelMessage message) {
        bluetoothClient.findDevice();
        if (!bluetoothClient.isConnected()) {
            bluetoothClient.connect();
        }
        if (!bluetoothClient.isConnected()) {
            return false;
        }

        // Cheap enough to send before every message rather than tracking
        // whether the Pi's already up to date - keeps this stateless.
        bluetoothClient.sendCommand(BluetoothClient.COMMAND_SET_BRIGHTNESS, new byte[]{(byte) settings.getBrightness()});

        Frame firstFrame = message.frames.get(0);
        boolean success = bluetoothClient.sendCommand(BluetoothClient.COMMAND_IMAGE, firstFrame.ppmBytes);
        if (!success) {
            return false;
        }

        currentMessage = message;
        if (listener != null) {
            listener.onMessageStarted(message);
        }
        handler.postDelayed(() -> sendFrame(message, 1), firstFrame.durationMs);
        return true;
    }

    private void sendFrame(PanelMessage message, int frameIndex) {
        if (frameIndex >= message.frames.size()) {
            bluetoothClient.sendCommand(BluetoothClient.COMMAND_KILL, new byte[0]);
            finish(message);
            return;
        }

        Frame frame = message.frames.get(frameIndex);
        boolean success = bluetoothClient.sendCommand(BluetoothClient.COMMAND_IMAGE, frame.ppmBytes);
        if (!success) {
            finish(message);
            return;
        }
        handler.postDelayed(() -> sendFrame(message, frameIndex + 1), frame.durationMs);
    }

    private void finish(PanelMessage message) {
        if (currentMessage == message) {
            currentMessage = null;
            if (listener != null) {
                listener.onMessageFinished(message);
            }
        }
    }
}
