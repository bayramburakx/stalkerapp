package com.stalkerapp.ui.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.stalkerapp.data.Episode
import com.stalkerapp.data.Season
import com.stalkerapp.data.VodItem
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.ui.components.resolveUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VodDetailScreen(
    vodId: Long,
    isSeriesHint: Boolean = false,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val profile = vm.repository.cachedProfile()
    val scope = rememberCoroutineScope()

    var item by remember { mutableStateOf<VodItem?>(null) }
    var seasons by remember { mutableStateOf<List<Season>>(emptyList()) }
    var episodes by remember { mutableStateOf<List<Episode>?>(null) }
    var selectedSeason by remember { mutableStateOf<Long?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingEpisodes by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }

    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()

    LaunchedEffect(vodId) {
        loading = true
        error = null
        try {
            val p = profile ?: return@LaunchedEffect
            // This portal ignores the single-item fetch (vod_id), so rely on the
            // already-loaded catalog item keyed by id — otherwise every title would
            // resolve to the same (first) item.
            var base: VodItem? = null
            val deadline = System.currentTimeMillis() + 60_000L
            while (base == null && System.currentTimeMillis() < deadline) {
                base = vm.vodCatalog.value.byId[vodId]
                    ?: runCatching { vm.repository.vodById(p, vodId) }.getOrNull()
                if (base == null) delay(400)
            }
            if (base == null) {
                error = "İçerik bulunamadı"
                return@LaunchedEffect
            }
            // Enrich with detailed info (actors, full director, etc.) when available.
            val info = runCatching { vm.repository.vodInfo(p, vodId) }.getOrNull()
            item = if (info != null) {
                base.copy(
                    actors = info.actors.ifBlank { base.actors },
                    director = info.director.ifBlank { base.director },
                    country = info.country.ifBlank { base.country },
                    year = info.year.ifBlank { base.year },
                    rating = info.rating.ifBlank { base.rating },
                    genres = info.genres.ifBlank { base.genres },
                    description = info.description.ifBlank { base.description },
                    duration = info.duration.ifBlank { base.duration },
                    writers = info.writers.ifBlank { base.writers }
                )
            } else base
            val merged = item
            if ((merged?.isSeries == true || isSeriesHint) && profile != null) {
                seasons = vm.repository.loadSeasons(profile, vodId)
                selectedSeason = seasons.firstOrNull()?.id
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(selectedSeason) {
        val it = item ?: return@LaunchedEffect
        val sid = selectedSeason ?: return@LaunchedEffect
            if (!it.isSeries && !isSeriesHint) return@LaunchedEffect
        val p = profile ?: return@LaunchedEffect
        loadingEpisodes = true
        episodes = null
        try {
            episodes = vm.repository.loadEpisodes(p, it.id, sid)
        } catch (e: Exception) {
            error = e.message
        } finally {
            loadingEpisodes = false
        }
    }

    val it = item
    if (it == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator() else Text(error ?: "İçerik bulunamadı")
        }
        return
    }

    val isSeries = it.isSeries || isSeriesHint
    // Poster hero: anasayfadaki gibi ekran yüksekliğinin yarısı kadar.
    val heroHeight = with(LocalConfiguration.current) { screenHeightDp.dp / 2f }
    val favVods by vm.favoriteVods.collectAsStateWithLifecycle()
    val isFavorite = remember(favVods, it) { favVods.any { f -> f.id == it.id } }
    val yearText = it.year.take(4).takeIf { y -> y.isNotBlank() && y.all(Char::isDigit) }.orEmpty()
    val genre = it.genres.trim().ifBlank {
        catalog.categories.firstOrNull { c -> c.id == it.categoryId }?.title.orEmpty()
    }
    val actors = it.actors.split(",").map { it.trim() }.filter { it.isNotBlank() }
    val durationText = formatDuration(it.duration)

    fun play(episode: Episode? = null) {
        val p = profile ?: return
        scope.launch {
            playing = true
            try {
                val url = vm.repository.vodStreamUrl(it.copy(isSeries = isSeries), p, episode)
                PlaybackManager.currentVodId = it.id
                PlaybackManager.play(url, it.name, it.poster)
                onOpenPlayer()
            } catch (e: Exception) {
                error = e.message
            } finally {
                playing = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // ---------- Hero: poster, yarım ekran, anasayfadaki gibi ----------
            item {
                Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {
                    AsyncImage(
                        model = resolveUrl(it.poster, profile?.baseUrl.orEmpty()),
                        contentDescription = it.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Üstten şeffaf, alta doğru koyulaşan yumuşak geçiş.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        0.30f to Color.Black.copy(alpha = 0.15f),
                                        0.60f to Color.Black.copy(alpha = 0.50f),
                                        1.0f to Color.Black.copy(alpha = 0.88f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            it.name,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                shadow = Shadow(color = Color.Black, blurRadius = 12f)
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        // DİZİ/FİLM • tür (yıl burada değil, altta gösterilir).
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
                                    modifier = Modifier.widthIn(max = 200.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Oynat: beyaz zemin, siyah kalın yazı.
                            Button(
                                onClick = { play() },
                                enabled = !playing,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.height(48.dp)
                            ) {
                                if (playing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.Black
                                    )
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Oynat", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                            // Favori: Oynat'ın hemen sağında, yuvarlak içinde kalp.
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.20f))
                                    .clickable { vm.toggleFavoriteVod(it) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favori",
                                    tint = if (isFavorite) Color(0xFFFF5252) else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ---------- Bilgiler: yıl • süre, yönetmen, yazar, sinopsis ----------
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    val metaParts = listOfNotNull(
                        yearText.takeIf { it.isNotBlank() },
                        durationText.takeIf { it.isNotBlank() }
                    )
                    if (metaParts.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            metaParts.forEachIndexed { index, part ->
                                if (index > 0) Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(part, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    it.director.takeIf { d -> d.isNotBlank() }?.let { d ->
                        Text("Yönetmen: $d", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                    }
                    it.writers.takeIf { w -> w.isNotBlank() }?.let { w ->
                        Text("Yazar: $w", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                    }
                    if (it.description.isNotBlank()) {
                        Text(
                            it.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---------- Oyuncular: yuvarlak baş harfler + isimler ----------
            if (actors.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            "Oyuncular",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(actors) { name ->
                                Column(
                                    modifier = Modifier.width(72.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            initials(name),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---------- Dizi: sezon kutuları + S1B1 bölüm kartları ----------
            if (isSeries) {
                item {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            "Sezonlar",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(seasons, key = { it.id }) { s ->
                                val sel = selectedSeason == s.id
                                val shape = RoundedCornerShape(12.dp)
                                Box(
                                    modifier = Modifier
                                        .clip(shape)
                                        .background(
                                            if (sel) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .border(1.dp, if (sel) Color.Transparent else MaterialTheme.colorScheme.outline, shape)
                                        .clickable { selectedSeason = s.id }
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        seasonLabel(s),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                when {
                    loadingEpisodes -> item { LoadingBox() }
                    episodes.orEmpty().isEmpty() -> item { EmptyState("Bölüm bulunamadı") }
                    else -> item {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                "Bölümler",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(episodes.orEmpty(), key = { it.id }) { ep ->
                                    val seasonNum = selectedSeason ?: 0
                                    Box(
                                        modifier = Modifier
                                            .width(112.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { play(ep) }
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "S${seasonNum}B${ep.episodeNumber}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (ep.name.isNotBlank()) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    ep.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

            if (error != null) {
                item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            }
            item { Spacer(Modifier.height(48.dp)) }
        }

        // ---------- Üst bar: şeffaf arka plan, sadece geri tuşu (kalp yok) ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
            }
        }
    }
}

/** Süre: portal dakika ya da saniye verebilir; ikisini de "X sa Y dk" / "X dk" biçimine çevirir. */
private fun formatDuration(raw: String): String {
    val n = raw.trim().toLongOrNull() ?: return ""
    if (n <= 0) return ""
    return if (n >= 600) {
        val h = n / 3600
        val m = (n % 3600) / 60
        when {
            h > 0 && m > 0 -> "${h} sa ${m} dk"
            h > 0 -> "${h} sa"
            else -> "$m dk"
        }
    } else "$n dk"
}

/** Oyuncu dairesi için baş harfler: ad + soyadın ilk harfleri. */
private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return "?"
    val first = parts.first().firstOrNull()?.uppercase() ?: ""
    val second = if (parts.size > 1) parts[1].firstOrNull()?.uppercase() ?: "" else ""
    return first + second
}

/** Sezon etiketi: "Season 1"/"Sezon 1" gibi tekrarları "1. Sezon" biçimine çevirir. */
private fun seasonLabel(s: Season): String {
    val name = s.name.trim()
    if (name.isBlank()) return "${s.id}. Sezon"
    if (name.matches(Regex("(?i)(season|sezon|staffel)\\s*\\d+"))) return "${s.id}. Sezon"
    return name
}
