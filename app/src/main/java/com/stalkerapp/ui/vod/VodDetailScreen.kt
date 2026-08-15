package com.stalkerapp.ui.vod

import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Episode
import com.stalkerapp.data.Season
import com.stalkerapp.data.TmdbClient
import com.stalkerapp.data.TmdbEpisodeInfo
import com.stalkerapp.data.TmdbPerson
import com.stalkerapp.data.VodItem
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.ui.components.resolveUrl
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VodDetailScreen(
    vodId: Long,
    isSeriesHint: Boolean = false,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit = { _, _ -> },
    onOpenPerson: (String, Boolean) -> Unit = { _, _ -> }
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
    // İzlenme işaretleri anlık güncellenir (basınca Store'dan taze okunur).
    var watchedEps by remember { mutableStateOf(app.store.watchedEpisodes()) }
    // Tüm bölümleri izlenen sezonlar (sezon rozeti için).
    var fullyWatchedSeasons by remember { mutableStateOf<Set<Long>>(emptySet()) }
    // TMDB zenginleştirme (oyuncu fotoğrafları + fragman). Anahtar yoksa boş kalır.
    var tmdbCast by remember { mutableStateOf<List<TmdbPerson>>(emptyList()) }
    var trailerKey by remember { mutableStateOf("") }
    // Sezon posterleri + bölüm küçük resimleri (portal önce, yoksa TMDB).
    var seasonPosters by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var episodeThumbs by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    // TMDB'den gelen gerçek bölüm adları (portaldaki bölümlerde ad yoktur).
    var episodeNames by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    // Sezon üstüne uzun basınca "izlendi işaretle?" onayı sorulur.
    var seasonConfirm by remember { mutableStateOf<Season?>(null) }
    val context = LocalContext.current

    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()

    LaunchedEffect(item?.tmdbId, item?.isSeries, isSeriesHint) {
        val i = item ?: return@LaunchedEffect
        val key = app.store.settings().tmdbApiKey
        if (key.isNotBlank() && i.tmdbId > 0) {
            val enr = app.tmdb.enrich(i.tmdbId, i.isSeries || isSeriesHint, key)
            tmdbCast = enr.cast
            trailerKey = enr.trailerKey
        }
    }

    // Sezon posterleri: TMDB'de gerçek sezon posteri varsa o, yoksa portalın
    // sezon görseli (varsa), o da yoksa dizi afişi kart üzerinde gösterilir.
    LaunchedEffect(item?.tmdbId, item?.isSeries, isSeriesHint, seasons) {
        val i = item ?: return@LaunchedEffect
        if (seasons.isEmpty() || !(i.isSeries || isSeriesHint)) return@LaunchedEffect
        val key = app.store.settings().tmdbApiKey
        val withKey = key.isNotBlank() && i.tmdbId > 0
        val map = mutableMapOf<Long, String>()
        seasons.forEach { s ->
            val num = s.id.toInt().coerceAtLeast(1)
            if (withKey) {
                val p = runCatching { app.tmdb.seasonPoster(i.tmdbId, num, key) }.getOrDefault("")
                if (p.isNotBlank()) {
                    map[s.id] = TmdbClient.photoUrl(p, large = true)
                    return@forEach
                }
            }
            if (s.poster.isNotBlank()) map[s.id] = resolveUrl(s.poster, profile?.baseUrl.orEmpty())
        }
        seasonPosters = map
    }

    // Bölüm küçük resimleri + gerçek adları: portal görseli önce, yoksa TMDB'den
    // still + bölüm adı çekilir (bölüm bölüm eklenir, anında görünür).
    LaunchedEffect(item?.tmdbId, item?.isSeries, isSeriesHint, selectedSeason, episodes) {
        val i = item ?: return@LaunchedEffect
        val eps = episodes.orEmpty()
        if (eps.isEmpty() || !(i.isSeries || isSeriesHint)) return@LaunchedEffect
        val key = app.store.settings().tmdbApiKey
        val withKey = key.isNotBlank() && i.tmdbId > 0
        val seasonNum = selectedSeason?.toInt()?.coerceAtLeast(1) ?: 1
        var thumbs = emptyMap<Long, String>()
        var names = emptyMap<Long, String>()
        eps.forEach { e ->
            val info = if (withKey) {
                runCatching { app.tmdb.episodeInfo(i.tmdbId, seasonNum, e.episodeNumber.coerceAtLeast(1), key) }
                    .getOrDefault(TmdbEpisodeInfo())
            } else TmdbEpisodeInfo()
            val url = when {
                e.thumb.isNotBlank() -> resolveUrl(e.thumb, profile?.baseUrl.orEmpty())
                info.stillPath.isNotBlank() -> TmdbClient.photoUrl(info.stillPath, large = true)
                else -> ""
            }
            if (url.isNotBlank()) {
                thumbs = thumbs + (e.id to url)
                episodeThumbs = thumbs
            }
            val nm = e.name.ifBlank { info.name }
            if (nm.isNotBlank()) {
                names = names + (e.id to nm)
                episodeNames = names
            }
        }
    }

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
    val watchLater by vm.watchLater.collectAsStateWithLifecycle()
    val yearText = it.year.take(4).takeIf { y -> y.isNotBlank() && y.all(Char::isDigit) }.orEmpty()
    val genre = it.genres.trim().ifBlank {
        catalog.categories.firstOrNull { c -> c.id == it.categoryId }?.title.orEmpty()
    }
    val actors = it.actors.split(",").map { it.trim() }.filter { it.isNotBlank() }
    val durationText = formatDuration(it.duration)

    // Benzer İçerikler: tür/kategori benzerliğinden istemci tarafı öneriler.
    // LazyColumn DSL'i composable olmadığı için burada (dışarıda) hesaplanır.
    val similar = remember(catalog.allItems, it, isSeries) {
        val tokens = (it.genres + " " + it.country).split(Regex("[,\\s]+"))
            .map { t -> t.trim().lowercase() }.filter { t -> t.length > 1 }.toSet()
        catalog.allItems
            .filter { c -> c.id != it.id && catalog.isSeriesItem(c) == isSeries }
            .map { c ->
                val score = tokens.count { t -> c.genres.contains(t, ignoreCase = true) } * 2 +
                    (if (c.categoryId == it.categoryId) 1 else 0)
                c to score
            }
            .filter { s -> s.second > 0 }
            .sortedByDescending { s -> s.second }
            .take(12)
            .map { s -> s.first }
    }

    fun episodeKey(ep: Episode?, seasonNum: Long): String =
        "${it.id}:$seasonNum:${ep?.episodeNumber}"

    /** İzlenmemiş ilk bölüm; hepsi izlendiyse ilk bölüm. */
    fun firstEpisodeToPlay(allEps: List<Episode>, seasonNum: Long): Episode {
        val seen = watchedEps
        return allEps.firstOrNull { episodeKey(it, seasonNum) !in seen } ?: allEps.first()
    }

    /** Seçili sezonun tüm bölümleri izlendiyse sezon rozetini günceller. */
    fun refreshSeasonWatched(seasonId: Long) {
        val eps = episodes.orEmpty()
        val prefix = "${it.id}:$seasonId:"
        val allWatched = eps.isNotEmpty() && eps.all { e -> "$prefix${e.episodeNumber}" in watchedEps }
        fullyWatchedSeasons =
            if (allWatched) fullyWatchedSeasons + seasonId else fullyWatchedSeasons - seasonId
    }

    fun toggleEpisodeWatched(ep: Episode, seasonNum: Long) {
        val key = episodeKey(ep, seasonNum)
        if (key in watchedEps) app.store.clearEpisodeWatched(key) else app.store.markEpisodeWatched(key)
        watchedEps = app.store.watchedEpisodes()
        vm.bumpWatched()
        refreshSeasonWatched(seasonNum)
    }

    /** Bir sezonun tüm bölümlerini izlendi/izlenmedi işaretler (uzun basma). */
    fun toggleSeasonWatched(seasonId: Long) {
        val p = profile ?: return
        scope.launch {
            val nums = runCatching { vm.repository.seasonEpisodeNumbers(p, it.id, seasonId) }
                .getOrDefault(emptyList())
            if (nums.isEmpty()) return@launch
            val prefix = "${it.id}:$seasonId:"
            val allWatched = nums.all { n -> "$prefix$n" in watchedEps }
            nums.forEach { n ->
                val key = "$prefix$n"
                if (allWatched) app.store.clearEpisodeWatched(key) else app.store.markEpisodeWatched(key)
            }
            watchedEps = app.store.watchedEpisodes()
            vm.bumpWatched()
            fullyWatchedSeasons = if (!allWatched) fullyWatchedSeasons + seasonId else fullyWatchedSeasons - seasonId
        }
    }

    /** Kaldığı yerden devam: kayıtlı konum varsa direkt oradan başlar (sormaz). */
    fun play(episode: Episode? = null) {
        val p = profile ?: return
        scope.launch {
            playing = true
            try {
                val allEps = episodes.orEmpty()
                if (!isSeries || allEps.isEmpty()) {
                    // Film: kayıtlı konumdan devam et.
                    val prog = app.store.loadVodProgress()[it.id]
                    val startMs = if (prog != null && prog.durationMs > 0 &&
                        prog.positionMs.toDouble() in (prog.durationMs * 0.02)..(prog.durationMs * 0.95)
                    ) prog.positionMs else 0L
                    val url = vm.repository.vodStreamUrl(it, p, null)
                    PlaybackManager.currentVodId = it.id
                    PlaybackManager.play(url, it.name, it.poster, startPositionMs = startMs)
                } else {
                    // Dizi: bölüm HER ZAMAN playEpisode ile oynatılır. Kuyruk
                    // (VodQueue) doldurulur ki "Sonraki Bölüm" butonu, binge modu
                    // ve %85 otomatik "izlendi" işareti çalışsın. (Bölümü film
                    // yoluyla oynatmak kuyruğu temizliyor ve bunların hepsini kırıyordu.)
                    val seasonNum = selectedSeason ?: seasons.firstOrNull()?.id ?: 0
                    val target = if (episode != null) {
                        episode
                    } else {
                        // İlerlemesi olan bölüm varsa oradan, yoksa izlenmemiş ilk bölüm.
                        val withProgress = allEps.firstOrNull { ep ->
                            val pr = app.store.episodeProgress()[episodeKey(ep, seasonNum)]
                            pr != null && pr.durationMs > 0 &&
                                pr.positionMs.toDouble() in (pr.durationMs * 0.02)..(pr.durationMs * 0.95)
                        }
                        withProgress ?: firstEpisodeToPlay(allEps, seasonNum)
                    }
                    val idx = allEps.indexOfFirst { e -> e.id == target.id }.coerceAtLeast(0)
                    val pr = app.store.episodeProgress()[episodeKey(target, seasonNum)]
                    val startMs = if (pr != null && pr.durationMs > 0 &&
                        pr.positionMs.toDouble() in (pr.durationMs * 0.02)..(pr.durationMs * 0.95)
                    ) pr.positionMs else 0L
                    PlaybackManager.playEpisode(it, p, allEps, seasonNum, idx, startPositionMs = startMs)
                }
                onOpenPlayer()
            } catch (e: Exception) {
                error = e.message
            } finally {
                playing = false
            }
        }
    }

    fun onPlayPressed(episode: Episode? = null) = play(episode)

    // Oynatıcıdan dönünce (bölüm %85 izlendi / binge sonrası) rozetler tazelenir.
    LifecycleResumeEffect(Unit) {
        watchedEps = app.store.watchedEpisodes()
        val sid = selectedSeason
        if (sid != null) refreshSeasonWatched(sid)
        onPauseOrDispose { }
    }

    // Sezon seçilince o sezonun bölümlerini yükler ve sezon rozetini tazeler.
    LaunchedEffect(selectedSeason) {
        val it = item ?: return@LaunchedEffect
        val sid = selectedSeason ?: return@LaunchedEffect
        if (!it.isSeries && !isSeriesHint) return@LaunchedEffect
        val p = profile ?: return@LaunchedEffect
        loadingEpisodes = true
        episodes = null
        try {
            episodes = vm.repository.loadEpisodes(p, it.id, sid)
            refreshSeasonWatched(sid)
        } catch (e: Exception) {
            error = e.message
        } finally {
            loadingEpisodes = false
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
                                onClick = { onPlayPressed() },
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
                            // Sonra İzle: Oynat'ın sağında, yuvarlak içinde saat simgesi.
                            val inWatchLater = remember(watchLater, it) { watchLater.any { w -> w.id == it.id } }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.20f))
                                    .clickable { vm.toggleWatchLater(it) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (inWatchLater) Icons.Default.Schedule else Icons.Default.Schedule,
                                    contentDescription = "Sonra İzle",
                                    tint = if (inWatchLater) Color(0xFF64B5F6) else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            // Fragman: TMDB anahtarı + fragman varsa YouTube'da açar.
                            if (trailerKey.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.20f))
                                        .clickable {
                                            runCatching {
                                                val intent = Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("https://www.youtube.com/watch?v=$trailerKey")
                                                )
                                                context.startActivity(intent)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.SmartDisplay,
                                        contentDescription = "Fragman",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
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
                        durationText.takeIf { it.isNotBlank() },
                        it.rating.takeIf { r -> r.isNotBlank() && r != "0" }?.let { "★ $it" }
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
                        Text(
                            "Yönetmen: $d",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onOpenPerson(d, true) }
                        )
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

            // ---------- Fragman: sinopsisin altında, gömülü oynatıcı ----------
            if (trailerKey.isNotBlank()) {
                item {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            "Fragman",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        TrailerPlayer(
                            key = trailerKey,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
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
                            items(actors) { actorName ->
                                // TMDB'den fotoğraf varsa baş harf yerine fotoğraf.
                                val person = tmdbCast.firstOrNull { p ->
                                    actorName.contains(p.name, ignoreCase = true) ||
                                        p.name.contains(actorName, ignoreCase = true)
                                }
                                val photo = person?.photoPath?.let { TmdbClient.photoUrl(it) }.orEmpty()
                                Column(
                                    modifier = Modifier
                                        .width(72.dp)
                                        .clickable { onOpenPerson(actorName, false) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (photo.isNotBlank()) {
                                            AsyncImage(
                                                model = photo,
                                                contentDescription = actorName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(56.dp)
                                            )
                                        } else {
                                            Text(
                                                initials(actorName),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        actorName,
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

            // ---------- Dizi: sezon posterleri + S1B1 bölüm kartları ----------
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
                                val fullyWatched = s.id in fullyWatchedSeasons
                                val poster = seasonPosters[s.id].orEmpty()
                                Column(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .combinedClickable(
                                            onClick = { selectedSeason = s.id },
                                            // Uzun basma: izlendi işaretlemeden önce onay sorulur.
                                            onLongClick = { seasonConfirm = s }
                                        )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(2f / 3f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .then(
                                                if (sel) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                                else Modifier
                                            )
                                    ) {
                                        if (poster.isNotBlank()) {
                                            AsyncImage(
                                                model = poster,
                                                contentDescription = seasonLabel(s),
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    seasonLabel(s),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    textAlign = TextAlign.Center,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (fullyWatched) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF2E7D32)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Sezon izlendi",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        seasonLabel(s),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                                    val watched = episodeKey(ep, seasonNum) in watchedEps
                                    val thumb = episodeThumbs[ep.id].orEmpty()
                                    // Bölüm adı: portal adı varsa o, TMDB'den gelirse o,
                                    // yoksa "Bölüm N" (kutu her zaman ad gösterir).
                                    val name = episodeNames[ep.id].orEmpty()
                                        .ifBlank { ep.name }
                                        .ifBlank { "Bölüm ${ep.episodeNumber}" }
                                    Box(
                                        modifier = Modifier
                                            .width(196.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (watched) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .combinedClickable(
                                                onClick = { onPlayPressed(ep) },
                                                onLongClick = { toggleEpisodeWatched(ep, seasonNum) }
                                            )
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            // Bölüm küçük resmi (varsa) + S#B# rozeti.
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(16f / 9f)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(Color.Black)
                                            ) {
                                                if (thumb.isNotBlank()) {
                                                    AsyncImage(
                                                        model = thumb,
                                                        contentDescription = "S${seasonNum}B${ep.episodeNumber}",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(6.dp)
                                                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        "S${seasonNum}B${ep.episodeNumber}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                                // İzlenme rozeti + anlık işaretleme butonu.
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(6.dp)
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (watched) Color(0xFF2E7D32)
                                                            else Color.Black.copy(alpha = 0.55f)
                                                        )
                                                        .clickable { toggleEpisodeWatched(ep, seasonNum) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = if (watched) "İzlenmedi yap" else "İzlendi işaretle",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                            // Bölüm adı kartın İÇİNDE (küçük resmin altında).
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                            )
                                            Spacer(Modifier.height(6.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---------- Benzer İçerikler (tür/kategori benzerliği) ----------
            if (similar.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            "Benzer İçerikler",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(similar, key = { it.id }) { s ->
                                VodPoster(
                                    item = s,
                                    baseUrl = profile?.baseUrl.orEmpty(),
                                    isSeries = catalog.isSeriesItem(s),
                                    posterWidth = 110,
                                    onClick = { onOpenVod(s.id, catalog.isSeriesItem(s)) }
                                )
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

    // Sezon onay sheet'i: uzun basınca izlendi işaretlemeden önce sorar.
    // Sezon zaten işaretliyse "geri al" seçeneği sunar (tekrar uzun bas → geri al).
    if (seasonConfirm != null) {
        val s = seasonConfirm!!
        val alreadyWatched = s.id in fullyWatchedSeasons
        ModalBottomSheet(onDismissRequest = { seasonConfirm = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
            ) {
                Text(
                    seasonLabel(s),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (alreadyWatched) "Tüm bölümleri izlenmedi olarak işaretle?"
                    else "Tüm bölümleri izlendi olarak işaretle?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        toggleSeasonWatched(s.id)
                        seasonConfirm = null
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (alreadyWatched) "İzlenmedi İşaretle" else "İzlendi İşaretle",
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { seasonConfirm = null },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("İptal")
                }
            }
        }
    }
}

/**
 * Fragman oynatıcı (katmanlı): önce doğrudan video akışı aranır (Piped —
 * TMDB videoyu barındırmaz, yalnızca YouTube kimliğini verir) ve ExoPlayer ile
 * uygulama İÇİNDE video olarak oynatılır. Akış sunucularına ulaşılamazsa gömülü
 * WebView'da mobil YouTube sayfası açılır (yine uygulama içinde oynar); en kötü
 * ihtimalle YouTube uygulamasında açma kısayolu vardır.
 */
@Composable
private fun TrailerPlayer(key: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as StalkerApp
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var siteMode by remember { mutableStateOf(false) }

    fun openInYoutube() {
        runCatching {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/watch?v=$key")
            )
            context.startActivity(intent)
        }
    }

    fun startTrailer() {
        if (loading || streamUrl != null) return
        loading = true
        scope.launch {
            val url = app.tmdb.youtubeStreamUrl(key)
            loading = false
            if (url.isNullOrBlank()) {
                // Akış sunucularına ulaşılamadı: WebView yedeğine geç (yine oynar).
                siteMode = true
            } else {
                streamUrl = url
            }
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
    ) {
        when {
            streamUrl != null -> {
                // Uygulama içinde gerçek video oynatıcı (site değil).
                TrailerVideoPlayer(
                    url = streamUrl!!,
                    onError = {
                        streamUrl = null
                        siteMode = true
                    }
                )
            }
            siteMode -> {
                // Video akışı alınamadı: gömülü mobil YouTube sayfası (uygulama içinde).
                TrailerWebView(key)
            }
            else -> {
                AsyncImage(
                    model = "https://img.youtube.com/vi/$key/hqdefault.jpg",
                    contentDescription = "Fragman",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f))
                        .clickable { startTrailer() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                strokeWidth = 3.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Fragmanı oynat",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }
        }
        // Sağ altta her zaman YouTube'da açma kısayolu.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { openInYoutube() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                "YouTube'da aç",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

/** Fragmanı uygulama içinde ExoPlayer ile oynatan mini video oynatıcı. */
@Composable
private fun TrailerVideoPlayer(url: String, onError: () -> Unit) {
    val context = LocalContext.current
    // Bazı akış sunucuları varsayılan ExoPlayer UA'sını reddeder; tarayıcı UA'sı kullanılır.
    val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        .setAllowCrossProtocolRedirects(true)
    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onError()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                this.player = player
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/** Gömülü mobil YouTube sayfası (akış yedekleri kapalıysa yine uygulama içinde oynatır). */
@Composable
private fun TrailerWebView(key: String) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webChromeClient = WebChromeClient()
                loadUrl("https://m.youtube.com/watch?v=$key&playsinline=1")
            }
        },
        modifier = Modifier.fillMaxSize()
    )
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
