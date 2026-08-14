package com.stalkerapp.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.Profile
import com.stalkerapp.data.VodItem
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.components.ChannelRow
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.vod.VodPoster
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(
    profile: Profile?,
    onOpenPlayer: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (profile == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Portal bağlı değil")
        }
        return
    }

    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val favChannels by vm.favoriteChannels.collectAsStateWithLifecycle()
    val favVods by vm.favoriteVods.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    if (favChannels.isEmpty() && favVods.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Henüz favori yok", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Alt boşluk: içerik yüzen cam pill'in arkasından akıyor, son öğenin
        // pill altında kaybolmaması için.
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            if (favChannels.isNotEmpty()) {
                item {
                    Text(
                        "Kanallar",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(favChannels, key = { "ch${it.id}" }) { ch ->
                    ChannelRow(
                        channel = ch,
                        baseUrl = profile.baseUrl,
                        isFavorite = true,
                        onToggleFavorite = { vm.toggleFavoriteChannel(ch) },
                        onClick = {
                            scope.launch {
                                val idx = favChannels.indexOfFirst { it.id == ch.id }
                                if (idx >= 0) {
                                    PlaybackManager.playChannel(favChannels, idx, profile)
                                    onOpenPlayer()
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
            if (favVods.isNotEmpty()) {
                item {
                    Text(
                        "Film & Dizi",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                item {
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(favVods, key = { it.id }) { item ->
                            VodPoster(
                                item = item,
                                baseUrl = profile.baseUrl,
                                onClick = { onOpenVod(item.id, item.isSeries) },
                                posterWidth = 120
                            )
                        }
                    }
                }
            }
        }
    }
}
