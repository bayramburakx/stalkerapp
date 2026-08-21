package com.stalkerapp.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stalkerapp.data.OfflineDownloadManager
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.GlassSurface
import com.stalkerapp.ui.components.PortioButton
import com.stalkerapp.ui.components.PortioButtonStyle
import com.stalkerapp.ui.components.PortioCard
import com.stalkerapp.ui.components.SectionTitle
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.ui.theme.PortioShape

/**
 * Portio İndirilenler Ekranı (DownloadsScreen)
 */
@Composable
fun DownloadsScreen(
    onPlayOffline: (OfflineDownloadManager.DownloadEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val downloads by OfflineDownloadManager.downloads.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PortioColors.Background)
            .statusBarsPadding()
    ) {
        Spacer(Modifier.height(16.dp))

        SectionTitle(
            title = "İndirilenler",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        val usedMb = OfflineDownloadManager.usedDiskBytes() / (1024 * 1024)
        if (usedMb > 0) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = PortioShape.Card
            ) {
                Text(
                    "Kullanılan depolama: ${usedMb} MB",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Download,
                    title = "İndirilen içerik yok",
                    subtitle = "Film veya bölüm sayfasından \"İndir\" butonuna basarak Wi-Fi'de içerik indirebilirsin."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(downloads, key = { it.id }) { entry ->
                    DownloadItemCard(
                        entry = entry,
                        onPlay = { onPlayOffline(entry) },
                        onDelete = { OfflineDownloadManager.cancel(entry.id) }
                    )
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun DownloadItemCard(
    entry: OfflineDownloadManager.DownloadEntry,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    PortioCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        onClick = { if (entry.state == "completed") onPlay() }
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (entry.poster.isNotBlank()) {
                AsyncImage(
                    model = entry.poster,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp, 92.dp)
                        .clip(PortioShape.Small)
                        .background(PortioColors.SurfaceRaised)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp, 92.dp)
                        .clip(PortioShape.Small)
                        .background(PortioColors.SurfaceRaised),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Download,
                        null,
                        tint = Color.White.copy(0.5f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.episodeLabel.isNotBlank()) {
                    Text(
                        entry.episodeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = PortioColors.TextMuted
                    )
                }
                Spacer(Modifier.height(10.dp))

                when (entry.state) {
                    "downloading" -> {
                        LinearProgressIndicator(
                            progress = { entry.progressPct / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${entry.progressPct.toInt()}% indiriliyor",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                    "completed" -> {
                        val sizeStr = if (entry.fileSizeBytes >= 1024L * 1024L * 1024L) {
                            String.format(java.util.Locale.US, "%.2f GB", entry.fileSizeBytes / (1024.0 * 1024.0 * 1024.0))
                        } else if (entry.fileSizeBytes > 0) {
                            "${entry.fileSizeBytes / (1024 * 1024)} MB"
                        } else ""
                        Text(
                            "✓ İndirildi${if (sizeStr.isNotBlank()) " · $sizeStr" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = PortioColors.Success
                        )
                    }
                    "failed" -> {
                        Text(
                            "✗ İndirme başarısız",
                            style = MaterialTheme.typography.labelSmall,
                            color = PortioColors.Error
                        )
                    }
                    "queued" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Sırada bekliyor",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            if (entry.state == "completed") {
                PortioButton(
                    onClick = onPlay,
                    style = PortioButtonStyle.Primary,
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Oynat",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }

            PortioButton(
                onClick = onDelete,
                style = PortioButtonStyle.Glass,
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Sil",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
