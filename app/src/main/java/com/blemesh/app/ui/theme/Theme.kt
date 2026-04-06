package com.blemesh.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Purple60,
    onPrimary = Color.White,
    primaryContainer = Purple40,
    onPrimaryContainer = Purple80,
    secondary = Teal60,
    onSecondary = Color.Black,
    secondaryContainer = Teal40,
    onSecondaryContainer = Teal80,
    error = ErrorLight,
    onError = Color.Black,
    background = Surface1,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface3,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF49454F),
    outlineVariant = Color(0xFF312F33),
)

@Composable
fun BLEMeshTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
