package com.stalkerapp.ui.vod

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Profile
import com.stalkerapp.data.VodItem
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.VodCatalogStatus
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.GlassChip
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.ui.components.VodQuickActionsSheet
import com.stalkerapp.ui.components.resolveUrl
import com.stalkerapp.ui.rememberMainViewModel

@Composable
fun VodScreen(
    profile: Profile?,
    onOpenVod: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    filterIsSeries: Boolean? = null
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    if (profile == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Henüz kaynak eklenmedi.\nVOD kataloğu için Ayarlar → Playlist & Kaynaklar bölümünden bir Stalker portal ekleyebilirsin.",
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

    LaunchedEffect(profile) {
        vm.syncVodIfNeeded(profile)
    }

    // Kategori/arama değişince paging baştan başlar.
    LaunchedEffect(selectedCategory, query, filterIsSeries) {
        visibleCount = 90
        gridState.scrollToItem(0)
    }

    // +18 ve kullanıcının gizlediği kategoriler listelerden filtrelenir.
    val catTitles = remember(catalog) { catalog.categories.associate { it.id to it.title } }
    val adultRegex = Regex("18|yetkin|adult|xxx|erotik|porno", RegexOption.IGNORE_CASE)
    fun keepItem(item: VodItem): Boolean {
        val title = catTitles[item.categoryId]
        val hidden = settings.hiddenCategories.contains(title.orEmpty())
        val adult = title != null && adultRegex.containsMatchIn(title)
        return !hidden && (settings.adultContentEnabled || !adult)
    }

    val filtered = remember(catalog.allItems, selectedCategory, query, filterIsSeries, settings.adultContentEnabled, settings.hiddenCategories) {
        val q = query.trim()
        catalog.allItems
            .filter { keepItem(it) }
            .let { list ->
                when (filterIsSeries) {
                    true -> list.filter { catalog.isSeriesItem(it) }
                    false -> list.filter { !catalog.isSeriesItem(it) }
                    else -> list
                }
            }
            .let { list -> if (selectedCategory > 0L) list.filter { it.categoryId == selectedCategory } else list }
            .let { list ->
                if (q.isBlank()) list
                else list.filter {
                    it.name.contains(q, ignoreCase = true) ||
                        it.originalName.contains(q, ignoreCase = true)
                }
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
    val seriesCatIds = remember(catalog) {
        catalog.allItems.filter { catalog.isSeriesItem(it) }.map { it.categoryId }.toSet()
    }
    val hiddenTitles = remember(settings.hiddenCategories) { settings.hiddenCategories.toSet() }
    val shownCats = when (filterIsSeries) {
        true -> catList.filter { it.id in seriesCatIds }
        false -> catList.filter { it.id !in seriesCatIds }
        else -> catList
    }.filter { it.title !in hiddenTitles }
        .filter { settings.adultContentEnabled || !Regex("18|yetkin|adult|xxx|erotik|porno", RegexOption.IGNORE_CASE).containsMatchIn(it.title) }

    Column(modifier = modifier.fillMaxSize()) {
        if (catalog.status == VodCatalogStatus.Error) {
            Text(
                "VOD senkron hatası. Yenileme için kategorileri açın.",
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
                                "Film / dizi ara…",
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
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    GlassChip(
                        selected = selectedCategory == 0L && query.isBlank(),
                        onClick = { selectedCategory = 0L },
                        label = "Tümü"
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
        }

        when {
            catalog.status == VodCatalogStatus.Syncing && catalog.allItems.isEmpty() -> LoadingBox()
            catalog.allItems.isEmpty() && catalog.status != VodCatalogStatus.Syncing ->
                EmptyState("VOD içeriği bulunamadı")
            filtered.isEmpty() -> EmptyState("İçerik bulunamadı")
            else -> {
                val pageItems = filtered.take(visibleCount)
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    // Alt boşluk: içerik yüzen cam pill'in arkasından akıyor, son
                    // posterin pill altında kaybolmaması için.
                    contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pageItems, key = { it.id }) { item ->
                        val isSeries = filterIsSeries == true || catalog.isSeriesItem(item)
                        VodPoster(
                            item = item,
                            baseUrl = profile.baseUrl,
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
            item = qi,
            isSeries = filterIsSeries == true || catalog.isSeriesItem(qi),
            vm = vm,
            onOpenDetail = { onOpenVod(qi.id, catalog.isSeriesItem(qi)) },
            onDismiss = { quickActionItem = null }
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
    onLongPress: (() -> Unit)? = null
) {
    // Gri kart arka planı yok: sadece poster + altında başlık ve yıl.
    Column(
        modifier = Modifier
            .then(if (posterWidth != null) Modifier.width(posterWidth.dp) else Modifier.fillMaxWidth())
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
                model = resolveUrl(item.poster, baseUrl),
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
                        "DİZİ",
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
                        contentDescription = "İzlendi",
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
        }
        Column(modifier = Modifier.padding(top = 6.dp)) {
            Text(
                text = item.name,
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
