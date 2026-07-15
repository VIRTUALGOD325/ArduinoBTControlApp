package com.eduprime.arduinobt;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class IoTDashboardActivity extends BaseActivity {

    private String userGeneratedUID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iot_dashboard);

        userGeneratedUID = getIntent().getStringExtra("userGeneratedUID");
        if (userGeneratedUID == null || userGeneratedUID.isEmpty()) {
            redirectToConnectionType();
            return;
        }

        FirebaseAuth iotAuth = IoTFirebaseManager.getAuth(this);
        FirebaseUser user    = iotAuth.getCurrentUser();

        TextView userEmailText = findViewById(R.id.userEmailText);
        if (user != null) {
            userEmailText.setText(user.getEmail());
        }

        TextView signOutBtn = findViewById(R.id.signOutBtn);
        signOutBtn.setOnClickListener(v -> {
            iotAuth.signOut();
            redirectToConnectionType();
        });

        CardView buttonsCard  = findViewById(R.id.buttonsCard);
        CardView joystickCard = findViewById(R.id.joystickCard);
        CardView sliderCard   = findViewById(R.id.sliderCard);
        CardView terminalCard = findViewById(R.id.terminalCard);
        CardView speechToTextCard = findViewById(R.id.speechToTextCard);
        CardView textToSpeechCard = findViewById(R.id.textToSpeechCard);
        CardView cameraCard   = findViewById(R.id.cameraCard);
        CardView aiCard       = findViewById(R.id.aiCard);

        buttonsCard.setOnClickListener(v -> launch(IoTButtonControlActivity.class));
        joystickCard.setOnClickListener(v -> launch(IoTJoystickActivity.class));
        sliderCard.setOnClickListener(v -> launch(IoTSliderActivity.class));
        terminalCard.setOnClickListener(v -> launch(IoTTerminalActivity.class));
        speechToTextCard.setOnClickListener(v -> launch(IoTSpeechToTextActivity.class));
        textToSpeechCard.setOnClickListener(v -> launch(IoTTextToSpeechActivity.class));
        cameraCard.setOnClickListener(v -> launch(IoTCameraActivity.class));
        aiCard.setOnClickListener(v -> launch(IoTAIControlActivity.class));
    }

    private void launch(Class<?> cls) {
        Intent intent = new Intent(this, cls);
        intent.putExtra("userGeneratedUID", userGeneratedUID);
        startActivity(intent);
    }

    private void redirectToConnectionType() {
        getSharedPreferences("connection", MODE_PRIVATE).edit().remove("type").apply();
        startActivity(new Intent(this, ConnectionTypeActivity.class));
        finish();
    }
}
