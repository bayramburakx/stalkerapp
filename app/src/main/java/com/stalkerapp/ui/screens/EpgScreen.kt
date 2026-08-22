package com.stalkerapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.CatchupHelper
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
import com.stalkerapp.ui.components.PortioBadge
import com.stalkerapp.ui.components.PortioButton
import com.stalkerapp.ui.components.PortioCard
import com.stalkerapp.ui.components.PortioPrimaryButton
import com.stalkerapp.ui.components.PortioSearchBar
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.ui.theme.PortioShape
import com.stalkerapp.util.L10n
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Portio EPG Rehberi Ekranı (EpgScreen) - Zaman Çizelgeli EPG Kılavuzu
 */
@Composable
fun EpgScreen(
    profile: Profile?,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val lang = vm.store.settings().language
    val baseUrl = profile?.baseUrl.orEmpty()

    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var genres by remember { mutableStateOf<List<Genre>>(emptyList()) }
    var selectedGenreId by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var selectedProgram by remember { mutableStateOf<Pair<Channel, EpgProgram>?>(null) }

    LaunchedEffect(profile) {
        if (profile == null) return@LaunchedEffect
        loading = true
        val chList = runCatching { vm.repository.loadChannels(profile) }.getOrDefault(emptyList())
        val gList = runCatching { vm.repository.loadGenres(profile) }.getOrDefault(emptyList())
        channels = chList
        genres = gList
        loading = false
    }

    val filteredChannels = remember(channels, selectedGenreId, searchQuery) {
        channels.filter { ch ->
            val matchGenre = selectedGenreId == null || ch.tvGenreId == selectedGenreId.toString() || ch.tvGenreTitle == genres.firstOrNull { it.id == selectedGenreId }?.title
            val matchSearch = searchQuery.isBlank() || ch.name.contains(searchQuery, ignoreCase = true)
            matchGenre && matchSearch
        }
    }

    Scaffold(
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PortioColors.Background)
                .statusBarsPadding()
        ) {
            // 1. Üst Bar: Geri Butonu + Başlık + Arama
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, PortioColors.Hairline, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = L10n.t(lang, "Yayın Akışı & EPG"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }

            // Arama Çubuğu
            PortioSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = L10n.t(lang, "Kanal veya program ara..."),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Kategori Çipleri
            if (genres.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        GlassChip(
                            selected = selectedGenreId == null,
                            onClick = { selectedGenreId = null },
                            label = L10n.t(lang, "Tümü")
                        )
                    }
                    items(genres, key = { it.id }) { g ->
                        GlassChip(
                            selected = selectedGenreId == g.id,
                            onClick = { selectedGenreId = g.id },
                            label = g.title
                        )
                    }
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingBox()
                }
            } else if (filteredChannels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(L10n.t(lang, "Kanal bulunamadı"))
                }
            } else {
                // Kanal Listesi & EPG Programları
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredChannels, key = { it.id }) { ch ->
                        EpgChannelItem(
                            channel = ch,
                            profile = profile,
                            vm = vm,
                            baseUrl = baseUrl,
                            onPlayChannel = {
                                val idx = channels.indexOfFirst { it.id == ch.id }
                                if (idx >= 0) {
                                    PlaybackManager.playChannel(channels, idx, profile)
                                    onOpenPlayer()
                                }
                            },
                            onSelectProgram = { prog ->
                                selectedProgram = ch to prog
                            }
                        )
                    }
                }
            }
        }
    }

    // Program Detay & Oynatma Dialogu
    selectedProgram?.let { (ch, prog) ->
        val now = System.currentTimeMillis()
        val isLive = now in prog.startTs..prog.stopTs
        val isPast = now > prog.stopTs && (ch.isTvArchive || ch.archiveDuration > 0)

        AlertDialog(
            onDismissRequest = { selectedProgram = null },
            containerColor = PortioColors.SurfaceElevated,
            title = {
                Text(prog.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${ch.name} • ${prog.timeFormatted()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = PortioColors.Accent,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (prog.desc.isNotBlank()) {
                        Text(prog.desc, style = MaterialTheme.typography.bodyMedium, color = PortioColors.TextSecondary)
                    }
                    if (isLive) {
                        PortioBadge(text = "CANLI YAYIN", backgroundColor = PortioColors.AccentRed)
                    } else if (isPast) {
                        PortioBadge(text = "TEKRAR İZLE (KAYIT)", backgroundColor = PortioColors.AccentBlue)
                    }
                }
            },
            confirmButton = {
                if (isLive) {
                    Button(
                        onClick = {
                            selectedProgram = null
                            val idx = channels.indexOfFirst { it.id == ch.id }
                            if (idx >= 0) {
                                PlaybackManager.playChannel(channels, idx, profile)
                                onOpenPlayer()
                            }
                        }
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(L10n.t(lang, "Canlı İzle"))
                    }
                } else if (isPast) {
                    Button(
                        onClick = {
                            selectedProgram = null
                            val catchupUrl = CatchupHelper.buildStalkerCatchupUrl(ch.cmd, prog.startTs / 1000, prog.stopTs / 1000)
                            PlaybackManager.play(catchupUrl, prog.name, ch.logo, ch.name)
                            onOpenPlayer()
                        }
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(L10n.t(lang, "Kaydı Oynat"))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedProgram = null }) {
                    Text(L10n.t(lang, "Kapat"), color = Color.White)
                }
            }
        )
    }
}

@Composable
private fun EpgChannelItem(
    channel: Channel,
    profile: Profile?,
    vm: MainViewModel,
    baseUrl: String,
    onPlayChannel: () -> Unit,
    onSelectProgram: (EpgProgram) -> Unit
) {
    var programs by remember { mutableStateOf<List<EpgProgram>>(emptyList()) }
    LaunchedEffect(channel.id) {
        if (profile != null) {
            val list = runCatching { vm.repository.loadEpg(profile, channel) }.getOrDefault(emptyList())
            programs = list
        }
    }

    PortioCard(
        onClick = onPlayChannel,
        shape = PortioShape.Card,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) { isFocused ->
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(logo = channel.logo, channelName = channel.name, baseUrl = baseUrl)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = channel.tvGenreTitle.ifBlank { "Canlı Kanal" },
                        style = MaterialTheme.typography.labelSmall,
                        color = PortioColors.TextMuted
                    )
                }
            }

            // EPG Program Yatay Şeridi
            if (programs.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(programs.take(8), key = { "${it.chId}_${it.startTs}" }) { prog ->
                        val now = System.currentTimeMillis()
                        val isLive = now in prog.startTs..prog.stopTs
                        Box(
                            modifier = Modifier
                                .clip(PortioShape.Small)
                                .background(if (isLive) PortioColors.Accent.copy(alpha = 0.35f) else PortioColors.SurfaceRaised)
                                .border(
                                    1.dp,
                                    if (isLive) PortioColors.Accent else PortioColors.Hairline,
                                    PortioShape.Small
                                )
                                .clickable { onSelectProgram(prog) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (isLive) {
                                        Box(Modifier.size(6.dp).clip(CircleShape).background(PortioColors.AccentRed))
                                    }
                                    Text(
                                        prog.timeFormatted(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isLive) PortioColors.AccentBlue else PortioColors.TextMuted,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    prog.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 140.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun EpgProgram.timeFormatted(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    val s = sdf.format(Date(startTs))
    val e = sdf.format(Date(stopTs))
    return "$s - $e"
}

/** Geriye dönük uyumluluk alias fonksiyonu */
@Composable
fun EpgGuideScreen(
    profile: Profile?,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    EpgScreen(profile, onBack, onOpenPlayer)
}
