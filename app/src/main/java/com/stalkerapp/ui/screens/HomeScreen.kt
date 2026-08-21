package com.stalkerapp.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stalkerapp.R
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.Profile
import com.stalkerapp.data.VodItem
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.components.AdultPinDialog
import com.stalkerapp.ui.components.ChannelLogo
import com.stalkerapp.ui.components.ChannelRow
import com.stalkerapp.ui.components.NavigationTabItem
import com.stalkerapp.ui.components.PortioBadge
import com.stalkerapp.ui.components.PortioBottomNavBar
import com.stalkerapp.ui.components.PortioButton
import com.stalkerapp.ui.components.PortioButtonStyle
import com.stalkerapp.ui.components.PortioCard
import com.stalkerapp.ui.components.PortioMediaCard
import com.stalkerapp.ui.components.PortioPrimaryButton
import com.stalkerapp.ui.components.PortioProgressBar
import com.stalkerapp.ui.components.PortioSecondaryButton
import com.stalkerapp.ui.components.PortioSideNavRail
import com.stalkerapp.ui.components.RatingBadge
import com.stalkerapp.ui.components.SectionTitle
import com.stalkerapp.ui.components.resolveUrl
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.ui.theme.PortioShape
import com.stalkerapp.ui.tv.isTvDevice
import com.stalkerapp.ui.tv.isTvSelectKey
import com.stalkerapp.util.L10n
import kotlinx.coroutines.delay

/**
 * Portio Ana Ekranı (HomeScreen) - Portio Tasarım Sistemine Dayalı Zengin Dashboard
 */
