package com.eduprime.arduinobt.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TrainingDataManager {

    private static final String DATA_FILE = "training_data.json";
    private static final Gson GSON = new Gson();

    // ─── Data classes ───────────────────────────────────────────────────────

    public static class TrainingClass {
        public String name;
        public String emoji;
        public String command;
        public List<List<String>> sampleLabels = new ArrayList<>();
        public List<String> samplePaths = new ArrayList<>();

        public TrainingClass(String name, String emoji, String command) {
            this.name = name;
            this.emoji = emoji;
            this.command = command;
        }
    }

    /** Returned by classify() — carries matched class name, score, and gap from runner-up. */
    public static class ClassifyResult {
        public final String className;
        public final float confidence; // normalised 0–1
        public final float margin;     // gap above second-best class

        public ClassifyResult(String className, float confidence, float margin) {
            this.className = className;
            this.confidence = confidence;
            this.margin = margin;
        }
    }

    /** Portable export format — no device-specific image paths. */
    public static class ModelExport {
        public int version = 2;
        public String exportDate;
        public String appId = "ArduinoBTControl";
        public Map<String, ExportClass> classes = new LinkedHashMap<>();

        public static class ExportClass {
            public String name;
            public String emoji;
            public String command;
            public List<List<String>> sampleLabels;
        }
    }

    // ─── Fields ─────────────────────────────────────────────────────────────

    private final Context context;
    private Map<String, TrainingClass> classes;

    public TrainingDataManager(Context context) {
        this.context = context.getApplicationContext();
        classes = load();
    }

    // ─── Queries ────────────────────────────────────────────────────────────

    public List<TrainingClass> getClasses() {
        return new ArrayList<>(classes.values());
    }

    public String getCommand(String className) {
        TrainingClass tc = classes.get(className);
        return tc != null ? tc.command : null;
    }

    public int getSampleCount(String className) {
        TrainingClass tc = classes.get(className);
        return tc != null ? tc.sampleLabels.size() : 0;
    }

    public int getTotalSampleCount() {
        int total = 0;
        for (TrainingClass tc : classes.values()) total += tc.sampleLabels.size();
        return total;
    }

    public List<String> getSamplePaths(String className) {
        TrainingClass tc = classes.get(className);
        return tc != null ? new ArrayList<>(tc.samplePaths) : new ArrayList<>();
    }

    // ─── Mutations ──────────────────────────────────────────────────────────

    public void addClass(String name, String emoji, String command) {
        if (!classes.containsKey(name)) {
            classes.put(name, new TrainingClass(name, emoji, command));
            save();
        }
    }

    public void deleteClass(String name) {
        TrainingClass tc = classes.remove(name);
        if (tc != null) {
            for (String path : tc.samplePaths) if (!path.isEmpty()) new File(path).delete();
            save();
        }
    }

    public void setCommand(String className, String command) {
        if (classes.containsKey(className)) {
            classes.get(className).command = command;
            save();
        }
    }

    public void addSample(String className, Bitmap bitmap, List<String> mlLabels) {
        TrainingClass tc = classes.get(className);
        if (tc == null) return;
        try {
            File dir = new File(context.getFilesDir(), "training/" + className);
            dir.mkdirs();
            File img = new File(dir, "sample_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(img)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            }
            tc.samplePaths.add(img.getAbsolutePath());
            tc.sampleLabels.add(normalise(mlLabels));
            save();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void clearAll() {
        for (TrainingClass tc : classes.values()) {
            for (String path : tc.samplePaths) if (!path.isEmpty()) new File(path).delete();
        }
        classes.clear();
        save();
    }

    // ─── Classification ──────────────────────────────────────────────────────

    /**
     * Classify a set of live ML-Kit labels against all training classes.
     *
     * Uses a frequency-weighted signature approach:
     *  1. For each class, build a label→frequency map from all training samples.
     *  2. Score the live labels against that signature (partial/substring credit).
     *  3. Require both a minimum score threshold AND that the winner is
     *     at least 40 % ahead of the runner-up to avoid ambiguous matches.
     *
     * Returns null when nothing is confident enough.
     */
    public ClassifyResult classify(List<String> currentLabels) {
        if (currentLabels.isEmpty() || classes.isEmpty()) return null;

        String bestClass = null;
        float bestScore = 0f, secondScore = 0f;

        for (TrainingClass tc : classes.values()) {
            if (tc.sampleLabels.isEmpty()) continue;
            float score = scoreAgainst(currentLabels, tc);
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                bestClass = tc.name;
            } else if (score > secondScore) {
                secondScore = score;
            }
        }

        // Must clear minimum confidence
        if (bestClass == null || bestScore < 0.15f) return null;

        // Must be meaningfully ahead of any runner-up (reduces false positives)
        if (classes.size() > 1 && secondScore > 0f && bestScore < secondScore * 1.4f) return null;

        return new ClassifyResult(bestClass, bestScore, bestScore - secondScore);
    }

    /**
     * Score live labels against a single training class using a frequency-weighted
     * signature. Labels that appear consistently across many training samples carry
     * more weight; labels seen in only one sample are downweighted automatically.
     */
    private float scoreAgainst(List<String> currentLabels, TrainingClass tc) {
        if (tc.sampleLabels.isEmpty()) return 0f;

        // Build label → relative frequency (0–1) from all training samples
        Map<String, Float> freq = new HashMap<>();
        float n = tc.sampleLabels.size();
        for (List<String> sample : tc.sampleLabels) {
            Set<String> unique = new HashSet<>(sample); // count once per sample
            for (String label : unique) {
                freq.merge(label, 1f / n, Float::sum);
            }
        }

        // Sum of all frequencies (= max possible match score)
        float maxPossible = 0f;
        for (float f : freq.values()) maxPossible += f;
        if (maxPossible == 0f) return 0f;

        // Match live labels against the frequency signature
        float matchScore = 0f;
        outer:
        for (String live : currentLabels) {
            if (freq.containsKey(live)) {
                matchScore += freq.get(live);
                continue;
            }
            // Substring credit for partial matches (e.g. "ball" / "football")
            for (Map.Entry<String, Float> entry : freq.entrySet()) {
                if (live.contains(entry.getKey()) || entry.getKey().contains(live)) {
                    matchScore += entry.getValue() * 0.5f;
                    continue outer;
                }
            }
        }

        return matchScore / maxPossible;
    }

    // ─── Export / Import ─────────────────────────────────────────────────────

    /**
     * Serialise all training classes to a portable JSON string (no image paths).
     * Suitable for saving as a .arduinoai file.
     */
    public String exportToJson() {
        ModelExport export = new ModelExport();
        export.exportDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                .format(new Date());

        for (TrainingClass tc : classes.values()) {
            ModelExport.ExportClass ec = new ModelExport.ExportClass();
            ec.name         = tc.name;
            ec.emoji        = tc.emoji;
            ec.command      = tc.command;
            ec.sampleLabels = new ArrayList<>(tc.sampleLabels);
            export.classes.put(tc.name, ec);
        }
        return GSON.toJson(export);
    }

    /**
     * Load training data from a previously exported JSON string.
     *
     * @param json  The exported JSON content.
     * @param merge If true, adds to existing classes (existing class samples are kept).
     *              If false, clears everything first.
     * @return true on success.
     */
    public boolean importFromJson(String json, boolean merge) {
        try {
            ModelExport imported = GSON.fromJson(json, ModelExport.class);
            if (imported == null || imported.classes == null) return false;

            if (!merge) clearAll();

            for (Map.Entry<String, ModelExport.ExportClass> entry : imported.classes.entrySet()) {
                ModelExport.ExportClass ec = entry.getValue();
                if (ec.name == null) continue;

                if (!classes.containsKey(ec.name)) {
                    classes.put(ec.name, new TrainingClass(ec.name,
                            ec.emoji != null ? ec.emoji : "🎯",
                            ec.command != null ? ec.command : "F"));
                }
                TrainingClass tc = classes.get(ec.name);
                tc.command = ec.command != null ? ec.command : tc.command;

                if (ec.sampleLabels != null) {
                    for (List<String> sample : ec.sampleLabels) {
                        if (sample == null) continue;
                        tc.sampleLabels.add(new ArrayList<>(sample));
                        tc.samplePaths.add(""); // no path in exported models
                    }
                }
            }
            save();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ─── Persistence ────────────────────────────────────────────────────────

    private void save() {
        try (FileWriter w = new FileWriter(dataFile())) {
            GSON.toJson(classes, w);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private Map<String, TrainingClass> load() {
        File f = dataFile();
        if (!f.exists()) return new HashMap<>();
        try (FileReader r = new FileReader(f)) {
            Type type = new TypeToken<Map<String, TrainingClass>>(){}.getType();
            Map<String, TrainingClass> loaded = GSON.fromJson(r, type);
            return loaded != null ? loaded : new HashMap<>();
        } catch (Exception e) { return new HashMap<>(); }
    }

    private File dataFile() {
        return new File(context.getFilesDir(), DATA_FILE);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static List<String> normalise(List<String> labels) {
        List<String> out = new ArrayList<>(labels.size());
        for (String l : labels) out.add(l.toLowerCase(Locale.US).trim());
        return out;
    }
}
