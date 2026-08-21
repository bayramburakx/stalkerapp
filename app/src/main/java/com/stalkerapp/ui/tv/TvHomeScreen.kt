package com.stalkerapp.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.stalkerapp.ui.components.ChannelLogo
import com.stalkerapp.ui.components.resolveUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TV kumandasında OK / Seçim tuşuna basılıp basılmadığını kontrol eder.
 * Android TV DPAD_CENTER, ENTER ve NUMPAD_ENTER tuşlarını kapsar.
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

/**
 * Android TV 10-foot UI ana ekranı.
 * D-Pad gezinme, büyük odak göstergeleri, sol gezinme çubuğu, TV için optimize edilmiş
 * Canlı TV, Film, Dizi ve Arama görünümleri ile televizyonlar için özel optimize edilmiştir.
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

    // TV açıldığında kanal ve VOD içeriklerini otomatik yükle
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

    // Seçili TV sekmesi: 0: Ana Sayfa, 1: Canlı TV, 2: Filmler, 3: Diziler, 4: Ara
    var selectedTab by remember { mutableIntStateOf(0) }

    // Ana Sayfa için Popüler Filmler (Throttled & Background compute)
    val popularMovies by produceState(
        initialValue = emptyList<VodItem>(),
        catalog.movies.size,
        catalog.allItems.size,
        catalog.status
    ) {
        value = withContext(Dispatchers.Default) {
            val list = if (catalog.movies.isNotEmpty()) catalog.movies
            else catalog.allItems.filter { !catalog.isSeriesItem(it) }
            list.filter { it.id > 0 }
                .distinctBy { it.id }
                .sortedWith(
                    compareByDescending<VodItem> {
                        it.rating.replace(',', '.').substringBefore('/').trim().toFloatOrNull() ?: 0f
                    }.thenByDescending { it.year.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
                     .thenByDescending { it.id }
                )
                .take(30)
        }
    }

    // Ana Sayfa için Popüler Diziler (Throttled & Background compute)
    val popularSeries by produceState(
        initialValue = emptyList<VodItem>(),
        catalog.series.size,
        catalog.allItems.size,
        catalog.status
    ) {
        value = withContext(Dispatchers.Default) {
            val list = if (catalog.series.isNotEmpty()) catalog.series
            else catalog.allItems.filter { catalog.isSeriesItem(it) }
            list.filter { it.id > 0 }
                .distinctBy { it.id }
                .sortedWith(
                    compareByDescending<VodItem> {
                        it.rating.replace(',', '.').substringBefore('/').trim().toFloatOrNull() ?: 0f
                    }.thenByDescending { it.year.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
                     .thenByDescending { it.id }
                )
                .take(30)
        }
    }

    val liveChannels = remember(homeChannels, favChannels) {
        val all = (favChannels + (homeChannels ?: emptyList())).filter { it.id > 0 }.distinctBy { it.id }
        all.take(30)
    }

    val recentChannelList = remember(recentChannels) {
        recentChannels.filter { it.id > 0 }.distinctBy { it.id }.take(20)
    }

    // İzlemeye devam listesi
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

    val firstItemFocusRequester = remember { FocusRequester() }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D14))
            .statusBarsPadding()
    ) {
        // ==========================================
        // 1. SOL DİKEY MENÜ (TV NAVIGATION RAIL)
        // ==========================================
        Column(
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
                .background(Color(0xFF0F111A))
                .border(
                    width = 1.dp,
                    color = Color(0xFF1E293B).copy(alpha = 0.6f),
                    shape = RoundedCornerShape(0.dp)
                )
                .padding(horizontal = 12.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Portio Logo + Aktif Kaynak Etiketi
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    "Portio",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontSize = 22.sp
                )
                Spacer(Modifier.width(8.dp))
                val sourceBadge = when (activeKind) {
                    "m3u" -> "M3U"
                    "xtream" -> "Xtream"
                    else -> "Stalker"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        sourceBadge,
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Ana TV Navigasyon Butonları
            TvNavRailItem(
                icon = Icons.Default.Home,
                label = "Ana Sayfa",
                selected = selectedTab == 0,
                focusRequester = if (selectedTab == 0) firstItemFocusRequester else null,
                onClick = { selectedTab = 0 }
            )
            Spacer(Modifier.height(6.dp))
            TvNavRailItem(
                icon = Icons.Default.LiveTv,
                label = "Canlı TV",
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 }
            )
            Spacer(Modifier.height(6.dp))
            TvNavRailItem(
                icon = Icons.Default.Movie,
                label = "Filmler",
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 }
            )
            Spacer(Modifier.height(6.dp))
            TvNavRailItem(
                icon = Icons.Default.VideoLibrary,
                label = "Diziler",
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 }
            )
            Spacer(Modifier.height(6.dp))
            TvNavRailItem(
                icon = Icons.Default.Search,
                label = "Ara",
                selected = selectedTab == 4,
                onClick = { selectedTab = 4 }
            )

            Spacer(Modifier.weight(1f))

            // Alt Menü: Profil & Ayarlar
            TvNavRailItem(
                icon = Icons.Default.AccountCircle,
                label = "Profil",
                selected = false,
                onClick = onOpenProfiles
            )
            Spacer(Modifier.height(6.dp))
            TvNavRailItem(
                icon = Icons.Default.Settings,
                label = "Ayarlar",
                selected = false,
                onClick = onOpenSettings
            )
        }

        // ==========================================
        // 2. SAĞ İÇERİK ALANI (TV NATIVE VIEWS)
        // ==========================================
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF0C0D14))
        ) {
            // Senkronizasyon Durum Çubuğu
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
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF131A2A))
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Katalog yükleniyor… (%$pct)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Medium
                        )
                        LinearProgressIndicator(
                            progress = { ratio.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .width(120.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFF38BDF8),
                            trackColor = Color(0xFF1E293B)
                        )
                    }
                }
            }

            // TV Sekme İçerikleri
            when (selectedTab) {
                1 -> {
                    // CANLI TV - TV NATIVE
                    TvLiveSection(
                        vm = vm,
                        onOpenChannel = onOpenChannel
                    )
                }
                2 -> {
                    // FİLMLER - TV NATIVE
                    TvVodSection(
                        catalog = catalog,
                        isSeries = false,
                        tmdbApiKey = settings.tmdbApiKey,
                        onOpenVod = { id -> onOpenVod(id, false) }
                    )
                }
                3 -> {
                    // DİZİLER - TV NATIVE
                    TvVodSection(
                        catalog = catalog,
                        isSeries = true,
                        tmdbApiKey = settings.tmdbApiKey,
                        onOpenVod = { id -> onOpenVod(id, true) }
                    )
                }
                4 -> {
                    // ARAMA - TV NATIVE
                    TvSearchSection(
                        vm = vm,
                        catalog = catalog,
                        onOpenChannel = onOpenChannel,
                        onOpenVod = onOpenVod
                    )
                }
                else -> {
                    // ANA SAYFA DASHBOARD
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // İzlemeye Devam
                        if (continueWatching.isNotEmpty()) {
                            item(key = "section_cw") {
                                TvSection(title = "İzlemeye Devam Et") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(continueWatching, key = { "cw_${it.id}" }) { item ->
                                            TvVodCard(
                                                item = item,
                                                tmdbApiKey = settings.tmdbApiKey,
                                                onClick = { onOpenVod(item.id, catalog.isSeriesItem(item)) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Canlı TV Kanalları
                        if (liveChannels.isNotEmpty()) {
                            item(key = "section_live") {
                                TvSection(title = "Canlı TV Kanalları") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(liveChannels, key = { "live_${it.id}" }) { channel ->
                                            TvChannelCard(
                                                channel = channel,
                                                onClick = { onOpenChannel(channel) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Popüler Filmler
                        if (popularMovies.isNotEmpty()) {
                            item(key = "section_movies") {
                                TvSection(title = "Popüler Filmler") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(popularMovies, key = { "mov_${it.id}" }) { item ->
                                            TvVodCard(
                                                item = item,
                                                tmdbApiKey = settings.tmdbApiKey,
                                                onClick = { onOpenVod(item.id, false) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Popüler Diziler
                        if (popularSeries.isNotEmpty()) {
                            item(key = "section_series") {
                                TvSection(title = "Popüler Diziler") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(popularSeries, key = { "ser_${it.id}" }) { item ->
                                            TvVodCard(
                                                item = item,
                                                tmdbApiKey = settings.tmdbApiKey,
                                                onClick = { onOpenVod(item.id, true) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Son İzlenen Kanallar
                        if (recentChannelList.isNotEmpty()) {
                            item(key = "section_recent") {
                                TvSection(title = "Son İzlenen Kanallar") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(recentChannelList, key = { "recent_${it.id}" }) { channel ->
                                            TvChannelCard(
                                                channel = channel,
                                                onClick = { onOpenChannel(channel) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Boş Durum
                        if (popularMovies.isEmpty() && popularSeries.isEmpty() && liveChannels.isEmpty()) {
                            item(key = "section_empty") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.Tv,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.4f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            if (catalog.status == VodCatalogStatus.Syncing) "İçerikler yükleniyor, lütfen bekleyin…"
                                            else "İçerik bulunamadı. Ayarlar bölümünden kaynak ekleyin.",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 15.sp
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

    // İlk odak isteği (TV kumandası için)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        runCatching { firstItemFocusRequester.requestFocus() }
    }
}

// =========================================================================
// TV CANLI TV GÖRÜNÜMÜ (Hızlı 2 Panelli D-Pad Gezinme)
// =========================================================================
@Composable
private fun TvLiveSection(
    vm: MainViewModel,
    onOpenChannel: (Channel) -> Unit
) {
    val profile = vm.repository.cachedProfile()
    var genres by remember { mutableStateOf<List<Genre>?>(null) }
    var allChannels by remember { mutableStateOf<List<Channel>?>(null) }
    var selectedGenreId by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(profile, vm.activeSourceKind(), vm.activeSourceId()) {
        loading = true
        error = null
        try {
            val loaded = vm.loadChannelsForActiveSource(profile)
            if (loaded != null) {
                genres = loaded.first
                allChannels = loaded.second
            } else {
                error = "Kanal listesi yüklenemedi."
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF00E5FF), strokeWidth = 3.dp)
        }
        return
    }

    if (error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(error ?: "Hata oluştu", color = Color.White.copy(alpha = 0.7f))
        }
        return
    }

    val channelList = allChannels ?: emptyList()
    val genreList = genres ?: emptyList()

    val filteredChannels = remember(channelList, selectedGenreId) {
        if (selectedGenreId == 0L) channelList
        else channelList.filter { it.tvGenreId == selectedGenreId }
    }

    Row(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Sol Kategori Listesi
        LazyColumn(
            modifier = Modifier
                .width(180.dp)
                .fillMaxHeight()
                .background(Color(0xFF131722), RoundedCornerShape(10.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                TvCategoryItem(
                    title = "Tümü (${channelList.size})",
                    selected = selectedGenreId == 0L,
                    onClick = { selectedGenreId = 0L }
                )
            }
            items(genreList, key = { it.id }) { g ->
                val count = remember(channelList, g.id) {
                    channelList.count { it.tvGenreId == g.id }
                }
                TvCategoryItem(
                    title = "${g.title} ($count)",
                    selected = selectedGenreId == g.id,
                    onClick = { selectedGenreId = g.id }
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Sağ Kanal Listesi / Izgarası
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredChannels, key = { it.id }) { ch ->
                TvChannelGridCard(
                    channel = ch,
                    onClick = { onOpenChannel(ch) }
                )
            }
        }
    }
}

// =========================================================================
// TV VOD GÖRÜNÜMÜ (Film & Dizi - Hızlı ve Sayfalı)
// =========================================================================
@Composable
private fun TvVodSection(
    catalog: VodCatalogState,
    isSeries: Boolean,
    tmdbApiKey: String,
    onOpenVod: (Long) -> Unit
) {
    val items = if (isSeries) {
        if (catalog.series.isNotEmpty()) catalog.series
        else catalog.allItems.filter { catalog.isSeriesItem(it) }
    } else {
        if (catalog.movies.isNotEmpty()) catalog.movies
        else catalog.allItems.filter { !catalog.isSeriesItem(it) }
    }

    val cats = remember(catalog.categories, isSeries) {
        catalog.categories.filter { c ->
            val isS = com.stalkerapp.data.ExternalVod.isSeriesCat(c.id) ||
                VodCatalogState.isSeriesCatTitle(c.title)
            if (isSeries) isS else !isS
        }
    }

    var selectedCatId by remember { mutableStateOf(0L) }
    var visibleLimit by remember { mutableIntStateOf(40) }

    val filtered = remember(items, selectedCatId) {
        if (selectedCatId == 0L) items
        else items.filter { it.categoryId == selectedCatId }
    }

    val displayedItems = remember(filtered, visibleLimit) {
        filtered.take(visibleLimit)
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Üst Kategori Şeridi
        if (cats.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    TvFilterChip(
                        title = "Tümü (${items.size})",
                        selected = selectedCatId == 0L,
                        onClick = {
                            selectedCatId = 0L
                            visibleLimit = 40
                        }
                    )
                }
                items(cats, key = { it.id }) { cat ->
                    TvFilterChip(
                        title = cat.title,
                        selected = selectedCatId == cat.id,
                        onClick = {
                            selectedCatId = cat.id
                            visibleLimit = 40
                        }
                    )
                }
            }
        }

        // VOD Poster Grid
        val gridState = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(displayedItems, key = { it.id }) { item ->
                TvVodCard(
                    item = item,
                    tmdbApiKey = tmdbApiKey,
                    onClick = { onOpenVod(item.id) }
                )
            }
            if (displayedItems.size < filtered.size) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF131722))
                            .clickable { visibleLimit += 40 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Daha fazla yükle…", color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =========================================================================
// TV ARAMA GÖRÜNÜMÜ (Placeholder Üzerinde Klavye Açmayan TV Arama)
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

    val profile = vm.repository.cachedProfile()
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
        value = withContext(Dispatchers.Default) {
            val chMatches = (homeChannels ?: emptyList()).filter { it.name.contains(q, ignoreCase = true) }.take(20)
            val movMatches = catalog.allItems.filter { !catalog.isSeriesItem(it) && it.name.contains(q, ignoreCase = true) }.take(20)
            val serMatches = catalog.allItems.filter { catalog.isSeriesItem(it) && it.name.contains(q, ignoreCase = true) }.take(20)
            Triple(chMatches, movMatches, serMatches)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // TV Arama Butonu / Kartı (Klavye yalnızca basınca / OK deyince açılır)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (searchFocused) Color(0xFF1E293B) else Color(0xFF131722))
                .border(
                    width = if (searchFocused) 3.dp else 1.dp,
                    color = if (searchFocused) Color(0xFF00E5FF) else Color.White.copy(0.12f),
                    shape = RoundedCornerShape(12.dp)
                )
                .onFocusChanged { searchFocused = it.isFocused }
                .focusable()
                .clickable { isInputDialogOpen = true }
                .onKeyEvent { ev ->
                    if (isTvSelectKey(ev)) {
                        isInputDialogOpen = true
                        true
                    } else false
                }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null, tint = if (searchFocused) Color(0xFF00E5FF) else Color.White.copy(0.7f))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (query.isNotBlank()) query else "Film, dizi veya kanal ara… (Seçmek için Kumandadan OK'a basın)",
                    color = if (query.isNotBlank()) Color.White else Color.White.copy(0.5f),
                    fontSize = 15.sp,
                    fontWeight = if (query.isNotBlank()) FontWeight.Bold else FontWeight.Normal
                )
                if (query.isNotBlank()) {
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { query = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = "Temizle", tint = Color.White.copy(0.6f))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Arama Sonuçları
        val (chResults, movResults, serResults) = searchResults
        if (query.isNotBlank()) {
            if (chResults.isEmpty() && movResults.isEmpty() && serResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sonuç bulunamadı: \"$query\"", color = Color.White.copy(0.6f), fontSize = 15.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    if (chResults.isNotEmpty()) {
                        item {
                            TvSection(title = "Kanallar (${chResults.size})") {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(chResults, key = { "sr_ch_${it.id}" }) { ch ->
                                        TvChannelCard(channel = ch, onClick = { onOpenChannel(ch) })
                                    }
                                }
                            }
                        }
                    }
                    if (movResults.isNotEmpty()) {
                        item {
                            TvSection(title = "Filmler (${movResults.size})") {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(movResults, key = { "sr_mov_${it.id}" }) { mov ->
                                        TvVodCard(item = mov, onClick = { onOpenVod(mov.id, false) })
                                    }
                                }
                            }
                        }
                    }
                    if (serResults.isNotEmpty()) {
                        item {
                            TvSection(title = "Diziler (${serResults.size})") {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(serResults, key = { "sr_ser_${it.id}" }) { ser ->
                                        TvVodCard(item = ser, onClick = { onOpenVod(ser.id, true) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // TV Klavye Giriş Modalı (Yalnızca tıklandığında açılır)
    if (isInputDialogOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { isInputDialogOpen = false },
            title = { Text("Arama Yapın", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                var tempText by remember { mutableStateOf(query) }
                val focusReq = remember { FocusRequester() }
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = tempText,
                        onValueChange = { tempText = it; query = it },
                        singleLine = true,
                        placeholder = { Text("Aramak istediğiniz adı yazın…") },
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
                androidx.compose.material3.TextButton(onClick = { isInputDialogOpen = false }) {
                    Text("Ara", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    query = ""
                    isInputDialogOpen = false
                }) {
                    Text("Temizle", color = Color.White.copy(0.6f))
                }
            },
            containerColor = Color(0xFF131722),
            shape = RoundedCornerShape(14.dp)
        )
    }
}

// =========================================================================
// TV YARDIMCI BİLEŞENLER (Odak / D-Pad Optimize)
// =========================================================================

@Composable
private fun TvNavRailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.05f else 1.0f, label = "rail_scale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    focused -> Color(0xFF00E5FF)
                    selected -> Color(0xFF1E293B)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (focused) 2.5.dp else if (selected) 1.dp else 0.dp,
                color = if (focused) Color.White else if (selected) Color(0xFF00E5FF).copy(0.4f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
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
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (focused) Color.Black else if (selected) Color(0xFF38BDF8) else Color.White.copy(0.8f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                color = if (focused) Color.Black else if (selected) Color.White else Color.White.copy(0.85f),
                fontSize = 13.sp,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TvCategoryItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    focused -> Color(0xFF00E5FF)
                    selected -> Color(0xFF1E293B)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onKeyEvent { ev ->
                if (isTvSelectKey(ev)) {
                    onClick(); true
                } else false
            }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            title,
            color = if (focused) Color.Black else if (selected) Color(0xFF38BDF8) else Color.White.copy(0.8f),
            fontSize = 12.sp,
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvFilterChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    focused -> Color(0xFF00E5FF)
                    selected -> Color(0xFF1E293B)
                    else -> Color(0xFF131722)
                }
            )
            .border(
                width = if (focused) 2.5.dp else if (selected) 1.dp else 1.dp,
                color = if (focused) Color.White else if (selected) Color(0xFF00E5FF).copy(0.5f) else Color.White.copy(0.08f),
                shape = RoundedCornerShape(20.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onKeyEvent { ev ->
                if (isTvSelectKey(ev)) {
                    onClick(); true
                } else false
            }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            title,
            color = if (focused) Color.Black else if (selected) Color(0xFF38BDF8) else Color.White.copy(0.8f),
            fontSize = 12.sp,
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun TvSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            fontSize = 18.sp
        )
        content()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TvChannelCard(
    channel: Channel,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.10f else 1.0f, label = "ch_scale")

    Box(
        modifier = Modifier
            .size(125.dp, 80.dp)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color(0xFF1E293B) else Color(0xFF131722))
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Color(0xFF00E5FF) else Color.White.copy(0.08f),
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
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
        contentAlignment = Alignment.Center
    ) {
        ChannelLogo(
            logo = channel.logo,
            modifier = Modifier.size(80.dp, 48.dp)
        )
        if (focused) {
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

@Composable
private fun TvChannelGridCard(
    channel: Channel,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.06f else 1.0f, label = "ch_grid_scale")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color(0xFF1E293B) else Color(0xFF131722))
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Color(0xFF00E5FF) else Color.White.copy(0.08f),
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onKeyEvent { ev ->
                if (isTvSelectKey(ev)) {
                    onClick(); true
                } else false
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelLogo(
            logo = channel.logo,
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
                    color = Color(0xFF38BDF8),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TvVodCard(
    item: VodItem,
    tmdbApiKey: String = "",
    onClick: () -> Unit
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.08f else 1.0f, label = "vod_scale")

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

    Column(modifier = Modifier.width(130.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .scale(scale)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF131722))
                .border(
                    width = if (focused) 3.dp else 0.dp,
                    color = if (focused) Color(0xFF00E5FF) else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                )
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .onKeyEvent { ev ->
                    if (isTvSelectKey(ev)) {
                        onClick(); true
                    } else false
                }
        ) {
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
                        .padding(4.dp)
                        .background(Color(0xFFE50914), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
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
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("★ $ratingText", color = Color(0xFFFFC107), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            item.name,
            color = Color.White.copy(if (focused) 1f else 0.8f),
            fontSize = 12.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
