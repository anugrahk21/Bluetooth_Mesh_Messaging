package com.blemesh.app.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.blemesh.app.data.model.UserIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ble_mesh_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val USERNAME = stringPreferencesKey("username")
        val AVATAR_COLOR = longPreferencesKey("avatar_color")
        val CREATED_AT = longPreferencesKey("created_at")
        val IDENTITY_SET = booleanPreferencesKey("identity_set")
    }

    val identity: Flow<UserIdentity?> = context.dataStore.data.map { prefs ->
        if (prefs[IDENTITY_SET] == true) {
            UserIdentity(
                deviceId = prefs[DEVICE_ID] ?: "",
                username = prefs[USERNAME] ?: "",
                avatarColor = prefs[AVATAR_COLOR] ?: 0xFFBB86FC,
                createdAt = prefs[CREATED_AT] ?: 0L
            )
        } else null
    }

    val isIdentitySet: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IDENTITY_SET] == true
    }

    suspend fun saveIdentity(username: String) {
        context.dataStore.edit { prefs ->
            prefs[DEVICE_ID] = UUID.randomUUID().toString()
            prefs[USERNAME] = username
            prefs[AVATAR_COLOR] = generateAvatarColor(username)
            prefs[CREATED_AT] = System.currentTimeMillis()
            prefs[IDENTITY_SET] = true
        }
    }

    suspend fun updateUsername(username: String) {
        context.dataStore.edit { prefs ->
            prefs[USERNAME] = username
            prefs[AVATAR_COLOR] = generateAvatarColor(username)
        }
    }

    private fun generateAvatarColor(username: String): Long {
        val colors = listOf(
            0xFFBB86FC, 0xFF03DAC6, 0xFFCF6679, 0xFF6200EE,
            0xFF018786, 0xFFFF5722, 0xFF4CAF50, 0xFF2196F3,
            0xFFFF9800, 0xFF9C27B0, 0xFFE91E63, 0xFF00BCD4
        )
        return colors[username.hashCode().and(0x7FFFFFFF) % colors.size]
    }
}
