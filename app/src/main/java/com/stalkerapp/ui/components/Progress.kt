package com.stalkerapp.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.ui.theme.PortioShape

/**
 * Portio İlerleme ve Yükleme Göstergeleri (Progress & Shimmer)
 */

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp)
        )
    }
}

/** Doğrusal İlerleme Çubuğu (PortioProgressBar) */
@Composable
fun PortioProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    trackColor: Color = Color.White.copy(alpha = 0.2f),
    bufferedProgress: Float? = null,
    height: Dp = 4.dp,
    shape: Shape = PortioShape.Pill
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(trackColor)
    ) {
        if (bufferedProgress != null && bufferedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferedProgress.coerceIn(0f, 1f))
                    .height(height)
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.4f))
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(shape)
                .background(color)
        )
    }
}

/** Oynatıcı İlerleme Çubuğu (PortioScrubber) */
@Composable
fun PortioScrubber(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long = 0L,
    modifier: Modifier = Modifier,
    activeColor: Color = PortioColors.AccentRed,
    trackColor: Color = Color.White.copy(alpha = 0.25f),
    bufferedColor: Color = Color.White.copy(alpha = 0.45f)
) {
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val bufferedProgress = if (durationMs > 0) (bufferedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    PortioProgressBar(
        progress = progress,
        bufferedProgress = bufferedProgress,
        color = activeColor,
        trackColor = trackColor,
        height = 6.dp,
        modifier = modifier
    )
}

/** Shimmer Efekti İçin Gradyan Fırçası */
@Composable
fun rememberShimmerBrush(
    shimmerColors: List<Color> = listOf(
        Color.White.copy(alpha = 0.04f),
        Color.White.copy(alpha = 0.14f),
        Color.White.copy(alpha = 0.04f)
    )
): Brush {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )
}

/** Shimmer Yükleme Kutusu */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = PortioShape.CardSmall
) {
    val brush = rememberShimmerBrush()
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

/** Medya Afişi İskeleti (PosterSkeleton) */
@Composable
fun PosterSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ShimmerBox(
            shape = PortioShape.Poster,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
        )
        Spacer(Modifier.height(8.dp))
        ShimmerBox(
            shape = PortioShape.Small,
            modifier = Modifier.fillMaxWidth(0.8f).height(14.dp)
        )
        Spacer(Modifier.height(4.dp))
        ShimmerBox(
            shape = PortioShape.Small,
            modifier = Modifier.fillMaxWidth(0.4f).height(10.dp)
        )
    }
}

/** Kanal Satırı İskeleti (ChannelSkeleton) */
@Composable
fun ChannelSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBox(
            shape = PortioShape.Small,
            modifier = Modifier.size(44.dp, 32.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            ShimmerBox(
                shape = PortioShape.Small,
                modifier = Modifier.fillMaxWidth(0.6f).height(16.dp)
            )
            Spacer(Modifier.height(6.dp))
            ShimmerBox(
                shape = PortioShape.Small,
                modifier = Modifier.fillMaxWidth(0.35f).height(12.dp)
            )
        }
    }
}
