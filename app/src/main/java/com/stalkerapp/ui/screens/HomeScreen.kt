package com.stalkerapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stalkerapp.StalkerApp
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.components.AdultPinDialog
import com.stalkerapp.ui.components.NavigationTabItem
import com.stalkerapp.ui.components.PortioBottomNavBar
import com.stalkerapp.ui.home.HomeDashboardScreen
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.util.L10n

/**
 * Portio Ana Ekranı (HomeScreen) - Sekmeli gezinme & TV/Mobil desteği
 */
@Composable
fun HomeScreen(
    onOpenPlayer: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onOpenOnboarding: () -> Unit = {},
    onOpenProfiles: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    var profile by remember { mutableStateOf(vm.repository.cachedProfile()) }
    val lang = vm.store.settings().language

    var tab by remember {
        mutableIntStateOf(vm.store.settings().defaultTab.coerceIn(0, 3))
    }
    val gotoTab: (Int) -> Unit = { tab = it }

    LaunchedEffect(Unit) {
        vm.resumeLastLiveChannelIfEnabled(profile)
    }

    val settings by vm.settings.collectAsStateWithLifecycle()
    val adultUnlocked by vm.adultUnlocked.collectAsStateWithLifecycle()
    var showAdultPin by remember { mutableStateOf(false) }

    LaunchedEffect(settings.adultContentEnabled, settings.lockAdultWithPin, adultUnlocked) {
        if (settings.adultContentEnabled && settings.lockAdultWithPin && !adultUnlocked) {
            showAdultPin = true
        }
    }

    if (showAdultPin) {
        AdultPinDialog(
            lang = lang,
            onUnlock = { pin ->
                if (vm.unlockAdult(pin)) {
                    showAdultPin = false
                    true
                } else false
            },
            onDismiss = { showAdultPin = false }
        )
    }

    val saveableStateHolder = rememberSaveableStateHolder()

    val navItems = listOf(
        NavigationTabItem(Icons.Default.Home, L10n.t(lang, "Ana Sayfa")),
        NavigationTabItem(Icons.Default.LiveTv, L10n.t(lang, "Canlı TV")),
        NavigationTabItem(Icons.Default.Movie, L10n.t(lang, "Filmler")),
        NavigationTabItem(Icons.Default.VideoLibrary, L10n.t(lang, "Diziler")),
        NavigationTabItem(Icons.Default.Download, L10n.t(lang, "İndirilenler")),
        NavigationTabItem(Icons.Default.Settings, L10n.t(lang, "Ayarlar")),
        NavigationTabItem(Icons.Default.Search, L10n.t(lang, "Ara"), onClick = onOpenSearch)
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            PortioBottomNavBar(
                items = navItems,
                selectedTabIndex = tab,
                onTabSelected = { tab = it }
            )
        }
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .background(PortioColors.Background)
            .padding(top = padding.calculateTopPadding())

        val portalKey = when (vm.activeSourceKind()) {
            "m3u", "xtream" -> "${vm.activeSourceKind()}:${vm.activeSourceId() ?: "none"}"
            else -> profile?.portal?.id ?: "none"
        }

        saveableStateHolder.SaveableStateProvider("$tab:$portalKey") {
            when (tab) {
                0 -> HomeDashboardScreen(profile, onOpenVod, onOpenPlayer, gotoTab, contentModifier)
                1 -> LiveTvScreen(profile, onOpenPlayer, contentModifier.statusBarsPadding(), onOpenGuide = onOpenGuide)
                2 -> MoviesScreen(profile, onOpenVod, contentModifier.statusBarsPadding())
                3 -> SeriesScreen(profile, onOpenVod, contentModifier.statusBarsPadding())
                4 -> DownloadsScreen(
                    onPlayOffline = { entry ->
                        val playUrl = com.stalkerapp.data.OfflineDownloadManager.getPlayableOfflineUrl(entry)
                        com.stalkerapp.playback.PlaybackManager.playOffline(
                            playUrl, entry.title, entry.poster, entry.episodeLabel
                        )
                        onOpenPlayer()
                    },
                    modifier = contentModifier.statusBarsPadding()
                )
                5 -> SettingsScreen(
                    vm = vm,
                    modifier = contentModifier.statusBarsPadding(),
                    onPortalsChanged = {
                        val p = vm.repository.cachedProfile()
                        profile = p
                        if (p != null) vm.syncVodIfNeeded(p)
                    },
                    onOpenLibrary = { tab = 6 },
                    onOpenPlayer = onOpenPlayer,
                    onBack = { gotoTab(0) },
                    onRestartSetup = onOpenOnboarding,
                    onOpenProfiles = onOpenProfiles
                )
                6 -> LibraryScreen(profile, onOpenPlayer, onOpenVod, contentModifier.statusBarsPadding())
            }
        }
    }
}
