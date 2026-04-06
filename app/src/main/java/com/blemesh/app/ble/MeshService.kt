package com.blemesh.app.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.blemesh.app.BLEMeshApp

class MeshService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    1,
                    notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) 
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE 
                    else 0
                )
            } else {
                startForeground(1, notification)
            }
            Log.d("MeshService", "Foreground service started successfully.")
        } catch (e: Exception) {
            Log.e("MeshService", "Failed to start foreground service", e)
        }

        // Start BLE discovery if permissions are met
        val app = application as BLEMeshApp
        if (app.bleManager.hasPermissions()) {
            app.bleManager.startDiscovery()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        val app = application as BLEMeshApp
        app.bleManager.stopDiscovery()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "MeshTalk Background"
            val descriptionText = "Keeps the MeshTalk network alive in the background"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel("MESHTALK_BG_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "MESHTALK_BG_CHANNEL")
            .setContentTitle("MeshTalk is running")
            .setContentText("Listening for mesh network messages")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
