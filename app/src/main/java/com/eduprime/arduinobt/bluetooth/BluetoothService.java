package com.eduprime.arduinobt.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bluetooth service supporting:
 *  - Classic SPP (HC-05, HC-06, standard Arduino BT shields)
 *  - BLE Serial (HM-10, ESP32 BLE UART, other 0xFFE0 UART services)
 */
public class BluetoothService {

    private static final String TAG = "BluetoothService";

    // Classic SPP UUID
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // HM-10 / BLE UART service + characteristic UUIDs
    private static final UUID BLE_SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID BLE_CHAR_UUID    = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    // Nordic UART service (ESP32 BLE)
    private static final UUID NUS_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID NUS_RX_UUID      = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID NUS_TX_UUID      = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID CLIENT_CHAR_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public enum ConnectionType { CLASSIC, BLE_HM10, BLE_ESP32 }

    public interface OnConnectCallback {
        void onConnected();
        void onConnectionFailed(String reason);
    }

    private static BluetoothService instance;

    // Classic BT
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private Thread readThread;

    // BLE
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic bleWriteChar;
    private ConnectionType connectionType = ConnectionType.CLASSIC;

    private final List<OnDataListener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Context appContext;
    private OnConnectCallback pendingConnectCallback;

    private BluetoothService() {}

    public static BluetoothService getInstance() {
        if (instance == null) instance = new BluetoothService();
        return instance;
    }

    public interface OnDataListener {
        void onDataReceived(String data);
        void onConnectionLost();
        void onDataSent(String cmd);
    }

    /**
     * Detect device type, connect, and notify callback when truly ready.
     * Classic: callback fires after socket.connect() succeeds.
     * BLE: callback fires after GATT services are discovered and UART char found.
     */
    @SuppressWarnings("MissingPermission")
    public void connect(BluetoothDevice device, Context context, OnConnectCallback callback) {
        if (socket != null && socket.isConnected()) disconnect();
        appContext = context.getApplicationContext();
        pendingConnectCallback = callback;

        if (device.getType() == BluetoothDevice.DEVICE_TYPE_LE) {
            connectBle(device);
        } else {
            new Thread(() -> {
                try {
                    connectClassic(device);
                    mainHandler.post(this::fireConnected);
                } catch (IOException e) {
                    mainHandler.post(() -> fireFailed(e.getMessage()));
                }
            }).start();
        }
    }

    private void fireConnected() {
        OnConnectCallback cb = pendingConnectCallback;
        pendingConnectCallback = null;
        if (cb != null) cb.onConnected();
    }

    private void fireFailed(String reason) {
        OnConnectCallback cb = pendingConnectCallback;
        pendingConnectCallback = null;
        if (cb != null) cb.onConnectionFailed(reason);
    }

    /** Legacy connect — keeps backward compat with existing callers (Classic SPP). */
    public void connect(BluetoothDevice device) throws IOException {
        connectClassic(device);
    }

    @SuppressWarnings("MissingPermission")
    private void connectClassic(BluetoothDevice device) throws IOException {
        connectionType = ConnectionType.CLASSIC;
        socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
        socket.connect();
        outputStream = socket.getOutputStream();
        inputStream  = socket.getInputStream();
        startReadLoop();
        Log.d(TAG, "Classic SPP connected to " + device.getName());
    }

    @SuppressWarnings("MissingPermission")
    private void connectBle(BluetoothDevice device) {
        if (appContext == null) return;
        gatt = device.connectGatt(appContext, false, gattCallback);
        Log.d(TAG, "BLE connecting to " + device.getName());
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        @SuppressWarnings("MissingPermission")
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mainHandler.post(() -> {
                    fireFailed("Device disconnected");
                    for (OnDataListener l : new ArrayList<>(listeners)) l.onConnectionLost();
                });
            }
        }

        @Override
        @SuppressWarnings("MissingPermission")
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            bleWriteChar = null;
            connectionType = ConnectionType.BLE_HM10;

            // Check HM-10 style first (FFE0/FFE1)
            BluetoothGattService svc = gatt.getService(BLE_SERVICE_UUID);
            if (svc != null) {
                bleWriteChar = svc.getCharacteristic(BLE_CHAR_UUID);
                connectionType = ConnectionType.BLE_HM10;
            }
            // Check Nordic UART (ESP32)
            if (bleWriteChar == null) {
                svc = gatt.getService(NUS_SERVICE_UUID);
                if (svc != null) {
                    bleWriteChar = svc.getCharacteristic(NUS_RX_UUID);
                    BluetoothGattCharacteristic tx = svc.getCharacteristic(NUS_TX_UUID);
                    if (tx != null) enableNotify(tx);
                    connectionType = ConnectionType.BLE_ESP32;
                }
            }
            if (bleWriteChar != null && connectionType == ConnectionType.BLE_HM10) {
                enableNotify(bleWriteChar);
            }

            if (bleWriteChar != null) {
                Log.d(TAG, "BLE ready. Type: " + connectionType);
                mainHandler.post(BluetoothService.this::fireConnected);
            } else {
                Log.w(TAG, "No UART service found on " + g.getDevice().getName());
                mainHandler.post(() -> fireFailed("No UART service found — is this an HM-10 or ESP32?"));
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g,
                                             BluetoothGattCharacteristic characteristic) {
            String data = characteristic.getStringValue(0);
            if (data == null) return;
            mainHandler.post(() -> {
                for (OnDataListener l : new ArrayList<>(listeners)) l.onDataReceived(data);
            });
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g,
                                           BluetoothGattCharacteristic characteristic, int status) {
            // write completed
        }
    };

    @SuppressWarnings("MissingPermission")
    private void enableNotify(BluetoothGattCharacteristic characteristic) {
        gatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor desc = characteristic.getDescriptor(CLIENT_CHAR_CONFIG);
        if (desc != null) {
            desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(desc);
        }
    }

    /** Send a command appending '\n' (used by all controls). Notifies listeners. */
    public void send(String command) {
        sendRaw(command + "\n");
        mainHandler.post(() -> {
            for (OnDataListener l : new ArrayList<>(listeners)) l.onDataSent(command);
        });
    }

    /** Send raw bytes with no modification — used by Terminal's line-ending selector. */
    @SuppressWarnings("MissingPermission")
    public void sendRaw(String raw) {
        byte[] bytes = raw.getBytes();
        if (gatt != null && bleWriteChar != null) {
            bleWriteChar.setValue(bytes);
            gatt.writeCharacteristic(bleWriteChar);
            return;
        }
        if (outputStream == null) return;
        try { outputStream.write(bytes); } catch (IOException e) { e.printStackTrace(); }
    }

    public boolean isConnected() {
        boolean classicOk = socket != null && socket.isConnected();
        boolean bleOk = gatt != null && bleWriteChar != null;
        return classicOk || bleOk;
    }

    public ConnectionType getConnectionType() { return connectionType; }

    public void addListener(OnDataListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(OnDataListener listener) { listeners.remove(listener); }

    @SuppressWarnings("MissingPermission")
    public void disconnect() {
        try {
            if (readThread != null) readThread.interrupt();
            if (socket != null) socket.close();
            if (gatt != null) { gatt.disconnect(); gatt.close(); }
        } catch (IOException e) { e.printStackTrace(); }
        finally {
            socket = null; outputStream = null; inputStream = null;
            gatt = null; bleWriteChar = null;
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
