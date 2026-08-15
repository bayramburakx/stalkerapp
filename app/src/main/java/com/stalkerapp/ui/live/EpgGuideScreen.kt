package com.stalkerapp.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.EpgProgram
import com.stalkerapp.data.Genre
import com.stalkerapp.data.Profile
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.components.ChannelLogo
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.GlassChip
import com.stalkerapp.ui.components.LoadingBox
import com.stalkerapp.ui.components.resolveUrl
import com.stalkerapp.ui.rememberMainViewModel

/**
 * Tam ekran EPG rehberi: kanallar + "şimdi/sonra" programları. İlk ~60 kanalın
 * EPG'si arka planda çekilir; kalanlar için "yükleniyor" gösterilir.
 */
@Composable
fun EpgGuideScreen(
    profile: Profile?,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)

    if (profile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Portal bağlı değil")
        }
        return
    }

    var genres by remember { mutableStateOf<List<Genre>?>(null) }
    var channels by remember { mutableStateOf<List<Channel>?>(null) }
    var epg by remember { mutableStateOf<Map<Long, List<EpgProgram>>>(emptyMap()) }
    var selectedGenre by remember { mutableStateOf(0L) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(profile) {
        runCatching { vm.repository.loadGenres(profile) }
            .onSuccess { genres = it }
            .onFailure { error = it.message; genres = emptyList() }
    }

    LaunchedEffect(selectedGenre, profile) {
        loading = true
        error = null
        try {
            val list = vm.repository.loadChannels(profile, selectedGenre)
            channels = list
            // EPG'yi ilk 60 kanal için çek (her kanal ayrı istek; tamamı çok yavaş olur).
            val map = mutableMapOf<Long, List<EpgProgram>>()
            list.take(60).forEach { ch ->
                val programs = runCatching { vm.repository.loadEpg(profile, ch) }
                    .getOrDefault(emptyList())
                map[ch.id] = programs
            }
            epg = map
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Üst bar: geri + başlık
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
                }
                Text(
                    "EPG Rehberi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Anasayfa dili: cam arama çubuğu.
                val searchShape = RoundedCornerShape(50)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(searchShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.60f))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), searchShape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    androidx.compose.foundation.text.BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isBlank()) {
                                Text(
                                    "Kanal ara…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                    )
                }
            }

            val genreList = genres.orEmpty()
            if (genreList.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        GlassChip(
                            selected = selectedGenre == 0L,
                            onClick = { selectedGenre = 0L },
                            label = "Tümü"
                        )
                    }
                    items(genreList) { g ->
                        GlassChip(
                            selected = selectedGenre == g.id,
                            onClick = { selectedGenre = g.id },
                            label = g.title
                        )
                    }
                }
            }

            val list = channels.orEmpty()
            val filtered = if (query.isBlank()) list
            else list.filter { it.name.contains(query.trim(), ignoreCase = true) }

            when {
                loading && channels == null -> LoadingBox()
                error != null && channels == null -> EmptyState("$error\n\nGeri dönüp tekrar deneyin")
                filtered.isEmpty() -> EmptyState("Kanal bulunamadı")
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(filtered, key = { it.id }) { ch ->
                        val programs = epg[ch.id].orEmpty()
                        val now = programs.firstOrNull { it.isCurrent }
                        val next = programs.firstOrNull { p ->
                            !p.isCurrent && p.startTs > System.currentTimeMillis() / 1000
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val idx = list.indexOfFirst { it.id == ch.id }
                                    if (idx >= 0) {
                                        PlaybackManager.playChannel(list, idx, profile)
                                        onOpenPlayer()
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ChannelLogo(
                                    logo = resolveUrl(ch.logo, profile.baseUrl),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        ch.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Kanal ${ch.number}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                                Text(
                                    when {
                                        programs.isEmpty() -> "EPG yok"
                                        programs.first().isDefault -> "Varsayılan"
                                        else -> "EPG"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (programs.isEmpty() || programs.firstOrNull()?.isDefault == true)
                                        Color.White.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                            if (now != null) {
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (now.isDefault) Color.White.copy(alpha = 0.08f)
                                            else Color(0xFF1E3A8A).copy(alpha = 0.55f)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Kanalın altında şu an oynanan program adı.
                                    Text(
                                        "● ${now.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (!now.isDefault) {
                                        Text(
                                            "${vm.repository.formatEpoch(now.startTs)} — ${vm.repository.formatEpoch(now.stopTs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF8AB4F8)
                                        )
                                    }
                                }
                            }
                            if (next != null && !next.isDefault) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${vm.repository.formatEpoch(next.startTs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.45f),
                                        modifier = Modifier.width(96.dp)
                                    )
                                    Text(
                                        next.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}
