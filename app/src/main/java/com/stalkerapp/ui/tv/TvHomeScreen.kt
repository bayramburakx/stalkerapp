package com.stalkerapp.ui.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.stalkerapp.data.VodItem
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.VodCatalogStatus
import com.stalkerapp.ui.components.ChannelLogo
import com.stalkerapp.ui.components.resolveUrl
import com.stalkerapp.ui.live.LiveTvScreen
import com.stalkerapp.ui.vod.VodScreen

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
 * Android TV 10-foot UI ana ekranı.
 * D-Pad gezinme, büyük odak göstergeleri, üst sekme çubuğu, otomatik içerik yükleme
 * ve TMDB kapak entegrasyonu ile televizyonlar için özel optimize edilmiştir.
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

    // Seçili TV sekmesi: 0: Ana Sayfa, 1: Canlı TV, 2: Filmler, 3: Diziler
    var selectedTab by remember { mutableIntStateOf(0) }

    val moviesList = remember(catalog) {
        if (catalog.movies.isNotEmpty()) catalog.movies
        else catalog.allItems.filter { !catalog.isSeriesItem(it) }
    }
    val seriesList = remember(catalog) {
        if (catalog.series.isNotEmpty()) catalog.series
        else catalog.allItems.filter { catalog.isSeriesItem(it) }
    }

    val popularMovies = remember(moviesList) {
        moviesList.sortedByDescending {
            it.rating.replace(',', '.').substringBefore('/').trim().toFloatOrNull() ?: 0f
        }.take(30)
    }
    val popularSeries = remember(seriesList) {
        seriesList.sortedByDescending {
            it.rating.replace(',', '.').substringBefore('/').trim().toFloatOrNull() ?: 0f
        }.take(30)
    }

    val liveChannels = remember(homeChannels, favChannels) {
        val all = (favChannels + (homeChannels ?: emptyList())).distinctBy { it.id }
        all.take(40)
    }

    // İzlemeye devam listesi
    val watchedVersion by vm.watchedVersion.collectAsStateWithLifecycle()
    val curSourceKey = app.store.activeSourceKey()
    val continueWatching = remember(catalog, watchedVersion, curSourceKey) {
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
        (vodProg + epProg).distinctBy { it.id }.take(20)
    }

    val firstItemFocusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D14))
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ---------- TV Üst Gezinme Çubuğu ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Portio Logo + Aktif Kaynak Etiketi
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Portio",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 28.sp
                    )
                    Spacer(Modifier.width(10.dp))
                    val sourceBadge = when (activeKind) {
                        "m3u" -> "M3U"
                        "xtream" -> "Xtream"
                        else -> "Stalker"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            sourceBadge,
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // TV Sekmeleri
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvNavTab(
                        icon = Icons.Default.Home,
                        label = "Ana Sayfa",
                        selected = selectedTab == 0,
                        focusRequester = if (selectedTab == 0) firstItemFocusRequester else null,
                        onClick = { selectedTab = 0 }
                    )
                    TvNavTab(
                        icon = Icons.Default.LiveTv,
                        label = "Canlı TV",
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                    TvNavTab(
                        icon = Icons.Default.Movie,
                        label = "Filmler",
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 }
                    )
                    TvNavTab(
                        icon = Icons.Default.VideoLibrary,
                        label = "Diziler",
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 }
                    )
                    TvNavTab(
                        icon = Icons.Default.Search,
                        label = "Ara",
                        selected = false,
                        onClick = onOpenSearch
                    )
                    TvNavTab(
                        icon = Icons.Default.AccountCircle,
                        label = "Profil",
                        selected = false,
                        onClick = onOpenProfiles
                    )
                    TvNavTab(
                        icon = Icons.Default.Settings,
                        label = "Ayarlar",
                        selected = false,
                        onClick = onOpenSettings
                    )
                }
            }

            // ---- Kaynak Yükleme İlerleme Çubuğu (TV) ----
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
                        .padding(horizontal = 36.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF131A2A))
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Kaynaklar yükleniyor…",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                "%$pct",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { ratio.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF38BDF8),
                            trackColor = Color(0xFF1E293B)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${catalog.loadedCount} içerik yüklendi • ${catalog.doneCategories}/${catalog.totalCategories} kategori",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // ---------- TV İçerik Alanı ----------
            when (selectedTab) {
                1 -> {
                    // Canlı TV Tam Ekran
                    LiveTvScreen(
                        profile = profile,
                        onOpenPlayer = onOpenPlayer,
                        onOpenGuide = onOpenGuide,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                    )
                }
                2 -> {
                    // Filmler Tam Ekran
                    VodScreen(
                        profile = profile,
                        onOpenVod = onOpenVod,
                        filterIsSeries = false,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                    )
                }
                3 -> {
                    // Diziler Tam Ekran
                    VodScreen(
                        profile = profile,
                        onOpenVod = onOpenVod,
                        filterIsSeries = true,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                    )
                }
                else -> {
                    // Ana Sayfa Dashboard (D-Pad kaydırılabilir)
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(bottom = 32.dp)
                    ) {
                        // İzlemeye Devam
                        if (continueWatching.isNotEmpty()) {
                            TvSection(title = "İzlemeye Devam Et") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 36.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(continueWatching, key = { it.id }) { item ->
                                        TvVodCard(
                                            item = item,
                                            tmdbApiKey = settings.tmdbApiKey,
                                            onClick = { onOpenVod(item.id, catalog.isSeriesItem(item)) }
                                        )
                                    }
                                }
                            }
                        }

                        // Canlı TV Kanalları
                        if (liveChannels.isNotEmpty()) {
                            TvSection(title = "Canlı TV Kanalları") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 36.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(liveChannels, key = { it.id }) { channel ->
                                        TvChannelCard(
                                            channel = channel,
                                            onClick = { onOpenChannel(channel) }
                                        )
                                    }
                                }
                            }
                        }

                        // Popüler Filmler
                        if (popularMovies.isNotEmpty()) {
                            TvSection(title = "Filmler") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 36.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(popularMovies, key = { it.id }) { movie ->
                                        TvVodCard(
                                            item = movie,
                                            tmdbApiKey = settings.tmdbApiKey,
                                            onClick = { onOpenVod(movie.id, false) }
                                        )
                                    }
                                }
                            }
                        }

                        // Popüler Diziler
                        if (popularSeries.isNotEmpty()) {
                            TvSection(title = "Diziler") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 36.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(popularSeries, key = { it.id }) { series ->
                                        TvVodCard(
                                            item = series,
                                            tmdbApiKey = settings.tmdbApiKey,
                                            onClick = { onOpenVod(series.id, true) }
                                        )
                                    }
                                }
                            }
                        }

                        // Son İzlenen Kanallar
                        if (recentChannels.isNotEmpty()) {
                            TvSection(title = "Son İzlenen Kanallar") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 36.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(recentChannels, key = { it.id }) { channel ->
                                        TvChannelCard(
                                            channel = channel,
                                            onClick = { onOpenChannel(channel) }
                                        )
                                    }
                                }
                            }
                        }

                        // Henüz içerik yoksa veya yükleniyorsa bilgilendirme
                        if (popularMovies.isEmpty() && popularSeries.isEmpty() && liveChannels.isEmpty()) {
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
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // İlk odak
    LaunchedEffect(Unit) {
        runCatching { firstItemFocusRequester.requestFocus() }
    }
}

