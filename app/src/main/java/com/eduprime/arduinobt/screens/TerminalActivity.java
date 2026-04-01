package com.eduprime.arduinobt.screens;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ScrollView;
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

    private BluetoothService btService;
    private TextView terminalOutput;
    private ScrollView terminalScroll;
    private EditText commandInput;

    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;

    private final SimpleDateFormat sdf = new SimpleDateFormat("[HH:mm:ss]", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        btService = BluetoothService.getInstance();
        btService.setListener(this);

        terminalOutput = findViewById(R.id.terminalOutput);
        terminalScroll = findViewById(R.id.terminalScroll);
        commandInput   = findViewById(R.id.commandInput);

        findViewById(R.id.sendBtn).setOnClickListener(v -> sendCommand());
        commandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendCommand(); return true; }
            return false;
        });

        // History navigation
        findViewById(R.id.histUpBtn).setOnClickListener(v -> navigateHistory(1));
        findViewById(R.id.histDownBtn).setOnClickListener(v -> navigateHistory(-1));

        setupBottomNav();

        appendLog(!btService.isConnected()
                ? "! Not connected. Go to DEVICES to connect."
                : "> Ready. Waiting for data...");
    }

    private void sendCommand() {
        String cmd = commandInput.getText().toString().trim();
        if (cmd.isEmpty()) return;

        // Add to history (avoid consecutive duplicates)
        if (history.isEmpty() || !history.get(0).equals(cmd)) {
            history.add(0, cmd);
            if (history.size() > 50) history.remove(history.size() - 1);
        }
        historyIndex = -1;

        appendLog("> " + cmd);
        btService.send(cmd);
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

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_terminal);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if      (id == R.id.nav_devices)    startActivity(new Intent(this, DeviceActivityList.class));
            else if (id == R.id.nav_controller) startActivity(new Intent(this, ControllerActivity.class));
            return true;
        });
    }

    @Override
    public void onDataReceived(String data) {
        appendLog(data.trim());
    }

    @Override
    public void onConnectionLost() {
        appendLog("! Connection lost.");
        Toast.makeText(this, "Connection lost", Toast.LENGTH_SHORT).show();
    }
}
