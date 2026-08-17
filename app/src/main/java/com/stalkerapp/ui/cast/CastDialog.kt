package com.stalkerapp.ui.cast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stalkerapp.StalkerApp
import com.stalkerapp.cast.CastManager

private val L10nLocal: Map<String, String> = mapOf(
    // Türkçe -> English
    "Yayınla (Chromecast)" to "Cast (Chromecast)",
    "Yayın devam ediyor.\nCihaz listesi şu an boş görünüyor." to "Casting is in progress.\nThe device list appears empty right now.",
    "Yayın cihazı aranıyor…\nTelefon ve cihazın aynı Wi-Fi ağında olduğundan emin ol." to "Searching for cast devices…\nMake sure your phone and device are on the same Wi-Fi network.",
    "Bağlanıyor…" to "Connecting…",
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
        title = { Text(str(lang, "Yayınla (Chromecast)")) },
        text = {
            Column {
                if (routes.isEmpty()) {
                    Text(
                        if (isCasting) {
                            str(lang, "Yayın devam ediyor.\nCihaz listesi şu an boş görünüyor.")
                        } else {
                            str(lang, "Yayın cihazı aranıyor…\nTelefon ve cihazın aynı Wi-Fi ağında olduğundan emin ol.")
                        }
                    )
                } else {
                    routes.forEach { route ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (route.selected) onDisconnect() else onConnect(route)
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (route.selected) Icons.Default.CastConnected else Icons.Default.Cast,
                                contentDescription = null,
                                tint = if (route.selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = route.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (route.connecting) {
                                Text(str(lang, "Bağlanıyor…"), style = MaterialTheme.typography.labelMedium)
                            } else if (route.selected) {
                                Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(str(lang, "Kapat")) }
        }
    )
}