@Composable
fun HomeScreen(
    onOpenPlayer: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onOpenOnboarding: () -> Unit = {},
    onOpenProfiles: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    var profile by remember { mutableStateOf(vm.repository.cachedProfile()) }
    val lang = vm.store.settings().language

    val isTv = remember {
        val pref = app.store.settings().preferredLayout
        if (pref == "tv") true else if (pref == "mobile") false else isTvDevice(context)
    }

    var tab by remember {
        mutableIntStateOf(vm.store.settings().defaultTab.coerceIn(0, 4))
    }
    val gotoTab: (Int) -> Unit = { tab = it }

    LaunchedEffect(Unit) {
        vm.resumeLastLiveChannelIfEnabled(profile)
    }

    val settings by vm.settings.collectAsStateWithLifecycle()
    val adultUnlocked by vm.adultUnlocked.collectAsStateWithLifecycle()
    var showAdultPin by remember { mutableStateOf(false) }

    LaunchedEffect(settings.adultContentEnabled, settings.lockAdultWithPin, adultUnlocked) {
        if (settings.adultContentEnabled && settings.lockAdultWithPin && !adultUnlocked) {
            showAdultPin = true
        }
    }

    if (showAdultPin) {
        AdultPinDialog(
            lang = lang,
            onUnlock = { pin ->
                if (vm.unlockAdult(pin)) {
                    showAdultPin = false
                    true
                } else false
            },
            onDismiss = { showAdultPin = false }
        )
    }

    val saveableStateHolder = rememberSaveableStateHolder()

    val navItems = listOf(
        NavigationTabItem(Icons.Default.Home, L10n.t(lang, "Ana Sayfa")),
        NavigationTabItem(Icons.Default.LiveTv, L10n.t(lang, "Canlı TV")),
        NavigationTabItem(Icons.Default.Movie, L10n.t(lang, "Filmler")),
        NavigationTabItem(Icons.Default.VideoLibrary, L10n.t(lang, "Diziler")),
        NavigationTabItem(Icons.Default.Download, L10n.t(lang, "İndirilenler")),
        NavigationTabItem(Icons.Default.Settings, L10n.t(lang, "Ayarlar")),
        NavigationTabItem(Icons.Default.Search, L10n.t(lang, "Ara"), onClick = onOpenSearch)
    )

    val portalKey = when (vm.activeSourceKind()) {
        "m3u", "xtream" -> "${vm.activeSourceKind()}:${vm.activeSourceId() ?: "none"}"
        else -> profile?.portal?.id ?: "none"
    }

    if (isTv) {
        // Android TV & Büyük Ekran Düzeni: Yan Menü (SideNavRail) + İçerik
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(PortioColors.Background)
        ) {
            PortioSideNavRail(
                items = navItems,
                selectedTabIndex = tab,
                onTabSelected = { tab = it },
                header = {
                    Image(
                        painter = painterResource(R.drawable.portio_logo),
                        contentDescription = "Portio",
                        modifier = Modifier
                            .size(42.dp)
                            .clip(PortioShape.Medium)
                            .clickable { onOpenProfiles() }
                    )
                }
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                saveableStateHolder.SaveableStateProvider("$tab:$portalKey") {
                    when (tab) {
                        0 -> PortioHomeDashboard(
                            profile = profile,
                            vm = vm,
                            onOpenVod = onOpenVod,
                            onOpenPlayer = onOpenPlayer,
                            onOpenSearch = onOpenSearch,
                            onOpenProfiles = onOpenProfiles,
                            onGotoTab = gotoTab,
                            lang = lang
                        )
                        1 -> LiveTvScreen(profile, onOpenPlayer, Modifier.fillMaxSize(), onOpenGuide = onOpenGuide)
                        2 -> MoviesScreen(profile, onOpenVod, Modifier.fillMaxSize())
                        3 -> SeriesScreen(profile, onOpenVod, Modifier.fillMaxSize())
                        4 -> DownloadsScreen(
                            onPlayOffline = { entry ->
                                val playUrl = com.stalkerapp.data.OfflineDownloadManager.getPlayableOfflineUrl(entry)
                                PlaybackManager.playOffline(playUrl, entry.title, entry.poster, entry.episodeLabel)
                                onOpenPlayer()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        5 -> SettingsScreen(
                            vm = vm,
                            modifier = Modifier.fillMaxSize(),
                            onPortalsChanged = {
                                val p = vm.repository.cachedProfile()
                                profile = p
                                if (p != null) vm.syncVodIfNeeded(p)
                            },
                            onOpenLibrary = { tab = 6 },
                            onOpenPlayer = onOpenPlayer,
                            onBack = { gotoTab(0) },
                            onRestartSetup = onOpenOnboarding,
                            onOpenProfiles = onOpenProfiles
                        )
                        6 -> LibraryScreen(profile, onOpenPlayer, onOpenVod, Modifier.fillMaxSize())
                    }
                }
            }
        }
    } else {
        // Mobil Telefon & Tablet Düzeni: Yüzen Cam Alt Menü (BottomNavBar)
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                PortioBottomNavBar(
                    items = navItems,
                    selectedTabIndex = tab,
                    onTabSelected = { tab = it }
                )
            }
        ) { padding ->
            val contentModifier = Modifier
                .fillMaxSize()
                .background(PortioColors.Background)
                .padding(top = padding.calculateTopPadding())

            saveableStateHolder.SaveableStateProvider("$tab:$portalKey") {
                when (tab) {
                    0 -> PortioHomeDashboard(
                        profile = profile,
                        vm = vm,
                        onOpenVod = onOpenVod,
                        onOpenPlayer = onOpenPlayer,
                        onOpenSearch = onOpenSearch,
                        onOpenProfiles = onOpenProfiles,
                        onGotoTab = gotoTab,
                        lang = lang,
                        modifier = contentModifier
                    )
                    1 -> LiveTvScreen(profile, onOpenPlayer, contentModifier.statusBarsPadding(), onOpenGuide = onOpenGuide)
                    2 -> MoviesScreen(profile, onOpenVod, contentModifier.statusBarsPadding())
                    3 -> SeriesScreen(profile, onOpenVod, contentModifier.statusBarsPadding())
                    4 -> DownloadsScreen(
                        onPlayOffline = { entry ->
                            val playUrl = com.stalkerapp.data.OfflineDownloadManager.getPlayableOfflineUrl(entry)
                            PlaybackManager.playOffline(playUrl, entry.title, entry.poster, entry.episodeLabel)
                            onOpenPlayer()
                        },
                        modifier = contentModifier.statusBarsPadding()
                    )
                    5 -> SettingsScreen(
                        vm = vm,
                        modifier = contentModifier.statusBarsPadding(),
                        onPortalsChanged = {
                            val p = vm.repository.cachedProfile()
                            profile = p
                            if (p != null) vm.syncVodIfNeeded(p)
                        },
                        onOpenLibrary = { tab = 6 },
                        onOpenPlayer = onOpenPlayer,
                        onBack = { gotoTab(0) },
                        onRestartSetup = onOpenOnboarding,
                        onOpenProfiles = onOpenProfiles
                    )
                    6 -> LibraryScreen(profile, onOpenPlayer, onOpenVod, contentModifier.statusBarsPadding())
                }
            }
        }
    }
}

