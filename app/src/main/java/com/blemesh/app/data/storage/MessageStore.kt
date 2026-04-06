package com.blemesh.app.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.blemesh.app.data.model.Message
import com.blemesh.app.data.model.MessageStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MessageStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("messages", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveMessages(peerAddress: String, messages: List<Message>) {
        prefs.edit().putString("chat_$peerAddress", gson.toJson(messages)).apply()
    }

    fun loadMessages(peerAddress: String): List<Message> {
        val json = prefs.getString("chat_$peerAddress", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Message>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getAllPeerAddresses(): Set<String> {
        return prefs.all.keys
            .filter { it.startsWith("chat_") }
            .map { it.removePrefix("chat_") }
            .toSet()
    }
}
