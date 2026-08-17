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

class MainActivity : AppCompatActivity() {

    private val permissionRequestCode = 1001
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBluetoothPermissions()

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
            text = "Enable Keyboard"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }

        val selectButton = Button(this).apply {
            text = "Select Keyboard"
            setOnClickListener {
                val imm = getSystemService(INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                imm.showInputMethodPicker()
            }
        }

        val info = TextView(this).apply {
            text = """
                SAME WI-FI:
                Connect the laptop and phone to the same Wi-Fi.
                Use the IP address shown above in the Windows program.
                TCP port: ${KeyboardBridgeService.WIFI_PORT}

                BLUETOOTH:
                Keep Bluetooth enabled on both devices.
                The Windows program scans for the phone automatically.

                For either mode, WiFiSync must be enabled and selected as the Android input method.
            """.trimIndent()
            textSize = 15f
            setPadding(0, 30, 0, 0)
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(refreshButton)
        layout.addView(enableButton)
        layout.addView(selectButton)
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

        statusText.text = """
            Wi-Fi IP: $ip
            Port: ${KeyboardBridgeService.WIFI_PORT}

            Keyboard receiver starts when
            "WiFiSync" is selected.
        """.trimIndent()
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )

            val missing = permissions.any {
                ActivityCompat.checkSelfPermission(this, it) !=
                        PackageManager.PERMISSION_GRANTED
            }

            if (missing) {
                ActivityCompat.requestPermissions(
                    this,
                    permissions,
                    permissionRequestCode
                )
            }
        }
    }
}
