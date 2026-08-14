package com.stalkerapp.ui.vod

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Genre
import com.stalkerapp.data.Profile
import com.stalkerapp.data.VodItem
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.ui.components.resolveUrl
import com.stalkerapp.ui.rememberMainViewModel

@Composable
fun VodScreen(
    profile: Profile?,
    onOpenVod: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    filterIsSeries: Boolean? = null
) {
    if (profile == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Portal bağlı değil")
        }
        return
    }

    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)

    var categories by remember { mutableStateOf<List<Genre>?>(null) }
    var items by remember { mutableStateOf<List<VodItem>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var page by remember { mutableStateOf(1) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf(0L) }
    var query by remember { mutableStateOf("") }
    val gridState = rememberLazyGridState()

    val seriesCatIds = remember(categories) {
        categories.orEmpty().filter { it.title.contains("dizi", ignoreCase = true) }.map { it.id }.toSet()
    }

    suspend fun loadPage(page: Int, search: String): List<VodItem> {
        return if (filterIsSeries == true) {
            if (selectedCategory > 0L) {
                vm.repository.loadVodList(profile, selectedCategory, page, search)
            } else {
                seriesCatIds.flatMap { catId ->
                    runCatching { vm.repository.loadVodList(profile, catId, page, search) }
                        .getOrDefault(emptyList())
                }.distinctBy { it.id }
            }
        } else {
            val cat = if (search.isNotBlank()) 0L else selectedCategory
            vm.repository.loadVodList(profile, cat, page, search)
        }
    }

    fun hasMoreFromTotal(newItems: List<VodItem>): Boolean {
        if (filterIsSeries == true && selectedCategory == 0L) return newItems.isNotEmpty()
        val cat = if (query.isNotBlank()) 0L else selectedCategory
        return newItems.size < vm.repository.vodTotal(profile, cat, query.trim())
    }

    LaunchedEffect(Unit) {
        try {
            categories = vm.repository.loadVodCategories(profile)
        } catch (e: Exception) {
            error = e.message
            categories = emptyList()
        }
    }

    LaunchedEffect(selectedCategory, query) {
        loading = true
        error = null
        page = 1
        hasMore = true
        loadingMore = false
        if (query.isNotBlank()) kotlinx.coroutines.delay(400)
        try {
            items = loadPage(1, query.trim())
            hasMore = hasMoreFromTotal(items.orEmpty())
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo }
            .collect { info ->
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                val total = info.totalItemsCount
                if (total > 0 && last >= total - 4 && hasMore && !loadingMore && !loading && items?.isNotEmpty() == true) {
                    loadingMore = true
                    try {
                        page += 1
                        val more = loadPage(page, query.trim())
                        val existingIds = items.orEmpty().map { it.id }.toSet()
                        val newOnes = more.filter { it.id !in existingIds }
                        items = items.orEmpty() + newOnes
                        if (newOnes.isEmpty()) {
                            hasMore = false
                        } else {
                            hasMore = hasMoreFromTotal(items.orEmpty())
                        }
                    } catch (e: Exception) {
                        page -= 1
                        error = e.message
                    } finally {
                        loadingMore = false
                    }
                }
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Film / dizi ara…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )

        val catList = categories.orEmpty()
        val shownCats = when (filterIsSeries) {
            true -> catList.filter { it.title.contains("dizi", ignoreCase = true) }
            false -> catList.filter { !it.title.contains("dizi", ignoreCase = true) }
            else -> catList
        }
        if (shownCats.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == 0L && query.isBlank(),
                        onClick = { selectedCategory = 0L },
                        label = { Text("Tümü") }
                    )
                }
                items(shownCats) { c ->
                    FilterChip(
                        selected = selectedCategory == c.id,
                        onClick = { selectedCategory = c.id },
                        label = { Text(c.title) }
                    )
                }
            }
        }

        when {
            loading && items == null -> LoadingBox()
            error != null -> EmptyState("$error\n\nGeri dönüp tekrar deneyin")
            items.orEmpty().isEmpty() -> EmptyState("İçerik bulunamadı")
            else -> {
                val isSeriesItem: (VodItem) -> Boolean = { it ->
                    it.isSeries || it.seriesData.isNotBlank() || it.selectedSeason.isNotBlank() || seriesCatIds.contains(selectedCategory)
                }
                val typeFiltered = if (filterIsSeries == true) {
                    items.orEmpty()
                } else {
                    items.orEmpty().filter { !isSeriesItem(it) }
                }
                val filtered = typeFiltered.let { list ->
                    if (query.isBlank()) list
                    else list.filter {
                        it.name.contains(query.trim(), ignoreCase = true) ||
                            it.originalName.contains(query.trim(), ignoreCase = true)
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        VodPoster(
                            item = item,
                            baseUrl = profile.baseUrl,
                            isSeries = filterIsSeries == true || item.isSeries,
                            onClick = { onOpenVod(item.id, filterIsSeries == true || isSeriesItem(item)) }
                        )
                    }
                    if (loadingMore) {
                        item { LoadingBox() }
                    }
                }
            }
        }
    }
}

@Composable
fun VodPoster(item: VodItem, baseUrl: String, onClick: () -> Unit, isSeries: Boolean = item.isSeries, width: Int? = null) {
    Card(modifier = Modifier.then(if (width != null) Modifier.width(width.dp) else Modifier.fillMaxWidth()).clickable(onClick = onClick)) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                AsyncImage(
                    model = resolveUrl(item.poster, baseUrl),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (isSeries) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color(0xFFE50914), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "DİZİ",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(6.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(item.year, item.rating).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
