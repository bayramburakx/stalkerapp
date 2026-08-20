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

    // İzlemeye Devam: filmler + dizi bölümleri, EN YENİ İZLENEN BAŞTA.
    val continueWatching = remember(catalog, watchedVersion, hiddenFromHome, curSourceKey) {
        val movies = app.store.loadVodProgress().mapNotNull { (id, p) ->
            if (id in hiddenFromHome || (p.sourceKey.isNotBlank() && p.sourceKey != curSourceKey)) null
            else if (p.durationMs > 0 && p.positionMs > 0 && p.positionMs < p.durationMs * 0.85)
                itemFor(id, p)?.let { HomeEntry(it, p.positionMs, p.durationMs, p.lastUpdated) }
            else null
        }
        val eps = app.store.episodeProgress().mapNotNull { (key, p) ->
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

    // Son İzlenenler: film + bölüm ilerlemeleri, son izlenme zamanına göre
    // sıralanır (tamamlanmışlar da dahil). En yeni başta.
    val recentlyWatched = remember(catalog, watchedVersion, hiddenFromHome, curSourceKey) {
        val vod = app.store.loadVodProgress().mapNotNull { (id, p) ->
            if (id in hiddenFromHome || (p.sourceKey.isNotBlank() && p.sourceKey != curSourceKey)) null
            else itemFor(id, p)?.let { HomeEntry(it, p.positionMs, p.durationMs, p.lastUpdated) }
        }
        val eps = app.store.episodeProgress().mapNotNull { (key, p) ->
            val id = key.substringBefore(':').toLongOrNull()
            if (id == null || id in hiddenFromHome || (p.sourceKey.isNotBlank() && p.sourceKey != curSourceKey)) null
            else itemFor(id, p)?.let { HomeEntry(it, p.positionMs, p.durationMs, p.lastUpdated, episodeLabelOf(key, p)) }
        }
        (vod + eps)
            .sortedByDescending { it.lastUpdated }
            .distinctBy { it.item.id }
            .take(20)
    }

    // Senin İçin (Öneriler): RecommendationEngine ile
    var recommendations by remember { mutableStateOf<List<VodItem>>(emptyList()) }
    LaunchedEffect(catalog.allItems, watchedVersion, settings.tmdbApiKey) {
        val storeWatchHistory = app.store.loadVodProgress().map { it.key } +
            app.store.episodeProgress().mapNotNull { it.key.substringBefore(':').toLongOrNull() }
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
    val mList = if (catalog.movies.isNotEmpty()) catalog.movies else catalog.allItems.filter { !catalog.isSeriesItem(it) }
    val sList = if (catalog.series.isNotEmpty()) catalog.series else catalog.allItems.filter { catalog.isSeriesItem(it) }
    val movies = remember(mList, blockedCategoryIds, sectionSize) {
        mList.filter { it.id > 0 && it.categoryId !in blockedCategoryIds }.distinctBy { it.id }.take(sectionSize)
    }
    val series = remember(sList, blockedCategoryIds, sectionSize) {
        sList.filter { it.id > 0 && it.categoryId !in blockedCategoryIds }.distinctBy { it.id }.take(sectionSize)
    }
    val featured = remember(series, movies) {
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.10f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$sourceLabel — ${str(lang, "Kaynaklar yükleniyor")}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "%$pct",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { ratio.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${catalog.loadedCount} ${str(lang, "içerik yüklendi")} • ${catalog.doneCategories}/${catalog.totalCategories} ${str(lang, "kategori")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        sectionOrder.forEach { key ->
            when (key) {
                "recent" -> if (recentlyWatched.isNotEmpty()) {
                    Section(title = str(lang, "Son İzlenenler"), lang = lang, onSeeAll = null) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(recentlyWatched, key = { it.item.id }) { e ->
                                VodPoster(
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
                    Section(title = str(lang, "İzlemeye Devam"), lang = lang, onSeeAll = null) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(continueWatching, key = { it.item.id }) { e ->
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
                    Section(title = str(lang, "Senin İçin"), lang = lang, onSeeAll = null) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(recommendations, key = { it.id }) { item ->
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
                "movies" -> Section(title = str(lang, "Popüler Filmler"), lang = lang, onSeeAll = { onGotoTab(2) }) {
                    if (movies.isEmpty() && catalog.status != VodCatalogStatus.Syncing) {
                        EmptyState(str(lang, "Film bulunamadı"))
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
                "series" -> Section(title = str(lang, "Popüler Diziler"), lang = lang, onSeeAll = { onGotoTab(3) }) {
                    if (series.isEmpty() && catalog.status != VodCatalogStatus.Syncing) {
                        EmptyState(str(lang, "Dizi bulunamadı"))
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
                "recentchannels" -> if (settings.recentChannelsOnHome && recentChannels.isNotEmpty()) {
                    Section(title = str(lang, "Son İzlenen Kanallar"), lang = lang, onSeeAll = { onGotoTab(1) }) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(recentChannels, key = { it.id }) { ch ->
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
                "favchannels" -> Section(title = str(lang, "Favori Kanallar"), lang = lang, onSeeAll = { onGotoTab(1) }) {
                    if (favChannels.isEmpty()) {
                        Text(str(lang, "Henüz favori kanal yok"), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(favChannels.take(sectionSize), key = { it.id }) { ch ->
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
                "live" -> Section(title = str(lang, "Canlı TV"), lang = lang, onSeeAll = { onGotoTab(1) }) {
                    when {
                        loadingChannels -> LoadingBox()
                        homeChannels.isNullOrEmpty() -> EmptyState(str(lang, "Kanal bulunamadı"))
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
                    Section(title = str(lang, "Favori Filmler & Diziler"), lang = lang, onSeeAll = { onGotoTab(4) }) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(favVods.take(sectionSize), key = { it.id }) { item ->
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
                    Text(str(lang, "Detayları Gör"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, lang: String, onSeeAll: (() -> Unit)?, content: @Composable () -> Unit) {
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
                        contentDescription = str(lang, "Tümü"),
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
    episodeLabel: String? = null,
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
            if (episodeLabel != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        episodeLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        Text(
            if (episodeLabel != null) "${item.name} • $episodeLabel" else item.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
