package com.eduprime.arduinobt.screens;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import java.util.HashSet;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.eduprime.arduinobt.BaseActivity;
import com.eduprime.arduinobt.R;
import com.eduprime.arduinobt.notifications.NotificationHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DeviceActivityList extends BaseActivity {

    private static final int REQUEST_BT_PERMISSION = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;
    private static final String TAG = "DeviceActivityList";

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private boolean isScanning = false;
    private final Set<String> shownAddresses = new HashSet<>();
    private final Handler scanHandler = new Handler(Looper.getMainLooper());

    private final ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (shownAddresses.contains(device.getAddress())) return;
            shownAddresses.add(device.getAddress());
            deviceList.add(device);
            adapter.notifyItemInserted(deviceList.size() - 1);
            nodeCount.setText(String.format("%02d", deviceList.size()));
            emptyText.setVisibility(View.GONE);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(TAG, "BLE scan failed, code=" + errorCode);
            String msg = errorCode == SCAN_FAILED_FEATURE_UNSUPPORTED
                    ? "BLE scan not supported on this device"
                    : "BLE scan failed (code " + errorCode + ")";
            Toast.makeText(DeviceActivityList.this, msg, Toast.LENGTH_LONG).show();
        }
    };

    private final ActivityResultLauncher<Intent> enableBtLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (bluetoothAdapter.isEnabled()) {
                    loadPairedDevices();
                } else {
                    Toast.makeText(this, "Bluetooth is required to use this app", Toast.LENGTH_LONG).show();
                }
            });
    private DeviceAdapter adapter;
    private final List<BluetoothDevice> deviceList = new ArrayList<>();
    private TextView nodeCount, emptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        nodeCount = findViewById(R.id.node_count);
        emptyText = findViewById(R.id.emptyText);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        adapter = new DeviceAdapter(deviceList, this::onDeviceSelected);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = manager.getAdapter();

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> loadPairedDevices());

        NotificationHelper.createChannels(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 99);
            }
        }
        setupBottomNav();
        loadPairedDevices();
    }

    private void loadPairedDevices() {
        if (!bluetoothAdapter.isEnabled()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Bluetooth is off")
                    .setMessage("Bluetooth must be on to find your Arduino. Turn it on now?")
                    .setPositiveButton("Turn On", (d, w) ->
                            enableBtLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN
                        }, REQUEST_BT_PERMISSION);
                return;
            }
        }

        Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
        deviceList.clear();
        shownAddresses.clear();
        if (paired != null) {
            deviceList.addAll(paired);
            for (BluetoothDevice d : paired) shownAddresses.add(d.getAddress());
        }

        adapter.notifyDataSetChanged();
        nodeCount.setText(String.format("%02d", deviceList.size()));
        emptyText.setVisibility(deviceList.isEmpty() ? View.VISIBLE : View.GONE);

        startBleScan();
    }

    @SuppressWarnings("MissingPermission")
    private void startBleScan() {
        if (!bluetoothAdapter.isEnabled()) return;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_LOCATION_PERMISSION);
                return;
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) return;
        }

        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            Log.w(TAG, "getBluetoothLeScanner() returned null — BLE not supported?");
            return;
        }

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        bleScanner.startScan(null, settings, bleScanCallback);
        isScanning = true;
        Log.d(TAG, "BLE scan started");
        Toast.makeText(this, "Scanning for BLE devices…", Toast.LENGTH_SHORT).show();

        scanHandler.removeCallbacksAndMessages(null);
        scanHandler.postDelayed(this::stopBleScan, 15_000);
    }

    @SuppressWarnings("MissingPermission")
    private void stopBleScan() {
        if (isScanning && bleScanner != null) {
            bleScanner.stopScan(bleScanCallback);
            isScanning = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isScanning && bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            startBleScan();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopBleScan();
    }

    private void onDeviceSelected(BluetoothDevice device) {
        Intent intent = new Intent(this, ControllerActivity.class);
        intent.putExtra("device", device);
        startActivity(intent);
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_devices);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_controller)  startActivity(new Intent(this, ControllerActivity.class));
            else if (id == R.id.nav_terminal)   startActivity(new Intent(this, TerminalActivity.class));
            else if (id == R.id.nav_settings)   startActivity(new Intent(this, SettingsActivity.class));
            else if (id == R.id.nav_ai)     startActivity(new Intent(this, AIControlActivity.class));
            return true;
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BT_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadPairedDevices();
            } else {
                Toast.makeText(this, "Bluetooth permission required", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBleScan();
            }
        }
    }
}
