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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import com.stalkerapp.ui.components.AppleSectionHeader
import com.stalkerapp.ui.components.AppleTvButton
import com.stalkerapp.ui.components.AppleTvButtonStyle
import com.stalkerapp.ui.components.AppleTvCard
import com.stalkerapp.ui.components.AppleTvTokens
import com.stalkerapp.ui.components.ChannelLogo
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.GlassSurface
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.ui.components.VodQuickActionsSheet
import com.stalkerapp.ui.components.resolveUrl
import com.stalkerapp.ui.rememberMainViewModel
import kotlinx.coroutines.delay

private val L10nLocal: Map<String, String> = mapOf(
    // Türkçe -> English
    "Henüz bir kaynak eklemedin" to "You haven't added a source yet",
    "Ayarlar → Playlist & Kaynaklar bölümünden Stalker portal, M3U listesi " to "From Settings → Playlist & Sources, add a Stalker portal, M3U list ",
    "veya Xtream Codes ekleyerek izlemeye başlayabilirsin." to "or Xtream Codes to start watching.",
    "Son İzlenenler" to "Recently Watched",
    "İzlemeye Devam" to "Continue Watching",
    "Popüler Filmler" to "Popular Movies",
    "Popüler Diziler" to "Popular Series",
    "Son İzlenen Kanallar" to "Recently Watched Channels",
    "Favori Kanallar" to "Favorite Channels",
    "Canlı TV" to "Live TV",
    "Favori Filmler & Diziler" to "Favorite Movies & Series",
    "Film bulunamadı" to "No movies found",
    "Dizi bulunamadı" to "No series found",
    "Kanal bulunamadı" to "No channels found",
    "Henüz favori kanal yok" to "No favorite channels yet",
    "DİZİ" to "SERIES",
    "FİLM" to "MOVIE",
    "Oynat" to "Play",
    "Detayları Gör" to "View Details",
    "Tümü" to "All",
    "Kaynaklar yükleniyor" to "Loading sources",
    "içerik yüklendi" to "items loaded",
    "kategori" to "categories"
)
private fun str(lang: String, text: String): String =
    if (lang == "en") L10nLocal[text] ?: text else text

