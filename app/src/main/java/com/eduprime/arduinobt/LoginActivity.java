package com.eduprime.arduinobt;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.eduprime.arduinobt.screens.DeviceActivityList;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private GoogleSignInClient googleSignInClient;
    private SharedPreferences prefs;
    private EditText emailInput, passwordInput, pinInput;

    // NOTE: R.string.default_web_client_id is auto-generated when you add
    // google-services.json to your app/ folder (from Firebase Console).
    // See setup instructions at the bottom of this file.
    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getData() == null) return;
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    firebaseAuthWithGoogle(account.getIdToken());
                } catch (ApiException e) {
                    Toast.makeText(this, "Google sign-in failed: " + e.getStatusCode(), Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth  = FirebaseAuth.getInstance();
        prefs = getSharedPreferences("auth", MODE_PRIVATE);

        // Setup Google Sign-In
        // REPLACE "YOUR_WEB_CLIENT_ID" with the value from your google-services.json
        // OR add it to res/values/strings.xml as <string name="default_web_client_id">...</string>
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        emailInput    = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        pinInput      = findViewById(R.id.pinInput);

        findViewById(R.id.googleSignInBtn).setOnClickListener(v ->
                googleLauncher.launch(googleSignInClient.getSignInIntent()));

        findViewById(R.id.signInBtn).setOnClickListener(v -> signInWithEmail());
        findViewById(R.id.registerBtn).setOnClickListener(v -> registerWithEmail());
        ((Button) findViewById(R.id.pinBtn)).setOnClickListener(v -> handlePin());
        ((TextView) findViewById(R.id.guestBtn)).setOnClickListener(v -> guestLogin());
    }

    private void signInWithEmail() {
        String email = emailInput.getText().toString().trim();
        String pass  = passwordInput.getText().toString().trim();
        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }
        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> goToMain())
                .addOnFailureListener(e -> Toast.makeText(this, "Sign in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void registerWithEmail() {
        String email = emailInput.getText().toString().trim();
        String pass  = passwordInput.getText().toString().trim();
        if (email.isEmpty() || pass.length() < 6) {
            Toast.makeText(this, "Enter email and password (min 6 chars)", Toast.LENGTH_SHORT).show();
            return;
        }
        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> goToMain())
                .addOnFailureListener(e -> Toast.makeText(this, "Registration failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void handlePin() {
        String pin = pinInput.getText().toString().trim();
        if (pin.length() != 4) {
            Toast.makeText(this, "Enter a 4-digit PIN", Toast.LENGTH_SHORT).show();
            return;
        }
        String savedPin = prefs.getString("pin", null);
        if (savedPin == null) {
            prefs.edit().putString("pin", pin).apply();
            Toast.makeText(this, "PIN set! Remember it for next time.", Toast.LENGTH_LONG).show();
            goToMain();
        } else if (pin.equals(savedPin)) {
            prefs.edit().putBoolean("pin_verified", true).apply();
            goToMain();
        } else {
            Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
            pinInput.setText("");
        }
    }

    private void guestLogin() {
        prefs.edit().putBoolean("guest", true).apply();
        goToMain();
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(r -> goToMain())
                .addOnFailureListener(e -> Toast.makeText(this, "Auth failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void goToMain() {
        startActivity(new Intent(this, DeviceActivityList.class));
        finish();
    }

    /*
     * FIREBASE SETUP (one-time):
     * 1. Go to https://console.firebase.google.com
     * 2. Create a project → Add Android app (package: com.eduprime.arduinobt)
     * 3. Download google-services.json → place it in ArduinoBTControl/app/
     * 4. In Firebase Console → Authentication → Sign-in method:
     *    Enable "Google" and "Email/Password"
     * 5. Sync Gradle — the default_web_client_id string is auto-generated
     */
}
