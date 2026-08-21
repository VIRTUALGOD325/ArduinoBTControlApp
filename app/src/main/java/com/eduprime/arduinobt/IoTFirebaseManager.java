package com.eduprime.arduinobt;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Manages the secondary named FirebaseApp instance used for the IoT dashboard flow.
 *
 * NOTE: app/google-services.json (the only Firebase config registered for package
 * com.eduprime.arduinobt) is actually provisioned against the "iot-ep-e2562" project —
 * i.e. the app's [DEFAULT] FirebaseApp already IS the IoT project. There is no genuinely
 * separate "main" Firebase project configured for this package today. Because of that,
 * we no longer hardcode a second set of API key/App ID/DB URL literals here: we just
 * re-register the [DEFAULT] app's own FirebaseOptions (sourced from google-services.json
 * at build time) under a second app name, purely so FirebaseDatabase/FirebaseAuth can be
 * addressed independently from the main-flow instances without colliding with them.
 * If a genuinely distinct IoT Firebase project is ever provisioned, add its
 * google-services.json client entry for this package and swap this back to explicit
 * FirebaseOptions built from that entry (or a secondary google-services set) rather than
 * inline string literals.
 */
public class IoTFirebaseManager {

    private static final String IOT_APP_NAME = "IoT";

    private static FirebaseApp iotApp;

    public static FirebaseApp getApp(Context context) {
        if (iotApp == null) {
            for (FirebaseApp app : FirebaseApp.getApps(context)) {
                if (IOT_APP_NAME.equals(app.getName())) {
                    iotApp = app;
                    return iotApp;
                }
            }
            FirebaseOptions options = FirebaseApp.getInstance().getOptions();
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
