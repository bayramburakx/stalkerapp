package com.stalkerapp.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.ui.components.GlassChip
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.vod.VodPoster
import kotlinx.coroutines.delay

private val L10nLocal: Map<String, String> = mapOf(
    // Türkçe -> English
    "Film, dizi, kanal ara…" to "Search movies, series, channels…",
    "Geri" to "Back",
    "Kanallar" to "Channels",
    "Film & Dizi" to "Movies & Series",
    "Bu kaynakta VOD araması desteklenmiyor" to "VOD search is not supported for this source",
    "Sonuç bulunamadı" to "No results found",
    "Keşfet" to "Discover",
    "Tümü" to "All",
    "Film" to "Movie",
    "Dizi" to "Series",
    "Belgesel" to "Documentary",
    "Tür" to "Genre",
    "Kategori: Tümü" to "Category: All",
    "İçerik bulunamadı" to "No content found",
)
private fun str(lang: String, text: String): String =
    if (lang == "en") L10nLocal[text] ?: text else text

@OptIn(ExperimentalMaterial3Api::class)
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
            // Aktif kaynağa göre kanalları yükle (Stalker / M3U / Xtream fark etmez).
            runCatching { allChannels = vm.loadChannelsForActiveSource(profile)?.second }
        }
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            vodResults = null
            vodMessage = null
            return@LaunchedEffect
        }
        delay(300)
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
                // M3U/Xtream-only (profile=null) kullanıcısında VOD kataloğu yoktur.
                vodMessage = str(lang, "Bu kaynakta VOD araması desteklenmiyor")
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
        topBar = {
            TopAppBar(
                title = {
                    // Anasayfa dili: cam arama çubuğu.
                    val searchShape = RoundedCornerShape(50)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(searchShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.60f))
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), searchShape)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        androidx.compose.foundation.text.BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (query.isBlank()) {
                                    Text(
                                        str(lang, "Film, dizi, kanal ara…"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                inner()
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = str(lang, "Geri"))
                    }
                }
            )
        }
    ) { padding ->
        if (query.isBlank()) {
            DiscoverContent(
                catalog = catalog,
                baseUrl = profile?.baseUrl.orEmpty(),
                onOpenVod = onOpenVod,
                lang = lang,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            return@Scaffold
        }

        val vodList = vodResults
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (liveFiltered.isNotEmpty()) {
                item {
                    Text("${str(lang, "Kanallar")} (${liveFiltered.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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
                }
            }
            if (loadingVod && vodList == null) {
                item { LoadingBox() }
            } else if (vodList != null && vodList.isNotEmpty()) {
                item {
                    Text("${str(lang, "Film & Dizi")} (${vodList.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                item {
                    LazyRow(contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(vodList, key = { it.id }) { item ->
                            val isSeries = item.isSeries || item.seriesRef.isNotBlank()
                            VodPoster(item = item, baseUrl = profile?.baseUrl.orEmpty(),
                                isSeries = isSeries, onClick = { onOpenVod(item.id, isSeries) }, posterWidth = 120)
                        }
                    }
                }
            } else if (liveFiltered.isEmpty() && vodList != null && vodList.isEmpty() && !loadingVod) {
                item { EmptyState(vodMessage ?: str(lang, "Sonuç bulunamadı")) }
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
        // Anasayfa dili: cam pill filtre + açılır ok.
        val pillShape = RoundedCornerShape(50)
        Row(
            modifier = Modifier
                .clip(pillShape)
                .background(
                    if (selected > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.60f)
                )
                .border(
                    1.dp,
                    if (selected > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                    pillShape
                )
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected > 0) FontWeight.Bold else FontWeight.Normal,
                color = if (selected > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = if (selected > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelect(i); expanded = false }
                )
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
    var typeFilter by remember { mutableStateOf(0) } // 0 tümü, 1 film, 2 dizi, 3 belgesel
    var genreFilter by remember { mutableStateOf<Long?>(null) }

    val catTitle = remember(catalog.categories) { catalog.categories.associate { it.id to it.title } }
    val types = listOf(str(lang, "Tümü"), str(lang, "Film"), str(lang, "Dizi"), str(lang, "Belgesel"))
    val genres = catalog.categories.take(14)

    val discover = remember(catalog, typeFilter, genreFilter) {
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
            okType && okGenre
        }.take(150)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            str(lang, "Keşfet"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DropdownFilterChip(
                label = "${str(lang, "Tür")}: ${types[typeFilter]}",
                options = types,
                selected = typeFilter,
                onSelect = { typeFilter = it }
            )
            if (genres.isNotEmpty()) {
                val genreOptions = listOf(null) + genres.map { it.id }
                val genreLabels = listOf(str(lang, "Kategori: Tümü")) + genres.map { it.title }
                DropdownFilterChip(
                    label = genreLabels[genreOptions.indexOf(genreFilter)],
                    options = genreLabels,
                    selected = genreOptions.indexOf(genreFilter),
                    onSelect = { i -> genreFilter = genreOptions[i] }
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when {
                catalog.status == VodCatalogStatus.Syncing && catalog.allItems.isEmpty() -> LoadingBox()
                discover.isEmpty() -> EmptyState(str(lang, "İçerik bulunamadı"))
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(discover, key = { it.id }) { item ->
                        val isSeries = item.isSeries || item.seriesRef.isNotBlank()
                        VodPoster(
                            item = item,
                            baseUrl = baseUrl,
                            isSeries = isSeries,
                            onClick = { onOpenVod(item.id, isSeries) }
                        )
                    }
                }
            }
        }
    }
}