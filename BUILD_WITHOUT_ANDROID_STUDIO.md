# Build the Android APK without Android Studio

You can build the Android APK using GitHub Actions. Nothing from the Android development toolchain needs to be installed on your Windows laptop.

## Steps

1. Create a GitHub account if you do not already have one.
2. Create a new repository.
3. Upload the entire contents of this extracted project to that repository.
   Make sure the hidden `.github` folder is included.
4. Open the repository on GitHub.
5. Click **Actions**.
6. Select **Build Android APK**.
7. Click **Run workflow** and then **Run workflow**.
8. Open the completed workflow run.
9. Scroll to the **Artifacts** section.
10. Download **LaptopKeyboardBridge-APK**.
11. Extract the downloaded artifact ZIP.
12. You will get:

       LaptopKeyboardBridge.apk

13. Transfer that APK to your Android phone.
14. Open the APK on Android and install it.
15. If Android blocks installation, allow **Install unknown apps** for the app you used to open the APK.
16. Launch **Laptop Keyboard Bridge**.
17. Tap **Enable Keyboard** and enable it.
18. Tap **Select Keyboard** and select **Laptop Keyboard Bridge**.

Then use the `windows` folder on your laptop for Bluetooth or same-Wi-Fi typing.

## Windows side

Install Python 3 and run:

    cd windows
    pip install -r requirements.txt
    python keyboard_sender.py

Choose:

    1 = Same Wi-Fi
    2 = Bluetooth

No Android Studio is required.
