# ArduinoBTControl

An Android Bluetooth controller app for Arduino/ESP32 robot projects. Control your robot wirelessly over Classic Bluetooth (HC-05) with D-pad, joystick, voice, or tilt — with a real-time serial terminal built in — or drive it remotely over the internet through the built-in Firebase-backed IoT dashboard.

## Status

Deploy-readiness pass complete (see commit `d58d532`): permission-flow crash, BLE write queueing, plaintext-PIN storage, the 16KB native-library page-size alignment issue, and R8/ProGuard shrinking have all been fixed and verified via `assembleDebug`/`assembleRelease`. Still outstanding before a store submission: a real-device 16KB-page-size test and a runtime smoke test of the minified release build (Firebase, Gson import/export, Glide, MLKit, Google Sign-In).

## Features

- **Multiple control modes** — D-pad, joystick, voice commands, and accelerometer tilt
- **4 configurable action buttons** (A/B/C/D) with custom labels and commands
- **All D-pad/joystick/tilt/voice commands configurable** — change what gets sent per direction in Settings
- **Real-time terminal** — see every command sent (`>`) and every response received (`<`) with timestamps
- **Connection timer** — tracks how long you've been connected
- **Firebase authentication** — Google Sign-In, Email/Password, or local 4-digit PIN (stored as a salted SHA-256 hash, never plaintext)
- **Guest mode** — skip login entirely
- **First-launch setup** — name your action buttons before you start
- **Full-screen immersive UI** — no status bar or nav bar, designed for kids
- **Speed slider** — sends `SPD:<value>` to control motor speed
- **BT enable prompt** — asks to turn on Bluetooth if it's off
- **IoT dashboard (Firebase Realtime Database)** — an alternate control path for driving the robot over the internet instead of direct Bluetooth: buttons, joystick, slider, camera, speech-to-text/text-to-speech, and a terminal, all relayed through Firebase RTDB. Reachable via the connection-type picker after login.
- **On-device AI** — object detection and a simple trainable image classifier (`AI/` package) for camera-driven autonomous modes.

## Hardware

| Module                    | Status    |
| ------------------------- | --------- |
| HC-05 (Classic Bluetooth) | Supported |
| HM-10 (BLE)               | Planned   |

## Default Commands

| Action   | Default Command Sent |
| -------- | -------------------- |
| Forward  | `F`                |
| Back     | `B`                |
| Left     | `L`                |
| Right    | `R`                |
| Stop     | `S`                |
| Speed    | `SPD:<0-100>`      |
| Button A | `A`                |
| Button B | `BZ`               |
| Button C | `AUTO`             |
| Button D | `STOP`             |

All commands are configurable in the Settings screen.

## Project Structure

```
app/src/main/java/com/eduprime/arduinobt/
├── BaseActivity.java              # Full-screen immersive mode for all activities
├── SplashActivity.java            # Entry point — routes to login or app
├── LoginActivity.java             # Firebase + PIN (salted SHA-256) + guest auth
├── SetupActivity.java             # First-launch button label setup
├── MainActivity.java
├── ConnectionTypeActivity.java    # Choose direct-Bluetooth vs. IoT/Firebase control
│
├── bluetooth/
│   └── BluetoothService.java      # Singleton BT service (Classic SPP + BLE), multi-listener, write queue
├── screens/
│   ├── DeviceActivityList.java        # Lists paired BT devices
│   ├── ControllerActivity.java        # Main controller (D-pad, joystick, voice, tilt)
│   ├── LandscapeControllerActivity.java
│   ├── TerminalActivity.java          # Serial terminal with command history
│   ├── SettingsActivity.java          # Configure button labels, commands, baud rate
│   ├── AIControlActivity.java         # Camera-driven autonomous control
│   ├── AISettingsActivity.java
│   ├── DataCollectionActivity.java    # Capture training samples for the classifier
│   ├── CameraActivity.java
│   └── DeviceAdapter.java             # RecyclerView adapter for device list
├── views/
│   ├── JoystickView.java          # Custom joystick View
│   └── DetectionOverlayView.java  # Draws AI object-detection boxes over the camera preview
├── AI/
│   ├── AIHandler.java
│   ├── ObjectDetectionManager.java    # MLKit object detection
│   └── TrainingDataManager.java       # Trainable image classifier (Gson-serialized model)
├── camera/CameraActivity.java
├── led/LedActivity.java
├── notifications/NotificationHelper.java
│
├── IoTFirebaseManager.java        # Firebase Realtime Database bridge for the IoT flow
├── IoTLoginActivity.java / IoTSignUpActivity.java
├── IoTDashboardActivity.java
├── IoTButtonControlActivity.java / IoTButtonSettingsActivity.java
├── IoTJoystickActivity.java / IoTSliderActivity.java
├── IoTCameraActivity.java
├── IoTSpeechToTextActivity.java / IoTTextToSpeechActivity.java
└── IoTTerminalActivity.java
```

## Setup

### 1. Clone and open

```bash
git clone git@github.com:VIRTUALGOD325/ArduinoBTControlApp.git
```

Open in Android Studio.

### 2. Firebase (required for Google Sign-In and the IoT dashboard)

