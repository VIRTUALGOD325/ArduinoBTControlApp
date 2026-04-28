package com.eduprime.arduinobt.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.eduprime.arduinobt.ai.ObjectDetectionManager.DetectionResult;

import java.util.ArrayList;
import java.util.List;

public class DetectionOverlayView extends View {

    private final Paint boxPaint = new Paint();
    private final Paint textBgPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint crosshairPaint = new Paint();

    private List<DetectionResult> results = new ArrayList<>();
    private int sourceWidth = 1, sourceHeight = 1;

    private static final int[] BOX_COLORS = {
            0xFFA8FF78, 0xFFFFD700, 0xFF9ECAFF, 0xFFFF9EC4, 0xFFFF9800
    };

    public DetectionOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4f);
        boxPaint.setAntiAlias(true);

        textBgPaint.setStyle(Paint.Style.FILL);
        textBgPaint.setAntiAlias(true);

        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(36f);
        textPaint.setAntiAlias(true);
        textPaint.setFakeBoldText(true);

        crosshairPaint.setColor(0x809ECAFF);
        crosshairPaint.setStyle(Paint.Style.STROKE);
        crosshairPaint.setStrokeWidth(2f);
        crosshairPaint.setAntiAlias(true);
    }

    public void updateResults(List<DetectionResult> results, int srcW, int srcH) {
        this.results = results;
        this.sourceWidth = srcW;
        this.sourceHeight = srcH;
        postInvalidate();
    }

    public void clearResults() {
        results = new ArrayList<>();
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawCrosshair(canvas);
        if (results.isEmpty()) return;

        float scaleX = (float) getWidth() / sourceWidth;
        float scaleY = (float) getHeight() / sourceHeight;

        for (int i = 0; i < results.size(); i++) {
            DetectionResult r = results.get(i);
            int color = BOX_COLORS[i % BOX_COLORS.length];
            boxPaint.setColor(color);

            RectF scaled = new RectF(
                    r.boundingBox.left  * scaleX,
                    r.boundingBox.top   * scaleY,
                    r.boundingBox.right * scaleX,
                    r.boundingBox.bottom* scaleY);
            canvas.drawRoundRect(scaled, 12, 12, boxPaint);

            String tag = r.label + " " + Math.round(r.confidence * 100) + "%";
            float textW = textPaint.measureText(tag);
            float tagLeft  = scaled.left;
            float tagTop   = Math.max(0, scaled.top - 48f);
            textBgPaint.setColor(color);
            canvas.drawRoundRect(tagLeft, tagTop, tagLeft + textW + 16, tagTop + 44, 8, 8, textBgPaint);
            canvas.drawText(tag, tagLeft + 8, tagTop + 34, textPaint);
        }
    }

    private void drawCrosshair(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float size = 40f;
        canvas.drawLine(cx - size, cy, cx + size, cy, crosshairPaint);
        canvas.drawLine(cx, cy - size, cx, cy + size, crosshairPaint);
        canvas.drawCircle(cx, cy, size / 2f, crosshairPaint);
    }
}
