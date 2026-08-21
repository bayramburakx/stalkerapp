package com.stalkerapp.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
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
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.VodItem
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.VodCatalogStatus
import com.stalkerapp.ui.components.AppleSectionHeader
import com.stalkerapp.ui.components.AppleTvCard
import com.stalkerapp.ui.components.ChannelRow
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.GlassChip
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.ui.components.resolveUrl
import com.stalkerapp.ui.rememberMainViewModel
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
    "Arama" to "Search",
    "Film, dizi, kanal adı yazın…" to "Type a movie, series or channel name…",
    "Tamam" to "OK",
    "Temizle" to "Clear",
    "DİZİ" to "SERIES",
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
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White
                ),
                title = {
                    // Anasayfa dili: cam arama çubuğu (18.dp yuvarlak, ince beyaz çerçeve).
                    val searchShape = RoundedCornerShape(18.dp)
                    val ctx = LocalContext.current
                    val isTv = com.stalkerapp.ui.tv.isTvDevice(ctx)
                    var isInputModalOpen by remember { mutableStateOf(false) }
                    var isFocused by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(searchShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(
                                width = if (isFocused) 2.5.dp else 1.dp,
                                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.18f),
                                shape = searchShape
                            )
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable(isTv)
                            .clickable(isTv) { isInputModalOpen = true }
                            .onKeyEvent { ev ->
                                if (isTv && com.stalkerapp.ui.tv.isTvSelectKey(ev)) {
                                    isInputModalOpen = true; true
                                } else false
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        if (isTv) {
                            Text(
                                text = if (query.isNotBlank()) query else str(lang, "Film, dizi, kanal ara… (OK tuşuna basın)"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (query.isNotBlank()) Color.White else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            androidx.compose.foundation.text.BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    if (query.isBlank()) {
                                        Text(
                                            str(lang, "Film, dizi, kanal ara…"),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                    inner()
                                }
                            )
                        }
                    }

                    if (isTv && isInputModalOpen) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { isInputModalOpen = false },
                            title = { Text(str(lang, "Arama"), color = Color.White, fontWeight = FontWeight.Bold) },
                            text = {
                                var tempText by remember { mutableStateOf(query) }
                                val focusReq = remember { FocusRequester() }
                                Column {
                                    androidx.compose.material3.OutlinedTextField(
                                        value = tempText,
                                        onValueChange = { tempText = it; query = it },
                                        singleLine = true,
                                        placeholder = { Text(str(lang, "Film, dizi, kanal adı yazın…")) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusReq)
                                    )
                                    LaunchedEffect(Unit) {
                                        kotlinx.coroutines.delay(100)
                                        runCatching { focusReq.requestFocus() }
                                    }
                                }
                            },
                            confirmButton = {
                                androidx.compose.material3.TextButton(onClick = { isInputModalOpen = false }) {
                                    Text(str(lang, "Tamam"), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(onClick = {
                                    query = ""
                                    isInputModalOpen = false
                                }) {
                                    Text(str(lang, "Temizle"), color = Color.White.copy(0.6f))
                                }
                            },
                            containerColor = Color(0xFF16161C),
                            shape = RoundedCornerShape(18.dp)
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
        Box(modifier = Modifier.fillMaxSize().background(Color.Black).padding(padding)) {
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
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (liveFiltered.isNotEmpty()) {
                    item {
                        AppleSectionHeader(
                            str(lang, "Kanallar") + " (${liveFiltered.size})",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
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
                    }
                }
                if (loadingVod && vodList == null) {
                    item { LoadingBox() }
                } else if (vodList != null && vodList.isNotEmpty()) {
                    item {
                        AppleSectionHeader(
                            str(lang, "Film & Dizi") + " (${vodList.size})",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                    item {
                        val colCount = 3
                        val rows = (vodList.size + colCount - 1) / colCount
                        val gridHeight: Dp = (rows * 172 + (rows - 1) * 10).dp
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(colCount),
                            modifier = Modifier.fillMaxWidth().height(gridHeight),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(vodList, key = { it.id }) { item ->
                                val isSeries = item.isSeries || item.seriesRef.isNotBlank()
                                PosterCard(
                                    item = item,
                                    baseUrl = profile?.baseUrl.orEmpty(),
                                    isSeries = isSeries,
                                    onClick = { onOpenVod(item.id, isSeries) }
                                )
                            }
                        }
                    }
                } else if (liveFiltered.isEmpty() && vodList != null && vodList.isEmpty() && !loadingVod) {
                    item { EmptyState(vodMessage ?: str(lang, "Sonuç bulunamadı")) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterCard(
    item: VodItem,
    baseUrl: String,
    isSeries: Boolean,
    onClick: () -> Unit
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val settings = app.store.settings()
    var resolvedPoster by remember(item.id, item.poster) {
        mutableStateOf(app.tmdb.getCachedPoster(item.name, isSeries) ?: item.poster)
    }
    LaunchedEffect(item.name, item.poster, item.year, isSeries, settings.tmdbApiKey) {
        if (settings.tmdbApiKey.isNotBlank()) {
            val p = app.tmdb.resolvePoster(item.name, item.year, isSeries, item.poster, settings.tmdbApiKey)
            if (p.isNotBlank()) resolvedPoster = p
        }
    }
    AppleTvCard(onClick = onClick, cornerRadius = 18.dp) { focused ->
        Column {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                AsyncImage(
                    model = resolveUrl(resolvedPoster.ifBlank { item.poster }, baseUrl),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                )
                if (isSeries) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.White, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            str(settings.language, "DİZİ"),
                            color = Color.Black,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, start = 2.dp, end = 2.dp)
            )
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
        // Anasayfa dili: cam çip (GlassChip) + açılır ok menüsü.
        GlassChip(
            selected = selected > 0,
            onClick = { expanded = true },
            label = label
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color(0xFF16161C)
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
    // Tür filtreleri kaynağa göre dinamiktir: katalogda o türden içerik yoksa
    // buton gösterilmez (M3U/Xtream/Stalker kaynağına göre değişir).
    val hasMovies = remember(catalog) { catalog.allItems.any { !catalog.isSeriesItem(it) } }
    val hasSeries = remember(catalog) { catalog.allItems.any { catalog.isSeriesItem(it) } }
    val hasDocs = remember(catalog) {
        catalog.categories.any { c ->
            c.title.contains("belgesel", ignoreCase = true) || c.title.contains("documentary", ignoreCase = true)
        }
    }
    val typeOptions = remember(catalog, hasMovies, hasSeries, hasDocs) {
        val opts = mutableListOf<Pair<Int, String>>()
        opts += 0 to str(lang, "Tümü")
        if (hasMovies) opts += 1 to str(lang, "Film")
        if (hasSeries) opts += 2 to str(lang, "Dizi")
        if (hasDocs) opts += 3 to str(lang, "Belgesel")
        opts
    }
    // Kategori listesi kaynağın kategorileridir (Stalker VOD kategorileri,
    // Xtream kategorileri ya da M3U group-title'ları) — sabit değildir.
    val genres = catalog.categories.filter { it.id != 0L }.take(60)
    val types = typeOptions.map { it.second }
    val typeToIndex = typeOptions.associate { it.first to typeOptions.indexOf(it) }
    // typeFilter int değeri 0/1/2/3; dinamik listede endekse çevrilir.
    val selectedTypeIndex = typeToIndex[typeFilter] ?: 0

    // Katalog değişince artık var olmayan bir tür/kategori seçiliyse sıfırla
    // (kaynak değişince filtreler yeni kaynağın içeriğine göre güncellensin).
    LaunchedEffect(catalog, typeOptions) {
        if (typeFilter != 0 && typeOptions.none { it.first == typeFilter }) typeFilter = 0
        if (genreFilter != null && catalog.categories.none { it.id == genreFilter }) genreFilter = null
    }

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
        AppleSectionHeader(
            str(lang, "Keşfet"),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DropdownFilterChip(
                label = "${str(lang, "Tür")}: ${types.getOrElse(selectedTypeIndex) { types.firstOrNull() ?: "" }}",
                options = types,
                selected = selectedTypeIndex,
                onSelect = { i -> typeFilter = typeOptions.getOrNull(i)?.first ?: 0 }
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
        Box(modifier = Modifier.fillMaxSize().weight(1f).padding(top = 8.dp)) {
            when {
                catalog.status == VodCatalogStatus.Syncing && catalog.allItems.isEmpty() -> LoadingBox()
                discover.isEmpty() -> EmptyState(str(lang, "İçerik bulunamadı"))
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(discover, key = { it.id }) { item ->
                        val isSeries = item.isSeries || item.seriesRef.isNotBlank()
                        PosterCard(
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
