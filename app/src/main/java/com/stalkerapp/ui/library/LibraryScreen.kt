package com.stalkerapp.ui.library

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.vod.VodPoster

private val L10nLocal: Map<String, String> = mapOf(
    // Türkçe -> English
    "Tümü" to "All",
    "Devam Eden" to "In Progress",
    "İzlediklerim" to "Watched",
    "Sonra İzle" to "Watch Later",
    "Favoriler" to "Favorites",
    "Kütüphanem" to "My Library",
    "Kütüphane yükleniyor…" to "Loading your library…",
    "Kütüphanen henüz boş.\nİzlediğin ve favorilediğin içerikler burada görünür." to "Your library is still empty.\nContent you watch and favorite will show up here.",
    "Favori Kanallar" to "Favorite Channels",
    "Sırala" to "Reorder",
    "Tamam" to "Done",
    "Favori kanalların sırasını yukarı/aşağı butonlarıyla değiştir" to "Change favorite channel order using up/down buttons",
    "Bu listede henüz içerik yok. Bir içeriğin detayından listeye ekleyebilirsin." to "This list is still empty. You can add content to it from the item's details.",
    "İzlemeye Devam" to "Keep Watching",
    "devam et" to "continue",
    "Sonra İzleyeceklerim" to "Watch Later",
    "bölüm izlendi" to "episodes watched",
    "Devam eden içerik yok" to "No content in progress",
    "Henüz izlediğin içerik yok" to "Nothing watched yet",
    "Sonra izle listesi boş" to "Watch later list is empty",
    "Favori içerik yok" to "No favorites yet",
    "Favori Filmler & Diziler" to "Favorite Movies & Series"
)
private fun str(lang: String, text: String): String =
    if (lang == "en") L10nLocal[text] ?: text else text

private enum class LibFilter(val label: String) {
    ALL("Tümü"),
    CONTINUE("Devam Eden"),
    WATCHED("İzlediklerim"),
    WATCH_LATER("Sonra İzle"),
    FAVORITES("Favoriler")
}

