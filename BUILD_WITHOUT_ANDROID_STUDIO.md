# Build WiFiSync without Android Studio

WiFiSync uses GitHub Actions to build both the Android APK and Windows EXE.

## Steps

1. Upload this project to a GitHub repository.
2. Open the repository's **Actions** tab.
3. Select **Build WiFiSync Release**.
4. Click **Run workflow**.
5. Wait for both:
   - Android APK
   - Windows EXE
6. Download the two artifacts:
   - `WiFiSync-Android-APK`
   - `WiFiSync-Windows`

No Android Studio is required.

The Windows release artifact contains `WiFiSync.exe`, so Python is not required for ordinary use either.
