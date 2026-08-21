package com.stalkerapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stalkerapp.StalkerApp
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.PortioBadge
import com.stalkerapp.ui.components.PortioMediaCard
import com.stalkerapp.ui.components.resolveUrl
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.ui.theme.PortioShape

/**
 * Portio Kişi / Oyuncu / Yönetmen Detay Ekranı (PersonDetailScreen)
 */
@Composable
fun PersonDetailScreen(
    personName: String,
    isDirector: Boolean = false,
    onBack: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val catalog = vm.vodCatalog.value
    val profile = vm.repository.cachedProfile()
    val baseUrl = profile?.baseUrl.orEmpty()

    val matchingItems = remember(personName, isDirector, catalog.allItems) {
        catalog.allItems.filter { item ->
            if (isDirector) {
                item.director.contains(personName, ignoreCase = true)
            } else {
                item.actors.contains(personName, ignoreCase = true)
            }
        }
    }

    Scaffold(
        containerColor = Color.Black
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(115.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(PortioColors.Background)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Başlık & Avatar
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, PortioColors.Hairline, CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(PortioColors.SurfaceRaised)
                                .border(2.dp, PortioColors.PrimaryGlow, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = personName,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                PortioBadge(
                                    text = if (isDirector) "Yönetmen" else "Oyuncu",
                                    backgroundColor = PortioColors.PrimaryGlow
                                )
                                PortioBadge(
                                    text = "${matchingItems.size} Yapım",
                                    backgroundColor = Color.White.copy(alpha = 0.15f)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            if (matchingItems.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("Bu kişiye ait içerik bulunamadı")
                }
            } else {
                items(matchingItems, key = { it.id }) { item ->
                    val isSeries = catalog.isSeriesItem(item)
                    PortioMediaCard(
                        title = item.name,
                        posterUrl = resolveUrl(item.poster, baseUrl),
                        subtitle = item.year.take(4),
                        badgeText = if (isSeries) "DİZİ" else null,
                        badgeColor = PortioColors.BadgeSeries,
                        rating = item.rating,
                        onClick = { onOpenVod(item.id, isSeries) }
                    )
                }
            }
        }
    }
}

/** Geriye dönük uyumluluk alias fonksiyonu */
@Composable
fun PersonScreen(
    personName: String,
    isDirector: Boolean = false,
    onBack: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit
) {
    PersonDetailScreen(personName, isDirector, onBack, onOpenVod)
}
