# WiFiSync

> Local cross-device connectivity utility for sending laptop keyboard input to an Android device over the same Wi-Fi network, with optional Bluetooth LE support.

![Windows](https://img.shields.io/badge/Windows-WiFiSync.exe-0078D4?logo=windows&logoColor=white)
![Android](https://img.shields.io/badge/Android-WiFiSync.apk-3DDC84?logo=android&logoColor=white)
![Wi-Fi](https://img.shields.io/badge/Primary-Same%20Wi--Fi-4A90E2)
![Bluetooth](https://img.shields.io/badge/Optional-Bluetooth%20LE-0082FC?logo=bluetooth&logoColor=white)

## Overview

WiFiSync links a Windows laptop and Android phone locally.

```text
Physical laptop keyboard
        |
        v
     Windows
        |
        +---- local Windows application
        |
        +---- WiFiSync
                |
                +---- Same Wi-Fi / TCP
                |
                +---- Bluetooth LE
                        |
                        v
                  Android WiFiSync
                        |
                        v
                Android InputMethodService
                        |
                        v
                  Focused text field
```

The Windows app remains a normal, visible user process. It does not install itself as a Windows service, impersonate Microsoft components, hide from Task Manager, or add anti-monitoring behavior.

## Branding

Windows build metadata:

```text
Product Name: WiFiSync
Executable: WiFiSync.exe
File Description: Local Wi-Fi connectivity utility
```

Android app label:

```text
WiFiSync
```

The Android package ID is intentionally unchanged from earlier builds so an existing installation can be upgraded.

## Features

- Same-Wi-Fi connection
- Optional Bluetooth LE connection
- `F8`: toggle phone forwarding
- `Enter`: normal Android Enter key
- `Shift + Enter`: Shift-modified Enter for editors that use it as a line break
- `Esc`: disconnect and exit
- Laptop keyboard remains locally usable
- No Android Studio required
- GitHub Actions builds both Windows and Android releases

## Important input behavior

### Forwarding ON

```text
Laptop key -> Windows
           -> Android
```

This is a mirror mode.

### Forwarding OFF

```text
Laptop key -> Windows only
```

Press `F8` to toggle between these states.

WiFiSync does not globally suppress physical Windows keyboard input.

## Build everything with GitHub Actions

Upload the entire project to GitHub.

Then:

1. Open **Actions**.
2. Select **Build WiFiSync Release**.
3. Click **Run workflow**.
4. Wait for both jobs to finish.

Two artifacts will be produced:

```text
WiFiSync-Android-APK
WiFiSync-Windows
```

The first contains:

```text
WiFiSync.apk
```

The second contains:

```text
WiFiSync.exe
```

## Android installation

1. Download `WiFiSync-Android-APK`.
2. Extract `WiFiSync.apk`.
3. Copy it to the Android phone.
4. Install it.
5. Open **WiFiSync**.
6. Tap **Enable Keyboard**.
7. Enable **WiFiSync** in Android's input-method settings.
8. Return to WiFiSync.
9. Tap **Select Keyboard**.
10. Select **WiFiSync**.

## Windows installation

No Python installation is needed when using the release EXE.

1. Download `WiFiSync-Windows`.
2. Extract `WiFiSync.exe`.
3. Run `WiFiSync.exe`.

Windows may show a reputation warning because a GitHub-built executable is not code-signed with a commercial certificate. Only run a binary you built from source or otherwise trust.

## Same-Wi-Fi mode

Connect the laptop and phone to the same trusted local Wi-Fi.

Android shows something similar to:

```text
Wi-Fi IP: 192.168.1.42
Port: 50505
```

Run `WiFiSync.exe`, select:

```text
1. Same Wi-Fi
```

and enter the phone IP.

Controls after connection:

| Key | Action |
|---|---|
| `F8` | Toggle Android forwarding ON/OFF |
| `Enter` | Normal Enter |
| `Shift + Enter` | Shift-modified Enter |
| `Esc` | Disconnect and exit |

## Bluetooth mode

1. Enable Bluetooth on both devices.
2. Give the Android app its requested Bluetooth permissions.
3. Select WiFiSync as the Android input method.
4. Start `WiFiSync.exe`.
5. Select Bluetooth mode.

The Windows client scans for the BLE service advertised by the Android input method.

## Network security

The current Wi-Fi protocol is intended for a trusted private LAN.

It listens on TCP port:

```text
50505
```

Do not expose that port to the public internet.

The current version does not yet include authenticated pairing or transport encryption. Avoid sensitive typing on untrusted/public networks.

## Source layout

```text
.
├── .github/
│   └── workflows/
│       └── build-wifisync-release.yml
├── android/
├── windows/
│   ├── wifisync.py
│   ├── requirements.txt
│   ├── version_info.txt
│   ├── wifisync.ico
│   └── run.bat
└── README.md
```

## Development mode

If you want to run from Python instead of the EXE:

```bash
cd windows
pip install -r requirements.txt
python wifisync.py
```

## Troubleshooting

### Same-Wi-Fi connection fails

Check that:

- the phone and laptop are on the same Wi-Fi
- the Android app shows a valid local IP
- WiFiSync is selected as the Android input method
- the Wi-Fi network does not use client isolation
- local firewall/network policy permits TCP `50505`

### Bluetooth does not find the phone

Check that:

- Bluetooth is enabled
- Android Bluetooth permissions were granted
- WiFiSync is the selected Android input method
- the phone supports BLE advertising/peripheral mode

### Enter works but Shift+Enter behaves differently

Android applications decide how key events are interpreted. WiFiSync sends a genuine Enter key event with Shift metadata; an individual editor may choose different behavior.

## Responsible use

WiFiSync is designed as a normal local-device productivity utility. It does not contain process hiding, security-tool bypasses, anti-monitoring logic, or Windows-component impersonation.

Use it only where external-device and software policies permit it.
