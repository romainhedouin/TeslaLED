package com.teslaowls.teslaled;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class BluetoothClient {

    public static final int COMMAND_IMAGE = 0;
    public static final int COMMAND_KILL = 2;
    public static final int COMMAND_SET_BRIGHTNESS = 3;
    private static final int STATUS_OK = 0;

    /** Result of sendCommand() - message is the Pi's own explanation on failure
     * (what actually went wrong), not a locally-guessed generic reason. */
    public static final class Result {
        public final boolean success;
        public final String message;

        private Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        static Result ok() {
            return new Result(true, "");
        }

        static Result failure(String message) {
            return new Result(false, message);
        }
    }
    // BluetoothSocket.connect() has no documented timeout - in poor radio
    // conditions it can block far longer than the ~12s Android typically
    // takes, and every send()/stop() funnels through the single ioHandler
    // thread that's stuck inside it. This bounds the freeze.
    private static final int CONNECT_TIMEOUT_MS = 10000;

    private final Context context;
    private BluetoothDevice device = null;
    private BluetoothSocket socket = null;

    public BluetoothClient(Context context) {
        this.context = context;
    }

    public void findDevice() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            System.out.println("[-] Bluetooth not supported");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            System.out.println("[-] Bluetooth not enabled");
            return;
        }

        System.out.println("[+] Bluetooth enabled");

        this.device = bluetoothAdapter.getRemoteDevice("B8:27:EB:A5:89:A8");
        System.out.println("[+] Device found");
    }

    /** @return false if the socket couldn't be created (permission denied, no device, or the create call itself threw). */
    public boolean createSocket() {
        try {
            if (ActivityCompat.checkSelfPermission(this.context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            if (this.device == null) {
                System.out.println("[-] Couldn't create socket: no device (findDevice() not called, or Bluetooth was off/unsupported).");
                return false;
            }
            this.socket = this.device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            return true;
        } catch (IOException e) {
            System.out.println("[-] Couldn't create socket.");
            e.printStackTrace();
            this.socket = null;
            return false;
        }
    }

    public void connect() {
        try {
            if (ActivityCompat.checkSelfPermission(this.context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            // createSocket() can fail (permission, no device, threw) and
            // leaves this.socket null - calling connect() on it below would
            // NPE on this thread instead of failing gracefully.
            if (!this.createSocket()) {
                return;
            }

            final BluetoothSocket socketToConnect = this.socket;
            final AtomicBoolean connectFinished = new AtomicBoolean(false);
            // BluetoothSocket.connect() has no documented timeout and can
            // hang indefinitely in poor radio conditions. Every send()/stop()
            // funnels through the single ioHandler thread that's blocked
            // inside this call, so an unbounded hang here freezes the whole
            // app's Bluetooth pipeline, Stop button included. Force it to
            // give up after CONNECT_TIMEOUT_MS by closing the socket out
            // from under it, which makes the blocked connect() throw.
            Thread watchdog = new Thread(() -> {
                try {
                    Thread.sleep(CONNECT_TIMEOUT_MS);
                } catch (InterruptedException e) {
                    return; // connect() already finished; nothing to do.
                }
                synchronized (connectFinished) {
                    if (!connectFinished.get()) {
                        System.out.println("[-] Socket connect timed out after " + CONNECT_TIMEOUT_MS + "ms, forcing it closed");
                        try {
                            socketToConnect.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            });
            watchdog.setDaemon(true);
            watchdog.start();
            try {
                socketToConnect.connect();
            } finally {
                synchronized (connectFinished) {
                    connectFinished.set(true);
                }
                watchdog.interrupt();
            }
            System.out.println("[+] Socket connected");
        } catch (IOException e) {
            System.out.println("[-] Socket connection failed");
            e.printStackTrace();
            discardStaleSocket();
        }
    }

    public void closeSocket() {
        try {
            this.socket.close();
        } catch (IOException e) {
            System.out.println("[-] Socket closing failed");
            e.printStackTrace();
        }

        System.out.println("[+] Socket closed");
    }

    /**
     * Wire format: [1 byte command type][4 bytes big-endian payload length][payload],
     * then a response in the same shape, mirrored: [1 byte status][4 bytes
     * big-endian message length][message, UTF-8]. STATUS_OK's message is
     * normally empty; a non-OK status carries the Pi's own explanation of
     * what actually went wrong - callers surface that directly rather than
     * guessing at a generic failure reason.
     * RFCOMM is a reliable ordered stream (like TCP) so there's no need to
     * hand-chunk the payload or use a sentinel value to mark the end -
     * OutputStream.write(byte[]) already blocks until everything is written,
     * and the length prefix tells the far end exactly how many bytes to read.
     */
    public Result sendCommand(int commandType, byte[] payload) {
        try {
            OutputStream outputStream = this.socket.getOutputStream();
            int length = payload.length;
            byte[] header = new byte[]{
                    (byte) commandType,
                    (byte) (length >>> 24),
                    (byte) (length >>> 16),
                    (byte) (length >>> 8),
                    (byte) length,
            };
            outputStream.write(header);
            if (length > 0) {
                outputStream.write(payload);
            }
            outputStream.flush();

            InputStream inputStream = this.socket.getInputStream();
            byte[] responseHeader = readExact(inputStream, 5);
            if (responseHeader == null) {
                // Remote end closed the stream without an IOException on
                // write() - e.g. the Pi's process restarted after this
                // socket was established. isConnected() would still report
                // true for this socket, so without discarding it here every
                // future send() would keep reusing the same dead socket.
                System.out.println("[-] Command sending failed: remote closed the connection");
                discardStaleSocket();
                return Result.failure("Connection lost");
            }
            int status = responseHeader[0] & 0xFF;
            int messageLength = ((responseHeader[1] & 0xFF) << 24) | ((responseHeader[2] & 0xFF) << 16)
                    | ((responseHeader[3] & 0xFF) << 8) | (responseHeader[4] & 0xFF);
            String message = "";
            if (messageLength > 0) {
                byte[] messageBytes = readExact(inputStream, messageLength);
                if (messageBytes == null) {
                    discardStaleSocket();
                    return Result.failure("Connection lost");
                }
                message = new String(messageBytes, StandardCharsets.UTF_8);
            }
            return status == STATUS_OK ? Result.ok() : Result.failure(message);
        } catch (IOException e) {
            System.out.println("[-] Command sending failed");
            e.printStackTrace();
            // The remote end (Pi) may have gone away without us noticing -
            // e.g. its process restarted - in which case this socket is
            // permanently broken even though Android's isConnected() can
            // keep reporting true. Discard it so the next send() attempt
            // opens a fresh connection instead of repeating the same
            // failing write forever.
            discardStaleSocket();
            return Result.failure(e.getMessage() != null ? e.getMessage() : "Connection error");
        }
    }

    /** Reads exactly n bytes, looping since a single read() call isn't
     * guaranteed to return everything at once. Returns null if the stream
     * ends before n bytes arrive. */
    private static byte[] readExact(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int total = 0;
        while (total < n) {
            int read = in.read(buf, total, n - total);
            if (read == -1) {
                return null;
            }
            total += read;
        }
        return buf;
    }

    private void discardStaleSocket() {
        if (this.socket == null) {
            return;
        }
        try {
            this.socket.close();
        } catch (IOException ignored) {
        }
        this.socket = null;
    }

    public boolean isConnected() {
        if (this.socket != null) {
            return this.socket.isConnected();
        }
        return false;
    }
}
