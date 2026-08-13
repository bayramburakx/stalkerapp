package com.stalkerapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FA8FF),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1B3A6B),
    onPrimaryContainer = Color(0xFFCFE3FF),
    secondary = Color(0xFFB0BEC5),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    error = Color(0xFFF85149)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B63C4),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF161B22),
    surface = Color.White,
    onSurface = Color(0xFF161B22)
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