/**
 * Portio Ana Sayfa Dashboard Paneli
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PortioHomeDashboard(
    profile: Profile?,
    vm: MainViewModel,
    onOpenVod: (Long, Boolean) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenProfiles: () -> Unit,
    onGotoTab: (Int) -> Unit,
    lang: String,
    modifier: Modifier = Modifier
) {
    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()
    val favChannels by vm.favoriteChannels.collectAsStateWithLifecycle()
    val userProfile by vm.userProfile.collectAsStateWithLifecycle()
    val watchedVersion by vm.watchedVersion.collectAsStateWithLifecycle()
    val activeKind = vm.activeSourceKind()
    val baseUrl = profile?.baseUrl.orEmpty()

    val vodProgress = remember(watchedVersion) { vm.store.loadVodProgress() }
    val byId = remember(catalog.allItems) { catalog.allItems.associateBy { it.id } }

    val continueItems = remember(vodProgress, byId) {
        vodProgress.filter { (_, p) ->
            p.durationMs > 0 && p.positionMs > 0 && p.positionMs < p.durationMs * 0.85
        }.keys.mapNotNull { byId[it] ?: vodProgress[it]?.toVodItem(it) }
            .distinctBy { it.id }
            .sortedByDescending { vodProgress[it.id]?.lastUpdated ?: 0L }
            .take(15)
    }

    val featuredItems = remember(catalog.allItems) {
        if (catalog.allItems.isNotEmpty()) {
            catalog.allItems
                .filter { it.poster.isNotBlank() && it.rating.isNotBlank() }
                .take(6)
                .ifEmpty { catalog.allItems.take(5) }
        } else emptyList()
    }

    val trendingMovies = remember(catalog.movies, catalog.allItems) {
        val list = if (catalog.movies.isNotEmpty()) catalog.movies else catalog.allItems.filter { !catalog.isSeriesItem(it) }
        list.take(20)
    }

    val popularSeries = remember(catalog.series, catalog.allItems) {
        val list = if (catalog.series.isNotEmpty()) catalog.series else catalog.allItems.filter { catalog.isSeriesItem(it) }
        list.take(20)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        // 1. Üst Başlık Çubuğu: Logo + Portio + Kaynak Rozeti + Arama + Profil
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.portio_logo),
                    contentDescription = "Portio Logo",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(PortioShape.Medium)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Portio",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        PortioBadge(
                            text = when (activeKind) {
                                "xtream" -> "XTREAM"
                                "m3u" -> "M3U"
                                else -> "STALKER"
                            },
                            backgroundColor = when (activeKind) {
                                "xtream" -> PortioColors.AccentPurple
                                "m3u" -> PortioColors.AccentOrange
                                else -> PortioColors.AccentBlue
                            },
                            textColor = Color.White
                        )
                    }
                    Text(
                        profile?.portal?.name ?: "IPTV Player",
                        style = MaterialTheme.typography.labelSmall,
                        color = PortioColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, PortioColors.Hairline, CircleShape)
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Ara", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                Spacer(Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PortioColors.SurfaceElevated)
                        .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        .clickable { onOpenProfiles() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(userProfile.avatar.ifBlank { "😀" }, fontSize = 18.sp)
                }
            }
        }

        // 2. Öne Çıkanlar (Hero Banner Carousel)
        if (featuredItems.isNotEmpty()) {
            item {
                val pagerState = rememberPagerState(pageCount = { featuredItems.size })
                LaunchedEffect(pagerState.currentPage) {
                    delay(5000)
                    if (featuredItems.size > 1) {
                        val next = (pagerState.currentPage + 1) % featuredItems.size
                        pagerState.animateScrollToPage(next)
                    }
                }

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        pageSpacing = 14.dp,
                        modifier = Modifier.fillMaxWidth().height(230.dp)
                    ) { page ->
                        val item = featuredItems[page]
                        val isSeries = catalog.isSeriesItem(item)
                        HeroBannerCard(
                            item = item,
                            baseUrl = baseUrl,
                            isSeries = isSeries,
                            onPlay = { onOpenVod(item.id, isSeries) },
                            onDetail = { onOpenVod(item.id, isSeries) }
                        )
                    }

                    // Gösterge Noktaları
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(featuredItems.size) { index ->
                            val active = index == pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(width = if (active) 18.dp else 6.dp, height = 6.dp)
                                    .clip(PortioShape.Pill)
                                    .background(if (active) Color.White else Color.White.copy(alpha = 0.25f))
                            )
                        }
                    }
                }
            }
        }

        // 3. İzlemeye Devam Et (Continue Watching)
        if (continueItems.isNotEmpty()) {
            item {
                SectionTitle(
                    title = L10n.t(lang, "İzlemeye Devam Et"),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    onSeeAll = { onGotoTab(6) }
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(continueItems, key = { it.id }) { item ->
                        val progress = vodProgress[item.id]
                        val progressPct = if (progress != null && progress.durationMs > 0) {
                            (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val isSeries = catalog.isSeriesItem(item)

                        Column(modifier = Modifier.width(135.dp)) {
                            PortioCard(
                                onClick = { onOpenVod(item.id, isSeries) },
                                shape = PortioShape.Poster,
                                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = resolveUrl(item.poster, baseUrl),
                                        contentDescription = item.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(PortioColors.PosterOverlay)
                                    )
                                    // Ortada Play İkonu
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.85f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                                    }
                                    // Alt Progress Bar
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                    ) {
                                        PortioProgressBar(progress = progressPct, color = PortioColors.AccentRed)
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 4. Canlı TV Hızlı Kanallar
        if (favChannels.isNotEmpty()) {
            item {
                SectionTitle(
                    title = L10n.t(lang, "Favori Kanallar"),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    onSeeAll = { onGotoTab(1) }
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(favChannels.take(12), key = { it.id }) { ch ->
                        PortioCard(
                            onClick = {
                                val idx = favChannels.indexOfFirst { it.id == ch.id }
                                if (idx >= 0) {
                                    PlaybackManager.playChannel(favChannels, idx, profile)
                                    onOpenPlayer()
                                }
                            },
                            shape = PortioShape.CardSmall,
                            containerColor = PortioColors.SurfaceRaised,
                            modifier = Modifier.width(180.dp)
                        ) { isFocused ->
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ChannelLogo(logo = ch.logo, channelName = ch.name, baseUrl = baseUrl)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ch.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = ch.tvGenreTitle.ifBlank { "Canlı Yayın" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PortioColors.TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Trend Filmler
        if (trendingMovies.isNotEmpty()) {
            item {
                SectionTitle(
                    title = L10n.t(lang, "Trend Filmler"),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    onSeeAll = { onGotoTab(2) }
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(trendingMovies, key = { it.id }) { movie ->
                        Column(modifier = Modifier.width(125.dp)) {
                            PortioMediaCard(
                                title = movie.name,
                                posterUrl = resolveUrl(movie.poster, baseUrl),
                                subtitle = movie.year.take(4),
                                rating = movie.rating,
                                onClick = { onOpenVod(movie.id, false) }
                            )
                        }
                    }
                }
            }
        }

        // 6. Popüler Diziler
        if (popularSeries.isNotEmpty()) {
            item {
                SectionTitle(
                    title = L10n.t(lang, "Popüler Diziler"),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    onSeeAll = { onGotoTab(3) }
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(popularSeries, key = { it.id }) { series ->
                        Column(modifier = Modifier.width(125.dp)) {
                            PortioMediaCard(
                                title = series.name,
                                posterUrl = resolveUrl(series.poster, baseUrl),
                                subtitle = series.year.take(4),
                                badgeText = "DİZİ",
                                badgeColor = PortioColors.BadgeSeries,
                                rating = series.rating,
                                onClick = { onOpenVod(series.id, true) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Öne Çıkan İçerik Banner Kartı
 */
