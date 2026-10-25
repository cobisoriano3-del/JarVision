package com.jarvision.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarVisionColors = darkColorScheme(
    primary = Color(0xFF78B9FF),
    onPrimary = Color(0xFF08243B),
    secondary = Color(0xFF8ED0FF),
    onSecondary = Color(0xFF09263A),
    background = Color(0xFF0A1019),
    onBackground = Color(0xFFE8EDF5),
    surface = Color(0xFF101A27),
    onSurface = Color(0xFFE8EDF5),
    surfaceVariant = Color(0xFF1B2939),
    onSurfaceVariant = Color(0xFFB3C2D4),
    outline = Color(0xFF394B60)
)

@Composable
fun JarVisionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarVisionColors,
        typography = Typography(),
        content = content
    )
}
