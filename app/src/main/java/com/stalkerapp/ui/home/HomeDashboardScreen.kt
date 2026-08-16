package com.stalkerapp.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.stalkerapp.ui.components.VodQuickActionsSheet
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
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val favChannels by vm.favoriteChannels.collectAsStateWithLifecycle()
    val favVods by vm.favoriteVods.collectAsStateWithLifecycle()

    // Henüz hiçbir kaynak eklenmemişse ana sayfa yerine yönlendirme gösterilir.
    val sourceKind = vm.enabledSourceKind()
    if (profile == null && sourceKind == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(
                    "Henüz bir kaynak eklemedin",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ayarlar → Playlist & Kaynaklar bölümünden Stalker portal, M3U listesi " +
                        "veya Xtream Codes ekleyerek izlemeye başlayabilirsin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    // Kanallar ViewModel'de önbelleklenir: sekmeler arası geçişte ağ isteği
    // tekrarlanmaz, bu da menü geçişlerindeki takılmayı azaltır.
    val homeChannels by vm.homeChannels.collectAsStateWithLifecycle()
    var loadingChannels by remember { mutableStateOf(homeChannels == null) }

    LaunchedEffect(Unit) {
        profile?.let {
            vm.syncVodIfNeeded(it)
            if (homeChannels == null) {
                loadingChannels = true
                vm.loadHomeChannels(it)
            }
        }
        loadingChannels = false
    }

    // Ana sayfadan kaldırılanlar (uzun bas → "Ana Sayfadan Kaldır").
    val hiddenFromHome = settings.hiddenFromHome.toSet()
    // İzleme ilerlemeleri oynatma sırasında değişir; watchedVersion her
    // değişimde bu listeler Store'dan taze okunur (yoksa bayat kalırdı).
    val watchedVersion by vm.watchedVersion.collectAsStateWithLifecycle()
    val continueWatching = remember(catalog, watchedVersion, hiddenFromHome) {
        val progress = app.store.loadVodProgress()
        progress.mapNotNull { (id, p) ->
            if (id !in hiddenFromHome && p.durationMs > 0 && p.positionMs > 0 && p.positionMs < p.durationMs * 0.85) {
                catalog.byId[id]?.let { it to p }
            } else null
        }
    }

    // Son İzlenenler: hem film ilerlemeleri hem de dizi bölüm ilerlemeleri,
    // son izlenme zamanına göre sıralanır (tamamlanmışlar da dahil).
    val recentlyWatched = remember(catalog, watchedVersion, hiddenFromHome) {
        val vod = app.store.loadVodProgress().mapNotNull { (id, p) ->
            if (id in hiddenFromHome) null else catalog.byId[id]?.let { Triple(it, p.lastUpdated, p.positionMs) }
        }
        val eps = app.store.episodeProgress().mapNotNull { (key, p) ->
            val id = key.substringBefore(':').toLongOrNull()
            if (id == null || id in hiddenFromHome) null
            else catalog.byId[id]?.let { Triple(it, p.lastUpdated, p.positionMs) }
        }
        // byId garantili: katalogda olmayan öğe map'te bulunmaz.
        (vod + eps)
            .sortedByDescending { it.second }
            .distinctBy { it.first.id }
            .take(20)
            .map { it.first to it.third }
    }

    // Uzun bas → hızlı işlemler sheet'i + izlenme işaretleri.
    var quickActionItem by remember { mutableStateOf<VodItem?>(null) }
    // İzlenme işaretleri anlık: watchedVersion değişince Store'dan taze okunur.
    val watchedOverrides = remember(watchedVersion) { app.store.watchedOverrides() }
    val vodProgressMap = remember(watchedVersion) { app.store.loadVodProgress() }
    fun isWatched(item: VodItem): Boolean {
        val p = vodProgressMap[item.id]
        return item.id in watchedOverrides ||
            (p != null && p.durationMs > 0 && p.positionMs >= p.durationMs * 0.85)
    }

    // Kullanıcının gizlediği kategoriler + +18 filtresi (Kütüphane & İçerik ayarları).
    val catTitles = remember(catalog) { catalog.categories.associate { it.id to it.title } }
    val adultRegex = Regex("18|yetkin|adult|xxx|erotik|porno", RegexOption.IGNORE_CASE)
    fun keepItem(item: VodItem): Boolean {
        val title = catTitles[item.categoryId]
        val hidden = settings.hiddenCategories.contains(title.orEmpty())
        val adult = title != null && adultRegex.containsMatchIn(title)
        return !hidden && (settings.adultContentEnabled || !adult)
    }

    val movies = remember(catalog, settings.adultContentEnabled, settings.hiddenCategories) {
        catalog.allItems.filter { !catalog.isSeriesItem(it) && keepItem(it) }.take(20)
    }
    val series = remember(catalog, settings.adultContentEnabled, settings.hiddenCategories) {
        catalog.allItems.filter { catalog.isSeriesItem(it) && keepItem(it) }.take(20)
    }
    val featured = remember(catalog, settings.adultContentEnabled, settings.hiddenCategories) {
        (series.take(6) + movies.take(6)).shuffled()
    }

    // Ana sayfa düzeni: poster genişliği (Ayarlar → Kütüphane & İçerik → Ana Sayfa Düzeni).
    val posterWidth = when (settings.homeLayout) {
        "compact" -> 104
        "list" -> 168
        else -> 130
    }

    // Bölüm sırası (Ayarlar'dan değiştirilebilir).
    val sectionOrder = remember(settings.homeSectionOrder) {
        val known = listOf("recent", "continue", "movies", "series", "favchannels", "live", "favvods")
        val custom = settings.homeSectionOrder.filter { it in known }
        custom + known.filter { it !in custom }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        if (featured.isNotEmpty()) {
            HeroBanner(
                items = featured,
                baseUrl = profile?.baseUrl.orEmpty(),
                catTitle = { id -> catTitles[id].orEmpty() },
                onOpenVod = onOpenVod,
                scrollState = scrollState
            )
            // Hero'nun altına nefes payı: içerik hero'ya çok yapışık durmasın.
            Spacer(modifier = Modifier.height(16.dp))
        }

        sectionOrder.forEach { key ->
            when (key) {
                "recent" -> if (recentlyWatched.isNotEmpty()) {
                    Section(title = "Son İzlenenler", onSeeAll = null) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(recentlyWatched, key = { it.first.id }) { (item, _) ->
                                VodPoster(
                                    item = item,
                                    baseUrl = profile?.baseUrl.orEmpty(),
                                    isSeries = catalog.isSeriesItem(item),
                                    posterWidth = posterWidth,
                                    watched = isWatched(item),
                                    onLongPress = { quickActionItem = item },
                                    onClick = { onOpenVod(item.id, catalog.isSeriesItem(item)) }
                                )
                            }
                        }
                    }
                }
                "continue" -> if (continueWatching.isNotEmpty()) {
                    Section(title = "İzlemeye Devam", onSeeAll = null) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(continueWatching, key = { it.first.id }) { (item, prog) ->
                                ContinueWatchingCard(
                                    item = item,
                                    baseUrl = profile?.baseUrl.orEmpty(),
                                    positionMs = prog.positionMs,
                                    durationMs = prog.durationMs,
                                    onClick = { onOpenVod(item.id, catalog.isSeriesItem(item)) },
                                    onLongPress = { quickActionItem = item }
                                )
                            }
                        }
                    }
                }
                "movies" -> Section(title = "Popüler Filmler", onSeeAll = { onGotoTab(2) }) {
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
                                    baseUrl = profile?.baseUrl.orEmpty(),
                                    isSeries = false,
                                    posterWidth = posterWidth,
                                    watched = isWatched(item),
                                    onLongPress = { quickActionItem = item },
                                    onClick = { onOpenVod(item.id, false) }
                                )
                            }
                        }
                    }
                }
                "series" -> Section(title = "Popüler Diziler", onSeeAll = { onGotoTab(3) }) {
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
                                    baseUrl = profile?.baseUrl.orEmpty(),
                                    isSeries = true,
                                    posterWidth = posterWidth,
                                    watched = isWatched(item),
                                    onLongPress = { quickActionItem = item },
                                    onClick = { onOpenVod(item.id, true) }
                                )
                            }
                        }
                    }
                }
                "favchannels" -> Section(title = "Favori Kanallar", onSeeAll = { onGotoTab(1) }) {
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
                                    baseUrl = profile?.baseUrl.orEmpty(),
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
                "live" -> Section(title = "Canlı TV", onSeeAll = { onGotoTab(1) }) {
                    when {
                        loadingChannels -> LoadingBox()
                        homeChannels.isNullOrEmpty() -> EmptyState("Kanal bulunamadı")
                        else -> LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(homeChannels.orEmpty(), key = { it.id }) { ch ->
                                ChannelCard(
                                    channel = ch,
                                    baseUrl = profile?.baseUrl.orEmpty(),
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
                "favvods" -> if (favVods.isNotEmpty()) {
                    Section(title = "Favori Filmler & Diziler", onSeeAll = { onGotoTab(4) }) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(favVods.take(20), key = { it.id }) { item ->
                                VodPoster(
                                    item = item,
                                    baseUrl = profile?.baseUrl.orEmpty(),
                                    isSeries = catalog.isSeriesItem(item),
                                    posterWidth = posterWidth,
                                    watched = isWatched(item),
                                    onLongPress = { quickActionItem = item },
                                    onClick = { onOpenVod(item.id, catalog.isSeriesItem(item)) }
                                )
                            }
                        }
                    }
                }
            }
        }
        // İçerik yüzen cam pill'in arkasından akıyor; son öğenin pill'in
        // altında kaybolmaması için altta boşluk bırak.
        Spacer(modifier = Modifier.height(96.dp))
    }

    if (quickActionItem != null) {
        val qi = quickActionItem!!
        VodQuickActionsSheet(
            item = qi,
            isSeries = catalog.isSeriesItem(qi),
            vm = vm,
            onOpenDetail = { onOpenVod(qi.id, catalog.isSeriesItem(qi)) },
            onDismiss = { quickActionItem = null }
        )
    }
}

