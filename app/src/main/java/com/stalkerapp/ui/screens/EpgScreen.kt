package com.stalkerapp.ui.screens

import androidx.compose.runtime.Composable
import com.stalkerapp.data.Profile

/**
 * Portio EPG Rehberi Ekranı (EpgScreen)
 */
@Composable
fun EpgScreen(
    profile: Profile?,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    com.stalkerapp.ui.live.EpgGuideScreen(
        profile = profile,
        onBack = onBack,
        onOpenPlayer = onOpenPlayer
    )
}

/** Geriye dönük uyumluluk alias fonksiyonu */
@Composable
fun EpgGuideScreen(
    profile: Profile?,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    EpgScreen(
        profile = profile,
        onBack = onBack,
        onOpenPlayer = onOpenPlayer
    )
}
