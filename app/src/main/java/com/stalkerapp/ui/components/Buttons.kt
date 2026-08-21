package com.stalkerapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.ui.theme.PortioShape

enum class PortioButtonStyle {
    Primary,
    Secondary,
    Glass,
    Outline,
    Ghost,
    Destructive
}

/**
 * Portio Ana Buton Bileşeni - Dokunmatik ve Android TV D-Pad uyumlu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PortioButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PortioButtonStyle = PortioButtonStyle.Primary,
    shape: Shape = PortioShape.ButtonPill,
    enabled: Boolean = true,
    loading: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    content: @Composable RowScope.(isFocused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            !enabled -> 1.0f
            isPressed -> 0.96f
            isFocused -> 1.06f
            else -> 1.0f
        },
        animationSpec = tween(160),
        label = "portioBtnScale"
    )

    val (containerColor, contentColor, border) = when (style) {
        PortioButtonStyle.Primary -> {
            val bg = if (isFocused) Color.White else Color.White.copy(alpha = if (enabled) 0.95f else 0.4f)
            Triple(bg, Color.Black, null)
        }
        PortioButtonStyle.Secondary -> {
            val bg = if (isFocused) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.14f)
            val stroke = BorderStroke(1.dp, if (isFocused) PortioColors.FocusBorder else PortioColors.HairlineStrong)
            Triple(bg, Color.White, stroke)
        }
        PortioButtonStyle.Glass -> {
            val bg = if (isFocused) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)
            val stroke = BorderStroke(1.dp, if (isFocused) PortioColors.FocusBorder else PortioColors.Hairline)
            Triple(bg, Color.White, stroke)
        }
        PortioButtonStyle.Outline -> {
            val bg = if (isFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent
            val stroke = BorderStroke(1.5.dp, if (isFocused) PortioColors.FocusBorder else Color.White.copy(alpha = 0.35f))
            Triple(bg, Color.White, stroke)
        }
        PortioButtonStyle.Ghost -> {
            val bg = if (isFocused) Color.White.copy(alpha = 0.15f) else Color.Transparent
            Triple(bg, Color.White, null)
        }
        PortioButtonStyle.Destructive -> {
            val bg = if (isFocused) PortioColors.Error else PortioColors.Error.copy(alpha = 0.2f)
            val stroke = BorderStroke(1.dp, PortioColors.Error.copy(alpha = 0.5f))
            val textCol = if (isFocused) Color.White else PortioColors.Error
            Triple(bg, textCol, stroke)
        }
    }

    Surface(
        modifier = modifier
            .scale(scale)
            .then(
                if (isFocused && style == PortioButtonStyle.Primary) {
                    Modifier.shadow(
                        elevation = 16.dp,
                        shape = shape,
                        spotColor = PortioColors.FocusGlow.copy(alpha = 0.55f)
                    )
                } else Modifier
            )
            .clip(shape)
            .focusable(interactionSource = interactionSource, enabled = enabled && !loading)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .onKeyEvent { ev ->
                if (ev.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MENU && ev.type == KeyEventType.KeyUp) {
                    onLongClick?.invoke(); true
                } else if (com.stalkerapp.ui.tv.isTvSelectKey(ev)) {
                    onClick(); true
                } else false
            },
        shape = shape,
        color = containerColor,
        border = border,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = contentColor,
                    strokeWidth = 2.dp
                )
            } else {
                content(isFocused)
            }
        }
    }
}

/** Birincil Aksiyon Butonu */
@Composable
fun PortioPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    PortioButton(
        onClick = onClick,
        modifier = modifier,
        style = PortioButtonStyle.Primary,
        enabled = enabled,
        loading = loading
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** İkincil Buton */
@Composable
fun PortioSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    PortioButton(
        onClick = onClick,
        modifier = modifier,
        style = PortioButtonStyle.Secondary,
        enabled = enabled,
        loading = loading
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

/** Cam (Glass) Buton */
@Composable
fun PortioGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    PortioButton(
        onClick = onClick,
        modifier = modifier,
        style = PortioButtonStyle.Glass,
        enabled = enabled
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

/** İkon Butonu */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PortioIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    style: PortioButtonStyle = PortioButtonStyle.Glass,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color = Color.White,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1.0f,
        animationSpec = tween(160),
        label = "iconBtnScale"
    )

    val (bg, border) = when (style) {
        PortioButtonStyle.Primary -> Color.White to null
        PortioButtonStyle.Secondary -> Color.White.copy(alpha = 0.16f) to BorderStroke(1.dp, PortioColors.HairlineStrong)
        PortioButtonStyle.Glass -> Color.Black.copy(alpha = 0.55f) to BorderStroke(1.dp, PortioColors.Hairline)
        PortioButtonStyle.Outline -> Color.Transparent to BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
        PortioButtonStyle.Ghost -> Color.Transparent to null
        PortioButtonStyle.Destructive -> PortioColors.Error.copy(alpha = 0.2f) to BorderStroke(1.dp, PortioColors.Error.copy(alpha = 0.5f))
    }

    Surface(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .focusable(interactionSource = interactionSource, enabled = enabled)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .onKeyEvent { ev ->
                if (com.stalkerapp.ui.tv.isTvSelectKey(ev)) {
                    onClick(); true
                } else false
            },
        shape = CircleShape,
        color = if (isFocused && style != PortioButtonStyle.Primary) Color.White.copy(alpha = 0.22f) else bg,
        border = if (isFocused) BorderStroke(2.dp, PortioColors.FocusBorder) else border
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isFocused && style == PortioButtonStyle.Primary) Color.Black else tint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/** Oynat (Play) Butonu */
@Composable
fun PortioPlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Oynat",
    playing: Boolean = false
) {
    PortioButton(
        onClick = onClick,
        modifier = modifier,
        style = PortioButtonStyle.Primary,
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
    ) { isFocused ->
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

/** Geriye Dönük Uyumluluk için AppleTvButton & AppleTvButtonStyle tanımları */
enum class AppleTvButtonStyle { Primary, Secondary, Glass }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppleTvButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    style: AppleTvButtonStyle = AppleTvButtonStyle.Primary,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.96f
            isFocused -> 1.06f
            else -> 1.0f
        },
        animationSpec = tween(160),
        label = "appleBtnScale"
    )

    val (container, border) = when (style) {
        AppleTvButtonStyle.Primary -> Color.White to null
        AppleTvButtonStyle.Secondary -> (Color.White.copy(alpha = 0.14f)) to BorderStroke(1.dp, PortioColors.HairlineStrong)
        AppleTvButtonStyle.Glass -> (Color.White.copy(alpha = 0.08f)) to BorderStroke(1.dp, PortioColors.Hairline)
    }
    val contentColor = if (style == AppleTvButtonStyle.Primary) Color.Black else Color.White

    Surface(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (isFocused && style == AppleTvButtonStyle.Primary) 16.dp else 0.dp,
                shape = PortioShape.Pill,
                spotColor = PortioColors.FocusGlow.copy(alpha = 0.5f)
            )
            .clip(PortioShape.Pill)
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
            },
        shape = PortioShape.Pill,
        color = container,
        border = if (isFocused && style != AppleTvButtonStyle.Primary) BorderStroke(2.dp, PortioColors.FocusBorder) else border,
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center) { content(isFocused) }
    }
}
