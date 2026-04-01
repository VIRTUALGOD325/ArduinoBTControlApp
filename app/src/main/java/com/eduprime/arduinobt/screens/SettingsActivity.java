package com.eduprime.arduinobt.screens;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.eduprime.arduinobt.BaseActivity;
import com.eduprime.arduinobt.R;

public class SettingsActivity extends BaseActivity {

    private static final String[] BAUDS = {"9600", "19200", "38400", "57600", "115200"};
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        EditText labelA = findViewById(R.id.labelA), cmdA = findViewById(R.id.cmdA);
        EditText labelB = findViewById(R.id.labelB), cmdB = findViewById(R.id.cmdB);
        EditText labelC = findViewById(R.id.labelC), cmdC = findViewById(R.id.cmdC);
        EditText labelD = findViewById(R.id.labelD), cmdD = findViewById(R.id.cmdD);

        labelA.setText(prefs.getString("label_a", "TOGGLE LED"));
        cmdA.setText(prefs.getString("cmd_a", "A"));
        labelB.setText(prefs.getString("label_b", "BUZZER"));
        cmdB.setText(prefs.getString("cmd_b", "BZ"));
        labelC.setText(prefs.getString("label_c", "AUTO MODE"));
        cmdC.setText(prefs.getString("cmd_c", "AUTO"));
        labelD.setText(prefs.getString("label_d", "EMERGENCY"));
        cmdD.setText(prefs.getString("cmd_d", "STOP"));

        Spinner baudSpinner = findViewById(R.id.baudSpinner);
        baudSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, BAUDS));
        String saved = prefs.getString("baud_rate", "9600");
        for (int i = 0; i < BAUDS.length; i++) {
            if (BAUDS[i].equals(saved)) { baudSpinner.setSelection(i); break; }
        }

        findViewById(R.id.saveBtn).setOnClickListener(v -> {
            prefs.edit()
                    .putString("label_a", labelA.getText().toString().trim())
                    .putString("cmd_a",   cmdA.getText().toString().trim())
                    .putString("label_b", labelB.getText().toString().trim())
                    .putString("cmd_b",   cmdB.getText().toString().trim())
                    .putString("label_c", labelC.getText().toString().trim())
                    .putString("cmd_c",   cmdC.getText().toString().trim())
                    .putString("label_d", labelD.getText().toString().trim())
                    .putString("cmd_d",   cmdD.getText().toString().trim())
                    .putString("baud_rate", BAUDS[baudSpinner.getSelectedItemPosition()])
                    .apply();
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
    }
}
