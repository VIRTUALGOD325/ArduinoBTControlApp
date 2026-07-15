package com.eduprime.arduinobt;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

import com.google.firebase.database.DatabaseReference;

import java.util.Locale;

public class IoTTextToSpeechActivity extends BaseActivity implements TextToSpeech.OnInitListener {

    private EditText ttsInput;
    private TextView lastSpeechLabel;
    private TextToSpeech ttsEngine;
    private DatabaseReference ttsRef;
    private boolean isTtsReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iot_text_to_speech);

        String uid = getIntent().getStringExtra("userGeneratedUID");
        if (uid == null) {
            finish();
            return;
        }

        ttsRef = IoTFirebaseManager.getDatabase(this)
                .getReference("commands/" + uid + "/speaker");

        ttsInput        = findViewById(R.id.ttsInput);
        lastSpeechLabel = findViewById(R.id.lastSpeechLabel);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        // Initialize Android TextToSpeech engine
        ttsEngine = new TextToSpeech(this, this);

        CardView speakLocalBtn = findViewById(R.id.speakLocalCard);
        speakLocalBtn.setOnClickListener(v -> speakLocally());

        CardView sendIoTBtn = findViewById(R.id.sendIoTCard);
        sendIoTBtn.setOnClickListener(v -> sendToIoT());
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = ttsEngine.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Language not supported by TextToSpeech", Toast.LENGTH_SHORT).show();
            } else {
                isTtsReady = true;
            }
        } else {
            Toast.makeText(this, "TextToSpeech initialization failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void speakLocally() {
        String text = ttsInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isTtsReady && ttsEngine != null) {
            ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "localTtsId");
            lastSpeechLabel.setText(text);
        } else {
            Toast.makeText(this, "Text-to-Speech is not ready yet", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendToIoT() {
        String text = ttsInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show();
            return;
        }

        ttsRef.setValue(text)
                .addOnSuccessListener(aVoid -> {
                    lastSpeechLabel.setText(text);
                    Toast.makeText(IoTTextToSpeechActivity.this, "Sent to IoT database", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(IoTTextToSpeechActivity.this, "Failed to send: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onDestroy() {
        if (ttsEngine != null) {
            ttsEngine.stop();
            ttsEngine.shutdown();
        }
        super.onDestroy();
    }
}
