package com.stalkerapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stalkerapp.data.Portal
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.VodCatalogStatus
import com.stalkerapp.ui.components.AppCard
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    onPortalsChanged: () -> Unit = {}
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cooldown by vm.cooldownSeconds.collectAsStateWithLifecycle()
    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()
    val profile = vm.repository.cachedProfile()
    val portals = vm.store.portals()
    val activeId = vm.store.activePortalId()

    var timezoneOffset by remember(settings.timezoneOffset) { mutableFloatStateOf(settings.timezoneOffset.toFloat()) }
    var requestInterval by remember(settings.requestIntervalMs) { mutableFloatStateOf(settings.requestIntervalMs.toFloat()) }
    var buffer by remember(settings.maxBufferMs) { mutableFloatStateOf((settings.maxBufferMs / 1000).toFloat()) }
    var showEpgTimes by remember { mutableStateOf(true) }

    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Portal?>(null) }
    var showSwitch by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineMedium)

        // ---------- VOD sync status ----------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("VOD Kataloğu Senkronizasyonu", style = MaterialTheme.typography.titleMedium)
                when (catalog.status) {
                    VodCatalogStatus.Syncing -> {
                        val ratio = if (catalog.totalCategories > 0) catalog.doneCategories.toFloat() / catalog.totalCategories else 0f
                        LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                        val portalPart = if (catalog.portalTotal > 0) " · portal toplamı: ${catalog.portalTotal}" else ""
                        Text(
                            "${catalog.loadedCount} içerik yüklendi$portalPart · ${catalog.doneCategories}/${catalog.totalCategories} kategori",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Senkronizasyon arka planda devam ediyor. Uygulamayı kapatıp açsanız bile kaldığı yerden sürdürülür.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    VodCatalogStatus.Ready -> {
                        Text(
                            "✓ ${catalog.loadedCount} içerik senkronize edildi",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (catalog.portalTotal > 0 && catalog.loadedCount < catalog.portalTotal * 0.95) {
                            Text(
                                "⚠ Portaldan eksik çekildi (${catalog.loadedCount} / ${catalog.portalTotal}). Şimdi Senkronize Et'e basın.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (catalog.lastSync > 0) {
                            val ago = (System.currentTimeMillis() - catalog.lastSync) / 1000
                            Text(
                                "Son senkron: ${if (ago < 60) "$ago sn önce" else "${ago / 60} dk önce"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    VodCatalogStatus.Error -> {
                        Text("Senkronizasyon hatası oluştu.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    else -> {
                        Text("Katalog henüz yüklenmedi.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { if (profile != null) vm.syncVodCatalog(profile, force = true) }, enabled = profile != null) {
                        Text("Şimdi Senkronize Et")
                    }
                    OutlinedButton(onClick = { vm.resetVodCatalog() }) {
                        Text("Kataloğu Sıfırla")
                    }
                }
            }
        }

        // ---------- EPG ----------
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("EPG", style = MaterialTheme.typography.titleMedium)
                Text(
                    "EPG kaynağı: ${profile?.baseUrl ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = showEpgTimes, onCheckedChange = { showEpgTimes = it })
                    Text("EPG saatlerini yerel saate göre göster", modifier = Modifier.padding(start = 8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                vm.repository.clearEpgCache()
                                vm.showMessage("EPG önbelleği güncellendi")
                            }
                        },
                        enabled = profile != null
                    ) {
                        Text("EPG'yi Güncelle")
                    }
                    OutlinedButton(
                        onClick = {
                            vm.repository.clearEpgCache()
                            vm.showMessage("EPG önbelleği temizlendi")
                        }
                    ) {
                        Text("EPG'yi Temizle")
                    }
                }
            }
        }

        // ---------- Portals ----------
        Text("Portallar", style = MaterialTheme.typography.titleMedium)
        Text(
            "Kullanılan portalı buradan değiştirebilir, yeni portal ekleyebilir veya silebilirsiniz.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (portals.isEmpty()) {
            Text("Kayıtlı portal yok", style = MaterialTheme.typography.bodyMedium)
        } else {
            portals.forEach { p ->
                val isActive = p.id == activeId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(p.name.ifBlank { p.url }, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${p.url}  •  MAC: ${p.mac.ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isActive) {
                            OutlinedButton(onClick = {
                                vm.launchSwitch(p) { onPortalsChanged() }
                            }) {
                                Text("Aktif Yap")
                            }
                        } else {
                            Text(
                                "● Aktif",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                        OutlinedButton(onClick = { editing = p; showDialog = true }) {
                            Text("Düzenle")
                        }
                        OutlinedButton(onClick = {
                            vm.deletePortal(p.id)
                            val remaining = vm.store.portals()
                            if (remaining.isEmpty()) {
                                vm.store.setActivePortalId(null)
                                vm.resetVodCatalog()
                            } else if (vm.store.activePortalId() == null) {
                                vm.launchSwitch(remaining.first()) { onPortalsChanged() }
                            }
                            onPortalsChanged()
                        }) {
                            Text("Sil")
                        }
                    }
                }
            }
        }
        Button(onClick = { editing = null; showDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Yeni Portal Ekle")
        }

        // ---------- Timezone ----------
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Zaman Dilimi Ofseti", style = MaterialTheme.typography.titleMedium)
                Text(
                    "EPG kaymalarını düzeltmek için sağlayıcı sunucusu ile kendi saatiniz arasındaki farkı girin. (+3, -2 vb.)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = timezoneOffset,
                    onValueChange = {
                        timezoneOffset = it
                        vm.saveSettings(settings.copy(timezoneOffset = it.toInt()))
                    },
                    valueRange = -12f..12f,
                    steps = 23
                )
                Text("Ofset: ${timezoneOffset.toInt()} saat", style = MaterialTheme.typography.bodyLarge)
            }
        }

        // ---------- Rate limit ----------
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("İstek Aralığı (Rate Limit)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Stalker portalları ardışık isteklere duyarlıdır. İstekler arası minimum bekleme süresi. (ms)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = requestInterval,
                    onValueChange = {
                        requestInterval = it
                        vm.saveSettings(settings.copy(requestIntervalMs = it.toLong()))
                    },
                    valueRange = 0f..3000f,
                    steps = 29
                )
                Text("${requestInterval.toLong()} ms", style = MaterialTheme.typography.bodyLarge)
            }
        }

        // ---------- Buffer ----------
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Oynatma Tamponu (Buffer)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Canlı yayın takılmalarını azaltmak için tampon süresi. (saniye)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = buffer,
                    onValueChange = {
                        buffer = it
                        vm.saveSettings(settings.copy(maxBufferMs = it.toInt() * 1000))
                    },
                    valueRange = 15f..120f,
                    steps = 20
                )
                Text("${buffer.toInt()} sn", style = MaterialTheme.typography.bodyLarge)
            }
        }

        // ---------- Cooldown ----------
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Cooldown Yönetimi", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (cooldown > 0) "Sunucu istekleri engelledi. Kalan süre: ${cooldown}s"
                    else "Sunucu engeli yok.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (cooldown > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { vm.clearCooldown() },
                    enabled = cooldown > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cooldown'u Temizle")
                }
            }
        }

        // ---------- Account ----------
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Hesap", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Aktif profil: ${profile?.portal?.name ?: profile?.baseUrl ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showSwitch = true }) {
                        Text("Profil Değiştir")
                    }
                    OutlinedButton(onClick = { editing = null; showDialog = true }) {
                        Text("Profil Ekle")
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    if (showDialog) {
        PortalEditDialog(
            initial = editing,
            onDismiss = { showDialog = false },
            onSave = { portal ->
                vm.savePortal(portal)
                showDialog = false
                if (vm.store.activePortalId() == null) {
                    vm.launchSwitch(portal) { onPortalsChanged() }
                }
                onPortalsChanged()
            }
        )
    }

    if (showSwitch) {
        ProfileSwitchDialog(
            portals = portals,
            activeId = activeId,
            onDismiss = { showSwitch = false },
            onSwitch = { p ->
                showSwitch = false
                vm.launchSwitch(p) { onPortalsChanged() }
            }
        )
    }
}

