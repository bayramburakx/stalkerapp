package com.stalkerapp.ui.screens

import androidx.compose.runtime.Composable
import com.stalkerapp.ui.vod.VodDetailScreen

/**
 * Portio İçerik Detay Ekranı (ContentDetailScreen) - Film ve Dizi Detayları
 */
@Composable
fun ContentDetailScreen(
    vodId: Long,
    isSeriesHint: Boolean = false,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit = { _, _ -> },
    onOpenPerson: (String, Boolean) -> Unit = { _, _ -> }
) {
    VodDetailScreen(
        vodId = vodId,
        isSeriesHint = isSeriesHint,
        onBack = onBack,
        onOpenPlayer = onOpenPlayer,
        onOpenVod = onOpenVod,
        onOpenPerson = onOpenPerson
    )
}

/** Geriye dönük uyumluluk alias fonksiyonu */
@Composable
fun VodDetailScreen(
    vodId: Long,
    isSeriesHint: Boolean = false,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit = { _, _ -> },
    onOpenPerson: (String, Boolean) -> Unit = { _, _ -> }
) {
    ContentDetailScreen(
        vodId = vodId,
        isSeriesHint = isSeriesHint,
        onBack = onBack,
        onOpenPlayer = onOpenPlayer,
        onOpenVod = onOpenVod,
        onOpenPerson = onOpenPerson
    )
}
