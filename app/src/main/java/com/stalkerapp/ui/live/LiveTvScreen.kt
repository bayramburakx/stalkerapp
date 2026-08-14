package com.stalkerapp.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.Genre
import com.stalkerapp.data.Profile
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.components.ChannelRow
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.playback.PlaybackManager
import kotlinx.coroutines.launch

@Composable
fun LiveTvScreen(
    profile: Profile?,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (profile == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Portal bağlı değil")
        }
        return
    }

    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val cooldown by vm.cooldownSeconds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var genres by remember { mutableStateOf<List<Genre>?>(null) }
    var channels by remember { mutableStateOf<List<Channel>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedGenre by remember { mutableStateOf(0L) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(profile) {
        try {
            genres = vm.repository.loadGenres(profile)
        } catch (e: Exception) {
            error = e.message
            genres = emptyList()
        }
    }

    LaunchedEffect(selectedGenre, profile) {
        loading = true
        error = null
        try {
            channels = vm.repository.loadChannels(profile, selectedGenre)
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
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

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Kanal ara…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )

        val genreList = genres.orEmpty()
        if (genreList.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedGenre == 0L,
                        onClick = { selectedGenre = 0L },
                        label = { Text("Tümü") }
                    )
                }
                items(genreList) { g ->
                    FilterChip(
                        selected = selectedGenre == g.id,
                        onClick = { selectedGenre = g.id },
                        label = { Text(g.title) }
                    )
                }
            }
        }

        when {
            loading && channels == null -> LoadingBox()
            error != null -> EmptyState("$error\n\nGeri dönüp tekrar deneyin")
            channels.orEmpty().isEmpty() -> EmptyState("Kanal bulunamadı")
            else -> {
                val favChannels by vm.favoriteChannels.collectAsStateWithLifecycle()
                val filtered = channels.orEmpty().let { list ->
                    if (query.isBlank()) list
                    else list.filter { it.name.contains(query.trim(), ignoreCase = true) }
                }
                // Alt boşluk: içerik yüzen cam pill'in arkasından akıyor, son
                // kanalın pill altında kaybolmaması için.
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(filtered, key = { it.id }) { ch ->
                        val isFav = favChannels.any { it.id == ch.id }
                        ChannelRow(
                            channel = ch,
                            baseUrl = profile.baseUrl,
                            isFavorite = isFav,
                            onToggleFavorite = { vm.toggleFavoriteChannel(ch) },
                            onClick = {
                                scope.launch {
                                    val list = channels.orEmpty()
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