@Composable
private fun ProfileSwitchDialog(
    portals: List<Portal>,
    activeId: String?,
    onDismiss: () -> Unit,
    onSwitch: (Portal) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
        title = { Text("Profil Değiştir") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (portals.isEmpty()) {
                    Text("Kayıtlı profil yok.")
                } else {
                    portals.forEach { p ->
                        val isActive = p.id == activeId
                        OutlinedButton(
                            onClick = { onSwitch(p) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isActive
                        ) {
                            Text(if (isActive) "● ${p.name.ifBlank { p.url }}" else p.name.ifBlank { p.url })
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun PortalEditDialog(
    initial: Portal?,
    onDismiss: () -> Unit,
    onSave: (Portal) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var mac by remember { mutableStateOf(initial?.mac ?: "") }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val trimmed = url.trim()
                if (trimmed.isBlank()) { error = "Portal adresi gerekli"; return@TextButton }
                val id = initial?.id ?: ("p_" + trimmed.hashCode().toString() + System.currentTimeMillis().toString().takeLast(4))
                onSave(
                    Portal(
                        id = id,
                        name = name.ifBlank { trimmed },
                        url = trimmed,
                        mac = mac.trim(),
                        username = username.trim(),
                        password = password.trim()
                    )
                )
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
        title = { Text(if (initial == null) "Yeni Portal" else "Portal Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("İsim") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Portal URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mac, onValueChange = { mac = it }, label = { Text("MAC (boş olabilir)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Kullanıcı adı (opsiyonel)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Şifre (opsiyonel)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    )
}
