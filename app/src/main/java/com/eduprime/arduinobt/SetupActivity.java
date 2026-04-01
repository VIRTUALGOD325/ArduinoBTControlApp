package com.eduprime.arduinobt;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;

import com.eduprime.arduinobt.screens.DeviceActivityList;

public class SetupActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        EditText labelA = findViewById(R.id.setupLabelA);
        EditText labelB = findViewById(R.id.setupLabelB);
        EditText labelC = findViewById(R.id.setupLabelC);
        EditText labelD = findViewById(R.id.setupLabelD);

        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);

        // Pre-fill if user had already set labels (e.g. came back from settings)
        labelA.setText(prefs.getString("label_a", ""));
        labelB.setText(prefs.getString("label_b", ""));
        labelC.setText(prefs.getString("label_c", ""));
        labelD.setText(prefs.getString("label_d", ""));

        findViewById(R.id.startBtn).setOnClickListener(v -> {
            String a = labelA.getText().toString().trim();
            String b = labelB.getText().toString().trim();
            String c = labelC.getText().toString().trim();
            String d = labelD.getText().toString().trim();

            prefs.edit()
                    .putString("label_a", a.isEmpty() ? "TOGGLE LED"   : a)
                    .putString("label_b", b.isEmpty() ? "BUZZER"        : b)
                    .putString("label_c", c.isEmpty() ? "AUTO MODE"     : c)
                    .putString("label_d", d.isEmpty() ? "EMERGENCY"     : d)
                    .putBoolean("setup_done", true)
                    .apply();
            goToDevices();
        });

        findViewById(R.id.skipSetup).setOnClickListener(v -> {
            // Save defaults so labels are never empty
            if (!prefs.contains("label_a")) {
                prefs.edit()
                        .putString("label_a", "TOGGLE LED")
                        .putString("label_b", "BUZZER")
                        .putString("label_c", "AUTO MODE")
                        .putString("label_d", "EMERGENCY")
                        .apply();
            }
            prefs.edit().putBoolean("setup_done", true).apply();
            goToDevices();
        });
    }

    private void goToDevices() {
        startActivity(new Intent(this, DeviceActivityList.class));
        finish();
    }
}
