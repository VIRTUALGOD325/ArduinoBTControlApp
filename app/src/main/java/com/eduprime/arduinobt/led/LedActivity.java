package com.eduprime.arduinobt.led;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.eduprime.arduinobt.R;
import com.eduprime.arduinobt.bluetooth.BluetoothService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class LedActivity extends AppCompatActivity implements BluetoothService.OnDataListener {

    private BluetoothService btService;
    private TextView ledStatus;
    private boolean ledOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_led);

        btService = BluetoothService.getInstance();
        btService.addListener(this);

        ledStatus = findViewById(R.id.ledStatus);

        Button btnOn  = findViewById(R.id.btnOn);
        Button btnOff = findViewById(R.id.btnOff);

        BluetoothDevice device = getIntent().getParcelableExtra("device");

        if (device != null) {
            ((TextView) findViewById(R.id.deviceName)).setText(device.getName());
            updateStatus(false);
            if (!btService.isConnected()) {
                connectToDevice(device);
            } else {
                updateStatus(true);
            }
        } else {
            updateStatus(btService.isConnected());
        }

        btnOn.setOnClickListener(v -> turnOn());
        btnOff.setOnClickListener(v -> turnOff());

        findViewById(R.id.disconnectBtn).setOnClickListener(v -> disconnect());
    }

    private void turnOn() {
        if (!btService.isConnected()) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        btService.send("1");
        ledOn = true;
        ledStatus.setText("LED ON");
        ledStatus.setTextColor(0xFFFFEB3B);
    }

    private void turnOff() {
        if (!btService.isConnected()) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        btService.send("0");
        ledOn = false;
        ledStatus.setText("LED OFF");
        ledStatus.setTextColor(0xFF6B7280);
    }

    private void disconnect() {
        btService.disconnect();
        finish();
    }

    private void updateStatus(boolean connected) {
        findViewById(R.id.statusDot).setBackgroundResource(
                connected ? R.drawable.circle_green : R.drawable.circle_red);
        TextView status = findViewById(R.id.connectionStatus);
        status.setText(connected ? "Connected" : "Disconnected");
        status.setTextColor(connected ? 0xFF4CAF50 : 0xFFF44336);
    }

    private void connectToDevice(BluetoothDevice device) {
        ProgressDialog progress = ProgressDialog.show(
                this, "Connecting…", "Please wait…", true, false);

        btService.connect(device, this, new BluetoothService.OnConnectCallback() {
            @Override
            public void onConnected() {
                progress.dismiss();
                updateStatus(true);
            }

            @Override
            public void onConnectionFailed(String reason) {
                progress.dismiss();
                updateStatus(false);
                new MaterialAlertDialogBuilder(LedActivity.this)
                        .setTitle("Connection Failed")
                        .setMessage("Could not connect to " + device.getName() + ".\n" + reason)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    @Override
    public void onDataReceived(String data) {}

    @Override
    public void onDataSent(String cmd) {}

    @Override
    public void onConnectionLost() {
        runOnUiThread(() -> {
            updateStatus(false);
            Toast.makeText(this, "Connection lost", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        btService.removeListener(this);
    }
}
