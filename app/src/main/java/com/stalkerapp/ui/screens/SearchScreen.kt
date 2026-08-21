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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.VodItem
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.VodCatalogStatus
import com.stalkerapp.ui.components.ChannelRow
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.GlassChip
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.ui.components.PortioMediaCard
import com.stalkerapp.ui.components.PortioSearchBar
import com.stalkerapp.ui.components.PortioTopAppBar
import com.stalkerapp.ui.components.SectionTitle
import com.stalkerapp.ui.components.resolveUrl
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.util.L10n
import kotlinx.coroutines.delay

/**
 * Portio Arama & Keşfet Ekranı (SearchScreen)
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit,
    onOpenPlayer: () -> Unit
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val lang = vm.store.settings().language
    val profile = vm.repository.cachedProfile()
    val favChannels by vm.favoriteChannels.collectAsStateWithLifecycle()
    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var allChannels by remember { mutableStateOf<List<Channel>?>(null) }
    var vodResults by remember { mutableStateOf<List<VodItem>?>(null) }
    var loadingVod by remember { mutableStateOf(false) }
    var vodMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (allChannels == null) {
            runCatching { allChannels = vm.loadChannelsForActiveSource(profile)?.second }
        }
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            vodResults = null
            vodMessage = null
            return@LaunchedEffect
        }
        delay(250)
        loadingVod = true
        vodMessage = null
        try {
            val q = query.trim()
            val cat = vm.vodCatalog.value
            vodResults = if (cat.allItems.isNotEmpty()) {
                cat.allItems.filter {
                    it.name.contains(q, ignoreCase = true) ||
                        it.originalName.contains(q, ignoreCase = true)
                }.take(80)
            } else if (profile != null) {
                runCatching { vm.repository.loadVodList(profile, 0, 1, q) }.getOrDefault(emptyList())
            } else {
                vodMessage = L10n.t(lang, "Bu kaynakta VOD araması desteklenmiyor")
                emptyList()
            }
        } finally {
            loadingVod = false
        }
    }

    val liveFiltered = remember(query, allChannels) {
        val q = query.trim()
        if (q.isBlank()) emptyList()
        else (allChannels ?: emptyList()).filter { it.name.contains(q, ignoreCase = true) }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Column {
                PortioTopAppBar(
                    title = L10n.t(lang, "Arama & Keşfet"),
                    onBack = onBack
                )
                PortioSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = L10n.t(lang, "Film, dizi, kanal ara…"),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PortioColors.Background)
                .padding(padding)
        ) {
            if (query.isBlank()) {
                DiscoverContent(
                    catalog = catalog,
                    baseUrl = profile?.baseUrl.orEmpty(),
                    onOpenVod = onOpenVod,
                    lang = lang,
                    modifier = Modifier.fillMaxSize()
                )
                return@Scaffold
            }

            val vodList = vodResults
            LazyColumn(
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (liveFiltered.isNotEmpty()) {
                    item {
                        SectionTitle(
                            title = L10n.t(lang, "Kanallar") + " (${liveFiltered.size})",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(liveFiltered, key = { it.id }) { ch ->
                        val isFav = favChannels.any { it.id == ch.id }
                        ChannelRow(
                            channel = ch,
                            baseUrl = profile?.baseUrl.orEmpty(),
                            isFavorite = isFav,
                            onToggleFavorite = { vm.toggleFavoriteChannel(ch) },
                            onClick = {
                                val list = liveFiltered
                                val idx = list.indexOfFirst { it.id == ch.id }
                                if (idx >= 0) {
                                    PlaybackManager.playChannel(list, idx, profile)
                                    onOpenPlayer()
                                }
                            }
                        )
                        HorizontalDivider(color = PortioColors.Hairline)
                    }
                }

                if (loadingVod && vodList == null) {
                    item { LoadingBox() }
                } else if (vodList != null && vodList.isNotEmpty()) {
                    item {
                        SectionTitle(
                            title = L10n.t(lang, "Film & Dizi") + " (${vodList.size})",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                    item {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 135.dp),
                            modifier = Modifier.fillMaxWidth().height(520.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            items(vodList, key = { it.id }) { item ->
                                val isSeries = item.isSeries || item.seriesRef.isNotBlank()
                                PortioMediaCard(
                                    title = item.name,
                                    posterUrl = resolveUrl(item.poster, profile?.baseUrl.orEmpty()),
                                    subtitle = item.year.take(4),
                                    badgeText = if (isSeries) "DİZİ" else null,
                                    rating = item.rating,
                                    onClick = { onOpenVod(item.id, isSeries) }
                                )
                            }
                        }
                    }
                } else if (liveFiltered.isEmpty() && vodList != null && vodList.isEmpty() && !loadingVod) {
                    item { EmptyState(vodMessage ?: L10n.t(lang, "Sonuç bulunamadı")) }
                }
            }
        }
    }
}

@Composable
private fun DiscoverContent(
    catalog: com.stalkerapp.ui.VodCatalogState,
    baseUrl: String,
    onOpenVod: (Long, Boolean) -> Unit,
    lang: String,
    modifier: Modifier = Modifier
) {
    var typeFilter by remember { mutableStateOf(0) }
    var genreFilter by remember { mutableStateOf<Long?>(null) }

    val catTitle = remember(catalog.categories) { catalog.categories.associate { it.id to it.title } }
    val hasMovies = remember(catalog) { catalog.allItems.any { !catalog.isSeriesItem(it) } }
    val hasSeries = remember(catalog) { catalog.allItems.any { catalog.isSeriesItem(it) } }
    val hasDocs = remember(catalog) {
        catalog.categories.any { c ->
            c.title.contains("belgesel", ignoreCase = true) || c.title.contains("documentary", ignoreCase = true)
        }
    }
    val typeOptions = remember(catalog, hasMovies, hasSeries, hasDocs) {
        val opts = mutableListOf<Pair<Int, String>>()
        opts += 0 to L10n.t(lang, "Tümü")
        if (hasMovies) opts += 1 to L10n.t(lang, "Film")
        if (hasSeries) opts += 2 to L10n.t(lang, "Dizi")
        if (hasDocs) opts += 3 to L10n.t(lang, "Belgesel")
        opts
    }

    val genres = catalog.categories.filter { it.id != 0L }.take(60)
    val types = typeOptions.map { it.second }
    val typeToIndex = typeOptions.associate { it.first to typeOptions.indexOf(it) }
    val selectedTypeIndex = typeToIndex[typeFilter] ?: 0

    val discover = remember(catalog, typeFilter, genreFilter, typeOptions) {
        val allowedTypes = typeOptions.map { it.first }.toSet()
        catalog.allItems.filter { item ->
            val isSeries = catalog.isSeriesItem(item)
            val okType = when (typeFilter) {
                1 -> !isSeries
                2 -> isSeries
                3 -> catTitle[item.categoryId]?.let { t ->
                    t.contains("belgesel", ignoreCase = true) || t.contains("documentary", ignoreCase = true)
                } ?: false
                else -> true
            }
            val okGenre = genreFilter == null || item.categoryId == genreFilter
            okType && okGenre && (typeFilter == 0 || typeFilter in allowedTypes)
        }.take(150)
    }

    Column(modifier = modifier.fillMaxSize()) {
        SectionTitle(
            L10n.t(lang, "Keşfet"),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DropdownFilterChip(
                label = "${L10n.t(lang, "Tür")}: ${types.getOrElse(selectedTypeIndex) { types.firstOrNull() ?: "" }}",
                options = types,
                selected = selectedTypeIndex,
                onSelect = { i -> typeFilter = typeOptions.getOrNull(i)?.first ?: 0 }
            )
            if (genres.isNotEmpty()) {
                val genreOptions = listOf(null) + genres.map { it.id }
                val genreLabels = listOf(L10n.t(lang, "Kategori: Tümü")) + genres.map { it.title }
                DropdownFilterChip(
                    label = genreLabels[genreOptions.indexOf(genreFilter)],
                    options = genreLabels,
                    selected = genreOptions.indexOf(genreFilter),
                    onSelect = { i -> genreFilter = genreOptions[i] }
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize().weight(1f).padding(top = 8.dp)) {
            when {
                catalog.status == VodCatalogStatus.Syncing && catalog.allItems.isEmpty() -> LoadingBox()
                discover.isEmpty() -> EmptyState(L10n.t(lang, "İçerik bulunamadı"))
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 135.dp),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(discover, key = { it.id }) { item ->
                        val isSeries = item.isSeries || item.seriesRef.isNotBlank()
                        PortioMediaCard(
                            title = item.name,
                            posterUrl = resolveUrl(item.poster, baseUrl),
                            subtitle = item.year.take(4),
                            badgeText = if (isSeries) "DİZİ" else null,
                            rating = item.rating,
                            onClick = { onOpenVod(item.id, isSeries) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DropdownFilterChip(
    label: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        GlassChip(
            selected = selected > 0,
            onClick = { expanded = true },
            label = label
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = PortioColors.SurfaceRaised
        ) {
            options.forEachIndexed { i, opt ->
                DropdownMenuItem(
                    text = { Text(opt, color = Color.White) },
                    onClick = { onSelect(i); expanded = false }
                )
            }
        }
    }
}
