package com.eduprime.arduinobt;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class IoTButtonSettingsActivity extends BaseActivity {

    static final String PREFS = "iot_button_cmds";

    // Keys
    static final String K_UP    = "up";
    static final String K_DOWN  = "down";
    static final String K_LEFT  = "left";
    static final String K_RIGHT = "right";
    static final String K_STOP  = "stop";
    static final String K_1 = "1"; static final String K_2 = "2"; static final String K_3 = "3";
    static final String K_4 = "4"; static final String K_5 = "5"; static final String K_6 = "6";
    static final String K_7 = "7"; static final String K_8 = "8"; static final String K_9 = "9";

    // Defaults
    static final String D_UP    = "UP";
    static final String D_DOWN  = "DOWN";
    static final String D_LEFT  = "LEFT";
    static final String D_RIGHT = "RIGHT";
    static final String D_STOP  = "STOP";
    static final String D_1 = "one"; static final String D_2 = "two";   static final String D_3 = "three";
    static final String D_4 = "four"; static final String D_5 = "five"; static final String D_6 = "six";
    static final String D_7 = "seven"; static final String D_8 = "eight"; static final String D_9 = "nine";

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iot_button_settings);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        TextView saveBtn = findViewById(R.id.saveBtn);
        saveBtn.setOnClickListener(v -> save());

        TextView resetBtn = findViewById(R.id.resetBtn);
        resetBtn.setOnClickListener(v -> resetToDefaults());

        loadIntoFields();
    }

    private void loadIntoFields() {
        setField(R.id.cmdUp,    prefs.getString(K_UP,    D_UP));
        setField(R.id.cmdDown,  prefs.getString(K_DOWN,  D_DOWN));
        setField(R.id.cmdLeft,  prefs.getString(K_LEFT,  D_LEFT));
        setField(R.id.cmdRight, prefs.getString(K_RIGHT, D_RIGHT));
        setField(R.id.cmdStop,  prefs.getString(K_STOP,  D_STOP));
        setField(R.id.cmd1,     prefs.getString(K_1, D_1));
        setField(R.id.cmd2,     prefs.getString(K_2, D_2));
        setField(R.id.cmd3,     prefs.getString(K_3, D_3));
        setField(R.id.cmd4,     prefs.getString(K_4, D_4));
        setField(R.id.cmd5,     prefs.getString(K_5, D_5));
        setField(R.id.cmd6,     prefs.getString(K_6, D_6));
        setField(R.id.cmd7,     prefs.getString(K_7, D_7));
        setField(R.id.cmd8,     prefs.getString(K_8, D_8));
        setField(R.id.cmd9,     prefs.getString(K_9, D_9));
    }

    private void save() {
        SharedPreferences.Editor ed = prefs.edit();
        ed.putString(K_UP,    getField(R.id.cmdUp,    D_UP));
        ed.putString(K_DOWN,  getField(R.id.cmdDown,  D_DOWN));
        ed.putString(K_LEFT,  getField(R.id.cmdLeft,  D_LEFT));
        ed.putString(K_RIGHT, getField(R.id.cmdRight, D_RIGHT));
        ed.putString(K_STOP,  getField(R.id.cmdStop,  D_STOP));
        ed.putString(K_1,     getField(R.id.cmd1, D_1));
        ed.putString(K_2,     getField(R.id.cmd2, D_2));
        ed.putString(K_3,     getField(R.id.cmd3, D_3));
        ed.putString(K_4,     getField(R.id.cmd4, D_4));
        ed.putString(K_5,     getField(R.id.cmd5, D_5));
        ed.putString(K_6,     getField(R.id.cmd6, D_6));
        ed.putString(K_7,     getField(R.id.cmd7, D_7));
        ed.putString(K_8,     getField(R.id.cmd8, D_8));
        ed.putString(K_9,     getField(R.id.cmd9, D_9));
        ed.apply();
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void resetToDefaults() {
        prefs.edit().clear().apply();
        loadIntoFields();
        Toast.makeText(this, "Reset to defaults", Toast.LENGTH_SHORT).show();
    }

    private void setField(int id, String value) {
        ((EditText) findViewById(id)).setText(value);
    }

    private String getField(int id, String fallback) {
        String v = ((EditText) findViewById(id)).getText().toString().trim();
        return v.isEmpty() ? fallback : v;
    }
}
