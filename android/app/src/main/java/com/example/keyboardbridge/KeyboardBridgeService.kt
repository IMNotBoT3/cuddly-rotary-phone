package com.example.keyboardbridge

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.ParcelUuid
import android.view.KeyEvent
import androidx.core.app.ActivityCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors

class KeyboardBridgeService : InputMethodService() {

    companion object {
        const val WIFI_PORT = 50505

        val BLE_SERVICE_UUID: UUID =
            UUID.fromString("7c8a7e20-3b1f-4e55-9f36-4d83f54bf0c1")

        val BLE_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("7c8a7e21-3b1f-4e55-9f36-4d83f54bf0c1")
    }

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var gattServer: BluetoothGattServer? = null

    private var serverSocket: ServerSocket? = null
    private val wifiExecutor = Executors.newCachedThreadPool()

    override fun onCreate() {
        super.onCreate()
        startWifiServer()
        startBluetoothServer()
    }

    override fun onDestroy() {
        stopWifiServer()
        stopBluetoothServer()
        super.onDestroy()
    }

    // -----------------------------
    // Wi-Fi TCP server
    // -----------------------------

    private fun startWifiServer() {
        wifiExecutor.execute {
            try {
                serverSocket = ServerSocket(WIFI_PORT)

                while (!serverSocket!!.isClosed) {
                    val client = serverSocket!!.accept()
                    handleWifiClient(client)
                }
            } catch (_: Exception) {
                // Service may be stopping or port may already be in use.
            }
        }
    }

    private fun handleWifiClient(socket: Socket) {
        wifiExecutor.execute {
            try {
                socket.use { client ->
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
            } catch (_: Exception) { }
        }
    }

    private fun stopWifiServer() {
        try {
            serverSocket?.close()
        } catch (_: Exception) { }

        serverSocket = null
        wifiExecutor.shutdownNow()
    }

    // -----------------------------
    // Bluetooth LE GATT server
    // -----------------------------

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
            advertiser.startAdvertising(settings, data, advertiseCallback)
        } catch (_: Exception) { }
    }

    private fun stopBluetoothServer() {
        if (hasBtAdvertisePermission()) {
            try {
                bluetoothAdapter
                    ?.bluetoothLeAdvertiser
                    ?.stopAdvertising(advertiseCallback)
            } catch (_: Exception) { }
        }

        if (hasBtConnectPermission()) {
            try {
                gattServer?.close()
            } catch (_: Exception) { }
        }

        gattServer = null
    }

    private val advertiseCallback = object : AdvertiseCallback() {}

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

    // -----------------------------
    // Android keyboard input
    // -----------------------------

    private fun runOnMainThread(block: () -> Unit) {
        mainExecutor.execute(block)
    }

    private fun handleCommand(command: String) {
        when {
            command.startsWith("T:") -> {
                val text = command.substring(2)
                currentInputConnection?.commitText(text, 1)
            }

            command == "K:ENTER" -> sendKey(KeyEvent.KEYCODE_ENTER)
            command == "K:BACKSPACE" -> sendKey(KeyEvent.KEYCODE_DEL)
            command == "K:TAB" -> sendKey(KeyEvent.KEYCODE_TAB)

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
}
