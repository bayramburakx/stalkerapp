package com.stalkerapp.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * Portio Design System - Colors
 */
object PortioColors {
    // Backgrounds & Surfaces
    val Background = Color(0xFF07070A)
    val BackgroundAmoled = Color(0xFF000000)
    val Surface = Color(0xFF0E0E14)
    val SurfaceRaised = Color(0xFF161620)
    val SurfaceElevated = Color(0xFF1E1E2C)
    val SurfaceVariant = Color(0x1AFFFFFF)
    val SurfaceGlass = Color(0x990A0A10)
    val SurfaceGlassBorder = Color(0x2EFFFFFF)

    // Primaries & Accents
    val Primary = Color(0xFFFFFFFF)
    val OnPrimary = Color(0xFF000000)
    val PrimaryVariant = Color(0xFFE2E8F0)
    val PrimaryContainer = Color(0x33FFFFFF)
    val OnPrimaryContainer = Color(0xFFFFFFFF)

    // Vibrant Accent Colors
    val Accent = Color(0xFF38BDF8) // Default Accent Cyan-Blue
    val AccentRed = Color(0xFFE50914)
    val AccentBlue = Color(0xFF3B82F6)
    val AccentGreen = Color(0xFF10B981)
    val AccentPurple = Color(0xFF8B5CF6)
    val AccentOrange = Color(0xFFF97316)
    val AccentYellow = Color(0xFFF59E0B)
    val AccentCyan = Color(0xFF06B6D4)

    // Text & Content
    val OnSurface = Color(0xFFFFFFFF)
    val OnSurfaceVariant = Color(0xB3FFFFFF)
    val OnSurfaceMuted = Color(0x80FFFFFF)
    val OnSurfaceDisabled = Color(0x40FFFFFF)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xB3FFFFFF)
    val TextMuted = Color(0x80FFFFFF)

    // Status Colors
    val Live = Color(0xFFEF4444)
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFFF453A)
    val Info = Color(0xFF38BDF8)

    // Badges
    val Badge4K = Color(0xFFF59E0B)
    val BadgeFHD = Color(0xFF38BDF8)
    val BadgeHD = Color(0xFF818CF8)
    val BadgeSeries = Color(0xFFE50914)
    val BadgeWatched = Color(0xFF22C55E)
    val BadgeRating = Color(0xFFFFC107)

    // Hairlines & Focus (TV / Controller)
    val Hairline = Color.White.copy(alpha = 0.12f)
    val HairlineStrong = Color.White.copy(alpha = 0.22f)
    val FocusBorder = Color(0xFFFFFFFF)
    val FocusGlow = Color(0xFFFFFFFF)

    // Gradients
    val GlassGradient: Brush
        get() = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.04f)
            )
        )

    val BackdropScrim: Brush
        get() = Brush.verticalGradient(
            listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.55f),
                Color.Black
            )
        )

    val HeroGradient: Brush
        get() = Brush.verticalGradient(
            listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.4f),
                Color(0xFF07070A)
            )
        )

    val PosterOverlay: Brush
        get() = Brush.verticalGradient(
            listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.85f)
            )
        )

    fun badgeGradient(seed: String): Brush {
        val hash = seed.hashCode()
        val colors = when (Math.abs(hash) % 6) {
            0 -> listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6)) // Blue
            1 -> listOf(Color(0xFF831843), Color(0xFFEC4899)) // Pink
            2 -> listOf(Color(0xFF14532D), Color(0xFF22C55E)) // Green
            3 -> listOf(Color(0xFF581C87), Color(0xFFA855F7)) // Purple
            4 -> listOf(Color(0xFF7C2D12), Color(0xFFF97316)) // Orange
            else -> listOf(Color(0xFF0F172A), Color(0xFF334155)) // Slate
        }
        return Brush.linearGradient(colors)
    }

    val DarkColorScheme = darkColorScheme(
        primary = Primary,
        onPrimary = OnPrimary,
        primaryContainer = PrimaryContainer,
        onPrimaryContainer = OnPrimaryContainer,
        secondary = OnSurfaceMuted,
        onSecondary = OnSurface,
        background = Background,
        onBackground = OnSurface,
        surface = Surface,
        onSurface = OnSurface,
        surfaceVariant = SurfaceVariant,
        onSurfaceVariant = OnSurfaceVariant,
        outline = Hairline,
        error = Error
    )

    val LightColorScheme = lightColorScheme(
        primary = Color.Black,
        onPrimary = Color.White,
        primaryContainer = Color(0x1A000000),
        onPrimaryContainer = Color.Black,
        secondary = Color(0x80000000),
        onSecondary = Color.Black,
        background = Color(0xFFF2F2F7),
        onBackground = Color.Black,
        surface = Color.White,
        onSurface = Color.Black,
        surfaceVariant = Color(0x1A000000),
        onSurfaceVariant = Color(0xB3000000),
        outline = Color(0x33000000),
        error = Color(0xFFFF3B30)
    )

    val AmoledColorScheme = DarkColorScheme.copy(
        background = BackgroundAmoled,
        surface = BackgroundAmoled,
        surfaceVariant = Color(0xFF0E0E14)
    )
}
