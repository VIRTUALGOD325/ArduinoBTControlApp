package com.eduprime.arduinobt;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.database.DatabaseReference;

public class IoTCameraActivity extends BaseActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 100;

    private PreviewView previewView;
    private TextView cameraStatusText, flashText;
    private DatabaseReference cameraRef;
    private boolean isFlashOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iot_camera);

        String uid = getIntent().getStringExtra("userGeneratedUID");
        if (uid == null) {
            finish();
            return;
        }

        cameraRef = IoTFirebaseManager.getDatabase(this)
                .getReference("commands/" + uid + "/camera");

        previewView      = findViewById(R.id.cameraPreview);
        cameraStatusText = findViewById(R.id.cameraStatusText);
        flashText        = findViewById(R.id.flashText);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }

        // Action controls
        CardView captureRemote = findViewById(R.id.captureRemoteCard);
        captureRemote.setOnClickListener(v -> {
            cameraRef.child("action").setValue("CAPTURE");
            cameraStatusText.setText("Triggered Remote Capture");
            Toast.makeText(this, "Remote capture command sent", Toast.LENGTH_SHORT).show();
        });

        CardView flashToggle = findViewById(R.id.flashToggleCard);
        flashToggle.setOnClickListener(v -> {
            isFlashOn = !isFlashOn;
            String flashState = isFlashOn ? "ON" : "OFF";
            cameraRef.child("flash").setValue(flashState);
            flashText.setText(isFlashOn ? "💡 Flash: ON" : "💡 Flash: OFF");
            cameraStatusText.setText("Flash: " + flashState);
        });

        CardView captureLocal = findViewById(R.id.captureLocalCard);
        captureLocal.setOnClickListener(v -> {
            Toast.makeText(this, "Phone Snapshot Captured!", Toast.LENGTH_SHORT).show();
            cameraStatusText.setText("Local Snapshot Saved");
        });

        // Servo Direction Buttons
        ImageButton upBtn    = findViewById(R.id.servoUpBtn);
        ImageButton downBtn  = findViewById(R.id.servoDownBtn);
        ImageButton leftBtn  = findViewById(R.id.servoLeftBtn);
        ImageButton rightBtn = findViewById(R.id.servoRightBtn);
        TextView centerBtn   = findViewById(R.id.servoCenterBtn);

        upBtn.setOnClickListener(v -> sendServoCommand("TILT_UP"));
        downBtn.setOnClickListener(v -> sendServoCommand("TILT_DOWN"));
        leftBtn.setOnClickListener(v -> sendServoCommand("PAN_LEFT"));
        rightBtn.setOnClickListener(v -> sendServoCommand("PAN_RIGHT"));
        centerBtn.setOnClickListener(v -> sendServoCommand("CENTER"));
    }

    private void sendServoCommand(String command) {
        cameraRef.child("servo").setValue(command);
        cameraStatusText.setText("Servo: " + command);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview);
                cameraStatusText.setText("Camera Feed Active");

            } catch (Exception e) {
                cameraStatusText.setText("Camera Connection Failed");
                Toast.makeText(this, "Camera failed: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                cameraStatusText.setText("Permission Denied");
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
