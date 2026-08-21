# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ─── Firebase (Auth / Realtime Database) ───────────────────────────────────
# Firebase Auth/Database use reflection to (de)serialize a handful of internal
# model classes. The SDK ships its own consumer ProGuard rules via the AAR,
# but we keep this belt-and-suspenders rule for the annotation it relies on.
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
    @com.google.firebase.database.PropertyName <methods>;
}

# ─── Gson ───────────────────────────────────────────────────────────────────
# Gson uses reflection to read field names, so obfuscating/removing fields on
# the model classes it (de)serializes breaks save/load and export/import of
# training data. Keep only the specific model classes Gson touches
# (TrainingDataManager's nested data classes, including inherited GSON
# TypeToken usage) rather than a blanket keep.
-keepclassmembers class com.eduprime.arduinobt.AI.TrainingDataManager$* {
    <fields>;
}
-keep class com.eduprime.arduinobt.AI.TrainingDataManager$TrainingClass { <fields>; <init>(...); }
-keep class com.eduprime.arduinobt.AI.TrainingDataManager$ClassifyResult { <fields>; <init>(...); }
-keep class com.eduprime.arduinobt.AI.TrainingDataManager$ModelExport { <fields>; }
-keep class com.eduprime.arduinobt.AI.TrainingDataManager$ModelExport$ExportClass { <fields>; }
-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ─── Glide ──────────────────────────────────────────────────────────────────
# Glide ships its own consumer-proguard-rules.pro inside the AAR (and the
# annotation processor generates a GeneratedAppGlideModule), so R8 picks these
# up automatically — no manual rules are required. Kept here only as a
# documented no-op / reminder in case that assumption changes:
# -keep public class * extends com.bumptech.glide.module.AppGlideModule
# -keep class * extends com.bumptech.glide.module.LibraryGlideModule

# ─── ML Kit (object detection / image labeling) ────────────────────────────
# ML Kit ships its own consumer rules via its AAR; this is a defensive keep
# for the on-device model option/builder classes this app calls into
# reflectively-adjacent code paths (result listeners) around.
-keep class com.google.mlkit.vision.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }

# ─── Google Sign-In / Play Services Auth ───────────────────────────────────
-keep class com.google.android.gms.auth.api.signin.** { *; }
-keep class com.google.android.gms.common.api.** { *; }