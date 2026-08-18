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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Başlık
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "İndirilenler",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Disk kullanımı özeti
        val usedMb = OfflineDownloadManager.usedDiskBytes() / (1024 * 1024)
        if (usedMb > 0) {
            Text(
                "Kullanılan depolama: ${usedMb} MB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
        }

        HorizontalDivider()

        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Download,
                    title = "İndirilen içerik yok",
                    subtitle = "Film veya bölüm sayfasından \"İndir\" butonuna basarak Wi-Fi'de içerik indirebilirsin."
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(downloads, key = { it.id }) { entry ->
                    DownloadItem(
                        entry = entry,
                        onPlay = { onPlayOffline(entry) },
                        onDelete = { OfflineDownloadManager.cancel(entry.id) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Küçük poster
        if (entry.poster.isNotBlank()) {
            AsyncImage(
                model = entry.poster,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp, 80.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.DarkGray)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp, 80.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Download, null, tint = Color.White.copy(0.5f))
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.episodeLabel.isNotBlank()) {
                Text(
                    entry.episodeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))

            when (entry.state) {
                "downloading" -> {
                    LinearProgressIndicator(
                        progress = { entry.progressPct / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${entry.progressPct.toInt()}% indiriliyor",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                "completed" -> {
                    val sizeMb = entry.fileSizeBytes / (1024 * 1024)
                    Text(
                        "✓ İndirildi${if (sizeMb > 0) " · $sizeMb MB" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50)
                    )
                }
                "failed" -> {
                    Text(
                        "✗ İndirme başarısız",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                "queued" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Sırada bekliyor",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // İndirilmişse oynat butonu
        if (entry.state == "completed") {
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Oynat",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Sil butonu
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Sil",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
