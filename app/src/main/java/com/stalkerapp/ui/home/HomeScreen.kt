package com.stalkerapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stalkerapp.StalkerApp
import com.stalkerapp.ui.MainViewModel
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
            Button(
                onClick = { if (!onUnlock(pin)) error = true },
                enabled = pin.isNotBlank()
            ) { Text(str(lang, "Aç")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(lang, "Vazgeç")) } }
    )
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
        NavItem(Icons.Default.Settings, com.stalkerapp.util.L10n.t(lang, "Ayarlar")),
        NavItem(Icons.Default.Search, com.stalkerapp.util.L10n.t(lang, "Ara"), onClick = onOpenSearch)
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Tek glass (cam) pill: yanlardan tam yuvarlak, gölge + boşluk ile
            // yüzer (floating) hissi, yarı saydam cam arka plan. Sabit yükseklik
            // (62dp) tek bir kutu — `blur` kullanılmaz (bazı cihazlarda öğenin
            // tüm ekranı kaplamasına yol açan Compose render sorunları var);
            // yarı saydam yüzey + ince çerçeve + gölge cam görünümünü verir.
            val glassShape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .height(62.dp)
                    .shadow(18.dp, glassShape)
                    .clip(glassShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.93f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                        shape = glassShape
                    )
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
                                .clip(glassShape)
                                .clickable { if (item.onClick != null) item.onClick() else tab = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(40),
                                // Aktif öğe: açık gri yarı saydam zemin (cam üzerinde
                                // açık gri görünür) + beyaz simge.
                                color = if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = if (selected) Color.White
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(9.dp).size(22.dp)
                                )
                            }
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
        val contentModifier = Modifier.padding(top = padding.calculateTopPadding())
        // Portal değişince (farklı id) HomeDashboardScreen'i baştan kur ki Canlı TV
        // önizleme kanalları yeni portaldan yüklensin (iç LaunchedEffect(Unit) yeniden tetiklenir).
        val portalKey = profile?.portal?.id ?: "none"
        saveableStateHolder.SaveableStateProvider("$tab:$portalKey") {
            when (tab) {
                0 -> HomeDashboardScreen(profile, onOpenVod, onOpenPlayer, gotoTab, contentModifier)
                1 -> LiveTvScreen(profile, onOpenPlayer, contentModifier.statusBarsPadding(), onOpenGuide = onOpenGuide)
                2 -> VodScreen(profile, onOpenVod, contentModifier.statusBarsPadding(), filterIsSeries = false)
                3 -> VodScreen(profile, onOpenVod, contentModifier.statusBarsPadding(), filterIsSeries = true)
                4 -> SettingsScreen(
                    vm = vm,
                    modifier = contentModifier.statusBarsPadding(),
                    onPortalsChanged = {
                        val p = vm.repository.cachedProfile()
                        profile = p
                        if (p != null) vm.syncVodIfNeeded(p)
                    },
                    onOpenLibrary = { tab = 5 },
                    onOpenPlayer = onOpenPlayer,
                    // Telefon geri tuşu ayarlardan çıkarken uygulamayı kapatmasın.
                    onBack = { gotoTab(0) },
                    onRestartSetup = onOpenOnboarding,
                    onOpenProfiles = onOpenProfiles
                )
                5 -> LibraryScreen(profile, onOpenPlayer, onOpenVod, contentModifier.statusBarsPadding())
            }
        }
    }
}