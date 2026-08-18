package com.stalkerapp.ui.settings

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stalkerapp.data.Profile
import com.stalkerapp.data.Settings
import com.stalkerapp.sync.FirebaseSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Önbellek ve indirmeler ayarları bölümü. */
@Composable
fun CacheSettingsSection(
    lang: String,
    settings: Settings,
    vm: SettingsViewModel,
    context: Context,
    scope: CoroutineScope
) {
    Text(str(lang, "Önbellek & Çevrimdışı İndirmeler"), style = MaterialTheme.typography.titleSmall)

    ToggleRow(
        icon = Icons.Default.Sync,
        title = str(lang, "Delta Senkronizasyon"),
        desc = str(lang, "Katalog güncellenirken sadece değişen içerikler indirilir (bant genişliği tasarrufu)"),
        checked = settings.deltaSync,
        onCheckedChange = { vm.saveSettings(settings.copy(deltaSync = it)) }
    )

    ToggleRow(
        icon = Icons.Default.Wifi,
        title = str(lang, "Yalnızca Wi-Fi'da İndir"),
        desc = str(lang, "Çevrimdışı indirmeler hücresel veriyi kullanmaz"),
        checked = settings.downloadWifiOnly,
        onCheckedChange = { vm.saveSettings(settings.copy(downloadWifiOnly = it)) }
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    var maxCacheMb by remember(settings.maxCacheMb) { mutableFloatStateOf(settings.maxCacheMb.toFloat()) }
    SliderSetting(
        icon = Icons.Default.Storage,
        title = str(lang, "Katalog Önbellek Boyutu"),
        description = str(lang, "Katalog verileri ve görseller için maksimum alan (MB)"),
        value = maxCacheMb,
        valueRange = 100f..2000f,
        steps = 19,
        valueText = "${maxCacheMb.toInt()} MB",
        onChange = {
            maxCacheMb = it
            vm.saveSettings(settings.copy(maxCacheMb = it.toLong()))
        }
    )

    var maxOfflineMb by remember(settings.maxOfflineStorageMb) { mutableFloatStateOf(settings.maxOfflineStorageMb.toFloat()) }
    SliderSetting(
        icon = Icons.Default.Download,
        title = str(lang, "Çevrimdışı İndirme Kotası"),
        description = str(lang, "Film ve diziler için ayrılan maksimum depolama (MB)"),
        value = maxOfflineMb,
        valueRange = 500f..10000f,
        steps = 19,
        valueText = "${maxOfflineMb.toInt()} MB",
        onChange = {
            maxOfflineMb = it
            vm.saveSettings(settings.copy(maxOfflineStorageMb = it.toLong()))
            com.stalkerapp.data.OfflineDownloadManager.init(context, it.toLong())
        }
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showClearDownloadsConfirm by remember { mutableStateOf(false) }

    SectionHeader(Icons.Default.Delete, str(lang, "Depolama Temizliği"))

    OutlinedButton(
        onClick = { showClearCacheConfirm = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(str(lang, "Önbelleği Temizle"))
    }

    OutlinedButton(
        onClick = { showClearDownloadsConfirm = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ) {
        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(str(lang, "İndirilenleri Sıfırla"))
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text(str(lang, "Önbelleği Temizle")) },
            text = {
                Text(str(lang, "Uygulama resim önbelleği, geçici dosyalar ve bellek verileri silinecek. İçerikleriniz veya hesaplarınız silinmez."))
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheConfirm = false
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                            context.cacheDir.deleteRecursively()
                            context.codeCacheDir.deleteRecursively()
                        }
                        com.stalkerapp.playback.IntroDetector.clearCache()
                    }
                    vm.showMessage(str(lang, "Önbellek başarıyla temizlendi ✓"))
                }) { Text(str(lang, "Temizle")) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) { Text(str(lang, "İptal")) }
            }
        )
    }

    if (showClearDownloadsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDownloadsConfirm = false },
            title = { Text(str(lang, "İndirilenleri Sıfırla")) },
            text = {
                Text(str(lang, "Cihaza indirilmiş tüm çevrimdışı film ve diziler tamamen silinecek. Emin misiniz?"))
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDownloadsConfirm = false
                    com.stalkerapp.data.OfflineDownloadManager.clearAllDownloads()
                    vm.showMessage(str(lang, "İndirilenler temizlendi ✓"))
                }) { Text(str(lang, "Tümünü Sil"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloadsConfirm = false }) { Text(str(lang, "İptal")) }
            }
        )
    }
}

