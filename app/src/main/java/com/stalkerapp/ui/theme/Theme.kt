package com.stalkerapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4F8CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A6E),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF9AA7FF),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE9EEF5),
    surface = Color(0xFF141923),
    onSurface = Color(0xFFE9EEF5),
    surfaceVariant = Color(0xFF1F2630),
    onSurfaceVariant = Color(0xFF9AA6B5),
    outline = Color(0xFF2A3340),
    error = Color(0xFFFF5C5C)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF0B2A66),
    background = Color(0xFFF4F6FB),
    onBackground = Color(0xFF10151C),
    surface = Color.White,
    onSurface = Color(0xFF10151C),
    surfaceVariant = Color(0xFFEAEFF6),
    onSurfaceVariant = Color(0xFF56616F),
    outline = Color(0xFFD5DCE6),
    error = Color(0xFFD32F2F)
)

@Composable
fun StalkerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
