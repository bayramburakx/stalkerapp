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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.stalkerapp.data.Channel
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.ui.theme.PortioShape
import com.stalkerapp.ui.tv.isTvSelectKey

/**
 * Portio Rozet & Etiket Bileşenleri
 */

@Composable
fun PortioBadge(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = PortioColors.SurfaceVariant,
    textColor: Color = Color.White,
    shape: RoundedCornerShape = PortioShape.Badge
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** 4K / UHD Rozeti */
@Composable
fun Badge4K(modifier: Modifier = Modifier) {
    PortioBadge(
        text = "4K",
        backgroundColor = PortioColors.Badge4K,
        textColor = Color.Black,
        modifier = modifier
    )
}

/** FHD Rozeti */
@Composable
fun BadgeFHD(modifier: Modifier = Modifier) {
    PortioBadge(
        text = "FHD",
        backgroundColor = PortioColors.BadgeFHD,
        textColor = Color.Black,
        modifier = modifier
    )
}

/** HD Rozeti */
@Composable
fun BadgeHD(modifier: Modifier = Modifier) {
    PortioBadge(
        text = "HD",
        backgroundColor = PortioColors.BadgeHD,
        textColor = Color.White,
        modifier = modifier
    )
}

/** Canlı TV Rozeti */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(PortioShape.Badge)
            .background(PortioColors.Live)
            .padding(horizontal = 6.dp, vertical = 2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(PortioShape.Full)
                .background(Color.White)
        )
        Text(
            text = "CANLI",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
    }
}

/** Dizi Rozeti */
@Composable
fun SeriesBadge(modifier: Modifier = Modifier) {
    PortioBadge(
        text = "DİZİ",
        backgroundColor = PortioColors.BadgeSeries,
        textColor = Color.White,
        modifier = modifier
    )
}

/** İzlendi Rozeti */
@Composable
fun WatchedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(PortioColors.BadgeWatched),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "İzlendi",
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
    }
}

/** IMDb Rating Rozeti */
@Composable
fun RatingBadge(
    rating: String,
    modifier: Modifier = Modifier
) {
    val clean = rating.trim().trimEnd('/')
    if (clean.isBlank() || clean == "0" || clean == "0.0") return

    Row(
        modifier = modifier
            .clip(PortioShape.Badge)
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(horizontal = 6.dp, vertical = 2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = PortioColors.BadgeRating,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = clean,
            color = PortioColors.BadgeRating,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Seçilebilir Cam Filtre Çipi */
@Composable
fun GlassChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1.0f,
        label = "glass_chip_scale"
    )
    val pillShape = PortioShape.Pill
    Box(
        modifier = modifier
            .scale(scale)
            .clip(pillShape)
            .background(
                when {
                    isFocused -> Color.White
                    selected -> Color.White.copy(alpha = 0.25f)
                    else -> Color.Black.copy(alpha = 0.55f)
                }
            )
            .border(
                if (isFocused) 2.5.dp else 1.dp,
                when {
                    isFocused -> Color.White
                    selected -> Color.White.copy(alpha = 0.7f)
                    else -> PortioColors.Hairline
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
                selected -> Color.White
                else -> PortioColors.TextSecondary
            },
            maxLines = 1
        )
    }
}

@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/** Kanal Logosu ve Monogram Yedek Rozeti */
@Composable
fun ChannelLogo(
    logo: String,
    modifier: Modifier = Modifier,
    channelName: String = "",
    baseUrl: String = ""
) {
    val resolved = remember(logo, baseUrl) { resolveUrl(logo, baseUrl) }
    var loadFailed by remember(resolved) { mutableStateOf(false) }

    val cleanTitle = remember(channelName) {
        channelName
            .replace(Regex("^[↺✦☪★✫✪*\\-\\s]+"), "")
            .replace(Regex("(?i)\\s+(FHD|UHD|HD\\+|HD|SD|4K|2160p|1080p|720p|TEST|HEVC)\\b.*"), "")
            .trim()
            .ifBlank { channelName.take(6).trim() }
    }

    val badgeGradient = remember(cleanTitle) {
        PortioColors.badgeGradient(cleanTitle)
    }

    Box(
        modifier = modifier
            .size(44.dp, 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PortioColors.SurfaceRaised)
            .border(1.dp, PortioColors.Hairline, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (resolved.isNotBlank() && !loadFailed && !resolved.contains("tvlogolar.xyz")) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(resolved)
                    .crossfade(false)
                    .build(),
                contentDescription = channelName,
                contentScale = ContentScale.Fit,
                onError = { loadFailed = true },
                modifier = Modifier.fillMaxSize().padding(3.dp)
            )
        } else {
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
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = 11.sp
                )
            }
        }
    }
}

/** Kanal Satırı Bileşeni (ChannelRow) */
@OptIn(ExperimentalFoundationApi::class)
@Composable
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
            .clip(PortioShape.Medium)
            .background(
                when {
                    isFocused -> Color.White.copy(alpha = 0.22f)
                    highlight -> PortioColors.PrimaryContainer
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isFocused) 2.5.dp else 0.dp,
                color = if (isFocused) PortioColors.FocusBorder else Color.Transparent,
                shape = PortioShape.Medium
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
                    onClick(channel); true
                } else false
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!hideNumber) {
            Text(
                text = channel.number.toString(),
                color = if (highlight) Color.White else PortioColors.TextMuted,
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
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!nowPlaying.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(PortioShape.Full)
                            .background(PortioColors.Success)
                    )
                    Text(
                        text = nowPlaying,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (channel.tvGenreTitle.isNotBlank()) {
                Text(
                    text = channel.tvGenreTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PortioColors.TextMuted,
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
                    tint = if (isFavorite) PortioColors.AccentRed else PortioColors.TextMuted,
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
