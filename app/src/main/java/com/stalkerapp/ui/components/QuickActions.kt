package com.stalkerapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stalkerapp.data.VodItem
import com.stalkerapp.ui.MainViewModel

private val L10nLocal: Map<String, String> = mapOf(
    // Türkçe -> English
    "Dizi" to "Series",
    "Film" to "Movie",
    "Favorilerden Çıkar" to "Remove from Favorites",
    "Favorilere Ekle" to "Add to Favorites",
    "Sonra İzle'den Çıkar" to "Remove from Watch Later",
    "Sonra İzle'ye Ekle" to "Add to Watch Later",
    "İzlenmedi İşaretle" to "Mark as Not Watched",
    "İzlendi İşaretle" to "Mark as Watched",
    "Listeleri Gizle" to "Hide Lists",
    "Listeye Ekle" to "Add to List",
    "Henüz liste yok. Ayarlar → Kütüphanem bölümünden liste oluşturabilirsin." to "No lists yet. Create lists from Settings → My Library.",
    "Ana Sayfadan Kaldır" to "Remove from Home",
    "Detayları Gör" to "View Details"
)
private fun str(lang: String, text: String): String =
    if (lang == "en") L10nLocal[text] ?: text else text

/**
 * Uzun basma ile açılan hızlı işlemler: favori, izlendi işaretle/geri al, detay.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VodQuickActionsSheet(
    lang: String,
    item: VodItem,
    isSeries: Boolean,
    vm: MainViewModel,
    onOpenDetail: () -> Unit,
    onDismiss: () -> Unit
) {
    val favVods by vm.favoriteVods.collectAsStateWithLifecycle()
    val watchLater by vm.watchLater.collectAsStateWithLifecycle()
    val isFav = remember(favVods, item) { favVods.any { it.id == item.id } }
    val isWatchLater = remember(watchLater, item) { watchLater.any { it.id == item.id } }
    var showLists by remember { mutableStateOf(false) }
    val progress = vm.store.loadVodProgress()[item.id]
    val watchedByProgress = progress != null && progress.durationMs > 0 &&
        progress.positionMs >= progress.durationMs * 0.85
    var watched by remember { mutableStateOf(vm.store.isWatchedOverride(item.id) || watchedByProgress) }
    val baseUrl = vm.repository.cachedProfile()?.baseUrl.orEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (item.poster.isNotBlank()) {
                        AsyncImage(
                            model = resolveUrl(item.poster, baseUrl),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (isSeries) str(lang, "Dizi") else str(lang, "Film"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            QuickActionRow(
                icon = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                tint = if (isFav) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface,
                label = if (isFav) str(lang, "Favorilerden Çıkar") else str(lang, "Favorilere Ekle")
            ) {
                vm.toggleFavoriteVod(item)
                onDismiss()
            }
            QuickActionRow(
                icon = Icons.Default.Schedule,
                tint = MaterialTheme.colorScheme.onSurface,
                label = if (isWatchLater) str(lang, "Sonra İzle'den Çıkar") else str(lang, "Sonra İzle'ye Ekle")
            ) {
                vm.toggleWatchLater(item)
                onDismiss()
            }
            QuickActionRow(
                icon = Icons.Default.CheckCircle,
                tint = if (watched) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
                label = if (watched) str(lang, "İzlenmedi İşaretle") else str(lang, "İzlendi İşaretle")
            ) {
                watched = vm.toggleWatched(item.id)
                onDismiss()
            }
            QuickActionRow(
                icon = Icons.Default.List,
                tint = MaterialTheme.colorScheme.onSurface,
                label = if (showLists) str(lang, "Listeleri Gizle") else str(lang, "Listeye Ekle")
            ) {
                showLists = !showLists
            }
            if (showLists) {
                val userLists = vm.userLists.value
                if (userLists.isEmpty()) {
                    Text(
                        str(lang, "Henüz liste yok. Ayarlar → Kütüphanem bölümünden liste oluşturabilirsin."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 36.dp, vertical = 6.dp)
                    )
                } else {
                    userLists.forEach { l ->
                        val inList = l.itemIds.contains(item.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.toggleInUserList(l.id, item)
                                }
                                .padding(horizontal = 36.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${if (inList) "☑ " else "☐ "}${l.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            // Ana sayfadan kaldır: "Son İzlenenler" / "İzlemeye Devam" bölümlerinde
            // bu medya bir daha görünmez (izleme ilerlemesi korunur).
            QuickActionRow(
                icon = Icons.Default.Home,
                tint = MaterialTheme.colorScheme.error,
                label = str(lang, "Ana Sayfadan Kaldır")
            ) {
                vm.hideFromHome(item.id)
                onDismiss()
            }
            QuickActionRow(
                icon = Icons.Default.Info,
                tint = MaterialTheme.colorScheme.onSurface,
                label = str(lang, "Detayları Gör")
            ) {
                onDismiss()
                onOpenDetail()
            }
        }
    }
}

@Composable
private fun QuickActionRow(
    icon: ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
