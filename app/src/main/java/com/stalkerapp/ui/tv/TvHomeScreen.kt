package com.stalkerapp.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stalkerapp.data.Channel
import com.stalkerapp.data.VodItem
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.components.ChannelLogo
import com.stalkerapp.ui.components.resolveUrl

/**
 * Android TV 10-foot UI ana ekranı.
 * Büyük poster grid, D-pad navigasyonu ve odak sistemi ile tasarlanmıştır.
 * TvHomeScreen yalnızca TV modunda (Leanback) gösterilir.
 */
@Composable
fun TvHomeScreen(
    vm: MainViewModel,
    onOpenVod: (Long, Boolean) -> Unit,
    onOpenChannel: (Channel) -> Unit,
    onOpenSettings: () -> Unit
) {
    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()
    val favChannels by vm.favoriteChannels.collectAsStateWithLifecycle()

    val popularMovies = remember(catalog) {
        catalog.movies.sortedByDescending {
            it.rating.toFloatOrNull() ?: 0f
        }.take(20)
    }
    val popularSeries = remember(catalog) {
        catalog.series.sortedByDescending {
            it.rating.toFloatOrNull() ?: 0f
        }.take(20)
    }

    val settingsFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ---------- Üst Bar ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Portio",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 32.sp
                )
                TvFocusableButton(
                    onClick = onOpenSettings,
                    focusRequester = settingsFocusRequester
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Ayarlar", color = Color.White, fontSize = 18.sp)
                    }
                }
            }

            // ---------- Favori Kanallar ----------
            if (favChannels.isNotEmpty()) {
                TvSection(title = "Favori Kanallar") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(favChannels) { channel ->
                            TvChannelCard(
                                channel = channel,
                                onFocus = { /* mini önizleme tetikle */ },
                                onClick = { onOpenChannel(channel) }
                            )
                        }
                    }
                }
            }

            // ---------- Popüler Filmler ----------
            if (popularMovies.isNotEmpty()) {
                TvSection(title = "Popüler Filmler") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(popularMovies) { movie ->
                            TvVodCard(
                                item = movie,
                                onClick = { onOpenVod(movie.id, false) }
                            )
                        }
                    }
                }
            }

            // ---------- Popüler Diziler ----------
            if (popularSeries.isNotEmpty()) {
                TvSection(title = "Popüler Diziler") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(popularSeries) { series ->
                            TvVodCard(
                                item = series,
                                onClick = { onOpenVod(series.id, true) }
                            )
                        }
                    }
                }
            }
        }
    }

    // İlk odak
    LaunchedEffect(Unit) {
        runCatching { firstItemFocusRequester.requestFocus() }
    }
}

@Composable
private fun TvSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
            fontSize = 22.sp
        )
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TvChannelCard(
    channel: Channel,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .size(120.dp, 80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color(0xFF1A1A2E) else Color(0xFF1A1A1A))
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Color(0xFF4FC3F7) else Color.White.copy(0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.Enter) {
                    onClick(); true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        ChannelLogo(
            logo = channel.logo,
            modifier = Modifier.size(80.dp, 52.dp)
        )
        if (focused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f)))
                    )
            )
            Text(
                channel.name,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun TvVodCard(
    item: VodItem,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier.width(140.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1A1A))
                .border(
                    width = if (focused) 3.dp else 0.dp,
                    color = if (focused) Color(0xFF4FC3F7) else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                )
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .onKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown && ev.key == Key.Enter) {
                        onClick(); true
                    } else false
                }
        ) {
            AsyncImage(
                model = resolveUrl(item.poster),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            item.name,
            color = Color.White.copy(if (focused) 1f else 0.85f),
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvFocusableButton(
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color(0xFF1A1A2E) else Color.Transparent)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Color(0xFF4FC3F7) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.Enter) {
                    onClick(); true
                } else false
            }
    ) {
        content()
    }
}
