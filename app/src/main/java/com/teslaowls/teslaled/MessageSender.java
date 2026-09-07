package com.teslaowls.teslaled;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import com.teslaowls.teslaled.model.Frame;
import com.teslaowls.teslaled.model.PanelMessage;

/**
 * Sends a PanelMessage's frames in order, timing each one client-side (the
 * app is the sole thing controlling display duration/kill, so it always
 * knows authoritatively what's currently on the panel - see getCurrentMessage()).
 * A message with a liveDataSource is instead re-rendered and re-sent every
 * second until stop() is called, rather than following a fixed sequence.
 *
 * All BluetoothClient I/O (which blocks - a socket connect can take several
 * seconds) runs on a dedicated background thread, never the caller's thread.
 * currentMessage/generation are only ever touched on that background thread,
 * so send()/stop() just post a request to it rather than needing locks.
 * Listener callbacks are posted back to the main thread since they touch UI.
 */
public class MessageSender {

    private static final long LIVE_TICK_MS = 1000;

    public interface Listener {
        void onMessageStarted(PanelMessage message);

        /** Called with every frame actually sent (the first one, and every subsequent one - including live-stream ticks). */
        void onFrameUpdated(PanelMessage message, byte[] ppmBytes);

        void onMessageFinished(PanelMessage message);

        void onSendFailed(PanelMessage message);
    }

    private final BluetoothClient bluetoothClient;
    private final Settings settings;
    private final HandlerThread ioThread = new HandlerThread("MessageSender-io");
    private final Handler ioHandler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Listener listener;

    // Only ever read/written on ioThread.
    private PanelMessage currentMessage;
    private int generation = 0;

    public MessageSender(BluetoothClient bluetoothClient, Settings settings) {
        this.bluetoothClient = bluetoothClient;
        this.settings = settings;
        ioThread.start();
        ioHandler = new Handler(ioThread.getLooper());
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void send(PanelMessage message) {
        ioHandler.post(() -> doSend(message));
    }

    /** Immediately stops whatever's currently showing (finite sequence or live stream) and kills the panel. */
    public void stop() {
        ioHandler.post(this::doStop);
    }

    /** Pushes the current brightness setting immediately if already connected, rather than waiting for the next send(). */
    public void syncBrightness() {
        ioHandler.post(() -> {
            if (bluetoothClient.isConnected()) {
                bluetoothClient.sendCommand(BluetoothClient.COMMAND_SET_BRIGHTNESS, new byte[]{(byte) settings.getBrightness()});
            }
        });
    }

    private void doSend(PanelMessage message) {
        if (!connectAndSyncBrightness()) {
            notifyFailed(message);
            return;
        }

        if (message.liveDataSource != null) {
            doStartLive(message);
            return;
        }

        Frame firstFrame = message.frames.get(0);
        boolean success = bluetoothClient.sendCommand(BluetoothClient.COMMAND_IMAGE, firstFrame.ppmBytes);
        if (!success) {
            notifyFailed(message);
            return;
        }

        int myGeneration = ++generation;
        currentMessage = message;
        notifyStarted(message);
        notifyFrame(message, firstFrame.ppmBytes);
        ioHandler.postDelayed(() -> sendFrame(message, 1, myGeneration), firstFrame.durationMs);
    }

    private void doStartLive(PanelMessage message) {
        byte[] firstFrame = message.liveDataSource.renderFrame();
        boolean success = bluetoothClient.sendCommand(BluetoothClient.COMMAND_IMAGE, firstFrame);
        if (!success) {
            notifyFailed(message);
            return;
        }

        int myGeneration = ++generation;
        currentMessage = message;
        notifyStarted(message);
        notifyFrame(message, firstFrame);
        ioHandler.postDelayed(() -> tickLive(message, myGeneration), LIVE_TICK_MS);
    }

    private void doStop() {
        generation++;
        if (currentMessage == null) {
            return;
        }
        bluetoothClient.sendCommand(BluetoothClient.COMMAND_KILL, new byte[0]);
        PanelMessage finished = currentMessage;
        currentMessage = null;
        notifyFinished(finished);
    }

    private boolean connectAndSyncBrightness() {
        bluetoothClient.findDevice();
        if (!bluetoothClient.isConnected()) {
            bluetoothClient.connect();
        }
        if (!bluetoothClient.isConnected()) {
            return false;
        }
        // Cheap enough to send before every message rather than tracking
        // whether the Pi's already up to date - keeps this stateless. Its
        // result must be checked: a failure here can mean the socket was
        // just discarded as stale, and the next sendCommand() would NPE
        // on a null socket rather than fail gracefully.
        return bluetoothClient.sendCommand(BluetoothClient.COMMAND_SET_BRIGHTNESS, new byte[]{(byte) settings.getBrightness()});
    }

    private void sendFrame(PanelMessage message, int frameIndex, int myGeneration) {
        if (myGeneration != generation) {
            return;
        }
        if (frameIndex >= message.frames.size()) {
            bluetoothClient.sendCommand(BluetoothClient.COMMAND_KILL, new byte[0]);
            PanelMessage finished = currentMessage;
            currentMessage = null;
            notifyFinished(finished);
            return;
        }

        Frame frame = message.frames.get(frameIndex);
        boolean success = bluetoothClient.sendCommand(BluetoothClient.COMMAND_IMAGE, frame.ppmBytes);
        if (!success) {
            PanelMessage finished = currentMessage;
            currentMessage = null;
            notifyFinished(finished);
            return;
        }
        notifyFrame(message, frame.ppmBytes);
        ioHandler.postDelayed(() -> sendFrame(message, frameIndex + 1, myGeneration), frame.durationMs);
    }

    private void tickLive(PanelMessage message, int myGeneration) {
        if (myGeneration != generation) {
            return;
        }
        byte[] ppm = message.liveDataSource.renderFrame();
        boolean success = bluetoothClient.sendCommand(BluetoothClient.COMMAND_IMAGE, ppm);
        if (!success) {
            PanelMessage finished = currentMessage;
            currentMessage = null;
            notifyFinished(finished);
            return;
        }
        notifyFrame(message, ppm);
        ioHandler.postDelayed(() -> tickLive(message, myGeneration), LIVE_TICK_MS);
    }

    private void notifyStarted(PanelMessage message) {
        if (listener != null) {
            mainHandler.post(() -> listener.onMessageStarted(message));
        }
    }

    private void notifyFrame(PanelMessage message, byte[] ppmBytes) {
        if (listener != null) {
            mainHandler.post(() -> listener.onFrameUpdated(message, ppmBytes));
        }
    }

    private void notifyFinished(PanelMessage message) {
        if (listener != null) {
            mainHandler.post(() -> listener.onMessageFinished(message));
        }
    }

    private void notifyFailed(PanelMessage message) {
        if (listener != null) {
            mainHandler.post(() -> listener.onSendFailed(message));
        }
    }
}
