package com.eduprime.arduinobt;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUI();
    }

    /**
     * Called after every setContentView(). Overrides Material3's NavigationBarView
     * inset listener which otherwise adds system-nav-bar height as bottom padding,
     * causing the floating pill to appear taller than it should.
     */
    @Override
    public void onContentChanged() {
        super.onContentChanged();
        View nav = findViewById(R.id.bottomNav);
        if (nav != null) {
            ViewCompat.setOnApplyWindowInsetsListener(nav, (v, insets) -> {
                v.setPadding(0, 0, 0, 0);
                return insets;
            });
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    protected void navigateTo(Class<?> targetActivity) {
        android.content.Intent intent = new android.content.Intent(this, targetActivity);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }
}
