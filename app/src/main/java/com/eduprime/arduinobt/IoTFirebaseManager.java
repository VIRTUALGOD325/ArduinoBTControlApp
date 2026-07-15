package com.eduprime.arduinobt;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Manages the secondary FirebaseApp for the IoT project (iot-ep-e2562).
 * The main app uses "arduinobtapp"; IoT has its own separate project.
 */
public class IoTFirebaseManager {

    private static final String IOT_APP_NAME   = "IoT";
    private static final String APP_ID         = "1:807452855612:android:f0f171dfbd2a6390fb2239";
    private static final String API_KEY        = "AIzaSyDckB1zPYKN1VjhizYYpznp0o9WyfW9gaU";
    private static final String DATABASE_URL   = "https://iot-ep-e2562-default-rtdb.firebaseio.com";
    private static final String PROJECT_ID     = "iot-ep-e2562";
    private static final String STORAGE_BUCKET = "iot-ep-e2562.firebasestorage.app";
    private static final String GCM_SENDER_ID  = "807452855612";

    private static FirebaseApp iotApp;

    public static FirebaseApp getApp(Context context) {
        if (iotApp == null) {
            for (FirebaseApp app : FirebaseApp.getApps(context)) {
                if (IOT_APP_NAME.equals(app.getName())) {
                    iotApp = app;
                    return iotApp;
                }
            }
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApplicationId(APP_ID)
                    .setApiKey(API_KEY)
                    .setDatabaseUrl(DATABASE_URL)
                    .setProjectId(PROJECT_ID)
                    .setStorageBucket(STORAGE_BUCKET)
                    .setGcmSenderId(GCM_SENDER_ID)
                    .build();
            iotApp = FirebaseApp.initializeApp(context, options, IOT_APP_NAME);
        }
        return iotApp;
    }

    public static FirebaseAuth getAuth(Context context) {
        return FirebaseAuth.getInstance(getApp(context));
    }

    public static FirebaseDatabase getDatabase(Context context) {
        return FirebaseDatabase.getInstance(getApp(context));
    }
}