@Composable
private fun TvNavTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    focused -> Color(0xFF38BDF8)
                    selected -> Color(0xFF1E293B)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .onKeyEvent { ev ->
                if (isTvSelectKey(ev)) {
                    onClick(); true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (focused) Color.Black else if (selected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                color = if (focused) Color.Black else if (selected) Color.White else Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium
            )
        }
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
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 36.dp, vertical = 8.dp),
            fontSize = 20.sp
        )
        content()
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun TvChannelCard(
    channel: Channel,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(130.dp, 84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color(0xFF1E293B) else Color(0xFF131722))
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Color(0xFF38BDF8) else Color.White.copy(0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .onKeyEvent { ev ->
                if (isTvSelectKey(ev)) {
                    onClick(); true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        ChannelLogo(
            logo = channel.logo,
            modifier = Modifier.size(84.dp, 52.dp)
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
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(4.dp)
            )
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

    var resolvedPoster by remember(item.id, item.poster) {
        mutableStateOf(app.tmdb.getCachedPoster(item.name, item.isSeries) ?: item.poster)
    }

    LaunchedEffect(item.name, item.poster, item.year, item.isSeries, tmdbApiKey) {
        if (tmdbApiKey.isNotBlank()) {
            val p = app.tmdb.resolvePoster(item.name, item.year, item.isSeries, item.poster, tmdbApiKey)
            if (p.isNotBlank()) {
                resolvedPoster = p
            }
        }
    }

    Column(
        modifier = Modifier.width(135.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF131722))
                .border(
                    width = if (focused) 3.dp else 0.dp,
                    color = if (focused) Color(0xFF38BDF8) else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                )
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .clickable(onClick = onClick)
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
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("DİZİ", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
