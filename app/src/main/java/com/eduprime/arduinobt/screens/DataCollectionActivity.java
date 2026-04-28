package com.eduprime.arduinobt.screens;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.eduprime.arduinobt.BaseActivity;
import com.eduprime.arduinobt.R;
import com.eduprime.arduinobt.ai.ObjectDetectionManager;
import com.eduprime.arduinobt.ai.TrainingDataManager;
import com.eduprime.arduinobt.notifications.NotificationHelper;
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

public class DataCollectionActivity extends BaseActivity {

    private static final int REQ_CAMERA   = 102;
    private static final int REQ_STORAGE  = 103;

    private PreviewView previewView;
    private TextView currentClassName, sampleCount, captureBtn;
    private EditText commandInput;
    private RecyclerView classList;

    private ImageCapture imageCapture;
    private ObjectDetectionManager detectionManager;
    private TrainingDataManager trainingManager;
    private ClassAdapter classAdapter;

    private String selectedClass = null;
    private String pendingExportJson = null; // held while waiting for storage permission

    // File picker for model import
    private final ActivityResultLauncher<String> importLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                handleImportUri(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_collection);

        previewView      = findViewById(R.id.cameraPreview);
        currentClassName = findViewById(R.id.currentClassName);
        sampleCount      = findViewById(R.id.sampleCount);
        captureBtn       = findViewById(R.id.captureBtn);
        commandInput     = findViewById(R.id.commandInput);
        classList        = findViewById(R.id.classList);

        trainingManager  = new TrainingDataManager(this);
        detectionManager = new ObjectDetectionManager();

