package com.eduprime.arduinobt;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.eduprime.arduinobt.screens.AIControlActivity;
import com.eduprime.arduinobt.screens.DeviceActivityList;
import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SharedPreferences settingsPrefs   = getSharedPreferences("settings", MODE_PRIVATE);
            SharedPreferences connectionPrefs = getSharedPreferences("connection", MODE_PRIVATE);

            boolean firebaseAuth = FirebaseAuth.getInstance().getCurrentUser() != null;
            boolean setupDone    = settingsPrefs.getBoolean("setup_done", false);
            String  connType     = connectionPrefs.getString("type", null);

            if (connType == null) {
                // First launch — let user choose connection type
                startActivity(new Intent(this, ConnectionTypeActivity.class));
            } else if ("AI".equals(connType)) {
                startActivity(new Intent(this, AIControlActivity.class));
            } else if ("IOT".equals(connType)) {
                if (firebaseAuth) {
                    startActivity(new Intent(this, IoTDashboardActivity.class));
                } else {
                    startActivity(new Intent(this, IoTLoginActivity.class));
                }
            } else {
                // BT path — no login required
                startActivity(new Intent(this, setupDone
                        ? DeviceActivityList.class
                        : SetupActivity.class));
            }
            finish();
        }, 2000);
    }
}
