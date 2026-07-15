package com.eduprime.arduinobt;

import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.firebase.database.DatabaseReference;

public class IoTSliderActivity extends BaseActivity {

    private SeekBar seekBar;
    private TextView valueLabel, percentLabel;
    private DatabaseReference sliderRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iot_slider);

        String uid = getIntent().getStringExtra("userGeneratedUID");
        if (uid == null) { finish(); return; }

        sliderRef    = IoTFirebaseManager.getDatabase(this).getReference("commands/" + uid + "/slider");
        valueLabel   = findViewById(R.id.valueLabel);
        percentLabel = findViewById(R.id.percentLabel);
        seekBar      = findViewById(R.id.seekBar);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        seekBar.setMax(255);
        seekBar.setProgress(128);
        updateDisplay(128);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateDisplay(progress);
                sliderRef.setValue(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        // Preset buttons
        bindPreset(R.id.preset0,   0);
        bindPreset(R.id.preset25,  64);
        bindPreset(R.id.preset50,  128);
        bindPreset(R.id.preset75,  191);
        bindPreset(R.id.preset100, 255);
    }

    private void bindPreset(int viewId, int value) {
        findViewById(viewId).setOnClickListener(v -> {
            seekBar.setProgress(value);
            updateDisplay(value);
            sliderRef.setValue(value);
        });
    }

    private void updateDisplay(int value) {
        valueLabel.setText(String.valueOf(value));
        int pct = Math.round(value / 255f * 100f);
        percentLabel.setText(pct + "%");
    }
}
