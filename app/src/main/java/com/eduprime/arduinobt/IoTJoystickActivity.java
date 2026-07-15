package com.eduprime.arduinobt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.firebase.database.DatabaseReference;

public class IoTJoystickActivity extends BaseActivity {

    private TextView xValueText, yValueText, directionLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iot_joystick);

        String uid = getIntent().getStringExtra("userGeneratedUID");
        if (uid == null) { finish(); return; }

        xValueText     = findViewById(R.id.xValue);
        yValueText     = findViewById(R.id.yValue);
        directionLabel = findViewById(R.id.directionLabel);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        DatabaseReference joystickRef = IoTFirebaseManager.getDatabase(this)
                .getReference("commands/" + uid + "/joystick");

        FrameLayout container = findViewById(R.id.joystickContainer);
        container.addView(new JoystickView(this, joystickRef, this::onJoystickUpdate));
    }

    private void onJoystickUpdate(int x, int y) {
        xValueText.setText(String.valueOf(x));
        yValueText.setText(String.valueOf(y));
        directionLabel.setText(resolveDirection(x, y));
    }

    private String resolveDirection(int x, int y) {
        int dx = x - 512;
        int dy = y - 512; // positive dy = joystick pushed down = BACKWARD
        int threshold = 100;
        if (Math.abs(dx) < threshold && Math.abs(dy) < threshold) return "CENTER";
        if (Math.abs(dy) >= Math.abs(dx)) return dy < 0 ? "FORWARD" : "BACKWARD";
        return dx < 0 ? "RIGHT" : "LEFT";
    }

    interface JoystickListener {
        void onUpdate(int x, int y);
    }

    static class JoystickView extends View {
        private final DatabaseReference joystickRef;
        private final JoystickListener listener;

        private final Paint outerPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint innerPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ballPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint axisPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float cx, cy, ballX, ballY, outerRadius, ballRadius;

        JoystickView(Context context, DatabaseReference joystickRef, JoystickListener listener) {
            super(context);
            this.joystickRef = joystickRef;
            this.listener    = listener;
            ballRadius = 30f * context.getResources().getDisplayMetrics().density;

            outerPaint.setColor(0xFF1A2433);
            outerPaint.setStyle(Paint.Style.FILL);

            innerPaint.setColor(0xFF1E2A38);
            innerPaint.setStyle(Paint.Style.FILL);

            ballPaint.setColor(0xFF9ECAFF);
            ballPaint.setStyle(Paint.Style.FILL);

            axisPaint.setColor(0xFF2A3A50);
            axisPaint.setStyle(Paint.Style.STROKE);
            axisPaint.setStrokeWidth(2f);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            cx = w / 2f;
            cy = h / 2f;
            outerRadius = Math.min(w, h) / 2f - 8f;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // Outer ring
            canvas.drawCircle(cx, cy, outerRadius, outerPaint);
            // Inner circle
            canvas.drawCircle(cx, cy, outerRadius * 0.55f, innerPaint);
            // Axes
            canvas.drawLine(cx, cy - outerRadius + 20, cx, cy + outerRadius - 20, axisPaint);
            canvas.drawLine(cx - outerRadius + 20, cy, cx + outerRadius - 20, cy, axisPaint);
            // Ball with glow shadow
            Paint shadow = new Paint(ballPaint);
            shadow.setAlpha(60);
            canvas.drawCircle(cx + ballX, cy + ballY, ballRadius + 8f, shadow);
            canvas.drawCircle(cx + ballX, cy + ballY, ballRadius, ballPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - cx;
                    float dy = event.getY() - cy;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    float maxR = outerRadius - ballRadius;
                    if (dist > maxR) { dx = dx / dist * maxR; dy = dy / dist * maxR; }
                    ballX = dx;
                    ballY = dy;
                    int xVal = Math.max(0, Math.min(1023, (int) Math.round(512 - (dx / maxR) * 512)));
                    int yVal = Math.max(0, Math.min(1023, (int) Math.round(512 - (dy / maxR) * 512)));
                    joystickRef.child("x").setValue(xVal);
                    joystickRef.child("y").setValue(yVal);
                    if (listener != null) listener.onUpdate(xVal, yVal);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    ballX = 0; ballY = 0;
                    joystickRef.child("x").setValue(512);
                    joystickRef.child("y").setValue(512);
                    if (listener != null) listener.onUpdate(512, 512);
                    break;
            }
            invalidate();
            return true;
        }
    }
}
