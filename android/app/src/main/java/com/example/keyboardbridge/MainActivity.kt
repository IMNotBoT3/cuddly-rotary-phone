package com.example.keyboardbridge

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private val permissionRequestCode = 1001
    private lateinit var statusText: TextView
    private lateinit var screenshotButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(40, 50, 40, 40)
        }

        val title = TextView(this).apply {
            text = "WiFiSync"
            textSize = 25f
        }

        statusText = TextView(this).apply {
            textSize = 17f
            setPadding(0, 25, 0, 25)
        }

        val refreshButton = Button(this).apply {
            text = "Refresh Wi-Fi Address"
            setOnClickListener { refreshStatus() }
        }

        val enableButton = Button(this).apply {
            text = "Enable WiFiSync Input"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }

        val selectButton = Button(this).apply {
            text = "Select WiFiSync Input"
            setOnClickListener {
                val imm = getSystemService(INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                imm.showInputMethodPicker()
            }
        }

        screenshotButton = Button(this).apply {
            text = "Open Latest Screenshot"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ScreenshotActivity::class.java))
            }
        }

        val info = TextView(this).apply {
            text = """
                SAME WI-FI:
                Connect the laptop and phone to the same Wi-Fi.
                Keyboard port: ${KeyboardBridgeService.WIFI_PORT}
                Screenshot port: ${KeyboardBridgeService.WIFI_SCREENSHOT_PORT}

                WINDOWS HOTKEYS:
                F8 = toggle phone keyboard forwarding
                F9 = capture active window and send to phone
                Shift+F9 = capture full desktop and send to phone
                Esc = disconnect

                SCREENSHOTS:
                Received screenshots are stored temporarily in WiFiSync.
                Android shows a notification you can tap to preview,
                save, or share using Android's normal Share sheet.

                WiFiSync must be selected as the Android input method
                for the local receivers to be active.
            """.trimIndent()
            textSize = 15f
            setPadding(0, 30, 0, 0)
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(refreshButton)
        layout.addView(enableButton)
        layout.addView(selectButton)
        layout.addView(screenshotButton)
        layout.addView(info)

        setContentView(layout)
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val wifiManager =
            applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        @Suppress("DEPRECATION")
        val ip = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)

        val latest = File(cacheDir, ScreenshotActivity.LATEST_SCREENSHOT_NAME)

        statusText.text = """
            Wi-Fi IP: $ip
            Keyboard port: ${KeyboardBridgeService.WIFI_PORT}
            Screenshot port: ${KeyboardBridgeService.WIFI_SCREENSHOT_PORT}

            Latest screenshot: ${if (latest.exists()) "Available" else "None yet"}

            Receivers start when WiFiSync is selected
            as the active Android input method.
        """.trimIndent()

        screenshotButton.isEnabled = latest.exists()
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) !=
                    PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missing.toTypedArray(),
                permissionRequestCode
            )
        }
    }
}
