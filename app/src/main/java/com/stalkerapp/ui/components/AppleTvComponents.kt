package com.stalkerapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppleTvCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.95f
            isFocused -> 1.05f
            else -> 1.0f
        },
        label = "cardScale"
    )

    val border = if (isFocused) {
        BorderStroke(3.dp, Color(0xFF00E5FF))
    } else {
        BorderStroke(1.dp, Color.White.copy(0.08f))
    }

    Surface(
        modifier = modifier
            .scale(scale)
            .focusable(interactionSource = interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null, // Custom visual indication via scale/border
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
        shape = RoundedCornerShape(12.dp),
        color = if (isFocused) Color(0xFF1E293B) else Color(0xFF131722),
        border = border
    ) {
        Box {
            content(isFocused)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppleTvButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    focusedColor: Color = Color(0xFF00E5FF),
    unfocusedColor: Color = Color.White.copy(alpha = 0.1f),
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.95f
            isFocused -> 1.08f
            else -> 1.0f
        },
        label = "btnScale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .focusable(interactionSource = interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
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
        shape = RoundedCornerShape(50),
        color = if (isFocused) focusedColor else unfocusedColor,
        border = if (isFocused) BorderStroke(3.dp, Color.White) else null
    ) {
        Box {
            content(isFocused)
        }
    }
}
