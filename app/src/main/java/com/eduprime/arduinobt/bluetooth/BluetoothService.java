package com.eduprime.arduinobt.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothService {

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static BluetoothService instance;

    private BluetoothSocket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private Thread readThread;
    private final List<OnDataListener> listeners = new ArrayList<>();
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
        void onDataSent(String cmd);
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
            mainHandler.post(() -> {
                for (OnDataListener l : new ArrayList<>(listeners)) l.onDataSent(command);
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    public void addListener(OnDataListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(OnDataListener listener) {
        listeners.remove(listener);
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
                        for (OnDataListener l : new ArrayList<>(listeners)) l.onDataReceived(data);
                    });
                } catch (IOException e) {
                    mainHandler.post(() -> {
                        for (OnDataListener l : new ArrayList<>(listeners)) l.onConnectionLost();
                    });
                    break;
                }
            }
        });
        readThread.start();
    }
}