/** Hesap, bulut ve veri yedekleme ayarları bölümü. */
@Composable
fun AccountSettingsSection(
    lang: String,
    profile: Profile,
    profiles: List<Profile>,
    appProfile: com.stalkerapp.data.AppProfile?,
    settings: Settings,
    vm: SettingsViewModel,
    scope: CoroutineScope,
    watchedVersion: Int,
    restoreMessage: String?,
    onOpenProfiles: () -> Unit,
    onRestartSetup: () -> Unit,
    shareBackup: () -> Unit,
    onRestoreRequest: () -> Unit
) {
    fun t(k: String) = str(lang, k)
    var showResetAll by remember { mutableStateOf(false) }

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
                profile.name.ifBlank { t("İzleyici") },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                appProfile?.portal?.name ?: appProfile?.baseUrl ?: t("Aktif kaynak yok"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    OutlinedButton(
        onClick = onOpenProfiles,
        modifier = Modifier.fillMaxWidth()
    ) { Text(t("Profili Değiştir") + " (${profiles.size})") }
    OutlinedButton(
        onClick = onRestartSetup,
        modifier = Modifier.fillMaxWidth()
    ) { Text(t("Kurulumu Yeniden Aç")) }

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    SectionHeader(Icons.Default.Cloud, t("Bulut Hesabı"))
    val firebase = FirebaseSyncManager.instance
    val signedIn = firebase.isSignedIn
    if (signedIn) {
        Text(
            t("Giriş yapıldı") + ": ${firebase.userEmail}\n" +
                t("Verilerin bulutta senkronlanır — başka cihazda aynı hesapla giriş yapınca kaldığın yerden devam edersin."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val ok = firebase.pushBackup(vm.store)
                        vm.showMessage(if (ok) t("Yedek buluta kaydedildi ✓") else t("Yedeklenemedi — oturum kapalı"))
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text(t("Buluta Yedekle")) }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val ok = firebase.restoreFromCloud(vm.store)
                        if (ok) vm.refreshFlows()
                        vm.showMessage(firebase.syncState.value.ifBlank { t("İşlem tamam") })
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text(t("Buluttan Geri Yükle")) }
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    firebase.pushBackup(vm.store)
                    firebase.signOut()
                    vm.store.setAccount(null)
                    vm.refreshFlows()
                    vm.showMessage(t("Çıkış yapıldı"))
                    onRestartSetup()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(t("Çıkış Yap"), color = MaterialTheme.colorScheme.error) }
    } else {
        Text(
            t("Hesap oluşturup giriş yaparsan favorilerin, izleme geçmişin ve ayarların bulutta saklanır; başka cihazda kaldığın yerden devam edersin."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = { onRestartSetup() },
            modifier = Modifier.fillMaxWidth()
        ) { Text(t("Giriş Yap / Kayıt Ol")) }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    SectionHeader(Icons.Default.Star, t("İstatistikler"))
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
        "✓ " + t("İzlenen") + ": ${stats.first} (" + t("film + bölüm") + ")",
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        "★ " + t("Favoriler") + ": ${stats.second} (" + t("film + kanal") + ")",
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        "⏱ " + t("Toplam izleme") + ": ${("%.1f").format(stats.third)} " + t("saat"),
        style = MaterialTheme.typography.bodyMedium
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    SectionHeader(Icons.Default.Refresh, t("Yedekleme & Veri"))
    Text(
        t("Tüm veriler (kaynaklar, ayarlar, favoriler, izleme geçmişi, listeler) tek JSON olarak dışa aktarılır. Telefon değiştirirken yedeği geri yükleyebilirsin."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(
        onClick = { shareBackup() },
        modifier = Modifier.fillMaxWidth()
    ) { Text(t("Yedeği Dışa Aktar (Paylaş)")) }
    OutlinedButton(
        onClick = onRestoreRequest,
        modifier = Modifier.fillMaxWidth()
    ) { Text(t("Yedekten Geri Yükle")) }
    if (restoreMessage != null) {
        Text(
            restoreMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
    OutlinedButton(
        onClick = { showResetAll = true },
        modifier = Modifier.fillMaxWidth()
    ) { Text(t("Tüm Verileri Sıfırla"), color = MaterialTheme.colorScheme.error) }
    if (showResetAll) {
        AlertDialog(
            onDismissRequest = { showResetAll = false },
            confirmButton = {
                TextButton(onClick = {
                    showResetAll = false
                    vm.clearAllData()
                    vm.showMessage(t("Tüm veriler silindi"))
                }) { Text(t("Evet, Sil"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showResetAll = false }) { Text(t("Vazgeç")) } },
            title = { Text(t("Tüm veriler silinecek")) },
            text = { Text(t("Kaynaklar, ayarlar, favoriler, izleme geçmişi ve katalog kalıcı olarak silinir. Bu işlem geri alınamaz.")) }
        )
    }
}
