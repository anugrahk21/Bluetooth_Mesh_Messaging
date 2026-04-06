package com.blemesh.app.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.blemesh.app.ble.BLEManager
import com.blemesh.app.data.model.UserIdentity
import com.blemesh.app.ui.screens.*

sealed class Screen(val route: String) {
    data object Identity : Screen("identity")
    data object Discovery : Screen("discovery")
    data object Chat : Screen("chat/{peerAddress}/{peerUsername}") {
        fun createRoute(peerAddress: String, peerUsername: String) =
            "chat/$peerAddress/$peerUsername"
    }
    data object Settings : Screen("settings")
}

@Composable
fun AppNavigation(
    identity: UserIdentity?,
    bleManager: BLEManager,
    onIdentitySet: (String) -> Unit,
    onUpdateUsername: (String) -> Unit,
    onRequestPermissions: () -> Unit
) {
    val navController = rememberNavController()

    val startDestination = if (identity != null) Screen.Discovery.route else Screen.Identity.route

    val peers by bleManager.central.discoveredPeers.collectAsStateWithLifecycle()
    val isScanning by bleManager.central.isScanning.collectAsStateWithLifecycle()
    val isAdvertising by bleManager.peripheral.isAdvertising.collectAsStateWithLifecycle()
    val bleState by bleManager.bleState.collectAsStateWithLifecycle()
    val allMessages by bleManager.messages.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Identity.route) {
            IdentitySetupScreen(onIdentitySet = { username ->
                onIdentitySet(username)
                navController.navigate(Screen.Discovery.route) {
                    popUpTo(Screen.Identity.route) { inclusive = true }
                }
            })
        }

        composable(Screen.Discovery.route) {
            PeerDiscoveryScreen(
                username = identity?.username ?: "",
                peers = peers.values.toList().sortedByDescending { it.lastSeen },
                isScanning = isScanning,
                bleState = bleState,
                onPeerClick = { peer ->
                    navController.navigate(Screen.Chat.createRoute(peer.deviceAddress, peer.username))
                },
                onToggleScan = {
                    if (isScanning) bleManager.stopDiscovery()
                    else bleManager.startDiscovery()
                },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onRequestPermissions = onRequestPermissions
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("peerAddress") { type = NavType.StringType },
                navArgument("peerUsername") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val peerAddress = backStackEntry.arguments?.getString("peerAddress") ?: return@composable
            val peerUsername = backStackEntry.arguments?.getString("peerUsername") ?: return@composable
            val peerMessages = allMessages[peerAddress] ?: emptyList()
            val isConnected = peers[peerAddress]?.isConnected == true

            ChatScreen(
                peerUsername = peerUsername,
                peerAddress = peerAddress,
                messages = peerMessages,
                currentDeviceId = identity?.deviceId ?: "",
                isConnected = isConnected,
                onSendMessage = { text -> bleManager.sendMessage(peerAddress, text) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                username = identity?.username ?: "",
                deviceId = identity?.deviceId ?: "",
                isScanning = isScanning,
                isAdvertising = isAdvertising,
                onBack = { navController.popBackStack() },
                onUpdateUsername = onUpdateUsername
            )
        }
    }
}
