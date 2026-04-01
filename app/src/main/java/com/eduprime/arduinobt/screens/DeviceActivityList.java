package com.eduprime.arduinobt.screens;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.eduprime.arduinobt.BaseActivity;
import com.eduprime.arduinobt.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DeviceActivityList extends BaseActivity {

    private static final int REQUEST_BT_PERMISSION = 1;

    private BluetoothAdapter bluetoothAdapter;

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
        if (paired != null) deviceList.addAll(paired);

        adapter.notifyDataSetChanged();
        nodeCount.setText(String.format("%02d", deviceList.size()));
        emptyText.setVisibility(deviceList.isEmpty() ? View.VISIBLE : View.GONE);
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
            if (id == R.id.nav_controller) {
                startActivity(new Intent(this, ControllerActivity.class));
            } else if (id == R.id.nav_terminal) {
                startActivity(new Intent(this, TerminalActivity.class));
            }
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
        }
    }
}
