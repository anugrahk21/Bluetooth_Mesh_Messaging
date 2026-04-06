package com.blemesh.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.blemesh.app.data.model.Message
import com.blemesh.app.data.model.MessageStatus
import com.blemesh.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageBubble(
    message: Message,
    isOwn: Boolean,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (isOwn) SentBubble else ReceivedBubble
    val textColor = if (isOwn) SentBubbleText else ReceivedBubbleText
    val shape = if (isOwn) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Column {
                if (!isOwn) {
                    Text(
                        text = message.fromUsername,
                        style = MaterialTheme.typography.labelSmall,
                        color = Purple60,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.6f)
                    )
                    if (isOwn) {
                        val (icon, tint) = when (message.status) {
                            MessageStatus.SENDING -> Icons.Default.Schedule to textColor.copy(alpha = 0.6f)
                            MessageStatus.SENT -> Icons.Default.Check to textColor.copy(alpha = 0.6f)
                            MessageStatus.DELIVERED -> Icons.Default.DoneAll to Purple60
                            MessageStatus.FAILED -> Icons.Default.ErrorOutline to ErrorLight
                        }
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
