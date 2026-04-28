package com.eduprime.arduinobt.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class JoystickView extends View {

    private float centerX, centerY, outerRadius, innerRadius;
    private float thumbX, thumbY;
    private OnMoveListener listener;

    // No BlurMaskFilter — simulated glow with layered alpha circles instead
    private final Paint outerPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow1Paint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow2Paint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    public interface OnMoveListener {
        void onMove(float x, float y);
    }

    public JoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);

        outerPaint.setColor(Color.parseColor("#1E1E1E"));
        outerPaint.setStyle(Paint.Style.FILL);

        ringPaint.setColor(Color.parseColor("#333333"));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2f);

        glow1Paint.setColor(0x229ECAFF);   // very transparent outer glow
        glow1Paint.setStyle(Paint.Style.FILL);

        glow2Paint.setColor(0x559ECAFF);   // semi-transparent inner glow
        glow2Paint.setStyle(Paint.Style.FILL);

        thumbPaint.setColor(Color.parseColor("#9ECAFF"));
        thumbPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        centerX = w / 2f;
        centerY = h / 2f;
        outerRadius = Math.min(w, h) / 2f - 8;
        innerRadius = outerRadius * 0.35f;
        thumbX = centerX;
        thumbY = centerY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawCircle(centerX, centerY, outerRadius, outerPaint);
        canvas.drawCircle(centerX, centerY, outerRadius, ringPaint);
        canvas.drawCircle(thumbX, thumbY, innerRadius * 2.2f, glow1Paint);
        canvas.drawCircle(thumbX, thumbY, innerRadius * 1.5f, glow2Paint);
        canvas.drawCircle(thumbX, thumbY, innerRadius, thumbPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                // Prevent the parent NestedScrollView from stealing touch events
                // while the joystick thumb is being dragged.
                getParent().requestDisallowInterceptTouchEvent(true);
                moveThumb(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                resetThumb();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void moveThumb(float x, float y) {
        float dx = x - centerX;
        float dy = y - centerY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist <= outerRadius) {
            thumbX = x;
            thumbY = y;
        } else {
            float angle = (float) Math.atan2(dy, dx);
            thumbX = centerX + outerRadius * (float) Math.cos(angle);
            thumbY = centerY + outerRadius * (float) Math.sin(angle);
        }

        if (listener != null) {
            listener.onMove(
                (thumbX - centerX) / outerRadius * 100f,
                (thumbY - centerY) / outerRadius * 100f
            );
        }
        invalidate();
    }

    private void resetThumb() {
        thumbX = centerX;
        thumbY = centerY;
        if (listener != null) listener.onMove(0, 0);
        invalidate();
    }

    public void setOnMoveListener(OnMoveListener listener) {
        this.listener = listener;
    }
}
