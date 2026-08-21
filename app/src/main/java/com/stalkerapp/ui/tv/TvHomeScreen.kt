package com.stalkerapp.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.Genre
import com.stalkerapp.data.VodItem
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.VodCatalogState
import com.stalkerapp.ui.VodCatalogStatus
import com.stalkerapp.ui.components.AppleTvButton
import com.stalkerapp.ui.components.AppleTvButtonStyle
import com.stalkerapp.ui.components.AppleTvCard
import com.stalkerapp.ui.components.AppleSectionHeader
import com.stalkerapp.ui.components.AppleTvTokens
import com.stalkerapp.ui.components.ChannelLogo
import com.stalkerapp.ui.components.GlassChip
import com.stalkerapp.ui.components.GlassSurface
import com.stalkerapp.ui.components.resolveUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TV kumandasında OK / Seçim tuşuna basılıp basılmadığını kontrol eder.
 */
fun isTvSelectKey(ev: KeyEvent): Boolean {
    return ev.type == KeyEventType.KeyDown && (
        ev.key == Key.DirectionCenter ||
        ev.key == Key.Enter ||
        ev.key == Key.NumPadEnter
    )
}

/**
 * Cihazın Android TV / Leanback televizyon cihazı olup olmadığını kontrol eder.
 */
fun isTvDevice(context: android.content.Context): Boolean {
    val uiMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK
    val pm = context.packageManager
    return uiMode == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
        pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
        pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION)
}

private data class AppleTvTab(val title: String, val icon: ImageVector, val id: Int)

/**
 * Apple TV Tasarım Sistemine Dayalı Android TV Ana Ekranı.
 * - Üstte Yüzen Frosted Glass Kapsül Sekme Çubuğu
 * - Sinematik 16:9 Hero Billboard (Öne Çıkan Başlık, IMDb Rozeti, Hemen İzle Butonu)
 * - Yumuşak D-Pad Odak Animasyonları ve 60 FPS Sıfır Donma Mimarisi
 */
