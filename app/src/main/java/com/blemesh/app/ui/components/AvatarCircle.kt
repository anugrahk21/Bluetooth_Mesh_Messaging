package com.blemesh.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AvatarCircle(
    username: String,
    color: Color,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = username.take(1).uppercase(),
            color = color,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun generateColorFromString(str: String): Long {
    val colors = listOf(
        0xFFBB86FC, 0xFF03DAC6, 0xFFCF6679, 0xFF6200EE,
        0xFF018786, 0xFFFF5722, 0xFF4CAF50, 0xFF2196F3,
        0xFFFF9800, 0xFF9C27B0, 0xFFE91E63, 0xFF00BCD4
    )
    return colors[str.hashCode().and(0x7FFFFFFF) % colors.size]
}
