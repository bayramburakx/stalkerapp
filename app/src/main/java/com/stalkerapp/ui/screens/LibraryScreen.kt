package com.stalkerapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Profile
import com.stalkerapp.data.VodItem
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.VodCatalogStatus
import com.stalkerapp.ui.components.ChannelRow
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.GlassChip
import com.stalkerapp.ui.components.GlassSurface
import com.stalkerapp.ui.components.PortioButton
import com.stalkerapp.ui.components.PortioButtonStyle
import com.stalkerapp.ui.components.PortioMediaCard
import com.stalkerapp.ui.components.SectionTitle
import com.stalkerapp.ui.components.resolveUrl
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.util.L10n

private enum class LibFilter(val label: String) {
    ALL("Tümü"),
    CONTINUE("Devam Eden"),
    WATCHED("İzlediklerim"),
    WATCH_LATER("Sonra İzle"),
    FAVORITES("Favoriler")
}

/**
 * Portio Kütüphanem Ekranı (LibraryScreen)
 */
@Composable
fun LibraryScreen(
    profile: Profile?,
    onOpenPlayer: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val lang = vm.store.settings().language
    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()
    val favVods by vm.favoriteVods.collectAsStateWithLifecycle()
    val watchLater by vm.watchLater.collectAsStateWithLifecycle()
    val userLists by vm.userLists.collectAsStateWithLifecycle()
    val watchedVersion by vm.watchedVersion.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf(LibFilter.ALL) }
    var activeListId by remember { mutableStateOf<String?>(null) }

    val watchedOverrides = remember(watchedVersion) { app.store.watchedOverrides() }
    val vodProgress = remember(watchedVersion) { app.store.loadVodProgress() }
    val episodeProgress = remember(watchedVersion) { app.store.episodeProgress() }
    val watchedEps = remember(watchedVersion) { app.store.watchedEpisodes() }

    val favChannels by vm.favoriteChannels.collectAsStateWithLifecycle()
    var isReorderingChannels by remember { mutableStateOf(false) }

    fun moveChannel(from: Int, to: Int) {
        if (from == to || from !in favChannels.indices || to !in favChannels.indices) return
        val list = favChannels.toMutableList()
        val moved = list.removeAt(from)
        list.add(to, moved)
        vm.store.saveFavoriteChannels(list)
        vm.refreshFlows()
    }

    val byId = remember(catalog.allItems) {
        catalog.allItems.associateBy { it.id }
    }
    fun resolve(item: VodItem): VodItem = byId[item.id] ?: item

    fun isWatched(item: VodItem): Boolean {
        val p = vodProgress[item.id]
        return item.id in watchedOverrides ||
            (p != null && p.durationMs > 0 && p.positionMs >= p.durationMs * 0.85)
    }

    val continueItems = remember(vodProgress, episodeProgress, byId) {
        val vodIds = vodProgress.filter { (_, p) ->
            p.durationMs > 0 && p.positionMs > 0 &&
                p.positionMs < p.durationMs * 0.85
        }.keys
        val seriesIds = episodeProgress.keys.mapNotNull { key ->
            key.substringBefore(':').toLongOrNull()
        }.toSet()
        (vodIds + seriesIds).mapNotNull { id -> byId[id] ?: vodProgress[id]?.toVodItem(id) }
            .distinctBy { it.id }
            .sortedByDescending { vodProgress[it.id]?.lastUpdated ?: 0L }
            .take(20)
    }

    val watchedItems = remember(watchedOverrides, vodProgress, watchedEps, byId) {
        val fromOverride = watchedOverrides.mapNotNull { byId[it] }
        val fromProgress = vodProgress.filter { (_, p) ->
            p.durationMs > 0 && p.positionMs >= p.durationMs * 0.85
        }.keys.mapNotNull { byId[it] }
        val fromEps = watchedEps.mapNotNull { key ->
            key.substringBefore(':').toLongOrNull()
        }.mapNotNull { byId[it] }
        (fromOverride + fromProgress + fromEps).distinctBy { it.id }
    }

    val watchLaterResolved = watchLater.map { resolve(it) }
    val favResolved = favVods.map { resolve(it) }

    val activeList = userLists.firstOrNull { it.id == activeListId }
    val listItems = remember(activeList, byId) {
        activeList?.itemIds?.mapNotNull { byId[it] } ?: emptyList()
    }

    Column(modifier = modifier.fillMaxSize().background(PortioColors.Background)) {
        SectionTitle(L10n.t(lang, "Kütüphanem"), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LibFilter.entries.toList()) { f ->
                val selected = filter == f && activeListId == null
                GlassChip(
                    selected = selected,
                    onClick = { filter = f; activeListId = null },
                    label = L10n.t(lang, f.label)
                )
            }
            items(userLists) { l ->
                val selected = activeListId == l.id
                GlassChip(
                    selected = selected,
                    onClick = { activeListId = l.id; filter = LibFilter.ALL },
                    label = "📁 ${l.name}"
                )
            }
        }

        val hasAny = continueItems.isNotEmpty() || watchedItems.isNotEmpty() ||
            watchLaterResolved.isNotEmpty() || favResolved.isNotEmpty() ||
            listItems.isNotEmpty() || favChannels.isNotEmpty() || catalog.allItems.isNotEmpty()

        if (!hasAny) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    if (catalog.status == VodCatalogStatus.Syncing)
                        L10n.t(lang, "Kütüphane yükleniyor…")
                    else
                        L10n.t(lang, "Kütüphanen henüz boş.\nİzlediğin ve favorilediğin içerikler burada görünür.")
                )
            }
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                activeList != null -> {
                    item { SectionTitle("📁 ${activeList.name}") }
                    item {
                        if (listItems.isEmpty()) {
                            Text(
                                L10n.t(lang, "Bu listede henüz içerik yok. Bir içeriğin detayından listeye ekleyebilirsin."),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                    item { LibPosterRow(listItems, profile, catalog.isSeriesItem, onOpenVod, watchedOverrides, vodProgress) }
                }
                filter == LibFilter.ALL -> {
                    if (continueItems.isNotEmpty()) {
                        item { SectionTitle(L10n.t(lang, "İzlemeye Devam")) }
                        item {
                            LibPosterRow(continueItems, profile, catalog.isSeriesItem, onOpenVod, watchedOverrides, vodProgress) { item ->
                                val seriesEps = episodeProgress.filterKeys { it.startsWith("${item.id}:") }
                                if (seriesEps.isNotEmpty()) {
                                    val latest = seriesEps.maxByOrNull { (_, v) -> v.lastUpdated }
                                    val parts = latest?.key?.split(":") ?: emptyList()
                                    if (parts.size >= 3) "S${parts[1]} · B${parts[2]}" else null
                                } else null
                            }
                        }
                    }
                    if (watchLaterResolved.isNotEmpty()) {
                        item { SectionTitle(L10n.t(lang, "Sonra İzleyeceklerim")) }
                        item { LibPosterRow(watchLaterResolved, profile, catalog.isSeriesItem, onOpenVod, watchedOverrides, vodProgress) }
                    }
                    if (favResolved.isNotEmpty()) {
                        item { SectionTitle(L10n.t(lang, "Favoriler")) }
                        item { LibPosterRow(favResolved, profile, catalog.isSeriesItem, onOpenVod, watchedOverrides, vodProgress) }
                    }
                    if (favChannels.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    L10n.t(lang, "Favori Kanallar") + " (${favChannels.size})",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                PortioButton(
                                    onClick = { isReorderingChannels = !isReorderingChannels },
                                    style = PortioButtonStyle.Secondary,
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(
                                        if (isReorderingChannels) L10n.t(lang, "Tamam") else L10n.t(lang, "Sırala"),
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        itemsIndexed(favChannels, key = { _, ch -> ch.id }) { index, ch ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isReorderingChannels) {
                                    Column(
                                        modifier = Modifier.padding(end = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        IconButton(
                                            onClick = { moveChannel(index, index - 1) },
                                            enabled = index > 0,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Yukarı", tint = if (index > 0) Color.White else Color.Gray)
                                        }
                                        IconButton(
                                            onClick = { moveChannel(index, index + 1) },
                                            enabled = index < favChannels.size - 1,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Aşağı", tint = if (index < favChannels.size - 1) Color.White else Color.Gray)
                                        }
                                    }
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ChannelRow(
                                        channel = ch,
                                        baseUrl = profile?.baseUrl.orEmpty(),
                                        isFavorite = true,
                                        onToggleFavorite = { vm.toggleFavoriteChannel(ch) },
                                        onClick = { c ->
                                            if (!isReorderingChannels) {
                                                PlaybackManager.playChannel(favChannels, favChannels.indexOfFirst { it.id == c.id }.coerceAtLeast(0), profile)
                                                onOpenPlayer()
                                            }
                                        }
                                    )
                                }
                            }
                            HorizontalDivider(color = PortioColors.Hairline)
                        }
                    }
                    if (watchedItems.isNotEmpty()) {
                        item { SectionTitle(L10n.t(lang, "İzlediklerim")) }
                        item {
                            LibPosterRow(watchedItems, profile, catalog.isSeriesItem, onOpenVod, watchedOverrides, vodProgress) { item ->
                                val eps = watchedEps.count { it.startsWith("${item.id}:") }
                                if (eps > 0) "$eps ${L10n.t(lang, "bölüm izlendi")}" else null
                            }
                        }
                    }
                }
                filter == LibFilter.CONTINUE -> {
                    if (continueItems.isEmpty()) {
                        item { Text(L10n.t(lang, "Devam eden içerik yok"), color = Color.White.copy(alpha = 0.6f)) }
                    } else {
                        item { SectionTitle(L10n.t(lang, "İzlemeye Devam")) }
                        item { LibPosterRow(continueItems, profile, catalog.isSeriesItem, onOpenVod, watchedOverrides, vodProgress) }
                    }
                }
                filter == LibFilter.WATCHED -> {
                    if (watchedItems.isEmpty()) {
                        item { Text(L10n.t(lang, "Henüz izlediğin içerik yok"), color = Color.White.copy(alpha = 0.6f)) }
                    } else {
                        item { SectionTitle(L10n.t(lang, "İzlediklerim")) }
                        item { LibPosterRow(watchedItems, profile, catalog.isSeriesItem, onOpenVod, watchedOverrides, vodProgress) }
                    }
                }
                filter == LibFilter.WATCH_LATER -> {
                    if (watchLaterResolved.isEmpty()) {
                        item { Text(L10n.t(lang, "Sonra izle listesi boş"), color = Color.White.copy(alpha = 0.6f)) }
                    } else {
                        item { SectionTitle(L10n.t(lang, "Sonra İzleyeceklerim")) }
                        item { LibPosterRow(watchLaterResolved, profile, catalog.isSeriesItem, onOpenVod, watchedOverrides, vodProgress) }
                    }
                }
                filter == LibFilter.FAVORITES -> {
                    if (favResolved.isEmpty() && favChannels.isEmpty()) {
                        item { Text(L10n.t(lang, "Favori içerik yok"), color = Color.White.copy(alpha = 0.6f)) }
                    } else {
                        if (favResolved.isNotEmpty()) {
                            item { SectionTitle(L10n.t(lang, "Favori Filmler & Diziler")) }
                            item { LibPosterRow(favResolved, profile, catalog.isSeriesItem, onOpenVod, watchedOverrides, vodProgress) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibPosterRow(
    items: List<VodItem>,
    profile: Profile?,
    isSeries: (VodItem) -> Boolean,
    onOpenVod: (Long, Boolean) -> Unit,
    watchedOverrides: Set<Long>,
    vodProgress: Map<Long, com.stalkerapp.data.VodProgress>,
    subtitle: ((VodItem) -> String?)? = null
) {
    if (items.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(items, key = { it.id }) { item ->
            val p = vodProgress[item.id]
            val watched = item.id in watchedOverrides ||
                (p != null && p.durationMs > 0 && p.positionMs >= p.durationMs * 0.85)
            Column(modifier = Modifier.width(120.dp)) {
                PortioMediaCard(
                    title = item.name,
                    posterUrl = resolveUrl(item.poster, profile?.baseUrl.orEmpty()),
                    subtitle = subtitle?.invoke(item) ?: item.year.take(4),
                    badgeText = if (isSeries(item)) "DİZİ" else null,
                    watched = watched,
                    rating = item.rating,
                    onClick = { onOpenVod(item.id, isSeries(item)) }
                )
            }
        }
    }
}