1. Go to [Firebase Console](https://console.firebase.google.com) → create a project
2. Add an Android app with package `com.eduprime.arduinobt`
3. Download `google-services.json` → place it in `app/`
4. Enable **Google** and **Email/Password** under Authentication → Sign-in method
5. Enable **Realtime Database** and confirm its security rules require authentication (the IoT control flow relays commands through it)
6. Sync Gradle

> If you skip Firebase, guest mode, PIN login, and direct-Bluetooth control still work without `google-services.json` — only Google Sign-In and the IoT/Firebase control flow need it.

### 3. Signing (required for a release build)

`app/build.gradle`'s `release` build type reads a signing config from `keystore.properties` (gitignored, not included in this repo). Copy `keystore.properties.template` → `keystore.properties` and fill in your own keystore path/passwords, or generate a new upload keystore with `keytool -genkey -v -keystore <name>.jks -keyalg RSA -keysize 2048 -validity 10000 -alias <alias>`.

### 4. Pair your HC-05

- Default pairing PIN: `1234` or `0000`
- Pair in Android Bluetooth settings before opening the app
- The app lists paired devices — tap one to connect

## Building

```bash
./gradlew assembleDebug      # unsigned debug build
./gradlew assembleRelease    # signed, minified (R8/ProGuard) release build — needs keystore.properties
```

The `release` build type has `minifyEnabled true` / `shrinkResources true`; keep rules for Firebase, Gson (`AI/TrainingDataManager` model classes), Glide, MLKit, and Google Sign-In live in `app/proguard-rules.pro`. There's also a `stable` build type (`initWith release`, signed with the debug key) meant for internal side-loaded builds only — **never upload a `stable`-signed artifact to Play Console.**

## Tech Stack

- **Language:** Java
- **Build:** Gradle (Groovy DSL)
- **Min SDK:** 23 (Android 6.0)
- **Target SDK:** 36
- **Auth:** Firebase Authentication + Google Sign-In
- **UI:** ConstraintLayout, RecyclerView, BottomNavigationView, Material Components
- **BT Protocol:** SPP (Serial Port Profile) — UUID `00001101-0000-1000-8000-00805F9B34FB`

## Arduino Side

### Pin Layout

| Pin     | Component             |
| ------- | --------------------- |
| 4       | LED Blue              |
| 5       | Y                     |
| 6       | Buzzer                |
| 12      | Motor IN1             |
| 13      | Motor IN2             |
| 14 (A0) | Motor ENA (PWM speed) |

### Sketch

```cpp
#include <SoftwareSerial.h>

// HC-05 on pins 10 (RX) and 11 (TX) — frees up hardware serial for debugging
SoftwareSerial bt(10, 11);

// Output pins
const int PIN_LED    = 4;
const int PIN_Y      = 5;
const int PIN_BUZZER = 6;
const int PIN_IN1    = 12;
const int PIN_IN2    = 13;
const int PIN_ENA    = A0;  // pin 14 — PWM speed control

// Toggle states
bool ledState    = false;
bool yState      = false;
bool buzzerState = false;

int motorSpeed = 200;  // 0-255, updated by SPD: command

void setup() {
  pinMode(PIN_LED,    OUTPUT);
  pinMode(PIN_Y,      OUTPUT);
  pinMode(PIN_BUZZER, OUTPUT);
  pinMode(PIN_IN1,    OUTPUT);
  pinMode(PIN_IN2,    OUTPUT);
  pinMode(PIN_ENA,    OUTPUT);

  motorStop();
  bt.begin(9600);
  Serial.begin(9600);
}

void loop() {
  if (bt.available()) {
    String cmd = bt.readStringUntil('\n');
    cmd.trim();
    Serial.println("CMD: " + cmd);
    handleCommand(cmd);
  }
}

void handleCommand(String cmd) {
  if      (cmd == "F")     motorForward();
  else if (cmd == "B")     motorBack();
  else if (cmd == "L")     motorLeft();
  else if (cmd == "R")     motorRight();
  else if (cmd == "S")     motorStop();
  else if (cmd == "ESTOP") emergencyStop();
  else if (cmd == "LED")   togglePin(PIN_LED,    ledState);
  else if (cmd == "BZ")    togglePin(PIN_BUZZER, buzzerState);
  else if (cmd == "Y")     togglePin(PIN_Y,      yState);
  else if (cmd.startsWith("SPD:")) {
    motorSpeed = map(cmd.substring(4).toInt(), 0, 100, 0, 255);
  }
}

// Motor
void motorForward()  { analogWrite(PIN_ENA, motorSpeed); digitalWrite(PIN_IN1, HIGH); digitalWrite(PIN_IN2, LOW);  }
void motorBack()     { analogWrite(PIN_ENA, motorSpeed); digitalWrite(PIN_IN1, LOW);  digitalWrite(PIN_IN2, HIGH); }
void motorLeft()     { analogWrite(PIN_ENA, motorSpeed / 2); digitalWrite(PIN_IN1, HIGH); digitalWrite(PIN_IN2, LOW); }
void motorRight()    { analogWrite(PIN_ENA, motorSpeed / 2); digitalWrite(PIN_IN1, LOW);  digitalWrite(PIN_IN2, HIGH); }
void motorStop()     { analogWrite(PIN_ENA, 0); digitalWrite(PIN_IN1, LOW); digitalWrite(PIN_IN2, LOW); }
void emergencyStop() { motorStop(); digitalWrite(PIN_LED, LOW); digitalWrite(PIN_BUZZER, LOW); digitalWrite(PIN_Y, LOW); }

// Toggle helper
void togglePin(int pin, bool &state) {
  state = !state;
  digitalWrite(pin, state ? HIGH : LOW);
}
```

> The D-pad buttons send stop (`S`) automatically when you release them, so the motor stops as soon as you let go.