        classList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        classAdapter = new ClassAdapter(trainingManager.getClasses());
        classList.setAdapter(classAdapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnDone).setOnClickListener(v -> onDone());
        captureBtn.setOnClickListener(v -> capturePhoto());
        findViewById(R.id.btnChangeClass).setOnClickListener(v -> showClassPicker());
        findViewById(R.id.btnAddClass).setOnClickListener(v -> showAddClassDialog());
        findViewById(R.id.btnExport).setOnClickListener(v -> exportModel());
        findViewById(R.id.btnImport).setOnClickListener(v -> importModel());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }

        refreshClassList();
    }

    // ─── Camera ──────────────────────────────────────────────────────────────

    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider provider = ProcessCameraProvider.getInstance(this).get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
            } catch (Exception e) {
                Toast.makeText(this, "Camera error", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void capturePhoto() {
        if (selectedClass == null) {
            Toast.makeText(this, "Pick a class first! 👆", Toast.LENGTH_SHORT).show();
            return;
        }
        if (imageCapture == null) return;

        captureBtn.setAlpha(0.5f);
        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                Bitmap bitmap = imageProxyToBitmap(image);
                image.close();
                if (bitmap == null) { captureBtn.setAlpha(1f); return; }

                detectionManager.labelBitmap(bitmap, labels -> {
                    if (labels.isEmpty()) {
                        Toast.makeText(DataCollectionActivity.this,
                                "⚠️ No labels detected — try better lighting or a clearer shot",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        trainingManager.addSample(selectedClass, bitmap, labels);
                        refreshClassList();
                        updateCountDisplay();
                        Toast.makeText(DataCollectionActivity.this,
                                "📸 Saved! Labels: " + String.join(", ",
                                        labels.subList(0, Math.min(3, labels.size()))),
                                Toast.LENGTH_SHORT).show();
                    }
                    captureBtn.setAlpha(1f);
                });
            }

            @Override
            public void onError(@NonNull ImageCaptureException e) {
                captureBtn.setAlpha(1f);
                Toast.makeText(DataCollectionActivity.this, "Capture failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            java.nio.ByteBuffer buf = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.postRotate(image.getImageInfo().getRotationDegrees());
            return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        } catch (Exception e) { return null; }
    }

    // ─── Export ──────────────────────────────────────────────────────────────

    private void exportModel() {
        int classCount  = trainingManager.getClasses().size();
        int sampleCount = trainingManager.getTotalSampleCount();
        if (classCount == 0) {
            Toast.makeText(this, "Nothing trained yet!", Toast.LENGTH_SHORT).show();
            return;
        }

        String json = trainingManager.exportToJson();
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String filename = "robot_model_" + timestamp + ".arduinoai";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+: use MediaStore, no permission needed
            writeToDownloadsQ(json, filename);
        } else {
            // API ≤ 28: need WRITE_EXTERNAL_STORAGE
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Downloads.IS_PENDING, 1);
            }

            Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    : MediaStore.Files.getContentUri("external");

            Uri uri = getContentResolver().insert(collection, values);
            if (uri == null) throw new Exception("MediaStore insert failed");

            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new Exception("Cannot open output stream");
                os.write(json.getBytes("UTF-8"));
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(uri, values, null, null);
            }

            Toast.makeText(this,
                    "✅ Exported to Downloads/" + filename, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void writeToDownloadsLegacy(String json, String filename) {
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            dir.mkdirs();
            File file = new File(dir, filename);
            try (FileWriter fw = new FileWriter(file)) {
                fw.write(json);
            }
            Toast.makeText(this,
                    "✅ Exported to Downloads/" + filename, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ─── Import ──────────────────────────────────────────────────────────────

    private void importModel() {
        importLauncher.launch("*/*");
    }

    private void handleImportUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) throw new Exception("Cannot read file");

            // Read stream into string
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) sb.append(new String(buf, 0, n, "UTF-8"));
            is.close();
            String json = sb.toString();

            // Check it looks like a valid export
            if (!json.contains("\"classes\"")) {
                Toast.makeText(this, "Not a valid .arduinoai model file", Toast.LENGTH_SHORT).show();
                return;
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle("📂 Load Model")
                    .setMessage("How do you want to load this model?")
                    .setPositiveButton("MERGE with existing", (d, w) -> {
                        if (trainingManager.importFromJson(json, true)) {
                            refreshClassList();
                            updateCountDisplay();
                            int total = trainingManager.getClasses().size();
                            Toast.makeText(this, "✅ Merged! " + total + " classes loaded",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Import failed — invalid file", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("REPLACE all", (d, w) ->
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle("Replace training data?")
                                    .setMessage("This will delete all existing training. Are you sure?")
                                    .setPositiveButton("YES, REPLACE", (d2, w2) -> {
                                        if (trainingManager.importFromJson(json, false)) {
                                            refreshClassList();
                                            updateCountDisplay();
                                            int total = trainingManager.getClasses().size();
                                            Toast.makeText(this, "✅ Loaded! " + total + " classes",
                                                    Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(this, "Import failed — invalid file",
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    })
                                    .setNegativeButton("CANCEL", null)
                                    .show())
                    .setNeutralButton("CANCEL", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to read file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ─── Class management ────────────────────────────────────────────────────

    private void showAddClassDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_class, null, false);
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
                    if (cmd.isEmpty())  cmd = "F";
                    trainingManager.addClass(name, emoji, cmd);
                    selectedClass = name;
                    refreshClassList();
                    updateCountDisplay();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void showClassPicker() {
        List<TrainingDataManager.TrainingClass> classes = trainingManager.getClasses();
        if (classes.isEmpty()) { showAddClassDialog(); return; }
        String[] names = new String[classes.size()];
        for (int i = 0; i < classes.size(); i++)
            names[i] = classes.get(i).emoji + " " + classes.get(i).name
                    + " (" + classes.get(i).sampleLabels.size() + " photos)";
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select a class to teach")
                .setItems(names, (d, i) -> {
                    selectedClass = classes.get(i).name;
                    updateCountDisplay();
                })
                .show();
    }

    private void updateCountDisplay() {
        if (selectedClass == null) {
            currentClassName.setText("No class selected");
            sampleCount.setText("0 photos taken");
            commandInput.setText("");
        } else {
            currentClassName.setText(selectedClass);
            int count = trainingManager.getSampleCount(selectedClass);
            sampleCount.setText(count + " photo" + (count == 1 ? "" : "s") + " taken");
            String cmd = trainingManager.getCommand(selectedClass);
            commandInput.setText(cmd != null ? cmd : "");
        }
    }

    private void refreshClassList() {
        classAdapter.updateData(trainingManager.getClasses());
    }

    private void onDone() {
        if (selectedClass != null) {
            String cmd = commandInput.getText().toString().trim();
            if (!cmd.isEmpty()) trainingManager.setCommand(selectedClass, cmd);
        }
        int total = trainingManager.getClasses().size();
        NotificationHelper.notifyTrainingComplete(this, total);
        Toast.makeText(this, "🎉 Robot trained! " + total + " things learned.", Toast.LENGTH_LONG).show();
        finish();
    }

    // ─── Permissions ─────────────────────────────────────────────────────────

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == REQ_CAMERA && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else if (req == REQ_STORAGE) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED
                    && pendingExportJson != null) {
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                writeToDownloadsLegacy(pendingExportJson, "robot_model_" + ts + ".arduinoai");
            } else {
                Toast.makeText(this, "Storage permission required to export", Toast.LENGTH_SHORT).show();
            }
            pendingExportJson = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (detectionManager != null) detectionManager.shutdown();
    }

    // ─── Adapter ─────────────────────────────────────────────────────────────

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
            h.emoji.setText(tc.emoji);
            h.name.setText(tc.name);
            h.count.setText(tc.sampleLabels.size() + " photos");
            h.command.setText("CMD: " + tc.command);
            h.select.setOnClickListener(v -> {
                selectedClass = tc.name;
                updateCountDisplay();
                Toast.makeText(DataCollectionActivity.this,
                        "Selected: " + tc.emoji + " " + tc.name, Toast.LENGTH_SHORT).show();
            });
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView emoji, name, count, command, select;
            VH(View v) {
                super(v);
                emoji   = v.findViewById(R.id.classEmoji);
                name    = v.findViewById(R.id.className);
                count   = v.findViewById(R.id.sampleCount);
                command = v.findViewById(R.id.classCommand);
                select  = v.findViewById(R.id.btnSelect);
            }
        }
    }
}