@Composable
private fun HeroBannerCard(
    item: VodItem,
    baseUrl: String,
    isSeries: Boolean,
    onPlay: () -> Unit,
    onDetail: () -> Unit
) {
    PortioCard(
        onClick = onDetail,
        shape = PortioShape.CardLarge,
        modifier = Modifier.fillMaxSize()
    ) { isFocused ->
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = resolveUrl(item.poster, baseUrl),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PortioColors.HeroGradient)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSeries) {
                        PortioBadge(text = "DİZİ", backgroundColor = PortioColors.BadgeSeries, textColor = Color.White)
                    }
                    if (item.year.isNotBlank()) {
                        PortioBadge(text = item.year.take(4), backgroundColor = Color.White.copy(alpha = 0.2f))
                    }
                    if (item.rating.isNotBlank() && item.rating != "0") {
                        RatingBadge(rating = item.rating)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PortioPrimaryButton(
                        text = "İzle",
                        icon = Icons.Default.PlayArrow,
                        onClick = onPlay,
                        modifier = Modifier.height(38.dp)
                    )
                    PortioSecondaryButton(
                        text = "Detay",
                        icon = Icons.Default.Info,
                        onClick = onDetail,
                        modifier = Modifier.height(38.dp)
                    )
                }
            }
        }
    }
}
