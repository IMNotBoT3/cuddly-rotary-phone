# Laptop Keyboard Bridge: Bluetooth + Same Wi-Fi

Use your Windows laptop's physical keyboard to type into Android apps.

Supported connection modes:

1. Same Wi-Fi
2. Bluetooth Low Energy

No USB or ADB is required for normal use.

---

## Architecture

Windows laptop keyboard
        |
        v
Windows sender
        |
        +---- Same Wi-Fi / TCP
        |
        +---- Bluetooth LE
        |
        v
Android Input Method Service
        |
        v
Active Android text field

---

# Android setup

Open the `android` folder in Android Studio.

Build and install the application.

Launch:

    Laptop Keyboard Bridge

Then:

1. Allow Bluetooth permissions if you want Bluetooth mode.
2. Press `Enable Keyboard`.
3. Enable `Laptop Keyboard Bridge`.
4. Press `Select Keyboard`.
5. Select `Laptop Keyboard Bridge`.

The receiver runs while this input method service is active.

---

# Same Wi-Fi mode

Connect the Android phone and Windows laptop to the same router/Wi-Fi.

The Android app displays something similar to:

    Wi-Fi IP: 192.168.1.42
    Port: 50505

On Windows:

    pip install -r requirements.txt

Then:

    python keyboard_sender.py

Choose:

    1. Same Wi-Fi

Enter the Android phone IP address.

Example:

    192.168.1.42

Once connected, tap any text field on Android and type on the laptop.

Press ESC to stop.

---

# Bluetooth mode

Keep Bluetooth enabled on both devices.

Run:

    python keyboard_sender.py

Choose:

    2. Bluetooth

The Windows application searches automatically for the BLE service advertised by the Android keyboard.

Once connected, tap any Android text field and type.

Press ESC to stop.

---

# Supported input

- Letters
- Numbers
- Symbols
- Space
- Enter
- Backspace
- Tab
- Arrow keys
- Home
- End

---

# Why same Wi-Fi is useful

Wi-Fi is generally the easiest mode when both devices are already connected to the same router.

It avoids BLE compatibility issues and usually offers lower latency for frequent typing.

Bluetooth remains useful when a Wi-Fi network is unavailable.

---

# Security note

The current Wi-Fi implementation is intended for use on a trusted private LAN.

It listens on TCP port:

    50505

It does not yet implement encryption or device authentication.

Do not expose port 50505 to the public internet.

A production version should add:

- Pairing code
- Session authentication
- Encryption
- Trusted-device storage
- Connection approval on Android

---

# Troubleshooting Wi-Fi

If the connection fails:

1. Confirm both devices are on the same Wi-Fi network.
2. Confirm the Android keyboard is selected.
3. Refresh the IP address in the Android app.
4. Make sure the Windows network profile allows local device communication.
5. Avoid guest Wi-Fi networks that isolate devices from each other.
6. Make sure port 50505 is not blocked.

---

# Project status

This bundle contains source code.

The Android application must be built with Android Studio.

The Windows side runs with Python 3.
