package com.example.keyboardbridge

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.ParcelUuid
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors

class KeyboardBridgeService : InputMethodService() {

    companion object {
        const val WIFI_PORT = 50505
        const val WIFI_SCREENSHOT_PORT = 50506

        private const val SCREENSHOT_CHANNEL_ID = "wifisync_screenshots"
        private const val SCREENSHOT_NOTIFICATION_ID = 5001
        private const val MAX_SCREENSHOT_BYTES = 30 * 1024 * 1024

        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A
        )

        val BLE_SERVICE_UUID: UUID =
            UUID.fromString("7c8a7e20-3b1f-4e55-9f36-4d83f54bf0c1")

        val BLE_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("7c8a7e21-3b1f-4e55-9f36-4d83f54bf0c1")
    }

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var gattServer: BluetoothGattServer? = null

    private var commandServerSocket: ServerSocket? = null
    private var screenshotServerSocket: ServerSocket? = null

    private val wifiExecutor = Executors.newCachedThreadPool()

    override fun onCreate() {
        super.onCreate()

        createScreenshotNotificationChannel()
        startWifiServers()
        startBluetoothServer()
    }

    override fun onDestroy() {
        stopWifiServers()
        stopBluetoothServer()
        super.onDestroy()
    }

    // -------------------------------------------------
    // Same-Wi-Fi receivers
    // -------------------------------------------------

    private fun startWifiServers() {
        wifiExecutor.execute {
            try {
                commandServerSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(WIFI_PORT))
                }

                while (commandServerSocket?.isClosed == false) {
                    val client = commandServerSocket?.accept() ?: break
                    handleKeyboardClient(client)
                }
            } catch (_: Exception) {
                // Service may be stopping or port may already be occupied.
            }
        }

        wifiExecutor.execute {
            try {
                screenshotServerSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(WIFI_SCREENSHOT_PORT))
                }

                while (screenshotServerSocket?.isClosed == false) {
                    val client = screenshotServerSocket?.accept() ?: break
                    handleScreenshotClient(client)
                }
            } catch (_: Exception) {
                // Service may be stopping or port may already be occupied.
            }
        }
    }

    private fun handleKeyboardClient(socket: Socket) {
        wifiExecutor.execute {
            try {
                socket.use { client ->
                    client.keepAlive = true
                    client.tcpNoDelay = true

                    val reader = BufferedReader(
                        InputStreamReader(
                            client.getInputStream(),
                            StandardCharsets.UTF_8
                        )
                    )

                    while (true) {
                        val line = reader.readLine() ?: break

                        runOnMainThread {
                            handleCommand(line)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun handleScreenshotClient(socket: Socket) {
        wifiExecutor.execute {
            try {
                socket.use { client ->
                    client.soTimeout = 15000
                    client.keepAlive = true
                    client.tcpNoDelay = true

                    val input = DataInputStream(
                        BufferedInputStream(client.getInputStream())
                    )

                    val length = input.readInt()

                    if (length <= PNG_SIGNATURE.size || length > MAX_SCREENSHOT_BYTES) {
                        return@use
                    }

                    val firstBytes = ByteArray(PNG_SIGNATURE.size)
                    input.readFully(firstBytes)

                    if (!firstBytes.contentEquals(PNG_SIGNATURE)) {
                        return@use
                    }

                    val outputFile = File(
                        cacheDir,
                        ScreenshotActivity.LATEST_SCREENSHOT_NAME
                    )

                    FileOutputStream(outputFile).use { output ->
                        output.write(firstBytes)

                        var remaining = length - firstBytes.size
                        val buffer = ByteArray(64 * 1024)

                        while (remaining > 0) {
                            val count = input.read(
                                buffer,
                                0,
                                minOf(buffer.size, remaining)
                            )

                            if (count < 0) {
                                throw IllegalStateException(
                                    "Screenshot connection closed before transfer completed."
                                )
                            }

                            output.write(buffer, 0, count)
                            remaining -= count
                        }
                    }

                    client.getOutputStream().apply {
                        write("OK\n".toByteArray(StandardCharsets.UTF_8))
                        flush()
                    }

                    notifyScreenshotReceived()
                }
            } catch (_: Exception) {
                runOnMainThread {
                    Toast.makeText(
                        this,
                        "WiFiSync screenshot transfer failed.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun stopWifiServers() {
        try {
            commandServerSocket?.close()
        } catch (_: Exception) {
        }

        try {
            screenshotServerSocket?.close()
        } catch (_: Exception) {
        }

        commandServerSocket = null
        screenshotServerSocket = null
        wifiExecutor.shutdownNow()
    }

    // -------------------------------------------------
    // Screenshot notification
    // -------------------------------------------------

    private fun createScreenshotNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SCREENSHOT_CHANNEL_ID,
                "WiFiSync screenshots",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a screenshot arrives from WiFiSync."
            }

            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            manager.createNotificationChannel(channel)
        }
    }

    private fun notifyScreenshotReceived() {
        val intent = Intent(
            this,
            ScreenshotActivity::class.java
        )

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            this,
            SCREENSHOT_CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("WiFiSync screenshot received")
            .setContentText("Tap to preview, save, or share the image.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationAllowed =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

        if (notificationAllowed) {
            NotificationManagerCompat.from(this).notify(
                SCREENSHOT_NOTIFICATION_ID,
                notification
            )
        } else {
            runOnMainThread {
                Toast.makeText(
                    this,
                    "WiFiSync screenshot received. Open WiFiSync to preview it.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // -------------------------------------------------
    // Bluetooth LE keyboard receiver
    // -------------------------------------------------

    private fun hasBtConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBtAdvertisePermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startBluetoothServer() {
        bluetoothManager =
            getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (!hasBtConnectPermission() || !hasBtAdvertisePermission()) return

        try {
            gattServer = bluetoothManager?.openGattServer(this, gattCallback)
        } catch (_: Exception) {
            return
        }

        val service = BluetoothGattService(
            BLE_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val characteristic = BluetoothGattCharacteristic(
            BLE_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        service.addCharacteristic(characteristic)
        gattServer?.addService(service)

        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(BLE_SERVICE_UUID))
            .build()

        try {
            advertiser.startAdvertising(
                settings,
                data,
                advertiseCallback
            )
        } catch (_: Exception) {
        }
    }

    private fun stopBluetoothServer() {
        if (hasBtAdvertisePermission()) {
            try {
                bluetoothAdapter
                    ?.bluetoothLeAdvertiser
                    ?.stopAdvertising(advertiseCallback)
            } catch (_: Exception) {
            }
        }

        if (hasBtConnectPermission()) {
            try {
                gattServer?.close()
            } catch (_: Exception) {
            }
        }

        gattServer = null
    }

    private val advertiseCallback = object : AdvertiseCallback() {
    }

    private val gattCallback = object : BluetoothGattServerCallback() {

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == BLE_CHARACTERISTIC_UUID) {
                val command = String(
                    value,
                    StandardCharsets.UTF_8
                )

                runOnMainThread {
                    handleCommand(command)
                }
            }

            if (responseNeeded && hasBtConnectPermission()) {
                try {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    // -------------------------------------------------
    // Android input
    // -------------------------------------------------

    private fun runOnMainThread(block: () -> Unit) {
        mainExecutor.execute(block)
    }

    private fun handleCommand(command: String) {
        when {
            command == "K:PING" -> {
                // Keepalive heartbeat: intentionally no UI/input action.
            }

            command.startsWith("T:") -> {
                val text = command.substring(2)
                currentInputConnection?.commitText(text, 1)
            }

            command == "K:ENTER" ->
                handleEnter()

            command == "K:SHIFT_ENTER" ->
                insertNewLine()

            command == "K:BACKSPACE" ->
                sendKey(KeyEvent.KEYCODE_DEL)

            command == "K:TAB" ->
                sendKey(KeyEvent.KEYCODE_TAB)

            command == "K:LEFT" ->
                sendKey(KeyEvent.KEYCODE_DPAD_LEFT)

            command == "K:RIGHT" ->
                sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)

            command == "K:UP" ->
                sendKey(KeyEvent.KEYCODE_DPAD_UP)

            command == "K:DOWN" ->
                sendKey(KeyEvent.KEYCODE_DPAD_DOWN)

            command == "K:HOME" ->
                sendKey(KeyEvent.KEYCODE_MOVE_HOME)

            command == "K:END" ->
                sendKey(KeyEvent.KEYCODE_MOVE_END)
        }
    }

    private fun sendKey(keyCode: Int) {
        currentInputConnection?.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        )

        currentInputConnection?.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_UP, keyCode)
        )
    }

    private fun handleEnter() {
        val connection = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo

        val action = editorInfo?.imeOptions
            ?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE

        when (action) {
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_PREVIOUS -> {
                if (!connection.performEditorAction(action)) {
                    sendKey(KeyEvent.KEYCODE_ENTER)
                }
            }

            else -> {
                if (!connection.performEditorAction(EditorInfo.IME_ACTION_SEND)) {
                    sendKey(KeyEvent.KEYCODE_ENTER)
                }
            }
        }
    }

    private fun insertNewLine() {
        currentInputConnection?.commitText("\n", 1)
    }
}