/**
 * Kütüphanem: kullanıcının izleme geçmişi ve listeleri tek ekranda.
 * Filtre çipleriyle devam edenler / izlediklerim / sonra izle / favoriler /
 * özel listeler arasında gezilir (arama ekranındaki gibi).
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

    // Favori canlı TV kanalları (tam Channel nesnesi olarak saklanır).
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

    // Katalog hazırsa id -> öğe haritası (listelerdeki öğeleri zenginleştirmek için).
    val byId = remember(catalog.allItems) {
        catalog.allItems.associateBy { it.id }
    }
    fun resolve(item: VodItem): VodItem = byId[item.id] ?: item

    fun isWatched(item: VodItem): Boolean {
        val p = vodProgress[item.id]
        return item.id in watchedOverrides ||
            (p != null && p.durationMs > 0 && p.positionMs >= p.durationMs * 0.85)
    }

    // Devam edenler: %5..%85 arası ilerlemesi olan filmler + bölüm ilerlemesi olan diziler.
    val continueItems = remember(vodProgress, episodeProgress, byId) {
        val vodIds = vodProgress.filter { (_, p) ->
            p.durationMs > 0 && p.positionMs > 0 &&
                p.positionMs < p.durationMs * 0.85
        }.keys
        val seriesIds = episodeProgress.keys.mapNotNull { key ->
            key.substringBefore(':').toLongOrNull()
        }.toSet()
        // Katalog byId'sinde yoksa ilerleme kaydındaki öğe anlık görüntüsüne düş
        // (katalog senkronu tamamlanmamış olsa bile "Devam Eden" dolu görünür).
        (vodIds + seriesIds).mapNotNull { id -> byId[id] ?: vodProgress[id]?.toVodItem(id) }
            .distinctBy { it.id }
            .sortedByDescending { vodProgress[it.id]?.lastUpdated ?: 0L }
            .take(20)
    }

    // İzlenenler: override / %85+ izlenen film + en az bir bölümü izlenen diziler.
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

    // Seçili özel liste öğeleri (katalog varsa zenginleştirilir).
    val activeList = userLists.firstOrNull { it.id == activeListId }
    val listItems = remember(activeList, byId) {
        activeList?.itemIds?.mapNotNull { byId[it] } ?: emptyList()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            str(lang, "Kütüphanem"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        // Başlık ile filtre çipleri arasında nefes payı.
        Spacer(modifier = Modifier.height(4.dp))

        // Filtre çipleri (arama ekranındaki gibi).
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LibFilter.entries.toList()) { f ->
                GlassChip(
                    selected = filter == f && activeListId == null,
                    onClick = { filter = f; activeListId = null },
                    label = str(lang, f.label)
                )
            }
            items(userLists) { l ->
                GlassChip(
                    selected = activeListId == l.id,
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
                        str(lang, "Kütüphane yükleniyor…")
                    else
                        str(lang, "Kütüphanen henüz boş.\nİzlediğin ve favorilediğin içerikler burada görünür.")
                )
            }
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                // Favori kanallar her filtrenin üstünde sabit gösterilir
                // (Kütüphanem'de canlı TV favorileri de görünmeli).
                favChannels.isNotEmpty() && activeList == null && filter == LibFilter.ALL -> {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                str(lang, "Favori Kanallar") + " (${favChannels.size})",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            GlassChip(
                                selected = isReorderingChannels,
                                onClick = { isReorderingChannels = !isReorderingChannels },
                                label = if (isReorderingChannels) str(lang, "Tamam") else str(lang, "Sırala")
                            )
                        }
                    }
                    if (isReorderingChannels) {
                        item {
                            Text(
                                str(lang, "Favori kanalların sırasını yukarı/aşağı butonlarıyla değiştir"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    itemsIndexed(favChannels, key = { _, ch -> ch.id }) { index, ch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isReorderingChannels) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else Color.Transparent),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isReorderingChannels) {
                                Column(
                                    modifier = Modifier.padding(start = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    IconButton(
                                        onClick = { moveChannel(index, index - 1) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Yukarı", tint = if (index > 0) MaterialTheme.colorScheme.primary else Color.Gray)
                                    }
                                    IconButton(
                                        onClick = { moveChannel(index, index + 1) },
                                        enabled = index < favChannels.size - 1,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Aşağı", tint = if (index < favChannels.size - 1) MaterialTheme.colorScheme.primary else Color.Gray)
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
                        HorizontalDivider()
                    }
                }
                activeList != null -> {
                    item { SectionHeader("📁 ${activeList.name}") }
                    item {
                        if (listItems.isEmpty()) {
                            Text(
                                str(lang, "Bu listede henüz içerik yok. Bir içeriğin detayından listeye ekleyebilirsin."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                    item { PosterRow(listItems, profile, catalog.isSeriesItem, onOpenVod, watchedOverrides, vodProgress) }
                }
                filter == LibFilter.ALL -> {
                    if (continueItems.isNotEmpty()) {
                        item { SectionHeader(str(lang, "İzlemeye Devam")) }
                        item { PosterRow(continueItems, profile, catalog.isSeriesItem, onOpenVod, watchedOverrides, vodProgress) { item ->
                            // Dizi ise ilerleme bölüm bazında olabilir.
                            val seriesEps = episodeProgress.filterKeys { it.startsWith("${item.id}:") }
                            if (seriesEps.isNotEmpty()) {
                                val latest = seriesEps.maxByOrNull { (k, v) -> v.lastUpdated }
                                val parts = latest?.key?.split(":") ?: emptyList()
                                if (parts.size >= 3) "S${parts[1]} · B${parts[2]} — ${str(lang, "devam et")}"
                                else null
                            } else null
                        } }
                    }
                    if (watchLaterResolved.isNotEmpty()) {
                        item { SectionHeader(str(lang, "Sonra İzleyeceklerim")) }
                        item { PosterRow(watchLaterResolved, profile, catalog.isSeriesItem, onOpenVod, watchedOverrides, vodProgress) }
                    }
                    if (favResolved.isNotEmpty()) {
                        item { SectionHeader(str(lang, "Favoriler")) }
                        item { PosterRow(favResolved, profile, catalog.isSeriesItem, onOpenVod, watchedOverrides, vodProgress) }
                    }
                    if (watchedItems.isNotEmpty()) {
                        item { SectionHeader(str(lang, "İzlediklerim")) }
                        item { PosterRow(watchedItems, profile, catalog.isSeriesItem, onOpenVod, watchedOverrides, vodProgress) { item ->
                            val eps = watchedEps.count { it.startsWith("${item.id}:") }
                            if (eps > 0) "$eps ${str(lang, "bölüm izlendi")}" else null
                        } }
                    }
                    userLists.filter { it.itemIds.isNotEmpty() }.forEach { l ->
                        item { SectionHeader("📁 ${l.name}") }
                        item { PosterRow(
                            l.itemIds.mapNotNull { byId[it] },
                            profile, catalog.isSeriesItem, onOpenVod, watchedOverrides, vodProgress
                        ) }
                    }
                }
                filter == LibFilter.CONTINUE -> {
                    if (continueItems.isEmpty()) {
                        item { EmptyHint(str(lang, "Devam eden içerik yok")) }
                    } else {
                        item { SectionHeader(str(lang, "İzlemeye Devam")) }
                        item { PosterRow(continueItems, profile, catalog.isSeriesItem, onOpenVod, watchedOverrides, vodProgress) }
                    }
                }
                filter == LibFilter.WATCHED -> {
                    if (watchedItems.isEmpty()) {
                        item { EmptyHint(str(lang, "Henüz izlediğin içerik yok")) }
                    } else {
                        item { SectionHeader(str(lang, "İzlediklerim")) }
                        item { PosterRow(watchedItems, profile, catalog.isSeriesItem, onOpenVod, watchedOverrides, vodProgress) { item ->
                            val eps = watchedEps.count { it.startsWith("${item.id}:") }
                            if (eps > 0) "$eps ${str(lang, "bölüm izlendi")}" else null
                        } }
                    }
                }
                filter == LibFilter.WATCH_LATER -> {
                    if (watchLaterResolved.isEmpty()) {
                        item { EmptyHint(str(lang, "Sonra izle listesi boş")) }
                    } else {
                        item { SectionHeader(str(lang, "Sonra İzleyeceklerim")) }
                        item { PosterRow(watchLaterResolved, profile, catalog.isSeriesItem, onOpenVod, watchedOverrides, vodProgress) }
                    }
                }
                filter == LibFilter.FAVORITES -> {
                    if (favResolved.isEmpty() && favChannels.isEmpty()) {
                        item { EmptyHint(str(lang, "Favori içerik yok")) }
                    } else {
                        if (favResolved.isNotEmpty()) {
                            item { SectionHeader(str(lang, "Favori Filmler & Diziler")) }
                            item { PosterRow(favResolved, profile, catalog.isSeriesItem, onOpenVod, watchedOverrides, vodProgress) }
                        }
                        if (favChannels.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        str(lang, "Favori Kanallar") + " (${favChannels.size})",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    GlassChip(
                                        selected = isReorderingChannels,
                                        onClick = { isReorderingChannels = !isReorderingChannels },
                                        label = if (isReorderingChannels) str(lang, "Tamam") else str(lang, "Sırala")
                                    )
                                }
                            }
                            if (isReorderingChannels) {
                                item {
                                    Text(
                                        str(lang, "Favori kanalların sırasını yukarı/aşağı butonlarıyla değiştir"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            itemsIndexed(favChannels, key = { _, ch -> ch.id }) { index, ch ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isReorderingChannels) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else Color.Transparent),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isReorderingChannels) {
                                        Column(
                                            modifier = Modifier.padding(start = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            IconButton(
                                                onClick = { moveChannel(index, index - 1) },
                                                enabled = index > 0,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Yukarı", tint = if (index > 0) MaterialTheme.colorScheme.primary else Color.Gray)
                                            }
                                            IconButton(
                                                onClick = { moveChannel(index, index + 1) },
                                                enabled = index < favChannels.size - 1,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Aşağı", tint = if (index < favChannels.size - 1) MaterialTheme.colorScheme.primary else Color.Gray)
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
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
private fun PosterRow(
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
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items, key = { it.id }) { item ->
            val p = vodProgress[item.id]
            val watched = item.id in watchedOverrides ||
                (p != null && p.durationMs > 0 && p.positionMs >= p.durationMs * 0.85)
            Column(modifier = Modifier.width(120.dp)) {
                VodPoster(
                    item = item,
                    baseUrl = profile?.baseUrl.orEmpty(),
                    isSeries = isSeries(item),
                    watched = watched,
                    onClick = { onOpenVod(item.id, isSeries(item)) }
                )
                subtitle?.invoke(item)?.let { s ->
                    Text(
                        s,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
