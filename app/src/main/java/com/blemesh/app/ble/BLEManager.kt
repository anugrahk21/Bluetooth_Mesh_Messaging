package com.blemesh.app.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.blemesh.app.data.model.Message
import com.blemesh.app.data.model.MessageStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class BLEManager(private val context: Context) {

    val central = BLECentral(context)
    val peripheral = BLEPeripheral(context)

    private val _messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val messages: StateFlow<Map<String, List<Message>>> = _messages

    private val _bleState = MutableStateFlow(BLEState.UNKNOWN)
    val bleState: StateFlow<BLEState> = _bleState

    private var currentUsername = ""
    private var currentDeviceId = ""

    fun initialize(username: String, deviceId: String) {
        currentUsername = username
        currentDeviceId = deviceId

        central.onMessageReceived = { address, payload -> handleIncoming(address, payload) }
        peripheral.onMessageReceived = { address, payload -> handleIncoming(address, payload) }

        createNotificationChannel()
        checkBleState()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "MeshTalk Messages"
            val descriptionText = "Notifications for incoming Bluetooth messages"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("MESHTALK_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun checkBleState() {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bm.adapter

        _bleState.value = when {
            adapter == null -> BLEState.NOT_SUPPORTED
            !adapter.isEnabled -> BLEState.DISABLED
            !hasPermissions() -> BLEState.NO_PERMISSIONS
            else -> BLEState.READY
        }
    }

    fun hasPermissions(): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = mutableListOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissions.toTypedArray()
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    fun startDiscovery() {
        checkBleState()
        if (_bleState.value != BLEState.READY) return

        peripheral.startAdvertising(currentUsername)
        central.startScan()
        Log.d("BLEManager", "Discovery started as $currentUsername")
    }

    fun stopDiscovery() {
        central.stopScan()
        peripheral.stopAdvertising()
    }

    fun sendMessage(peerAddress: String, text: String) {
        val message = Message(
            id = UUID.randomUUID().toString(),
            fromDeviceId = currentDeviceId,
            toDeviceId = peerAddress,
            fromUsername = currentUsername,
            text = text,
            status = MessageStatus.SENDING
        )

        addMessage(peerAddress, message)

        val payload = "$currentDeviceId|$currentUsername|$text"

        if (central.isConnectedTo(peerAddress)) {
            val sent = central.sendMessage(peerAddress, payload)
            updateStatus(peerAddress, message.id, if (sent) MessageStatus.SENT else MessageStatus.FAILED)
        } else {
            central.connectToPeer(
                address = peerAddress,
                onConnected = {
                    val sent = central.sendMessage(peerAddress, payload)
                    updateStatus(peerAddress, message.id, if (sent) MessageStatus.SENT else MessageStatus.FAILED)
                },
                onDisconnected = {
                    updateStatus(peerAddress, message.id, MessageStatus.FAILED)
                }
            )
        }
    }

    private fun handleIncoming(address: String, payload: String) {
        val parts = payload.split("|", limit = 3)
        if (parts.size < 3) return

        val message = Message(
            id = UUID.randomUUID().toString(),
            fromDeviceId = parts[0],
            toDeviceId = currentDeviceId,
            fromUsername = parts[1],
            text = parts[2],
            status = MessageStatus.DELIVERED
        )

        addMessage(address, message)
        showNotification(parts[1], parts[2])
    }
    
    private fun showNotification(senderName: String, messageText: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val builder = NotificationCompat.Builder(context, "MESHTALK_CHANNEL")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("New message from $senderName")
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun addMessage(peerAddress: String, message: Message) {
        _messages.value = _messages.value.toMutableMap().apply {
            put(peerAddress, getOrDefault(peerAddress, emptyList()) + message)
        }
    }

    private fun updateStatus(peerAddress: String, messageId: String, status: MessageStatus) {
        _messages.value = _messages.value.toMutableMap().apply {
            val updated = getOrDefault(peerAddress, emptyList()).map {
                if (it.id == messageId) it.copy(status = status) else it
            }
            put(peerAddress, updated)
        }
    }

    fun cleanup() {
        central.stopScan()
        central.disconnectAll()
        peripheral.stopAdvertising()
    }
}

enum class BLEState {
    UNKNOWN, NOT_SUPPORTED, DISABLED, NO_PERMISSIONS, READY
}
