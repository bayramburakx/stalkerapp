package com.stalkerapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    // Kullanıcı isteği: mavi yok — siyah / beyaz / açık gri palet.
    primary = Color(0xFFE9EEF5),
    onPrimary = Color(0xFF10151C),
    primaryContainer = Color(0xFF2A3340),
    onPrimaryContainer = Color(0xFFE9EEF5),
    secondary = Color(0xFFC7CFDA),
    // Kullanıcı isteği: uygulama arka planı tam siyah (hero altı dahil).
    background = Color.Black,
    onBackground = Color(0xFFE9EEF5),
    surface = Color.Black,
    onSurface = Color(0xFFE9EEF5),
    surfaceVariant = Color(0xFF1F2630),
    onSurfaceVariant = Color(0xFF9AA6B5),
    outline = Color(0xFF2A3340),
    error = Color(0xFFFF5C5C)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF10151C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E7EE),
    onPrimaryContainer = Color(0xFF10151C),
    secondary = Color(0xFF56616F),
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
