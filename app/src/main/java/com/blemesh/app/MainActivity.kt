package com.blemesh.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import androidx.core.content.ContextCompat
import com.blemesh.app.ble.MeshService
import com.blemesh.app.ui.navigation.AppNavigation
import com.blemesh.app.ui.theme.BLEMeshTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val app by lazy { application as BLEMeshApp }
    private var hasRequestedPermissions = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        Log.d("MainActivity", "Permissions result: allGranted=$allGranted")
        if (allGranted) {
            app.bleManager.checkBleState()
            
            val intent = Intent(this, MeshService::class.java)
            ContextCompat.startForegroundService(this, intent)
            Log.d("MainActivity", "MeshService started after permission grant")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            BLEMeshTheme {
                val identity by app.preferencesManager.identity.collectAsStateWithLifecycle(initialValue = null)

                // Initialize BLE and auto-request permissions when identity is ready
                LaunchedEffect(identity) {
                    identity?.let {
                        Log.d("MainActivity", "Identity loaded: ${it.username}")
                        app.bleManager.initialize(it.username, it.deviceId)

                        // Auto-request permissions if not already granted
                        if (!app.bleManager.hasPermissions() && !hasRequestedPermissions) {
                            hasRequestedPermissions = true
                            Log.d("MainActivity", "Auto-requesting BLE permissions")
                            permissionLauncher.launch(app.bleManager.getRequiredPermissions())
                        } else if (app.bleManager.hasPermissions()) {
                            // Permissions already granted, start immediately
                            Log.d("MainActivity", "Permissions OK, starting MeshService")
                            val serviceIntent = Intent(this@MainActivity, MeshService::class.java)
                            ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
                        }
                    }
                }

                AppNavigation(
                    identity = identity,
                    bleManager = app.bleManager,
                    onIdentitySet = { username ->
                        CoroutineScope(Dispatchers.IO).launch {
                            app.preferencesManager.saveIdentity(username)
                        }
                    },
                    onUpdateUsername = { username ->
                        CoroutineScope(Dispatchers.IO).launch {
                            app.preferencesManager.updateUsername(username)
                        }
                    },
                    onRequestPermissions = {
                        permissionLauncher.launch(app.bleManager.getRequiredPermissions())
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        app.bleManager.isAppInForeground = true
    }

    override fun onPause() {
        super.onPause()
        app.bleManager.isAppInForeground = false
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        val address = intent.getStringExtra("OPEN_CHAT_ADDRESS")
        val username = intent.getStringExtra("OPEN_CHAT_USERNAME")
        if (address != null && username != null) {
            Log.d("MainActivity", "Deep linking to chat with $username ($address)")
            app.bleManager.setPendingChat(address, username)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Do NOT cleanup BLEManager here, because we want MeshService to keep it running!
    }
}
