package com.stalkerapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stalkerapp.StalkerApp
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.components.AppleTvButton
import com.stalkerapp.ui.components.AppleTvButtonStyle
import com.stalkerapp.ui.components.AppleTvTokens
import com.stalkerapp.ui.components.GlassSurface
import com.stalkerapp.ui.rememberMainViewModel
import com.stalkerapp.ui.library.LibraryScreen
import com.stalkerapp.ui.live.LiveTvScreen
import com.stalkerapp.ui.settings.SettingsScreen
import com.stalkerapp.ui.vod.VodScreen

private val L10nLocal: Map<String, String> = mapOf(
    // Türkçe -> English
    "Yetişkin İçerik Kilitli" to "Adult Content Locked",
    "Bu içeriği görmek için PIN gerekli (Gizlilik & Güvenlik'te ayarlanır)." to "A PIN is required to view this content (set in Privacy & Security).",
    "PIN" to "PIN",
    "Yanlış PIN" to "Wrong PIN",
    "Aç" to "Open",
    "Vazgeç" to "Cancel"
)
private fun str(lang: String, text: String): String =
    if (lang == "en") L10nLocal[text] ?: text else text

private data class NavItem(val icon: ImageVector, val label: String, val onClick: (() -> Unit)? = null)

@Composable
private fun AdultPinDialog(
    lang: String,
    onUnlock: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppleTvTokens.SurfaceRaised,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(str(lang, "Yetişkin İçerik Kilitli")) },
        text = {
            Column {
                Text(
                    str(lang, "Bu içeriği görmek için PIN gerekli (Gizlilik & Güvenlik'te ayarlanır)."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.take(8); error = false },
                    label = { Text(str(lang, "PIN")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) {
                    Text(
                        str(lang, "Yanlış PIN"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            AppleTvButton(
                onClick = { if (!onUnlock(pin)) error = true },
                enabled = pin.isNotBlank(),
                style = AppleTvButtonStyle.Primary
            ) {
                Text(
                    str(lang, "Aç"),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        },
        dismissButton = {
            AppleTvButton(onClick = onDismiss, style = AppleTvButtonStyle.Glass) {
                Text(
                    str(lang, "Vazgeç"),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }
    )
}

@Composable
private fun AppleNavItem(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val active = selected || isFocused
    Surface(
        shape = RoundedCornerShape(40),
        color = if (active) Color.White.copy(alpha = 0.16f) else Color.Transparent,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(40))
            .shadow(
                elevation = if (active) 14.dp else 0.dp,
                shape = RoundedCornerShape(40),
                spotColor = AppleTvTokens.FocusGlow.copy(alpha = 0.45f),
                ambientColor = AppleTvTokens.FocusGlow.copy(alpha = 0.30f)
            )
            .border(
                width = if (active) 1.5.dp else 0.dp,
                color = if (active) AppleTvTokens.FocusBorder else Color.Transparent,
                shape = RoundedCornerShape(40)
            )
            .onFocusChanged { fs: FocusState -> isFocused = fs.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(9.dp).size(22.dp)
        )
    }
}

@Composable
fun HomeScreen(
    onOpenPlayer: () -> Unit,
    onOpenVod: (Long, Boolean) -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onOpenOnboarding: () -> Unit = {},
    onOpenProfiles: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    var profile by remember { mutableStateOf(vm.repository.cachedProfile()) }
    val lang = vm.store.settings().language

    // Açılış sekmesi Kütüphane & İçerik ayarından değiştirilebilir
    // (0=Ana Sayfa, 1=Canlı TV, 2=Filmler, 3=Diziler).
    var tab by remember {
        mutableIntStateOf(vm.store.settings().defaultTab.coerceIn(0, 3))
    }
    val gotoTab: (Int) -> Unit = { tab = it }

    // "Açılışta son kanalı oynat" ayarı: uygulama açılınca son izlenen canlı kanal
    // otomatik başlatılır (kaynak hâlâ aktifse; hata/sessiz durumda atlanır).
    LaunchedEffect(Unit) {
        vm.resumeLastLiveChannelIfEnabled(profile)
    }

    // Yetişkin içerik PIN kilidi: ayar açıksa ve oturumda PIN girilmediyse
    // uygulama genelinde PIN sorulur (içerik listeleri kilitli kalır).
    val settings by vm.settings.collectAsStateWithLifecycle()
    val adultUnlocked by vm.adultUnlocked.collectAsStateWithLifecycle()
    var showAdultPin by remember { mutableStateOf(false) }
    LaunchedEffect(settings.adultContentEnabled, settings.lockAdultWithPin, adultUnlocked) {
        if (settings.adultContentEnabled && settings.lockAdultWithPin && !adultUnlocked) {
            showAdultPin = true
        }
    }
    if (showAdultPin) {
        AdultPinDialog(
            lang = lang,
            onUnlock = { pin ->
                if (vm.unlockAdult(pin)) {
                    showAdultPin = false
                    true
                } else false
            },
            onDismiss = { showAdultPin = false }
        )
    }
    // Sekmeler arası geçişte her ekranın durumu (pager sayfası, kaydırma
    // konumu, arama/ filtre girişleri) korunur — sıfırdan kurulmadığı için
    // menü geçişleri daha akıcı olur.
    val saveableStateHolder = rememberSaveableStateHolder()

    // Kütüphanem alt menüden kaldırıldı: Ayarlar → Kütüphanem üzerinden açılır
    // (tab 5'e geçiş yapılır; menüde 6 öğe kalır: Ana, Canlı, Film, Dizi, Ayarlar, Ara).
    val navItems = listOf(
        NavItem(Icons.Default.Home, com.stalkerapp.util.L10n.t(lang, "Ana Sayfa")),
        NavItem(Icons.Default.LiveTv, com.stalkerapp.util.L10n.t(lang, "Canlı TV")),
        NavItem(Icons.Default.Movie, com.stalkerapp.util.L10n.t(lang, "Filmler")),
        NavItem(Icons.Default.VideoLibrary, com.stalkerapp.util.L10n.t(lang, "Diziler")),
        NavItem(Icons.Default.Download, com.stalkerapp.util.L10n.t(lang, "İndirilenler")),
        NavItem(Icons.Default.Settings, com.stalkerapp.util.L10n.t(lang, "Ayarlar")),
        NavItem(Icons.Default.Search, com.stalkerapp.util.L10n.t(lang, "Ara"), onClick = onOpenSearch)
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Tek cam (glass) pill: derin siyah + ince beyaz çerçeve, yarı
            // saydam; aktif öğe beyaz parıltı (glow) ile belirginleşir, mavi
            // aksan yok. Sabit yükseklik (62dp) tek bir yüzen kutu.
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .height(62.dp),
                shape = AppleTvTokens.PillShape
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navItems.forEachIndexed { index, item ->
                        val selected = index == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(AppleTvTokens.PillShape)
                        ) {
                            AppleNavItem(
                                item = item,
                                selected = selected,
                                onClick = { if (item.onClick != null) item.onClick() else tab = index }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        // İçerik yüzen cam pill'in ARKASINDAN akar (sadece üst iç boşluk
        // uygulanır); böylece pill'in arkasında dolu bir bant görünmez ve cam
        // efekt gerçek olur. Her sekme kendi listesinin sonuna pill yüksekliği
        // kadar boşluk ekler. SaveableStateProvider sayesinde sekme değişince
        // ekran durumu (kaydırma konumu, pager sayfası) kaybolmaz.
        val contentModifier = Modifier
            .fillMaxSize()
            .background(AppleTvTokens.Surface)
            .padding(top = padding.calculateTopPadding())
        // Portal değişince (farklı id) HomeDashboardScreen'i baştan kur ki Canlı TV
        // önizleme kanalları yeni portaldan yüklensin (iç LaunchedEffect(Unit) yeniden tetiklenir).
        val portalKey = when (vm.activeSourceKind()) {
            "m3u", "xtream" -> "${vm.activeSourceKind()}:${vm.activeSourceId() ?: "none"}"
            else -> profile?.portal?.id ?: "none"
        }
        saveableStateHolder.SaveableStateProvider("$tab:$portalKey") {
            when (tab) {
                0 -> HomeDashboardScreen(profile, onOpenVod, onOpenPlayer, gotoTab, contentModifier)
                1 -> LiveTvScreen(profile, onOpenPlayer, contentModifier.statusBarsPadding(), onOpenGuide = onOpenGuide)
                2 -> VodScreen(profile, onOpenVod, contentModifier.statusBarsPadding(), filterIsSeries = false)
                3 -> VodScreen(profile, onOpenVod, contentModifier.statusBarsPadding(), filterIsSeries = true)
                4 -> com.stalkerapp.ui.downloads.DownloadsScreen(
                    onPlayOffline = { entry ->
                        val playUrl = com.stalkerapp.data.OfflineDownloadManager.getPlayableOfflineUrl(entry)
                        com.stalkerapp.playback.PlaybackManager.playOffline(
                            playUrl, entry.title, entry.poster, entry.episodeLabel
                        )
                        onOpenPlayer()
                    },
                    modifier = contentModifier.statusBarsPadding()
                )
                5 -> SettingsScreen(
                    vm = vm,
                    modifier = contentModifier.statusBarsPadding(),
                    onPortalsChanged = {
                        val p = vm.repository.cachedProfile()
                        profile = p
                        if (p != null) vm.syncVodIfNeeded(p)
                    },
                    onOpenLibrary = { tab = 6 },
                    onOpenPlayer = onOpenPlayer,
                    // Telefon geri tuşu ayarlardan çıkarken uygulamayı kapatmasın.
                    onBack = { gotoTab(0) },
                    onRestartSetup = onOpenOnboarding,
                    onOpenProfiles = onOpenProfiles
                )
                6 -> LibraryScreen(profile, onOpenPlayer, onOpenVod, contentModifier.statusBarsPadding())
            }
        }
    }
}
