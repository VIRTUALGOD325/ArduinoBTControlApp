package com.eduprime.arduinobt;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.eduprime.arduinobt.screens.DeviceActivityList;
import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SharedPreferences authPrefs     = getSharedPreferences("auth", MODE_PRIVATE);
            SharedPreferences settingsPrefs = getSharedPreferences("settings", MODE_PRIVATE);
            boolean guestMode    = authPrefs.getBoolean("guest", false);
            boolean pinVerified  = authPrefs.getBoolean("pin_verified", false);
            boolean firebaseAuth = FirebaseAuth.getInstance().getCurrentUser() != null;
            boolean setupDone    = settingsPrefs.getBoolean("setup_done", false);

            if (firebaseAuth || guestMode || pinVerified) {
                if (!setupDone) {
                    startActivity(new Intent(this, SetupActivity.class));
                } else {
                    startActivity(new Intent(this, DeviceActivityList.class));
                }
            } else {
                startActivity(new Intent(this, LoginActivity.class));
            }
            finish();
        }, 2000);
    }
}
