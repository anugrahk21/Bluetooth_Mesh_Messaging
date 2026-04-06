package com.blemesh.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blemesh.app.ui.components.AvatarCircle
import com.blemesh.app.ui.components.generateColorFromString
import com.blemesh.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    username: String,
    deviceId: String,
    isScanning: Boolean,
    isAdvertising: Boolean,
    onBack: () -> Unit,
    onUpdateUsername: (String) -> Unit
) {
    var editingName by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(username) }

    Scaffold(
        containerColor = Surface0,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface0)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile section
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface2),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AvatarCircle(
                        username = username,
                        color = Color(generateColorFromString(username)),
                        size = 80.dp
                    )

                    if (editingName) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { if (it.length <= 20) newName = it.replace(Regex("[^a-zA-Z0-9_]"), "") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Purple60, unfocusedBorderColor = Surface4,
                                cursorColor = Purple60, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                focusedContainerColor = Surface3, unfocusedContainerColor = Surface3
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { editingName = false; newName = username }) { Text("Cancel") }
                            Button(
                                onClick = { if (newName.length >= 3) { onUpdateUsername(newName); editingName = false } },
                                colors = ButtonDefaults.buttonColors(containerColor = Purple40)
                            ) { Text("Save") }
                        }
                    } else {
                        Text(username, style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { editingName = true }) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit username")
                        }
                    }
                }
            }

            // Info section
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface2),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsRow(Icons.Default.Fingerprint, "Device ID", deviceId.take(8) + "...")
                    HorizontalDivider(color = Surface4)
                    SettingsRow(Icons.Default.BluetoothSearching, "Scanning", if (isScanning) "Active" else "Inactive")
                    HorizontalDivider(color = Surface4)
                    SettingsRow(Icons.Default.CellTower, "Advertising", if (isAdvertising) "Active" else "Inactive")
                }
            }

            // About
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface2),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsRow(Icons.Default.Info, "Version", "1.0.0")
                    HorizontalDivider(color = Surface4)
                    SettingsRow(Icons.Default.Bluetooth, "Protocol", "BLE 5.0")
                    HorizontalDivider(color = Surface4)
                    SettingsRow(Icons.Default.Hub, "Mesh", "Coming soon")
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Purple60, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}
