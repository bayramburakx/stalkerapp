package com.stalkerapp.ui.live

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.Genre
import com.stalkerapp.data.Profile
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.components.ChannelRow
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.GlassChip
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.playback.PlaybackManager
import kotlinx.coroutines.launch

@Composable
fun LiveTvScreen(
    profile: Profile?,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenGuide: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val cooldown by vm.cooldownSeconds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Aktif kanal kaynağı: Stalker portal (varsayılan) ya da M3U / Xtream.
    val kind = vm.activeSourceKind()
    val sourceId = vm.activeSourceId()
    val isExternal = kind == "m3u" || kind == "xtream"
    val sourceName = when (kind) {
        "m3u" -> vm.m3uSources().firstOrNull { it.id == sourceId }?.name
        "xtream" -> vm.xtreamSources().firstOrNull { it.id == sourceId }?.name
        else -> null
    }

    // Hiçbir kaynak eklenmemiş veya aktif kaynak türü Ayarlar'dan kapatılmış.
    if (vm.enabledSourceKind() == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(
                    "Henüz bir kaynak eklemedin",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ayarlar → Playlist & Kaynaklar bölümünden Stalker portal, M3U listesi " +
                        "veya Xtream Codes ekleyerek kanalları burada görebilirsin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    var genres by remember { mutableStateOf<List<Genre>?>(null) }
    var channels by remember { mutableStateOf<List<Channel>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedGenre by remember { mutableStateOf(0L) }
    var query by remember { mutableStateOf("") }
    // Kanal adının altında gösterilecek "şu an oynayan" programlar (harici EPG).
    var nowPlaying by remember { mutableStateOf(emptyMap<Long, String>()) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val hiddenGroups = settings.hiddenChannelGroups.toSet()

    LaunchedEffect(profile, kind, sourceId) {
        selectedGenre = 0L
        query = ""
    }

    LaunchedEffect(selectedGenre, profile, kind, sourceId) {
        loading = true
        error = null
        try {
            val loaded = vm.loadChannelsForActiveSource(profile)
            if (loaded == null) {
                error = if (isExternal) "Kaynak yüklenemedi — Ayarlar'dan kontrol edin"
                else "Portal bağlı değil"
            } else {
                val (g, ch) = loaded
                genres = g
                channels = ch
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    // Kanal listesi yüklendiğinde EPG'den "şu an oynayan" programları hazırla.
    LaunchedEffect(channels) {
        val ch = channels ?: return@LaunchedEffect
        nowPlaying = vm.repository.nowPlayingTitles(ch)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (cooldown > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Cooldown aktif — ${cooldown}s sonra istek gönderilebilir",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Aktif kaynak rozeti (Stalker dışındaki kaynaklar için).
        if (isExternal && sourceName != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    "Kaynak: ${sourceName.ifBlank { if (kind == "m3u") "M3U" else "Xtream" }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Anasayfa dili: cam arama çubuğu.
            val searchShape = RoundedCornerShape(50)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(searchShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.60f))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), searchShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isBlank()) {
                            Text(
                                "Kanal ara…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                )
            }
        }

        val genreList = genres.orEmpty().filter { it.id == 0L || it.title !in hiddenGroups }
        if (genreList.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    GlassChip(
                        selected = selectedGenre == 0L,
                        onClick = { selectedGenre = 0L },
                        label = "Tümü"
                    )
                }
                items(genreList.filter { it.id != 0L }, key = { it.id }) { g ->
                    GlassChip(
                        selected = selectedGenre == g.id,
                        onClick = { selectedGenre = g.id },
                        label = g.title
                    )
                }
            }
        }

        val allChannels = channels.orEmpty().filter { it.tvGenreTitle !in hiddenGroups }
        // Kategori filtresi tür başlığına göre yapılır (Stalker/M3U/Xtream hepsinde çalışır).
        val activeGenreTitle = genreList.firstOrNull { it.id == selectedGenre }?.title
        val filtered = allChannels.filter { ch ->
            (selectedGenre <= 0L || ch.tvGenreTitle == activeGenreTitle || ch.tvGenreId == selectedGenre) &&
                (query.isBlank() || ch.name.contains(query.trim(), ignoreCase = true))
        }

        when {
            loading && channels == null -> LoadingBox()
            error != null && channels == null -> EmptyState("$error\n\nGeri dönüp tekrar deneyin")
            allChannels.isEmpty() -> EmptyState("Kanal bulunamadı")
            filtered.isEmpty() -> EmptyState("Sonuç bulunamadı")
            else -> {
                val favChannels by vm.favoriteChannels.collectAsStateWithLifecycle()
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(filtered, key = { it.id }) { ch ->
                        val isFav = favChannels.any { it.id == ch.id }
                        ChannelRow(
                            channel = ch,
                            baseUrl = profile?.baseUrl.orEmpty(),
                            isFavorite = isFav,
                            nowPlaying = nowPlaying[ch.id],
                            onToggleFavorite = { vm.toggleFavoriteChannel(ch) },
                            onClick = {
                                scope.launch {
                                    val list = allChannels
                                    val idx = list.indexOfFirst { it.id == ch.id }
                                    if (idx >= 0) {
                                        PlaybackManager.playChannel(list, idx, profile)
                                        onOpenPlayer()
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
