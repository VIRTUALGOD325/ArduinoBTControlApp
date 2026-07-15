package com.eduprime.arduinobt.screens;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.eduprime.arduinobt.BaseActivity;
import com.eduprime.arduinobt.R;
import com.eduprime.arduinobt.bluetooth.BluetoothService;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class SettingsActivity extends BaseActivity {

    private static final String[] BAUDS = {"9600", "19200", "38400", "57600", "115200"};
    // Display labels shown in the spinner; values are what actually gets appended to each command.
    private static final String[] LINE_ENDING_LABELS = {"None", "Newline  (\\n)", "CR + LF  (\\r\\n)"};
    private static final String[] LINE_ENDING_VALUES = {"", "\n", "\r\n"};
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

        // Per-direction "no Stop" flags (for toggle-style outputs mapped to a D-pad button)
        CheckBox noStopFwd = findViewById(R.id.noStopForward);
        CheckBox noStopBck = findViewById(R.id.noStopBack);
        CheckBox noStopLft = findViewById(R.id.noStopLeft);
        CheckBox noStopRgt = findViewById(R.id.noStopRight);
        noStopFwd.setChecked(prefs.getBoolean("nostop_fwd",   false));
        noStopBck.setChecked(prefs.getBoolean("nostop_back",  false));
        noStopLft.setChecked(prefs.getBoolean("nostop_left",  false));
        noStopRgt.setChecked(prefs.getBoolean("nostop_right", false));

        // Joystick commands
        EditText joyUp    = findViewById(R.id.joyUp);
        EditText joyDown  = findViewById(R.id.joyDown);
        EditText joyLeft  = findViewById(R.id.joyLeft);
        EditText joyRight = findViewById(R.id.joyRight);
        EditText joyStop  = findViewById(R.id.joyStop);
        EditText joyUR    = findViewById(R.id.joyUR);
        EditText joyUL    = findViewById(R.id.joyUL);
        EditText joyDR    = findViewById(R.id.joyDR);
        EditText joyDL    = findViewById(R.id.joyDL);

        joyUp.setText(prefs.getString("joy_up",    "F"));
        joyDown.setText(prefs.getString("joy_down", "B"));
        joyLeft.setText(prefs.getString("joy_left", "L"));
        joyRight.setText(prefs.getString("joy_right","R"));
        joyStop.setText(prefs.getString("joy_stop", "S"));
        joyUR.setText(prefs.getString("joy_ur",     "FR"));
        joyUL.setText(prefs.getString("joy_ul",     "FL"));
        joyDR.setText(prefs.getString("joy_dr",     "BR"));
        joyDL.setText(prefs.getString("joy_dl",     "BL"));

        // Numpad commands — collapsible section
        View numpadBody   = findViewById(R.id.numpadBody);
        View numpadHeader = findViewById(R.id.numpadHeader);
        TextView numpadArrow = findViewById(R.id.numpadArrow);
        final boolean[] numpadExpanded = {false};
        numpadHeader.setOnClickListener(v -> {
            numpadExpanded[0] = !numpadExpanded[0];
            numpadBody.setVisibility(numpadExpanded[0] ? View.VISIBLE : View.GONE);
            ObjectAnimator.ofFloat(numpadArrow, "rotation",
                    numpadExpanded[0] ? 180f : 0f).setDuration(200).start();
        });

        EditText numCmd0    = findViewById(R.id.numCmd0);
        EditText numCmd1    = findViewById(R.id.numCmd1);
        EditText numCmd2    = findViewById(R.id.numCmd2);
        EditText numCmd3    = findViewById(R.id.numCmd3);
        EditText numCmd4    = findViewById(R.id.numCmd4);
        EditText numCmd5    = findViewById(R.id.numCmd5);
        EditText numCmd6    = findViewById(R.id.numCmd6);
        EditText numCmd7    = findViewById(R.id.numCmd7);
        EditText numCmd8    = findViewById(R.id.numCmd8);
        EditText numCmd9    = findViewById(R.id.numCmd9);
        EditText numCmdStar = findViewById(R.id.numCmdStar);
        EditText numCmdHash = findViewById(R.id.numCmdHash);

        numCmd0.setText(prefs.getString("numpad_0",    "0"));
        numCmd1.setText(prefs.getString("numpad_1",    "1"));
        numCmd2.setText(prefs.getString("numpad_2",    "2"));
        numCmd3.setText(prefs.getString("numpad_3",    "3"));
        numCmd4.setText(prefs.getString("numpad_4",    "4"));
        numCmd5.setText(prefs.getString("numpad_5",    "5"));
        numCmd6.setText(prefs.getString("numpad_6",    "6"));
        numCmd7.setText(prefs.getString("numpad_7",    "7"));
        numCmd8.setText(prefs.getString("numpad_8",    "8"));
        numCmd9.setText(prefs.getString("numpad_9",    "9"));
        numCmdStar.setText(prefs.getString("numpad_star", "*"));
        numCmdHash.setText(prefs.getString("numpad_hash", "#"));

        // Baud rate
        Spinner baudSpinner = findViewById(R.id.baudSpinner);
        ArrayAdapter<String> baudAdapter = new ArrayAdapter<>(this,
                R.layout.spinner_item_dark, BAUDS);
        baudAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);
        baudSpinner.setAdapter(baudAdapter);
        String saved = prefs.getString("baud_rate", "9600");
        for (int i = 0; i < BAUDS.length; i++) {
            if (BAUDS[i].equals(saved)) { baudSpinner.setSelection(i); break; }
        }

        // Line ending appended to every command
        Spinner lineEndingSpinner = findViewById(R.id.lineEndingSpinner);
        ArrayAdapter<String> leAdapter = new ArrayAdapter<>(this,
                R.layout.spinner_item_dark, LINE_ENDING_LABELS);
        leAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);
        lineEndingSpinner.setAdapter(leAdapter);
        String savedEnding = prefs.getString("line_ending", "\n");
        for (int i = 0; i < LINE_ENDING_VALUES.length; i++) {
            if (LINE_ENDING_VALUES[i].equals(savedEnding)) { lineEndingSpinner.setSelection(i); break; }
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
                    .putBoolean("nostop_fwd",   noStopFwd.isChecked())
                    .putBoolean("nostop_back",  noStopBck.isChecked())
                    .putBoolean("nostop_left",  noStopLft.isChecked())
                    .putBoolean("nostop_right", noStopRgt.isChecked())
                    // Joystick
                    .putString("joy_up",     notEmpty(joyUp,    "F"))
                    .putString("joy_down",   notEmpty(joyDown,  "B"))
                    .putString("joy_left",   notEmpty(joyLeft,  "L"))
                    .putString("joy_right",  notEmpty(joyRight, "R"))
                    .putString("joy_stop",   notEmpty(joyStop,  "S"))
                    .putString("joy_ur",     notEmpty(joyUR,    "FR"))
                    .putString("joy_ul",     notEmpty(joyUL,    "FL"))
                    .putString("joy_dr",     notEmpty(joyDR,    "BR"))
                    .putString("joy_dl",     notEmpty(joyDL,    "BL"))
                    // Numpad
                    .putString("numpad_0",   notEmpty(numCmd0,    "0"))
                    .putString("numpad_1",   notEmpty(numCmd1,    "1"))
                    .putString("numpad_2",   notEmpty(numCmd2,    "2"))
                    .putString("numpad_3",   notEmpty(numCmd3,    "3"))
                    .putString("numpad_4",   notEmpty(numCmd4,    "4"))
                    .putString("numpad_5",   notEmpty(numCmd5,    "5"))
                    .putString("numpad_6",   notEmpty(numCmd6,    "6"))
                    .putString("numpad_7",   notEmpty(numCmd7,    "7"))
                    .putString("numpad_8",   notEmpty(numCmd8,    "8"))
                    .putString("numpad_9",   notEmpty(numCmd9,    "9"))
                    .putString("numpad_star",notEmpty(numCmdStar, "*"))
                    .putString("numpad_hash",notEmpty(numCmdHash, "#"))
                    .putString("baud_rate",  BAUDS[baudSpinner.getSelectedItemPosition()])
                    .putString("line_ending", LINE_ENDING_VALUES[lineEndingSpinner.getSelectedItemPosition()])
                    .apply();
            // Apply the line ending to the live connection immediately.
            BluetoothService.getInstance()
                    .setLineEnding(LINE_ENDING_VALUES[lineEndingSpinner.getSelectedItemPosition()]);
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
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if      (id == R.id.nav_settings)   return true; // already here
            else if (id == R.id.nav_devices)    navigateTo(DeviceActivityList.class);
            else if (id == R.id.nav_controller) navigateTo(ControllerActivity.class);
            else if (id == R.id.nav_terminal)   navigateTo(TerminalActivity.class);
            else if (id == R.id.nav_ai)         navigateTo(AIControlActivity.class);
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        if (nav != null) nav.setSelectedItemId(R.id.nav_settings);
    }
}
