package com.eduprime.arduinobt;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.eduprime.arduinobt.screens.AIControlActivity;

public class ConnectionTypeActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_type);

        TextView backBtn = findViewById(R.id.backBtn);
        CardView btCard  = findViewById(R.id.btCard);
        CardView iotCard = findViewById(R.id.iotCard);
        CardView aiCard  = findViewById(R.id.aiCard);

        backBtn.setOnClickListener(v -> finish());

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
            }
        });

        btCard.setOnClickListener(v -> {
            saveConnectionType("BT");
            boolean setupDone = getSharedPreferences("settings", MODE_PRIVATE)
                    .getBoolean("setup_done", false);
            startActivity(new Intent(this, setupDone
                    ? com.eduprime.arduinobt.screens.DeviceActivityList.class
                    : SetupActivity.class));
            finish();
        });

        iotCard.setOnClickListener(v -> {
            saveConnectionType("IOT");
            startActivity(new Intent(this, IoTLoginActivity.class));
            finish();
        });

        aiCard.setOnClickListener(v -> {
            saveConnectionType("AI");
            startActivity(new Intent(this, AIControlActivity.class));
            finish();
        });
    }

    private void saveConnectionType(String type) {
        getSharedPreferences("connection", MODE_PRIVATE)
                .edit()
                .putString("type", type)
                .apply();
    }
}
