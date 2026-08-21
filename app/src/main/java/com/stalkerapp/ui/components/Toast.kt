package com.stalkerapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.ui.theme.PortioShape
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow

enum class ToastType {
    Success,
    Error,
    Info,
    Warning
}

data class ToastData(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val type: ToastType = ToastType.Info,
    val durationMs: Long = 3000L
)

/**
 * Toast Bildirim Yöneticisi (ToastManager Singleton)
 */
object ToastManager {
    private val _toastEvents = Channel<ToastData>(Channel.BUFFERED)
    val toastEvents = _toastEvents.receiveAsFlow()

    fun show(message: String, type: ToastType = ToastType.Info, durationMs: Long = 3000L) {
        _toastEvents.trySend(ToastData(message = message, type = type, durationMs = durationMs))
    }

    fun success(message: String) = show(message, ToastType.Success)
    fun error(message: String) = show(message, ToastType.Error)
    fun info(message: String) = show(message, ToastType.Info)
    fun warning(message: String) = show(message, ToastType.Warning)
}

/**
 * Toast Durum Tutucusu
 */
class ToastState {
    var currentToast by mutableStateOf<ToastData?>(null)
        private set

    fun show(toast: ToastData) {
        currentToast = toast
    }

    fun dismiss() {
        currentToast = null
    }
}

@Composable
fun rememberToastState(): ToastState {
    return remember { ToastState() }
}

/**
 * Toast Gösterim Host Bileşeni (Ekranın üstünde yüzer)
 */
@Composable
fun ToastHost(
    modifier: Modifier = Modifier,
    toastState: ToastState = rememberToastState()
) {
    LaunchedEffect(Unit) {
        ToastManager.toastEvents.collect { toast ->
            toastState.show(toast)
            delay(toast.durationMs)
            toastState.dismiss()
        }
    }

    val current = toastState.currentToast

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 16.dp, start = 20.dp, end = 20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            current?.let { toast ->
                PortioToast(toast = toast)
            }
        }
    }
}

/**
 * Portio Toast Görsel Bileşeni
 */
@Composable
fun PortioToast(
    toast: ToastData,
    modifier: Modifier = Modifier
) {
    val (icon: ImageVector, iconColor: Color, borderColor: Color) = when (toast.type) {
        ToastType.Success -> Triple(Icons.Default.CheckCircle, PortioColors.Success, PortioColors.Success.copy(alpha = 0.4f))
        ToastType.Error -> Triple(Icons.Default.Error, PortioColors.Error, PortioColors.Error.copy(alpha = 0.4f))
        ToastType.Warning -> Triple(Icons.Default.Warning, PortioColors.Warning, PortioColors.Warning.copy(alpha = 0.4f))
        ToastType.Info -> Triple(Icons.Default.Info, PortioColors.Info, PortioColors.Info.copy(alpha = 0.4f))
    }

    Surface(
        shape = PortioShape.Pill,
        color = Color(0xFF14141E).copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        shadowElevation = 12.dp,
        modifier = modifier.clip(PortioShape.Pill)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = toast.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}
