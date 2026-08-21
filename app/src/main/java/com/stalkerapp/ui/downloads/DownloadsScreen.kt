package com.stalkerapp.ui.downloads

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
import com.stalkerapp.ui.components.AppleHairline
import com.stalkerapp.ui.components.AppleSectionHeader
import com.stalkerapp.ui.components.AppleTvButton
import com.stalkerapp.ui.components.AppleTvButtonStyle
import com.stalkerapp.ui.components.AppleTvCard
import com.stalkerapp.ui.components.AppleTvTokens
import com.stalkerapp.ui.components.EmptyState
import com.stalkerapp.ui.components.GlassSurface

/**
 * İndirilen ve indirilmekte olan içeriklerin listesi.
 * Tamamlananlar doğrudan oynatılabilir (çevrimdışı mod).
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
            .background(AppleTvTokens.Surface)
            .statusBarsPadding()
    ) {
        Spacer(Modifier.height(16.dp))

        // Başlık
        AppleSectionHeader(
            title = "İndirilenler",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        // Disk kullanımı özeti
        val usedMb = OfflineDownloadManager.usedDiskBytes() / (1024 * 1024)
        if (usedMb > 0) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = AppleTvTokens.CardShape
            ) {
                Text(
                    "Kullanılan depolama: ${usedMb} MB",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        AppleHairline(modifier = Modifier.padding(horizontal = 24.dp))

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
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(downloads, key = { it.id }) { entry ->
                    DownloadItem(
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
private fun DownloadItem(
    entry: OfflineDownloadManager.DownloadEntry,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    AppleTvCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        onClick = { },
        cornerRadius = 18.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Küçük poster
            if (entry.poster.isNotBlank()) {
                AsyncImage(
                    model = entry.poster,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp, 92.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppleTvTokens.SurfaceRaised)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp, 92.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppleTvTokens.SurfaceRaised),
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
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.episodeLabel.isNotBlank()) {
                    Text(
                        entry.episodeLabel,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
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
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
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
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                    "failed" -> {
                        Text(
                            "✗ İndirme başarısız",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
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
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // İndirilmişse oynat butonu
            if (entry.state == "completed") {
                AppleTvButton(
                    onClick = onPlay,
                    style = AppleTvButtonStyle.Secondary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Oynat",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Sil butonu
            AppleTvButton(
                onClick = onDelete,
                style = AppleTvButtonStyle.Glass,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Sil",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
