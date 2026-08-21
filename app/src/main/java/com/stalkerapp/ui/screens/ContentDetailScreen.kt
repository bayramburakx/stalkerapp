package com.stalkerapp.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Episode
import com.stalkerapp.data.OfflineDownloadManager
import com.stalkerapp.data.Season
import com.stalkerapp.data.VodItem
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.GlassChip
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.ui.components.PortioBadge
import com.stalkerapp.ui.components.PortioButton
import com.stalkerapp.ui.components.PortioButtonStyle
import com.stalkerapp.ui.components.PortioCard
import com.stalkerapp.ui.components.PortioMediaCard
import com.stalkerapp.ui.components.PortioPrimaryButton
import com.stalkerapp.ui.components.PortioProgressBar
import com.stalkerapp.ui.components.PortioSecondaryButton
import com.stalkerapp.ui.components.RatingBadge
import com.stalkerapp.ui.components.SectionTitle
import com.stalkerapp.ui.components.ToastManager
import com.stalkerapp.ui.components.resolveUrl
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.ui.theme.PortioShape
import com.stalkerapp.util.L10n
import kotlinx.coroutines.launch

/**
 * Portio İçerik Detay Ekranı (ContentDetailScreen) - Film ve Dizi Sayfası
 */
@Composable
fun ContentDetailScreen(
    vodId: Long,
    isSeriesHint: Boolean = false,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit = { _, _ -> },
    onOpenPerson: (String, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val app = context.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val lang = vm.store.settings().language
    val profile = vm.repository.cachedProfile()
    val scope = rememberCoroutineScope()
    val baseUrl = profile?.baseUrl.orEmpty()

    val favVods by vm.favoriteVods.collectAsStateWithLifecycle()
    val watchLater by vm.watchLater.collectAsStateWithLifecycle()
    val watchedVersion by vm.watchedVersion.collectAsStateWithLifecycle()

    var vodItem by remember { mutableStateOf<VodItem?>(null) }
    var seasons by remember { mutableStateOf<List<Season>>(emptyList()) }
    var episodesBySeason by remember { mutableStateOf<Map<Long, List<Episode>>>(emptyMap()) }
    var selectedSeasonIdx by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var isSeries by remember { mutableStateOf(isSeriesHint) }

    LaunchedEffect(vodId) {
        loading = true
        val cat = vm.vodCatalog.value
        val item = cat.allItems.firstOrNull { it.id == vodId }
        vodItem = item
        if (item != null) {
            isSeries = item.isSeries || isSeriesHint || cat.isSeriesItem(item)
            if (isSeries && profile != null) {
                val loadedSeasons = runCatching { vm.repository.loadSeasons(profile, vodId) }.getOrDefault(emptyList())
                seasons = loadedSeasons
                if (loadedSeasons.isNotEmpty()) {
                    val firstSeason = loadedSeasons[0]
                    val eps = runCatching { vm.repository.loadEpisodes(profile, vodId, firstSeason.id) }.getOrDefault(emptyList())
                    episodesBySeason = mapOf(firstSeason.id to eps)
                }
            }
        }
        loading = false
    }

    LaunchedEffect(selectedSeasonIdx, seasons) {
        val s = seasons.getOrNull(selectedSeasonIdx)
        if (s != null && !episodesBySeason.containsKey(s.id) && profile != null) {
            val eps = runCatching { vm.repository.loadEpisodes(profile, vodId, s.id) }.getOrDefault(emptyList())
            episodesBySeason = episodesBySeason + (s.id to eps)
        }
    }

    val item = vodItem

    Scaffold(
        containerColor = Color.Black
    ) { padding ->
        if (loading && item == null) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                LoadingBox()
            }
            return@Scaffold
        }

        if (item == null) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                EmptyState(L10n.t(lang, "İçerik bulunamadı"))
            }
            return@Scaffold
        }

        val isFav = favVods.any { it.id == item.id }
        val isWatchLater = watchLater.any { it.id == item.id }
        val watched = vm.store.isWatchedOverride(item.id)

        val progress = vm.store.loadVodProgress()[item.id]
        val progressPct = if (progress != null && progress.durationMs > 0) {
            (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val currentSeason = seasons.getOrNull(selectedSeasonIdx)
        val currentEpisodes = if (currentSeason != null) episodesBySeason[currentSeason.id].orEmpty() else emptyList()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(PortioColors.Background),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // 1. Hero Backdrop & Afiş
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    AsyncImage(
                        model = resolveUrl(item.poster, baseUrl),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(PortioColors.BackdropScrim)
                    )

                    // Üst Eylemler: Geri, Paylaş, Favori, Sonra İzle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .border(1.dp, PortioColors.Hairline, CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                vm.toggleFavoriteVod(item)
                                ToastManager.show(if (isFav) "Favorilerden çıkarıldı" else "Favorilere eklendi")
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .border(1.dp, PortioColors.Hairline, CircleShape)
                        ) {
                            Icon(
                                if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favori",
                                tint = if (isFav) PortioColors.AccentRed else Color.White
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                vm.toggleWatchLater(item)
                                ToastManager.show(if (isWatchLater) "Sonra İzle'den çıkarıldı" else "Sonra İzle'ye eklendi")
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .border(1.dp, PortioColors.Hairline, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = "Sonra İzle",
                                tint = if (isWatchLater) PortioColors.Badge4K else Color.White
                            )
                        }
                    }
                }
            }

            // 2. Başlık, Rozetler ve Eylemler
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isSeries) {
                            PortioBadge(text = "DİZİ", backgroundColor = PortioColors.BadgeSeries, textColor = Color.White)
                        }
                        if (item.year.isNotBlank()) {
                            PortioBadge(text = item.year.take(4), backgroundColor = Color.White.copy(alpha = 0.18f))
                        }
                        if (item.rating.isNotBlank() && item.rating != "0") {
                            RatingBadge(rating = item.rating)
                        }
                        if (item.genres.isNotBlank()) {
                            PortioBadge(text = item.genres.split(",").first().trim(), backgroundColor = Color.White.copy(alpha = 0.12f))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Ana Oynat Butonu & Aksiyonlar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PortioPrimaryButton(
                            text = if (progressPct > 0f) "Devam Et" else "Hemen Oynat",
                            icon = Icons.Default.PlayArrow,
                            onClick = {
                                if (isSeries && currentEpisodes.isNotEmpty()) {
                                    val ep = currentEpisodes.first()
                                    PlaybackManager.playEpisode(
                                        item = item,
                                        profile = profile,
                                        episodes = currentEpisodes,
                                        season = (selectedSeasonIdx + 1).toLong(),
                                        index = 0,
                                        startPositionMs = if (progressPct > 0f) progress?.positionMs ?: 0L else 0L
                                    )
                                    onOpenPlayer()
                                } else {
                                    PlaybackManager.currentVodItem = item
                                    PlaybackManager.currentVodId = item.id
                                    PlaybackManager.play(
                                        url = resolveUrl(item.cmd, baseUrl),
                                        title = item.name,
                                        artwork = resolveUrl(item.poster, baseUrl),
                                        startPositionMs = if (progressPct > 0f) progress?.positionMs ?: 0L else 0L
                                    )
                                    onOpenPlayer()
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp)
                        )

                        if (!isSeries && item.cmd.isNotBlank()) {
                            PortioSecondaryButton(
                                text = "İndir",
                                icon = Icons.Default.Download,
                                onClick = {
                                    scope.launch {
                                        OfflineDownloadManager.enqueue(
                                            OfflineDownloadManager.DownloadEntry(
                                                id = "vod_${item.id}",
                                                title = item.name,
                                                url = resolveUrl(item.cmd, baseUrl),
                                                poster = resolveUrl(item.poster, baseUrl)
                                            )
                                        )
                                        ToastManager.success("İndirme sıraya alındı")
                                    }
                                },
                                modifier = Modifier.height(48.dp)
                            )
                        }

                        PortioButton(
                            onClick = {
                                vm.toggleWatched(item.id)
                                ToastManager.show(if (watched) "İzlenmedi işaretlendi" else "İzlendi işaretlendi")
                            },
                            style = PortioButtonStyle.Glass,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = if (watched) PortioColors.Success else Color.White)
                        }
                    }

                    if (progressPct > 0f) {
                        Spacer(Modifier.height(12.dp))
                        PortioProgressBar(progress = progressPct, color = PortioColors.AccentRed, height = 4.dp)
                    }

                    Spacer(Modifier.height(16.dp))
                    if (item.description.isNotBlank()) {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PortioColors.TextSecondary,
                            lineHeight = 22.sp
                        )
                    }

                    if (item.director.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Yönetmen: ", style = MaterialTheme.typography.labelMedium, color = PortioColors.TextMuted)
                            Text(
                                item.director,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.clickable { onOpenPerson(item.director, true) }
                            )
                        }
                    }

                    if (item.actors.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Oyuncular: ", style = MaterialTheme.typography.labelMedium, color = PortioColors.TextMuted)
                        Spacer(Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(item.actors.split(",").map { it.trim() }.filter { it.isNotBlank() }) { actor ->
                                GlassChip(
                                    selected = false,
                                    onClick = { onOpenPerson(actor, false) },
                                    label = actor
                                )
                            }
                        }
                    }
                }
            }

            // 3. Dizi Bölümleri Listesi
            if (isSeries && seasons.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    SectionTitle(title = "Bölümler", modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }

                // Sezon Seçim Çipleri
                if (seasons.size > 1) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(seasons.indices.toList()) { idx ->
                                GlassChip(
                                    selected = selectedSeasonIdx == idx,
                                    onClick = { selectedSeasonIdx = idx },
                                    label = seasons[idx].name.ifBlank { "Sezon ${idx + 1}" }
                                )
                            }
                        }
                    }
                }

                itemsIndexed(currentEpisodes, key = { _, ep -> ep.id }) { idx, ep ->
                    EpisodeRow(
                        ep = ep,
                        item = item,
                        baseUrl = baseUrl,
                        onPlay = {
                            PlaybackManager.playEpisode(
                                item = item,
                                profile = profile,
                                episodes = currentEpisodes,
                                season = (selectedSeasonIdx + 1).toLong(),
                                index = idx
                            )
                            onOpenPlayer()
                        },
                        onDownload = {
                            scope.launch {
                                OfflineDownloadManager.enqueue(
                                    OfflineDownloadManager.DownloadEntry(
                                        id = "ep_${ep.id}",
                                        title = item.name,
                                        url = resolveUrl(if (ep.cmd.isNotBlank()) ep.cmd else ep.altCmd, baseUrl),
                                        poster = resolveUrl(if (ep.thumb.isNotBlank()) ep.thumb else item.poster, baseUrl),
                                        episodeLabel = ep.name.ifBlank { "Bölüm ${ep.episodeNumber}" },
                                        isSeries = true
                                    )
                                )
                                ToastManager.success("Bölüm indirmeye alındı")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    ep: Episode,
    item: VodItem,
    baseUrl: String,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    PortioCard(
        onClick = onPlay,
        shape = PortioShape.CardSmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) { isFocused ->
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 48.dp)
                    .clip(PortioShape.Small)
                    .background(PortioColors.SurfaceRaised),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ep.name.ifBlank { "Bölüm ${ep.episodeNumber}" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Download, contentDescription = "İndir", tint = PortioColors.TextMuted, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Geriye dönük uyumluluk alias fonksiyonu */
@Composable
fun VodDetailScreen(
    vodId: Long,
    isSeriesHint: Boolean = false,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit = { _, _ -> },
    onOpenPerson: (String, Boolean) -> Unit = { _, _ -> }
) {
    ContentDetailScreen(
        vodId = vodId,
        isSeriesHint = isSeriesHint,
        onBack = onBack,
        onOpenPlayer = onOpenPlayer,
        onOpenVod = onOpenVod,
        onOpenPerson = onOpenPerson
    )
}
