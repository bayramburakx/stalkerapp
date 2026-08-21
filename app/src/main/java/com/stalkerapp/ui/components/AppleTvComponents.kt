package com.stalkerapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Apple TV tasarım dili için paylaşılan token'lar.
 * - Derin siyah arka plan (tema zaten AMOLED siyah).
 * - Yalın beyaz metin, ince beyaz kenarlıklar.
 * - Odakta yumuşak beyaz parıltı (glow) + hafif büyüme.
 * - Cam (glassmorphism) yüzeyler: yarı saydam siyah + ince çerçeve.
 */
object AppleTvTokens {
    val FocusGlow = Color.White
    val FocusBorder = Color.White
    val Surface = Color(0xFF0B0B0F)
    val SurfaceRaised = Color(0xFF16161C)
    val Hairline = Color.White.copy(alpha = 0.12f)
    val HairlineStrong = Color.White.copy(alpha = 0.22f)
    val PillShape = RoundedCornerShape(50)
    val CardShape = RoundedCornerShape(18.dp)
    val CardShapeSmall = RoundedCornerShape(12.dp)

    val GlassGradient: Brush
        get() = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.04f)
            )
        )

    val BackdropScrim: Brush
        get() = Brush.verticalGradient(
            listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.55f),
                Color.Black
            )
        )
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
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.96f
            isFocused -> 1.08f
            else -> 1.0f
        },
        animationSpec = tween(180),
        label = "appleCardScale"
    )

    val cardShape = RoundedCornerShape(cornerRadius)

    Surface(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (isFocused) 18.dp else 0.dp,
                shape = cardShape,
                spotColor = AppleTvTokens.FocusGlow.copy(alpha = 0.55f),
                ambientColor = AppleTvTokens.FocusGlow.copy(alpha = 0.35f)
            )
            .clip(cardShape)
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
        shape = cardShape,
        color = if (isFocused) AppleTvTokens.SurfaceRaised else AppleTvTokens.Surface,
        border = if (isFocused) BorderStroke(2.5.dp, AppleTvTokens.FocusBorder) else BorderStroke(1.dp, AppleTvTokens.Hairline)
    ) {
        Box { content(isFocused) }
    }
}

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
        AppleTvButtonStyle.Secondary -> (Color.White.copy(alpha = 0.14f)) to BorderStroke(1.dp, AppleTvTokens.HairlineStrong)
        AppleTvButtonStyle.Glass -> (Color.White.copy(alpha = 0.08f)) to BorderStroke(1.dp, AppleTvTokens.Hairline)
    }
    val contentColor = if (style == AppleTvButtonStyle.Primary) Color.Black else Color.White

    Surface(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (isFocused && style == AppleTvButtonStyle.Primary) 16.dp else 0.dp,
                shape = AppleTvTokens.PillShape,
                spotColor = AppleTvTokens.FocusGlow.copy(alpha = 0.5f)
            )
            .clip(AppleTvTokens.PillShape)
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
        shape = AppleTvTokens.PillShape,
        color = container,
        border = border,
        contentColor = contentColor
    ) {
        Box { content(isFocused) }
    }
}

enum class AppleTvButtonStyle { Primary, Secondary, Glass }

/**
 * Yarı saydam cam (glassmorphism) yüzey. Arka planı karartır, ince beyaz
 * çerçeve ekler. Gerçek bulanıklık (blur) Android TV'de RenderEffect ile
 * pahalı olabileceğinden, gradyan + saydamlıkla "cam hissi" verir; isteğe
 * bağlı [blur] ile dokunmatik cihazlarda gerçek blur açılabilir.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = AppleTvTokens.CardShape,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier.clip(shape),
        shape = shape,
        color = Color.Black.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, AppleTvTokens.Hairline)
    ) {
        Box(modifier = Modifier.background(AppleTvTokens.GlassGradient)) { content() }
    }
}

/** Bölüm başlığı: büyük kalın başlık + isteğe bağlı "Tümünü gör" oku. */
@Composable
fun AppleSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null
) {
    SectionTitle(title = title, modifier = modifier, onSeeAll = onSeeAll)
}

/** İnce ayraç çizgisi (bölümler arası nefes alma). */
@Composable
fun AppleHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(AppleTvTokens.Hairline)
    ) {}
}
