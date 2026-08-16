package com.stalkerapp.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stalkerapp.BuildConfig
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Genre
import com.stalkerapp.data.M3uParser
import com.stalkerapp.data.M3uSource
import com.stalkerapp.data.Portal
import com.stalkerapp.data.UpdateChecker
import com.stalkerapp.data.UpdateInfo
import com.stalkerapp.data.UserProfile
import com.stalkerapp.data.VodItem
import com.stalkerapp.data.XtreamClient
import com.stalkerapp.data.XtreamSource
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.VodCatalogStatus
import com.stalkerapp.ui.components.GlassChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onOpenLibrary: () -> Unit = {},
    onBack: () -> Unit = {},
    onRestartSetup: () -> Unit = {}
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cooldown by vm.cooldownSeconds.collectAsStateWithLifecycle()
    val catalog by vm.vodCatalog.collectAsStateWithLifecycle()
    val profile by vm.userProfile.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val sourcesVersion by vm.sourcesVersion.collectAsStateWithLifecycle()
    val watchedVersion by vm.watchedVersion.collectAsStateWithLifecycle()
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
    var subtitleSize by remember(settings.subtitleSize) { mutableFloatStateOf(settings.subtitleSize.toFloat()) }

    var tmdbKey by remember(settings.tmdbApiKey) { mutableStateOf(settings.tmdbApiKey) }
    var epgUrl by remember(settings.epgUrl) { mutableStateOf(settings.epgUrl) }
    var prefAudioLang by remember(settings.preferredAudioLang) { mutableStateOf(settings.preferredAudioLang) }
    var prefSubtitleLang by remember(settings.preferredSubtitleLang) { mutableStateOf(settings.preferredSubtitleLang) }
    var updateDialog by remember { mutableStateOf<UpdateInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    // Dialog'lar
    var showPortalDialog by remember { mutableStateOf(false) }
    var editingPortal by remember { mutableStateOf<Portal?>(null) }
    var showProfiles by remember { mutableStateOf(false) }
    var showM3uDialog by remember { mutableStateOf(false) }
    var editingM3u by remember { mutableStateOf<M3uSource?>(null) }
    var showXtreamDialog by remember { mutableStateOf(false) }
    var editingXtream by remember { mutableStateOf<XtreamSource?>(null) }
    var showProfileEdit by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    // "Tüm Kaynakları Test Et" sonuçları.
    var testingAll by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf<List<Pair<String, String?>>?>(null) }
    // İzleme geçmişi / tüm veriler temizliği onay diyalogları.
    var showClearHistory by remember { mutableStateOf(false) }
    var showResetAll by remember { mutableStateOf(false) }
    var restoreMessage by remember { mutableStateOf<String?>(null) }
    // TMDB anahtar testi + görsel önbelleği.
    var tmdbTest by remember { mutableStateOf<String?>(null) }
    var testingTmdb by remember { mutableStateOf(false) }
    // Hakkında: lisans diyaloğu.
    var showLicense by remember { mutableStateOf(false) }
    // Gizlilik bölümündeki PIN alanı (mevcut PIN ile başlar).
    var pinNew by remember(settings.pin) { mutableStateOf(settings.pin) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val app = context.applicationContext as StalkerApp

    // Yedek geri yükleme: sistem dosya seçici ile JSON seçilir.
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val json = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                if (json.isNullOrBlank()) {
                    restoreMessage = "Yedek okunamadı"
                } else {
                    restoreMessage = if (vm.restoreBackup(json)) "Yedek geri yüklendi ✓" else "Yedek geçersiz"
                }
            }
        }
    }

    fun shareBackup() {
        val json = vm.backupJson()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Stalker Player yedeği")
            putExtra(Intent.EXTRA_TEXT, json)
        }
        runCatching { context.startActivity(Intent.createChooser(send, "Yedeği Paylaş")) }
    }

    // Sayfa gezinme: null = bölüm listesi, değer = açık bölüm sayfası.
    var currentSection by remember { mutableStateOf<String?>(null) }

    // PIN kilidi: ayarlara giriş. PIN boşsa kilit yoktur; ayarlanınca her
    // girişte sorulur (Gizlilik & Güvenlik bölümünden değiştirilir). Key'siz
    // remember: PIN'i bu ekranda ayarlayınca kullanıcı anında kilitlenmesin.
    var pinUnlocked by remember { mutableStateOf(settings.pin.isBlank()) }
    var pinError by remember { mutableStateOf(false) }
    var showPinReset by remember { mutableStateOf(false) }

    // Telefon geri tuşu: açık bölüm sayfasındaysa bölüm listesine, değilse ana sayfaya döner.
    BackHandler(enabled = true) {
        if (currentSection != null) currentSection = null else onBack()
    }

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

    // PIN girilmediyse ayarlar kilitli kalır (yalnızca kilit ekranı gösterilir).
    if (!pinUnlocked) {
        PinLockOverlay(
            modifier = modifier,
            error = pinError,
            onUnlock = { input ->
                if (input == settings.pin) {
                    pinUnlocked = true
                    pinError = false
                } else {
                    pinError = true
                }
            },
            onResetRequest = { showPinReset = true }
        )
        if (showPinReset) {
            AlertDialog(
                onDismissRequest = { showPinReset = false },
                confirmButton = {
                    TextButton(onClick = {
                        showPinReset = false
                        vm.clearAllData()
                        pinUnlocked = true
                    }) { Text("Evet, Sıfırla") }
                },
                dismissButton = { TextButton(onClick = { showPinReset = false }) { Text("Vazgeç") } },
                title = { Text("Tüm veriler sıfırlanacak") },
                text = {
                    Text("PIN'i unuttuysan tek seçenek tüm verileri silmek: portallar, ayarlar, izleme geçmişi ve katalog silinir. Devam edilsin mi?")
                }
            )
        }
        return
    }

    if (currentSection == null) {
        // ================= AYARLAR — BÖLÜM LİSTESİ =================
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Ayarlar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Bir bölüm seç:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SettingsNavRow(Icons.Default.Tv, "Playlist & Kaynaklar", "Stalker portal, M3U ve Xtream kaynakları") { currentSection = "playlist" }
            SettingsNavRow(Icons.Default.VideoLibrary, "Kütüphane & İçerik", "+18, gizlenen kategoriler, ana sayfa, VOD senkronu") { currentSection = "content" }
            SettingsNavRow(Icons.Default.Star, "Kütüphanem", "Favoriler, Sonra İzle, özel listeler") { currentSection = "library" }
            SettingsNavRow(Icons.Default.VolumeUp, "Oynatıcı", "Kalite, altyazı, çözücü, jestler") { currentSection = "player" }
            SettingsNavRow(Icons.Default.Link, "Entegrasyonlar", "TMDB ve harici servisler") { currentSection = "integrations" }
            SettingsNavRow(Icons.Default.Person, "Hesap", "Profil ve hesap ayarları") { currentSection = "account" }
            SettingsNavRow(Icons.Default.VerifiedUser, "Gizlilik & Güvenlik", "Gizlilik anlaşması") { currentSection = "privacy" }
            SettingsNavRow(Icons.Default.Info, "Hakkında & Destek", "Sürüm, güncelleme, destek") { currentSection = "about" }
            // Alt boşluk: içerik yüzen cam pill'in arkasından akıyor (scroll altı boş kalmasın).
            Spacer(Modifier.height(96.dp))
        }
        return
    }

    // ================= BÖLÜM SAYFASI =================
    SettingsPage(
        title = when (currentSection) {
            "playlist" -> "Playlist & Kaynaklar"
            "content" -> "Kütüphane & İçerik"
            "library" -> "Kütüphanem"
            "player" -> "Oynatıcı"
            "integrations" -> "Entegrasyonlar"
            "account" -> "Hesap"
            "privacy" -> "Gizlilik & Güvenlik"
            else -> "Hakkında & Destek"
        },
        onBack = { currentSection = null },
        modifier = modifier
    ) {
        when (currentSection) {
            "playlist" -> {
                // Kaynak başına istatistik: canlı kanal / film / dizi sayısı.
                data class SourceStats(val live: Int = 0, val movies: Int = 0, val series: Int = 0)
                val sourceStats = remember(sourcesVersion) { mutableStateMapOf<String, SourceStats>() }
                LaunchedEffect(sourcesVersion, m3uSources, xtreamSources) {
                    m3uSources.forEach { s ->
                        val live = runCatching { M3uParser.parse(s.content, s.id).size }.getOrDefault(0)
                        val (_, items) = runCatching { M3uParser.parseVod(s.content, s.id) }
                            .getOrDefault(emptyList<Genre>() to emptyList<VodItem>())
                        sourceStats[s.id] = SourceStats(
                            live = live,
                            movies = items.count { !it.isSeries },
                            series = items.count { it.isSeries }
                        )
                    }
                    xtreamSources.forEach { s ->
                        val live = runCatching { withContext(Dispatchers.IO) { vm.loadXtreamChannels(s) }.second.size }.getOrDefault(0)
                        // VOD istatistiği yalnızca AKTİF kaynak için çekilir (ağır
                        // istekler); pasif Xtream kaynaklarında kanal sayısı yeterli.
                        var movies = 0
                        var series = 0
                        if (activeKind == "xtream" && activeSourceId == s.id) {
                            val (_, items) = runCatching { withContext(Dispatchers.IO) { vm.loadXtreamVod(s) } }
                                .getOrDefault(emptyList<Genre>() to emptyList<VodItem>())
                            movies = items.count { !it.isSeries }
                            series = items.count { it.isSeries }
                        }
                        sourceStats[s.id] = SourceStats(live, movies, series)
                    }
                }
                Text(
                    "Stalker portal, M3U listesi ve Xtream Codes kaynaklarını buradan yönetirsin. " +
                        "Kapatılan kaynak türü Canlı TV, Filmler ve Dizilerde kullanılmaz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ---- Aktif kaynak özeti ----
                val activeSourceName = when (activeKind) {
                    "m3u" -> m3uSources.firstOrNull { it.id == activeSourceId }?.name
                        ?: "M3U listesi"
                    "xtream" -> xtreamSources.firstOrNull { it.id == activeSourceId }?.name
                        ?: "Xtream kaynağı"
                    else -> portals.firstOrNull { it.id == activeId }?.name
                        ?: appProfile?.portal?.name ?: "—"
                }
                val activeKindLabel = when (activeKind) {
                    "m3u" -> "M3U"
                    "xtream" -> "Xtream"
                    else -> "Stalker"
                }
                val activeStats = when (activeKind) {
                    "m3u" -> m3uSources.firstOrNull { it.id == activeSourceId }?.let { sourceStats[it.id] }
                    "xtream" -> xtreamSources.firstOrNull { it.id == activeSourceId }?.let { sourceStats[it.id] }
                    else -> null
                }
                SectionHeader(Icons.Default.Tv, "Aktif Kaynak")
                Text(
                    activeSourceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Tür: $activeKindLabel" + if (activeStats != null) {
                        "  •  Canlı: ${activeStats.live}  •  Film: ${activeStats.movies}  •  Dizi: ${activeStats.series}"
                    } else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Stalker dışı aktif kaynakta katalog boşsa yeniden çekme kısayolu.
                if (activeKind == "m3u" || activeKind == "xtream") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { scope.launch { vm.ensureExternalVodCatalog(force = true) } }
                        ) { Text("Film & Dizileri Yenile") }
                        OutlinedButton(
                            onClick = {
                                scope.launch { vm.loadChannelsForActiveSource(appProfile) }
                                vm.showMessage("Kanal listesi yenileniyor")
                            }
                        ) { Text("Kanal Listesini Yenile") }
                    }
                }

                // ---- Hızlı kaynak değiştirici ----
                if (portals.isNotEmpty() || m3uSources.isNotEmpty() || xtreamSources.isNotEmpty()) {
                    Text("Hızlı Kaynak Değiştir", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        portals.forEach { p ->
                            val sel = activeKind == "stalker" && p.id == activeId
                            GlassChip(
                                selected = sel,
                                onClick = {
                                    vm.setActiveSource("stalker", null)
                                    vm.launchSwitch(p) { onPortalsChanged() }
                                },
                                label = p.name.ifBlank { p.url }
                            )
                        }
                        m3uSources.forEach { s ->
                            val sel = activeKind == "m3u" && activeSourceId == s.id
                            GlassChip(
                                selected = sel,
                                onClick = { vm.setActiveSource("m3u", s.id) },
                                label = "M3U • ${s.name.ifBlank { s.url }}"
                            )
                        }
                        xtreamSources.forEach { s ->
                            val sel = activeKind == "xtream" && activeSourceId == s.id
                            GlassChip(
                                selected = sel,
                                onClick = { vm.setActiveSource("xtream", s.id) },
                                label = "XT • ${s.name.ifBlank { s.server }}"
                            )
                        }
                    }
                }

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
                                },
                                onTest = { vm.testPortal(p) }
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
                            val st = sourceStats[s.id]
                            SourceRow(
                                name = s.name.ifBlank { s.url },
                                subtitle = buildString {
                                    append(s.url)
                                    st?.let {
                                        append("  •  ${it.live} kanal")
                                        if (it.movies > 0) append("  •  ${it.movies} film")
                                        if (it.series > 0) append("  •  ${it.series} dizi")
                                    }
                                },
                                isActive = activeKind == "m3u" && activeSourceId == s.id,
                                onActivate = { vm.setActiveSource("m3u", s.id) },
                                onEdit = { editingM3u = s; showM3uDialog = true },
                                onDelete = { vm.deleteM3uSource(s.id) },
                                onTest = { vm.testM3u(s) },
                                onRefresh = { vm.refreshM3u(s) }
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
                            val st = sourceStats[s.id]
                            SourceRow(
                                name = s.name.ifBlank { s.server },
                                subtitle = buildString {
                                    append(s.server)
                                    append("  •  ${s.username}")
                                    st?.let {
                                        append("  •  ${it.live} kanal")
                                        if (it.movies > 0) append("  •  ${it.movies} film")
                                        if (it.series > 0) append("  •  ${it.series} dizi")
                                    }
                                },
                                isActive = activeKind == "xtream" && activeSourceId == s.id,
                                onActivate = { vm.setActiveSource("xtream", s.id) },
                                onEdit = { editingXtream = s; showXtreamDialog = true },
                                onDelete = { vm.deleteXtreamSource(s.id) },
                                onTest = { vm.testXtream(s) },
                                onRefresh = { vm.refreshXtream(s) }
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                // ---- Toplu kaynak testi ----
                Button(
                    onClick = {
                        scope.launch {
                            testingAll = true
                            testResults = null
                            val results = mutableListOf<Pair<String, String?>>()
                            portals.forEach { p -> results += (p.name.ifBlank { p.url }) to vm.testPortal(p) }
                            m3uSources.forEach { s -> results += (s.name.ifBlank { s.url }) to vm.testM3u(s) }
                            xtreamSources.forEach { s -> results += (s.name.ifBlank { s.server }) to vm.testXtream(s) }
                            testResults = results
                            testingAll = false
                        }
                    },
                    enabled = !testingAll &&
                        (portals.isNotEmpty() || m3uSources.isNotEmpty() || xtreamSources.isNotEmpty()),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (testingAll) "Test ediliyor…" else "Tüm Kaynakları Test Et") }
                testResults?.forEach { (name, err) ->
                    Text(
                        if (err == null) "✓ $name — bağlantı başarılı" else "✗ $name — $err",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (err == null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    )
                }
            }
            "content" -> {

                ToggleRow(
                    icon = Icons.Default.Lock,
                    title = "+18 İçerikler",
                    desc = "Yetişkin içerikli kategorileri göster",
                    checked = settings.adultContentEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(adultContentEnabled = it)) }
                )

                // ---- Gizlenen canlı TV grupları ----
                Text(
                    "Gizlenecek Canlı TV Grupları",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    "İstemediğin kanal gruplarını tek tek kapat — Canlı TV listesinde ve ana sayfada görünmez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                var channelGroups by remember { mutableStateOf<List<Genre>?>(null) }
                LaunchedEffect(appProfile, activeKind, activeSourceId) {
                    channelGroups = runCatching { vm.loadChannelsForActiveSource(appProfile)?.first }.getOrNull()
                }
                val groups = channelGroups
                if (groups == null) {
                    Text(
                        "Kanal grupları yükleniyor…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val visibleGroups = groups.filter { it.id != 0L }
                    if (visibleGroups.isEmpty()) {
                        Text(
                            "Aktif kaynakta grup bulunamadı. Canlı TV'ye kaynak eklerseniz gruplar burada listelenir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        visibleGroups.forEach { g ->
                            val hidden = settings.hiddenChannelGroups.contains(g.title)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        val newHidden = if (hidden) settings.hiddenChannelGroups - g.title
                                        else settings.hiddenChannelGroups + g.title
                                        vm.saveSettings(settings.copy(hiddenChannelGroups = newHidden))
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
                                    val newHidden = if (it) settings.hiddenChannelGroups - g.title
                                    else settings.hiddenChannelGroups + g.title
                                    vm.saveSettings(settings.copy(hiddenChannelGroups = newHidden))
                                })
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ---- Ana sayfadan gizlenenler (geri göster) ----
                if (settings.hiddenFromHome.isNotEmpty()) {
                    SectionHeader(Icons.Default.Home, "Ana Sayfadan Gizlenenler")
                    Text(
                        "Uzun bas → \"Ana Sayfadan Kaldır\" ile gizlenen medya buradan geri getirilebilir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    settings.hiddenFromHome.mapNotNull { id -> catalog.byId[id] }.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                vm.saveSettings(
                                    settings.copy(hiddenFromHome = settings.hiddenFromHome - item.id)
                                )
                                vm.showMessage("Ana sayfada tekrar görünüyor")
                            }) { Text("Geri Göster") }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ---- İzleme geçmişi ----
                SectionHeader(Icons.Default.Schedule, "İzleme Geçmişi")
                Text(
                    "Film/bölüm ilerlemeleri ve izlendi işaretleri silinir. Ana sayfadaki Son İzlenenler / İzlemeye Devam bölümleri boşalır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { showClearHistory = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("İzleme Geçmişini Temizle") }
                if (showClearHistory) {
                    AlertDialog(
                        onDismissRequest = { showClearHistory = false },
                        confirmButton = {
                            TextButton(onClick = {
                                showClearHistory = false
                                vm.clearWatchHistory()
                                vm.showMessage("İzleme geçmişi temizlendi")
                            }) { Text("Temizle") }
                        },
                        dismissButton = { TextButton(onClick = { showClearHistory = false }) { Text("Vazgeç") } },
                        title = { Text("İzleme geçmişi temizlensin mi?") },
                        text = { Text("Tüm ilerlemeler ve izlendi işaretleri silinir. Bu işlem geri alınamaz.") }
                    )
                }
                // ---- Gizlenen VOD kategorileri (tek tek aç/kapat) ----
                Text("Gizlenen Kategoriler", style = MaterialTheme.typography.titleSmall)
                Text(
                    "İstemediğin kategorileri tek tek kapat — Filmler/Diziler listelerinde ve ana sayfada görünmez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val allCats = catalog.categories.filter { it.id != 0L }
                val hiddenCatSet = remember(settings.hiddenCategories) { settings.hiddenCategories.toSet() }
                if (allCats.isEmpty()) {
                    Text(
                        "Katalog yüklenince kategoriler burada listelenir (VOD senkronu bekleniyor).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    allCats.forEach { c ->
                        val hidden = c.title in hiddenCatSet
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val newHidden = if (hidden) settings.hiddenCategories - c.title
                                    else settings.hiddenCategories + c.title
                                    vm.saveSettings(settings.copy(hiddenCategories = newHidden))
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                c.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = !hidden, onCheckedChange = {
                                val newHidden = if (it) settings.hiddenCategories - c.title
                                else settings.hiddenCategories + c.title
                                vm.saveSettings(settings.copy(hiddenCategories = newHidden))
                            })
                        }
                    }
                    if (hiddenCatSet.isNotEmpty()) {
                        TextButton(onClick = {
                            vm.saveSettings(settings.copy(hiddenCategories = emptyList()))
                            vm.showMessage("Gizlenen kategoriler tekrar gösteriliyor")
                        }) { Text("Hepsini Göster (${hiddenCatSet.size})") }
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

                // ---- Ana sayfa görünümü: hero + bölüm boyutu + açılış sekmesi ----
                ToggleRow(
                    icon = Icons.Default.Home,
                    title = "Hero Tanıtım Banner",
                    desc = "Ana sayfanın üstündeki büyük kaydırmalı tanıtım",
                    checked = settings.heroEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(heroEnabled = it)) }
                )
                Text("Bölüm Başına Öğe Sayısı", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Popüler Filmler/Diziler ve favori bölümlerinde gösterilecek öğe sayısı.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10 to "10", 20 to "20", 30 to "30").forEach { (key, label) ->
                        GlassChip(
                            selected = settings.homeSectionSize == key,
                            onClick = { vm.saveSettings(settings.copy(homeSectionSize = key)) },
                            label = label
                        )
                    }
                }
                Text("Açılış Sekmesi", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Uygulama açıldığında hangi sekme gösterilsin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Ana Sayfa", 1 to "Canlı TV", 2 to "Filmler", 3 to "Diziler").forEach { (key, label) ->
                        GlassChip(
                            selected = settings.defaultTab == key,
                            onClick = { vm.saveSettings(settings.copy(defaultTab = key)) },
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
                // Hazır EPG kaynakları: tek dokunuşla seç, sonra Kaydet.
                val trEpg = "https://epgshare01.online/epgshare01/epg_ripper_TR1.xml.gz"
                // epg.pw'nin tam "All" dosyası yüzlerce MB'dir ve telefonlarda pratik
                // değildir — çip, çalışan küçük "Lite" sürümüne işaret eder.
                val allEpg = "https://epg.pw/xmltv/epg_lite.xml.gz"
                Text(
                    "Önerilen: Türkiye EPG'si küçük (166KB) ve hızlıdır. epg.pw Lite dünya kanallarını içerir (1.7MB). İndirilen EPG cihaza kaydedilir, bir daha indirilmez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(
                        selected = epgUrl.trim() == trEpg,
                        onClick = { epgUrl = trEpg },
                        label = "Türkiye (önerilen)"
                    )
                    GlassChip(
                        selected = epgUrl.trim() == allEpg,
                        onClick = { epgUrl = allEpg },
                        label = "Tüm dünya (epg.pw Lite)"
                    )
                }
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
                Text("Zaman Dilimi (hızlı seçim)", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 1, 2, 3, -3).forEach { off ->
                        GlassChip(
                            selected = settings.timezoneOffset == off,
                            onClick = { vm.saveSettings(settings.copy(timezoneOffset = off)) },
                            label = when (off) {
                                3 -> "+3 (TR)"
                                0 -> "0 (GMT)"
                                else -> if (off > 0) "+$off" else "$off"
                            }
                        )
                    }
                }

                // ---- VOD senkron ----
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SectionHeader(Icons.Default.Refresh, "VOD Senkronizasyonu")
                ToggleRow(
                    icon = Icons.Default.Refresh,
                    title = "Otomatik Senkron",
                    desc = "Uygulama açılışında katalog arka planda otomatik senkronlanır",
                    checked = settings.autoSyncVod,
                    onCheckedChange = { vm.saveSettings(settings.copy(autoSyncVod = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Wifi,
                    title = "Yalnızca Wi-Fi'da Senkronla",
                    desc = "Mobil veride büyük katalog indirilmez (manuel \"Şimdi Senkronize Et\" yine çalışır)",
                    checked = settings.wifiOnlySync,
                    onCheckedChange = { vm.saveSettings(settings.copy(wifiOnlySync = it)) }
                )
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
            }
            "library" -> {
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
            "player" -> {

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
                ToggleRow(
                    icon = Icons.Default.PlayArrow,
                    title = "Kaldığın Yerden Devam Et",
                    desc = "Film ve bölümler kaldığı konumdan başlar",
                    checked = settings.resumePlayback,
                    onCheckedChange = { vm.saveSettings(settings.copy(resumePlayback = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Refresh,
                    title = "Canlı TV Otomatik Yeniden Bağlan",
                    desc = "Akış kesilince en fazla 3 kez otomatik yeniden dener",
                    checked = settings.autoRetryLive,
                    onCheckedChange = { vm.saveSettings(settings.copy(autoRetryLive = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Speed,
                    title = "Ekranı Açık Tut",
                    desc = "Oynatıcı açıkken ekran uyumaz",
                    checked = settings.keepScreenOn,
                    onCheckedChange = { vm.saveSettings(settings.copy(keepScreenOn = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Forward10,
                    title = "Kanal Ön Yükleme (Zapping)",
                    desc = "Sıradaki kanal önceden tamponlanır — kanal değiştirme hızlanır",
                    checked = settings.zappingPrefetch,
                    onCheckedChange = { vm.saveSettings(settings.copy(zappingPrefetch = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.PlayArrow,
                    title = "Arka Planda Oynatmaya Devam Et",
                    desc = "PiP dışında uygulama arka plana geçince oynatma duraklatılır",
                    checked = settings.backgroundPlayback,
                    onCheckedChange = { vm.saveSettings(settings.copy(backgroundPlayback = it)) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ---- Varsayılan ses/altyazı dili (ISO kodu) ----
                Text("Varsayılan Ses Dili", style = MaterialTheme.typography.titleSmall)
                Text(
                    "ISO kodu (ör. tr, en, de). Boş bırakılırsa otomatik seçilir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = prefAudioLang,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isLetter() || c == '-' }) prefAudioLang = it },
                    label = { Text("Ses dili kodu (örn. tr)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        vm.saveSettings(settings.copy(preferredAudioLang = prefAudioLang.trim().lowercase()))
                        vm.showMessage("Varsayılan ses dili kaydedildi")
                    },
                    enabled = prefAudioLang != settings.preferredAudioLang,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Ses Dili Kaydet") }

                Text("Varsayılan Altyazı Dili", style = MaterialTheme.typography.titleSmall)
                Text(
                    "ISO kodu (ör. tr, en). Boş bırakılırsa otomatik seçilir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = prefSubtitleLang,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isLetter() || c == '-' }) prefSubtitleLang = it },
                    label = { Text("Altyazı dili kodu (örn. tr)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        vm.saveSettings(settings.copy(preferredSubtitleLang = prefSubtitleLang.trim().lowercase()))
                        vm.showMessage("Varsayılan altyazı dili kaydedildi")
                    },
                    enabled = prefSubtitleLang != settings.preferredSubtitleLang,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Altyazı Dili Kaydet") }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ---- Oynatıcı yönü / kontrol süreleri ----
                Text("Oynatıcı Yönü", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "auto" to "Otomatik",
                        "landscape" to "Sabit Yatay",
                        "sensor" to "Serbest"
                    ).forEach { (key, label) ->
                        GlassChip(
                            selected = settings.playerOrientation == key,
                            onClick = { vm.saveSettings(settings.copy(playerOrientation = key)) },
                            label = label
                        )
                    }
                }
                Text("Kontrol Gizleme Süresi", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3 to "3 sn", 5 to "5 sn", 10 to "10 sn").forEach { (key, label) ->
                        GlassChip(
                            selected = settings.controlsTimeoutSec == key,
                            onClick = { vm.saveSettings(settings.copy(controlsTimeoutSec = key)) },
                            label = label
                        )
                    }
                }
                Text("Çift Dokunma Atlama", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Sol/sağ yarıya çift dokununca ileri-geri atlama miktarı.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10 to "10 sn", 20 to "20 sn", 30 to "30 sn").forEach { (key, label) ->
                        GlassChip(
                            selected = settings.doubleTapSeekSec == key,
                            onClick = { vm.saveSettings(settings.copy(doubleTapSeekSec = key)) },
                            label = label
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Varsayılan Oynatma Hızı", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Her oynatmada bu hız uygulanır (oynatıcı içinden de değiştirilebilir).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.75f to "0.75×", 1f to "1×", 1.25f to "1.25×", 1.5f to "1.5×", 2f to "2×").forEach { (key, label) ->
                        GlassChip(
                            selected = settings.playbackSpeed == key,
                            onClick = { vm.saveSettings(settings.copy(playbackSpeed = key)) },
                            label = label
                        )
                    }
                }

                SliderSetting(
                    icon = Icons.Default.Info,
                    title = "Altyazı Boyutu",
                    description = "Oynatıcıdaki altyazı yazı boyutu (varsayılan 16).",
                    value = subtitleSize,
                    valueRange = 10f..32f,
                    steps = 21,
                    valueText = "${subtitleSize.toInt()}",
                    onChange = {
                        subtitleSize = it
                        vm.saveSettings(settings.copy(subtitleSize = it.toInt()))
                    }
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
            "integrations" -> {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                testingTmdb = true
                                tmdbTest = null
                                val ok = app.tmdb.testKey(tmdbKey.trim())
                                tmdbTest = if (ok) "✓ Anahtar geçerli" else "✗ Geçersiz anahtar"
                                testingTmdb = false
                            }
                        },
                        enabled = tmdbKey.trim().isNotEmpty() && !testingTmdb
                    ) { Text(if (testingTmdb) "Test ediliyor…" else "Anahtarı Test Et") }
                    OutlinedButton(onClick = {
                        app.tmdb.clearCache()
                        runCatching { coil.Coil.imageLoader(context).memoryCache?.clear() }
                        runCatching { coil.Coil.imageLoader(context).diskCache?.clear() }
                        vm.showMessage("Görsel ve TMDB önbelleği temizlendi")
                    }) { Text("Önbelleği Temizle") }
                }
                if (tmdbTest != null) {
                    Text(
                        tmdbTest.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (tmdbTest?.startsWith("✓") == true) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("TMDB Dili", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Başlık, fragman ve oyuncu bilgilerinin dili (posterler etkilenmez).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("tr" to "Türkçe", "en" to "English").forEach { (key, label) ->
                        GlassChip(
                            selected = settings.tmdbLanguage == key,
                            onClick = { vm.saveSettings(settings.copy(tmdbLanguage = key)) },
                            label = label
                        )
                    }
                }
                ToggleRow(
                    icon = Icons.Default.PlayArrow,
                    title = "Fragmanlar",
                    desc = "Detay ekranında TMDB fragmanlarını göster",
                    checked = settings.tmdbTrailers,
                    onCheckedChange = { vm.saveSettings(settings.copy(tmdbTrailers = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Person,
                    title = "Oyuncu Fotoğrafları",
                    desc = "Detay ekranında TMDB oyuncu görsellerini kullan",
                    checked = settings.tmdbPeople,
                    onCheckedChange = { vm.saveSettings(settings.copy(tmdbPeople = it)) }
                )
            }
            "account" -> {
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
                    OutlinedButton(onClick = { showProfiles = true }) {
                        Text("Profiller (${profiles.size})")
                    }
                }
                OutlinedButton(
                    onClick = onRestartSetup,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Kurulumu Yeniden Aç") }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SectionHeader(Icons.Default.Star, "İstatistikler")
                val stats = remember(watchedVersion, settings) {
                    val prog = vm.store.loadVodProgress()
                    val filmsWatched = prog.values.count { it.durationMs > 0 && it.positionMs >= it.durationMs * 0.85 }
                    val epsWatched = vm.store.watchedEpisodes().size
                    val favVods = vm.store.favoriteVods().size
                    val favCh = vm.store.favoriteChannels().size
                    val totalMs = prog.values.sumOf { it.positionMs }
                    Triple(filmsWatched + epsWatched, favVods + favCh, totalMs / 3600_000.0)
                }
                Text(
                    "✓ İzlenen: ${stats.first} (film + bölüm)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "★ Favoriler: ${stats.second} (film + kanal)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "⏱ Toplam izleme: ${("%.1f").format(stats.third)} saat",
                    style = MaterialTheme.typography.bodyMedium
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SectionHeader(Icons.Default.Refresh, "Yedekleme & Veri")
                Text(
                    "Tüm veriler (kaynaklar, ayarlar, favoriler, izleme geçmişi, listeler) tek JSON olarak dışa aktarılır. Telefon değiştirirken yedeği geri yükleyebilirsin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { shareBackup() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Yedeği Dışa Aktar (Paylaş)") }
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf("text/*", "application/json")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Yedekten Geri Yükle") }
                if (restoreMessage != null) {
                    Text(
                        restoreMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(
                    onClick = { showResetAll = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Tüm Verileri Sıfırla", color = MaterialTheme.colorScheme.error) }
                if (showResetAll) {
                    AlertDialog(
                        onDismissRequest = { showResetAll = false },
                        confirmButton = {
                            TextButton(onClick = {
                                showResetAll = false
                                vm.clearAllData()
                                vm.showMessage("Tüm veriler silindi")
                            }) { Text("Evet, Sil", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = { TextButton(onClick = { showResetAll = false }) { Text("Vazgeç") } },
                        title = { Text("Tüm veriler silinecek") },
                        text = { Text("Kaynaklar, ayarlar, favoriler, izleme geçmişi ve katalog kalıcı olarak silinir. Bu işlem geri alınamaz.") }
                    )
                }
            }
            "privacy" -> {
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SectionHeader(Icons.Default.Lock, "PIN Kilidi")
                Text(
                    "Ayarlara giriş için 4 haneli PIN istenir. PIN boş bırakılırsa kilit kaldırılır. PIN unutulursa tek çözüm tüm verileri sıfırlamaktır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pinNew,
                    onValueChange = { if (it.length == 4 && it.all(Char::isDigit)) pinNew = it },
                    label = { Text("4 haneli PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        vm.saveSettings(settings.copy(pin = pinNew))
                        vm.showMessage(if (pinNew.isBlank()) "PIN kilidi kaldırıldı" else "PIN kaydedildi")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("PIN'i Kaydet") }
            }
            "about" -> {
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    "Sürüm ${BuildConfig.VERSION_NAME} (kod ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { showLicense = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Lisans & Açık Kaynak") }
                if (showLicense) {
                    AlertDialog(
                        onDismissRequest = { showLicense = false },
                        confirmButton = { TextButton(onClick = { showLicense = false }) { Text("Kapat") } },
                        title = { Text("Lisans") },
                        text = {
                            Text(
                                "Bu uygulama kişisel kullanım için geliştirilmiştir. " +
                                    "Stalker portal, M3U ve Xtream Codes destekli bir IPTV oynatıcıdır. " +
                                    "Hiçbir içerik uygulama tarafından barındırılmaz; yalnızca kullanıcının " +
                                    "eklediği kaynaklar oynatılır. Tüm veriler cihazda saklanır.\n\n" +
                                    "Açık kaynak bileşenler: ExoPlayer (media3), Coil, OkHttp, kotlinx.serialization."
                            )
                        }
                    )
                }
            }
        }
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
                    // İlk portal ekleniyor: M3U/Xtream aktifken bile uygulama yeni
                    // portala geçmeli (kaynak türü Stalker yapılır, sonra bağlanılır).
                    vm.setActiveSource("stalker", null)
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

    if (showProfiles) {
        MultiProfileDialog(
            profiles = profiles,
            activeId = profile.id.ifBlank { com.stalkerapp.data.Store.DEFAULT_PROFILE_ID },
            onDismiss = { showProfiles = false },
            onSwitch = { id ->
                showProfiles = false
                vm.switchProfile(id)
            },
            onAdd = { n, a ->
                showProfiles = false
                vm.addProfile(n, a)
            },
            onDelete = { id -> vm.deleteProfile(id) }
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
 * Ayarlar bölüm listesindeki satır: ikonlu cam kutu + başlık + açıklama + sağ ok.
 * Tıklayınca ilgili bölüm sayfası açılır.
 */
@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    SettingsCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** Bölüm sayfası kabuğu: geri oku + başlık + kaydırılabilir içerik kartı. */
@Composable
private fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        // Üst ekran içi boşluk (status bar) dahil dış modifier uygulanır;
        // aksi halde başlık/geri tuşu bildirim paneliyle iç içe kalır.
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        SettingsCard(modifier = Modifier.fillMaxWidth()) {
            content()
        }
        Spacer(modifier = Modifier.height(96.dp))
    }
}

/** PIN kilit ekranı: ayarlara giriş yalnızca doğru PIN ile açılır. */
@Composable
private fun PinLockOverlay(
    modifier: Modifier = Modifier,
    error: Boolean,
    onUnlock: (String) -> Unit,
    onResetRequest: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("PIN Kilidi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Ayarlara erişmek için PIN'i gir.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
            label = { Text("4 haneli PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = error,
            supportingText = if (error) {
                { Text("Yanlış PIN", color = MaterialTheme.colorScheme.error) }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onUnlock(pin) },
            enabled = pin.length == 4,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Kilidi Aç") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onResetRequest) {
            Text("PIN'i unuttum — tüm verileri sıfırla", color = MaterialTheme.colorScheme.error)
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
    onDelete: () -> Unit,
    onTest: (suspend () -> String?)? = null,
    onRefresh: (suspend () -> Unit)? = null
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
        // Test durumu: başarılı/başarısız göstergesi (Test butonundan sonra dolar).
        var testState by remember { mutableStateOf<String?>(null) } // null=boş, "ok", hata mesajı
        var testing by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        if (testing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text("Test ediliyor…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (testState != null) {
            val ok = testState == "ok"
            Text(
                if (ok) "✓ Bağlantı başarılı" else "✗ $testState",
                style = MaterialTheme.typography.labelSmall,
                color = if (ok) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            )
        }
        // 1. satır: etkinleştir + yenile (varsa).
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isActive) {
                OutlinedButton(onClick = onActivate, modifier = Modifier.weight(1f)) {
                    Text("Aktif Yap")
                }
            }
            if (onRefresh != null) {
                var refreshing by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            refreshing = true
                            onRefresh()
                            refreshing = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !refreshing
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (refreshing) "…" else "Yenile")
                }
            }
        }
        // 2. satır: test + düzenle + sil.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onTest != null) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            testing = true
                            testState = null
                            val err = onTest()
                            testState = err ?: "ok"
                            testing = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !testing
                ) { Text(if (testing) "…" else "Test Et") }
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
            TextButton(onClick = { onSave(UserProfile(id = initial.id, name = name.trim().ifBlank { "İzleyici" }, avatar = avatar)) }) {
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
private fun MultiProfileDialog(
    profiles: List<UserProfile>,
    activeId: String,
    onDismiss: () -> Unit,
    onSwitch: (String) -> Unit,
    onAdd: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newAvatar by remember { mutableStateOf(AVATARS.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
        title = { Text("Profiller") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profiles.forEach { p ->
                    val isActive = p.id == activeId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .clickable { if (!isActive) onSwitch(p.id) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(p.avatar, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name.ifBlank { "İzleyici" }, style = MaterialTheme.typography.bodyLarge)
                            if (isActive) {
                                Text("Aktif", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (profiles.size > 1) {
                            IconButton(onClick = { onDelete(p.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Profili sil",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
                if (adding) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(24) },
                        label = { Text("Yeni profil adı") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        AVATARS.take(8).forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (emoji == newAvatar) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        else Color.White.copy(alpha = 0.08f)
                                    )
                                    .clickable { newAvatar = emoji },
                                contentAlignment = Alignment.Center
                            ) { Text(emoji) }
                        }
                    }
                    Button(
                        onClick = {
                            onAdd(newName.trim().ifBlank { "İzleyici" }, newAvatar)
                            adding = false
                            newName = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Profili Ekle") }
                } else {
                    TextButton(onClick = { adding = true }) { Text("+ Yeni Profil") }
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
    var content by remember { mutableStateOf(initial?.content ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val trimmed = url.trim()
                if (!trimmed.startsWith("http")) { error = "Geçerli bir http(s) URL girin"; return@TextButton }
                val id = initial?.id ?: ("m3u_" + trimmed.hashCode().toString() + System.currentTimeMillis().toString().takeLast(4))
                onSave(
                    M3uSource(
                        id = id,
                        name = name.ifBlank { trimmed },
                        url = trimmed,
                        content = content.trim()
                    )
                )
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
        title = { Text(if (initial == null) "M3U Ekle" else "M3U Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "M3U listesinin adresini girin (#EXTM3U içeren dosya). Kanal kategorileri group-title'dan; " +
                        "\"dizi/series\" grubu Diziler, diğerleri Filmler sekmesinde görünür. İstersen listeyi " +
                        "doğrudan İçerik alanına da yapıştırabilirsin (URL boşsa bu içerik kullanılır).",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("İsim") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("M3U URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("İçerik (isteğe bağlı — #EXTM3U metni)") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
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
