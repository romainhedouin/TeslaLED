package com.teslaowls.teslaled;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class BluetoothClient {

    public static final int COMMAND_IMAGE = 0;
    public static final int COMMAND_KILL = 2;
    public static final int COMMAND_SET_BRIGHTNESS = 3;
    private static final int STATUS_OK = 0;
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
     * then a single-byte status response (0 = OK, anything else = error).
     * RFCOMM is a reliable ordered stream (like TCP) so there's no need to
     * hand-chunk the payload or use a sentinel value to mark the end -
     * OutputStream.write(byte[]) already blocks until everything is written,
     * and the length prefix tells the far end exactly how many bytes to read.
     */
    public boolean sendCommand(int commandType, byte[] payload) {
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

            int status = this.socket.getInputStream().read();
            if (status == -1) {
                // Remote end closed the stream without an IOException on
                // write() - e.g. the Pi's process restarted after this
                // socket was established. isConnected() would still report
                // true for this socket, so without discarding it here every
                // future send() would keep reusing the same dead socket.
                System.out.println("[-] Command sending failed: remote closed the connection");
                discardStaleSocket();
                return false;
            }
            return status == STATUS_OK;
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
            return false;
        }
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
