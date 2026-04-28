package com.eduprime.arduinobt.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.eduprime.arduinobt.R;
import com.eduprime.arduinobt.screens.DeviceActivityList;

public class NotificationHelper {

    private static final String CH_CONNECTION = "bt_connection";
    private static final String CH_DETECTION  = "ai_detection";
    private static final String CH_TRAINING   = "ai_training";

    private static final int ID_CONNECTED    = 1;
    private static final int ID_DISCONNECTED = 2;
    private static final int ID_DETECTED     = 3;
    private static final int ID_TRAINING     = 4;

    public static void createChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);

        nm.createNotificationChannel(new NotificationChannel(
                CH_CONNECTION, "Bluetooth Connection", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(
                CH_DETECTION, "AI Detections", NotificationManager.IMPORTANCE_DEFAULT));
        NotificationChannel training = new NotificationChannel(
                CH_TRAINING, "AI Training", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(training);
    }

    public static void notifyConnected(Context ctx, String deviceName) {
        send(ctx, ID_CONNECTED, CH_CONNECTION,
                "🤖 Robot Connected!",
                "Connected to " + deviceName + ". Ready to go!",
                DeviceActivityList.class);
    }

    public static void notifyDisconnected(Context ctx) {
        send(ctx, ID_DISCONNECTED, CH_CONNECTION,
                "📡 Robot Disconnected",
                "The Bluetooth connection was lost.",
                DeviceActivityList.class);
    }

    public static void notifyDetected(Context ctx, String label, String command) {
        send(ctx, ID_DETECTED, CH_DETECTION,
                "👁️ I see: " + label,
                "Sending command: " + command,
                com.eduprime.arduinobt.screens.AIControlActivity.class);
    }

    public static void notifyTrainingComplete(Context ctx, int classCount) {
        send(ctx, ID_TRAINING, CH_TRAINING,
                "🎓 Training Done!",
                "Robot learned " + classCount + " things. Let's go!",
                com.eduprime.arduinobt.screens.AIControlActivity.class);
    }

    private static void send(Context ctx, int id, String channel,
                              String title, String text, Class<?> target) {
        PendingIntent pi = PendingIntent.getActivity(ctx, id,
                new Intent(ctx, target),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        try {
            NotificationManagerCompat.from(ctx).notify(id, builder.build());
        } catch (SecurityException ignored) {}
    }
}
