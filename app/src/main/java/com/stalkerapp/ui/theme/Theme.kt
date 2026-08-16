package com.stalkerapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.stalkerapp.StalkerApp

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

/**
 * Tema modu (sistem/açık/koyu/AMOLED), vurgu rengi ve yazı ölçeği
 * Ayarlar → Görünüm & Cihaz'dan okunur. AMOLED modda arka plan tam siyahtır.
 */
@Composable
fun StalkerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val settings = (context.applicationContext as StalkerApp).store.settings()

    val dark = when (settings.themeMode) {
        "light" -> false
        "dark", "amoled" -> true
        else -> darkTheme
    }
    var colors = if (dark) DarkColors else LightColors

    // Vurgu rengi: atanmışsa primary/container ondan türetilir.
    val accent = settings.accentColor
    if (accent != 0L) {
        val c = Color(accent.toInt())
        val bg = if (dark) Color.Black else Color.White
        colors = colors.copy(
            primary = c,
            onPrimary = if (dark) Color.Black else Color.White,
            primaryContainer = c.copy(alpha = 0.18f).compositeOver(bg),
            onPrimaryContainer = if (dark) Color.White else Color(0xFF10151C)
        )
    }
    // AMOLED: tüm yüzeyler tam siyah.
    if (settings.themeMode == "amoled") {
        colors = colors.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF101418)
        )
    }

    // Yazı ölçeği: tüm sp tabanlı metinleri ölçeklendirir (LocalDensity.fontScale).
    val density = LocalDensity.current
    val fontScale = settings.uiFontScale.coerceIn(0.85f, 1.4f)

    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * fontScale)
    ) {
        MaterialTheme(
            colorScheme = colors,
            content = content
        )
    }
}

/** Giriş/onboarding ekranları için yumuşak degrade arka plan (tema renklerinden). */
@Composable
fun accentBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    return Brush.verticalGradient(
        listOf(
            scheme.background,
            scheme.surfaceVariant.copy(alpha = 0.4f).compositeOver(scheme.background),
            scheme.background
        )
    )
}
