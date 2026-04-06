package com.blemesh.app.data.model

data class UserIdentity(
    val deviceId: String,
    val username: String,
    val avatarColor: Long = 0xFFBB86FC,
    val createdAt: Long = System.currentTimeMillis()
)
