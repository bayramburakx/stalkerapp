package com.stalkerapp.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.playback.ChannelQueue
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.components.ChannelLogo
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val app = context.applicationContext as StalkerApp
    val vm: MainViewModel = viewModel { MainViewModel(app) }
    val profile = vm.repository.cachedProfile()
    val scope = rememberCoroutineScope()

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var overlayVisible by remember { mutableStateOf(true) }
    var currentChannel by remember { mutableStateOf(ChannelQueue.current) }
    var isFav by remember { mutableStateOf(vm.store.isFavorite("ch:${currentChannel?.id}")) }
    var error by remember { mutableStateOf<String?>(null) }
    var showTracks by remember { mutableStateOf(false) }
    var showSubs by remember { mutableStateOf(false) }
    var showEpg by remember { mutableStateOf(false) }

    val playerView = remember {
        PlayerView(context).apply {
            useController = false
            setEnablePip(true)
        }
    }

    DisposableEffect(Unit) {
        val listener: (ExoPlayer?) -> Unit = { p ->
            playerView.player = p
            currentChannel = ChannelQueue.current
        }
        PlaybackManager.addPlayerListener(listener)
        playerView.player = PlaybackManager.player
        onDispose {
            PlaybackManager.removePlayerListener(listener)
        }
    }

    val stateListener: (Boolean, Boolean) -> Unit = { playing, buffering ->
        isPlaying = playing
        isBuffering = buffering
    }
    DisposableEffect(Unit) {
        PlaybackManager.addStateListener(stateListener)
        onDispose { PlaybackManager.removeStateListener(stateListener) }
    }

    LaunchedEffect(overlayVisible) {
        if (overlayVisible) {
            delay(5000)
            overlayVisible = false
        }
    }

    val queueChannels = ChannelQueue.channels
    val queueProfile = ChannelQueue.profile

    fun switchTo(index: Int) {
        val ch = queueChannels.getOrNull(index) ?: return
        val p = queueProfile ?: return
        PlaybackManager.playChannel(queueChannels, index, p)
        currentChannel = ch
        isFav = vm.store.isFavorite("ch:${ch.id}")
        error = PlaybackManager.errorMessage
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { playerView }, modifier = Modifier.fillMaxSize())

        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        if (overlayVisible) {
            // Top gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { activity?.finish() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentChannel?.name ?: PlaybackManager.currentTitle,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (currentChannel != null) {
                            Text(
                                text = "Kanal ${currentChannel?.number ?: ""} — ${profile?.portal?.name ?: ""}",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    IconButton(onClick = {
                        val key = "ch:${currentChannel?.id}"
                        if (key != "ch:null") {
                            isFav = vm.toggleFavorite(key)
                        }
                    }) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favori",
                            tint = if (isFav) Color(0xFFFF5252) else Color.White
                        )
                    }
                    IconButton(onClick = { PlaybackManager.enterPip(activity) }) {
                        Icon(
                            Icons.Default.PictureInPictureAlt,
                            contentDescription = "PiP",
                            tint = Color.White
                        )
                    }
                }
            }

            // Bottom gradient with controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val p = queueProfile ?: return@IconButton
                        val idx = ChannelQueue.index - 1
                        if (idx >= 0) switchTo(idx)
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Önceki",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    IconButton(onClick = { PlaybackManager.togglePlayPause() }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Oynat/Duraklat",
                            tint = Color.White,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    IconButton(onClick = {
                        val p = queueProfile ?: return@IconButton
                        val idx = ChannelQueue.index + 1
                        if (idx < ChannelQueue.channels.size) switchTo(idx)
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Sonraki",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    IconButton(onClick = { showTracks = true }) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "Ses",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { showSubs = true }) {
                        Icon(
                            Icons.Default.Subtitles,
                            contentDescription = "Altyazı",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { showEpg = true }) {
                        Text("EPG", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                }

                // Zapping bar
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(queueChannels, key = { it.id }) { ch ->
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (ch.id == currentChannel?.id) Color.White.copy(alpha = 0.25f)
                                    else Color.Black.copy(alpha = 0.45f)
                                )
                                .clickable { switchTo(queueChannels.indexOfFirst { it.id == ch.id }) }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ChannelLogo(logo = ch.logo, modifier = Modifier.size(36.dp))
                            Text(
                                text = ch.name,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(72.dp).padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        if (error != null) {
            Text(
                text = error.orEmpty(),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )
        }
    }

    if (showTracks) {
        AudioTracksSheet(
            onDismiss = { showTracks = false },
            onSelect = { lang ->
                PlaybackManager.setAudioLanguage(lang)
                showTracks = false
            }
        )
    }

    if (showSubs) {
        SubtitleSheet(
            onDismiss = { showSubs = false },
            onSelect = { lang ->
                PlaybackManager.setSubtitleLanguage(lang)
                showSubs = false
            },
            enabled = PlaybackManager.subtitlesEnabled(),
            onToggle = { PlaybackManager.setSubtitlesEnabled(it) }
        )
    }

    if (showEpg) {
        EpgSheet(
            channel = currentChannel,
            profile = profile,
            onDismiss = { showEpg = false },
            vm = vm
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTracksSheet(onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    val tracks = remember { PlaybackManager.availableTracks(C.TRACK_TYPE_AUDIO) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text("Ses Dili", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            ListItem(
                headlineContent = { Text("Varsayılan (Otomatik)") },
                modifier = Modifier.clickable { onSelect(null) }
            )
            tracks.forEach { (lang, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    supportingContent = { Text(lang) },
                    modifier = Modifier.clickable { onSelect(lang) }
                )
            }
            if (tracks.isEmpty()) {
                Text(
                    "Ses izi bulunamadı",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSheet(
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val tracks = remember { PlaybackManager.availableTracks(C.TRACK_TYPE_TEXT) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text("Altyazı", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            ListItem(
                headlineContent = { Text("Altyazılar") },
                trailingContent = {
                    Switch(checked = enabled, onCheckedChange = { onToggle(it) })
                }
            )
            ListItem(
                headlineContent = { Text("Altyazı yok (Kapat)") },
                modifier = Modifier.clickable { onSelect(null) }
            )
            tracks.forEach { (lang, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    supportingContent = { Text(lang) },
                    modifier = Modifier.clickable { onSelect(lang) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpgSheet(
    channel: Channel?,
    profile: com.stalkerapp.data.Profile?,
    onDismiss: () -> Unit,
    vm: MainViewModel
) {
    if (channel == null || profile == null) {
        onDismiss()
        return
    }
    var programs by remember { mutableStateOf<List<com.stalkerapp.data.EpgProgram>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(channel.id) {
        try {
            programs = vm.repository.loadEpg(profile, channel.id)
        } catch (e: Exception) {
            error = e.message
        }
    }

    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Text(
                "EPG — ${channel.name}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            when {
                programs == null && error == null -> CircularProgressIndicator(
                    modifier = Modifier.padding(24.dp).align(Alignment.CenterHorizontally)
                )
                error != null -> Text(
                    error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
                programs.orEmpty().isEmpty() -> Text(
                    "EPG verisi yok",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
                else -> androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.height(400.dp)
                ) {
                    items(programs.orEmpty()) { p ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (p.isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "${vm.repository.formatEpoch(p.startTs)} — ${vm.repository.formatEpoch(p.stopTs)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (p.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (p.isCurrent) {
                                    Text(
                                        "● ŞİMDİ",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                p.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (p.isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            if (p.desc.isNotBlank()) {
                                Text(
                                    p.desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
