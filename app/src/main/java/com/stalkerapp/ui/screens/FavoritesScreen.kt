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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Profile
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.components.ChannelRow
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.PortioButton
import com.stalkerapp.ui.components.PortioButtonStyle
import com.stalkerapp.ui.components.PortioMediaCard
import com.stalkerapp.ui.components.PortioTopAppBar
import com.stalkerapp.ui.components.SectionTitle
import com.stalkerapp.ui.components.resolveUrl
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.util.L10n

/**
 * Portio Favoriler Ekranı (FavoritesScreen)
 */
@Composable
fun FavoritesScreen(
    profile: Profile?,
    onOpenPlayer: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val lang = vm.store.settings().language
    val channels by vm.favoriteChannels.collectAsStateWithLifecycle()
    val vods by vm.favoriteVods.collectAsStateWithLifecycle()
    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()

    var isReordering by remember { mutableStateOf(false) }

    fun moveChannel(from: Int, to: Int) {
        if (from == to || from !in channels.indices || to !in channels.indices) return
        val list = channels.toMutableList()
        val moved = list.removeAt(from)
        list.add(to, moved)
        vm.store.saveFavoriteChannels(list)
        vm.refreshFlows()
    }

    val byId = remember(catalog.allItems) { catalog.allItems.associateBy { it.id } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PortioColors.Background)
    ) {
        PortioTopAppBar(
            title = L10n.t(lang, "Favorilerim"),
            onBack = onBack,
            actions = {
                if (channels.isNotEmpty()) {
                    PortioButton(
                        onClick = { isReordering = !isReordering },
                        style = PortioButtonStyle.Secondary,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            if (isReordering) L10n.t(lang, "Tamam") else L10n.t(lang, "Sırala"),
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        )

        if (channels.isEmpty() && vods.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = L10n.t(lang, "Henüz favori eklenmedi"),
                    subtitle = L10n.t(lang, "Kanal veya film kartlarındaki kalp butonuna basarak favorilerine ekleyebilirsin.")
                )
            }
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (vods.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = L10n.t(lang, "Favori Film & Diziler") + " (${vods.size})",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(vods, key = { it.id }) { item ->
                            val resolved = byId[item.id] ?: item
                            val isSeries = catalog.isSeriesItem(resolved)
                            Column(modifier = Modifier.width(120.dp)) {
                                PortioMediaCard(
                                    title = resolved.name,
                                    posterUrl = resolveUrl(resolved.poster, profile?.baseUrl.orEmpty()),
                                    subtitle = resolved.year.take(4),
                                    badgeText = if (isSeries) "DİZİ" else null,
                                    rating = resolved.rating,
                                    onClick = { onOpenVod(resolved.id, isSeries) }
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            if (channels.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = L10n.t(lang, "Favori Kanallar") + " (${channels.size})",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                itemsIndexed(channels, key = { _, ch -> ch.id }) { index, ch ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isReordering) {
                            Column(
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                IconButton(
                                    onClick = { moveChannel(index, index - 1) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Yukarı", tint = if (index > 0) Color.White else Color.Gray)
                                }
                                IconButton(
                                    onClick = { moveChannel(index, index + 1) },
                                    enabled = index < channels.size - 1,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Aşağı", tint = if (index < channels.size - 1) Color.White else Color.Gray)
                                }
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            ChannelRow(
                                channel = ch,
                                baseUrl = profile?.baseUrl.orEmpty(),
                                isFavorite = true,
                                onToggleFavorite = { vm.toggleFavoriteChannel(ch) },
                                onClick = { c ->
                                    if (!isReordering) {
                                        PlaybackManager.playChannel(channels, channels.indexOfFirst { it.id == c.id }.coerceAtLeast(0), profile)
                                        onOpenPlayer()
                                    }
                                }
                            )
                        }
                    }
                    HorizontalDivider(color = PortioColors.Hairline)
                }
            }
        }
    }
}
