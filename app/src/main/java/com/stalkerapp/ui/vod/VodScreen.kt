package com.stalkerapp.ui.vod

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.scale
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.stalkerapp.data.matches
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import com.stalkerapp.data.ExternalVod
import com.stalkerapp.data.Genre
import com.stalkerapp.ui.tv.isTvSelectKey
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Profile
import com.stalkerapp.data.VodItem
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.VodCatalogState
import com.stalkerapp.ui.VodCatalogStatus
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.GlassChip
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.ui.components.VodQuickActionsSheet
import com.stalkerapp.ui.components.resolveUrl
import com.stalkerapp.ui.rememberMainViewModel

private val L10nLocal: Map<String, String> = mapOf(
    // Türkçe -> English
    "Henüz kaynak eklenmedi.\nVOD kataloğu için Ayarlar → Playlist & Kaynaklar bölümünden bir Stalker portal, M3U listesi veya Xtream Codes ekleyebilirsin." to "No source added yet.\nTo browse the VOD catalog, add a Stalker portal, M3U list or Xtream Codes from Settings → Playlists & Sources.",
    "VOD senkron hatası. Yenileme için kategorileri açın." to "VOD sync error. Open the categories to refresh.",
    "Film / dizi ara…" to "Search movies / series…",
    "Tümü" to "All",
    "VOD içeriği bulunamadı" to "No VOD content found",
    "İçerik bulunamadı" to "No content found",
    "DİZİ" to "SERIES",
    "İzlendi" to "Watched"
)
private fun str(lang: String, text: String): String =
    if (lang == "en") L10nLocal[text] ?: text else text

