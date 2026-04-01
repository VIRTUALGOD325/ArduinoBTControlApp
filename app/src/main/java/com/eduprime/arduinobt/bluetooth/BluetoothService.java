package com.eduprime.arduinobt.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothService {

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static BluetoothService instance;

    private BluetoothSocket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private Thread readThread;
    private OnDataListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BluetoothService() {}

    public static BluetoothService getInstance() {
        if (instance == null) {
            instance = new BluetoothService();
        }
        return instance;
    }

    public interface OnDataListener {
        void onDataReceived(String data);
        void onConnectionLost();
    }

    public void connect(BluetoothDevice device) throws IOException {
        if (socket != null && socket.isConnected()) {
            disconnect();
        }
        socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
        socket.connect();
        outputStream = socket.getOutputStream();
        inputStream = socket.getInputStream();
        startReadLoop();
    }

    public void send(String command) {
        if (outputStream == null) return;
        try {
            outputStream.write((command + "\n").getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    public void setListener(OnDataListener listener) {
        this.listener = listener;
    }

    public void disconnect() {
        try {
            if (readThread != null) readThread.interrupt();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            socket = null;
            outputStream = null;
            inputStream = null;
            instance = null;
        }
    }

    private void startReadLoop() {
        readThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;
            while (!Thread.currentThread().isInterrupted() && isConnected()) {
                try {
                    bytes = inputStream.read(buffer);
                    final String data = new String(buffer, 0, bytes);
                    mainHandler.post(() -> {
                        if (listener != null) listener.onDataReceived(data);
                    });
                } catch (IOException e) {
                    mainHandler.post(() -> {
                        if (listener != null) listener.onConnectionLost();
                    });
                    break;
                }
            }
        });
        readThread.start();
    }
}
