package com.eduprime.arduinobt;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.google.firebase.database.DatabaseReference;

public class IoTButtonControlActivity extends BaseActivity {

    private static final long REPEAT_INTERVAL_MS = 300;

    private String uid;
    private DatabaseReference buttonsRef;
    private final Handler handler = new Handler();
    private String activeCommand = null;
    private Runnable repeatRunnable;
    private TextView statusLabel;
    private View statusDot;
    private View activeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iot_button_control);

        uid = getIntent().getStringExtra("userGeneratedUID");
        if (uid == null) { finish(); return; }

        buttonsRef  = IoTFirebaseManager.getDatabase(this).getReference("commands/" + uid + "/buttons");
        statusLabel = findViewById(R.id.statusLabel);
        statusDot   = findViewById(R.id.statusDot);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        findViewById(R.id.settingsBtn).setOnClickListener(v -> {
            Intent intent = new Intent(this, IoTButtonSettingsActivity.class);
            startActivity(intent);
        });

        bindButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rebind after returning from settings so new command values take effect
        bindButtons();
    }

    private void bindButtons() {
        SharedPreferences prefs = getSharedPreferences(IoTButtonSettingsActivity.PREFS, MODE_PRIVATE);

        bind(R.id.btnUp,    prefs.getString(IoTButtonSettingsActivity.K_UP,    IoTButtonSettingsActivity.D_UP));
        bind(R.id.btnDown,  prefs.getString(IoTButtonSettingsActivity.K_DOWN,  IoTButtonSettingsActivity.D_DOWN));
        bind(R.id.btnLeft,  prefs.getString(IoTButtonSettingsActivity.K_LEFT,  IoTButtonSettingsActivity.D_LEFT));
        bind(R.id.btnRight, prefs.getString(IoTButtonSettingsActivity.K_RIGHT, IoTButtonSettingsActivity.D_RIGHT));
        bind(R.id.btnStop,  prefs.getString(IoTButtonSettingsActivity.K_STOP,  IoTButtonSettingsActivity.D_STOP));

        String[] keys     = {IoTButtonSettingsActivity.K_1, IoTButtonSettingsActivity.K_2, IoTButtonSettingsActivity.K_3,
                             IoTButtonSettingsActivity.K_4, IoTButtonSettingsActivity.K_5, IoTButtonSettingsActivity.K_6,
                             IoTButtonSettingsActivity.K_7, IoTButtonSettingsActivity.K_8, IoTButtonSettingsActivity.K_9};
        String[] defaults = {IoTButtonSettingsActivity.D_1, IoTButtonSettingsActivity.D_2, IoTButtonSettingsActivity.D_3,
                             IoTButtonSettingsActivity.D_4, IoTButtonSettingsActivity.D_5, IoTButtonSettingsActivity.D_6,
                             IoTButtonSettingsActivity.D_7, IoTButtonSettingsActivity.D_8, IoTButtonSettingsActivity.D_9};
        int[]    ids      = {R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5,
                             R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9};

        for (int i = 0; i < ids.length; i++) {
            bind(ids[i], prefs.getString(keys[i], defaults[i]));
        }
    }

    private void bind(int viewId, String command) {
        View btn = findViewById(viewId);
        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:  startCommand(command, btn); break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: stopCommand(); break;
            }
            return false;
        });
    }

    private void startCommand(String command, View btn) {
        if (activeView != null) activeView.setAlpha(1.0f);
        activeView = btn;
        btn.setAlpha(0.6f);
        activeCommand = command;
        buttonsRef.setValue(command);
        updateStatus(command, true);

        if (repeatRunnable != null) handler.removeCallbacks(repeatRunnable);
        repeatRunnable = new Runnable() {
            @Override public void run() {
                if (activeCommand != null && activeCommand.equals(command)) {
                    buttonsRef.setValue(command);
                    handler.postDelayed(this, REPEAT_INTERVAL_MS);
                }
            }
        };
        handler.postDelayed(repeatRunnable, REPEAT_INTERVAL_MS);
    }

    private void stopCommand() {
        activeCommand = null;
        if (repeatRunnable != null) handler.removeCallbacks(repeatRunnable);
        if (activeView != null) { activeView.setAlpha(1.0f); activeView = null; }
        buttonsRef.setValue("NONE");
        updateStatus("NONE", false);
    }

    private void updateStatus(String command, boolean active) {
        statusLabel.setText(command);
        statusLabel.setTextColor(active ? Color.parseColor("#9ECAFF") : Color.parseColor("#E5E2E1"));
        statusDot.setBackgroundColor(active ? Color.parseColor("#4CAF50") : Color.parseColor("#4A5568"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCommand();
    }
}