@Composable
fun VodScreen(
    profile: Profile?,
    onOpenVod: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    filterIsSeries: Boolean? = null
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val lang = vm.store.settings().language
    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val adultUnlocked by vm.adultUnlocked.collectAsStateWithLifecycle()
    // M3U/Xtream kaynaklarında Stalker profili olmayabilir; bu durumda katalog
    // aktif kaynaktan (dış katalog) gelir ve profil gerekmez.
    val externalSource = vm.enabledSourceKind() == "m3u" || vm.enabledSourceKind() == "xtream"

    if (profile == null && !externalSource) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                str(lang, "Henüz kaynak eklenmedi.\nVOD kataloğu için Ayarlar → Playlist & Kaynaklar bölümünden bir Stalker portal, M3U listesi veya Xtream Codes ekleyebilirsin."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    var selectedCategory by remember { mutableStateOf(0L) }
    var query by remember { mutableStateOf("") }
    // Uzun bas → hızlı işlemler sheet'i.
    var quickActionItem by remember { mutableStateOf<VodItem?>(null) }
    // Lazy yükleme: tüm katalog yerine başlangıçta sınırlı sayıda poster gösterilir;
    // aşağı scroll ettikçe kalanı yüklenir (donma/kasma için — Samsung S25FE).
    var visibleCount by remember { mutableStateOf(90) }
    val pageStep = 120
    val gridState = rememberLazyGridState()
    // İzlenme işaretleri anlık güncellenir: watchedVersion her değiştiğinde
    // Store'dan taze okunur (sheet'te izlendi işaretlenince rozet anında çıkar).
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

    // Kategori/arama değişince paging baştan başlar.
    LaunchedEffect(selectedCategory, query, filterIsSeries) {
        visibleCount = 90
        gridState.scrollToItem(0)
    }

    // +18 ve kullanıcının gizlediği kategoriler listelerden filtrelenir.
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
    var filterState by remember { mutableStateOf(com.stalkerapp.data.VodFilterState()) }

    val filtered by androidx.compose.runtime.produceState(
        initialValue = emptyList<VodItem>(),
        catalog.movies.size,
        catalog.series.size,
        catalog.allItems.size,
        catalog.status,
        selectedCategory,
        query,
        filterIsSeries,
        filterState,
        blockedCategoryIds
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val q = query.trim()
            var list = when (filterIsSeries) {
                true -> if (catalog.series.isNotEmpty()) catalog.series else catalog.allItems.filter { catalog.isSeriesItem(it) }
                false -> if (catalog.movies.isNotEmpty()) catalog.movies else catalog.allItems.filter { !catalog.isSeriesItem(it) }
                else -> catalog.allItems
            }
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

    // Aşağı scroll ettikçe yükle: son satıra yaklaşınca bir sonraki sayfayı ekle.
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 6
        }
    }
    // Sadece shouldLoadMore değişince tetiklenir — sayfa eklendikten sonra son
    // satır uzağa gider ve kullanıcı tekrar scroll edince yeni sayfa yüklenir.
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && visibleCount < filtered.size) {
            visibleCount += pageStep
        }
    }

    val catList = catalog.categories
    val seriesCatIds = catalog.seriesCategoryIds
    val shownCats = remember(catList, seriesCatIds, filterIsSeries, blockedCategoryIds, catalog.movies.size, catalog.series.size) {
        val raw = when (filterIsSeries) {
            true -> catList.filter {
                it.id in seriesCatIds || ExternalVod.isSeriesCat(it.id) ||
                (it.id in com.stalkerapp.data.PortalRepository.SERIES_CAT_BASE until ExternalVod.XTREAM_VOD_BASE) ||
                VodCatalogState.isSeriesCatTitle(it.title)
            }
            false -> catList.filter {
                it.id !in seriesCatIds && !ExternalVod.isSeriesCat(it.id) &&
                it.id < com.stalkerapp.data.PortalRepository.SERIES_CAT_BASE &&
                !VodCatalogState.isSeriesCatTitle(it.title)
            }
            else -> catList
        }.filter { it.id !in blockedCategoryIds }

        if (raw.isNotEmpty()) raw
        else if (catList.isNotEmpty()) {
            catList.filter { it.id !in blockedCategoryIds }
        } else {
            // Yedek: içeriklerden dinamik kategori üret
            val items = when (filterIsSeries) {
                true -> if (catalog.series.isNotEmpty()) catalog.series else catalog.allItems.filter { catalog.isSeriesItem(it) }
                false -> if (catalog.movies.isNotEmpty()) catalog.movies else catalog.allItems.filter { !catalog.isSeriesItem(it) }
                else -> catalog.allItems
            }
            items.mapNotNull { it.genres.takeIf { g -> g.isNotBlank() } ?: it.country.takeIf { c -> c.isNotBlank() } }
                .distinct()
                .take(30)
                .mapIndexed { idx, name -> Genre(id = -(idx + 100L), title = name) }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (catalog.status == VodCatalogStatus.Error) {
            Text(
                str(lang, "VOD senkron hatası. Yenileme için kategorileri açın."),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        // Anasayfa dili: cam arama çubuğu (yarı saydam, yuvarlak, ince çerçeve).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val searchShape = RoundedCornerShape(50)
            Row(
                modifier = Modifier
                    .weight(1f)
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
                                str(lang, "Film / dizi ara…"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                )
            }
        }

        if (shownCats.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp)
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        GlassChip(
                            selected = selectedCategory == 0L && query.isBlank(),
                            onClick = { selectedCategory = 0L },
                            label = str(lang, "Tümü")
                        )
                    }
                    items(shownCats) { c ->
                        GlassChip(
                            selected = selectedCategory == c.id,
                            onClick = { selectedCategory = c.id },
                            label = str(lang, c.title)
                        )
                    }
                }
                // Filtreleme / Sıralama Butonu
                IconButton(onClick = { showFilterDialog = true }) {
                    Icon(Icons.Default.FilterList, contentDescription = str(lang, "Filtreler"))
                }
            }
        }

        when {
            catalog.status == VodCatalogStatus.Syncing && catalog.allItems.isEmpty() -> LoadingBox()
            catalog.allItems.isEmpty() && catalog.status != VodCatalogStatus.Syncing ->
                EmptyState(str(lang, "VOD içeriği bulunamadı"))
            filtered.isEmpty() -> EmptyState(str(lang, "İçerik bulunamadı"))
            else -> {
                val pageItems = filtered.take(visibleCount)
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 135.dp),
                    contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pageItems, key = { it.id }) { item ->
                        val isSeries = filterIsSeries == true || catalog.isSeriesItem(item)
                        VodPoster(
                            item = item,
                            baseUrl = profile?.baseUrl.orEmpty(),
                            isSeries = isSeries,
                            watched = isWatched(item),
                            onLongPress = { quickActionItem = item },
                            onClick = { onOpenVod(item.id, isSeries) }
                        )
                    }
                    // Liste sonuna gelindiğinde bir sonraki sayfa yükleniyor göstergesi.
                    if (visibleCount < filtered.size) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
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
            isSeries = filterIsSeries == true || catalog.isSeriesItem(qi),
            vm = vm,
            onOpenDetail = { onOpenVod(qi.id, catalog.isSeriesItem(qi)) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VodPoster(
    item: VodItem,
    baseUrl: String,
    onClick: () -> Unit,
    isSeries: Boolean = item.isSeries,
    posterWidth: Int? = null,
    watched: Boolean = false,
    label: String? = null,
    onLongPress: (() -> Unit)? = null
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val settings = app.store.settings()
    val lang = settings.language
    var isFocused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.10f else 1.0f,
        label = "poster_scale"
    )

    var resolvedPoster by remember(item.id, item.poster) {
        mutableStateOf(app.tmdb.getCachedPoster(item.name, isSeries) ?: item.poster)
    }

    LaunchedEffect(item.name, item.poster, item.year, isSeries, settings.tmdbApiKey) {
        if (settings.tmdbApiKey.isNotBlank()) {
            val p = app.tmdb.resolvePoster(item.name, item.year, isSeries, item.poster, settings.tmdbApiKey)
            if (p.isNotBlank()) {
                resolvedPoster = p
            }
        }
    }

    Column(
        modifier = Modifier
            .then(if (posterWidth != null) Modifier.width(posterWidth.dp) else Modifier.fillMaxWidth())
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .border(
                width = if (isFocused) 3.5.dp else 0.dp,
                color = if (isFocused) Color(0xFF00E5FF) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .onKeyEvent { ev ->
                if (isTvSelectKey(ev)) {
                    onClick(); true
                } else false
            }
            .let { mod ->
                if (onLongPress != null) {
                    mod.combinedClickable(onClick = onClick, onLongClick = onLongPress)
                } else {
                    mod.clickable(onClick = onClick)
                }
            }
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            AsyncImage(
                model = resolveUrl(resolvedPoster.ifBlank { item.poster }, baseUrl),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
            )
            if (isSeries) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color(0xFFE50914), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        str(lang, "DİZİ"),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (watched) {
                // İzlenme işareti: yeşil onay rozeti (sol üst).
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF2E7D32)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = str(lang, "İzlendi"),
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            // IMDb puanı rozeti (sol alt): portal rating'i boş/0 değilse gösterilir.
            val ratingText = item.rating.trim().trimEnd('/').let { r ->
                if (r.isBlank() || r == "0" || r == "0.0") null else r
            }
            if (ratingText != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        "★ $ratingText",
                        color = Color(0xFFFFC107),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
            if (label != null) {
                // Bölüm etiketi ("S1E3" vb.): sağ alt köşede gösterilir.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(top = 6.dp)) {
            Text(
                text = if (label != null) "${item.name} • $label" else item.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Tarih olarak sadece yıl gösterilir (portal tam tarih dönebilir).
            val year = item.year.take(4).takeIf { it.isNotBlank() && it.all(Char::isDigit) }.orEmpty()
            if (year.isNotBlank()) {
                Text(
                    text = year,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun VodFilterDialog(
    lang: String,
    state: com.stalkerapp.data.VodFilterState,
    onDismiss: () -> Unit,
    onApply: (com.stalkerapp.data.VodFilterState) -> Unit
) {
    var sortMode by remember { mutableStateOf(state.sortMode) }
    var minRating by remember { mutableStateOf(state.minRating) }
    var yearFrom by remember { mutableStateOf(state.yearRange?.first ?: 1980) }
    var yearTo by remember { mutableStateOf(state.yearRange?.last ?: currentYear()) }
    var yearFilterOn by remember { mutableStateOf(state.yearRange != null) }
    var langFilter by remember { mutableStateOf(state.language) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onApply(
                    state.copy(
                        sortMode = sortMode,
                        minRating = minRating,
                        yearRange = if (yearFilterOn) yearFrom..yearTo else null,
                        language = langFilter
                    )
                )
            }) { Text(str(lang, "Uygula"), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onApply(com.stalkerapp.data.VodFilterState())
                }) { Text(str(lang, "Sıfırla"), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(str(lang, "İptal")) }
            }
        },
        title = { Text(str(lang, "Filtrele & Sırala")) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Sıralama
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(str(lang, "Sıralama"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val options = listOf(
                        com.stalkerapp.data.SortMode.DEFAULT to str(lang, "Varsayılan"),
                        com.stalkerapp.data.SortMode.NEWEST to str(lang, "En Yeni"),
                        com.stalkerapp.data.SortMode.HIGHEST_RATED to str(lang, "En Yüksek Puanlı"),
                        com.stalkerapp.data.SortMode.A_Z to "A-Z",
                        com.stalkerapp.data.SortMode.Z_A to "Z-A"
                    )
                    options.forEach { (mode, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { sortMode = mode }
                                .padding(vertical = 4.dp)
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = sortMode == mode,
                                onClick = { sortMode = mode }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                HorizontalDivider()
                // Puan Filtresi
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(str(lang, "Minimum IMDb Puanı: ${minRating.toInt()}"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Slider(
                        value = minRating,
                        onValueChange = { minRating = it },
                        valueRange = 0f..9f,
                        steps = 8
                    )
                }
                HorizontalDivider()
                // Yıl Aralığı Filtresi
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(str(lang, "Yıl Aralığı"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        androidx.compose.material3.FilterChip(
                            selected = yearFilterOn,
                            onClick = { yearFilterOn = !yearFilterOn },
                            label = { Text(str(lang, if (yearFilterOn) "Açık" else "Kapalı")) }
                        )
                    }
                    if (yearFilterOn) {
                        Text("$yearFrom – $yearTo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = yearFrom.toFloat(),
                            onValueChange = { yearFrom = it.toInt().coerceAtMost(yearTo) },
                            valueRange = 1980f..currentYear().toFloat(),
                            steps = 0
                        )
                        Slider(
                            value = yearTo.toFloat(),
                            onValueChange = { yearTo = it.toInt().coerceAtLeast(yearFrom) },
                            valueRange = 1980f..currentYear().toFloat(),
                            steps = 0
                        )
                    }
                }
                HorizontalDivider()
                // Dil Filtresi
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(str(lang, "Dil"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassChip(
                            selected = langFilter.isEmpty(),
                            onClick = { langFilter = "" },
                            label = str(lang, "Tümü")
                        )
                        GlassChip(
                            selected = langFilter == "tr",
                            onClick = { langFilter = "tr" },
                            label = "Türkçe"
                        )
                        GlassChip(
                            selected = langFilter == "en",
                            onClick = { langFilter = "en" },
                            label = "English"
                        )
                    }
                }
            }
        }
    )
}

private fun currentYear(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
