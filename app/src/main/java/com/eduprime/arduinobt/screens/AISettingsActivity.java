package com.eduprime.arduinobt.screens;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.eduprime.arduinobt.BaseActivity;
import com.eduprime.arduinobt.R;
import com.eduprime.arduinobt.AI.TrainingDataManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class AISettingsActivity extends BaseActivity {

    private static final String PREFS = "ml_params";

    private TrainingDataManager trainingManager;
    private SharedPreferences prefs;

    private LinearLayout classListContainer;
    private View emptyState;

    private TextView tvConfidenceValue, tvMatchValue, tvThrottleValue;
    private SeekBar seekConfidence, seekMatch, seekThrottle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_settings);

        trainingManager = new TrainingDataManager(this);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        classListContainer = findViewById(R.id.classListContainer);
        emptyState         = findViewById(R.id.emptyState);

        tvConfidenceValue = findViewById(R.id.tvConfidenceValue);
        tvMatchValue      = findViewById(R.id.tvMatchValue);
        tvThrottleValue   = findViewById(R.id.tvThrottleValue);
        seekConfidence    = findViewById(R.id.seekConfidence);
        seekMatch         = findViewById(R.id.seekMatch);
        seekThrottle      = findViewById(R.id.seekThrottle);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnClearAll).setOnClickListener(v -> confirmClearAll());

        setupTuningSection();
        // renderClassList() is called in onResume() — no need to call it here too
    }

    // ── Class list ───────────────────────────────────────────────────────────

    private void renderClassList() {
        classListContainer.removeAllViews();
        List<TrainingDataManager.TrainingClass> classes = trainingManager.getClasses();

        if (classes.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            return;
        }

        emptyState.setVisibility(View.GONE);

        for (TrainingDataManager.TrainingClass tc : classes) {
            View row = LayoutInflater.from(this)
                    .inflate(R.layout.item_ai_class_setting, classListContainer, false);

            ((TextView) row.findViewById(R.id.classEmoji)).setText(
                    tc.emoji != null ? tc.emoji : "🎯");
            ((TextView) row.findViewById(R.id.className)).setText(
                    tc.name != null ? tc.name : "");

            int count = tc.sampleLabels != null ? tc.sampleLabels.size() : 0;
            ((TextView) row.findViewById(R.id.sampleInfo)).setText(
                    count + " photo" + (count == 1 ? "" : "s") + " trained");

            EditText cmdInput = row.findViewById(R.id.classCommand);
            cmdInput.setText(tc.command);

            String className = tc.name;
            cmdInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                @Override public void afterTextChanged(Editable s) {
                    String cmd = s.toString().trim();
                    if (!cmd.isEmpty()) trainingManager.setCommand(className, cmd);
                }
            });

            classListContainer.addView(row);
        }
    }

    // ── Tuning section ───────────────────────────────────────────────────────

    private void setupTuningSection() {
        int savedConf     = prefs.getInt("confidence", 0);
        int savedMatch    = prefs.getInt("matchThreshold", 15);
        int savedThrottle = prefs.getInt("throttleSteps", 2); // step index; 0=100ms … 9=1000ms

        seekConfidence.setProgress(savedConf);
        seekMatch.setProgress(savedMatch);
        seekThrottle.setProgress(savedThrottle);

        tvConfidenceValue.setText(savedConf + "%");
        tvMatchValue.setText(savedMatch + "%");
        tvThrottleValue.setText(stepsToMs(savedThrottle) + " ms");

        seekConfidence.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                tvConfidenceValue.setText(p + "%");
                if (fromUser) prefs.edit().putInt("confidence", p).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });

        seekMatch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                tvMatchValue.setText(p + "%");
                if (fromUser) prefs.edit().putInt("matchThreshold", p).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });

        seekThrottle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                tvThrottleValue.setText(stepsToMs(p) + " ms");
                if (fromUser) prefs.edit().putInt("throttleSteps", p).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
    }

    /** Converts seekbar step (0–9) to milliseconds (100ms–1000ms in 100ms increments). */
    private static int stepsToMs(int steps) {
        return (steps + 1) * 100;
    }

    // ── Clear all ────────────────────────────────────────────────────────────

    private void confirmClearAll() {
        int classCount  = trainingManager.getClasses().size();
        int sampleCount = trainingManager.getTotalSampleCount();

        if (classCount == 0) {
            showToast("Nothing to clear — already empty");
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear All Training Data?")
                .setMessage("This will permanently delete " + classCount
                        + " object" + (classCount == 1 ? "" : "s") + " and "
                        + sampleCount + " photo" + (sampleCount == 1 ? "" : "s") + ".\n\n"
                        + "Consider exporting a backup first from the Teach panel.")
                .setPositiveButton("DELETE ALL", (d, w) -> {
                    trainingManager.clearAll();
                    renderClassList();
                    showToast("Training data cleared");
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void showToast(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    // ── Refresh on resume (data may have changed in Teach mode) ──────────────

    @Override
    protected void onResume() {
        super.onResume();
        renderClassList();
    }
}
