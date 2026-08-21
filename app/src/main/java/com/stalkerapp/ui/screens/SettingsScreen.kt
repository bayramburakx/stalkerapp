package com.stalkerapp.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stalkerapp.ui.MainViewModel

/**
 * Portio Ayarlar Ekranı (SettingsScreen)
 */
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    onPortalsChanged: (() -> Unit)? = null,
    onOpenLibrary: (() -> Unit)? = null,
    onOpenPlayer: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onRestartSetup: (() -> Unit)? = null,
    onOpenProfiles: (() -> Unit)? = null
) {
    com.stalkerapp.ui.settings.SettingsScreen(
        vm = vm,
        modifier = modifier,
        onPortalsChanged = onPortalsChanged,
        onOpenLibrary = onOpenLibrary,
        onOpenPlayer = onOpenPlayer,
        onBack = onBack,
        onRestartSetup = onRestartSetup,
        onOpenProfiles = onOpenProfiles
    )
}
