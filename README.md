# WiFiSync 6

> Local Windows ↔ Android input and image-transfer utility over a trusted same-Wi-Fi network, with optional Bluetooth LE keyboard forwarding.

![Windows](https://img.shields.io/badge/Windows-WiFiSync.exe-0078D4?logo=windows&logoColor=white)
![Android](https://img.shields.io/badge/Android-WiFiSync.apk-3DDC84?logo=android&logoColor=white)
![Wi-Fi](https://img.shields.io/badge/Wi--Fi-TCP%2050505%20%2B%2050506-4A90E2)
![Bluetooth](https://img.shields.io/badge/Keyboard-Bluetooth%20LE-0082FC?logo=bluetooth&logoColor=white)

## What changed in v6

WiFiSync now supports **user-triggered screenshot transfer** in Same-Wi-Fi mode.

| Shortcut | Action |
|---|---|
| `F8` | Toggle **Laptop only ↔ Mirror to phone** |
| `Enter` | Use Android editor action (Send/Done/Go/Search when supported) |
| `Shift + Enter` | Insert a real newline without triggering Send |
| `F9` | Capture the active Windows window and transfer it to Android |
| `Shift + F9` | Capture the full Windows desktop and transfer it to Android |
| `Esc` | Disconnect and exit |

After a screenshot arrives, Android posts a **WiFiSync screenshot received** notification. Tap it to preview the image and choose **Share** or **Save a Copy**.

The Windows client also reports screenshot success/failure through its normal tray/status notification.

## Architecture

```text
                    SAME WI-FI
Windows                                   Android
-------                                   -------

Physical keyboard
      |
      +--> Windows application
      |
      +--> WiFiSync.exe -- TCP :50505 --> Android InputMethodService
                                               |
                                               +--> focused text field


F9 / Shift+F9
      |
      +--> user-triggered screenshot
      |
      +--> WiFiSync.exe -- TCP :50506 --> temporary PNG
                                               |
                                               +--> Android notification
                                                      |
                                                      +--> Preview
                                                      +--> Share
                                                      +--> Save
```

Screenshot transfer is intentionally supported over **Same Wi-Fi**, not BLE, because screenshots are much larger than keyboard events.

## Windows identity

```text
Product Name: WiFiSync
Executable: WiFiSync.exe
File Description: Local Wi-Fi connectivity utility
```

WiFiSync remains a normal visible user application. It does not impersonate Windows/Microsoft components, hide its process, or contain monitoring-evasion behavior.

## Build without Android Studio

Upload the entire project to GitHub and run:

**Actions → Build WiFiSync Release → Run workflow**

When both jobs succeed, download:

```text
WiFiSync-Android-APK
WiFiSync-Windows
```

The artifacts contain:

```text
WiFiSync.apk
WiFiSync.exe
```

## Android setup

1. Install the new `WiFiSync.apk`.
2. Open WiFiSync.
3. Allow Bluetooth permissions if you use Bluetooth mode.
4. Allow notifications if you want screenshot-arrival notifications.
5. Tap **Enable WiFiSync Input**.
6. Enable WiFiSync in Android input-method settings.
7. Return to WiFiSync.
8. Tap **Select WiFiSync Input**.
9. Select WiFiSync.

The local TCP receivers run while WiFiSync is the selected Android input method.

The app shows:

```text
Wi-Fi IP: 192.168.x.x
Keyboard port: 50505
Screenshot port: 50506
```

## Windows setup

Download and extract `WiFiSync.exe`.

No Python installation is required for the release EXE.

Start it and select:

```text
1. Same Wi-Fi
```

Enter the phone IP displayed in the Android app.

Once connected, use the shortcuts listed above.

A tray icon is also available with commands for:

- toggle phone forwarding
- capture active window
- capture full screen
- exit

## Screenshot workflow

### Active window

Press:

```text
F9
```

WiFiSync captures the bounds of the current foreground Windows window, encodes it as PNG, and sends it to Android on TCP port `50506`.

### Full desktop

Press:

```text
Shift + F9
```

WiFiSync captures the Windows desktop across available displays and transfers the PNG to Android.

### On Android

After the transfer:

1. Tap the WiFiSync screenshot notification, or open WiFiSync and tap **Open Latest Screenshot**.
2. Preview the image.
3. Tap **Share** to open Android's standard Share sheet.
4. Choose an app such as a messaging/chat application.
5. Or tap **Save a Copy** and choose a destination.

WiFiSync does not automatically control another application's chat UI.

## Local network protocol

### Keyboard

```text
TCP 50505
UTF-8 line commands
```

### Screenshot

```text
TCP 50506
4-byte big-endian PNG length
PNG payload
```

The Android receiver:

- rejects invalid PNG signatures
- rejects zero/invalid lengths
- limits transfers to 30 MB
- stores only the latest received screenshot in the application's cache

## Security

This version is for a **trusted private LAN**.

It does not currently implement authenticated pairing or encrypted screenshot transfer.

Do not:

- expose TCP `50505` or `50506` to the public internet
- port-forward these ports
- use the screenshot feature for sensitive material on an untrusted/public Wi-Fi network

A future release should add pairing/authentication and encrypted transport.

## Bluetooth

Bluetooth LE remains available for keyboard forwarding.

Screenshot transfer requires Same-Wi-Fi mode because image payloads are much larger.

## Important behavior

Windows keyboard events are not globally suppressed.

When keyboard forwarding is ON:

```text
key --> local Windows app
    --> Android
```

When keyboard forwarding is OFF:

```text
key --> local Windows app only
```

## Responsible use

WiFiSync is a general local-device productivity utility.

The screenshot feature is user-triggered and reports its result through the application's normal status/tray behavior. The project does not include hidden screen capture, process hiding, secure-browser bypasses, anti-monitoring logic, or software impersonation.


## v6 reliability changes

WiFiSync now starts in **Laptop only** mode.

```text
Laptop only
Physical key -> Windows only
```

Press `F8`:

```text
Mirror to phone
Physical key -> Windows
             -> Android
```

Press `F8` again to return to Laptop only.

WiFiSync intentionally does not suppress the physical Windows keystroke.

### Enter

Normal Enter now uses Android's editor-action API. If the focused field advertises
Send, Done, Go, Search, Next, or Previous, WiFiSync calls that action directly.
This is more reliable for chat composers than sending only a raw key code.

If the editor does not advertise an action, WiFiSync tries the standard Send
editor action and then falls back to a raw Enter event.

### Shift+Enter

Shift+Enter commits a real newline (`\n`) through Android's `InputConnection`,
so it stays distinct from Send.

### Screenshot transfer

Windows now waits for an Android acknowledgement before reporting a screenshot
transfer as successful.

### Validation

The GitHub Actions Android job now runs `lintDebug` before `assembleDebug`,
which catches a broader class of manifest/API/resource issues before packaging.
