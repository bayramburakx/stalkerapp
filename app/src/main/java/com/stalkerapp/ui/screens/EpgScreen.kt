package com.stalkerapp.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.Genre
import com.stalkerapp.data.Profile
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.components.ChannelRow
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.GlassChip
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.ui.components.PortioSearchBar
import com.stalkerapp.ui.components.PortioTopAppBar
import com.stalkerapp.ui.live.EpgGuideGrid
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.util.L10n

/**
 * Portio EPG Rehberi Ekranı (EpgScreen)
 */
@Composable
fun EpgScreen(
    profile: Profile?,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val lang = vm.store.settings().language
    val favChannels by vm.favoriteChannels.collectAsStateWithLifecycle()

    var genres by remember { mutableStateOf<List<Genre>?>(null) }
    var channels by remember { mutableStateOf<List<Channel>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedGenre by remember { mutableStateOf(0L) }
    var query by remember { mutableStateOf("") }
    var viewMode by remember { mutableStateOf("grid") } // "grid" veya "list"

    LaunchedEffect(Unit) {
        loading = true
        try {
            val loaded = vm.loadChannelsForActiveSource(profile)
            if (loaded != null) {
                genres = loaded.first
                channels = loaded.second
            }
        } finally {
            loading = false
        }
    }

    val rawChannels = channels.orEmpty()
    val genreList = genres.orEmpty()
    val activeGenreTitle = genreList.firstOrNull { it.id == selectedGenre }?.title

    val displayedChannels = remember(rawChannels, selectedGenre, query, activeGenreTitle) {
        rawChannels.filter { ch ->
            val inGenre = selectedGenre <= 0L || ch.tvGenreTitle == activeGenreTitle || ch.tvGenreId == selectedGenre
            val matchQuery = query.isBlank() || ch.name.contains(query.trim(), ignoreCase = true)
            inGenre && matchQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PortioColors.Background)
    ) {
        PortioTopAppBar(
            title = L10n.t(lang, "Yayın Akışı (EPG)"),
            onBack = onBack,
            actions = {
                IconButton(onClick = { viewMode = if (viewMode == "grid") "list" else "grid" }) {
                    Icon(
                        imageVector = if (viewMode == "grid") Icons.Default.ViewList else Icons.Default.CalendarMonth,
                        contentDescription = "Görünüm Değiştir",
                        tint = Color.White
                    )
                }
            }
        )

        PortioSearchBar(
            query = query,
            onQueryChange = { query = it },
            placeholder = L10n.t(lang, "Kanal ara…"),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        if (genreList.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    GlassChip(
                        selected = selectedGenre == 0L,
                        onClick = { selectedGenre = 0L },
                        label = L10n.t(lang, "Tümü")
                    )
                }
                items(genreList.filter { it.title != "Tümü" }, key = { it.title }) { g ->
                    GlassChip(
                        selected = selectedGenre == g.id,
                        onClick = { selectedGenre = g.id },
                        label = g.title
                    )
                }
            }
        }

        when {
            loading && channels == null -> LoadingBox()
            displayedChannels.isEmpty() -> EmptyState(L10n.t(lang, "Kanal bulunamadı"))
            viewMode == "grid" -> {
                EpgGuideGrid(
                    channels = displayedChannels,
                    baseUrl = profile?.baseUrl.orEmpty(),
                    onPlayChannel = { ch ->
                        val idx = displayedChannels.indexOfFirst { it.id == ch.id }
                        if (idx >= 0) {
                            PlaybackManager.playChannel(displayedChannels, idx, profile)
                            onOpenPlayer()
                        }
                    },
                    onPlayArchive = { ch, entry ->
                        PlaybackManager.playArchive(ch, entry, profile)
                        onOpenPlayer()
                    }
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayedChannels, key = { it.id }) { ch ->
                        val isFav = favChannels.any { it.id == ch.id }
                        ChannelRow(
                            channel = ch,
                            baseUrl = profile?.baseUrl.orEmpty(),
                            isFavorite = isFav,
                            onToggleFavorite = { vm.toggleFavoriteChannel(ch) },
                            onClick = {
                                val idx = displayedChannels.indexOfFirst { it.id == ch.id }
                                if (idx >= 0) {
                                    PlaybackManager.playChannel(displayedChannels, idx, profile)
                                    onOpenPlayer()
                                }
                            }
                        )
                        HorizontalDivider(color = PortioColors.Hairline)
                    }
                }
            }
        }
    }
}

/** Geriye dönük uyumluluk alias fonksiyonu */
@Composable
fun EpgGuideScreen(
    profile: Profile?,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    EpgScreen(profile, onBack, onOpenPlayer)
}
