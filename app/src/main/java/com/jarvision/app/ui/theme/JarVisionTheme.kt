package com.jarvision.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarVisionColors = darkColorScheme(
    primary = Color(0xFF1685FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0A3767),
    onPrimaryContainer = Color(0xFFDDEEFF),
    secondary = Color(0xFF63B8FF),
    onSecondary = Color(0xFF001C31),
    secondaryContainer = Color(0xFF10385E),
    onSecondaryContainer = Color(0xFFD7ECFF),
    background = Color(0xFF020B18),
    onBackground = Color(0xFFF0F6FF),
    surface = Color(0xFF06182C),
    onSurface = Color(0xFFF0F6FF),
    surfaceVariant = Color(0xFF0A2542),
    onSurfaceVariant = Color(0xFFA8C0DE),
    outline = Color(0xFF1D4168),
    outlineVariant = Color(0xFF12304F),
    surfaceTint = Color(0xFF1685FF)
)

@Composable
fun JarVisionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = JarVisionColors, typography = Typography(), content = content)
}
