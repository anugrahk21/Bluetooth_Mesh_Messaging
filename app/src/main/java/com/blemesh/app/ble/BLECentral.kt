package com.blemesh.app.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.blemesh.app.data.model.Peer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BLECentral(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private val _discoveredPeers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val discoveredPeers: StateFlow<Map<String, Peer>> = _discoveredPeers

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val connectedGatts = mutableMapOf<String, BluetoothGatt>()
    private val seenMessageIds = mutableSetOf<String>()

    var onMessageReceived: ((fromAddress: String, message: String) -> Unit)? = null

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val address = device.address
            val rssi = result.rssi

            val scanRecord = result.scanRecord
            val manufacturerData = scanRecord?.getManufacturerSpecificData(0xFFFF)
            val username = manufacturerData?.let { String(it, Charsets.UTF_8) } ?: "Unknown"

            if (username == "Unknown" || username.isBlank()) return

            val peer = Peer(
                deviceId = address,
                deviceAddress = address,
                username = username,
                rssi = rssi,
                lastSeen = System.currentTimeMillis()
            )

            _discoveredPeers.value = _discoveredPeers.value.toMutableMap().apply {
                put(address, peer)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLECentral", "Scan failed: $errorCode")
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BLEConstants.SERVICE_UUID))
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(filters, settings, scanCallback)
        _isScanning.value = true
        Log.d("BLECentral", "Scanning started")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanner?.stopScan(scanCallback)
        _isScanning.value = false
    }

    @SuppressLint("MissingPermission")
    fun connectToPeer(address: String, onConnected: () -> Unit, onDisconnected: () -> Unit) {
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connectedGatts[address] = gatt
                        // Requesting MTU is async, we must wait for onMtuChanged to discover services
                        gatt.requestMtu(BLEConstants.MTU_SIZE)
                        updatePeerConnection(address, true)
                        // DO NOT call onConnected() here! Services are not discovered yet.
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connectedGatts.remove(address)
                        gatt.close()
                        updatePeerConnection(address, false)
                        onDisconnected()
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(BLEConstants.SERVICE_UUID)
                    val messageChar = service?.getCharacteristic(BLEConstants.MESSAGE_CHAR_UUID)
                    if (messageChar != null) {
                        gatt.setCharacteristicNotification(messageChar, true)
                        val descriptor = messageChar.getDescriptor(BLEConstants.CLIENT_CONFIG_DESCRIPTOR)
                        descriptor?.let {
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(it)
                        }
                    }
                    
                    // We must wait for descriptor to write before sending messages to avoid pipe collision
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        onConnected()
                    }, 300)
                }
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (characteristic.uuid == BLEConstants.MESSAGE_CHAR_UUID) {
                    val payload = String(characteristic.value, Charsets.UTF_8)
                    onMessageReceived?.invoke(gatt.device.address, payload)
                }
            }
        }

        device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun sendMessage(address: String, message: String): Boolean {
        val gatt = connectedGatts[address] ?: return false
        val service = gatt.getService(BLEConstants.SERVICE_UUID) ?: return false
        val characteristic = service.getCharacteristic(BLEConstants.MESSAGE_CHAR_UUID) ?: return false

        @Suppress("DEPRECATION")
        characteristic.value = message.toByteArray(Charsets.UTF_8)
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        @Suppress("DEPRECATION")
        return gatt.writeCharacteristic(characteristic)
    }

    @SuppressLint("MissingPermission")
    fun disconnectAll() {
        connectedGatts.values.forEach { gatt ->
            gatt.disconnect()
            gatt.close()
        }
        connectedGatts.clear()
    }

    fun isConnectedTo(address: String): Boolean = connectedGatts.containsKey(address)

    fun clearPeers() {
        _discoveredPeers.value = emptyMap()
    }

    private fun updatePeerConnection(address: String, connected: Boolean) {
        _discoveredPeers.value = _discoveredPeers.value.toMutableMap().apply {
            get(address)?.let { put(address, it.copy(isConnected = connected)) }
        }
    }
}
