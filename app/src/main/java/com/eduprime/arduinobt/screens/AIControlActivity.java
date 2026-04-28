package com.eduprime.arduinobt.screens;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.eduprime.arduinobt.BaseActivity;
import com.eduprime.arduinobt.R;
import com.eduprime.arduinobt.ai.ObjectDetectionManager;
import com.eduprime.arduinobt.ai.TrainingDataManager;
import com.eduprime.arduinobt.bluetooth.BluetoothService;
import com.eduprime.arduinobt.notifications.NotificationHelper;
import com.airbnb.lottie.LottieAnimationView;
import com.eduprime.arduinobt.views.DetectionOverlayView;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AIControlActivity extends BaseActivity {

    private static final int MODE_DETECT = 0, MODE_TRACK = 1, MODE_TRAIN = 2;
    private static final int REQ_CAMERA = 101;
    private static final long CMD_THROTTLE_MS = 300;

    private PreviewView previewView;
    private DetectionOverlayView overlay;
    private TextView tabDetect, tabTrack, tabTrain;
    private TextView detectedName, detectedConf, detectedCommand, detectionLabel;
    private SwitchCompat autoDriveSwitch;

    private LottieAnimationView animScanning, animDetected;

    private ObjectDetectionManager detectionManager;
    private TrainingDataManager trainingManager;
    private BluetoothService btService;
    private ExecutorService cameraExecutor;

    private int currentMode = MODE_DETECT;
    private long lastCmdTime = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_control);

        previewView     = findViewById(R.id.cameraPreview);
        overlay         = findViewById(R.id.detectionOverlay);
        tabDetect       = findViewById(R.id.tabDetect);
        tabTrack        = findViewById(R.id.tabTrack);
        tabTrain        = findViewById(R.id.tabTrain);
        detectedName    = findViewById(R.id.detectedName);
        detectedConf    = findViewById(R.id.detectedConf);
        detectedCommand = findViewById(R.id.detectedCommand);
        detectionLabel  = findViewById(R.id.detectionLabel);
        autoDriveSwitch = findViewById(R.id.autoDriveSwitch);
        animScanning    = findViewById(R.id.animScanning);
        animDetected    = findViewById(R.id.animDetected);

        btService       = BluetoothService.getInstance();
        trainingManager = new TrainingDataManager(this);
        cameraExecutor  = Executors.newSingleThreadExecutor();

        updateBtStatus();

        tabDetect.setOnClickListener(v -> setMode(MODE_DETECT));
        tabTrack.setOnClickListener(v  -> setMode(MODE_TRACK));
        tabTrain.setOnClickListener(v  -> startActivity(new Intent(this, DataCollectionActivity.class)));

        setMode(MODE_DETECT);
        setupBottomNav();
        requestCameraOrStart();
    }

    private void requestCameraOrStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    private void startCamera() {
        detectionManager = new ObjectDetectionManager();
        detectionManager.setListener((results, frameLabels, w, h) ->
                onDetectionResult(results, frameLabels, w, h));

        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider provider = ProcessCameraProvider.getInstance(this).get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, detectionManager);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception e) {
                Toast.makeText(this, "Camera error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void onDetectionResult(List<ObjectDetectionManager.DetectionResult> results,
                                    List<String> frameLabels, int imgW, int imgH) {
        handler.post(() -> {
            overlay.updateResults(results, imgW, imgH);

            if (results.isEmpty() && frameLabels.isEmpty()) {
                detectedName.setText("Nothing detected yet");
                detectedConf.setText("Point camera at something!");
                detectedCommand.setText("--");
                detectionLabel.setText("👁️ Scanning...");
                animDetected.setVisibility(android.view.View.GONE);
                animScanning.setVisibility(android.view.View.VISIBLE);
                if (!animScanning.isAnimating()) animScanning.playAnimation();
                return;
            }

            TrainingDataManager.ClassifyResult match = trainingManager.classify(frameLabels);
            String command      = null;
            String displayLabel;
            String confText;

            if (match != null) {
                displayLabel = match.className;
                command      = trainingManager.getCommand(match.className);
                int pct      = Math.round(match.confidence * 100);
                confText     = "✅ " + pct + "% match";
            } else if (!results.isEmpty()) {
                ObjectDetectionManager.DetectionResult top = results.get(0);
                displayLabel = top.label;
                int pct      = Math.round(top.confidence * 100);
                confText     = pct + "% — " + (frameLabels.isEmpty() ? "detecting..." :
                               String.join(", ", frameLabels.subList(0, Math.min(3, frameLabels.size()))));
            } else {
                displayLabel = frameLabels.isEmpty() ? "Scanning..." : frameLabels.get(0);
                confText     = String.join(", ", frameLabels.subList(0, Math.min(3, frameLabels.size())));
            }

            if (command == null && currentMode == MODE_TRACK) {
                command = detectionManager.getMotionCommand(results, imgW, imgH);
            }

            detectedName.setText(displayLabel);
            detectedConf.setText(confText);
            detectedCommand.setText(command != null ? command : "—");
            detectionLabel.setText("👁️ " + displayLabel);

            // Lottie: show detected burst on trained match, scanning pulse otherwise
            if (match != null) {
                animScanning.setVisibility(android.view.View.GONE);
                animScanning.pauseAnimation();
                animDetected.setVisibility(android.view.View.VISIBLE);
                if (!animDetected.isAnimating()) animDetected.playAnimation();
            } else {
                animDetected.setVisibility(android.view.View.GONE);
                animScanning.setVisibility(android.view.View.VISIBLE);
                if (!animScanning.isAnimating()) animScanning.playAnimation();
            }

            if (autoDriveSwitch.isChecked() && command != null && btService.isConnected()) {
                long now = System.currentTimeMillis();
                if (now - lastCmdTime > CMD_THROTTLE_MS) {
                    lastCmdTime = now;
                    btService.send(command);
                    if (match != null) {
                        NotificationHelper.notifyDetected(this, match.className, command);
                    }
                }
            }
        });
    }

    private void setMode(int mode) {
        currentMode = mode;
        tabDetect.setAlpha(mode == MODE_DETECT ? 1f : 0.5f);
        tabTrack.setAlpha(mode == MODE_TRACK   ? 1f : 0.5f);
        tabTrain.setAlpha(1f);
        overlay.clearResults();

        String hint = mode == MODE_DETECT ? "👁️ Scanning..."
                    : mode == MODE_TRACK  ? "🎯 Tracking target..." : "🎓 Teach mode";
        detectionLabel.setText(hint);
    }

    private void updateBtStatus() {
        boolean connected = btService.isConnected();
        findViewById(R.id.btStatusDot).setBackgroundResource(
                connected ? R.drawable.circle_green : R.drawable.circle_red);
        ((TextView) findViewById(R.id.btStatusText)).setText(
                connected ? "Robot Connected" : "Not Connected");
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == REQ_CAMERA && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED)
            startCamera();
        else if (req == REQ_CAMERA)
            Toast.makeText(this, "Camera permission required for AI features", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBtStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (detectionManager != null) detectionManager.shutdown();
        cameraExecutor.shutdown();
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_ai);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if      (id == R.id.nav_devices)    startActivity(new Intent(this, DeviceActivityList.class));
            else if (id == R.id.nav_controller) startActivity(new Intent(this, ControllerActivity.class));
            else if (id == R.id.nav_terminal)   startActivity(new Intent(this, TerminalActivity.class));
            else if (id == R.id.nav_settings)   startActivity(new Intent(this, SettingsActivity.class));
            return true;
        });
    }
}
