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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.ExternalVod
import com.stalkerapp.data.Genre
import com.stalkerapp.data.Profile
import com.stalkerapp.data.VodFilterState
import com.stalkerapp.data.VodItem
import com.stalkerapp.data.matches
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.VodCatalogState
import com.stalkerapp.ui.VodCatalogStatus
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.GlassChip
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.ui.components.PortioButton
import com.stalkerapp.ui.components.PortioButtonStyle
import com.stalkerapp.ui.components.PortioMediaCard
import com.stalkerapp.ui.components.PortioSearchBar
import com.stalkerapp.ui.components.SectionTitle
import com.stalkerapp.ui.components.VodQuickActionsSheet
import com.stalkerapp.ui.components.resolveUrl
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.ui.vod.VodFilterDialog
import com.stalkerapp.util.L10n

/**
 * Portio Filmler Ekranı (MoviesScreen) - Özel Film Kataloğu, Filtreleme ve Sıralama
 */
@Composable
fun MoviesScreen(
    profile: Profile?,
    onOpenVod: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val lang = vm.store.settings().language
    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val adultUnlocked by vm.adultUnlocked.collectAsStateWithLifecycle()
    val externalSource = vm.enabledSourceKind() == "m3u" || vm.enabledSourceKind() == "xtream"

    if (profile == null && !externalSource) {
        Box(modifier = modifier.fillMaxSize().background(PortioColors.Background), contentAlignment = Alignment.Center) {
            Text(
                L10n.t(lang, "Henüz kaynak eklenmedi.\nVOD kataloğu için Ayarlar → Playlist & Kaynaklar bölümünden bir Stalker portal, M3U listesi veya Xtream Codes ekleyebilirsin."),
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    var selectedCategory by remember { mutableStateOf(0L) }
    var query by remember { mutableStateOf("") }
    var quickActionItem by remember { mutableStateOf<VodItem?>(null) }
    var visibleCount by remember { mutableStateOf(90) }
    val pageStep = 120
    val gridState = rememberLazyGridState()
    val watchedVersion by vm.watchedVersion.collectAsStateWithLifecycle()
    val watchedOverrides = remember(watchedVersion) { app.store.watchedOverrides() }
    val vodProgress = remember(watchedVersion) { app.store.loadVodProgress() }

    fun isWatched(item: VodItem): Boolean {
        val p = vodProgress[item.id]
        return item.id in watchedOverrides ||
            (p != null && p.durationMs > 0 && p.positionMs >= p.durationMs * 0.85)
    }

    val activeKind = vm.enabledSourceKind()
    val activeSourceId = vm.activeSourceId()
    LaunchedEffect(profile, activeKind, activeSourceId) {
        if (activeKind == "m3u" || activeKind == "xtream") {
            vm.ensureExternalVodCatalog()
        } else {
            profile?.let { vm.syncVodIfNeeded(it) }
        }
    }

    LaunchedEffect(selectedCategory, query) {
        visibleCount = 90
        gridState.scrollToItem(0)
    }

    val adultVisible = settings.adultContentEnabled && (!settings.lockAdultWithPin || adultUnlocked)
    val blockedCategoryIds = remember(catalog.categories, settings.hiddenCategories, adultVisible) {
        val adultRegex = Regex("18|yetkin|adult|xxx|erotik|porno", RegexOption.IGNORE_CASE)
        val hiddenSet = settings.hiddenCategories.toSet()
        catalog.categories.filter { cat ->
            val hidden = hiddenSet.contains(cat.title)
            val adult = adultRegex.containsMatchIn(cat.title)
            hidden || (!adultVisible && adult)
        }.map { it.id }.toSet()
    }

    var showFilterDialog by remember { mutableStateOf(false) }
    var filterState by remember { mutableStateOf(VodFilterState()) }

    val filtered by androidx.compose.runtime.produceState(
        initialValue = emptyList<VodItem>(),
        catalog.movies.size,
        catalog.allItems.size,
        catalog.status,
        selectedCategory,
        query,
        filterState,
        blockedCategoryIds
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val q = query.trim()
            var list = if (catalog.movies.isNotEmpty()) catalog.movies else catalog.allItems.filter { !catalog.isSeriesItem(it) }
            if (selectedCategory != 0L) {
                list = list.filter { it.categoryId == selectedCategory }
            }
            if (blockedCategoryIds.isNotEmpty()) {
                list = list.filter { it.categoryId !in blockedCategoryIds }
            }

            if (q.isNotBlank()) {
                list = list.filter {
                    it.name.contains(q, ignoreCase = true) ||
                    it.originalName.contains(q, ignoreCase = true)
                }
            }

            if (filterState.isActive) {
                list = list.filter { item ->
                    filterState.matches(
                        name = item.name,
                        year = item.year,
                        rating = item.rating,
                        language = item.country
                    )
                }
                list = when (filterState.sortMode) {
                    com.stalkerapp.data.SortMode.DEFAULT -> list
                    com.stalkerapp.data.SortMode.A_Z -> list.sortedBy { it.name.lowercase() }
                    com.stalkerapp.data.SortMode.Z_A -> list.sortedByDescending { it.name.lowercase() }
                    com.stalkerapp.data.SortMode.NEWEST -> list.sortedByDescending {
                        it.year.take(4).toIntOrNull() ?: it.addedTimestamp.toInt()
                    }
                    com.stalkerapp.data.SortMode.HIGHEST_RATED -> list.sortedByDescending {
                        it.rating.replace(',', '.').substringBefore('/').trim().toFloatOrNull() ?: 0f
                    }
                }
            }
            list.filter { it.id > 0 }.distinctBy { it.id }
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && visibleCount < filtered.size) {
            visibleCount += pageStep
        }
    }

    val catList = catalog.categories
    val seriesCatIds = catalog.seriesCategoryIds
    val shownCats = remember(catList, seriesCatIds, blockedCategoryIds, catalog.movies.size) {
        val raw = catList.filter {
            it.id !in seriesCatIds && !ExternalVod.isSeriesCat(it.id) &&
            it.id < com.stalkerapp.data.PortalRepository.SERIES_CAT_BASE &&
            !VodCatalogState.isSeriesCatTitle(it.title)
        }.filter { it.id !in blockedCategoryIds }

        if (raw.isNotEmpty()) raw
        else if (catList.isNotEmpty()) {
            catList.filter { it.id !in blockedCategoryIds }
        } else {
            val items = if (catalog.movies.isNotEmpty()) catalog.movies else catalog.allItems.filter { !catalog.isSeriesItem(it) }
            items.mapNotNull { it.genres.takeIf { g -> g.isNotBlank() } ?: it.country.takeIf { c -> c.isNotBlank() } }
                .distinct()
                .take(30)
                .mapIndexed { idx, name -> Genre(id = -(idx + 100L), title = name) }
        }
    }

    val sectionTitle = remember(selectedCategory, shownCats, query) {
        when {
            query.isNotBlank() -> L10n.t(lang, "Film ara…")
            selectedCategory == 0L -> L10n.t(lang, "Tüm Filmler")
            else -> shownCats.firstOrNull { it.id == selectedCategory }?.title ?: L10n.t(lang, "Tümü")
        }
    }

    Column(modifier = modifier.fillMaxSize().background(PortioColors.Background)) {
        if (catalog.status == VodCatalogStatus.Error) {
            Text(
                L10n.t(lang, "VOD senkron hatası. Yenileme için kategorileri açın."),
                style = MaterialTheme.typography.labelSmall,
                color = PortioColors.Error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        PortioSearchBar(
            query = query,
            onQueryChange = { query = it },
            placeholder = L10n.t(lang, "Film ara…"),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (shownCats.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp)
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        GlassChip(
                            selected = selectedCategory == 0L && query.isBlank(),
                            onClick = { selectedCategory = 0L },
                            label = L10n.t(lang, "Tümü")
                        )
                    }
                    items(shownCats) { c ->
                        GlassChip(
                            selected = selectedCategory == c.id,
                            onClick = { selectedCategory = c.id },
                            label = c.title
                        )
                    }
                }
                PortioButton(
                    onClick = { showFilterDialog = true },
                    style = PortioButtonStyle.Secondary,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = L10n.t(lang, "Filtreler"),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        when {
            catalog.status == VodCatalogStatus.Syncing && catalog.allItems.isEmpty() -> LoadingBox()
            catalog.allItems.isEmpty() && catalog.status != VodCatalogStatus.Syncing ->
                EmptyState(L10n.t(lang, "Film içeriği bulunamadı"))
            filtered.isEmpty() -> EmptyState(L10n.t(lang, "İçerik bulunamadı"))
            else -> {
                val pageItems = filtered.take(visibleCount)
                Column(modifier = Modifier.fillMaxSize()) {
                    SectionTitle(
                        title = sectionTitle,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 135.dp),
                        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(pageItems, key = { it.id }) { item ->
                            val poster = resolveUrl(item.poster, profile?.baseUrl.orEmpty())
                            PortioMediaCard(
                                title = item.name,
                                posterUrl = poster,
                                subtitle = item.year.take(4),
                                watched = isWatched(item),
                                rating = item.rating,
                                onClick = { onOpenVod(item.id, false) },
                                onLongClick = { quickActionItem = item }
                            )
                        }
                        if (visibleCount < filtered.size) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (quickActionItem != null) {
        val qi = quickActionItem!!
        VodQuickActionsSheet(
            lang = lang,
            item = qi,
            isSeries = false,
            vm = vm,
            onOpenDetail = { onOpenVod(qi.id, false) },
            onDismiss = { quickActionItem = null }
        )
    }

    if (showFilterDialog) {
        VodFilterDialog(
            lang = lang,
            state = filterState,
            onDismiss = { showFilterDialog = false },
            onApply = { newState ->
                filterState = newState
                showFilterDialog = false
            }
        )
    }
}
