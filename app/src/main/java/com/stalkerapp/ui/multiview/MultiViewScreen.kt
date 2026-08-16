package com.stalkerapp.ui.multiview

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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.Profile
import kotlinx.coroutines.launch

/**
 * Multi View: 2 veya 4 kanalı aynı anda izleme (TiviMate/Sparkle tarzı).
 * Her bölme kendi ExoPlayer'ı ile ayrı akışı oynatır; bölmeye dokununca
 * ana oynatıcıda o kanal açılır.
 */
@Composable
fun MultiViewScreen(
    channels: List<Channel>,
    profile: Profile?,
    panes: Int,
    onSelectChannel: (Channel) -> Unit,
    onClose: () -> Unit
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val repository = app.repository
    val scope = rememberCoroutineScope()
    val paneCount = if (panes == 4) 4 else 2
    val shown = channels.take(paneCount)
    val context = LocalContext.current

    // Her bölme için ayrı oynatıcı kurulur; ekran kapanınca hepsi serbest bırakılır.
    val players = remember(shown.map { it.id }.toList()) {
        shown.map { ch ->
            Pair(ch, ExoPlayer.Builder(context).build())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            players.forEach { (_, p) -> p.release() }
        }
    }

    LaunchedEffect(players) {
        players.forEach { (ch, player) ->
            scope.launch {
                val url = runCatching { repository.channelStreamUrl(ch, profile) }.getOrNull()
                if (url.isNullOrBlank()) return@launch
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.play()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kapat", tint = Color.White)
            }
            Icon(Icons.Default.GridView, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Multi View — ${shown.size} Kanal",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
            }
        }

        if (shown.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Kanal seçilmedi", color = Color.White)
            }
            return@Column
        }

        // 2 bölme = tek satır, 4 bölme = 2x2 ızgara.
        Column(
            modifier = Modifier.fillMaxSize().padding(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val rows = if (paneCount == 4) 2 else 1
            repeat(rows) { r ->
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val cols = paneCount / rows
                    repeat(cols) { c ->
                        val idx = r * cols + c
                        val ch = shown.getOrNull(idx) ?: return@repeat
                        val player = players.firstOrNull { it.first.id == ch.id }?.second
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clickable {
                                    // Seçilen kanal ana oynatıcıda açılır, multi view kapanır.
                                    onSelectChannel(ch)
                                }
                        ) {
                            if (player != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            useController = false
                                            this.player = player
                                            setBackgroundColor(android.graphics.Color.BLACK)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            // Kanal adı rozeti.
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    ch.name,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            // Sessiz butonu yerine sade görünüm: dokun → seç.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    (idx + 1).toString(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
