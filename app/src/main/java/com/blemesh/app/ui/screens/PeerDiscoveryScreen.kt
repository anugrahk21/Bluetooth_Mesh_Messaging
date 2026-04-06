package com.blemesh.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blemesh.app.ble.BLEState
import com.blemesh.app.data.model.Peer
import com.blemesh.app.ui.components.AvatarCircle
import com.blemesh.app.ui.components.PeerCard
import com.blemesh.app.ui.components.generateColorFromString
import com.blemesh.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerDiscoveryScreen(
    username: String,
    peers: List<Peer>,
    isScanning: Boolean,
    bleState: BLEState,
    onPeerClick: (Peer) -> Unit,
    onToggleScan: () -> Unit,
    onSettingsClick: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanPulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOut), RepeatMode.Reverse),
        label = "scanPulse"
    )

    Scaffold(
        containerColor = Surface0,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MeshTalk", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            text = when (bleState) {
                                BLEState.READY -> if (isScanning) "Scanning..." else "Ready"
                                BLEState.DISABLED -> "Bluetooth disabled"
                                BLEState.NO_PERMISSIONS -> "Permissions needed"
                                BLEState.NOT_SUPPORTED -> "BLE not supported"
                                BLEState.UNKNOWN -> "Initializing..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (bleState) {
                                BLEState.READY -> if (isScanning) OnlineGreen else TextSecondary
                                else -> SignalWeak
                            }
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface0,
                    titleContentColor = TextPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (bleState == BLEState.NO_PERMISSIONS) onRequestPermissions()
                    else onToggleScan()
                },
                containerColor = if (isScanning) Surface4 else Purple40,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    if (isScanning) Icons.Default.Stop else Icons.Default.BluetoothSearching,
                    contentDescription = if (isScanning) "Stop" else "Scan"
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Identity card
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Surface2),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AvatarCircle(
                        username = username,
                        color = Color(generateColorFromString(username)),
                        size = 44.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(username, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("You", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    if (isScanning) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier.size(12.dp).scale(scanPulse).clip(CircleShape)
                                    .background(OnlineGreen.copy(alpha = 0.3f))
                            )
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(OnlineGreen))
                        }
                    }
                }
            }

            // Peers header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Nearby Devices",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${peers.size} found",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            if (bleState == BLEState.NO_PERMISSIONS) {
                // Permission prompt
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Purple60, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Permissions Required", style = MaterialTheme.typography.titleLarge, color = TextPrimary, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("MeshTalk needs Bluetooth and Location permissions to discover nearby devices.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onRequestPermissions, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple40)) {
                            Text("Grant Permissions")
                        }
                    }
                }
            } else if (peers.isEmpty()) {
                // Empty state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        if (isScanning) {
                            CircularProgressIndicator(color = Purple60, modifier = Modifier.size(48.dp), strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Looking for nearby devices...", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        } else {
                            Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No devices nearby", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (isScanning) "Make sure other devices have MeshTalk running"
                            else "Tap the scan button to discover nearby devices",
                            style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(peers, key = { it.deviceAddress }) { peer ->
                        PeerCard(peer = peer, onClick = { onPeerClick(peer) })
                    }
                }
            }
        }
    }
}
