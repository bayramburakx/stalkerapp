package com.stalkerapp.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.VodItem
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.components.ChannelRow
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.ui.vod.VodPoster
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit,
    onOpenPlayer: () -> Unit
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val profile = vm.repository.cachedProfile()
    val favChannels by vm.favoriteChannels.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var allChannels by remember { mutableStateOf<List<Channel>?>(null) }
    var vodResults by remember { mutableStateOf<List<VodItem>?>(null) }
    var loadingVod by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (profile != null && allChannels == null) {
            runCatching { allChannels = vm.repository.loadChannels(profile, 0) }
        }
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            vodResults = null
            return@LaunchedEffect
        }
        delay(400)
        if (profile != null) {
            loadingVod = true
            error = null
            try {
                vodResults = vm.repository.loadVodList(profile, 0, 1, query.trim())
            } catch (e: Exception) {
                error = e.message
            } finally {
                loadingVod = false
            }
        }
    }

    val liveFiltered = remember(query, allChannels) {
        val q = query.trim()
        if (q.isBlank()) emptyList()
        else (allChannels ?: emptyList()).filter { it.name.contains(q, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Kanal, film, dizi ara…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        if (query.isBlank()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Aramak için yazın", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        val vodList = vodResults
        val showEmpty = liveFiltered.isEmpty() && (vodList != null && vodList.isEmpty()) && !loadingVod

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (liveFiltered.isNotEmpty()) {
                item {
                    Text(
                        "Kanallar (${liveFiltered.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(liveFiltered, key = { it.id }) { ch ->
                    val isFav = favChannels.any { it.id == ch.id }
                    ChannelRow(
                        channel = ch,
                        baseUrl = profile?.baseUrl.orEmpty(),
                        isFavorite = isFav,
                        onToggleFavorite = { vm.toggleFavoriteChannel(ch) },
                        onClick = {
                            val list = liveFiltered
                            val idx = list.indexOfFirst { it.id == ch.id }
                            if (idx >= 0) {
                                PlaybackManager.playChannel(list, idx, profile ?: return@ChannelRow)
                                onOpenPlayer()
                            }
                        }
                    )
                }
            }

            if (loadingVod && vodList == null) {
                item { LoadingBox() }
            } else if (vodList != null && vodList.isNotEmpty()) {
                item {
                    Text(
                        "Film & Dizi (${vodList.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(vodList, key = { it.id }) { item ->
                            val isSeries = item.isSeries || item.seriesData.isNotBlank() || item.selectedSeason.isNotBlank()
                            VodPoster(
                                item = item,
                                baseUrl = profile?.baseUrl.orEmpty(),
                                isSeries = isSeries,
                                onClick = { onOpenVod(item.id, isSeries) },
                                width = 120
                            )
                        }
                    }
                }
            }

            if (showEmpty) {
                item {
                    EmptyState("Sonuç bulunamadı")
                }
            } else if (error != null) {
                item {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
