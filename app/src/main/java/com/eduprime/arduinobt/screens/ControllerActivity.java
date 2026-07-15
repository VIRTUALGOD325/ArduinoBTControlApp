package com.eduprime.arduinobt.screens;

import android.Manifest;
import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import androidx.core.app.ActivityCompat;

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
    private static final int SEND_PRESS = 0, SEND_RELEASE = 1, SEND_BOTH = 2;
    private Long connectionStartTime;
    private TextView connectionTimer;

    private BluetoothService btService;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private SharedPreferences prefs;
    private Dialog connectingDialog;

    private int  currentMode  = MODE_DPAD;
    private long lastSendTime = 0;

    // D-Pad latch / momentary
    private boolean dpadLatch = false;
    private int sendOn = SEND_PRESS;
    private final boolean[] latchActive = {false, false, false, false};
    private View dpadFwd, dpadBack, dpadLeft, dpadRight;
    private TextView btnMomentary, btnLatchMode, sendOnPress, sendOnRelease, sendOnBoth;
    private View sendOnControls;

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
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
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

        // Landscape controller mode
        findViewById(R.id.landscapeBtn).setOnClickListener(v -> {
            Intent i = new Intent(this, LandscapeControllerActivity.class);
            if (device != null) i.putExtra("device_name", device.getName());
            startActivity(i);
        });

        // D-Pad
        dpadFwd   = findViewById(R.id.btnForward);
        dpadBack  = findViewById(R.id.btnBack);
        dpadLeft  = findViewById(R.id.btnLeft);
        dpadRight = findViewById(R.id.btnRight);
        dpadFwd.setOnTouchListener(dpadTouch(0, "cmd_fwd",   "F", "nostop_fwd"));
        dpadBack.setOnTouchListener(dpadTouch(1, "cmd_back",  "B", "nostop_back"));
        dpadLeft.setOnTouchListener(dpadTouch(2, "cmd_left",  "L", "nostop_left"));
        dpadRight.setOnTouchListener(dpadTouch(3, "cmd_right", "R", "nostop_right"));
        findViewById(R.id.btnStop).setOnClickListener(v -> {
            java.util.Arrays.fill(latchActive, false);
            updateLatchVisuals();
            btService.send(prefs.getString("cmd_stop", "S"));
        });

        // Action buttons
        findViewById(R.id.btnA).setOnTouchListener(actionTouch("cmd_a", "LED"));
        findViewById(R.id.btnB).setOnTouchListener(actionTouch("cmd_b", "BZ"));
        findViewById(R.id.btnC).setOnTouchListener(actionTouch("cmd_c", "Y"));
        findViewById(R.id.btnD).setOnTouchListener(actionTouch("cmd_d", "ESTOP"));

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

        // Joystick — throttled, 8-quadrant, uses dedicated joy_* prefs
        ((JoystickView) findViewById(R.id.joystick)).setOnMoveListener((x, y) -> {
            if (!throttle()) return;
            String cmd;
            if (Math.abs(x) < 20 && Math.abs(y) < 20) {
                cmd = prefs.getString("joy_stop", "S");
            } else {
                float ax = Math.abs(x), ay = Math.abs(y);
                if      (ay > ax * 2 && y < 0) cmd = prefs.getString("joy_up",    "F");
                else if (ay > ax * 2)           cmd = prefs.getString("joy_down",  "B");
                else if (ax > ay * 2 && x > 0) cmd = prefs.getString("joy_right", "R");
                else if (ax > ay * 2)           cmd = prefs.getString("joy_left",  "L");
                else if (y < 0 && x > 0)        cmd = prefs.getString("joy_ur",    "FR");
                else if (y < 0)                 cmd = prefs.getString("joy_ul",    "FL");
                else if (x > 0)                 cmd = prefs.getString("joy_dr",    "BR");
                else                            cmd = prefs.getString("joy_dl",    "BL");
            }
            btService.send(cmd);
        });
        setupNumpad();
        setupDpadModeControls();

        // Voice
        findViewById(R.id.micBtn).setOnClickListener(v -> startVoice());

        setupBottomNav();
        setupOnBackPressed();
    }

    private void setupOnBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (btService != null && btService.isConnected()) {
                    new MaterialAlertDialogBuilder(ControllerActivity.this)
                            .setTitle("Disconnect")
                            .setMessage("Disconnect from the device and go back?")
                            .setPositiveButton("Disconnect", (dialog, which) -> {
                                btService.send(prefs.getString("cmd_stop", "S"));
                                btService.disconnect();
                                finish();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                } else {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadButtonLabels();
        if (currentMode == MODE_TILT)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        if (nav != null) nav.setSelectedItemId(R.id.nav_controller);
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

    private View.OnTouchListener dpadTouch(int btnIndex, String prefKey, String defaultCmd, String noStopKey) {
        return (v, event) -> {
            int action = event.getActionMasked();
            String cmd     = prefs.getString(prefKey, defaultCmd);
            String stopCmd = prefs.getString("cmd_stop", "S");
            // "No Stop" buttons drive toggle-style outputs (e.g. an LED) instead of motion:
            // they never emit the Stop command, so the output holds its state.
            boolean noStop = prefs.getBoolean(noStopKey, false);

            if (dpadLatch) {
                if (action == MotionEvent.ACTION_DOWN) {
                    if (!latchActive[btnIndex]) {
                        java.util.Arrays.fill(latchActive, false);
                        latchActive[btnIndex] = true;
                        btService.send(cmd);
                    } else {
                        latchActive[btnIndex] = false;
                        // Toggle output: re-send the same command to flip it back off.
                        btService.send(noStop ? cmd : stopCmd);
                    }
                    updateLatchVisuals();
                }
                // Ignore UP/CANCEL in latch mode — visual stays until next tap
            } else {
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        if (sendOn == SEND_PRESS || sendOn == SEND_BOTH)
                            btService.send(cmd);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        if (sendOn == SEND_RELEASE || sendOn == SEND_BOTH)
                            btService.send(cmd);
                        else if (!noStop)
                            btService.send(stopCmd);
                        break;
                }
            }
            return true;
        };
    }

    private View.OnTouchListener actionTouch(String prefKey, String defaultCmd) {
        return (v, event) -> {
            int action = event.getActionMasked();
            String cmd = prefs.getString(prefKey, defaultCmd);
            if (action == MotionEvent.ACTION_DOWN) {
                v.setPressed(true);
                if (sendOn == SEND_PRESS || sendOn == SEND_BOTH) btService.send(cmd);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.setPressed(false);
                if (sendOn == SEND_RELEASE || sendOn == SEND_BOTH) btService.send(cmd);
            }
            return true;
        };
    }

    private void setupDpadModeControls() {
        btnMomentary   = findViewById(R.id.btnMomentary);
        btnLatchMode   = findViewById(R.id.btnLatchMode);
        sendOnControls = findViewById(R.id.sendOnControls);
        sendOnPress    = findViewById(R.id.sendOnPress);
        sendOnRelease  = findViewById(R.id.sendOnRelease);
        sendOnBoth     = findViewById(R.id.sendOnBoth);

        dpadLatch = prefs.getBoolean("dpad_latch", false);
        sendOn    = prefs.getInt("dpad_send_on", SEND_PRESS);
        updateModeControls();

        btnMomentary.setOnClickListener(v -> {
            dpadLatch = false;
            clearLatch();
            prefs.edit().putBoolean("dpad_latch", false).apply();
            updateModeControls();
        });
        btnLatchMode.setOnClickListener(v -> {
            dpadLatch = true;
            clearLatch();
            prefs.edit().putBoolean("dpad_latch", true).apply();
            updateModeControls();
        });
        sendOnPress.setOnClickListener(v   -> setSendOn(SEND_PRESS));
        sendOnRelease.setOnClickListener(v -> setSendOn(SEND_RELEASE));
        sendOnBoth.setOnClickListener(v    -> setSendOn(SEND_BOTH));
    }

    private void setSendOn(int mode) {
        sendOn = mode;
        prefs.edit().putInt("dpad_send_on", mode).apply();
        updateModeControls();
    }

    private void updateModeControls() {
        int activeColor   = 0xFF1E3A5F, inactiveColor = 0xFF1A1A1A;
        int activeTxt     = 0xFFE5E2E1, inactiveTxt   = 0xFF6B7280;

        btnMomentary.setBackgroundColor(!dpadLatch ? activeColor : inactiveColor);
        btnMomentary.setTextColor(!dpadLatch ? activeTxt : inactiveTxt);
        btnLatchMode.setBackgroundColor(dpadLatch ? activeColor : inactiveColor);
        btnLatchMode.setTextColor(dpadLatch ? activeTxt : inactiveTxt);

        sendOnControls.setVisibility(dpadLatch ? View.GONE : View.VISIBLE);

        sendOnPress.setBackgroundColor(sendOn == SEND_PRESS   ? activeColor : inactiveColor);
        sendOnPress.setTextColor(sendOn == SEND_PRESS   ? activeTxt : inactiveTxt);
        sendOnRelease.setBackgroundColor(sendOn == SEND_RELEASE ? activeColor : inactiveColor);
        sendOnRelease.setTextColor(sendOn == SEND_RELEASE ? activeTxt : inactiveTxt);
        sendOnBoth.setBackgroundColor(sendOn == SEND_BOTH    ? activeColor : inactiveColor);
        sendOnBoth.setTextColor(sendOn == SEND_BOTH    ? activeTxt : inactiveTxt);
    }

    private void updateLatchVisuals() {
        View[] btns = {dpadFwd, dpadBack, dpadLeft, dpadRight};
        for (int i = 0; i < btns.length; i++) {
            if (btns[i] != null) btns[i].setPressed(latchActive[i]);
        }
    }

    private void clearLatch() {
        java.util.Arrays.fill(latchActive, false);
        updateLatchVisuals();
    }

    private void setupNumpad() {
        int[] keyIds = {
                R.id.key0, R.id.key1, R.id.key2, R.id.key3, R.id.key4,
                R.id.key5, R.id.key6, R.id.key7, R.id.key8, R.id.key9
        };
        String[] prefKeys = {"numpad_0","numpad_1","numpad_2","numpad_3","numpad_4",
                             "numpad_5","numpad_6","numpad_7","numpad_8","numpad_9"};
        String[] defaults = {"0","1","2","3","4","5","6","7","8","9"};

        for (int i = 0; i < keyIds.length; i++) {
            final String pref = prefKeys[i], def = defaults[i];
            findViewById(keyIds[i]).setOnClickListener(v ->
                    btService.send(prefs.getString(pref, def)));
        }
        findViewById(R.id.keyStar).setOnClickListener(v ->
                btService.send(prefs.getString("numpad_star", "*")));
        findViewById(R.id.keyHash).setOnClickListener(v ->
                btService.send(prefs.getString("numpad_hash", "#")));
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if      (id == R.id.nav_controller) return true; // already here
            else if (id == R.id.nav_devices)    navigateTo(DeviceActivityList.class);
            else if (id == R.id.nav_terminal)   navigateTo(TerminalActivity.class);
            else if (id == R.id.nav_settings)   navigateTo(SettingsActivity.class);
            else if (id == R.id.nav_ai)         navigateTo(AIControlActivity.class);
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
