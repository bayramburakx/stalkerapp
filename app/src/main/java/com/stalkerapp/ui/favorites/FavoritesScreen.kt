package com.stalkerapp.ui.favorites

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.Profile
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.components.ChannelRow
import com.stalkerapp.ui.components.GlassChip
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.vod.VodPoster
import kotlinx.coroutines.launch

private val L10nLocal: Map<String, String> = mapOf(
    "Henüz favori yok" to "No favorites yet",
    "Kanallar" to "Channels",
    "Film & Dizi" to "Movies & Series",
    "Sırala" to "Reorder",
    "Tamam" to "Done",
    "Favori kanalların sırasını yukarı/aşağı butonlarıyla değiştir" to "Change favorite channel order using up/down buttons"
)
private fun str(lang: String, text: String): String =
    if (lang == "en") L10nLocal[text] ?: text else text

@Composable
fun FavoritesScreen(
    profile: Profile?,
    onOpenPlayer: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val lang = vm.store.settings().language
    val favChannels by vm.favoriteChannels.collectAsStateWithLifecycle()
    val favVods by vm.favoriteVods.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isReordering by remember { mutableStateOf(false) }

    if (favChannels.isEmpty() && favVods.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(
                    str(lang, "Henüz favori yok"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Canlı TV veya Film/Dizi ekranında yıldız simgesine dokunarak favorilerine ekleyebilirsin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    val baseUrl = profile?.baseUrl.orEmpty()

    fun moveChannel(from: Int, to: Int) {
        if (from == to || from !in favChannels.indices || to !in favChannels.indices) return
        val list = favChannels.toMutableList()
        val moved = list.removeAt(from)
        list.add(to, moved)
        vm.store.saveFavoriteChannels(list)
        vm.refreshFlows()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        if (favVods.isNotEmpty()) {
            item {
                Text(
                    str(lang, "Film & Dizi"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(favVods, key = { it.id }) { item ->
                        VodPoster(
                            item = item,
                            baseUrl = baseUrl,
                            onClick = { onOpenVod(item.id, item.isSeries) },
                            posterWidth = 120
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        if (favChannels.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        str(lang, "Kanallar") + " (${favChannels.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    GlassChip(
                        selected = isReordering,
                        onClick = { isReordering = !isReordering },
                        label = if (isReordering) str(lang, "Tamam") else str(lang, "Sırala")
                    )
                }
            }

            if (isReordering) {
                item {
                    Text(
                        str(lang, "Favori kanalların sırasını yukarı/aşağı butonlarıyla değiştir"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }
            }

            itemsIndexed(favChannels, key = { _, ch -> ch.id }) { index, ch ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isReordering) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isReordering) {
                        Column(
                            modifier = Modifier.padding(start = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconButton(
                                onClick = { moveChannel(index, index - 1) },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Yukarı", tint = if (index > 0) MaterialTheme.colorScheme.primary else Color.Gray)
                            }
                            IconButton(
                                onClick = { moveChannel(index, index + 1) },
                                enabled = index < favChannels.size - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Aşağı", tint = if (index < favChannels.size - 1) MaterialTheme.colorScheme.primary else Color.Gray)
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ChannelRow(
                            channel = ch,
                            baseUrl = baseUrl,
                            isFavorite = true,
                            onToggleFavorite = { vm.toggleFavoriteChannel(ch) },
                            onClick = {
                                if (!isReordering) {
                                    scope.launch {
                                        val idx = favChannels.indexOfFirst { it.id == ch.id }
                                        if (idx >= 0) {
                                            PlaybackManager.playChannel(favChannels, idx, profile)
                                            onOpenPlayer()
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }
}
