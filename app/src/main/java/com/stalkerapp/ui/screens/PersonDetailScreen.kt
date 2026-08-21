package com.stalkerapp.ui.screens

import androidx.compose.runtime.Composable
import com.stalkerapp.ui.person.PersonScreen

/**
 * Portio Oyuncu & Yönetmen Detay Ekranı (PersonDetailScreen)
 */
@Composable
fun PersonDetailScreen(
    name: String,
    isDirector: Boolean,
    onBack: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit
) {
    PersonScreen(
        name = name,
        isDirector = isDirector,
        onBack = onBack,
        onOpenVod = onOpenVod
    )
}

/** Geriye dönük uyumluluk alias fonksiyonu */
@Composable
fun PersonScreen(
    name: String,
    isDirector: Boolean,
    onBack: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit
) {
    PersonDetailScreen(
        name = name,
        isDirector = isDirector,
        onBack = onBack,
        onOpenVod = onOpenVod
    )
}
