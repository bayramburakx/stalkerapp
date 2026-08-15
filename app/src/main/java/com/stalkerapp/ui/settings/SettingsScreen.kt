package com.stalkerapp.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stalkerapp.BuildConfig
import com.stalkerapp.data.M3uSource
import com.stalkerapp.data.Portal
import com.stalkerapp.data.UpdateChecker
import com.stalkerapp.data.UpdateInfo
import com.stalkerapp.data.UserProfile
import com.stalkerapp.data.XtreamClient
import com.stalkerapp.data.XtreamSource
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.VodCatalogStatus
import com.stalkerapp.ui.components.GlassChip
import kotlinx.coroutines.launch

private val AVATARS = listOf(
    "😀", "😎", "🦊", "🐼", "🐸", "🐙", "🦁", "🐯",
    "🚀", "🎬", "🍿", "🎮", "⚽", "🎵", "👾", "🤖"
)

private val HOME_SECTIONS = listOf(
    "recent" to "Son İzlenenler",
    "continue" to "İzlemeye Devam",
    "movies" to "Popüler Filmler",
    "series" to "Popüler Diziler",
    "favchannels" to "Favori Kanallar",
    "live" to "Canlı TV",
    "favvods" to "Favori Filmler & Diziler"
)

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    onPortalsChanged: () -> Unit = {},
    onOpenLibrary: () -> Unit = {}
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cooldown by vm.cooldownSeconds.collectAsStateWithLifecycle()
    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()
    val profile by vm.userProfile.collectAsStateWithLifecycle()
    val sourcesVersion by vm.sourcesVersion.collectAsStateWithLifecycle()
    val userLists by vm.userLists.collectAsStateWithLifecycle()
    val appProfile = vm.repository.cachedProfile()
    val activeKind = vm.activeSourceKind()
    val activeSourceId = vm.activeSourceId()

    // Kaynak listeleri: kompozisyon içinde prefs okumak donmaya yol açar —
    // kaynak sürümü değişince (remember key) bir kez okunur.
    val portals = remember(sourcesVersion) { vm.portals() }
    val activeId = remember(sourcesVersion) { vm.activePortalId() }
    val m3uSources = remember(sourcesVersion) { vm.m3uSources() }
    val xtreamSources = remember(sourcesVersion) { vm.xtreamSources() }

    var timezoneOffset by remember(settings.timezoneOffset) { mutableFloatStateOf(settings.timezoneOffset.toFloat()) }
    var requestInterval by remember(settings.requestIntervalMs) { mutableFloatStateOf(settings.requestIntervalMs.toFloat()) }
    var buffer by remember(settings.maxBufferMs) { mutableFloatStateOf((settings.maxBufferMs / 1000).toFloat()) }

    var tmdbKey by remember(settings.tmdbApiKey) { mutableStateOf(settings.tmdbApiKey) }
    var epgUrl by remember(settings.epgUrl) { mutableStateOf(settings.epgUrl) }
    var updateDialog by remember { mutableStateOf<UpdateInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    // Dialog'lar
    var showPortalDialog by remember { mutableStateOf(false) }
    var editingPortal by remember { mutableStateOf<Portal?>(null) }
    var showSwitch by remember { mutableStateOf(false) }
    var showM3uDialog by remember { mutableStateOf(false) }
    var editingM3u by remember { mutableStateOf<M3uSource?>(null) }
    var showXtreamDialog by remember { mutableStateOf(false) }
    var editingXtream by remember { mutableStateOf<XtreamSource?>(null) }
    var showProfileEdit by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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

        // ================= PLAYLIST & KAYNAKLAR =================
        AccordionSection(
            icon = Icons.Default.Tv,
            title = "Playlist & Kaynaklar",
            initiallyExpanded = true
        ) {
                Text(
                    "Stalker portal, M3U listesi ve Xtream Codes kaynaklarını buradan yönetirsin. " +
                        "Kapatılan kaynak türü Canlı TV ve kütüphanede kullanılmaz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ---- Kaynak türü anahtarları ----
                ToggleRow(
                    icon = Icons.Default.LiveTv,
                    title = "Stalker Portallar",
                    desc = "http://ip:port/c biçimindeki Stalker portal profilleri",
                    checked = settings.stalkerEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(stalkerEnabled = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Link,
                    title = "M3U Listeleri",
                    desc = "#EXTM3U biçimindeki kanal listeleri",
                    checked = settings.m3uEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(m3uEnabled = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.PlayArrow,
                    title = "Xtream Codes",
                    desc = "Sunucu + kullanıcı adı + şifre ile Xtream paneli",
                    checked = settings.xtreamEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(xtreamEnabled = it)) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ---- Stalker portallar ----
                if (settings.stalkerEnabled) {
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
                                    val remaining = vm.portals()
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
                }

                // ---- M3U ----
                if (settings.m3uEnabled) {
                    SourceGroupTitle("M3U Listeleri", activeKind == "m3u")
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
                }

                // ---- Xtream ----
                if (settings.xtreamEnabled) {
                    SourceGroupTitle("Xtream Codes", activeKind == "xtream")
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

        // ================= KÜTÜPHANE & İÇERİK =================
        AccordionSection(
            icon = Icons.Default.VideoLibrary,
            title = "Kütüphane & İçerik"
        ) {

                ToggleRow(
                    icon = Icons.Default.Lock,
                    title = "+18 İçerikler",
                    desc = "Yetişkin içerikli kategorileri göster",
                    checked = settings.adultContentEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(adultContentEnabled = it)) }
                )

                // ---- Gizlenen kategoriler ----
                Text(
                    "Gizlenecek Kategoriler",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    "İstemediğin kategorileri tek tek kapat — film/dizi listelerinde ve ana sayfada görünmez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (catalog.categories.isEmpty()) {
                    Text(
                        "Katalog senkronlanınca kategoriler burada listelenir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    catalog.categories.take(24).forEach { g ->
                        val hidden = settings.hiddenCategories.contains(g.title)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val newHidden = if (hidden) settings.hiddenCategories - g.title
                                    else settings.hiddenCategories + g.title
                                    vm.saveSettings(settings.copy(hiddenCategories = newHidden))
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                g.title,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = !hidden, onCheckedChange = {
                                val newHidden = if (it) settings.hiddenCategories - g.title
                                else settings.hiddenCategories + g.title
                                vm.saveSettings(settings.copy(hiddenCategories = newHidden))
                            })
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ---- Ana sayfa düzeni (anasayfa dili: cam pill seçenekler) ----
                Text("Ana Sayfa Düzeni", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("rows" to "Kartlar", "compact" to "Kompakt", "list" to "Liste").forEach { (key, label) ->
                        GlassChip(
                            selected = settings.homeLayout == key,
                            onClick = { vm.saveSettings(settings.copy(homeLayout = key)) },
                            label = label
                        )
                    }
                }

                // ---- Ana sayfa bölüm sırası ----
                Text(
                    "Ana Sayfa Bölüm Sırası",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                val order = remember(settings.homeSectionOrder) {
                    val custom = settings.homeSectionOrder
                    val known = HOME_SECTIONS.map { it.first }
                    val ordered = custom.filter { it in known } + known.filter { it !in custom }
                    ordered
                }
                order.forEachIndexed { idx, key ->
                    val label = HOME_SECTIONS.firstOrNull { it.first == key }?.second ?: key
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ListAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                        IconButton(
                            onClick = {
                                val newOrder = order.toMutableList()
                                if (idx > 0) {
                                    val tmp = newOrder[idx]; newOrder[idx] = newOrder[idx - 1]; newOrder[idx - 1] = tmp
                                    vm.saveSettings(settings.copy(homeSectionOrder = newOrder))
                                }
                            },
                            enabled = idx > 0
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Yukarı taşı", modifier = Modifier.size(18.dp)) }
                        IconButton(
                            onClick = {
                                val newOrder = order.toMutableList()
                                if (idx < order.size - 1) {
                                    val tmp = newOrder[idx]; newOrder[idx] = newOrder[idx + 1]; newOrder[idx + 1] = tmp
                                    vm.saveSettings(settings.copy(homeSectionOrder = newOrder))
                                }
                            },
                            enabled = idx < order.size - 1
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Aşağı taşı", modifier = Modifier.size(18.dp)) }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ---- EPG ----
                SectionHeader(Icons.Default.CalendarMonth, "EPG Rehberi")
                Text(
                    "Portal kendi EPG'sini vermiyorsa harici XMLTV linki kullanılır (xmltv_id eşleşmesi). " +
                        "Ör: sağlayıcının EPG linki ya da iptv-org/epg çıktısı (.xml / .xml.gz).",
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
                            vm.showMessage("EPG kaynağı kaydedildi")
                        },
                        enabled = epgUrl.trim().isNotEmpty()
                    ) { Text("Kaydet") }
                    OutlinedButton(onClick = {
                        vm.repository.clearEpgCache()
                        vm.showMessage("EPG önbelleği temizlendi")
                    }) { Text("Temizle") }
                }

                // ---- VOD senkron ----
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SectionHeader(Icons.Default.Refresh, "VOD Senkronizasyonu")
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
                    Button(
                        onClick = { if (appProfile != null) vm.syncVodCatalog(appProfile, force = true) },
                        enabled = appProfile != null
                    ) { Text("Şimdi Senkronize Et") }
                    OutlinedButton(onClick = { vm.resetVodCatalog() }) { Text("Kataloğu Sıfırla") }
                }
        }        // ================= KÜTÜPHANEM =================
        AccordionSection(
            icon = Icons.Default.Star,
            title = "Kütüphanem"
        ) {
            Text(
                "Sonra izle, izlediklerin, favorilerin ve özel listelerin tek ekranda.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onOpenLibrary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Kütüphanemi Aç")
            }
                Text("Özel Listelerim", style = MaterialTheme.typography.titleSmall)
                if (userLists.isEmpty()) {
                    Text(
                        "Henüz liste yok. Aşağıdan yeni bir liste oluştur; içerik detayından listeye ekleyebilirsin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    userLists.forEach { l ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "📁 ${l.name} (${l.itemIds.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { vm.deleteUserList(l.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Listeyi sil", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newListName,
                        onValueChange = { newListName = it.take(30) },
                        label = { Text("Yeni liste adı") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (newListName.trim().isNotBlank()) {
                                vm.addUserList(newListName.trim())
                                newListName = ""
                            }
                        },
                        enabled = newListName.trim().isNotBlank()
                    ) { Text("Oluştur") }
                }
        }

        // ================= OYNATICI =================
        AccordionSection(
            icon = Icons.Default.VolumeUp,
            title = "Oynatıcı"
        ) {

                ToggleRow(
                    icon = Icons.Default.PlayArrow,
                    title = "Otomatik Sonraki Bölüm",
                    desc = "Bölüm bitince sıradaki bölüm otomatik oynatılır (binge mod)",
                    checked = settings.bingeMode,
                    onCheckedChange = { vm.saveSettings(settings.copy(bingeMode = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Schedule,
                    title = "Picture-in-Picture",
                    desc = "Oynatıcıdan çıkınca küçük pencere devam eder",
                    checked = settings.pipEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(pipEnabled = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Speed,
                    title = "Oynatıcı Jestleri",
                    desc = "Kaydırarak parlaklık / ses / ileri-geri kontrolü",
                    checked = settings.gesturesEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(gesturesEnabled = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Info,
                    title = "Altyazılar",
                    desc = "Varsa altyazıları göster",
                    checked = settings.subtitlesEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(subtitlesEnabled = it)) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Varsayılan Video Kalitesi", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("auto" to "Otomatik", "1080p" to "1080p", "720p" to "720p", "480p" to "480p").forEach { (key, label) ->
                        GlassChip(
                            selected = settings.defaultQuality == key,
                            onClick = { vm.saveSettings(settings.copy(defaultQuality = key)) },
                            label = label
                        )
                    }
                }

                Text("Çözücü (Decoder)", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("auto" to "Otomatik", "hardware" to "Donanım", "software" to "Yazılım").forEach { (key, label) ->
                        GlassChip(
                            selected = settings.decoder == key,
                            onClick = { vm.saveSettings(settings.copy(decoder = key)) },
                            label = label
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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
                ) { Text("Cooldown'u Temizle") }

                Text("Zaman Dilimi Ofseti", style = MaterialTheme.typography.titleSmall)
                Text(
                    "EPG kaymalarını düzeltmek için sunucu ile kendi saatin arasındaki fark (+3, -2 vb.).",
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

        // ================= ENTEGRASYONLAR =================
        AccordionSection(
            icon = Icons.Default.Link,
            title = "Entegrasyonlar"
        ) {
                Text(
                    "TMDB (themoviedb.org) anahtarı: oyuncu fotoğrafları, fragman ve gerçek bölüm adları. " +
                        "Boşsa özellikler kapalıdır.",
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
                    enabled = tmdbKey.trim().isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Kaydet") }
        }

        // ================= HESAP =================
        AccordionSection(
            icon = Icons.Default.Person,
            title = "Hesap"
        ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(profile.avatar, style = MaterialTheme.typography.headlineSmall)
                    }
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            profile.name.ifBlank { "İzleyici" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            appProfile?.portal?.name ?: appProfile?.baseUrl ?: "Aktif kaynak yok",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showProfileEdit = true }) {
                        Text("Profili Düzenle")
                    }
                    OutlinedButton(
                        onClick = { showSwitch = true },
                        enabled = portals.isNotEmpty()                    ) { Text("Profil Değiştir") }
                }
        }

        // ================= GİZLİLİK =================
        AccordionSection(
            icon = Icons.Default.VerifiedUser,
            title = "Gizlilik & Güvenlik"
        ) {
                Text(
                    "Tüm verilerin (portallar, izleme geçmişi, listeler) yalnızca bu cihazda saklanır. " +
                        "Hiçbir veri uygulama dışına gönderilmez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { showPrivacy = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Gizlilik Anlaşması'nı Oku") }
        }

        // ================= HAKKINDA & DESTEK =================
        AccordionSection(
            icon = Icons.Default.Info,
            title = "Hakkında & Destek"
        ) {
                Text("Stalker Player v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Stalker portal, M3U ve Xtream Codes destekli IPTV oynatıcı.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { checkForUpdate() }, enabled = !checkingUpdate) {
                        Text(if (checkingUpdate) "Kontrol ediliyor…" else "Güncelleme Kontrol Et")
                    }
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/bayramburakx/stalkerapp")))
                        }
                    }) { Text("GitHub") }
                }
                if (updateMessage != null) {
                    Text(updateMessage.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
        }

        // Alt boşluk: içerik yüzen cam pill'in arkasından akıyor.
        Spacer(Modifier.height(96.dp))
    }

    // ---------- Dialog'lar ----------
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

    if (showProfileEdit) {
        ProfileEditDialog(
            initial = profile,
            onDismiss = { showProfileEdit = false },
            onSave = { p ->
                vm.saveUserProfile(p)
                showProfileEdit = false
            }
        )
    }

    if (showPrivacy) {
        PrivacyDialog(onDismiss = { showPrivacy = false })
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
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Ana ekranla aynı dil: ikonlu cam kutu + kalın büyük başlık. */
@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Uygulama kart diliyle uyumlu ayar kartı: yumuşak köşe + ince çerçeve. */
@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) { content() }
    }
}

/**
 * Tıklayınca açılıp kapanan ayar bölümü (akordeon). Başlık satırı her zaman
 * görünür; içerik yalnızca bölüm açıkken çizilir.
 */
@Composable
private fun AccordionSection(
    icon: ImageVector,
    title: String,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    SettingsCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Daralt" else "Genişlet",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun SourceGroupTitle(title: String, isActive: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (isActive) {
            Text("● Aktif", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
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

@Composable
private fun ProfileEditDialog(
    initial: UserProfile,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var avatar by remember { mutableStateOf(initial.avatar) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(UserProfile(name = name.trim().ifBlank { "İzleyici" }, avatar = avatar)) }) {
                Text("Kaydet")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
        title = { Text("Profili Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Adın") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val rows = AVATARS.chunked(4)
                rows.forEach { rowAvatars ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowAvatars.forEach { emoji ->
                            val selected = emoji == avatar
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        else Color.White.copy(alpha = 0.06f)
                                    )
                                    .clickable { avatar = emoji },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = MaterialTheme.typography.titleLarge.fontSize)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    )
}

@Composable
private fun PrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Anladım") } },
        title = { Text("Gizlilik Anlaşması") },
        text = {
            Text(
                "1. Tüm portal bilgileri, MAC adresi, izleme geçmişi, favoriler ve listeler yalnızca bu cihazda " +
                    "ve yalnızca uygulamanın kendi veri deposunda saklanır.\n\n" +
                    "2. Uygulama hiçbir veriyi üçüncü taraflarla paylaşmaz; analiz/izleme SDK'sı içermez.\n\n" +
                    "3. IPTV sağlayıcısına yapılan istekler doğrudan cihazdan, sağlayıcının kendi sunucusuna gider " +
                    "(uygulama aracılığıyla değil).\n\n" +
                    "4. TMDB anahtarını kendin eklersen, zenginleştirme istekleri doğrudan themoviedb.org'a gider.\n\n" +
                    "5. Uygulamayı kaldırdığında tüm veriler cihazdan silinir.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    )
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
        title = { Text("Portal Değiştir") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (portals.isEmpty()) {
                    Text("Kayıtlı portal yok.")
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
