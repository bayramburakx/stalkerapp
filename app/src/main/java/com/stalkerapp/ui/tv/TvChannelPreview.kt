package com.stalkerapp.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.stalkerapp.data.Channel
import com.stalkerapp.ui.components.resolveUrl
import kotlinx.coroutines.delay

/**
 * Kanal önizleme bileşeni (Android TV).
 * Bir kanala odaklanınca küçük, sessiz mini önizleme başlatır.
 * Odak kaybolunca ExoPlayer serbest bırakılır.
 *
 * Kullanım:
 * ```
 * TvChannelPreview(channel = focusedChannel, modifier = Modifier.size(320.dp, 180.dp))
 * ```
 */
@Composable
fun TvChannelPreview(
    channel: Channel?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    // Odaklanılan kanal değişince oynatıcıyı yeniden başlat
    LaunchedEffect(channel?.id) {
        if (channel == null) {
            player?.release()
            player = null
            return@LaunchedEffect
        }
        // 300ms bekle (hızlı geçişlerde gereksiz yükleme önlemek için)
        delay(300)

        player?.release()
        isLoading = true

        val cmd = channel.cmd
        if (cmd.isBlank()) return@LaunchedEffect

        val newPlayer = ExoPlayer.Builder(context).build().apply {
            volume = 0f // Sessiz önizleme
            repeatMode = Player.REPEAT_MODE_OFF

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY || state == Player.STATE_BUFFERING) {
                        isLoading = false
                    }
                }
            })

            val streamUrl = resolveUrl(cmd)
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            play()
        }
        player = newPlayer
    }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val currentPlayer = player
        if (currentPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = currentPlayer
                    }
                },
                update = { view ->
                    view.player = currentPlayer
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isLoading || channel == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White.copy(0.7f),
                strokeWidth = 2.dp
            )
        }
    }
}
