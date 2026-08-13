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

@Composable
fun VodScreen(
    profile: Profile?,
    onOpenVod: (Long) -> Unit,
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
    val vm: MainViewModel = viewModel { MainViewModel(app) }

    var categories by remember { mutableStateOf<List<Genre>?>(null) }
    var items by remember { mutableStateOf<List<VodItem>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf(0L) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            categories = vm.repository.loadVodCategories(profile)
            if (filterIsSeries == true && selectedCategory == 0L) {
                categories?.firstOrNull { it.title.contains("dizi", ignoreCase = true) }
                    ?.let { selectedCategory = it.id }
            }
        } catch (e: Exception) {
            error = e.message
            categories = emptyList()
        }
    }

    LaunchedEffect(selectedCategory) {
        loading = true
        error = null
        try {
            items = vm.repository.loadVodList(profile, selectedCategory)
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
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
                if (filterIsSeries != true) {
                    item {
                        FilterChip(
                            selected = selectedCategory == 0L,
                            onClick = { selectedCategory = 0L },
                            label = { Text("Tümü") }
                        )
                    }
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
                val typeFiltered = items.orEmpty()
                val filtered = typeFiltered.let { list ->
                    if (query.isBlank()) list
                    else list.filter {
                        it.name.contains(query.trim(), ignoreCase = true) ||
                            it.originalName.contains(query.trim(), ignoreCase = true)
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        VodPoster(item = item, baseUrl = profile.baseUrl, onClick = { onOpenVod(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun VodPoster(item: VodItem, baseUrl: String, onClick: () -> Unit, width: Int? = null) {
    Card(modifier = Modifier.then(if (width != null) Modifier.width(width.dp) else Modifier.fillMaxWidth()).clickable(onClick = onClick)) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                AsyncImage(
                    model = resolveUrl(item.poster, baseUrl),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (item.isSeries) {
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
