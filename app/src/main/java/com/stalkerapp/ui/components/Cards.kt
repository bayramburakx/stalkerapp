package com.stalkerapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.stalkerapp.data.Channel
import com.stalkerapp.data.VodItem
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.ui.theme.PortioShape

/**
 * Portio Kart Bileşenleri
 */

/** Temel Portio Kartı */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PortioCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = PortioShape.Card,
    containerColor: Color = PortioColors.SurfaceRaised,
    contentColor: Color = Color.White,
    border: BorderStroke? = BorderStroke(1.dp, PortioColors.Hairline),
    elevation: Dp = 0.dp,
    enabled: Boolean = true,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.97f
            isFocused -> 1.06f
            else -> 1.0f
        },
        animationSpec = tween(180),
        label = "portioCardScale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .then(
                if (isFocused) {
                    Modifier.shadow(
                        elevation = 18.dp,
                        shape = shape,
                        spotColor = PortioColors.FocusGlow.copy(alpha = 0.5f),
                        ambientColor = PortioColors.FocusGlow.copy(alpha = 0.3f)
                    )
                } else Modifier
            )
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier
                        .focusable(interactionSource = interactionSource, enabled = enabled)
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled,
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                        .onKeyEvent { ev ->
                            if (ev.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MENU && ev.type == KeyEventType.KeyUp) {
                                onLongClick?.invoke(); true
                            } else if (com.stalkerapp.ui.tv.isTvSelectKey(ev)) {
                                onClick(); true
                            } else false
                        }
                } else Modifier
            ),
        shape = shape,
        color = if (isFocused) PortioColors.SurfaceElevated else containerColor,
        border = if (isFocused) BorderStroke(2.5.dp, PortioColors.FocusBorder) else border,
        contentColor = contentColor,
        tonalElevation = elevation
    ) {
        Box { content(isFocused) }
    }
}

/** Cam Görünümlü Kart (GlassCard) */
@Composable
fun PortioGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    shape: RoundedCornerShape = PortioShape.Card,
    backgroundAlpha: Float = 0.65f,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    PortioCard(
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        shape = shape,
        containerColor = Color.Black.copy(alpha = backgroundAlpha),
        border = BorderStroke(1.dp, PortioColors.Hairline)
    ) { isFocused ->
        Box(modifier = Modifier.background(PortioColors.GlassGradient)) {
            content(isFocused)
        }
    }
}

/** Medya Afiş Kartı (Poster) */
@Composable
fun PortioMediaCard(
    title: String,
    posterUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badgeText: String? = null,
    badgeColor: Color = PortioColors.AccentRed,
    watched: Boolean = false,
    rating: String? = null,
    onLongClick: (() -> Unit)? = null
) {
    Column(modifier = modifier) {
        PortioCard(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = PortioShape.Poster,
            modifier = Modifier.fillMaxWidth()
        ) { isFocused ->
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(PortioShape.Poster)
                )

                // Üst Sağ Rozet (örn: DİZİ / 4K)
                if (badgeText != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(PortioShape.Badge)
                            .background(badgeColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Sol Üst İzlendi İkonu
                if (watched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(PortioColors.Success),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }

                // Alt Sol Rating
                if (!rating.isNullOrBlank() && rating != "0" && rating != "0.0") {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .clip(PortioShape.Badge)
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "★ $rating",
                            color = PortioColors.BadgeRating,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = PortioColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Kaynak Seçim Kartı (Stalker, Xtream, M3U) */
@Composable
fun PortioSourceCard(
    icon: ImageVector,
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PortioCard(
        onClick = onClick,
        shape = PortioShape.Card,
        modifier = modifier.fillMaxWidth()
    ) { isFocused ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(2.dp))
                Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(PortioShape.Full)
                    .background(if (selected) Color.White else Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Text("✓", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/** Geriye Dönük Uyumluluk Token & Card Nesneleri */
object AppleTvTokens {
    val FocusGlow = Color.White
    val FocusBorder = Color.White
    val Surface = Color(0xFF07070A)
    val SurfaceRaised = Color(0xFF121218)
    val Hairline = Color.White.copy(alpha = 0.12f)
    val HairlineStrong = Color.White.copy(alpha = 0.22f)
    val PillShape = RoundedCornerShape(50)
    val CardShape = RoundedCornerShape(18.dp)
    val CardShapeSmall = RoundedCornerShape(12.dp)

    val GlassGradient: Brush
        get() = PortioColors.GlassGradient

    val BackdropScrim: Brush
        get() = PortioColors.BackdropScrim
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppleTvCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    cornerRadius: Dp = 18.dp,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    PortioCard(
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        shape = RoundedCornerShape(cornerRadius),
        containerColor = AppleTvTokens.SurfaceRaised,
        content = content
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = PortioShape.Card
    val card = @Composable {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = PortioColors.SurfaceRaised.copy(alpha = 0.85f),
                contentColor = Color.White
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