// Ana sayfa "İzlemeye Devam / Son İzlenenler" kartı için ortak veri yapısı.
// Bölüm ilerlemeleri için episodeLabel ("S1E3") de taşınır.
private data class HomeEntry(
    val item: VodItem,
    val positionMs: Long,
    val durationMs: Long,
    val lastUpdated: Long,
    val episodeLabel: String? = null
)

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
    val lang = vm.store.settings().language
    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val adultUnlocked by vm.adultUnlocked.collectAsStateWithLifecycle()
    val favChannels by vm.favoriteChannels.collectAsStateWithLifecycle()
    val favVods by vm.favoriteVods.collectAsStateWithLifecycle()

    // Henüz hiçbir kaynak eklenmemişse ana sayfa yerine yönlendirme gösterilir.
    val sourceKind = vm.enabledSourceKind()
    if (profile == null && sourceKind == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(
                    str(lang, "Henüz bir kaynak eklemedin"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    str(lang, "Ayarlar → Playlist & Kaynaklar bölümünden Stalker portal, M3U listesi ") +
                        str(lang, "veya Xtream Codes ekleyerek izlemeye başlayabilirsin."),
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

    val sourcesVersion by vm.sourcesVersion.collectAsStateWithLifecycle()
    val activeKind = vm.enabledSourceKind()
    val activeSourceId = vm.activeSourceId()

    LaunchedEffect(profile, activeKind, activeSourceId, sourcesVersion) {
        if (activeKind == "m3u" || activeKind == "xtream") {
            // M3U/Xtream: VOD kataloğu aktif kaynaktan kurulur, kanallar da
            // aktif kaynaktan gelir (Stalker profili gerekmez).
            vm.ensureExternalVodCatalog()
        } else {
            profile?.let { vm.syncVodIfNeeded(it) }
        }
        if (homeChannels == null) {
            loadingChannels = true
            vm.loadHomeChannels(profile)
            loadingChannels = false
        }
    }

    // Ana sayfadan kaldırılanlar (uzun bas → "Ana Sayfadan Kaldır").
    val hiddenFromHome = settings.hiddenFromHome.toSet()
    // İzleme ilerlemeleri oynatma sırasında değişir; watchedVersion her
    // değişimde bu listeler Store'dan taze okunur (yoksa bayat kalırdı).
    val watchedVersion by vm.watchedVersion.collectAsStateWithLifecycle()
    // Katalog byId'sinde yoksa ilerleme kaydındaki öğe anlık görüntüsüne düş
    // (katalog senkronu henüz tamamlanmamış olsa bile listeler dolu görünür).
    fun itemFor(id: Long, p: com.stalkerapp.data.VodProgress): com.stalkerapp.data.VodItem? =
        catalog.byId[id] ?: p.toVodItem(id)

    // Yalnızca AKTİF kaynağın ilerlemeleri gösterilir: kaynak silinince/değişince
    // eski kaynağın "İzlemeye Devam / Son İzlenenler" içeriği görünmez.
    val curSourceKey = app.store.activeSourceKey()
    // Dizi bölümü kartları için etiket ("S1E3" veya bölüm adı).
    fun episodeLabelOf(key: String, p: com.stalkerapp.data.VodProgress): String {
        if (p.episodeLabel.isNotBlank()) return p.episodeLabel
        val parts = key.split(':')
        return buildString {
            if (parts.size > 1) append("S").append(parts[1])
            if (parts.size > 2) append("E").append(parts[2])
        }
    }

    // İzlemeye Devam: filmler + dizi bölümleri, EN YENİ İZLENEN BAŞTA (Arka plan iş parçacığı).
    val continueWatching by produceState(
        initialValue = emptyList<HomeEntry>(),
        catalog.byId.size,
        catalog.allItems.size,
        watchedVersion,
        hiddenFromHome,
        curSourceKey
    ) {
        value = withContext(Dispatchers.Default) {
            val vodProg = app.store.loadVodProgress()
            val epProg = app.store.episodeProgress()
            val movies = vodProg.mapNotNull { (id, p) ->
                if (id in hiddenFromHome || (p.sourceKey.isNotBlank() && p.sourceKey != curSourceKey)) null
                else if (p.durationMs > 0 && p.positionMs > 0 && p.positionMs < p.durationMs * 0.85)
                    itemFor(id, p)?.let { HomeEntry(it, p.positionMs, p.durationMs, p.lastUpdated) }
                else null
            }
            val eps = epProg.mapNotNull { (key, p) ->
                val id = key.substringBefore(':').toLongOrNull()
                if (id == null || id in hiddenFromHome || (p.sourceKey.isNotBlank() && p.sourceKey != curSourceKey)) null
                else if (p.positionMs > 0 &&
                    (p.durationMs <= 0 || (p.durationMs > 0 && p.positionMs < p.durationMs * 0.85))
                )
                    itemFor(id, p)?.let { HomeEntry(it, p.positionMs, p.durationMs, p.lastUpdated, episodeLabelOf(key, p)) }
                else null
            }
            (movies + eps)
                .sortedByDescending { it.lastUpdated }
                .distinctBy { it.item.id }
        }
    }

    // Son İzlenenler: film + bölüm ilerlemeleri, son izlenme zamanına göre
    // sıralanır (tamamlanmışlar da dahil). En yeni başta.
    val recentlyWatched by produceState(
        initialValue = emptyList<HomeEntry>(),
        catalog.byId.size,
        catalog.allItems.size,
        watchedVersion,
        hiddenFromHome,
        curSourceKey
    ) {
        value = withContext(Dispatchers.Default) {
            val vodProg = app.store.loadVodProgress()
            val epProg = app.store.episodeProgress()
            val vod = vodProg.mapNotNull { (id, p) ->
                if (id in hiddenFromHome || (p.sourceKey.isNotBlank() && p.sourceKey != curSourceKey)) null
                else itemFor(id, p)?.let { HomeEntry(it, p.positionMs, p.durationMs, p.lastUpdated) }
            }
            val eps = epProg.mapNotNull { (key, p) ->
                val id = key.substringBefore(':').toLongOrNull()
                if (id == null || id in hiddenFromHome || (p.sourceKey.isNotBlank() && p.sourceKey != curSourceKey)) null
                else itemFor(id, p)?.let { HomeEntry(it, p.positionMs, p.durationMs, p.lastUpdated, episodeLabelOf(key, p)) }
            }
            (vod + eps)
                .sortedByDescending { it.lastUpdated }
                .distinctBy { it.item.id }
                .take(20)
        }
    }

    // Senin İçin (Öneriler): RecommendationEngine ile
    var recommendations by remember { mutableStateOf<List<VodItem>>(emptyList()) }
    LaunchedEffect(catalog.allItems.size, watchedVersion, settings.tmdbApiKey) {
        val storeWatchHistory = withContext(Dispatchers.Default) {
            app.store.loadVodProgress().map { it.key } +
                app.store.episodeProgress().mapNotNull { it.key.substringBefore(':').toLongOrNull() }
        }
        val recommendationsList = com.stalkerapp.data.generateRecommendations(
            catalog.allItems,
            storeWatchHistory.distinct().toSet(),
            settings.tmdbApiKey,
            settings.tmdbLanguage
        )
        recommendations = recommendationsList
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
    // PIN kilidi açıksa yetişkin içerik, oturumda PIN girilmedikçe gizli kalır.
    val catTitles = remember(catalog) { catalog.categories.associate { it.id to it.title } }
    val adultRegex = Regex("18|yetkin|adult|xxx|erotik|porno", RegexOption.IGNORE_CASE)
    val adultVisible = settings.adultContentEnabled && (!settings.lockAdultWithPin || adultUnlocked)
    val blockedCategoryIds = remember(catalog.categories, settings.hiddenCategories, adultVisible) {
        val hiddenSet = settings.hiddenCategories.toSet()
        catalog.categories.filter { cat ->
            val hidden = hiddenSet.contains(cat.title)
            val adult = adultRegex.containsMatchIn(cat.title)
            hidden || (!adultVisible && adult)
        }.map { it.id }.toSet()
    }

    // Bölüm başına öğe sayısı (Kütüphane & İçerik → Ana Sayfa ayarları).
    val sectionSize = settings.homeSectionSize.coerceIn(5, 50)

    val movies by produceState(
        initialValue = emptyList<VodItem>(),
        catalog.movies.size,
        catalog.allItems.size,
        blockedCategoryIds,
        sectionSize
    ) {
        value = withContext(Dispatchers.Default) {
            val mList = if (catalog.movies.isNotEmpty()) catalog.movies else catalog.allItems.filter { !catalog.isSeriesItem(it) }
            mList.filter { it.id > 0 && it.categoryId !in blockedCategoryIds }.distinctBy { it.id }.take(sectionSize)
        }
    }

    val series by produceState(
        initialValue = emptyList<VodItem>(),
        catalog.series.size,
        catalog.allItems.size,
        blockedCategoryIds,
        sectionSize
    ) {
        value = withContext(Dispatchers.Default) {
            val sList = if (catalog.series.isNotEmpty()) catalog.series else catalog.allItems.filter { catalog.isSeriesItem(it) }
            sList.filter { it.id > 0 && it.categoryId !in blockedCategoryIds }.distinctBy { it.id }.take(sectionSize)
        }
    }

    val featured by produceState(
        initialValue = emptyList<VodItem>(),
        movies,
        series
    ) {
        value = withContext(Dispatchers.Default) {
            (series.take(6) + movies.take(6)).shuffled()
        }
    }

    // Ana sayfa düzeni: poster genişliği (Ayarlar → Kütüphane & İçerik → Ana Sayfa Düzeni).
    val posterWidth = when (settings.homeLayout) {
        "compact" -> 104
        "list" -> 168
        else -> 130
    }

    // Bölüm sırası (Ayarlar'dan değiştirilebilir).
    val sectionOrder = remember(settings.homeSectionOrder) {
        val known = listOf("recentchannels", "recent", "continue", "crossdevice", "recommendations", "movies", "series", "favchannels", "live", "favvods")
        val custom = settings.homeSectionOrder.filter { it in known }
        custom + known.filter { it !in custom }
    }

    // Ana sayfadaki "Son İzlenen Kanallar" satırı (son oynatılan canlı kanallar).
    val recentChannels by PlaybackManager.recentChannels.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppleTvTokens.Surface)
            .verticalScroll(scrollState)
    ) {
        // Hero banner Kütüphane & İçerik ayarından kapatılabilir.
        if (settings.heroEnabled && featured.isNotEmpty()) {
            HeroBanner(
                lang = lang,
                items = featured,
                baseUrl = profile?.baseUrl.orEmpty(),
                catTitle = { id -> catTitles[id].orEmpty() },
                onOpenVod = onOpenVod,
                scrollState = scrollState
            )
            // Hero'nun altına nefes payı: içerik hero'ya çok yapışık durmasın.
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ---- Kaynak Yükleme İlerleme Çubuğu ----
        if (catalog.status == VodCatalogStatus.Syncing) {
            val sourceLabel = when (activeKind) {
                "m3u" -> "M3U"
                "xtream" -> "Xtream"
                else -> "Stalker Portal"
            }
            val ratio = if (catalog.totalCategories > 0)
                catalog.doneCategories.toFloat() / catalog.totalCategories
            else if (catalog.portalTotal > 0)
                catalog.loadedCount.toFloat() / catalog.portalTotal
            else 0f
            val pct = (ratio * 100).toInt().coerceIn(0, 100)

            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$sourceLabel — ${str(lang, "Kaynaklar yükleniyor")}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            "%$pct",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { ratio.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.22f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${catalog.loadedCount} ${str(lang, "içerik yüklendi")} • ${catalog.doneCategories}/${catalog.totalCategories} ${str(lang, "kategori")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }

        sectionOrder.forEach { key ->
            when (key) {
                "recent" -> if (recentlyWatched.isNotEmpty()) {
                    AppleSectionHeader(title = str(lang, "Son İzlenenler"), onSeeAll = null) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(recentlyWatched, key = { index, e -> "rec_${e.item.id}_$index" }) { _, e ->
                                VodPosterCard(
                                    item = e.item,
                                    baseUrl = profile?.baseUrl.orEmpty(),
                                    isSeries = catalog.isSeriesItem(e.item),
                                    posterWidth = posterWidth,
                                    label = e.episodeLabel,
                                    watched = isWatched(e.item),
                                    onLongPress = { quickActionItem = e.item },
                                    onClick = { onOpenVod(e.item.id, catalog.isSeriesItem(e.item)) }
                                )
                            }
                        }
                    }
                }
                "continue" -> if (continueWatching.isNotEmpty()) {
                    AppleSectionHeader(title = str(lang, "İzlemeye Devam"), onSeeAll = null) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(continueWatching, key = { index, e -> "cw_${e.item.id}_$index" }) { _, e ->
                                ContinueWatchingCard(
                                    item = e.item,
                                    baseUrl = profile?.baseUrl.orEmpty(),
                                    positionMs = e.positionMs,
                                    durationMs = e.durationMs,
                                    episodeLabel = e.episodeLabel,
                                    onClick = { onOpenVod(e.item.id, catalog.isSeriesItem(e.item)) },
                                    onLongPress = { quickActionItem = e.item }
                                )
                            }
                        }
                    }
                }
                "recommendations" -> if (recommendations.isNotEmpty()) {
                    AppleSectionHeader(title = str(lang, "Senin İçin"), onSeeAll = null) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(recommendations, key = { index, item -> "rec_${item.id}_$index" }) { _, item ->
                                VodPosterCard(
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
                "movies" -> AppleSectionHeader(title = str(lang, "Popüler Filmler"), onSeeAll = { onGotoTab(2) }) {
                    if (movies.isEmpty() && catalog.status != VodCatalogStatus.Syncing) {
                        EmptyState(str(lang, "Film bulunamadı"))
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(movies, key = { index, item -> "mov_${item.id}_$index" }) { _, item ->
                                VodPosterCard(
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
                "series" -> AppleSectionHeader(title = str(lang, "Popüler Diziler"), onSeeAll = { onGotoTab(3) }) {
                    if (series.isEmpty() && catalog.status != VodCatalogStatus.Syncing) {
                        EmptyState(str(lang, "Dizi bulunamadı"))
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(series, key = { index, item -> "ser_${item.id}_$index" }) { _, item ->
                                VodPosterCard(
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
                "recentchannels" -> if (settings.recentChannelsOnHome && recentChannels.isNotEmpty()) {
                    AppleSectionHeader(title = str(lang, "Son İzlenen Kanallar"), onSeeAll = { onGotoTab(1) }) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(recentChannels, key = { index, ch -> "rc_${ch.id}_$index" }) { _, ch ->
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
                "favchannels" -> AppleSectionHeader(title = str(lang, "Favori Kanallar"), onSeeAll = { onGotoTab(1) }) {
                    if (favChannels.isEmpty()) {
                        Text(str(lang, "Henüz favori kanal yok"), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(favChannels.take(sectionSize), key = { index, ch -> "fc_${ch.id}_$index" }) { _, ch ->
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
                "live" -> AppleSectionHeader(title = str(lang, "Canlı TV"), onSeeAll = { onGotoTab(1) }) {
                    when {
                        loadingChannels -> LoadingBox()
                        homeChannels.isNullOrEmpty() -> EmptyState(str(lang, "Kanal bulunamadı"))
                        else -> LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(homeChannels.orEmpty(), key = { index, ch -> "live_${ch.id}_$index" }) { _, ch ->
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
                    AppleSectionHeader(title = str(lang, "Favori Filmler & Diziler"), onSeeAll = { onGotoTab(4) }) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(favVods.take(sectionSize), key = { index, item -> "fv_${item.id}_$index" }) { _, item ->
                                VodPosterCard(
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
            lang = lang,
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
    lang: String,
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
            .padding(horizontal = 16.dp)
            .height(heroHeight)
            .clip(RoundedCornerShape(18.dp))
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
                    .background(AppleTvTokens.BackdropScrim)
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
                        if (isSeries) str(lang, "DİZİ") else str(lang, "FİLM"),
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
                // 3) Birincil + ikincil aksiyon butonları (beyaz pill / cam pill)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    AppleTvButton(
                        onClick = { onOpenVod(item.id, isSeries) },
                        style = AppleTvButtonStyle.Primary,
                        modifier = Modifier.height(46.dp)
                    ) {
                        Text(
                            str(lang, "Oynat"),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 26.dp)
                        )
                    }
                    AppleTvButton(
                        onClick = { onOpenVod(item.id, isSeries) },
                        style = AppleTvButtonStyle.Glass,
                        modifier = Modifier.height(46.dp)
                    ) {
                        Text(
                            str(lang, "Detayları Gör"),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 22.dp)
                        )
                    }
                }

                if (items.size > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(items.size) { index ->
                            val isCurrent = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .size(if (isCurrent) 7.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(if (isCurrent) Color.White else Color.White.copy(alpha = 0.4f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VodPosterCard(
    item: VodItem,
    baseUrl: String,
    isSeries: Boolean,
    posterWidth: Int,
    label: String? = null,
    watched: Boolean = false,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    AppleTvCard(
        modifier = Modifier.width(posterWidth.dp),
        cornerRadius = 18.dp,
        onClick = onClick,
        onLongClick = onLongPress
    ) { _ ->
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            ) {
                AsyncImage(
                    model = resolveUrl(item.poster, baseUrl),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Alt gradyan: başlık okunabilir kalsın.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.55f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.85f)
                            )
                        )
                )
                if (watched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(4.dp)
                    ) {
                        Text(
                            "✓",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (label != null) {
                    GlassSurface(
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                        shape = AppleTvTokens.CardShapeSmall
                    ) {
                        Text(
                            label,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                item.name,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
            )
        }
    }
}

@Composable
private fun ChannelCard(channel: Channel, baseUrl: String, onClick: () -> Unit) {
    AppleTvCard(
        modifier = Modifier.width(160.dp),
        cornerRadius = 18.dp,
        onClick = onClick
    ) { _ ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ChannelLogo(
                logo = resolveUrl(channel.logo, baseUrl),
                channelName = channel.name,
                baseUrl = baseUrl,
                modifier = Modifier.size(44.dp, 32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (channel.tvGenreTitle.isNotBlank()) {
                    Text(
                        channel.tvGenreTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
    episodeLabel: String? = null,
    posterWidth: Int = 130,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    AppleTvCard(
        modifier = Modifier.width(posterWidth.dp),
        cornerRadius = 18.dp,
        onClick = onClick,
        onLongClick = onLongPress
    ) { _ ->
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
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
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
                if (episodeLabel != null) {
                    GlassSurface(
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                        shape = AppleTvTokens.CardShapeSmall
                    ) {
                        Text(
                            episodeLabel,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                if (episodeLabel != null) "${item.name} • $episodeLabel" else item.name,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
            )
        }
    }
}
