package com.stalkerapp.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stalkerapp.BuildConfig
import com.stalkerapp.StalkerApp
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.components.AdultPinDialog
import com.stalkerapp.ui.components.GlassChip
import com.stalkerapp.ui.components.PortioBadge
import com.stalkerapp.ui.components.PortioButton
import com.stalkerapp.ui.components.PortioButtonStyle
import com.stalkerapp.ui.components.PortioCard
import com.stalkerapp.ui.components.PortioConfirmDialog
import com.stalkerapp.ui.components.PortioDialog
import com.stalkerapp.ui.components.PortioPrimaryButton
import com.stalkerapp.ui.components.PortioSecondaryButton
import com.stalkerapp.ui.components.PortioSwitch
import com.stalkerapp.ui.components.PortioTextField
import com.stalkerapp.ui.components.PortioTopAppBar
import com.stalkerapp.ui.components.SectionTitle
import com.stalkerapp.ui.components.ToastManager
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.ui.theme.PortioShape
import com.stalkerapp.util.L10n
import kotlinx.coroutines.launch

/**
 * Portio Ayarlar Ekranı (SettingsScreen) - Tüm Ayarlar Paneli
 */
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    onPortalsChanged: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onOpenPlayer: () -> Unit = {},
    onBack: () -> Unit = {},
    onRestartSetup: () -> Unit = {},
    onOpenProfiles: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as StalkerApp
    val settings by vm.settings.collectAsStateWithLifecycle()
    val userProfile by vm.userProfile.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val lang = settings.language

    var showClearCacheConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(PortioColors.Background)
        ) {
            PortioTopAppBar(
                title = L10n.t(lang, "Ayarlar"),
                onBack = onBack
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Profil & Hesap Kartı
                item {
                    PortioCard(
                        onClick = onOpenProfiles,
                        shape = PortioShape.Card,
                        containerColor = PortioColors.SurfaceRaised
                    ) { isFocused ->
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(PortioColors.SurfaceElevated)
                                    .border(2.dp, PortioColors.Accent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(userProfile.avatar.ifBlank { "😀" }, fontSize = 24.sp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userProfile.name.ifBlank { "Kullanıcı Profili" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = L10n.t(lang, "Profili Değiştir / Yönet"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PortioColors.TextMuted
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = PortioColors.TextMuted)
                        }
                    }
                }

                // 2. Playlist & Kaynaklar
                item {
                    SettingsGroupHeader(title = L10n.t(lang, "Playlist & Kaynaklar"), icon = Icons.Default.Tv)
                    SettingsItem(
                        title = L10n.t(lang, "Kaynak Yönetimi"),
                        subtitle = L10n.t(lang, "Stalker, Xtream Codes ve M3U kaynaklarını ekle veya düzenle"),
                        onClick = onRestartSetup
                    )
                }

                // 3. Görünüm & Arayüz
                item {
                    SettingsGroupHeader(title = L10n.t(lang, "Görünüm & Düzen"), icon = Icons.Default.Palette)

                    SettingsItem(
                        title = L10n.t(lang, "Ana Sayfa Düzeni"),
                        subtitle = when (settings.homeLayout) {
                            "compact" -> "Kompakt Düzen"
                            "list" -> "Liste Düzeni"
                            else -> "Geniş Satırlar"
                        },
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                GlassChip(
                                    selected = settings.homeLayout == "rows",
                                    onClick = { vm.saveSettings(settings.copy(homeLayout = "rows")) },
                                    label = "Satır"
                                )
                                GlassChip(
                                    selected = settings.homeLayout == "compact",
                                    onClick = { vm.saveSettings(settings.copy(homeLayout = "compact")) },
                                    label = "Kompakt"
                                )
                            }
                        }
                    )
                }

                // 4. Oynatıcı & Medya
                item {
                    SettingsGroupHeader(title = L10n.t(lang, "Oynatıcı & Medya"), icon = Icons.Default.PlayCircle)

                    SettingsSwitchItem(
                        title = L10n.t(lang, "Donanım Çözücü"),
                        subtitle = L10n.t(lang, "Akıcı video oynatımı ve düşük pil tüketimi"),
                        checked = settings.decoder == "hardware" || settings.decoder == "auto",
                        onCheckedChange = { checked ->
                            vm.saveSettings(settings.copy(decoder = if (checked) "hardware" else "software"))
                        }
                    )

                    SettingsSwitchItem(
                        title = L10n.t(lang, "Arka Planda Oynatma"),
                        subtitle = L10n.t(lang, "Uygulama arka plana geçtiğinde ses çalmaya devam etsin"),
                        checked = settings.backgroundPlayback,
                        onCheckedChange = { checked ->
                            vm.saveSettings(settings.copy(backgroundPlayback = checked))
                        }
                    )

                    SettingsSwitchItem(
                        title = L10n.t(lang, "Resim İçinde Resim (PiP)"),
                        subtitle = L10n.t(lang, "Ana ekrana dönüldüğünde küçük pencerede oynat"),
                        checked = settings.pipEnabled,
                        onCheckedChange = { checked ->
                            vm.saveSettings(settings.copy(pipEnabled = checked))
                        }
                    )

                    SettingsSwitchItem(
                        title = L10n.t(lang, "Kaldığı Yerden Devam Et"),
                        subtitle = L10n.t(lang, "Filmleri ve bölümleri kaldığın süreden başlat"),
                        checked = settings.resumePlayback,
                        onCheckedChange = { checked ->
                            vm.saveSettings(settings.copy(resumePlayback = checked))
                        }
                    )
                }

                // 5. Dil & Altyazı
                item {
                    SettingsGroupHeader(title = L10n.t(lang, "Dil & Yerelleştirme"), icon = Icons.Default.Language)

                    SettingsItem(
                        title = L10n.t(lang, "Uygulama Dili"),
                        subtitle = if (lang == "tr") "Türkçe" else "English",
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                GlassChip(
                                    selected = lang == "tr",
                                    onClick = { vm.saveSettings(settings.copy(language = "tr")) },
                                    label = "TR"
                                )
                                GlassChip(
                                    selected = lang == "en",
                                    onClick = { vm.saveSettings(settings.copy(language = "en")) },
                                    label = "EN"
                                )
                            }
                        }
                    )
                }

                // 6. Ebeveyn Denetimi
                item {
                    SettingsGroupHeader(title = L10n.t(lang, "Ebeveyn Denetimi"), icon = Icons.Default.Lock)

                    SettingsSwitchItem(
                        title = L10n.t(lang, "Yetişkin İçerik Kilidi"),
                        subtitle = L10n.t(lang, "+18 kanallar ve içerikler PIN ile kilitlensin"),
                        checked = settings.lockAdultWithPin,
                        onCheckedChange = { checked ->
                            vm.saveSettings(settings.copy(lockAdultWithPin = checked))
                        }
                    )
                }

                // 7. Depolama & Önbellek
                item {
                    SettingsGroupHeader(title = L10n.t(lang, "Depolama & Bakım"), icon = Icons.Default.Storage)

                    SettingsItem(
                        title = L10n.t(lang, "Önbelleği Temizle"),
                        subtitle = L10n.t(lang, "Afişler, logolar ve geçici dosyaları temizle"),
                        onClick = { showClearCacheConfirm = true }
                    )

                    SettingsItem(
                        title = L10n.t(lang, "VOD Kataloğunu Yeniden Senkronize Et"),
                        subtitle = L10n.t(lang, "Portal film ve dizi listesini sunucudan tekrar çek"),
                        onClick = {
                            val p = vm.repository.cachedProfile()
                            if (p != null) {
                                vm.syncVodIfNeeded(p)
                                ToastManager.show(L10n.t(lang, "Senkronizasyon başlatıldı"))
                            }
                        }
                    )
                }

                // 8. Hakkında
                item {
                    SettingsGroupHeader(title = L10n.t(lang, "Hakkında"), icon = Icons.Default.Info)

                    SettingsItem(
                        title = "Portio IPTV Player",
                        subtitle = "Sürüm v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                        trailing = {
                            PortioBadge(text = "GÜNCEL", backgroundColor = PortioColors.Success)
                        }
                    )
                }
            }
        }
    }

    if (showClearCacheConfirm) {
        PortioConfirmDialog(
            title = L10n.t(lang, "Önbelleği Temizle"),
            message = L10n.t(lang, "Tüm geçici görseller ve önbellek dosyaları silinecektir. Devam etmek istiyor musun?"),
            confirmText = L10n.t(lang, "Temizle"),
            onConfirm = {
                showClearCacheConfirm = false
                app.cacheDir.deleteRecursively()
                ToastManager.success(L10n.t(lang, "Önbellek temizlendi"))
            },
            onDismiss = { showClearCacheConfirm = false }
        )
    }
}

@Composable
private fun SettingsGroupHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = PortioColors.Accent, modifier = Modifier.size(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = PortioColors.Accent
        )
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    PortioCard(
        onClick = onClick,
        shape = PortioShape.CardSmall,
        containerColor = PortioColors.Surface
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = PortioColors.TextMuted
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            } else if (onClick != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = PortioColors.TextMuted)
            }
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsItem(
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            PortioSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}
