package com.stalkerapp.ui.favorites

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.Profile
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.components.AppleHairline
import com.stalkerapp.ui.components.AppleSectionHeader
import com.stalkerapp.ui.components.AppleTvButton
import com.stalkerapp.ui.components.AppleTvButtonStyle
import com.stalkerapp.ui.components.AppleTvCard
import com.stalkerapp.ui.components.ChannelRow
import com.stalkerapp.ui.components.VodPoster
import com.stalkerapp.ui.rememberMainViewModel
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
        Box(
            modifier = modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    str(lang, "Henüz favori yok"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Canlı TV veya Film/Dizi ekranında yıldız simgesine dokunarak favorilerine ekleyebilirsin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
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

    fun playChannel(ch: Channel) {
        if (isReordering) return
        scope.launch {
            val idx = favChannels.indexOfFirst { it.id == ch.id }
            if (idx >= 0) {
                PlaybackManager.playChannel(favChannels, idx, profile)
                onOpenPlayer()
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        if (favVods.isNotEmpty()) {
            item {
                AppleSectionHeader(
                    title = str(lang, "Film & Dizi"),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
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
                Spacer(Modifier.height(24.dp))
            }
        }

        if (favChannels.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        str(lang, "Kanallar") + " (${favChannels.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    AppleTvButton(
                        onClick = { isReordering = !isReordering },
                        style = AppleTvButtonStyle.Secondary
                    ) {
                        Text(
                            if (isReordering) str(lang, "Tamam") else str(lang, "Sırala"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (isReordering) {
                item {
                    Text(
                        str(lang, "Favori kanalların sırasını yukarı/aşağı butonlarıyla değiştir"),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }
            }

            itemsIndexed(favChannels, key = { _, ch -> ch.id }) { index, ch ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isReordering) {
                        Column(
                            modifier = Modifier.padding(end = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconButton(
                                onClick = { moveChannel(index, index - 1) },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Yukarı",
                                    tint = if (index > 0) Color.White else Color.White.copy(alpha = 0.3f)
                                )
                            }
                            IconButton(
                                onClick = { moveChannel(index, index + 1) },
                                enabled = index < favChannels.size - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Aşağı",
                                    tint = if (index < favChannels.size - 1) Color.White else Color.White.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }

                    AppleTvCard(
                        modifier = Modifier.weight(1f),
                        onClick = { playChannel(ch) }
                    ) {
                        ChannelRow(
                            channel = ch,
                            baseUrl = baseUrl,
                            isFavorite = true,
                            onToggleFavorite = { vm.toggleFavoriteChannel(ch) },
                            onClick = { playChannel(ch) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            item { AppleHairline(Modifier.padding(top = 16.dp)) }
        }
    }
}
