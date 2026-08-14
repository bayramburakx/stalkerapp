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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.VodItem
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.VodCatalogStatus
import com.stalkerapp.ui.components.ChannelRow
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.ui.rememberMainViewModel
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
    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var allChannels by remember { mutableStateOf<List<Channel>?>(null) }
    var vodResults by remember { mutableStateOf<List<VodItem>?>(null) }
    var loadingVod by remember { mutableStateOf(false) }

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
        delay(300)
        loadingVod = true
        try {
            val q = query.trim()
            val cat = vm.vodCatalog.value
            vodResults = if (cat.allItems.isNotEmpty()) {
                cat.allItems.filter {
                    it.name.contains(q, ignoreCase = true) ||
                        it.originalName.contains(q, ignoreCase = true)
                }.take(80)
            } else if (profile != null) {
                runCatching { vm.repository.loadVodList(profile, 0, 1, q) }.getOrDefault(emptyList())
            } else emptyList()
        } finally {
            loadingVod = false
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
                        placeholder = { Text("Film, dizi, kanal ara…") },
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
            DiscoverContent(
                catalog = catalog,
                baseUrl = profile?.baseUrl.orEmpty(),
                onOpenVod = onOpenVod,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            return@Scaffold
        }

        val vodList = vodResults
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (liveFiltered.isNotEmpty()) {
                item {
                    Text("Kanallar (${liveFiltered.size})", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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
                    Text("Film & Dizi (${vodList.size})", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                item {
                    LazyRow(contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(vodList, key = { it.id }) { item ->
                            val isSeries = item.isSeries || item.seriesRef.isNotBlank()
                            VodPoster(item = item, baseUrl = profile?.baseUrl.orEmpty(),
                                isSeries = isSeries, onClick = { onOpenVod(item.id, isSeries) }, posterWidth = 120)
                        }
                    }
                }
            } else if (liveFiltered.isEmpty() && vodList != null && vodList.isEmpty() && !loadingVod) {
                item { EmptyState("Sonuç bulunamadı") }
            }
        }
    }
}

@Composable
private fun DiscoverContent(
    catalog: com.stalkerapp.ui.VodCatalogState,
    baseUrl: String,
    onOpenVod: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var typeFilter by remember { mutableStateOf(0) } // 0 tümü, 1 film, 2 dizi, 3 belgesel
    var genreFilter by remember { mutableStateOf<Long?>(null) }

    val catTitle = remember(catalog.categories) { catalog.categories.associate { it.id to it.title } }
    val types = listOf("Tümü", "Film", "Dizi", "Belgesel")
    val genres = catalog.categories.take(14)

    val discover = remember(catalog, typeFilter, genreFilter) {
        catalog.allItems.filter { item ->
            val isSeries = catalog.isSeriesItem(item)
            val okType = when (typeFilter) {
                1 -> !isSeries
                2 -> isSeries
                3 -> catTitle[item.categoryId]?.let { t ->
                    t.contains("belgesel", ignoreCase = true) || t.contains("documentary", ignoreCase = true)
                } ?: false
                else -> true
            }
            val okGenre = genreFilter == null || item.categoryId == genreFilter
            okType && okGenre
        }.take(150)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            "Keşfet",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(types) { t ->
                val idx = types.indexOf(t)
                FilterChip(
                    selected = typeFilter == idx,
                    onClick = { typeFilter = idx },
                    label = { Text(t) }
                )
            }
        }
        if (genres.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(genres, key = { it.id }) { g ->
                    FilterChip(
                        selected = genreFilter == g.id,
                        onClick = { genreFilter = if (genreFilter == g.id) null else g.id },
                        label = { Text(g.title) }
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when {
                catalog.status == VodCatalogStatus.Syncing && catalog.allItems.isEmpty() -> LoadingBox()
                discover.isEmpty() -> EmptyState("İçerik bulunamadı")
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(discover, key = { it.id }) { item ->
                        val isSeries = item.isSeries || item.seriesRef.isNotBlank()
                        VodPoster(
                            item = item,
                            baseUrl = baseUrl,
                            isSeries = isSeries,
                            onClick = { onOpenVod(item.id, isSeries) }
                        )
                    }
                }
            }
        }
    }
}
