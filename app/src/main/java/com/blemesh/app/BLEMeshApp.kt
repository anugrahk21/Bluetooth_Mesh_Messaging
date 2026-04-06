package com.blemesh.app

import android.app.Application
import com.blemesh.app.ble.BLEManager
import com.blemesh.app.data.storage.MessageStore
import com.blemesh.app.data.storage.PreferencesManager

class BLEMeshApp : Application() {

    lateinit var bleManager: BLEManager
        private set

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var messageStore: MessageStore
        private set

    override fun onCreate() {
        super.onCreate()
        bleManager = BLEManager(this)
        preferencesManager = PreferencesManager(this)
        messageStore = MessageStore(this)
    }
}
