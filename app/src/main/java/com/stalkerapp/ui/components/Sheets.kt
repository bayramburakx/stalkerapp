package com.stalkerapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stalkerapp.data.UpdateInfo
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.ui.theme.PortioShape
import com.stalkerapp.util.L10n

/**
 * Portio Modal & Alt Sayfa (Sheets & Dialogs) Bileşenleri
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortioBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = PortioColors.SurfaceRaised,
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = PortioShape.BottomSheet,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(PortioShape.Pill)
                    .background(Color.White.copy(alpha = 0.25f))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            content = content
        )
    }
}

/** Standart Portio Diyaloğu */
@Composable
fun PortioDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = content,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        containerColor = PortioColors.SurfaceRaised,
        shape = PortioShape.Dialog,
        modifier = modifier
    )
}

/** Onay / İptal Diyaloğu */
@Composable
fun PortioConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Tamam",
    dismissText: String = "İptal",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    PortioDialog(
        title = title,
        onDismissRequest = onDismiss,
        confirmButton = {
            PortioButton(
                onClick = onConfirm,
                style = if (isDestructive) PortioButtonStyle.Destructive else PortioButtonStyle.Primary
            ) {
                Text(confirmText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            PortioButton(
                onClick = onDismiss,
                style = PortioButtonStyle.Glass
            ) {
                Text(dismissText, color = Color.White)
            }
        }
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = PortioColors.TextSecondary
        )
    }
}

/** Boş Durum (EmptyState) Göstergesi */
@Composable
fun EmptyState(
    text: String = "",
    icon: ImageVector? = null,
    title: String? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = PortioColors.OnSurfaceMuted,
                    modifier = Modifier.size(48.dp)
                )
            }
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = subtitle ?: text,
                style = MaterialTheme.typography.bodyMedium,
                color = PortioColors.TextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Yetişkin İçerik PIN Kilidi Diyaloğu */
@Composable
fun AdultPinDialog(
    lang: String,
    onUnlock: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    PortioDialog(
        title = L10n.t(lang, "Yetişkin İçerik Kilitli"),
        onDismissRequest = onDismiss,
        confirmButton = {
            PortioPrimaryButton(
                text = L10n.t(lang, "Aç"),
                onClick = { if (!onUnlock(pin)) error = true },
                enabled = pin.isNotBlank()
            )
        },
        dismissButton = {
            PortioGlassButton(
                text = L10n.t(lang, "Vazgeç"),
                onClick = onDismiss
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = L10n.t(lang, "Bu içeriği görmek için PIN gerekli (Gizlilik & Güvenlik'te ayarlanır)."),
                style = MaterialTheme.typography.bodySmall,
                color = PortioColors.TextSecondary
            )
            PortioTextField(
                value = pin,
                onValueChange = { pin = it.take(8); error = false },
                label = "PIN",
                isError = error,
                errorMessage = if (error) L10n.t(lang, "Yanlış PIN") else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
        }
    }
}

/** Uygulama İçi Güncelleme Diyaloğu */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    lang: String,
    onDismiss: () -> Unit,
    onUpdateNow: () -> Unit,
    onRemindLater: () -> Unit,
    onNeverAsk: () -> Unit
) {
    PortioDialog(
        title = L10n.t(lang, "Yeni sürüm var!"),
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            PortioGlassButton(
                text = L10n.t(lang, "Vazgeç"),
                onClick = onDismiss
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "v${info.version} ${L10n.t(lang, "yayınlandı")} (${info.publishedAt.take(10)}). " +
                    L10n.t(lang, "Güncel APK'yı indirip kurmak ister misin?"),
                style = MaterialTheme.typography.bodyMedium,
                color = PortioColors.TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            PortioPrimaryButton(
                text = L10n.t(lang, "Şimdi Güncelle"),
                onClick = onUpdateNow,
                modifier = Modifier.fillMaxWidth()
            )
            PortioSecondaryButton(
                text = L10n.t(lang, "Sonra Hatırlat"),
                onClick = onRemindLater,
                modifier = Modifier.fillMaxWidth()
            )
            PortioGlassButton(
                text = L10n.t(lang, "Bir Daha Sorma"),
                onClick = onNeverAsk,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
