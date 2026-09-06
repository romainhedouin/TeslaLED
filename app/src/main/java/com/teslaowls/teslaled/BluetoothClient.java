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

public class BluetoothClient {

    public static final int COMMAND_IMAGE = 0;
    public static final int COMMAND_LEGACY = 1;
    public static final int COMMAND_KILL = 2;
    private static final int STATUS_OK = 0;

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

    public void createSocket() {
        try {
            if (ActivityCompat.checkSelfPermission(this.context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            this.socket = this.device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
        } catch (IOException e) {
            System.out.println("[-] Couldn't create socket.");
            e.printStackTrace();
        }
    }

    public void connect() {
        try {
            if (ActivityCompat.checkSelfPermission(this.context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            this.createSocket();
            this.socket.connect();
            System.out.println("[+] Socket connected");
        } catch (IOException e) {
            System.out.println("[-] Socket connection failed");
            e.printStackTrace();
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
            return status == STATUS_OK;
        } catch (IOException e) {
            System.out.println("[-] Command sending failed");
            e.printStackTrace();
            return false;
        }
    }

    public boolean isConnected() {
        if (this.socket != null) {
            return this.socket.isConnected();
        }
        return false;
    }
}
