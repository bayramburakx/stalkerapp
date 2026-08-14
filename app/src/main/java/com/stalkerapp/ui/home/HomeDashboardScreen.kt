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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.delay

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
    val featured = remember(catalog) { (series.take(6) + movies.take(6)).shuffled() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp)
    ) {
        if (featured.isNotEmpty()) {
            HeroBanner(
                items = featured,
                baseUrl = profile.baseUrl,
                onOpenVod = onOpenVod
            )
        }

        if (continueWatching.isNotEmpty()) {
            Section(title = "İzlemeye Devam", onSeeAll = null) {
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

        Section(title = "Popüler Filmler", onSeeAll = { onGotoTab(2) }) {
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
                            posterWidth = 130,
                            onClick = { onOpenVod(item.id, false) }
                        )
                    }
                }
            }
        }

        Section(title = "Popüler Diziler", onSeeAll = { onGotoTab(3) }) {
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
                            posterWidth = 130,
                            onClick = { onOpenVod(item.id, true) }
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
                            posterWidth = 130,
                            onClick = { onOpenVod(item.id, catalog.isSeriesItem(item)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroBanner(
    items: List<VodItem>,
    baseUrl: String,
    onOpenVod: (Long, Boolean) -> Unit
) {
    // Hero covers ~1.5/3 (half) of the screen height so it doesn't look short.
    val heroHeight = with(LocalConfiguration.current) { screenHeightDp.dp * 1.5f / 3f }
    val pagerState = rememberPagerState(pageCount = { items.size })

    LaunchedEffect(pagerState) {
        if (items.size > 1) {
            while (true) {
                delay(5000)
                val next = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) { page ->
        val item = items[page]
        val isSeries = item.isSeries || item.seriesRef.isNotBlank()
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = resolveUrl(item.poster, baseUrl),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.15f to Color.Transparent,
                                0.75f to Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSeries) Color(0xFFE50914) else MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            if (isSeries) "DİZİ" else "FİLM",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (item.year.isNotBlank()) {
                        Text(item.year, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(
                    onClick = { onOpenVod(item.id, isSeries) },
                    modifier = Modifier.width(170.dp)
                ) {
                    Text("Detayları Gör")
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
            .width(160.dp)
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
            .width(130.dp)
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
