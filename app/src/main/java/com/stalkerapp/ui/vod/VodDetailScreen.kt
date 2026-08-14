package com.stalkerapp.ui.vod

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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

@OptIn(ExperimentalMaterial3Api::class)
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
                    description = info.description.ifBlank { base.description }
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
        try {
            episodes = vm.repository.loadEpisodes(p, it.id, sid)
        } catch (e: Exception) {
            error = e.message
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
    val favVods by vm.favoriteVods.collectAsStateWithLifecycle()
    val isFavorite = remember(favVods, it) { it != null && favVods.any { f -> f.id == it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(it.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    IconButton(onClick = { it.let { v -> vm.toggleFavoriteVod(v) } }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favori",
                            tint = if (isFavorite) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    AsyncImage(
                        model = resolveUrl(it.poster, profile?.baseUrl.orEmpty()),
                        contentDescription = it.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 140.dp, height = 200.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(it.name, style = MaterialTheme.typography.titleLarge)
                        it.originalName.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        val categoryTitle = catalog.categories.firstOrNull { c -> c.id == it.categoryId }?.title
                        listOfNotNull(
                            categoryTitle?.let { "Kategori: $it" },
                            it.year.takeIf { it.isNotBlank() }?.let { "Yıl: $it" },
                            it.country.takeIf { it.isNotBlank() }?.let { "Ülke: $it" },
                            it.genres.takeIf { it.isNotBlank() }?.let { "Tür: $it" },
                            it.director.takeIf { it.isNotBlank() }?.let { "Yönetmen: $it" },
                            it.actors.takeIf { it.isNotBlank() }?.let { "Oyuncular: $it" }
                        ).forEach {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (it.rating.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(" IMDb: ${it.rating}", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { play() },
                    enabled = !playing,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    if (playing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("İzle")
                    }
                }
            }
            if (it.description.isNotBlank()) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Konu", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(it.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (isSeries) {
                item {
                    // Horizontal scroll: series can have many seasons (e.g. 8+) that
                    // don't fit on one screen — chips must scroll instead of overflowing.
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(seasons, key = { it.id }) { s ->
                            FilterChip(
                                selected = selectedSeason == s.id,
                                onClick = { selectedSeason = s.id },
                                label = { Text(s.name.ifBlank { s.id.toString() }) }
                            )
                        }
                    }
                }
                when {
                    episodes == null && loading -> item { LoadingBox() }
                    episodes.orEmpty().isEmpty() -> item { EmptyState("Bölüm bulunamadı") }
                    else -> items(episodes.orEmpty()) { ep ->
                        Text(
                            text = "${ep.episodeNumber}. ${ep.name.ifBlank { "Bölüm" }}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { play(ep) }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }
            if (error != null) {
                item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
