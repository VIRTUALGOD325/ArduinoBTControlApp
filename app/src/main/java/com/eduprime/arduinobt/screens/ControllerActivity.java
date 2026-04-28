package com.eduprime.arduinobt.screens;

import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.eduprime.arduinobt.BaseActivity;
import com.eduprime.arduinobt.notifications.NotificationHelper;
import com.eduprime.arduinobt.R;
import com.eduprime.arduinobt.bluetooth.BluetoothService;
import com.eduprime.arduinobt.views.JoystickView;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.Locale;

public class ControllerActivity extends BaseActivity
        implements BluetoothService.OnDataListener, SensorEventListener {

    private static final int  MODE_DPAD = 0, MODE_JOYSTICK = 1, MODE_VOICE = 2, MODE_TILT = 3;
    private static final long THROTTLE_MS = 100;
    private Long connectionStartTime;
    private TextView connectionTimer;

    private BluetoothService btService;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private SharedPreferences prefs;
    private Dialog connectingDialog;

    private int  currentMode  = MODE_DPAD;
    private long lastSendTime = 0;

    private View dpadContainer, joystickContainer, voiceContainer, tiltContainer;
    private View speedContainer, actionButtonsContainer;
    private View statusDot;
    private TextView connectionStatus;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long elapsed = (System.currentTimeMillis() - connectionStartTime) / 1000;
            if (connectionTimer != null)
                connectionTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", elapsed / 60, elapsed % 60));
            timerHandler.postDelayed(this, 1000);
        }
    };
    private TextView[] modeTabs;
    private TextView voiceStatus, tiltStatus;
    private TextView btnALabel, btnBLabel, btnCLabel, btnDLabel;

    private final ActivityResultLauncher<Intent> voiceLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getData() == null) return;
                ArrayList<String> matches = result.getData()
                        .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (matches != null && !matches.isEmpty()) processVoiceCommand(matches.get(0));
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);

        btService     = BluetoothService.getInstance();
        prefs         = getSharedPreferences("settings", MODE_PRIVATE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        statusDot        = findViewById(R.id.statusDot);
        connectionStatus = findViewById(R.id.connectionStatus);
        connectionTimer  = findViewById(R.id.connectionTimer);

        BluetoothDevice device = getIntent().getParcelableExtra("device");
        btService.addListener(this);

        if (device != null) {
            ((TextView) findViewById(R.id.deviceName)).setText(device.getName());
            if (!btService.isConnected()) {
                updateStatus(false);
                connectToDevice(device);
            } else {
                updateStatus(true);
            }
        } else {
            updateStatus(btService.isConnected());
        }

        // Mode containers + tabs
        dpadContainer     = findViewById(R.id.dpadContainer);
        joystickContainer = findViewById(R.id.joystickContainer);
        voiceContainer    = findViewById(R.id.voiceContainer);
        tiltContainer     = findViewById(R.id.tiltContainer);
        speedContainer    = findViewById(R.id.speedContainer);
        actionButtonsContainer = findViewById(R.id.actionButtonsContainer);
        voiceStatus       = findViewById(R.id.voiceStatus);
        tiltStatus        = findViewById(R.id.tiltStatus);

        modeTabs = new TextView[]{
            findViewById(R.id.modeDpad),
            findViewById(R.id.modeJoystick),
            findViewById(R.id.modeVoice),
            findViewById(R.id.modeTilt)
        };
        for (int i = 0; i < modeTabs.length; i++) {
            final int mode = i;
            modeTabs[i].setOnClickListener(v -> setMode(mode));
        }
        setMode(MODE_DPAD);

        // Action button labels (from settings)
        btnALabel = findViewById(R.id.btnALabel);
        btnBLabel = findViewById(R.id.btnBLabel);
        btnCLabel = findViewById(R.id.btnCLabel);
        btnDLabel = findViewById(R.id.btnDLabel);

        // Top bar buttons
        findViewById(R.id.disconnectBtn).setOnClickListener(v -> {
            btService.disconnect();
            startActivity(new Intent(this, DeviceActivityList.class));
            finish();
        });
        findViewById(R.id.settingsBtn).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // D-Pad — press sends direction, release sends stop automatically
        findViewById(R.id.btnForward).setOnTouchListener(dpadTouch("cmd_fwd",  "F"));
        findViewById(R.id.btnBack).setOnTouchListener(   dpadTouch("cmd_back", "B"));
        findViewById(R.id.btnLeft).setOnTouchListener(   dpadTouch("cmd_left", "L"));
        findViewById(R.id.btnRight).setOnTouchListener(  dpadTouch("cmd_right","R"));
        findViewById(R.id.btnStop).setOnClickListener(v  -> btService.send(prefs.getString("cmd_stop", "S")));

        // Action buttons — toggle commxands for pins 4 (LED), 6 (Buzzer), 5 (Y)
        findViewById(R.id.btnA).setOnClickListener(v -> btService.send(prefs.getString("cmd_a", "LED")));
        findViewById(R.id.btnB).setOnClickListener(v -> btService.send(prefs.getString("cmd_b", "BZ")));
        findViewById(R.id.btnC).setOnClickListener(v -> btService.send(prefs.getString("cmd_c", "Y")));
        findViewById(R.id.btnD).setOnClickListener(v -> btService.send(prefs.getString("cmd_d", "ESTOP")));

        // Speed slider
        TextView speedValue = findViewById(R.id.speedValue);
        ((SeekBar) findViewById(R.id.speedSlider)).setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                speedValue.setText(p + "%");
                if (fromUser) btService.send("SPD:" + p);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        // Joystick — throttled, uses same configurable commands as D-pad
        ((JoystickView) findViewById(R.id.joystick)).setOnMoveListener((x, y) -> {
            if (!throttle()) return;
            if      (Math.abs(x) < 20 && Math.abs(y) < 20) btService.send(prefs.getString("cmd_stop",  "S"));
            else if (Math.abs(y) >= Math.abs(x))            btService.send(y < 0 ? prefs.getString("cmd_fwd",  "F") : prefs.getString("cmd_back", "B"));
            else                                             btService.send(x > 0 ? prefs.getString("cmd_right","R") : prefs.getString("cmd_left", "L"));
        });
        setupNumpad();

        // Voice
        findViewById(R.id.micBtn).setOnClickListener(v -> startVoice());

        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadButtonLabels();
        if (currentMode == MODE_TILT)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    private void setMode(int mode) {
        currentMode = mode;
        dpadContainer.setVisibility(mode == MODE_DPAD      ? View.VISIBLE : View.GONE);
        joystickContainer.setVisibility(mode == MODE_JOYSTICK ? View.VISIBLE : View.GONE);
        voiceContainer.setVisibility(mode == MODE_VOICE    ? View.VISIBLE : View.GONE);
        tiltContainer.setVisibility(mode == MODE_TILT      ? View.VISIBLE : View.GONE);
        speedContainer.setVisibility(mode == MODE_JOYSTICK ? View.GONE : View.VISIBLE);
        actionButtonsContainer.setVisibility(mode == MODE_JOYSTICK ? View.GONE : View.VISIBLE);

        for (int i = 0; i < modeTabs.length; i++) {
            modeTabs[i].setTextColor(i == mode ? 0xFF9ECAFF : 0xFF6B7280);
            modeTabs[i].setBackgroundColor(i == mode ? 0xFF1E3A5F : 0xFF1A1A1A);
        }

        if (mode == MODE_TILT)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        else
            sensorManager.unregisterListener(this);
    }

    private void startVoice() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                    "Say: forward, back, left, right, or stop");
            voiceLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Voice not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void processVoiceCommand(String speech) {
        String s = speech.toLowerCase(Locale.getDefault());
        String cmd;
        if      (s.contains("forward") || s.contains("ahead"))    cmd = prefs.getString("cmd_fwd",   "F");
        else if (s.contains("back")    || s.contains("reverse"))  cmd = prefs.getString("cmd_back",  "B");
        else if (s.contains("left"))                               cmd = prefs.getString("cmd_left",  "L");
        else if (s.contains("right"))                              cmd = prefs.getString("cmd_right", "R");
        else if (s.contains("stop")    || s.contains("halt"))      cmd = prefs.getString("cmd_stop",  "S");
        else if (s.contains("led")     || s.contains("light"))     cmd = prefs.getString("cmd_a", "A");
        else if (s.contains("buzz"))                               cmd = prefs.getString("cmd_b", "BZ");
        else if (s.contains("auto"))                               cmd = prefs.getString("cmd_c", "AUTO");
        else                                                        cmd = speech;

        voiceStatus.setText("\"" + speech + "\"  →  " + cmd);
        btService.send(cmd);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        if (!throttle()) return;

        float ax = event.values[0]; // tilt left(-) / right(+)
        float ay = event.values[1]; // tilt forward(+) / back(-)
        float dead = 2.5f;

        String cmd;
        if      (Math.abs(ax) < dead && Math.abs(ay) < dead) cmd = prefs.getString("cmd_stop",  "S");
        else if (Math.abs(ay) >= Math.abs(ax))                cmd = ay > 0 ? prefs.getString("cmd_fwd", "F") : prefs.getString("cmd_back", "B");
        else                                                   cmd = ax < 0 ? prefs.getString("cmd_right","R") : prefs.getString("cmd_left","L");

        tiltStatus.setText(String.format(Locale.getDefault(),
                "x=%.1f  y=%.1f  →  %s", ax, ay, cmd));
        btService.send(cmd);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private boolean throttle() {
        long now = System.currentTimeMillis();
        if (now - lastSendTime < THROTTLE_MS) return false;
        lastSendTime = now;
        return true;
    }

    private void loadButtonLabels() {
        if (btnALabel != null) btnALabel.setText(prefs.getString("label_a", "TOGGLE LED"));
        if (btnBLabel != null) btnBLabel.setText(prefs.getString("label_b", "BUZZER"));
        if (btnCLabel != null) btnCLabel.setText(prefs.getString("label_c", "AUTO MODE"));
        if (btnDLabel != null) btnDLabel.setText(prefs.getString("label_d", "EMERGENCY"));
    }

    private void connectToDevice(BluetoothDevice device) {
        connectingDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Connecting...")
                .setMessage("Connecting to " + device.getName() + "\nMake sure the device is on.")
                .setCancelable(false)
                .create();
        connectingDialog.show();

        btService.connect(device, this, new BluetoothService.OnConnectCallback() {
            @Override
            public void onConnected() {
                dismissDialog();
                updateStatus(true);
                NotificationHelper.notifyConnected(ControllerActivity.this, device.getName());
            }

            @Override
            public void onConnectionFailed(String reason) {
                dismissDialog();
                updateStatus(false);
                new MaterialAlertDialogBuilder(ControllerActivity.this)
                        .setTitle("Connection Failed")
                        .setMessage("Could not connect to " + device.getName() + ".\n" + reason)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    private void dismissDialog() {
        if (connectingDialog != null && connectingDialog.isShowing()) {
            connectingDialog.dismiss();
            connectingDialog = null;
        }
    }

    private void updateStatus(boolean connected) {
        if (statusDot != null)
            statusDot.setBackgroundResource(connected ? R.drawable.circle_green : R.drawable.circle_red);
        if (connectionStatus != null) {
            connectionStatus.setText(connected ? "Connected" : "Disconnected");
            connectionStatus.setTextColor(connected ? 0xFF4CAF50 : 0xFFF44336);
        }
        if (connected) {
            connectionStartTime = System.currentTimeMillis();
            timerHandler.post(timerRunnable);
        } else {
            timerHandler.removeCallbacks(timerRunnable);
            if (connectionTimer != null) connectionTimer.setText("00:00");
        }
    }

    /** Returns a touch listener that sends the direction command on press and stop on release. */
    private View.OnTouchListener dpadTouch(String prefKey, String defaultCmd) {
        return (v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    btService.send(prefs.getString(prefKey, defaultCmd));
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    btService.send(prefs.getString("cmd_stop", "S"));
                    break;
            }
            return true;
        };
    }

    private void setupNumpad() {
        int[] keyIds = {
                R.id.key0, R.id.key1, R.id.key2, R.id.key3, R.id.key4,
                R.id.key5, R.id.key6, R.id.key7, R.id.key8, R.id.key9
        };

        for (int i = 0; i < keyIds.length; i++) {
            final String command = String.valueOf(i);
            findViewById(keyIds[i]).setOnClickListener(v -> btService.send(command));
        }

        findViewById(R.id.keyStar).setOnClickListener(v -> btService.send("*"));
        findViewById(R.id.keyHash).setOnClickListener(v -> btService.send("#"));
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_controller);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if      (id == R.id.nav_devices)  startActivity(new Intent(this, DeviceActivityList.class));
            else if (id == R.id.nav_terminal) startActivity(new Intent(this, TerminalActivity.class));
            else if (id == R.id.nav_settings) startActivity(new Intent(this, SettingsActivity.class));
            else if (id == R.id.nav_ai)     startActivity(new Intent(this, AIControlActivity.class));
            return true;
        });
    }

    @Override public void onDataReceived(String data) {}
    @Override public void onDataSent(String cmd) {}

    @Override
    public void onConnectionLost() {
        runOnUiThread(() -> {
            dismissDialog();
            updateStatus(false);
            NotificationHelper.notifyDisconnected(this);
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Connection Lost")
                    .setMessage("The Bluetooth connection was lost. Go back to devices?")
                    .setPositiveButton("Go Back", (d, w) -> {
                        startActivity(new Intent(this, DeviceActivityList.class));
                        finish();
                    })
                    .setNegativeButton("Stay", null)
                    .show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        btService.removeListener(this);
    }
}
