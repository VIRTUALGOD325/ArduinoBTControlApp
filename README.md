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

### Pin Layout

| Pin | Component |
|-----|-----------|
| 4   | LED Blue  |
| 5   | Y         |
| 6   | Buzzer    |
| 12  | Motor IN1 |
| 13  | Motor IN2 |
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
