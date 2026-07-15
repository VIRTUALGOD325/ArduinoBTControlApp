package com.eduprime.arduinobt;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class IoTSignUpActivity extends BaseActivity {

    private EditText userIdInput, emailInput, passwordInput;
    private TextView errorText;
    private ProgressBar progressBar;
    private FirebaseAuth auth;
    private FirebaseDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iot_signup);

        auth = IoTFirebaseManager.getAuth(this);
        db   = IoTFirebaseManager.getDatabase(this);

        userIdInput   = findViewById(R.id.userIdInput);
        emailInput    = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        errorText     = findViewById(R.id.errorText);
        progressBar   = findViewById(R.id.progressBar);

        Button signUpBtn  = findViewById(R.id.signUpBtn);
        TextView backBtn  = findViewById(R.id.backBtn);

        backBtn.setOnClickListener(v -> finish());
        signUpBtn.setOnClickListener(v -> attemptSignUp());
    }

    private void attemptSignUp() {
        String userId   = userIdInput.getText().toString().trim();
        String email    = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (userId.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError("All fields are required.");
            return;
        }
        if (!userId.matches("[a-zA-Z0-9]+")) {
            showError("User ID must contain only letters and numbers.");
            return;
        }
        if (password.length() < 6) {
            showError("Password must be at least 6 characters.");
            return;
        }

        setLoading(true);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    String authUID = result.getUser().getUid();

                    // Write user profile
                    Map<String, Object> userEntry = new HashMap<>();
                    userEntry.put("email", email);
                    userEntry.put("userID", userId);
                    db.getReference("users").child(authUID).setValue(userEntry);

                    // Initialize commands node
                    Map<String, Object> commandsEntry = new HashMap<>();
                    commandsEntry.put("buttons", "NONE");
                    commandsEntry.put("slider", 128);
                    Map<String, Object> joystick = new HashMap<>();
                    joystick.put("x", 512);
                    joystick.put("y", 512);
                    commandsEntry.put("joystick", joystick);
                    commandsEntry.put("terminal", "");
                    db.getReference("commands").child(userId).setValue(commandsEntry)
                            .addOnCompleteListener(task -> {
                                setLoading(false);
                                // Go back to login
                                finish();
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError(e.getMessage());
                });
    }

    private void showError(String msg) {
        errorText.setText(msg);
        errorText.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        findViewById(R.id.signUpBtn).setEnabled(!loading);
    }
}
