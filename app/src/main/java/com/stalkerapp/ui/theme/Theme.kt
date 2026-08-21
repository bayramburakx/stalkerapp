package com.stalkerapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.stalkerapp.StalkerApp

val LocalPortioColors = staticCompositionLocalOf { PortioColors }
val LocalPortioTypography = staticCompositionLocalOf { PortioTypography }
val LocalPortioShape = staticCompositionLocalOf { PortioShape }

/**
 * PortioTheme - Ana MaterialTheme sarmalayıcısı.
 * Tema modu (sistem/açık/koyu/AMOLED), vurgu rengi ve yazı ölçeğini uygular.
 */
@Composable
fun PortioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as? StalkerApp
    val settings = app?.store?.settings()

    val themeMode = settings?.themeMode ?: "dark"
    val dark = when (themeMode) {
        "light" -> false
        "dark", "amoled" -> true
        else -> darkTheme
    }

    var colors = if (dark) PortioColors.DarkColorScheme else PortioColors.LightColorScheme

    // Vurgu rengi: atanmışsa primary/container ondan türetilir.
    val accent = settings?.accentColor ?: 0L
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
    if (themeMode == "amoled") {
        colors = colors.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF101418)
        )
    }

    // Yazı ölçeği: tüm sp tabanlı metinleri ölçeklendirir (LocalDensity.fontScale).
    val density = LocalDensity.current
    val fontScale = (settings?.uiFontScale ?: 1.0f).coerceIn(0.85f, 1.4f)

    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * fontScale),
        LocalPortioColors provides PortioColors,
        LocalPortioTypography provides PortioTypography,
        LocalPortioShape provides PortioShape
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = PortioTypography.MaterialTypography,
            shapes = PortioShape.MaterialShapes,
            content = content
        )
    }
}

/**
 * Geriye dönük uyumluluk için StalkerTheme wrapper'ı.
 */
@Composable
fun StalkerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    PortioTheme(darkTheme = darkTheme, content = content)
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
