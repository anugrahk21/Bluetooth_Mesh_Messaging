package com.blemesh.app.data.model

data class Peer(
    val deviceId: String,
    val deviceAddress: String,
    val username: String,
    val rssi: Int = 0,
    val lastSeen: Long = System.currentTimeMillis(),
    val isConnected: Boolean = false
)
