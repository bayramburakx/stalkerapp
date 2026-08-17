package com.stalkerapp.ui.person

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.TmdbClient
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.resolveUrl
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.vod.VodPoster

private val L10nLocal: Map<String, String> = mapOf(
    // Türkçe -> English
    "Geri" to "Back",
    "Yönetmen" to "Director",
    "Oyuncu" to "Actor",
    "içerik" to "items",
    "İçerik bulunamadı" to "No content found",
)
private fun str(lang: String, text: String): String =
    if (lang == "en") L10nLocal[text] ?: text else text

/**
 * Oyuncu / Yönetmen sayfası: TMDB'den fotoğraf (anahtar varsa), katalogdan
 * filmografi. Oyuncu adına katalogdaki `actors`, yönetmene `director` alanından
 * eşleşme yapılır.
 */
@Composable
fun PersonScreen(
    name: String,
    isDirector: Boolean,
    onBack: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val lang = vm.store.settings().language
    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()
    val profile = vm.repository.cachedProfile()

    var photoUrl by remember { mutableStateOf("") }
    var tmdbName by remember { mutableStateOf(name) }

    LaunchedEffect(name) {
        val key = app.store.settings().tmdbApiKey
        if (key.isNotBlank()) {
            val p = app.tmdb.searchPerson(name, key)
            if (p != null) {
                photoUrl = TmdbClient.photoUrl(p.photoPath, large = true)
                if (p.name.isNotBlank()) tmdbName = p.name
            }
        }
    }

    val works = remember(catalog.allItems, name, isDirector) {
        catalog.allItems.filter { item ->
            if (isDirector) {
                norm(item.director).contains(norm(name))
            } else {
                item.actors.split(",").any { a -> norm(a.trim()).contains(norm(name)) }
            }
        }
    }
    val baseUrl = profile?.baseUrl.orEmpty()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 84.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUrl.isNotBlank()) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = tmdbName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(120.dp)
                            )
                        } else {
                            Text(
                                initials(tmdbName),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        tmdbName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    Text(
                        if (isDirector) str(lang, "Yönetmen") else str(lang, "Oyuncu"),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            item {
                Text(
                    "${works.size} ${str(lang, "içerik")}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (works.isEmpty()) {
                item { EmptyState(str(lang, "İçerik bulunamadı")) }
            } else {
                items(works.chunked(3)) { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { item ->
                            Box(modifier = Modifier.weight(1f)) {
                                VodPoster(
                                    item = item,
                                    baseUrl = baseUrl,
                                    isSeries = catalog.isSeriesItem(item),
                                    onClick = { onOpenVod(item.id, catalog.isSeriesItem(item)) }
                                )
                            }
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Üst bar: şeffaf, sadece geri tuşu.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 6.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = str(lang, "Geri"), tint = Color.White)
            }
        }
    }
}

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return "?"
    val first = parts.first().firstOrNull()?.uppercase() ?: ""
    val second = if (parts.size > 1) parts[1].firstOrNull()?.uppercase() ?: "" else ""
    return first + second
}

/**
 * Türkçe karakterleri sadeleştirip küçük harfe çevirir; aksan/yazım farkı
 * kaynaklı filmografi eşleşmelerini yakalamak için kullanılır.
 */
private fun norm(s: String): String =
    s.lowercase()
        .replace('ı', 'i')
        .replace('ş', 's')
        .replace('ğ', 'g')
        .replace('ü', 'u')
        .replace('ö', 'o')
        .replace('ç', 'c')
