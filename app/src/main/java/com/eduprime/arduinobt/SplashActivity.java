package com.eduprime.arduinobt;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.eduprime.arduinobt.screens.DeviceActivityList;
import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
            boolean guestMode    = prefs.getBoolean("guest", false);
            boolean pinVerified  = prefs.getBoolean("pin_verified", false);
            boolean firebaseAuth = FirebaseAuth.getInstance().getCurrentUser() != null;

            if (firebaseAuth || guestMode || pinVerified) {
                startActivity(new Intent(this, DeviceActivityList.class));
            } else {
                startActivity(new Intent(this, LoginActivity.class));
            }
            finish();
        }, 2000);
    }
}
