package com.eduprime.arduinobt;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

public class IoTLoginActivity extends BaseActivity {

    private EditText emailInput, passwordInput;
    private TextView errorText;
    private ProgressBar progressBar;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iot_login);

        auth          = IoTFirebaseManager.getAuth(this);
        emailInput    = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        errorText     = findViewById(R.id.errorText);
        progressBar   = findViewById(R.id.progressBar);
        Button signInBtn     = findViewById(R.id.signInBtn);
        TextView backBtn     = findViewById(R.id.backBtn);
        TextView signUpLink  = findViewById(R.id.signUpLink);

        backBtn.setOnClickListener(v -> {
            clearConnectionType();
            startActivity(new Intent(this, ConnectionTypeActivity.class));
            finish();
        });

        signUpLink.setOnClickListener(v ->
                startActivity(new Intent(this, IoTSignUpActivity.class)));

        signInBtn.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        String email    = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please enter your email and password.");
            return;
        }

        setLoading(true);

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    String uid = result.getUser().getUid();
                    lookupUserGeneratedUID(uid);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError("Login failed: " + e.getMessage());
                });
    }

    private void lookupUserGeneratedUID(String authUID) {
        IoTFirebaseManager.getDatabase(this)
                .getReference("users")
                .child(authUID)
                .child("userID")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        setLoading(false);
                        String userGeneratedUID = snapshot.getValue(String.class);
                        if (userGeneratedUID != null && !userGeneratedUID.isEmpty()) {
                            Intent intent = new Intent(IoTLoginActivity.this, IoTDashboardActivity.class);
                            intent.putExtra("userGeneratedUID", userGeneratedUID);
                            startActivity(intent);
                            finish();
                        } else {
                            auth.signOut();
                            showError("Your account is not configured for IoT access. Contact your administrator.");
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        setLoading(false);
                        auth.signOut();
                        showError("Could not verify access: " + error.getMessage());
                    }
                });
    }

    private void clearConnectionType() {
        getSharedPreferences("connection", MODE_PRIVATE).edit().remove("type").apply();
    }

    private void showError(String msg) {
        errorText.setText(msg);
        errorText.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        findViewById(R.id.signInBtn).setEnabled(!loading);
    }
}
