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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavHostController
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.Profile
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxHeight
import com.stalkerapp.playback.ChannelQueue
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.components.ChannelLogo
import com.stalkerapp.ui.components.ChannelRow
import com.stalkerapp.ui.components.resolveUrl
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(navController: NavHostController) {
    val context = LocalContext.current
    val activity = context as? Activity
    val app = context.applicationContext as StalkerApp
    val vm: MainViewModel = viewModel { MainViewModel(app) }
    val profile = vm.repository.cachedProfile()

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var overlayVisible by remember { mutableStateOf(true) }
    var currentChannel by remember { mutableStateOf(ChannelQueue.current) }
    var isFav by remember { mutableStateOf(vm.store.isFavorite("ch:${currentChannel?.id}")) }
    var error by remember { mutableStateOf<String?>(null) }
    var showTracks by remember { mutableStateOf(false) }
    var showSubs by remember { mutableStateOf(false) }
    var showEpg by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showPlayerSettings by remember { mutableStateOf(false) }
    var showChannels by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(nowTime()) }
    var battery by remember { mutableStateOf(100) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var aspectMode by remember { mutableStateOf(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    val playerView = remember {
        PlayerView(context).apply {
            useController = false
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

    DisposableEffect(Unit) {
        val el: (String?) -> Unit = { error = it }
        PlaybackManager.addErrorListener(el)
        onDispose { PlaybackManager.removeErrorListener(el) }
    }

    BackHandler(enabled = true) {
        if (!navController.popBackStack()) {
            navController.navigate("home") {
                popUpTo("login") { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, true) }
        }
    }

    LaunchedEffect(overlayVisible) {
        if (overlayVisible && !locked) {
            delay(5000)
            overlayVisible = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            clock = nowTime()
            delay(1000)
        }
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                battery = if (scale > 0) (level * 100 / scale) else level
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(aspectMode) {
        playerView.resizeMode = aspectMode
    }

    LaunchedEffect(playbackSpeed) {
        PlaybackManager.setPlaybackSpeed(playbackSpeed)
    }

    val queueChannels = ChannelQueue.channels
    val queueProfile = ChannelQueue.profile

    val zappingList = remember(queueChannels, currentChannel) {
        val idx = queueChannels.indexOfFirst { it.id == currentChannel?.id }
        if (idx < 0) queueChannels.take(31)
        else queueChannels.drop((idx - 15).coerceAtLeast(0)).take(31)
    }

    fun switchTo(index: Int) {
        val ch = queueChannels.getOrNull(index) ?: return
        val p = queueProfile ?: return
        PlaybackManager.playChannel(queueChannels, index, p)
        currentChannel = ch
        isFav = vm.store.isFavorite("ch:${ch.id}")
        error = PlaybackManager.errorMessage
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { if (!locked) overlayVisible = !overlayVisible }
            }
    ) {
        AndroidView(factory = { playerView }, modifier = Modifier.fillMaxSize())

        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        if (overlayVisible) {
            // ---------- TOP BAR ----------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    ChannelLogo(
                        logo = resolveUrl(currentChannel?.logo ?: "", profile?.baseUrl.orEmpty()),
                        modifier = Modifier.size(30.dp)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp)
                    ) {
                        Text(
                            text = currentChannel?.name ?: PlaybackManager.currentTitle,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (currentChannel != null) {
                            Text(
                                text = "${currentChannel?.tvGenreTitle ?: ""}  •  Kanal ${currentChannel?.number ?: ""}",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(clock, color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${battery}%", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                val key = "ch:${currentChannel?.id}"
                                if (key != "ch:null") isFav = vm.toggleFavorite(key)
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favori",
                                tint = if (isFav) Color(0xFFFF5252) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { Toast.makeText(context, "Chromecast yakında", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Cast, contentDescription = "Chromecast", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = {
                                val url = PlaybackManager.currentStreamUrl
                                if (url.isNotBlank()) {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { setType("video/*") }
                                        context.startActivity(Intent.createChooser(intent, "Oynatıcı seç"))
                                    }
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Harici oynatıcı", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { locked = true; overlayVisible = false },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Kilit", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { showInfo = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Bilgi", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { showPlayerSettings = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Ayarlar", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // ---------- BOTTOM BAR ----------
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            val p = queueProfile ?: return@IconButton
                            val idx = ChannelQueue.index - 1
                            if (idx >= 0) switchTo(idx)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Önceki", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        IconButton(onClick = { PlaybackManager.togglePlayPause() }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Oynat/Duraklat",
                                tint = Color.White,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                        IconButton(onClick = {
                            val p = queueProfile ?: return@IconButton
                            val idx = ChannelQueue.index + 1
                            if (idx < ChannelQueue.channels.size) switchTo(idx)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Sonraki", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        IconButton(onClick = { showTracks = true }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Ses", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { showSubs = true }) {
                            Icon(Icons.Default.Subtitles, contentDescription = "Altyazı", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showEpg = true }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Tv, contentDescription = "Rehber", tint = Color.White, modifier = Modifier.size(22.dp))
                                Text("Rehber", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = { showChannels = true }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.List, contentDescription = "Kanallar", tint = Color.White, modifier = Modifier.size(22.dp))
                                Text("Kanallar", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Zapping bar
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(zappingList, key = { it.id }) { ch ->
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

        // ---------- LOCK OVERLAY ----------
        if (locked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.TopStart
            ) {
                IconButton(
                    onClick = { locked = false; overlayVisible = true },
                    modifier = Modifier.statusBarsPadding().padding(8.dp)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = "Kiliti aç", tint = Color.White)
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

    if (showInfo) {
        PlayerInfoSheet(
            channel = currentChannel,
            profile = profile,
            vm = vm,
            onDismiss = { showInfo = false }
        )
    }

    if (showPlayerSettings) {
        PlayerSettingsSheet(
            currentSpeed = playbackSpeed,
            currentAspect = aspectMode,
            onSpeed = { playbackSpeed = it; showPlayerSettings = false },
            onAspect = { aspectMode = it; showPlayerSettings = false },
            onDismiss = { showPlayerSettings = false }
        )
    }

    ChannelListPanel(
        visible = showChannels,
        onClose = { showChannels = false },
        onSelect = { idx -> switchTo(idx); showChannels = false }
    )
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

private fun nowTime(): String {
    return runCatching {
        java.time.LocalTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerInfoSheet(
    channel: Channel?,
    profile: Profile?,
    vm: MainViewModel,
    onDismiss: () -> Unit
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

    val current = programs?.firstOrNull { it.isCurrent }
    val next = programs?.let { list ->
        val idx = list.indexOfFirst { it.isCurrent }
        if (idx >= 0) list.getOrNull(idx + 1) else null
    }

    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(logo = resolveUrl(channel.logo, profile.baseUrl), modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(channel.name, style = MaterialTheme.typography.titleLarge)
                    if (channel.tvGenreTitle.isNotBlank()) {
                        Text(
                            channel.tvGenreTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            when {
                programs == null && error == null -> CircularProgressIndicator()
                error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                else -> {
                    Text("Şu an", style = MaterialTheme.typography.titleMedium)
                    if (current != null) {
                        Text(
                            "${vm.repository.formatEpoch(current.startTs)} — ${vm.repository.formatEpoch(current.stopTs)}  ${current.name}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text("Yayın bilgisi yok", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Sonraki", style = MaterialTheme.typography.titleMedium)
                    if (next != null) {
                        Text(
                            "${vm.repository.formatEpoch(next.startTs)} — ${vm.repository.formatEpoch(next.stopTs)}  ${next.name}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text("Sonraki program yok", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(
    currentSpeed: Float,
    currentAspect: Int,
    onSpeed: (Float) -> Unit,
    onAspect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    val aspects = listOf(
        "Sığdır" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
        "Doldur" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
        "Yakınlaştır" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    )
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text("Oynatma Hızı", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            speeds.forEach { s ->
                ListItem(
                    headlineContent = { Text("${if (s == 1f) "Normal" else s}⨉") },
                    trailingContent = if (s == currentSpeed) {
                        { Text("✓", color = MaterialTheme.colorScheme.primary) }
                    } else null,
                    modifier = Modifier.clickable { onSpeed(s) }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Görüntü Oranı", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            aspects.forEach { (label, mode) ->
                ListItem(
                    headlineContent = { Text(label) },
                    trailingContent = if (mode == currentAspect) {
                        { Text("✓", color = MaterialTheme.colorScheme.primary) }
                    } else null,
                    modifier = Modifier.clickable { onAspect(mode) }
                )
            }
        }
    }
}

@Composable
fun ChannelListPanel(
    visible: Boolean,
    onClose: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val channels = ChannelQueue.channels
    val profile = ChannelQueue.profile
    var query by remember { mutableStateOf("") }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it })
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
            Box(modifier = Modifier.fillMaxSize().clickable { onClose() })
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(340.dp)
                    .align(Alignment.CenterStart),
                color = Color(0xFF141414)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Kanallar",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Kanal ara…") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    val filtered = if (query.isBlank()) channels else channels.filter {
                        it.name.contains(query.trim(), ignoreCase = true)
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filtered, key = { it.id }) { ch ->
                            ChannelRow(
                                channel = ch,
                                baseUrl = profile?.baseUrl.orEmpty()
                            ) {
                                onSelect(channels.indexOfFirst { c -> c.id == ch.id })
                            }
                        }
                    }
                }
            }
        }
    }
}
