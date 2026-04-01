package com.eduprime.arduinobt.screens;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.eduprime.arduinobt.BaseActivity;
import com.eduprime.arduinobt.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class SettingsActivity extends BaseActivity {

    private static final String[] BAUDS = {"9600", "19200", "38400", "57600", "115200"};
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        // Action button labels + commands
        EditText labelA = findViewById(R.id.labelA), cmdA = findViewById(R.id.cmdA);
        EditText labelB = findViewById(R.id.labelB), cmdB = findViewById(R.id.cmdB);
        EditText labelC = findViewById(R.id.labelC), cmdC = findViewById(R.id.cmdC);
        EditText labelD = findViewById(R.id.labelD), cmdD = findViewById(R.id.cmdD);

        labelA.setText(prefs.getString("label_a", "LED BLUE"));
        cmdA.setText(prefs.getString("cmd_a", "LED"));
        labelB.setText(prefs.getString("label_b", "BUZZER"));
        cmdB.setText(prefs.getString("cmd_b", "BZ"));
        labelC.setText(prefs.getString("label_c", "Y PIN"));
        cmdC.setText(prefs.getString("cmd_c", "Y"));
        labelD.setText(prefs.getString("label_d", "EMERGENCY"));
        cmdD.setText(prefs.getString("cmd_d", "ESTOP"));

        // D-pad commands
        EditText cmdFwd   = findViewById(R.id.cmdForward);
        EditText cmdBck   = findViewById(R.id.cmdBack);
        EditText cmdLft   = findViewById(R.id.cmdLeft);
        EditText cmdRgt   = findViewById(R.id.cmdRight);
        EditText cmdStp   = findViewById(R.id.cmdStop);

        cmdFwd.setText(prefs.getString("cmd_fwd",   "F"));
        cmdBck.setText(prefs.getString("cmd_back",  "B"));
        cmdLft.setText(prefs.getString("cmd_left",  "L"));
        cmdRgt.setText(prefs.getString("cmd_right", "R"));
        cmdStp.setText(prefs.getString("cmd_stop",  "S"));

        // Baud rate
        Spinner baudSpinner = findViewById(R.id.baudSpinner);
        baudSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, BAUDS));
        String saved = prefs.getString("baud_rate", "9600");
        for (int i = 0; i < BAUDS.length; i++) {
            if (BAUDS[i].equals(saved)) { baudSpinner.setSelection(i); break; }
        }

        findViewById(R.id.saveBtn).setOnClickListener(v -> {
            prefs.edit()
                    .putString("label_a",    labelA.getText().toString().trim())
                    .putString("cmd_a",      notEmpty(cmdA,   "A"))
                    .putString("label_b",    labelB.getText().toString().trim())
                    .putString("cmd_b",      notEmpty(cmdB,   "BZ"))
                    .putString("label_c",    labelC.getText().toString().trim())
                    .putString("cmd_c",      notEmpty(cmdC,   "AUTO"))
                    .putString("label_d",    labelD.getText().toString().trim())
                    .putString("cmd_d",      notEmpty(cmdD,   "STOP"))
                    .putString("cmd_fwd",    notEmpty(cmdFwd, "F"))
                    .putString("cmd_back",   notEmpty(cmdBck, "B"))
                    .putString("cmd_left",   notEmpty(cmdLft, "L"))
                    .putString("cmd_right",  notEmpty(cmdRgt, "R"))
                    .putString("cmd_stop",   notEmpty(cmdStp, "S"))
                    .putString("baud_rate",  BAUDS[baudSpinner.getSelectedItemPosition()])
                    .apply();
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        setupBottomNav();
    }

    /** Returns the trimmed text if non-empty, otherwise the fallback default. */
    private String notEmpty(EditText field, String fallback) {
        String v = field.getText().toString().trim();
        return v.isEmpty() ? fallback : v;
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_settings);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if      (id == R.id.nav_devices)    startActivity(new Intent(this, DeviceActivityList.class));
            else if (id == R.id.nav_controller) startActivity(new Intent(this, ControllerActivity.class));
            else if (id == R.id.nav_terminal)   startActivity(new Intent(this, TerminalActivity.class));
            return true;
        });
    }
}
