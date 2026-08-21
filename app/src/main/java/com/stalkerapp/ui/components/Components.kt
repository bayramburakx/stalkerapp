package com.stalkerapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.stalkerapp.data.Channel
import com.stalkerapp.data.UpdateInfo
import com.stalkerapp.ui.tv.isTvSelectKey
import com.stalkerapp.util.L10n

@Composable
fun LoadingBox() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun EmptyState(
    text: String = "",
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    title: String? = null,
    subtitle: String? = null
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon?.let {
                androidx.compose.material3.Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
            }
            title?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                subtitle ?: text,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Uygulama içi güncelleme pop-up'ı. Üç eylem sunar:
 * - "Şimdi Güncelle": APK linkini açar (tarayıcıda indirme başlar)
 * - "Sonra Hatırlat": kapatır, 24 saat sonra yeniden sorulur
 * - "Bir Daha Sorma": bu sürüm için bir daha sorulmaz (cihazda saklanır)
 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    lang: String,
    onDismiss: () -> Unit,
    onUpdateNow: () -> Unit,
    onRemindLater: () -> Unit,
    onNeverAsk: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.t(lang, "Vazgeç")) } },
        title = { Text(L10n.t(lang, "Yeni sürüm var!")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "v${info.version} ${L10n.t(lang, "yayınlandı")} (${info.publishedAt.take(10)}). " +
                        L10n.t(lang, "Güncel APK'yı indirip kurmak ister misin?"),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Button(onClick = onUpdateNow, modifier = Modifier.fillMaxWidth()) {
                    Text(L10n.t(lang, "Şimdi Güncelle"), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onRemindLater, modifier = Modifier.fillMaxWidth()) {
                    Text(L10n.t(lang, "Sonra Hatırlat"))
                }
                TextButton(onClick = onNeverAsk, modifier = Modifier.fillMaxWidth()) {
                    Text(L10n.t(lang, "Bir Daha Sorma"))
                }
            }
        }
    )
}

@Composable
fun ChannelLogo(
    logo: String,
    modifier: Modifier = Modifier,
    channelName: String = "",
    baseUrl: String = ""
) {
    val resolved = remember(logo, baseUrl) { resolveUrl(logo, baseUrl) }
    var loadFailed by remember(resolved) { mutableStateOf(false) }

    // Temizlenmiş kanal adı (örn: "↺TRT 1 FHD" -> "TRT 1", "TR ✫ ATV HD+" -> "ATV")
    val cleanTitle = remember(channelName) {
        channelName
            .replace(Regex("^[↺✦☪★✫✪*\\-\\s]+"), "")
            .replace(Regex("(?i)\\s+(FHD|UHD|HD\\+|HD|SD|4K|2160p|1080p|720p|TEST|HEVC)\\b.*"), "")
            .trim()
            .ifBlank { channelName.take(6).trim() }
    }

    val badgeGradient = remember(cleanTitle) {
        val hash = cleanTitle.hashCode()
        val colors = when (Math.abs(hash) % 6) {
            0 -> listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6)) // Mavi
            1 -> listOf(Color(0xFF831843), Color(0xFFEC4899)) // Pembe/Kırmızı
            2 -> listOf(Color(0xFF14532D), Color(0xFF22C55E)) // Yeşil
            3 -> listOf(Color(0xFF581C87), Color(0xFFA855F7)) // Mor
            4 -> listOf(Color(0xFF7C2D12), Color(0xFFF97316)) // Turuncu
            else -> listOf(Color(0xFF0F172A), Color(0xFF334155)) // Koyu Gri
        }
        androidx.compose.ui.graphics.Brush.linearGradient(colors)
    }

    Box(
        modifier = modifier
            .size(44.dp, 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF131722))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (resolved.isNotBlank() && !loadFailed && !resolved.contains("tvlogolar.xyz")) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(resolved)
                    .crossfade(false)
                    .build(),
                contentDescription = channelName,
                contentScale = ContentScale.Fit,
                onError = { loadFailed = true },
                modifier = Modifier.fillMaxSize().padding(3.dp)
            )
        } else {
            // Şık Kanal Monogram / İsim Rozeti
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(badgeGradient)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = cleanTitle.take(8).ifBlank { "TV" },
                    color = Color.White,
                    fontSize = 10.androidx.compose.ui.unit.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    lineHeight = 11.androidx.compose.ui.unit.sp
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ChannelRow(
    channel: Channel,
    baseUrl: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    hideNumber: Boolean = false,
    isFavorite: Boolean = false,
    nowPlaying: String? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onLongClick: ((Channel) -> Unit)? = null,
    onClick: (Channel) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1.0f,
        label = "channel_row_scale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isFocused -> Color(0xFF00E5FF).copy(alpha = 0.28f)
                    highlight -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isFocused) 2.5.dp else 0.dp,
                color = if (isFocused) Color(0xFF00E5FF) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { onClick(channel) },
                        onLongClick = { onLongClick(channel) }
                    )
                } else {
                    Modifier.clickable { onClick(channel) }
                }
            )
            .onKeyEvent { ev ->
                if (isTvSelectKey(ev)) {
                    onClick(channel)
                    true
                } else false
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!hideNumber) {
            Text(
                text = channel.number.toString(),
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.size(28.dp)
            )
        }
        ChannelLogo(
            logo = channel.logo,
            channelName = channel.name,
            baseUrl = baseUrl
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Şu an oynayan program (EPG) kanal adının hemen altında gösterilir;
            // yoksa tür adına düşülür.
            if (!nowPlaying.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF2E7D32))
                    )
                    Text(
                        text = nowPlaying,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (channel.tvGenreTitle.isNotBlank()) {
                Text(
                    text = channel.tvGenreTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onToggleFavorite != null) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favori",
                    tint = if (isFavorite) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

fun resolveUrl(url: String, baseUrl: String = ""): String {
    if (url.isBlank()) return ""
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    if (baseUrl.isBlank()) return url
    return baseUrl.trimEnd('/') + "/" + url.trimStart('/')
}

@Composable
fun InfoChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/** Consistent elevated surface card used across the app (anasayfa kart dili). */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val card = @Composable {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(
                // Anasayfa dili: gri dolgu yerine hafif cam hissi + ince çerçeve.
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) { content() }
    }
    if (onClick != null) {
        Box(modifier = Modifier.clickable(onClick = onClick)) { card() }
    } else {
        card()
    }
}

/** Anasayfa diliyle bölüm başlığı: kalın büyük başlık + cam pill "Tümü" oku. */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (onSeeAll != null) {
            val pillShape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(pillShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.70f))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), pillShape)
                    .clickable { onSeeAll() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Tümü",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Anasayfa diliyle seçilebilir filtre çipi: cam pill görünümü (Android TV D-pad uyumlu). */
@Composable
fun GlassChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.14f else 1.0f,
        label = "glass_chip_scale"
    )
    val pillShape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .scale(scale)
            .clip(pillShape)
            .background(
                when {
                    isFocused -> Color(0xFF00E5FF)
                    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.60f)
                }
            )
            .border(
                if (isFocused) 3.dp else 1.dp,
                when {
                    isFocused -> Color.White
                    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
                },
                pillShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .onKeyEvent { ev ->
                if (isTvSelectKey(ev)) {
                    onClick(); true
                } else false
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isFocused -> Color.Black
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )
    }
}
