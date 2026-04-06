package com.blemesh.app.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.blemesh.app.data.model.Message
import com.blemesh.app.data.model.MessageStatus
import com.blemesh.app.data.model.Peer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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

    var isAppInForeground = false
    var currentChatPeerAddress: String? = null

    private val _pendingChatToOpen = MutableStateFlow<Pair<String, String>?>(null)
    val pendingChatToOpen: StateFlow<Pair<String, String>?> = _pendingChatToOpen

    fun setPendingChat(address: String, username: String) {
        _pendingChatToOpen.value = Pair(address, username)
    }

    fun clearPendingChat() {
        _pendingChatToOpen.value = null
    }

    fun initialize(username: String, deviceId: String) {
        currentUsername = username
        currentDeviceId = deviceId
        Log.d("BLEManager", "Initialized with user=$username, deviceId=$deviceId")

        // Auto-Reconnect & Flush Engine: Watch for discovered peers and auto-connect if they have pending messages
        CoroutineScope(Dispatchers.IO).launch {
            central.discoveredPeers.collect { peers ->
                peers.values.forEach { peer ->
                    val msgs = _messages.value[peer.deviceAddress] ?: emptyList()
                    val hasPending = msgs.any { it.status == MessageStatus.PENDING && it.fromDeviceId == currentDeviceId }
                    
                    if (hasPending) {
                        if (peer.isConnected) {
                            // If connected and has pending messages, flush them!
                            flushPendingMessages(peer.deviceAddress)
                        } else {
                            // Discovering a disconnected peer with pending messages: Request connection silently
                            central.connectToPeer(
                                address = peer.deviceAddress,
                                onConnected = { flushPendingMessages(peer.deviceAddress) },
                                onDisconnected = {}
                            )
                        }
                    }
                }
            }
        }

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
            status = MessageStatus.PENDING // Default to PENDING until proven SENT
        )

        addMessage(peerAddress, message)

        val payload = "$currentDeviceId|$currentUsername|$text"

        if (central.isConnectedTo(peerAddress)) {
            val sent = central.sendMessage(peerAddress, payload)
            updateStatus(peerAddress, message.id, if (sent) MessageStatus.SENT else MessageStatus.PENDING)
        } else {
            central.connectToPeer(
                address = peerAddress,
                onConnected = {
                    flushPendingMessages(peerAddress)
                },
                onDisconnected = {
                    // Stays pending in the queue
                }
            )
        }
    }

    private fun flushPendingMessages(peerAddress: String) {
        if (!central.isConnectedTo(peerAddress)) return

        val msgs = _messages.value[peerAddress] ?: return
        msgs.forEach { msg ->
            if (msg.status == MessageStatus.PENDING && msg.fromDeviceId == currentDeviceId) {
                val payload = "$currentDeviceId|${currentUsername}|${msg.text}"
                val sent = central.sendMessage(peerAddress, payload)
                if (sent) {
                    updateStatus(peerAddress, msg.id, MessageStatus.SENT)
                }
            }
        }
    }

    private fun handleIncoming(address: String, payload: String) {
        val parts = payload.split("|", limit = 3)
        if (parts.size < 3) return

        val senderUsername = parts[1]
        
        // Find existing discovered peer by username. If found, route the message
        // to their advertised MAC address, so the Chat screen maps it correctly.
        val peer = central.discoveredPeers.value.values.firstOrNull { it.username == senderUsername }
        val finalAddress = peer?.deviceAddress ?: address

        // If they messaged us before we scanned them, ensure they appear in the UI list
        if (peer == null) {
            central.forceAddPeer(finalAddress, senderUsername)
        }

        val message = Message(
            id = UUID.randomUUID().toString(),
            fromDeviceId = parts[0],
            toDeviceId = currentDeviceId,
            fromUsername = senderUsername,
            text = parts[2],
            status = MessageStatus.DELIVERED
        )

        addMessage(finalAddress, message)

        if (!isAppInForeground) {
            // App is totally closed or backgrounded, throw push notification
            showNotification(finalAddress, parts[1], parts[2])
        } else if (currentChatPeerAddress != finalAddress) {
            // App is open, but we aren't chatting with this person. Show unread badge instead.
            central.incrementUnreadCount(finalAddress)
        }
    }
    
    private fun showNotification(address: String, senderName: String, messageText: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // Tap the notification to jump to this peer's ChatScreen
        val intent = Intent(context, Class.forName("com.blemesh.app.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_CHAT_ADDRESS", address)
            putExtra("OPEN_CHAT_USERNAME", senderName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            address.hashCode(), // unique ID per chat so intents don't overwrite each other
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, "MESHTALK_CHANNEL")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setColor(0xFF5E8BFF.toInt()) // Neon Blue accent to match the dark theme
            .setContentTitle("New message from $senderName")
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(address.hashCode(), builder.build())
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
