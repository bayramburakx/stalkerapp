package com.stalkerapp.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stalkerapp.BuildConfig
import com.stalkerapp.data.M3uSource
import com.stalkerapp.data.Portal
import com.stalkerapp.data.UpdateChecker
import com.stalkerapp.data.UpdateInfo
import com.stalkerapp.data.XtreamClient
import com.stalkerapp.data.XtreamSource
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
    val activeKind = vm.activeSourceKind()
    val activeSourceId = vm.activeSourceId()

    var timezoneOffset by remember(settings.timezoneOffset) { mutableFloatStateOf(settings.timezoneOffset.toFloat()) }
    var requestInterval by remember(settings.requestIntervalMs) { mutableFloatStateOf(settings.requestIntervalMs.toFloat()) }
    var buffer by remember(settings.maxBufferMs) { mutableFloatStateOf((settings.maxBufferMs / 1000).toFloat()) }
    var showEpgTimes by remember { mutableStateOf(true) }

    var tmdbKey by remember(settings.tmdbApiKey) { mutableStateOf(settings.tmdbApiKey) }
    var epgUrl by remember(settings.epgUrl) { mutableStateOf(settings.epgUrl) }
    var updateDialog by remember { mutableStateOf<UpdateInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Kaynak düzenleme dialog'ları.
    var showPortalDialog by remember { mutableStateOf(false) }
    var editingPortal by remember { mutableStateOf<Portal?>(null) }
    var showSwitch by remember { mutableStateOf(false) }
    var showM3uDialog by remember { mutableStateOf(false) }
    var editingM3u by remember { mutableStateOf<M3uSource?>(null) }
    var showXtreamDialog by remember { mutableStateOf(false) }
    var editingXtream by remember { mutableStateOf<XtreamSource?>(null) }

    fun checkForUpdate() {
        scope.launch {
            checkingUpdate = true
            updateMessage = null
            try {
                val info = UpdateChecker().latest()
                if (info != null && UpdateChecker.isNewer(info.version, BuildConfig.VERSION_NAME)) {
                    updateDialog = info
                } else {
                    updateMessage = "Güncel sürümdesiniz (v${BuildConfig.VERSION_NAME})"
                }
            } catch (e: Exception) {
                updateMessage = "Güncelleme kontrol edilemedi: ${e.message ?: "ağ hatası"}"
            } finally {
                checkingUpdate = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // ================= KAYNAKLAR =================
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SectionHeader(Icons.Default.Tv, "Kaynaklar")
                Text(
                    "Uygulama Stalker portal, M3U listesi ve Xtream Codes destekler. " +
                        "Canlı TV sekmesi hangi kaynak aktifse onun kanallarını gösterir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ---- Stalker portallar ----
                SourceGroupTitle("Stalker Portallar", activeKind == "stalker")
                if (portals.isEmpty()) {
                    Text("Kayıtlı portal yok", style = MaterialTheme.typography.bodyMedium)
                } else {
                    portals.forEach { p ->
                        val isActive = activeKind == "stalker" && p.id == activeId
                        SourceRow(
                            name = p.name.ifBlank { p.url },
                            subtitle = "${p.url} • MAC: ${p.mac.ifBlank { "—" }}",
                            isActive = isActive,
                            onActivate = {
                                vm.setActiveSource("stalker", null)
                                vm.launchSwitch(p) { onPortalsChanged() }
                            },
                            onEdit = { editingPortal = p; showPortalDialog = true },
                            onDelete = {
                                vm.deletePortal(p.id)
                                val remaining = vm.store.portals()
                                if (remaining.isEmpty()) {
                                    vm.store.setActivePortalId(null)
                                    vm.resetVodCatalog()
                                } else if (vm.store.activePortalId() == null) {
                                    vm.launchSwitch(remaining.first()) { onPortalsChanged() }
                                }
                                onPortalsChanged()
                            }
                        )
                    }
                }
                OutlinedButton(
                    onClick = { editingPortal = null; showPortalDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Stalker Portal Ekle")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ---- M3U ----
                SourceGroupTitle("M3U Listeleri", activeKind == "m3u")
                val m3uSources = vm.m3uSources()
                if (m3uSources.isEmpty()) {
                    Text("Kayıtlı M3U kaynağı yok", style = MaterialTheme.typography.bodyMedium)
                } else {
                    m3uSources.forEach { s ->
                        SourceRow(
                            name = s.name.ifBlank { s.url },
                            subtitle = s.url,
                            isActive = activeKind == "m3u" && activeSourceId == s.id,
                            onActivate = { vm.setActiveSource("m3u", s.id) },
                            onEdit = { editingM3u = s; showM3uDialog = true },
                            onDelete = { vm.deleteM3uSource(s.id) }
                        )
                    }
                }
                OutlinedButton(
                    onClick = { editingM3u = null; showM3uDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("M3U Ekle")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ---- Xtream ----
                SourceGroupTitle("Xtream Codes", activeKind == "xtream")
                val xtreamSources = vm.xtreamSources()
                if (xtreamSources.isEmpty()) {
                    Text("Kayıtlı Xtream kaynağı yok", style = MaterialTheme.typography.bodyMedium)
                } else {
                    xtreamSources.forEach { s ->
                        SourceRow(
                            name = s.name.ifBlank { s.server },
                            subtitle = "${s.server} • ${s.username}",
                            isActive = activeKind == "xtream" && activeSourceId == s.id,
                            onActivate = { vm.setActiveSource("xtream", s.id) },
                            onEdit = { editingXtream = s; showXtreamDialog = true },
                            onDelete = { vm.deleteXtreamSource(s.id) }
                        )
                    }
                }
                OutlinedButton(
                    onClick = { editingXtream = null; showXtreamDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Xtream Ekle")
                }
            }
        }

        // ================= VOD SENKRON =================
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionHeader(Icons.Default.VideoLibrary, "VOD Kataloğu Senkronizasyonu")
                when (catalog.status) {
                    VodCatalogStatus.Syncing -> {
                        val ratio = if (catalog.totalCategories > 0) catalog.doneCategories.toFloat() / catalog.totalCategories else 0f
                        LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                        val portalPart = if (catalog.portalTotal > 0) " • portal toplamı: ${catalog.portalTotal}" else ""
                        Text(
                            "${catalog.loadedCount} içerik yüklendi$portalPart • ${catalog.doneCategories}/${catalog.totalCategories} kategori",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Arka planda devam ediyor; uygulama kapansa bile kaldığı yerden sürdürülür.",
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
                    else -> Text("Katalog henüz yüklenmedi.", style = MaterialTheme.typography.bodyMedium)
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

        // ================= EPG =================
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionHeader(Icons.Default.CalendarMonth, "EPG Rehberi")
                Text(
                    "Portal kendi EPG'sini vermiyorsa harici bir XMLTV linki kullanılır " +
                        "(kanal eşleşmesi xmltv_id ile yapılır). Ör: sağlayıcının EPG linki ya da " +
                        "iptv-org/epg çıktısı (.xml / .xml.gz).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = epgUrl,
                    onValueChange = { epgUrl = it },
                    label = { Text("Harici EPG (XMLTV) URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.saveSettings(settings.copy(epgUrl = epgUrl.trim()))
                            vm.repository.clearEpgCache()
                            vm.showMessage("EPG kaynağı kaydedildi — rehber bir sonraki açılışta yüklenir")
                        },
                        enabled = epgUrl.trim().isNotEmpty()
                    ) {
                        Text("Kaydet")
                    }
                    OutlinedButton(onClick = {
                        vm.repository.clearEpgCache()
                        vm.showMessage("EPG önbelleği temizlendi")
                    }) {
                        Text("Temizle")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = showEpgTimes, onCheckedChange = { showEpgTimes = it })
                    Text("EPG saatlerini yerel saate göre göster", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        // ================= TMDB =================
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionHeader(Icons.Default.Movie, "TMDB Zenginleştirme")
                Text(
                    "Oyuncu fotoğrafları, fragman ve bölüm adları için TMDB API anahtarı " +
                        "(themoviedb.org → Ayarlar → API — ücretsiz). Boşsa kapalıdır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = tmdbKey,
                    onValueChange = { tmdbKey = it },
                    label = { Text("TMDB API Anahtarı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        vm.saveSettings(settings.copy(tmdbApiKey = tmdbKey.trim()))
                        vm.showMessage("TMDB anahtarı kaydedildi")
                    },
                    enabled = tmdbKey.trim().isNotEmpty()
                ) {
                    Text("Kaydet")
                }
            }
        }

        // ================= OYNATICI =================
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SectionHeader(Icons.Default.VolumeUp, "Oynatıcı")
                SliderSetting(
                    icon = Icons.Default.Speed,
                    title = "İstek Aralığı (Rate Limit)",
                    description = "Stalker portalları ardışık isteklere duyarlıdır. İstekler arası bekleme (ms).",
                    value = requestInterval,
                    valueRange = 0f..3000f,
                    steps = 29,
                    valueText = "${requestInterval.toLong()} ms",
                    onChange = {
                        requestInterval = it
                        vm.saveSettings(settings.copy(requestIntervalMs = it.toLong()))
                    }
                )
                SliderSetting(
                    icon = Icons.Default.Schedule,
                    title = "Oynatma Tamponu (Buffer)",
                    description = "Canlı yayın takılmalarını azaltmak için tampon süresi (saniye).",
                    value = buffer,
                    valueRange = 15f..120f,
                    steps = 20,
                    valueText = "${buffer.toInt()} sn",
                    onChange = {
                        buffer = it
                        vm.saveSettings(settings.copy(maxBufferMs = it.toInt() * 1000))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = settings.bingeMode, onCheckedChange = { vm.saveSettings(settings.copy(bingeMode = it)) })
                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        Text("Binge Mod", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Bölüm bitince sıradaki bölüm otomatik oynatılır ve izlendi işaretlenir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text("Cooldown Yönetimi", style = MaterialTheme.typography.titleSmall)
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

        // ================= ZAMAN DİLİMİ =================
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionHeader(Icons.Default.Schedule, "Zaman Dilimi Ofseti")
                Text(
                    "EPG kaymalarını düzeltmek için sunucu ile kendi saatiniz arasındaki farkı girin (+3, -2 vb.).",
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

        // ================= HESAP =================
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionHeader(Icons.Default.Person, "Hesap")
                Text(
                    "Aktif profil: ${profile?.portal?.name ?: profile?.baseUrl ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showSwitch = true }) {
                        Text("Profil Değiştir")
                    }
                    OutlinedButton(onClick = { editingPortal = null; showPortalDialog = true }) {
                        Text("Profil Ekle")
                    }
                }
            }
        }

        // ================= UYGULAMA =================
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionHeader(Icons.Default.SystemUpdate, "Uygulama Güncellemesi")
                Text(
                    "Mevcut sürüm: v${BuildConfig.VERSION_NAME}. GitHub üzerinden yeni APK yayınlanıp yayınlanmadığını kontrol eder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { checkForUpdate() }, enabled = !checkingUpdate) {
                    Text(if (checkingUpdate) "Kontrol ediliyor…" else "Güncelleme Kontrol Et")
                }
                if (updateMessage != null) {
                    Text(updateMessage.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Alt boşluk: içerik yüzen cam pill'in arkasından akıyor.
        Spacer(Modifier.height(96.dp))
    }

    if (showPortalDialog) {
        PortalEditDialog(
            initial = editingPortal,
            onDismiss = { showPortalDialog = false },
            onSave = { portal ->
                vm.savePortal(portal)
                showPortalDialog = false
                if (vm.store.activePortalId() == null) {
                    vm.launchSwitch(portal) { onPortalsChanged() }
                }
                onPortalsChanged()
            }
        )
    }

    if (showM3uDialog) {
        M3uDialog(
            initial = editingM3u,
            onDismiss = { showM3uDialog = false },
            onSave = { source ->
                vm.saveM3uSource(source)
                showM3uDialog = false
                if (editingM3u == null) {
                    vm.setActiveSource("m3u", source.id)
                    vm.showMessage("M3U kaynağı eklendi — Canlı TV'de yükleniyor")
                }
            }
        )
    }

    if (showXtreamDialog) {
        XtreamDialog(
            initial = editingXtream,
            onDismiss = { showXtreamDialog = false },
            onSave = { source ->
                vm.saveXtreamSource(source)
                showXtreamDialog = false
                if (editingXtream == null) {
                    vm.setActiveSource("xtream", source.id)
                    vm.showMessage("Xtream kaynağı eklendi — Canlı TV'de yükleniyor")
                }
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
                vm.setActiveSource("stalker", null)
                vm.launchSwitch(p) { onPortalsChanged() }
            }
        )
    }

    updateDialog?.let { info ->
        AlertDialog(
            onDismissRequest = { updateDialog = null },
            confirmButton = {
                TextButton(onClick = {
                    updateDialog = null
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
                    }
                }) { Text("İndir") }
            },
            dismissButton = { TextButton(onClick = { updateDialog = null }) { Text("Sonra") } },
            title = { Text("Yeni sürüm var!") },
            text = {
                Text(
                    "v${info.version} yayınlandı (${info.publishedAt.take(10)}). " +
                        "Güncel APK'yı indirip kurmak ister misin?"
                )
            }
        )
    }
}

// ---------- Yardımcı bileşenler ----------

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clipToRounded(10.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SourceGroupTitle(title: String, isActive: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (isActive) {
            Text(
                "● Aktif",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun SourceRow(
    name: String,
    subtitle: String,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (isActive) {
                Text("●", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isActive) {
                OutlinedButton(onClick = onActivate, modifier = Modifier.weight(1f)) {
                    Text("Aktif Yap")
                }
            }
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                Text("Düzenle")
            }
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                Text("Sil")
            }
        }
    }
}

@Composable
private fun SliderSetting(
    icon: ImageVector,
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    onChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value, onValueChange = onChange, valueRange = valueRange, steps = steps)
        Text(valueText, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun Modifier.clipToRounded(radius: Dp) = this.clip(RoundedCornerShape(radius))

// ---------- Dialog'lar ----------

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
        title = { Text(if (initial == null) "Yeni Stalker Portal" else "Portal Düzenle") },
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

@Composable
private fun M3uDialog(
    initial: M3uSource?,
    onDismiss: () -> Unit,
    onSave: (M3uSource) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val trimmed = url.trim()
                if (!trimmed.startsWith("http")) { error = "Geçerli bir http(s) URL girin"; return@TextButton }
                val id = initial?.id ?: ("m3u_" + trimmed.hashCode().toString() + System.currentTimeMillis().toString().takeLast(4))
                onSave(M3uSource(id = id, name = name.ifBlank { trimmed }, url = trimmed))
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
        title = { Text(if (initial == null) "M3U Ekle" else "M3U Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "M3U listesinin adresini girin (#EXTM3U içeren dosya). Kanal kategorileri group-title'dan gelir.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("İsim") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("M3U URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    )
}

@Composable
private fun XtreamDialog(
    initial: XtreamSource?,
    onDismiss: () -> Unit,
    onSave: (XtreamSource) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var server by remember { mutableStateOf(initial?.server ?: "") }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val srv = server.trim()
                if (!srv.startsWith("http")) { error = "Geçerli bir http(s) sunucu adresi girin"; return@TextButton }
                if (username.trim().isBlank()) { error = "Kullanıcı adı gerekli"; return@TextButton }
                checking = true
                error = null
                val candidate = XtreamSource(
                    id = initial?.id ?: ("xt_" + srv.hashCode().toString() + username.trim().hashCode().toString()),
                    name = name.ifBlank { srv },
                    server = srv,
                    username = username.trim(),
                    password = password.trim()
                )
                scope.launch {
                    val ok = runCatching { XtreamClient().validate(candidate) }.getOrDefault(false)
                    checking = false
                    if (ok) {
                        onSave(candidate)
                    } else {
                        error = "Xtream doğrulaması başarısız — sunucu, kullanıcı adı veya şifre hatalı"
                    }
                }
            }) { Text(if (checking) "Kontrol ediliyor…" else "Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
        title = { Text(if (initial == null) "Xtream Ekle" else "Xtream Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Xtream Codes sunucusu, kullanıcı adı ve şifre girin (ör: http://sunucu:8080). " +
                        "Kaydetmeden önce doğrulanır.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("İsim") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = server, onValueChange = { server = it }, label = { Text("Sunucu (http://host:port)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Kullanıcı adı") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Şifre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    )
}