@Composable
fun TvHomeScreen(
    vm: MainViewModel,
    onOpenVod: (Long, Boolean) -> Unit,
    onOpenChannel: (Channel) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onOpenProfiles: () -> Unit = {},
    onOpenPlayer: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val settings by vm.settings.collectAsStateWithLifecycle()
    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()
    val favChannels by vm.favoriteChannels.collectAsStateWithLifecycle()
    val homeChannels by vm.homeChannels.collectAsStateWithLifecycle()
    val recentChannels by PlaybackManager.recentChannels.collectAsStateWithLifecycle()

    val profile = vm.repository.cachedProfile()
    val activeKind = vm.enabledSourceKind()
    val activeSourceId = vm.activeSourceId()

    LaunchedEffect(profile, activeKind, activeSourceId) {
        if (activeKind == "m3u" || activeKind == "xtream") {
            vm.ensureExternalVodCatalog()
        } else {
            profile?.let { vm.syncVodIfNeeded(it) }
        }
        if (homeChannels == null) {
            vm.loadHomeChannels(profile)
        }
    }

    // 0: İzle (Ana Sayfa), 1: Canlı TV, 2: Filmler, 3: Diziler, 4: Ara
    var selectedTab by remember { mutableIntStateOf(0) }

    val popularMovies by produceState(
        initialValue = emptyList<VodItem>(),
        catalog.movies.size,
        catalog.status
    ) {
        if (catalog.status == VodCatalogStatus.Syncing && value.isNotEmpty()) return@produceState
        value = withContext(Dispatchers.Default) {
            val list = if (catalog.movies.isNotEmpty()) catalog.movies
            else catalog.allItems.take(100).filter { !catalog.isSeriesItem(it) }
            list.filter { it.id > 0 }.distinctBy { it.id }.take(30)
        }
    }

    val popularSeries by produceState(
        initialValue = emptyList<VodItem>(),
        catalog.series.size,
        catalog.status
    ) {
        if (catalog.status == VodCatalogStatus.Syncing && value.isNotEmpty()) return@produceState
        value = withContext(Dispatchers.Default) {
            val list = if (catalog.series.isNotEmpty()) catalog.series
            else catalog.allItems.take(100).filter { catalog.isSeriesItem(it) }
            list.filter { it.id > 0 }.distinctBy { it.id }.take(30)
        }
    }

    val liveChannels = remember(homeChannels, favChannels) {
        val all = (favChannels + (homeChannels ?: emptyList())).filter { it.id > 0 }.distinctBy { it.id }
        all.take(30)
    }

    val recentChannelList = remember(recentChannels) {
        recentChannels.filter { it.id > 0 }.distinctBy { it.id }.take(20)
    }

    val watchedVersion by vm.watchedVersion.collectAsStateWithLifecycle()
    val curSourceKey = app.store.activeSourceKey()
    val continueWatching by produceState(
        initialValue = emptyList<VodItem>(),
        catalog.byId.size,
        watchedVersion,
        curSourceKey
    ) {
        value = withContext(Dispatchers.Default) {
            val vodProg = app.store.loadVodProgress().mapNotNull { (id, p) ->
                if (p.sourceKey.isNotBlank() && p.sourceKey != curSourceKey) null
                else if (p.durationMs > 0 && p.positionMs > 0 && p.positionMs < p.durationMs * 0.85) {
                    catalog.byId[id] ?: p.toVodItem(id)
                } else null
            }
            val epProg = app.store.episodeProgress().mapNotNull { (key, p) ->
                val id = key.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
                if (p.sourceKey.isNotBlank() && p.sourceKey != curSourceKey) null
                else if (p.positionMs > 0 && (p.durationMs <= 0 || (p.durationMs > 0 && p.positionMs < p.durationMs * 0.85))) {
                    catalog.byId[id] ?: p.toVodItem(id)
                } else null
            }
            (vodProg + epProg).filter { it.id > 0 }.distinctBy { it.id }.take(20)
        }
    }

    // Apple TV Tabs
    val tabs = remember {
        listOf(
            AppleTvTab("İzle", Icons.Default.Home, 0),
            AppleTvTab("Canlı TV", Icons.Default.LiveTv, 1),
            AppleTvTab("Filmler", Icons.Default.Movie, 2),
            AppleTvTab("Diziler", Icons.Default.VideoLibrary, 3),
            AppleTvTab("Arama", Icons.Default.Search, 4)
        )
    }

    val heroItems = remember(popularMovies, popularSeries, continueWatching) {
        val list = mutableListOf<VodItem>()
        list.addAll(continueWatching.take(2))
        list.addAll(popularMovies.take(4))
        list.addAll(popularSeries.take(4))
        list.distinctBy { it.id }.take(8)
    }

    var quickActionVodItem by remember { mutableStateOf<VodItem?>(null) }
    var quickActionChannel by remember { mutableStateOf<Channel?>(null) }
    val favVods by vm.favoriteVods.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        // =========================================================================
        // 1. APPLE TV FLOATING GLASS ÜST GEZİNME ÇUBUĞU (TOP BAR)
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sol: Portio Markası ve Aktif Kaynak Rozeti
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "portio",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                )
                Spacer(Modifier.width(10.dp))
                val sourceBadge = when (activeKind) {
                    "m3u" -> "M3U"
                    "xtream" -> "XTREAM"
                    else -> "STALKER"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(0.5.dp, AppleTvTokens.Hairline, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = sourceBadge,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Orta: Apple TV Tarzı Yüzen Cam Kapsül Sekme Çubuğu
            GlassSurface(
                modifier = Modifier,
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        AppleTvTabButton(
                            tab = tab,
                            selected = selectedTab == tab.id,
                            onClick = { selectedTab = tab.id }
                        )
                    }
                }
            }

            // Sağ: Profil & Ayarlar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppleTvCircleAction(
                    icon = Icons.Default.AccountCircle,
                    label = "Profil",
                    onClick = onOpenProfiles
                )
                AppleTvCircleAction(
                    icon = Icons.Default.Settings,
                    label = "Ayarlar",
                    onClick = onOpenSettings
                )
                AppleTvCircleAction(
                    icon = Icons.Default.Search,
                    label = "Ara",
                    onClick = onOpenSearch
                )
                AppleTvCircleAction(
                    icon = Icons.Default.Tv,
                    label = "Kılavuz",
                    onClick = onOpenGuide
                )
            }
        }

        // Senkronizasyon Çubuğu (Yükleme yapılırken zarif animasyon)
        if (catalog.status == VodCatalogStatus.Syncing) {
            val ratio = if (catalog.totalCategories > 0)
                catalog.doneCategories.toFloat() / catalog.totalCategories
            else if (catalog.portalTotal > 0)
                catalog.loadedCount.toFloat() / catalog.portalTotal
            else 0f
            val pct = (ratio * 100).toInt().coerceIn(0, 100)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppleTvTokens.SurfaceRaised.copy(alpha = 0.7f))
                    .border(0.5.dp, AppleTvTokens.Hairline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Katalog senkronize ediliyor… (%$pct)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                    LinearProgressIndicator(
                        progress = { ratio.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .width(100.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }
        }

        // =========================================================================
        // 2. SEKMELER (ANA SAYFA, CANLI TV, FİLMLER, DİZİLER, ARAMA)
        // =========================================================================
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                1 -> {
                    TvLiveSection(
                        vm = vm,
                        onOpenChannel = onOpenChannel,
                        onLongClickChannel = { ch -> quickActionChannel = ch }
                    )
                }
                2 -> {
                    TvVodSection(
                        vm = vm,
                        isSeries = false,
                        tmdbApiKey = settings.tmdbApiKey,
                        onOpenVod = { id -> onOpenVod(id, false) },
                        onLongClickVod = { item -> quickActionVodItem = item }
                    )
                }
                3 -> {
                    TvVodSection(
                        vm = vm,
                        isSeries = true,
                        tmdbApiKey = settings.tmdbApiKey,
                        onOpenVod = { id -> onOpenVod(id, true) },
                        onLongClickVod = { item -> quickActionVodItem = item }
                    )
                }
                4 -> {
                    TvSearchSection(
                        vm = vm,
                        catalog = catalog,
                        onOpenChannel = onOpenChannel,
                        onOpenVod = onOpenVod
                    )
                }
                else -> {
                    // TAB 0: APPLE TV "İZLE" ANA SAYFASI
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // A) APPLE TV HERO BILLBOARD SLIDER (ÖNE ÇIKAN SİNEMATİK AFİŞLER)
                        if (heroItems.isNotEmpty()) {
                            item(key = "apple_tv_hero_billboard_slider") {
                                AppleTvHeroBillboard(
                                    items = heroItems,
                                    tmdbApiKey = settings.tmdbApiKey,
                                    onPlayClick = { item ->
                                        onOpenVod(item.id, catalog.isSeriesItem(item))
                                    },
                                    onDetailsClick = { item ->
                                        onOpenVod(item.id, catalog.isSeriesItem(item))
                                    }
                                )
                            }
                        }

                        // B) İZLEMEYE DEVAM ET
                        if (continueWatching.isNotEmpty()) {
                            item(key = "section_cw") {
                                TvSection(title = "İzlemeye Devam Et") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        itemsIndexed(continueWatching, key = { index, item -> "cw_${item.id}_$index" }) { _, item ->
                                            TvVodCard(
                                                item = item,
                                                tmdbApiKey = settings.tmdbApiKey,
                                                onClick = { onOpenVod(item.id, catalog.isSeriesItem(item)) },
                                                onLongClick = { quickActionVodItem = item }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // C) CANLI TV KANALLARI (Popüler & Favoriler)
                        if (liveChannels.isNotEmpty()) {
                            item(key = "section_live") {
                                TvSection(title = "Canlı TV Kanalları") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        itemsIndexed(liveChannels, key = { index, ch -> "live_${ch.id}_$index" }) { _, channel ->
                                            TvChannelCard(
                                                channel = channel,
                                                onClick = { onOpenChannel(channel) },
                                                onLongClick = { quickActionChannel = channel }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // D) POPÜLER FİLMLER
                        if (popularMovies.isNotEmpty()) {
                            item(key = "section_movies") {
                                TvSection(title = "Popüler Filmler") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        itemsIndexed(popularMovies, key = { index, item -> "mov_${item.id}_$index" }) { _, item ->
                                            TvVodCard(
                                                item = item,
                                                tmdbApiKey = settings.tmdbApiKey,
                                                onClick = { onOpenVod(item.id, false) },
                                                onLongClick = { quickActionVodItem = item }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // E) POPÜLER DİZİLER
                        if (popularSeries.isNotEmpty()) {
                            item(key = "section_series") {
                                TvSection(title = "Popüler Diziler") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        itemsIndexed(popularSeries, key = { index, item -> "ser_${item.id}_$index" }) { _, item ->
                                            TvVodCard(
                                                item = item,
                                                tmdbApiKey = settings.tmdbApiKey,
                                                onClick = { onOpenVod(item.id, true) },
                                                onLongClick = { quickActionVodItem = item }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // F) SON İZLENEN KANALLAR
                        if (recentChannelList.isNotEmpty()) {
                            item(key = "section_recent") {
                                TvSection(title = "Son İzlenen Kanallar") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        itemsIndexed(recentChannelList, key = { index, ch -> "rec_${ch.id}_$index" }) { _, channel ->
                                            TvChannelCard(
                                                channel = channel,
                                                onClick = { onOpenChannel(channel) },
                                                onLongClick = { quickActionChannel = channel }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Quick Actions Modalları (TV için Basılı Tutma Menüsü)
    if (quickActionVodItem != null) {
        val qItem = quickActionVodItem!!
        val isSeries = catalog.isSeriesItem(qItem)
        val isFav = favVods.any { it.id == qItem.id }
        val isWatched = app.store.isWatchedOverride(qItem.id)

        TvVodQuickActionsDialog(
            item = qItem,
            isSeries = isSeries,
            isFavorite = isFav,
            isWatched = isWatched,
            onPlay = { onOpenVod(qItem.id, isSeries) },
            onDetails = { onOpenVod(qItem.id, isSeries) },
            onToggleFavorite = { vm.toggleFavoriteVod(qItem) },
            onToggleWatched = {
                app.store.toggleWatchedOverride(qItem.id)
                vm.bumpWatched()
            },
            onHideFromHome = {
                val currentHidden = settings.hiddenFromHome.toMutableList()
                if (qItem.id !in currentHidden) {
                    currentHidden.add(qItem.id)
                    vm.saveSettings(settings.copy(hiddenFromHome = currentHidden))
                }
            },
            onDismiss = { quickActionVodItem = null }
        )
    }

    if (quickActionChannel != null) {
        val qChannel = quickActionChannel!!
        val isFav = favChannels.any { it.id == qChannel.id }
        TvChannelQuickActionsDialog(
            channel = qChannel,
            isFavorite = isFav,
            onPlay = { onOpenChannel(qChannel) },
            onToggleFavorite = { vm.toggleFavoriteChannel(qChannel) },
            onDismiss = { quickActionChannel = null }
        )
    }
}

// =========================================================================
// APPLE TV HERO BILLBOARD SLIDER (ÖNE ÇIKAN SİNEMATİK SLIDER)
// =========================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppleTvHeroBillboard(
    items: List<VodItem>,
    tmdbApiKey: String,
    onPlayClick: (VodItem) -> Unit,
    onDetailsClick: (VodItem) -> Unit
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })

    LaunchedEffect(pagerState) {
        if (items.size > 1) {
            while (true) {
                delay(5500)
                val next = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp)
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AppleTvTokens.Surface)
            .border(1.dp, AppleTvTokens.Hairline, RoundedCornerShape(20.dp))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = items[page]
            val app = LocalContext.current.applicationContext as StalkerApp
            var resolvedBackdrop by remember(item.id, item.poster) {
                mutableStateOf(app.tmdb.getCachedPoster(item.name, item.isSeries) ?: item.poster)
            }

            LaunchedEffect(item.name, item.poster, item.year, item.isSeries, tmdbApiKey) {
                if (tmdbApiKey.isNotBlank() && item.poster.isBlank()) {
                    val p = app.tmdb.resolvePoster(item.name, item.year, item.isSeries, item.poster, tmdbApiKey)
                    if (p.isNotBlank()) resolvedBackdrop = p
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = resolveUrl(resolvedBackdrop.ifBlank { item.poster }),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Apple TV Gradyan Karartması (Sol ve Alt)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.95f),
                                    Color.Black.copy(alpha = 0.70f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppleTvTokens.BackdropScrim)
                )

                // Başlık ve Bilgiler
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.68f)
                        .padding(28.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (item.isSeries) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE50914))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("DİZİ", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .border(0.5.dp, AppleTvTokens.HairlineStrong, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("4K UHD", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        val rating = item.rating.trim().trimEnd('/').takeIf { it.isNotBlank() && it != "0" && it != "0.0" }
                        if (rating != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .border(0.5.dp, Color(0xFFFFC107).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("★ $rating IMDb", color = Color(0xFFFFC107), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (item.year.isNotBlank() && item.year != "0") {
                            Text(item.year, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 26.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 30.sp
                    )

                    if (item.description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppleTvButton(
                            onClick = { onPlayClick(item) },
                            style = AppleTvButtonStyle.Primary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Hemen İzle",
                                    color = Color.Black,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        AppleTvButton(
                            onClick = { onDetailsClick(item) },
                            style = AppleTvButtonStyle.Secondary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Detaylar",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Apple TV Dot Pager Indicators (Sağ Alt)
        if (items.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(items.size) { index ->
                    val isCurrent = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isCurrent) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (isCurrent) Color.White else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

// =========================================================================
// TV CANLI TV GÖRÜNÜMÜ (Hızlı 2 Panelli D-Pad Gezinme)
// =========================================================================
@Composable
private fun TvLiveSection(
    vm: MainViewModel,
    onOpenChannel: (Channel) -> Unit,
    onLongClickChannel: ((Channel) -> Unit)? = null
) {
    val profile = vm.repository.cachedProfile()
    var genres by remember { mutableStateOf<List<Genre>?>(null) }
    var allChannels by remember { mutableStateOf<List<Channel>?>(null) }
    var selectedGenreId by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(profile, vm.enabledSourceKind(), vm.activeSourceId()) {
        loading = true
        val result = vm.loadChannelsForActiveSource(profile)
        if (result != null) {
            genres = result.first
            allChannels = result.second
        }
        loading = false
    }

    if (loading && allChannels == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    val genreList = remember(genres) { genres ?: listOf(Genre(0, "Tümü")) }
    val displayedChannels = remember(allChannels, selectedGenreId) {
        val list = allChannels ?: emptyList()
        val filtered = if (selectedGenreId <= 0L) list
        else list.filter { it.tvGenreId == selectedGenreId }
        filtered.filter { it.id > 0 }.distinctBy { it.id }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        // Sol Panel: Kategoriler
        LazyColumn(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .border(1.dp, AppleTvTokens.Hairline, RoundedCornerShape(16.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(genreList, key = { index, g -> "genre_${g.id}_$index" }) { _, g ->
                TvCategoryItem(
                    title = g.title,
                    selected = selectedGenreId == g.id,
                    onClick = { selectedGenreId = g.id }
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        // Sağ Panel: 3 Kolonlu Kanal Kartları
        val gridState = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(displayedChannels, key = { index, ch -> "ch_${ch.id}_$index" }) { _, channel ->
                TvChannelGridCard(
                    channel = channel,
                    onClick = { onOpenChannel(channel) },
                    onLongClick = { onLongClickChannel?.invoke(channel) }
                )
            }
        }
    }
}

// =========================================================================
// TV VOD GÖRÜNÜMÜ (Talep Üzerine Kategori Yükleme - 150ms Hız)
// =========================================================================
@Composable
private fun TvVodSection(
    vm: MainViewModel,
    isSeries: Boolean,
    tmdbApiKey: String,
    onOpenVod: (Long) -> Unit,
    onLongClickVod: ((VodItem) -> Unit)? = null
) {
    val profile = vm.repository.cachedProfile()
    var categories by remember { mutableStateOf<List<Genre>>(emptyList()) }
    var selectedCatId by remember { mutableStateOf(0L) }
    var items by remember { mutableStateOf<List<VodItem>>(emptyList()) }
    var loadingCats by remember { mutableStateOf(true) }
    var loadingItems by remember { mutableStateOf(true) }
    var visibleLimit by remember { mutableIntStateOf(40) }

    LaunchedEffect(profile, isSeries, vm.enabledSourceKind(), vm.activeSourceId()) {
        loadingCats = true
        categories = vm.loadVodCategories(profile, isSeries)
        loadingCats = false
    }

    LaunchedEffect(profile, isSeries, selectedCatId, vm.enabledSourceKind(), vm.activeSourceId()) {
        loadingItems = true
        items = vm.loadVodCategoryItems(profile, selectedCatId, isSeries)
        visibleLimit = 40
        loadingItems = false
    }

    val displayedItems = remember(items, visibleLimit) {
        items.take(visibleLimit).filter { it.id > 0 }.distinctBy { it.id }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        // Üst Kategori Filtre Çipleri
        if (categories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(categories, key = { index, cat -> "cat_${cat.id}_$index" }) { _, cat ->
                    GlassChip(
                        selected = selectedCatId == cat.id,
                        onClick = { selectedCatId = cat.id },
                        label = cat.title
                    )
                }
            }
        }

        if (loadingItems && items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Bu kategoride içerik bulunamadı.",
                    color = Color.White.copy(0.6f),
                    fontSize = 15.sp
                )
            }
        } else {
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(displayedItems, key = { index, item -> "vod_${item.id}_$index" }) { _, item ->
                    TvVodCard(
                        item = item,
                        tmdbApiKey = tmdbApiKey,
                        onClick = { onOpenVod(item.id) },
                        onLongClick = { onLongClickVod?.invoke(item) }
                    )
                }
                if (displayedItems.size < items.size) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppleTvTokens.SurfaceRaised)
                                .border(1.dp, AppleTvTokens.HairlineStrong, RoundedCornerShape(12.dp))
                                .clickable { visibleLimit += 40 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Daha fazla yükle (${items.size - displayedItems.size})…",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TV ARAMA GÖRÜNÜMÜ
// =========================================================================
@Composable
private fun TvSearchSection(
    vm: MainViewModel,
    catalog: VodCatalogState,
    onOpenChannel: (Channel) -> Unit,
    onOpenVod: (Long, Boolean) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var isInputDialogOpen by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }

    val homeChannels by vm.homeChannels.collectAsStateWithLifecycle()

    val searchResults by produceState(
        initialValue = Triple(emptyList<Channel>(), emptyList<VodItem>(), emptyList<VodItem>()),
        query,
        catalog.allItems.size,
        homeChannels
    ) {
        val q = query.trim()
        if (q.isBlank()) {
            value = Triple(emptyList(), emptyList(), emptyList())
            return@produceState
        }
        val channels = (homeChannels ?: emptyList()).filter { it.name.contains(q, ignoreCase = true) }.take(20)
        val movies = catalog.movies.filter { it.name.contains(q, ignoreCase = true) }.take(20)
        val series = catalog.series.filter { it.name.contains(q, ignoreCase = true) }.take(20)
        value = Triple(channels, movies, series)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        // Arama Çubuğu (Kumanda ile tıklayınca klavye modalı açılır)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .onFocusChanged { searchFocused = it.isFocused }
                .focusable()
                .border(
                    width = if (searchFocused) 3.dp else 1.dp,
                    color = if (searchFocused) Color.White else AppleTvTokens.Hairline,
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable { isInputDialogOpen = true }
                .onKeyEvent { ev ->
                    if (isTvSelectKey(ev)) {
                        isInputDialogOpen = true; true
                    } else false
                },
            shape = RoundedCornerShape(16.dp),
            color = AppleTvTokens.SurfaceRaised
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (searchFocused) Color.White else Color.White.copy(0.6f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = query.ifBlank { "Aramak istediğiniz film, dizi veya kanal adı yazın…" },
                    color = if (query.isNotBlank()) Color.White else Color.White.copy(0.4f),
                    fontSize = 15.sp
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Arama Sonuçları
        if (query.isNotBlank()) {
            val (channels, movies, series) = searchResults
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (channels.isNotEmpty()) {
                    item {
                        TvSection(title = "Kanallar (${channels.size})") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(channels, key = { index, ch -> "s_ch_${ch.id}_$index" }) { _, ch ->
                                    TvChannelCard(channel = ch, onClick = { onOpenChannel(ch) })
                                }
                            }
                        }
                    }
                }
                if (movies.isNotEmpty()) {
                    item {
                        TvSection(title = "Filmler (${movies.size})") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                itemsIndexed(movies, key = { index, m -> "s_mov_${m.id}_$index" }) { _, m ->
                                    TvVodCard(item = m, onClick = { onOpenVod(m.id, false) })
                                }
                            }
                        }
                    }
                }
                if (series.isNotEmpty()) {
                    item {
                        TvSection(title = "Diziler (${series.size})") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                itemsIndexed(series, key = { index, s -> "s_ser_${s.id}_$index" }) { _, s ->
                                    TvVodCard(item = s, onClick = { onOpenVod(s.id, true) })
                                }
                            }
                        }
                    }
                }
                if (channels.isEmpty() && movies.isEmpty() && series.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("Sonuç bulunamadı.", color = Color.White.copy(0.6f))
                        }
                    }
                }
            }
        }
    }

    if (isInputDialogOpen) {
        TvSearchInputDialog(
            initialQuery = query,
            onDismiss = { isInputDialogOpen = false },
            onConfirm = {
                query = it
                isInputDialogOpen = false
            }
        )
    }
}

// =========================================================================
// TV YARDIMCI BİLEŞENLERİ (APPLE TV STİLİNDE)
// =========================================================================
@Composable
private fun AppleTvTabButton(
    tab: AppleTvTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.10f else 1.0f, label = "tab_scale")

    Surface(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(50))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(50)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onKeyEvent { ev ->
                if (isTvSelectKey(ev)) {
                    onClick(); true
                } else false
            },
        shape = RoundedCornerShape(50),
        color = if (isFocused) Color.White
        else if (selected) Color.White.copy(alpha = 0.20f)
        else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.title,
                tint = if (isFocused) Color.Black else if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = tab.title,
                color = if (isFocused) Color.Black else if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AppleTvCircleAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.15f else 1.0f, label = "act_scale")

    Box(
        modifier = Modifier
            .size(38.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.10f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else AppleTvTokens.Hairline,
                shape = CircleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .onKeyEvent { ev ->
                if (isTvSelectKey(ev)) {
                    onClick(); true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun TvSearchInputDialog(
    initialQuery: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialQuery) }
    val focusReq = remember { FocusRequester() }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Arama Yapın", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("Aramak istediğiniz adı yazın…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusReq)
                )
                LaunchedEffect(Unit) {
                    delay(100)
                    runCatching { focusReq.requestFocus() }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(text) }) {
                Text("Ara", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = {
                onConfirm("")
            }) {
                Text("Temizle", color = Color.White.copy(0.6f))
            }
        },
        containerColor = AppleTvTokens.SurfaceRaised,
        shape = RoundedCornerShape(14.dp)
    )
}
@Composable
private fun TvCategoryItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .onKeyEvent { ev ->
                if (isTvSelectKey(ev)) {
                    onClick(); true
                } else false
            },
        color = when {
            focused -> Color.White
            selected -> AppleTvTokens.SurfaceRaised
            else -> Color.Transparent
        },
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = title,
            color = when {
                focused -> Color.Black
                selected -> Color.White
                else -> Color.White.copy(0.85f)
            },
            fontSize = 13.sp,
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        AppleSectionHeader(title = title, modifier = Modifier.padding(start = 12.dp, end = 12.dp))
        content()
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    AppleTvCard(
        modifier = Modifier.size(130.dp, 84.dp),
        onClick = onClick,
        onLongClick = onLongClick
    ) { isFocused ->
        ChannelLogo(
            logo = channel.logo,
            channelName = channel.name,
            modifier = Modifier.size(86.dp, 52.dp).align(Alignment.Center)
        )
        if (isFocused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f)))
                    )
            )
            Text(
                channel.name,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(3.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvChannelGridCard(
    channel: Channel,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.05f else 1.0f, label = "ch_grid_scale")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) AppleTvTokens.SurfaceRaised else Color.Black.copy(alpha = 0.5f))
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Color.White else AppleTvTokens.Hairline,
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .onKeyEvent { ev ->
                if (ev.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MENU && ev.type == KeyEventType.KeyUp) {
                    onLongClick?.invoke(); true
                } else if (isTvSelectKey(ev)) {
                    onClick(); true
                } else false
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelLogo(
            logo = channel.logo,
            channelName = channel.name,
            modifier = Modifier.size(54.dp, 36.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = if (focused) Color.White else Color.White.copy(0.9f),
                fontSize = 13.sp,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (channel.tvGenreTitle.isNotBlank()) {
                Text(
                    text = channel.tvGenreTitle,
                    color = Color.White.copy(0.6f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvVodCard(
    item: VodItem,
    tmdbApiKey: String = "",
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    var resolvedPoster by remember(item.id, item.poster) {
        mutableStateOf(app.tmdb.getCachedPoster(item.name, item.isSeries) ?: item.poster)
    }

    LaunchedEffect(item.name, item.poster, item.year, item.isSeries, tmdbApiKey) {
        if (tmdbApiKey.isNotBlank() && item.poster.isBlank()) {
            val p = app.tmdb.resolvePoster(item.name, item.year, item.isSeries, item.poster, tmdbApiKey)
            if (p.isNotBlank()) {
                resolvedPoster = p
            }
        }
    }

    Column(modifier = Modifier.width(135.dp)) {
        AppleTvCard(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            onClick = onClick,
            onLongClick = onLongClick
        ) { isFocused ->
            AsyncImage(
                model = resolveUrl(resolvedPoster.ifBlank { item.poster }),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (item.isSeries) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .background(Color(0xFFE50914), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("DİZİ", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
            val ratingText = item.rating.trim().trimEnd('/').let { r ->
                if (r.isBlank() || r == "0" || r == "0.0") null else r
            }
            if (ratingText != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("★ $ratingText", color = Color(0xFFFFC107), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.name,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =========================================================================
// TV QUICK ACTIONS MODAL DIALOGS (APPLE TV FROSTED GLASS)
// =========================================================================
@Composable
private fun TvVodQuickActionsDialog(
    item: VodItem,
    isSeries: Boolean,
    isFavorite: Boolean,
    isWatched: Boolean,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleWatched: () -> Unit,
    onHideFromHome: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = AppleTvTokens.SurfaceRaised.copy(alpha = 0.98f),
            modifier = Modifier
                .width(360.dp)
                .border(1.5.dp, AppleTvTokens.Hairline, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Apple TV Header: Mini Poster + Başlık + Rozetler
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        AsyncImage(
                            model = resolveUrl(item.poster),
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.20f))
                                    .border(0.5.dp, AppleTvTokens.HairlineStrong, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isSeries) "DİZİ" else "FİLM",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (item.year.isNotBlank() && item.year != "0") {
                                Text(
                                    text = item.year,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = AppleTvTokens.Hairline)
                Spacer(Modifier.height(2.dp))

                TvDialogActionRow(
                    icon = Icons.Default.PlayArrow,
                    title = "Hemen Oynat",
                    isPrimary = true,
                    onClick = { onPlay(); onDismiss() }
                )
                TvDialogActionRow(
                    icon = Icons.Default.Info,
                    title = "Detayları Gör",
                    onClick = { onDetails(); onDismiss() }
                )
                TvDialogActionRow(
                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    title = if (isFavorite) "Favorilerden Çıkar" else "Favorilere Ekle",
                    iconColor = if (isFavorite) Color(0xFFFF5252) else Color.White,
                    onClick = { onToggleFavorite(); onDismiss() }
                )
                TvDialogActionRow(
                    icon = Icons.Default.Check,
                    title = if (isWatched) "İzlenmedi Olarak İşaretle" else "İzlendi Olarak İşaretle",
                    onClick = { onToggleWatched(); onDismiss() }
                )
                TvDialogActionRow(
                    icon = Icons.Default.Clear,
                    title = "Ana Sayfadan Gizle",
                    iconColor = Color(0xFFFF5252),
                    onClick = { onHideFromHome(); onDismiss() }
                )
            }
        }
    }
}

@Composable
private fun TvChannelQuickActionsDialog(
    channel: Channel,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = AppleTvTokens.SurfaceRaised.copy(alpha = 0.98f),
            modifier = Modifier
                .width(350.dp)
                .border(1.5.dp, AppleTvTokens.Hairline, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelLogo(
                        logo = channel.logo,
                        channelName = channel.name,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channel.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = channel.tvGenreTitle.ifBlank { "Canlı TV Kanalı" },
                            color = Color.White.copy(0.6f),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = AppleTvTokens.Hairline)
                Spacer(Modifier.height(2.dp))

                TvDialogActionRow(
                    icon = Icons.Default.PlayArrow,
                    title = "Kanalı Aç",
                    isPrimary = true,
                    onClick = { onPlay(); onDismiss() }
                )
                TvDialogActionRow(
                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    title = if (isFavorite) "Favorilerden Çıkar" else "Favorilere Ekle",
                    iconColor = if (isFavorite) Color(0xFFFF5252) else Color.White,
                    onClick = { onToggleFavorite(); onDismiss() }
                )
            }
        }
    }
}

@Composable
private fun TvDialogActionRow(
    icon: ImageVector,
    title: String,
    iconColor: Color = Color.White,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.03f else 1.0f, label = "act_row_scale")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .onKeyEvent { ev ->
                if (isTvSelectKey(ev)) {
                    onClick(); true
                } else false
            },
        color = when {
            isFocused -> Color.White
            isPrimary -> AppleTvTokens.SurfaceRaised
            else -> Color.White.copy(alpha = 0.05f)
        },
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) Color.Black else iconColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = if (isFocused || isPrimary) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (isFocused) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.25f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
