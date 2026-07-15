package com.eduprime.arduinobt;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DatabaseReference;

import java.util.ArrayList;
import java.util.Locale;

public class IoTSpeechToTextActivity extends BaseActivity {

    private static final int REQ_RECORD_AUDIO = 201;

    private TextView listeningStatus, recognizedText, sentCommand;
    private DatabaseReference speechRef;
    private SharedPreferences prefs;

    private final ActivityResultLauncher<Intent> voiceLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                listeningStatus.setText("Tap mic to speak");
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ArrayList<String> matches = result.getData()
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        processVoiceCommand(matches.get(0));
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iot_speech_to_text);

        String uid = getIntent().getStringExtra("userGeneratedUID");
        if (uid == null) {
            finish();
            return;
        }

        speechRef = IoTFirebaseManager.getDatabase(this)
                .getReference("commands/" + uid + "/voice");

        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        listeningStatus = findViewById(R.id.listeningStatus);
        recognizedText  = findViewById(R.id.recognizedText);
        sentCommand     = findViewById(R.id.sentCommand);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        CardView micButton = findViewById(R.id.micButtonCard);
        micButton.setOnClickListener(v -> checkPermissionAndStartVoice());
    }

    private void checkPermissionAndStartVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startVoice();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQ_RECORD_AUDIO);
        }
    }

    private void startVoice() {
        try {
            listeningStatus.setText("Listening...");
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                    "Say a command (e.g., forward, back, left, right, stop)");
            voiceLauncher.launch(intent);
        } catch (Exception e) {
            listeningStatus.setText("Tap mic to speak");
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void processVoiceCommand(String speech) {
        recognizedText.setText("“" + speech + "”");
        String s = speech.toLowerCase(Locale.getDefault());
        String cmd;

        if (s.contains("forward") || s.contains("ahead")) {
            cmd = prefs.getString("cmd_fwd", "F");
        } else if (s.contains("back") || s.contains("reverse")) {
            cmd = prefs.getString("cmd_back", "B");
        } else if (s.contains("left")) {
            cmd = prefs.getString("cmd_left", "L");
        } else if (s.contains("right")) {
            cmd = prefs.getString("cmd_right", "R");
        } else if (s.contains("stop") || s.contains("halt")) {
            cmd = prefs.getString("cmd_stop", "S");
        } else if (s.contains("led") || s.contains("light")) {
            cmd = prefs.getString("cmd_a", "A");
        } else if (s.contains("buzz")) {
            cmd = prefs.getString("cmd_b", "BZ");
        } else if (s.contains("auto")) {
            cmd = prefs.getString("cmd_c", "AUTO");
        } else {
            cmd = speech;
        }

        sentCommand.setText(cmd);
        speechRef.setValue(cmd);
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == REQ_RECORD_AUDIO) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
                startVoice();
            } else {
                Toast.makeText(this, "Permission required for speech recognizer", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
