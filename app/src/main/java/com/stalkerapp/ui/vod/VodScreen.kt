package com.stalkerapp.ui.vod

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Profile
import com.stalkerapp.data.VodItem
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.VodCatalogStatus
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
    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()

    var selectedCategory by remember { mutableStateOf(0L) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(profile) {
        vm.syncVodIfNeeded(profile)
    }

    val filtered = remember(catalog.allItems, selectedCategory, query, filterIsSeries) {
        val q = query.trim()
        catalog.allItems
            .let { list ->
                when (filterIsSeries) {
                    true -> list.filter { catalog.isSeriesItem(it) }
                    false -> list.filter { !catalog.isSeriesItem(it) }
                    else -> list
                }
            }
            .let { list -> if (selectedCategory > 0L) list.filter { it.categoryId == selectedCategory } else list }
            .let { list ->
                if (q.isBlank()) list
                else list.filter {
                    it.name.contains(q, ignoreCase = true) ||
                        it.originalName.contains(q, ignoreCase = true)
                }
            }
    }

    val catList = catalog.categories
    val seriesCatIds = remember(catalog) {
        catalog.allItems.filter { catalog.isSeriesItem(it) }.map { it.categoryId }.toSet()
    }
    val shownCats = when (filterIsSeries) {
        true -> catList.filter { it.id in seriesCatIds }
        false -> catList.filter { it.id !in seriesCatIds }
        else -> catList
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (catalog.status == VodCatalogStatus.Error) {
            Text(
                "VOD senkron hatası. Yenileme için kategorileri açın.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Film / dizi ara…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { vm.syncVodCatalog(profile, force = true) },
                enabled = catalog.status != VodCatalogStatus.Syncing
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Kataloğu yenile",
                    tint = if (catalog.status == VodCatalogStatus.Syncing) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.primary
                )
            }
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
            catalog.status == VodCatalogStatus.Syncing && catalog.allItems.isEmpty() -> LoadingBox()
            catalog.allItems.isEmpty() && catalog.status != VodCatalogStatus.Syncing ->
                EmptyState("VOD içeriği bulunamadı")
            filtered.isEmpty() -> EmptyState("İçerik bulunamadı")
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    // Alt boşluk: içerik yüzen cam pill'in arkasından akıyor, son
                    // posterin pill altında kaybolmaması için.
                    contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.id }) { item ->
                        val isSeries = filterIsSeries == true || catalog.isSeriesItem(item)
                        VodPoster(
                            item = item,
                            baseUrl = profile.baseUrl,
                            isSeries = isSeries,
                            onClick = { onOpenVod(item.id, isSeries) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VodPoster(item: VodItem, baseUrl: String, onClick: () -> Unit, isSeries: Boolean = item.isSeries, posterWidth: Int? = null) {
    Card(
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .then(if (posterWidth != null) Modifier.width(posterWidth.dp) else Modifier.fillMaxWidth())
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                AsyncImage(
                    model = resolveUrl(item.poster, baseUrl),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
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
            Column(modifier = Modifier.padding(6.dp).heightIn(min = 46.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(item.year, item.rating).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
