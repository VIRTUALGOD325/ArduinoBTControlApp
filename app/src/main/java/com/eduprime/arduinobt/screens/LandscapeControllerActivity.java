package com.eduprime.arduinobt.screens;

import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.eduprime.arduinobt.BaseActivity;
import com.eduprime.arduinobt.R;
import com.eduprime.arduinobt.bluetooth.BluetoothService;
import com.eduprime.arduinobt.views.JoystickView;

public class LandscapeControllerActivity extends BaseActivity
        implements SensorEventListener, BluetoothService.OnDataListener {

    private static final int SEND_PRESS = 0, SEND_RELEASE = 1, SEND_BOTH = 2;

    private BluetoothService btService;
    private SharedPreferences prefs;
    private SensorManager sensorManager;
    private Sensor accelerometer;

    private View btDot;
    private TextView connStatus, lastCmd, gyroToggle, latchToggle;
    private boolean gyroActive = false;

    // D-Pad latch
    private boolean dpadLatch = false;
    private final boolean[] latchActive = {false, false, false, false};
    private View lsDpadUp, lsDpadDown, lsDpadLeft, lsDpadRight;

    private static final long THROTTLE_MS = 100;
    private long lastSendTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        setContentView(R.layout.activity_landscape_controller);

        btService     = BluetoothService.getInstance();
        prefs         = getSharedPreferences("settings", MODE_PRIVATE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        btDot      = findViewById(R.id.btDot);
        connStatus = findViewById(R.id.connStatus);
        lastCmd    = findViewById(R.id.lastCmd);
        gyroToggle = findViewById(R.id.gyroToggle);
        latchToggle = findViewById(R.id.latchToggle);

        String devName = getIntent().getStringExtra("device_name");
        if (devName != null) ((TextView) findViewById(R.id.deviceName)).setText(devName);

        updateStatus(btService.isConnected());
        btService.addListener(this);

        // Exit → return to portrait
        findViewById(R.id.exitBtn).setOnClickListener(v -> finish());

        // Latch toggle
        dpadLatch = prefs.getBoolean("dpad_latch", false);
        updateLatchToggleUi();
        latchToggle.setOnClickListener(v -> {
            dpadLatch = !dpadLatch;
            prefs.edit().putBoolean("dpad_latch", dpadLatch).apply();
            if (!dpadLatch) {
                java.util.Arrays.fill(latchActive, false);
                updateLatchVisuals();
            }
            updateLatchToggleUi();
        });

        // Gyroscope toggle
        gyroToggle.setOnClickListener(v -> {
            gyroActive = !gyroActive;
            gyroToggle.setTextColor(gyroActive ? 0xFFA8FF78 : 0xFF89919D);
            if (gyroActive)
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            else
                sensorManager.unregisterListener(this);
        });

        // Joystick — uses quadrant prefs; disabled when gyro is on
        ((JoystickView) findViewById(R.id.joystick)).setOnMoveListener((x, y) -> {
            if (gyroActive || !throttle()) return;
            send(resolveJoystickCmd(x, y));
        });

        // DPAD
        setupDpad();

        // XYAB — respect send-on pref from portrait controller
        findViewById(R.id.btnX).setOnTouchListener(actionTouch("cmd_a", "LED"));
        findViewById(R.id.btnY).setOnTouchListener(actionTouch("cmd_b", "BZ"));
        findViewById(R.id.btnA).setOnTouchListener(actionTouch("cmd_c", "Y"));
        findViewById(R.id.btnB).setOnTouchListener(actionTouch("cmd_d", "ESTOP"));
    }

    // ── Joystick 8-quadrant command resolver ─────────────────────────────────

    private String resolveJoystickCmd(float x, float y) {
        if (Math.abs(x) < 20 && Math.abs(y) < 20) return prefs.getString("joy_stop", "S");
        float ax = Math.abs(x), ay = Math.abs(y);
        if (ay > ax * 2) return y < 0 ? prefs.getString("joy_up",    "F") : prefs.getString("joy_down",  "B");
        if (ax > ay * 2) return x > 0 ? prefs.getString("joy_right", "R") : prefs.getString("joy_left",  "L");
        if (y < 0 && x > 0) return prefs.getString("joy_ur", "FR");
        if (y < 0)           return prefs.getString("joy_ul", "FL");
        if (x > 0)           return prefs.getString("joy_dr", "BR");
        return prefs.getString("joy_dl", "BL");
    }

    // ── DPAD ─────────────────────────────────────────────────────────────────

    private void setupDpad() {
        lsDpadUp    = findViewById(R.id.dpadUp);
        lsDpadDown  = findViewById(R.id.dpadDown);
        lsDpadLeft  = findViewById(R.id.dpadLeft);
        lsDpadRight = findViewById(R.id.dpadRight);

        lsDpadUp.setOnTouchListener(dpadTouch(0, "cmd_fwd",   "F"));
        lsDpadDown.setOnTouchListener(dpadTouch(1, "cmd_back",  "B"));
        lsDpadLeft.setOnTouchListener(dpadTouch(2, "cmd_left",  "L"));
        lsDpadRight.setOnTouchListener(dpadTouch(3, "cmd_right", "R"));
        findViewById(R.id.dpadStop).setOnClickListener(v -> {
            java.util.Arrays.fill(latchActive, false);
            updateLatchVisuals();
            send(prefs.getString("cmd_stop", "S"));
        });
    }

    private View.OnTouchListener dpadTouch(int idx, String prefKey, String defaultCmd) {
        return (v, event) -> {
            int action = event.getActionMasked();
            String cmd     = prefs.getString(prefKey, defaultCmd);
            String stopCmd = prefs.getString("cmd_stop", "S");

            if (dpadLatch) {
                if (action == MotionEvent.ACTION_DOWN) {
                    if (!latchActive[idx]) {
                        java.util.Arrays.fill(latchActive, false);
                        latchActive[idx] = true;
                        send(cmd);
                    } else {
                        latchActive[idx] = false;
                        send(stopCmd);
                    }
                    updateLatchVisuals();
                }
            } else {
                int sendOn = prefs.getInt("dpad_send_on", SEND_PRESS);
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        if (sendOn == SEND_PRESS || sendOn == SEND_BOTH) send(cmd);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        if (sendOn == SEND_RELEASE || sendOn == SEND_BOTH) send(cmd);
                        else send(stopCmd);
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
            int sendOn = prefs.getInt("dpad_send_on", SEND_PRESS);
            if (action == MotionEvent.ACTION_DOWN) {
                v.setPressed(true);
                if (sendOn == SEND_PRESS || sendOn == SEND_BOTH) send(cmd);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.setPressed(false);
                if (sendOn == SEND_RELEASE || sendOn == SEND_BOTH) send(cmd);
            }
            return true;
        };
    }

    private void updateLatchVisuals() {
        View[] btns = {lsDpadUp, lsDpadDown, lsDpadLeft, lsDpadRight};
        for (int i = 0; i < btns.length; i++) {
            if (btns[i] != null) btns[i].setPressed(latchActive[i]);
        }
    }

    private void updateLatchToggleUi() {
        latchToggle.setTextColor(dpadLatch ? 0xFFA8FF78 : 0xFF89919D);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void send(String cmd) {
        btService.send(cmd);
        runOnUiThread(() -> lastCmd.setText("→ " + cmd));
    }

    private boolean throttle() {
        long now = System.currentTimeMillis();
        if (now - lastSendTime < THROTTLE_MS) return false;
        lastSendTime = now;
        return true;
    }

    private void updateStatus(boolean connected) {
        if (btDot != null)
            btDot.setBackgroundResource(connected ? R.drawable.circle_green : R.drawable.circle_red);
        if (connStatus != null) {
            connStatus.setText(connected ? "CONNECTED" : "DISCONNECTED");
            connStatus.setTextColor(connected ? 0xFF4CAF50 : 0xFFF44336);
        }
    }

    // ── Gyroscope ────────────────────────────────────────────────────────────

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!gyroActive || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        if (!throttle()) return;
        float ax = event.values[0], ay = event.values[1], dead = 2.5f;
        String cmd;
        if (Math.abs(ax) < dead && Math.abs(ay) < dead) cmd = prefs.getString("cmd_stop",  "S");
        else if (Math.abs(ay) >= Math.abs(ax))           cmd = ay > 0 ? prefs.getString("joy_up","F") : prefs.getString("joy_down","B");
        else                                              cmd = ax < 0 ? prefs.getString("joy_right","R") : prefs.getString("joy_left","L");
        send(cmd);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override protected void onResume() {
        super.onResume();
        updateStatus(btService.isConnected());
        dpadLatch = prefs.getBoolean("dpad_latch", false);
        updateLatchToggleUi();
        if (gyroActive)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        btService.removeListener(this);
        sensorManager.unregisterListener(this);
    }

    // ── BT callbacks ──────────────────────────────────────────────────────────

    @Override public void onDataReceived(String data) {}
    @Override public void onDataSent(String cmd) {}

    @Override public void onConnectionLost() {
        runOnUiThread(() -> updateStatus(false));
    }
}
