# ArduinoBTControl

An Android Bluetooth controller app for Arduino projects using the HC-05 Classic Bluetooth module. Control your Arduino wirelessly with D-pad, joystick, voice, or tilt — with a real-time serial terminal built in.

## Features

- **Multiple control modes** — D-pad, joystick, voice commands, and accelerometer tilt
- **4 configurable action buttons** (A/B/C/D) with custom labels and commands
- **All D-pad/joystick/tilt/voice commands configurable** — change what gets sent per direction in Settings
- **Real-time terminal** — see every command sent (`>`) and every response received (`<`) with timestamps
- **Connection timer** — tracks how long you've been connected
- **Firebase authentication** — Google Sign-In, Email/Password, or local 4-digit PIN
- **Guest mode** — skip login entirely
- **First-launch setup** — name your action buttons before you start
- **Full-screen immersive UI** — no status bar or nav bar, designed for kids
- **Speed slider** — sends `SPD:<value>` to control motor speed
- **BT enable prompt** — asks to turn on Bluetooth if it's off

## Hardware

| Module | Status |
|--------|--------|
| HC-05 (Classic Bluetooth) | Supported |
| HM-10 (BLE) | Planned |

## Default Commands

| Action | Default Command Sent |
|--------|---------------------|
| Forward | `F` |
| Back | `B` |
| Left | `L` |
| Right | `R` |
| Stop | `S` |
| Speed | `SPD:<0-100>` |
| Button A | `A` |
| Button B | `BZ` |
| Button C | `AUTO` |
| Button D | `STOP` |

All commands are configurable in the Settings screen.

## Project Structure

```
app/src/main/java/com/eduprime/arduinobt/
├── BaseActivity.java              # Full-screen immersive mode for all activities
├── SplashActivity.java            # Entry point — routes to login or app
├── LoginActivity.java             # Firebase + PIN + guest auth
├── SetupActivity.java             # First-launch button label setup
├── MainActivity.java
├── bluetooth/
│   └── BluetoothService.java      # Singleton BT service, multi-listener, SPP UUID
├── screens/
│   ├── DeviceActivityList.java    # Lists paired BT devices
│   ├── ControllerActivity.java    # Main controller (D-pad, joystick, voice, tilt)
│   ├── TerminalActivity.java      # Serial terminal with command history
│   ├── SettingsActivity.java      # Configure button labels, commands, baud rate
│   └── DeviceAdapter.java         # RecyclerView adapter for device list
└── views/
    └── JoystickView.java          # Custom joystick View
```

## Setup

### 1. Clone and open
```bash
git clone https://github.com/VIRTUALGOD325/ArduinoBTControl.git
```
Open in Android Studio.

### 2. Firebase (required for Google Sign-In)
1. Go to [Firebase Console](https://console.firebase.google.com) → create a project
2. Add an Android app with package `com.eduprime.arduinobt`
3. Download `google-services.json` → place it in `app/`
4. Enable **Google** and **Email/Password** under Authentication → Sign-in method
5. Sync Gradle

> If you skip Firebase, guest mode and PIN login still work without `google-services.json`.

### 3. Pair your HC-05
- Default pairing PIN: `1234` or `0000`
- Pair in Android Bluetooth settings before opening the app
- The app lists paired devices — tap one to connect

## Tech Stack

- **Language:** Java
- **Build:** Gradle (Groovy DSL)
- **Min SDK:** 23 (Android 6.0)
- **Target SDK:** 36
- **Auth:** Firebase Authentication + Google Sign-In
- **UI:** ConstraintLayout, RecyclerView, BottomNavigationView, Material Components
- **BT Protocol:** SPP (Serial Port Profile) — UUID `00001101-0000-1000-8000-00805F9B34FB`

## Arduino Side

Your Arduino sketch should read serial input and act on single-character or short-string commands:

```cpp
void loop() {
  if (Serial.available()) {
    String cmd = Serial.readStringUntil('\n');
    cmd.trim();

    if (cmd == "F")    { /* move forward */ }
    else if (cmd == "B")    { /* move back    */ }
    else if (cmd == "L")    { /* turn left    */ }
    else if (cmd == "R")    { /* turn right   */ }
    else if (cmd == "S")    { /* stop         */ }
    else if (cmd.startsWith("SPD:")) {
      int speed = cmd.substring(4).toInt();
      /* set motor speed */
    }
  }
}
```

Connect HC-05 TX → Arduino RX (pin 0) and RX → TX (pin 1), or use `SoftwareSerial` for other pins.
