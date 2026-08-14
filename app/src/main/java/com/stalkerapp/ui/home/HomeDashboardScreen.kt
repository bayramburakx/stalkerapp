package com.stalkerapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.Profile
import com.stalkerapp.data.VodItem
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.VodCatalogStatus
import com.stalkerapp.ui.components.ChannelLogo
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.ui.components.resolveUrl
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.vod.VodPoster

@Composable
fun HomeDashboardScreen(
    profile: Profile?,
    onOpenVod: (Long, Boolean) -> Unit,
    onOpenPlayer: () -> Unit,
    onGotoTab: (Int) -> Unit,
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
    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()
    val favChannels by vm.favoriteChannels.collectAsStateWithLifecycle()
    val favVods by vm.favoriteVods.collectAsStateWithLifecycle()

    var channels by remember { mutableStateOf<List<Channel>?>(null) }
    var loadingChannels by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        vm.syncVodIfNeeded(profile)
        try {
            channels = vm.repository.loadChannels(profile, 0).take(30)
        } catch (_: Exception) { }
        loadingChannels = false
    }

    val continueWatching = remember(catalog, app.store) {
        val progress = app.store.loadVodProgress()
        progress.mapNotNull { (id, p) ->
            if (p.durationMs > 0 && p.positionMs > 0 && p.positionMs < p.durationMs * 0.95) {
                catalog.byId[id]?.let { it to p }
            } else null
        }
    }

    val movies = remember(catalog) { catalog.allItems.filter { !catalog.isSeriesItem(it) }.take(20) }
    val series = remember(catalog) { catalog.allItems.filter { catalog.isSeriesItem(it) }.take(20) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        if (catalog.status == VodCatalogStatus.Syncing) {
            LinearProgressIndicator(
                progress = { if (catalog.totalCategories > 0) catalog.doneCategories.toFloat() / catalog.totalCategories else 0f },
                modifier = Modifier.fillMaxWidth().height(4.dp)
            )
            Text(
                "VOD kataloğu yükleniyor: ${catalog.loadedCount} içerik",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (continueWatching.isNotEmpty()) {
            Section(title = "Devam Et", onSeeAll = null) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(continueWatching, key = { it.first.id }) { (item, prog) ->
                        ContinueWatchingCard(
                            item = item,
                            baseUrl = profile.baseUrl,
                            positionMs = prog.positionMs,
                            durationMs = prog.durationMs,
                            onClick = { onOpenVod(item.id, catalog.isSeriesItem(item)) }
                        )
                    }
                }
            }
        }

        Section(title = "Favori Kanallar", onSeeAll = { onGotoTab(1) }) {
            if (favChannels.isEmpty()) {
                Text("Henüz favori kanal yok", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(favChannels.take(20), key = { it.id }) { ch ->
                        ChannelCard(
                            channel = ch,
                            baseUrl = profile.baseUrl,
                            onClick = {
                                runCatching {
                                    PlaybackManager.playChannel(listOf(ch), 0, profile)
                                    onOpenPlayer()
                                }
                            }
                        )
                    }
                }
            }
        }

        Section(title = "Canlı TV", onSeeAll = { onGotoTab(1) }) {
            when {
                loadingChannels -> LoadingBox()
                channels.isNullOrEmpty() -> EmptyState("Kanal bulunamadı")
                else -> LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(channels!!, key = { it.id }) { ch ->
                        ChannelCard(
                            channel = ch,
                            baseUrl = profile.baseUrl,
                            onClick = {
                                runCatching {
                                    PlaybackManager.playChannel(listOf(ch), 0, profile)
                                    onOpenPlayer()
                                }
                            }
                        )
                    }
                }
            }
        }

        Section(title = "Filmler", onSeeAll = { onGotoTab(2) }) {
            if (movies.isEmpty() && catalog.status != VodCatalogStatus.Syncing) {
                EmptyState("Film bulunamadı")
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(movies, key = { it.id }) { item ->
                        VodPoster(
                            item = item,
                            baseUrl = profile.baseUrl,
                            isSeries = false,
                            posterWidth = 120,
                            onClick = { onOpenVod(item.id, false) }
                        )
                    }
                }
            }
        }

        Section(title = "Diziler", onSeeAll = { onGotoTab(3) }) {
            if (series.isEmpty() && catalog.status != VodCatalogStatus.Syncing) {
                EmptyState("Dizi bulunamadı")
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(series, key = { it.id }) { item ->
                        VodPoster(
                            item = item,
                            baseUrl = profile.baseUrl,
                            isSeries = true,
                            posterWidth = 120,
                            onClick = { onOpenVod(item.id, true) }
                        )
                    }
                }
            }
        }

        if (favVods.isNotEmpty()) {
            Section(title = "Favori Filmler & Diziler", onSeeAll = { onGotoTab(4) }) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(favVods.take(20), key = { it.id }) { item ->
                        VodPoster(
                            item = item,
                            baseUrl = profile.baseUrl,
                            isSeries = catalog.isSeriesItem(item),
                            posterWidth = 120,
                            onClick = { onOpenVod(item.id, catalog.isSeriesItem(item)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, onSeeAll: (() -> Unit)?, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (onSeeAll != null) {
                Text(
                    "Tümü",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onSeeAll() }
                )
            }
        }
        content()
    }
}

@Composable
private fun ChannelCard(channel: Channel, baseUrl: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ChannelLogo(logo = resolveUrl(channel.logo, baseUrl), modifier = Modifier.size(40.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(channel.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (channel.tvGenreTitle.isNotBlank()) {
                    Text(channel.tvGenreTitle, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: VodItem,
    baseUrl: String,
    positionMs: Long,
    durationMs: Long,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        Card {
            Box {
                AsyncImage(
                    model = resolveUrl(item.poster, baseUrl),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                )
                LinearProgressIndicator(
                    progress = { if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                    color = Color(0xFFE50914)
                )
            }
            Column(modifier = Modifier.padding(6.dp)) {
                Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
