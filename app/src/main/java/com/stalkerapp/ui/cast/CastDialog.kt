package com.stalkerapp.ui.cast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stalkerapp.StalkerApp
import com.stalkerapp.cast.CastManager

private val L10nLocal: Map<String, String> = mapOf(
    "Yayınla (Chromecast)" to "Cast (Chromecast)",
    "Yayın devam ediyor.\nCihaz listesi şu an boş görünüyor." to "Casting is in progress.\nThe device list appears empty right now.",
    "Yayın cihazı aranıyor…" to "Searching for cast devices…",
    "Telefon ve cihazın aynı Wi-Fi ağında olduğundan emin ol." to "Make sure your phone and device are on the same Wi-Fi network.",
    "Bağlanıyor…" to "Connecting…",
    "Bağlantıyı Kes" to "Disconnect",
    "Kapat" to "Close",
)
private fun str(lang: String, text: String): String =
    if (lang == "en") L10nLocal[text] ?: text else text

/**
 * Chromecast cihaz seçim dialog'u. Keşfedilen cihazları listeler; seçilen
 * cihaza dokunmak bağlanır, bağlıyken tekrar dokunmak bağlantıyı keser.
 */
@Composable
fun CastDialog(
    routes: List<CastManager.CastRoute>,
    isCasting: Boolean,
    onConnect: (CastManager.CastRoute) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val lang = (LocalContext.current.applicationContext as StalkerApp).store.settings().language
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF18181B),
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                    contentDescription = null,
                    tint = if (isCasting) MaterialTheme.colorScheme.primary else Color.White
                )
                Text(
                    str(lang, "Yayınla (Chromecast)"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (routes.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF222226),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (!isCasting) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                                Spacer(Modifier.height(10.dp))
                            }
                            Text(
                                if (isCasting) {
                                    str(lang, "Yayın devam ediyor.\nCihaz listesi şu an boş görünüyor.")
                                } else {
                                    str(lang, "Yayın cihazı aranıyor…")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                str(lang, "Telefon ve cihazın aynı Wi-Fi ağında olduğundan emin ol."),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    routes.forEach { route ->
                        val isSelected = route.selected
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color(0xFF222226),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) onDisconnect() else onConnect(route)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.CastConnected else Icons.Default.Cast,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = route.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color.White
                                    )
                                    if (isSelected) {
                                        Text(
                                            str(lang, "Bağlı — Dokunarak yayını durdur"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                if (route.connecting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isCasting) {
                TextButton(onClick = onDisconnect) {
                    Text(str(lang, "Bağlantıyı Kes"), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(str(lang, "Kapat"), color = Color.White.copy(alpha = 0.8f))
            }
        }
    )
}
