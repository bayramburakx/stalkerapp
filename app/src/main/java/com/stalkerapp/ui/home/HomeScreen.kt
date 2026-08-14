package com.stalkerapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stalkerapp.StalkerApp
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.favorites.FavoritesScreen
import com.stalkerapp.ui.live.LiveTvScreen
import com.stalkerapp.ui.settings.SettingsScreen
import com.stalkerapp.ui.vod.VodScreen

private data class NavItem(val icon: ImageVector, val label: String, val onClick: (() -> Unit)? = null)

@Composable
fun HomeScreen(
    onOpenPlayer: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit,
    onOpenSearch: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    var profile by remember { mutableStateOf(vm.repository.cachedProfile()) }

    var tab by remember { mutableIntStateOf(0) }
    val gotoTab: (Int) -> Unit = { tab = it }

    val navItems = listOf(
        NavItem(Icons.Default.Home, "Ana Sayfa"),
        NavItem(Icons.Default.LiveTv, "Canlı TV"),
        NavItem(Icons.Default.Movie, "Filmler"),
        NavItem(Icons.Default.VideoLibrary, "Diziler"),
        NavItem(Icons.Default.Star, "Favoriler"),
        NavItem(Icons.Default.Settings, "Ayarlar"),
        NavItem(Icons.Default.Search, "Ara", onClick = onOpenSearch)
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Tek glass (cam) pill: yanlardan tam yuvarlak, gölge + boşluk ile
            // yüzer (floating) hissi, yarı saydam cam arka plan. Sabit yükseklik
            // (62dp) tek bir kutu — `blur` kullanılmaz (bazı cihazlarda öğenin
            // tüm ekranı kaplamasına yol açan Compose render sorunları var);
            // yarı saydam yüzey + ince çerçeve + gölge cam görünümünü verir.
            val glassShape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .height(62.dp)
                    .shadow(18.dp, glassShape)
                    .clip(glassShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.60f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                        shape = glassShape
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navItems.forEachIndexed { index, item ->
                        val selected = index == tab && item.onClick == null
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(glassShape)
                                .clickable { if (item.onClick != null) item.onClick() else tab = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(40),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(9.dp).size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        // İçerik yüzen cam pill'in ARKASINDAN akar (sadece üst iç boşluk
        // uygulanır); böylece pill'in arkasında dolu bir bant görünmez ve cam
        // efekt gerçek olur. Her sekme kendi listesinin sonuna pill yüksekliği
        // kadar boşluk ekler.
        val contentModifier = Modifier.padding(top = padding.calculateTopPadding())
        when (tab) {
            0 -> HomeDashboardScreen(profile, onOpenVod, onOpenPlayer, gotoTab, contentModifier)
            1 -> LiveTvScreen(profile, onOpenPlayer, contentModifier.statusBarsPadding())
            2 -> VodScreen(profile, onOpenVod, contentModifier.statusBarsPadding(), filterIsSeries = false)
            3 -> VodScreen(profile, onOpenVod, contentModifier.statusBarsPadding(), filterIsSeries = true)
            4 -> FavoritesScreen(profile, onOpenPlayer, onOpenVod, contentModifier.statusBarsPadding())
            5 -> SettingsScreen(vm, contentModifier.statusBarsPadding()) {
                val p = vm.repository.cachedProfile()
                profile = p
                if (p != null) vm.syncVodIfNeeded(p)
            }
        }
    }
}