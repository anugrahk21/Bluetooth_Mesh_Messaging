package com.blemesh.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SignalCellularAlt1Bar
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blemesh.app.data.model.Peer
import com.blemesh.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PeerCard(
    peer: Peer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (peer.isConnected) Surface3 else Surface2,
        label = "bgColor"
    )
    val avatarColor = Color(generateColorFromString(peer.username))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (peer.isConnected) 4.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AvatarCircle(username = peer.username, color = avatarColor, size = 52.dp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.username,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (peer.unreadCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${peer.unreadCount} new message${if (peer.unreadCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(OnlineGreen)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatLastSeen(peer.lastSeen),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (peer.isConnected) Icons.Default.BluetoothConnected
                    else Icons.Default.BluetoothSearching,
                    contentDescription = null,
                    tint = if (peer.isConnected) Purple60 else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                SignalIcon(rssi = peer.rssi)
            }
        }
    }
}

@Composable
private fun SignalIcon(rssi: Int) {
    val (icon, color) = when {
        rssi > -50 -> Icons.Default.SignalCellular4Bar to SignalStrong
        rssi > -70 -> Icons.Default.SignalCellularAlt to SignalMedium
        else -> Icons.Default.SignalCellularAlt1Bar to SignalWeak
    }
    Icon(imageVector = icon, contentDescription = "$rssi dBm", tint = color, modifier = Modifier.size(16.dp))
}

private fun formatLastSeen(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        else -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
