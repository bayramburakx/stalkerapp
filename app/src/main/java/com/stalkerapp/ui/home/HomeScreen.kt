package com.stalkerapp.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stalkerapp.StalkerApp
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.favorites.FavoritesScreen
import com.stalkerapp.ui.live.LiveTvScreen
import com.stalkerapp.ui.settings.SettingsScreen
import com.stalkerapp.ui.vod.VodScreen

@Composable
fun HomeScreen(
    onOpenPlayer: () -> Unit,
    onOpenVod: (Long) -> Unit
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = viewModel { MainViewModel(app) }
    val profile = vm.repository.cachedProfile()

    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.LiveTv, contentDescription = null) },
                    label = { Text("Canlı TV") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Movie, contentDescription = null) },
                    label = { Text("VOD") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                    label = { Text("Favoriler") }
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Ayarlar") }
                )
            }
        }
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (tab) {
            0 -> LiveTvScreen(profile, onOpenPlayer, contentModifier)
            1 -> VodScreen(profile, onOpenVod, contentModifier)
            2 -> FavoritesScreen(profile, onOpenPlayer, onOpenVod, contentModifier)
            3 -> SettingsScreen(vm, contentModifier)
        }
    }
}
