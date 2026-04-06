package com.blemesh.app.data.model

data class Message(
    val id: String,
    val fromDeviceId: String,
    val toDeviceId: String,
    val fromUsername: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT
)

enum class MessageStatus {
    PENDING, SENDING, SENT, DELIVERED, FAILED
}
