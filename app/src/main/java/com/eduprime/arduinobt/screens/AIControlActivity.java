package com.eduprime.arduinobt.screens;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.eduprime.arduinobt.BaseActivity;
import com.eduprime.arduinobt.ConnectionTypeActivity;
import com.eduprime.arduinobt.R;
import com.eduprime.arduinobt.AI.ObjectDetectionManager;
import com.eduprime.arduinobt.AI.TrainingDataManager;
import com.eduprime.arduinobt.bluetooth.BluetoothService;
import com.eduprime.arduinobt.notifications.NotificationHelper;
import com.eduprime.arduinobt.views.DetectionOverlayView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AIControlActivity extends BaseActivity {

    private static final int MODE_DETECT = 0, MODE_TRACK = 1, MODE_TEACH = 2;
    private static final int REQ_CAMERA = 101, REQ_STORAGE = 103;
    private long cmdThrottleMs = 300;
    private static final String PREFS = "ml_params";

    // ── Camera ───────────────────────────────────────────────────────────────
    private ProcessCameraProvider cameraProvider;
    private Preview cameraPreview;
    private PreviewView previewView;
    private DetectionOverlayView overlay;
    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;

    // ── AI ───────────────────────────────────────────────────────────────────
    private ObjectDetectionManager detectionManager;
    private TrainingDataManager trainingManager;
    private BluetoothService btService;

    // ── Detect/Track UI ──────────────────────────────────────────────────────
    private TextView detectedName, detectedConf, detectedCommand, modeLabel;
    private SwitchCompat autoDriveSwitch;
    private SeekBar seekConfidence, seekMatchThreshold;
    private TextView tvConfidenceValue, tvMatchThresholdValue;
    private LottieAnimationView animScanning, animDetected;
    private LinearLayout detectTrackPanel;

    // ── Teach UI ─────────────────────────────────────────────────────────────
    private TextView teachClassName, teachSampleCount, btnCapture;
    private EditText teachCommandInput;
    private RecyclerView classList;
    private ClassAdapter classAdapter;
    private LinearLayout teachPanel;

    // ── Tabs ─────────────────────────────────────────────────────────────────
    private TextView tabDetect, tabTrack, tabTrain;

    // ── State ─────────────────────────────────────────────────────────────────
    private int currentMode = MODE_DETECT;
    private long lastCmdTime = 0;
    private String selectedClass = null;
    private String pendingExportJson = null;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> importLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) handleImportUri(uri);
            });

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_control);

        bindViews();

        btService       = BluetoothService.getInstance();
        detectionManager = new ObjectDetectionManager();
        detectionManager.setListener(this::onDetectionResult);
        trainingManager = new TrainingDataManager(this);
        cameraExecutor  = Executors.newSingleThreadExecutor();

        updateBtStatus();
        setupMlParams();
        setupTabs();
        setupTeachPanel();
        setupBottomBar();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { goToConnectionScreen(); }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            initCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    private void bindViews() {
        previewView           = findViewById(R.id.cameraPreview);
        overlay               = findViewById(R.id.detectionOverlay);
        tabDetect             = findViewById(R.id.tabDetect);
        tabTrack              = findViewById(R.id.tabTrack);
        tabTrain              = findViewById(R.id.tabTrain);
        modeLabel             = findViewById(R.id.modeLabel);
        detectedName          = findViewById(R.id.detectedName);
        detectedConf          = findViewById(R.id.detectedConf);
        detectedCommand       = findViewById(R.id.detectedCommand);
        autoDriveSwitch       = findViewById(R.id.autoDriveSwitch);
        animScanning          = findViewById(R.id.animScanning);
        animDetected          = findViewById(R.id.animDetected);
        seekConfidence        = findViewById(R.id.seekConfidence);
        seekMatchThreshold    = findViewById(R.id.seekMatchThreshold);
        tvConfidenceValue     = findViewById(R.id.tvConfidenceValue);
        tvMatchThresholdValue = findViewById(R.id.tvMatchThresholdValue);
        detectTrackPanel      = findViewById(R.id.detectTrackPanel);
        teachPanel            = findViewById(R.id.teachPanel);
        teachClassName        = findViewById(R.id.teachClassName);
        teachSampleCount      = findViewById(R.id.teachSampleCount);
        teachCommandInput     = findViewById(R.id.teachCommandInput);
        btnCapture            = findViewById(R.id.btnCapture);
        classList             = findViewById(R.id.classList);
    }

    // ── Modes ────────────────────────────────────────────────────────────────

    private void setupTabs() {
        tabDetect.setOnClickListener(v -> setMode(MODE_DETECT));
        tabTrack.setOnClickListener(v  -> setMode(MODE_TRACK));
        tabTrain.setOnClickListener(v  -> setMode(MODE_TEACH));
        setMode(MODE_DETECT);
    }

    private void setMode(int mode) {
        boolean wasTeach = currentMode == MODE_TEACH;
        boolean isTeach  = mode == MODE_TEACH;
        currentMode = mode;

        tabDetect.setAlpha(mode == MODE_DETECT ? 1f : 0.4f);
        tabTrack.setAlpha( mode == MODE_TRACK  ? 1f : 0.4f);
        tabTrain.setAlpha( mode == MODE_TEACH  ? 1f : 0.4f);

        detectTrackPanel.setVisibility(isTeach ? View.GONE : View.VISIBLE);
        teachPanel.setVisibility(isTeach ? View.VISIBLE : View.GONE);

        overlay.setVisibility(isTeach ? View.GONE : View.VISIBLE);
        if (isTeach) overlay.clearResults();

        modeLabel.setText(mode == MODE_DETECT ? "👁️ Scanning..."
                        : mode == MODE_TRACK   ? "🎯 Tracking..."
                                               : "🎓 Teach Mode");

        // Swap camera use-cases only when crossing the detect↔teach boundary
        if (wasTeach != isTeach && cameraProvider != null) {
            if (isTeach) bindTeachUseCase();
            else         bindDetectUseCase();
        }
    }

    // ── Camera ───────────────────────────────────────────────────────────────

    private void initCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                cameraProvider = ProcessCameraProvider.getInstance(this).get();
                cameraPreview  = new Preview.Builder().build();
                cameraPreview.setSurfaceProvider(previewView.getSurfaceProvider());
                if (currentMode == MODE_TEACH) bindTeachUseCase();
                else                           bindDetectUseCase();
            } catch (Exception e) {
                Toast.makeText(this, "Camera error: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindDetectUseCase() {
        if (cameraProvider == null || cameraPreview == null) return;
        detectionManager.setConfidenceThreshold(seekConfidence.getProgress() / 100f);
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(cameraExecutor, detectionManager);
        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this,
                CameraSelector.DEFAULT_BACK_CAMERA, cameraPreview, analysis);
        imageCapture = null;
    }

    private void bindTeachUseCase() {
        if (cameraProvider == null || cameraPreview == null) return;
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();
        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this,
                CameraSelector.DEFAULT_BACK_CAMERA, cameraPreview, imageCapture);
    }

    // ── Detection results (Detect / Track modes) ──────────────────────────────

    private void onDetectionResult(List<ObjectDetectionManager.DetectionResult> results,
                                   List<String> frameLabels, int imgW, int imgH) {
        if (currentMode == MODE_TEACH) return;
        handler.post(() -> {
            if (isDestroyed() || isFinishing()) return;
            overlay.updateResults(results, imgW, imgH);

            if (results.isEmpty() && frameLabels.isEmpty()) {
                detectedName.setText("Nothing detected yet");
                detectedConf.setText("Point camera at something!");
                detectedCommand.setText("--");
                setAnimState(false);
                return;
            }

            TrainingDataManager.ClassifyResult match = trainingManager.classify(frameLabels);
            String command;
            String displayLabel;
            String confText;

            if (match != null) {
                displayLabel = match.className;
                command      = trainingManager.getCommand(match.className);
                confText     = "✅ " + Math.round(match.confidence * 100) + "% match";
            } else if (!results.isEmpty()) {
                ObjectDetectionManager.DetectionResult top = results.get(0);
                displayLabel = top.label;
                command      = null;
                confText     = Math.round(top.confidence * 100) + "% — "
                        + (frameLabels.isEmpty() ? "detecting..."
                        : String.join(", ", frameLabels.subList(0, Math.min(3, frameLabels.size()))));
            } else {
                displayLabel = frameLabels.isEmpty() ? "Scanning..." : frameLabels.get(0);
                command      = null;
                confText     = String.join(", ", frameLabels.subList(0, Math.min(3, frameLabels.size())));
            }

            // Fall back to positional tracking when there's no (usable) class command.
            if ((command == null || command.trim().isEmpty()) && currentMode == MODE_TRACK) {
                command = detectionManager.getMotionCommand(results, imgW, imgH);
            }

            boolean hasCommand = command != null && !command.trim().isEmpty();

            detectedName.setText(displayLabel);
            detectedConf.setText(confText);
            detectedCommand.setText(hasCommand ? command : "—");
            modeLabel.setText("👁️ " + displayLabel);
            setAnimState(match != null);

            if (autoDriveSwitch.isChecked() && hasCommand && btService.isConnected()) {
                long now = System.currentTimeMillis();
                if (now - lastCmdTime > cmdThrottleMs) {
                    lastCmdTime = now;
                    btService.send(command);
                    if (match != null) {
                        NotificationHelper.notifyDetected(this, match.className, command);
                    }
                }
            }
        });
    }

    private void setAnimState(boolean detected) {
        if (detected) {
            animScanning.setVisibility(View.GONE);
            animScanning.pauseAnimation();
            animDetected.setVisibility(View.VISIBLE);
            if (!animDetected.isAnimating()) animDetected.playAnimation();
        } else {
            animDetected.setVisibility(View.GONE);
            animScanning.setVisibility(View.VISIBLE);
            if (!animScanning.isAnimating()) animScanning.playAnimation();
        }
    }

    // ── Teach panel ──────────────────────────────────────────────────────────

    private void setupTeachPanel() {
        classList.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        classAdapter = new ClassAdapter(trainingManager.getClasses());
        classList.setAdapter(classAdapter);
        refreshClassList();
        updateTeachDisplay();

        teachCommandInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (selectedClass != null && s.length() > 0) {
                    trainingManager.setCommand(selectedClass, s.toString().trim());
                }
            }
        });

        findViewById(R.id.btnAddClass).setOnClickListener(v -> showAddClassDialog());
        btnCapture.setOnClickListener(v -> capturePhoto());
        findViewById(R.id.btnExport).setOnClickListener(v -> exportModel());
        findViewById(R.id.btnImport).setOnClickListener(v -> importLauncher.launch("*/*"));
    }

    private void capturePhoto() {
        if (selectedClass == null) {
            Toast.makeText(this, "Pick a class first! 👆", Toast.LENGTH_SHORT).show();
            return;
        }
        if (imageCapture == null) {
            Toast.makeText(this, "Camera not ready — wait a moment", Toast.LENGTH_SHORT).show();
            return;
        }
        btnCapture.setAlpha(0.5f);
        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                Bitmap bitmap = imageProxyToBitmap(image);
                image.close();
                if (bitmap == null) { btnCapture.setAlpha(1f); return; }
                detectionManager.labelBitmap(bitmap, labels -> {
                    if (labels.isEmpty()) {
                        Toast.makeText(AIControlActivity.this,
                                "⚠️ No labels — try better lighting or angle",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        trainingManager.addSample(selectedClass, bitmap, labels);
                        refreshClassList();
                        updateTeachDisplay();
                        Toast.makeText(AIControlActivity.this,
                                "📸 Saved! " + String.join(", ",
                                        labels.subList(0, Math.min(3, labels.size()))),
                                Toast.LENGTH_SHORT).show();
                    }
                    btnCapture.setAlpha(1f);
                });
            }
            @Override
            public void onError(@NonNull ImageCaptureException e) {
                btnCapture.setAlpha(1f);
                Toast.makeText(AIControlActivity.this,
                        "Capture failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            java.nio.ByteBuffer buf = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            Matrix matrix = new Matrix();
            matrix.postRotate(image.getImageInfo().getRotationDegrees());
            return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        } catch (Exception e) { return null; }
    }

    private void showAddClassDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_add_class, null, false);
        EditText nameInput  = dialogView.findViewById(R.id.classNameInput);
        EditText emojiInput = dialogView.findViewById(R.id.classEmojiInput);
        EditText cmdInput   = dialogView.findViewById(R.id.classCommandInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle("🎓 Add New Thing to Teach")
                .setView(dialogView)
                .setPositiveButton("ADD", (d, w) -> {
                    String name  = nameInput.getText().toString().trim();
                    String emoji = emojiInput.getText().toString().trim();
                    String cmd   = cmdInput.getText().toString().trim();
                    if (name.isEmpty()) return;
                    if (emoji.isEmpty()) emoji = "🎯";
                    if (cmd.isEmpty())   cmd   = "F";
                    trainingManager.addClass(name, emoji, cmd);
                    selectedClass = name;
                    refreshClassList();
                    updateTeachDisplay();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void refreshClassList() {
        if (classAdapter != null)
            classAdapter.updateData(trainingManager.getClasses());
    }

    private void updateTeachDisplay() {
        if (selectedClass == null) {
            teachClassName.setText("No class selected");
            teachSampleCount.setText("Tap a class below to select");
            teachCommandInput.setText("");
        } else {
            teachClassName.setText(selectedClass);
            int count = trainingManager.getSampleCount(selectedClass);
            teachSampleCount.setText(count + " photo" + (count == 1 ? "" : "s") + " taken");
            String cmd = trainingManager.getCommand(selectedClass);
            teachCommandInput.setText(cmd != null ? cmd : "");
        }
    }

    // ── Export / Import ──────────────────────────────────────────────────────

    private void exportModel() {
        if (trainingManager.getClasses().isEmpty()) {
            Toast.makeText(this, "Nothing trained yet!", Toast.LENGTH_SHORT).show();
            return;
        }
        String json      = trainingManager.exportToJson();
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String filename  = "robot_model_" + timestamp + ".arduinoai";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeToDownloadsQ(json, filename);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingExportJson = json;
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            } else {
                writeToDownloadsLegacy(json, filename);
            }
        }
    }

    private void writeToDownloadsQ(String json, String filename) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri collection = MediaStore.Downloads.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri uri = getContentResolver().insert(collection, values);
            if (uri == null) throw new Exception("MediaStore insert failed");
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new Exception("Cannot open output stream");
                os.write(json.getBytes("UTF-8"));
            }
            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
            Toast.makeText(this, "✅ Exported: " + filename, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void writeToDownloadsLegacy(String json, String filename) {
        try {
            File dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            dir.mkdirs();
            try (FileWriter fw = new FileWriter(new File(dir, filename))) {
                fw.write(json);
            }
            Toast.makeText(this, "✅ Exported to Downloads/" + filename,
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleImportUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) throw new Exception("Cannot read file");
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) sb.append(new String(buf, 0, n, "UTF-8"));
            is.close();
            String json = sb.toString();
            if (!json.contains("\"classes\"")) {
                Toast.makeText(this, "Not a valid .arduinoai file", Toast.LENGTH_SHORT).show();
                return;
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle("📂 Load Model")
                    .setMessage("How do you want to load this model?")
                    .setPositiveButton("MERGE with existing", (d, w) -> {
                        if (trainingManager.importFromJson(json, true)) {
                            refreshClassList();
                            Toast.makeText(this, "✅ Merged — "
                                    + trainingManager.getClasses().size() + " classes",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Import failed", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("REPLACE all", (d, w) ->
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle("Replace all training data?")
                                    .setMessage("All existing training will be deleted. Are you sure?")
                                    .setPositiveButton("YES, REPLACE", (d2, w2) -> {
                                        if (trainingManager.importFromJson(json, false)) {
                                            selectedClass = null;
                                            refreshClassList();
                                            updateTeachDisplay();
                                            Toast.makeText(this, "✅ Loaded — "
                                                    + trainingManager.getClasses().size() + " classes",
                                                    Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(this, "Import failed",
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    })
                                    .setNegativeButton("CANCEL", null)
                                    .show())
                    .setNeutralButton("CANCEL", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to read file: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ── ML parameters ────────────────────────────────────────────────────────

    private void setupMlParams() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int savedConf  = prefs.getInt("confidence", 0);
        int savedMatch = prefs.getInt("matchThreshold", 15);

        seekConfidence.setProgress(savedConf);
        seekMatchThreshold.setProgress(savedMatch);
        tvConfidenceValue.setText(savedConf + "%");
        tvMatchThresholdValue.setText(savedMatch + "%");
        trainingManager.setMatchThreshold(savedMatch / 100f);

        View header   = findViewById(R.id.mlParamsHeader);
        View body     = findViewById(R.id.mlParamsBody);
        TextView arrow = findViewById(R.id.mlParamsArrow);
        header.setOnClickListener(v -> {
            boolean visible = body.getVisibility() == View.VISIBLE;
            body.setVisibility(visible ? View.GONE : View.VISIBLE);
            arrow.setText(visible ? "▼" : "▲");
        });

        seekConfidence.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                tvConfidenceValue.setText(p + "%");
                detectionManager.setConfidenceThreshold(p / 100f);
                if (fromUser) prefs.edit().putInt("confidence", p).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });

        seekMatchThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                tvMatchThresholdValue.setText(p + "%");
                trainingManager.setMatchThreshold(p / 100f);
                if (fromUser) prefs.edit().putInt("matchThreshold", p).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
    }

    // ── BT status ────────────────────────────────────────────────────────────

    private void updateBtStatus() {
        boolean connected = btService.isConnected();
        findViewById(R.id.btStatusDot).setBackgroundResource(
                connected ? R.drawable.circle_green : R.drawable.circle_red);
        ((TextView) findViewById(R.id.btStatusText)).setText(
                connected ? "Robot Connected" : "Not Connected");
    }

    // ── Bottom bar ───────────────────────────────────────────────────────────

    private void setupBottomBar() {
        findViewById(R.id.btnNavBack).setOnClickListener(v -> goToConnectionScreen());
        findViewById(R.id.btnNavSettings).setOnClickListener(v ->
                navigateTo(AISettingsActivity.class));
    }

    private void goToConnectionScreen() {
        if (detectionManager != null) detectionManager.setListener(null);
        // Unbind camera synchronously before leaving — prevents race in onDestroy
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
        Intent intent = new Intent(this, ConnectionTypeActivity.class);
        // CLEAR_TASK wipes the whole back stack so ConnectionTypeActivity starts clean
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        // No finish() needed — CLEAR_TASK removes this activity automatically
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onPause() {
        super.onPause();
        // Stop detection callbacks while activity is not in foreground
        if (detectionManager != null) detectionManager.setListener(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-register detection listener after returning from Settings or other screens
        if (detectionManager != null) detectionManager.setListener(this::onDetectionResult);
        updateBtStatus();
        // Reload settings that may have changed in AISettingsActivity
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int throttleSteps = prefs.getInt("throttleSteps", 2);
        cmdThrottleMs = (throttleSteps + 1) * 100L;
        trainingManager.setMatchThreshold(prefs.getInt("matchThreshold", 15) / 100f);
        if (cameraProvider == null
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                   == PackageManager.PERMISSION_GRANTED) {
            initCamera();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (detectionManager != null) {
            detectionManager.setListener(null);
            detectionManager.shutdown();
        }
        cameraExecutor.shutdown();
    }

    // ── Permissions ──────────────────────────────────────────────────────────

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms,
                                           @NonNull int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == REQ_CAMERA) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
                initCamera();
            } else {
                Toast.makeText(this, "Camera permission required for AI features",
                        Toast.LENGTH_SHORT).show();
            }
        } else if (req == REQ_STORAGE) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED
                    && pendingExportJson != null) {
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                writeToDownloadsLegacy(pendingExportJson, "robot_model_" + ts + ".arduinoai");
            } else {
                Toast.makeText(this, "Storage permission required to export",
                        Toast.LENGTH_SHORT).show();
            }
            pendingExportJson = null;
        }
    }

    // ── Class list adapter ───────────────────────────────────────────────────

    private class ClassAdapter extends RecyclerView.Adapter<ClassAdapter.VH> {
        private List<TrainingDataManager.TrainingClass> data;

        ClassAdapter(List<TrainingDataManager.TrainingClass> data) {
            this.data = new ArrayList<>(data);
        }

        void updateData(List<TrainingDataManager.TrainingClass> newData) {
            data = new ArrayList<>(newData);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_training_class, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            TrainingDataManager.TrainingClass tc = data.get(pos);
            h.emoji.setText(tc.emoji != null ? tc.emoji : "🎯");
            h.name.setText(tc.name != null ? tc.name : "");
            int count = tc.sampleLabels != null ? tc.sampleLabels.size() : 0;
            h.count.setText(count + " photos");
            h.command.setText("CMD: " + (tc.command != null ? tc.command : ""));

            boolean isSelected = tc.name != null && tc.name.equals(selectedClass);
            h.itemView.setAlpha(isSelected ? 1f : 0.7f);

            h.select.setOnClickListener(v -> {
                selectedClass = tc.name;
                updateTeachDisplay();
                notifyDataSetChanged();
            });

            h.delete.setOnClickListener(v -> {
                String n = tc.name != null ? tc.name : "";
                String e = tc.emoji != null ? tc.emoji : "🎯";
                int sampleCount = tc.sampleLabels != null ? tc.sampleLabels.size() : 0;
                new MaterialAlertDialogBuilder(AIControlActivity.this)
                        .setTitle("Remove " + e + " " + n + "?")
                        .setMessage("All " + sampleCount
                                + " training photos will be deleted.")
                        .setPositiveButton("REMOVE", (d, w) -> {
                            trainingManager.deleteClass(n);
                            if (n.equals(selectedClass)) {
                                selectedClass = null;
                                updateTeachDisplay();
                            }
                            refreshClassList();
                        })
                        .setNegativeButton("CANCEL", null)
                        .show();
            });
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView emoji, name, count, command, select, delete;
            VH(View v) {
                super(v);
                emoji   = v.findViewById(R.id.classEmoji);
                name    = v.findViewById(R.id.className);
                count   = v.findViewById(R.id.sampleCount);
                command = v.findViewById(R.id.classCommand);
                select  = v.findViewById(R.id.btnSelect);
                delete  = v.findViewById(R.id.btnDeleteClass);
            }
        }
    }
}