@Composable
private fun HeroBanner(
    items: List<VodItem>,
    baseUrl: String,
    catTitle: (Long) -> String,
    onOpenVod: (Long, Boolean) -> Unit,
    scrollState: ScrollState
) {
    // Hero: ekran yüksekliğinin yarısı kadar.
    val heroHeight = with(LocalConfiguration.current) { screenHeightDp.dp / 2f }
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
        // Yıl: portal "2026-08-14" gibi tam tarih döndürebilir, sadece yılı göster.
        val yearText = item.year.take(4).takeIf { it.isNotBlank() && it.all(Char::isDigit) }.orEmpty()
        // Tür: listedeki `genres_str` (ör. "Komedi"); yoksa kategori başlığına düş.
        val genre = item.genres.trim().ifBlank { catTitle(item.categoryId) }

        // clipToBounds: zoom sırasında büyüyen poster hero kutusunun dışına
        // taşmasın — gradient sınırının ötesine taşmaz.
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            // Aşağı kaydırdıkça görsel hafifçe yakınlaşır (dikey parallax/zoom).
            // Yatay kaydırmada (pager) bu efekt uygulanmaz — zoom yalnızca sayfa
            // aşağı kaydırılırken görünür. graphicsLayer bloğu state okur, bu
            // yüzden her karede tüm ekran yeniden çizilmez.
            AsyncImage(
                model = resolveUrl(item.poster, baseUrl),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val zoom = 1f + (scrollState.value / 450f).coerceAtMost(0.40f)
                        scaleX = zoom
                        scaleY = zoom
                    }
            )
            // Üstten şeffaf, alta doğru koyulaşan yumuşak geçiş: slider'ın alt
            // kenarı keskin bitmez, içerik sayfa arka planına karışır.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.30f to Color.Black.copy(alpha = 0.20f),
                                0.60f to Color.Black.copy(alpha = 0.55f),
                                1.0f to Color.Black.copy(alpha = 0.92f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1) Başlık (en üstte)
                Text(
                    item.name,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(color = Color.Black, blurRadius = 12f)
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
                // 2) DİZİ/FİLM • tür • yıl (nokta ayraçlı, ortada)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (isSeries) "DİZİ" else "FİLM",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (genre.isNotBlank()) {
                        Text("•", color = Color.White.copy(alpha = 0.8f))
                        Text(
                            genre,
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp)
                        )
                    }
                    if (yearText.isNotBlank()) {
                        Text("•", color = Color.White.copy(alpha = 0.8f))
                        Text(yearText, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                // 3) Detayları Gör butonu (beyaz zemin, siyah kalın yazı, ortada)
                Button(
                    onClick = { onOpenVod(item.id, isSeries) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.height(46.dp)
                ) {
                    Text("Detayları Gör", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
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
            // Başlıklar büyük + kalın; mavi aksan çubuğu yok.
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (onSeeAll != null) {
                // "Tümü": alt menüdeki cam pill ile aynı görünüm, sadece ok simgesi.
                val pillShape = RoundedCornerShape(50)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(pillShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.70f))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), pillShape)
                        .clickable { onSeeAll() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Tümü",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        // Başlık ile kartlar arasında nefes payı: kartlar başlığa çok yapışmasın.
        Spacer(modifier = Modifier.height(8.dp))
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
@OptIn(ExperimentalFoundationApi::class)
private fun ContinueWatchingCard(
    item: VodItem,
    baseUrl: String,
    positionMs: Long,
    durationMs: Long,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .then(
                if (onLongPress != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress)
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
        ) {
            AsyncImage(
                model = resolveUrl(item.poster, baseUrl),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            LinearProgressIndicator(
                progress = { if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                color = Color(0xFFE50914)
            )
        }
        Text(
            item.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
