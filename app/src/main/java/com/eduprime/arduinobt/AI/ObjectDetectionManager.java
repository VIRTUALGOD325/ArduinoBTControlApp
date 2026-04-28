package com.eduprime.arduinobt.ai;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.media.Image;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ObjectDetectionManager implements ImageAnalysis.Analyzer {

    /**
     * Called when both object detection AND image labeling finish for a frame.
     * frameLabels comes from ImageLabeler — the same pipeline used during training,
     * so they can be directly compared against training data via Jaccard similarity.
     */
    public interface DetectionListener {
        void onResult(List<DetectionResult> results, List<String> frameLabels,
                      int imageWidth, int imageHeight);
    }

    public static class DetectionResult {
        public final RectF boundingBox;
        public final String label;
        public final float confidence;
        public final int trackingId;

        public DetectionResult(RectF box, String label, float confidence, int trackingId) {
            this.boundingBox = box;
            this.label = label;
            this.confidence = confidence;
            this.trackingId = trackingId;
        }
    }

    private final ObjectDetector objectDetector;
    private final ImageLabeler imageLabeler;
    // Slightly lower threshold for live frames so we don't miss weaker matches
    private final ImageLabeler liveLabeler;
    private DetectionListener listener;

    public ObjectDetectionManager() {
        ObjectDetectorOptions opts = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build();
        objectDetector = ObjectDetection.getClient(opts);

        // Used for training captures — stricter threshold
        imageLabeler = ImageLabeling.getClient(
                new ImageLabelerOptions.Builder().setConfidenceThreshold(0.5f).build());

        // Used for live classification — looser threshold for better recall
        liveLabeler = ImageLabeling.getClient(
                new ImageLabelerOptions.Builder().setConfidenceThreshold(0.35f).build());
    }

    public void setListener(DetectionListener listener) {
        this.listener = listener;
    }

    @Override
    @SuppressWarnings("UnsafeOptInUsageError")
    public void analyze(@NonNull ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) { imageProxy.close(); return; }

        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        InputImage input = InputImage.fromMediaImage(mediaImage, rotation);
        int w = imageProxy.getWidth();
        int h = imageProxy.getHeight();

        // Run both tasks in parallel; close imageProxy only when both finish
        List<DetectionResult> results    = new ArrayList<>();
        List<String>          frameLabels = new ArrayList<>();
        AtomicInteger         pending    = new AtomicInteger(2);

        Runnable finish = () -> {
            if (pending.decrementAndGet() == 0) {
                if (listener != null) listener.onResult(results, frameLabels, w, h);
                imageProxy.close();
            }
        };

        objectDetector.process(input)
                .addOnSuccessListener(objects -> {
                    for (DetectedObject obj : objects) {
                        String label = "Object";
                        float confidence = 0f;
                        if (!obj.getLabels().isEmpty()) {
                            label      = obj.getLabels().get(0).getText();
                            confidence = obj.getLabels().get(0).getConfidence();
                        }
                        int tid = obj.getTrackingId() != null ? obj.getTrackingId() : -1;
                        results.add(new DetectionResult(
                                new RectF(obj.getBoundingBox()), label, confidence, tid));
                    }
                })
                .addOnCompleteListener(t -> finish.run());

        // ImageLabeler on the same InputImage — produces the same type of labels
        // as those stored during training, enabling accurate Jaccard matching
        liveLabeler.process(input)
                .addOnSuccessListener(labels -> {
                    for (ImageLabel l : labels) {
                        frameLabels.add(l.getText().toLowerCase());
                    }
                })
                .addOnCompleteListener(t -> finish.run());
    }

    /** Label a still bitmap during training capture. */
    public void labelBitmap(Bitmap bitmap, LabelCallback callback) {
        InputImage input = InputImage.fromBitmap(bitmap, 0);
        imageLabeler.process(input)
                .addOnSuccessListener(labels -> {
                    List<String> result = new ArrayList<>();
                    for (ImageLabel label : labels) {
                        result.add(label.getText().toLowerCase());
                    }
                    callback.onLabels(result);
                })
                .addOnFailureListener(e -> callback.onLabels(new ArrayList<>()));
    }

    public interface LabelCallback {
        void onLabels(List<String> labels);
    }

    /**
     * Returns a directional BT command (F/B/L/R/S) based on where the primary
     * detected object sits in the frame — used by Track mode.
     */
    public String getMotionCommand(List<DetectionResult> results, int imgW, int imgH) {
        if (results.isEmpty()) return "";

        DetectionResult primary = results.get(0);
        float cx      = (primary.boundingBox.left + primary.boundingBox.right) / 2f;
        float cy      = (primary.boundingBox.top  + primary.boundingBox.bottom) / 2f;
        float frameCx = imgW / 2f;
        float frameCy = imgH / 2f;
        float dx      = cx - frameCx;
        float dy      = cy - frameCy;
        float threshX = imgW * 0.2f;
        float threshY = imgH * 0.2f;

        if (Math.abs(dx) < threshX && Math.abs(dy) < threshY) return "S";
        if (Math.abs(dx) >= Math.abs(dy)) return dx > 0 ? "R" : "L";
        return dy > 0 ? "B" : "F";
    }

    public void shutdown() {
        objectDetector.close();
        imageLabeler.close();
        liveLabeler.close();
    }
}
