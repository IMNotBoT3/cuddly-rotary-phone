# Fixes in this package

- Fixed Android `BluetoothGattServerCallback.onCharacteristicWriteRequest` nullability/signature.
- Updated GitHub Actions workflow to use Android SDK setup explicitly.
- Updated GitHub Actions action versions to current Node 24-compatible releases.
- Keeps both Same Wi-Fi and Bluetooth modes.

## v2

- Added `android/gradle.properties`.
- Enabled AndroidX with `android.useAndroidX=true`.
- Enabled Jetifier for compatibility.
- Added standard Gradle JVM/Kotlin settings.
