package com.eduprime.arduinobt.screens;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.eduprime.arduinobt.BaseActivity;
import com.eduprime.arduinobt.R;
import com.eduprime.arduinobt.bluetooth.BluetoothService;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TerminalActivity extends BaseActivity implements BluetoothService.OnDataListener {

    private static final String[] LINE_ENDINGS = {"No Line Ending", "Newline (\\n)", "Carriage Return (\\r)", "Both NL+CR"};

    private BluetoothService btService;
    private TextView terminalOutput;
    private ScrollView terminalScroll;
    private EditText commandInput;
    private TextView connectionTypeLabel;

    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private int lineEndingMode = 1; // default: \n

    private final SimpleDateFormat sdf = new SimpleDateFormat("[HH:mm:ss]", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        btService = BluetoothService.getInstance();
        btService.addListener(this);

        terminalOutput      = findViewById(R.id.terminalOutput);
        terminalScroll      = findViewById(R.id.terminalScroll);
        commandInput        = findViewById(R.id.commandInput);
        connectionTypeLabel = findViewById(R.id.connectionTypeLabel);

        // Show baud rate from prefs
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        String baud = prefs.getString("baud_rate", "9600");
        TextView baudView = findViewById(R.id.baudRate);
        if (baudView != null) baudView.setText("● " + baud + " BAUD");

        // Connection type badge
        updateConnectionTypeBadge();

        // Line ending spinner
        Spinner lineEndingSpinner = findViewById(R.id.lineEndingSpinner);
        if (lineEndingSpinner != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, LINE_ENDINGS);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            lineEndingSpinner.setAdapter(adapter);
            lineEndingSpinner.setSelection(lineEndingMode);
            lineEndingSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, android.view.View v, int pos, long id) {
                    lineEndingMode = pos;
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        findViewById(R.id.sendBtn).setOnClickListener(v -> sendCommand());
        commandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendCommand(); return true; }
            return false;
        });

        findViewById(R.id.histUpBtn).setOnClickListener(v -> navigateHistory(1));
        findViewById(R.id.histDownBtn).setOnClickListener(v -> navigateHistory(-1));

        findViewById(R.id.clearBtn).setOnClickListener(v -> {
            terminalOutput.setText("");
            appendLog("--- Terminal cleared ---");
        });

        setupBottomNav();

        appendLog(!btService.isConnected()
                ? "! Not connected. Go to DEVICES to connect first."
                : "> Ready [" + getConnectionTypeString() + "]. Waiting for data...");
    }

    private void updateConnectionTypeBadge() {
        if (connectionTypeLabel == null) return;
        connectionTypeLabel.setText(getConnectionTypeString());
    }

    private String getConnectionTypeString() {
        if (!btService.isConnected()) return "OFFLINE";
        switch (btService.getConnectionType()) {
            case BLE_HM10:   return "HM-10 BLE";
            case BLE_ESP32:  return "ESP32 BLE";
            default:         return "Classic SPP";
        }
    }

    private void sendCommand() {
        String cmd = commandInput.getText().toString().trim();
        if (cmd.isEmpty()) return;

        if (history.isEmpty() || !history.get(0).equals(cmd)) {
            history.add(0, cmd);
            if (history.size() > 50) history.remove(history.size() - 1);
        }
        historyIndex = -1;

        // Build command with selected line ending
        String toSend;
        switch (lineEndingMode) {
            case 0: toSend = cmd;             break; // no ending
            case 1: toSend = cmd + "\n";      break; // LF
            case 2: toSend = cmd + "\r";      break; // CR
            case 3: toSend = cmd + "\r\n";    break; // CR+LF
            default: toSend = cmd + "\n";
        }

        appendLog("→ " + cmd);
        // Send raw (BluetoothService.send() normally appends \n, so we use outputStream directly
        // by calling send() and stripping the extra — or better: add a raw send method)
        btService.sendRaw(toSend);
        commandInput.setText("");
    }

    private void navigateHistory(int direction) {
        if (history.isEmpty()) return;
        historyIndex = Math.max(-1, Math.min(history.size() - 1, historyIndex + direction));
        if (historyIndex >= 0) {
            commandInput.setText(history.get(historyIndex));
            commandInput.setSelection(commandInput.getText().length());
        } else {
            commandInput.setText("");
        }
    }

    private void appendLog(String text) {
        String line = sdf.format(new Date()) + " " + text + "\n";
        terminalOutput.append(line);
        terminalScroll.post(() -> terminalScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    public void onDataReceived(String data) {
        appendLog("← " + data.trim());
    }

    @Override
    public void onDataSent(String cmd) {
        // Already logged in sendCommand(); ignore duplicate from listener
    }

    @Override
    public void onConnectionLost() {
        appendLog("! Connection lost.");
        Toast.makeText(this, "Connection lost", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        btService.removeListener(this);
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_terminal);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if      (id == R.id.nav_devices)    startActivity(new Intent(this, DeviceActivityList.class));
            else if (id == R.id.nav_controller) startActivity(new Intent(this, ControllerActivity.class));
            else if (id == R.id.nav_ai)         startActivity(new Intent(this, AIControlActivity.class));
            else if (id == R.id.nav_settings)   startActivity(new Intent(this, SettingsActivity.class));
            return true;
        });
    }
}
