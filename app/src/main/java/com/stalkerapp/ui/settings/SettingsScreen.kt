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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.stalkerapp.data.VodItem
import com.stalkerapp.data.XtreamClient
import com.stalkerapp.data.FirebaseSyncManager
import com.stalkerapp.data.CustomChannelGroup
import com.stalkerapp.data.XtreamSource
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.MainViewModel
import com.stalkerapp.ui.VodCatalogStatus
import com.stalkerapp.ui.components.GlassChip
import com.stalkerapp.ui.components.UpdateDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val L10nLocal: Map<String, String> = mapOf(
    // Türkçe -> English
    "(trakt.tv/settings/apps üzerinden ücretsiz uygulama oluşturup alınır)." to "(get it free by creating an app at trakt.tv/settings/apps).",
    "+50 ms" to "+50 ms",
    "A/V Senkron (Ses Gecikmesi)" to "A/V Sync (Audio Delay)",
    "AMOLED modda arka plan tam siyahtır (OLED ekranlar için)." to "In AMOLED mode the background is pure black (for OLED screens).",
    "Ad Düzenleyici (Önek / Sonek)" to "Name Editor (Prefix / Suffix)",
    "Ad Düzenleyiciyi Uygula" to "Apply Name Editor",
    "Aktif Kaynak" to "Active Source",
    "Aktif kaynakta grup bulunamadı. Canlı TV'ye kaynak eklerseniz gruplar burada listelenir." to "No groups found in the active source. Add a source to Live TV and its groups will be listed here.",
    "Akış Formatı (Zorla)" to "Stream Format (Force)",
    "Altyazı Dili Kaydet" to "Save Subtitle Language",
    "Altyazı Türleri" to "Subtitle Types",
    "Altyazı dili kodu (örn. tr)" to "Subtitle language code (e.g. tr)",
    "Ana Sayfa Bölüm Sırası" to "Home Section Order",
    "Ana Sayfa Düzeni" to "Home Layout",
    "Ana Sayfadan Gizlenenler" to "Hidden from Home",
    "Ana aksan rengi; bileşenlerin seçili/kutucuk renklerini etkiler." to "Primary accent color; affects selected/highlight colors of components.",
    "Ana sayfada tekrar görünüyor" to "Visible on Home again",
    "Android TV" to "Android TV",
    "Arka planda devam ediyor; uygulama kapansa bile kaldığı yerden sürdürülür." to "Continuing in the background; resumes where it left off even if the app is closed.",
    "Auto Frame Rate (AFR)" to "Auto Frame Rate (AFR)",
    "Açılış Sekmesi" to "Opening Tab",
    "Aşağı taşı" to "Move down",
    "Bağlantıyı Kaldır" to "Remove Connection",
    "Başlık, fragman ve oyuncu bilgilerinin dili (posterler etkilenmez)." to "Language of titles, trailers and cast info (posters are not affected).",
    "Bölüm Başına Öğe Sayısı" to "Items per Section",
    "Client ID'yi Kaydet" to "Save Client ID",
    "Cooldown Yönetimi" to "Cooldown Management",
    "Cooldown'u Temizle" to "Clear Cooldown",
    "Dil" to "Language",
    "EPG Kaynak Önceliği" to "EPG Source Priority",
    "EPG Rehberi" to "EPG Guide",
    "EPG kaymalarını düzeltmek için sunucu ile kendi saatin arasındaki fark (+3, -2 vb.)." to "Time difference between the server and your clock to fix EPG offsets (+3, -2, etc.).",
    "EPG kaynağı kaydedildi" to "EPG source saved",
    "EPG rehberinden planlanan kayıtlar cihaza indirilir. MPEG-TS / ilerlemeli akışlarda güvenilirdir." to "Recordings scheduled from the EPG guide are saved to the device. Reliable for MPEG-TS / progressive streams.",
    "EPG önbelleği temizlendi (bir sonraki rehber görünümünde yeniden çekilir)" to "EPG cache cleared (it will be re-fetched on the next guide view)",
    "Ekle" to "Add",
    "Film & Dizileri Yenile" to "Refresh Movies & Series",
    "Film/bölüm ilerlemeleri ve izlendi işaretleri silinir. Ana sayfadaki Son İzlenenler / İzlemeye Devam bölümleri boşalır." to "Movie/episode progress and watched marks are deleted. The Recently Watched / Keep Watching sections on Home become empty.",
    "Geri Göster" to "Show Again",
    "Geçmiş Gün Sayısı" to "Past Days Count",
    "Gizlenecek Canlı TV Grupları" to "Live TV Groups to Hide",
    "Gizlenen Kategoriler" to "Hidden Categories",
    "Gizlenen kategoriler tekrar gösteriliyor" to "Hidden categories shown again",
    "Görsel ve TMDB önbelleği temizlendi" to "Image and TMDB cache cleared",
    "Güncelleme Sıklığı" to "Update Frequency",
    "Harici EPG (XMLTV) URL" to "External EPG (XMLTV) URL",
    "Harici XMLTV ne sıklıkta yeniden indirilsin (saat)." to "How often to re-download the external XMLTV (hours).",
    "Henüz liste yok. Aşağıdan yeni bir liste oluştur; içerik detayından listeye ekleyebilirsin." to "No lists yet. Create one below; you can add content to it from the item's details.",
    "Her oynatmada bu hız uygulanır (oynatıcı içinden de değiştirilebilir)." to "This speed is applied on every playback (can also be changed inside the player).",
    "Hızlı Kaynak Değiştir" to "Quick Source Switch",
    "ISO kodu (ör. tr, en). Boş bırakılırsa otomatik seçilir." to "ISO code (e.g. tr, en). Auto-selected if left empty.",
    "ISO kodu (ör. tr, en, de). Boş bırakılırsa otomatik seçilir." to "ISO code (e.g. tr, en, de). Auto-selected if left empty.",
    "Kanal +/- / medya tuşları zapping için kullanılır (Oynatıcı ayarlarından kapatılabilir)." to "Channel +/- / media keys are used for zapping (can be disabled in Player settings).",
    "Kanal Listesini Yenile" to "Refresh Channel List",
    "Kanal Yönetimi" to "Channel Management",
    "Kanal adlarının başından/sonundan silinecek kısımlar (virgülle ayırın, ör. HD, FHD, TR)." to "Parts to strip from channel names (comma separated, e.g. HD, FHD, TR).",
    "Kanal adı düzenleyici kaydedildi" to "Channel name editor saved",
    "Kanal açılmıyorsa veya takılıyorsa akışı zorla HLS ya da MPEG-TS olarak çözmeyi dener." to "If a channel won't open or keeps stalling, it tries to force the stream as HLS or MPEG-TS.",
    "Kanal grupları yükleniyor…" to "Loading channel groups…",
    "Kanal listesi yenileniyor" to "Refreshing channel list",
    "Kanal listesinde bir kanala uzun basarak özel logo atayabilir, özel gruba taşıyabilir ve sıralayabilirsin." to "Long-press a channel in the list to assign a custom logo, move it to a custom group and reorder it.",
    "Kapalı altyazılar (CC) ve yayın altyazıları (DVB/PGS) ayrı ayrı kapatılabilir. " to "Closed captions (CC) and broadcast subtitles (DVB/PGS) can be disabled separately. ",
    "Kapatılan kaynak türü Canlı TV, Filmler ve Dizilerde kullanılmaz." to "A disabled source type is not used in Live TV, Movies and Series.",
    "Katalog henüz yüklenmedi." to "Catalog not loaded yet.",
    "Katalog yüklenince kategoriler burada listelenir (VOD senkronu bekleniyor)." to "Categories are listed here once the catalog is loaded (waiting for VOD sync).",
    "Kataloğu Sıfırla" to "Reset Catalog",
    "Kaydet" to "Save",
    "Kaydı sil" to "Delete recording",
    "Kayıtlar" to "Recordings",
    "Kayıtlı M3U kaynağı yok" to "No saved M3U source",
    "Kayıtlı Xtream kaynağı yok" to "No saved Xtream source",
    "Kayıtlı portal yok" to "No saved portal",
    "Kontrol Gizleme Süresi" to "Controls Auto-hide Timeout",
    "Kütüphanemi Aç" to "Open My Library",
    "Listeyi sil" to "Delete list",
    "M3U Ekle" to "Add M3U",
    "Oluştur" to "Create",
    "Oynat" to "Play",
    "Oynatıcı Yönü" to "Player Orientation",
    "Popüler Filmler/Diziler ve favori bölümlerinde gösterilecek öğe sayısı." to "Number of items shown in Popular Movies/Series and favorites sections.",
    "Portal kendi EPG'sini vermiyorsa harici XMLTV linki kullanılır (xmltv_id eşleşmesi). " to "If the portal doesn't provide its own EPG, an external XMLTV link is used (xmltv_id matching). ",
    "Rehberde bugünden önceki kaç günün programları gösterilsin (catch-up rehberi)." to "How many days before today to show in the guide (catch-up guide).",
    "Senkronizasyon hatası oluştu." to "A synchronization error occurred.",
    "Ses Dili Kaydet" to "Save Audio Language",
    "Ses dili kodu (örn. tr)" to "Audio language code (e.g. tr)",
    "Sesin videoya göre gecikmesi (ms). Pozitif = ses gecikir. Oynatıcı içinden de ayarlanır." to "Audio delay relative to video (ms). Positive = audio is delayed. Also adjustable from the player.",
    "Sil" to "Delete",
    "Silinecek sonekler" to "Suffixes to strip",
    "Silinecek önekler" to "Prefixes to strip",
    "Sol/sağ yarıya çift dokununca ileri-geri atlama miktarı." to "Seek amount when double-tapping the left/right half.",
    "Sonra izle, izlediklerin, favorilerin ve özel listelerin tek ekranda." to "Watch later, watched, favorites and custom lists in one screen.",
    "Stalker Portal Ekle" to "Add Stalker Portal",
    "Stalker portal, M3U listesi ve Xtream Codes kaynaklarını buradan yönetirsin. " to "Manage Stalker portal, M3U list and Xtream Codes sources here. ",
    "Sıfırla" to "Reset",
    "TMDB (themoviedb.org) anahtarı: oyuncu fotoğrafları, fragman ve gerçek bölüm adları. " to "TMDB (themoviedb.org) key: actor photos, trailers and real episode names. ",
    "TMDB API Anahtarı" to "TMDB API Key",
    "TMDB Dili" to "TMDB Language",
    "TMDB anahtarı kaydedildi" to "TMDB key saved",
    "Tema" to "Theme",
    "Temizle" to "Clear",
    "Trakt Client ID" to "Trakt Client ID",
    "Trakt Client ID kaydedildi" to "Trakt Client ID saved",
    "Trakt bağlantısı kaldırıldı" to "Trakt connection removed",
    "Trakt.tv" to "Trakt.tv",
    "Trakt.tv ile Bağlan" to "Connect with Trakt.tv",
    "Tüm dünya (epg.pw Lite)" to "Worldwide (epg.pw Lite)",
    "Tüm ilerlemeler ve izlendi işaretleri silinir. Bu işlem geri alınamaz." to "All progress and watched marks are deleted. This cannot be undone.",
    "Türkiye (önerilen)" to "Turkey (recommended)",
    "Uygulama TV box launcher'larında görünür ve kumanda ile gezinilebilir. " to "The app appears in TV box launchers and is navigable with a remote. ",
    "Uygulama açıldığında hangi sekme gösterilsin." to "Which tab to show when the app opens.",
    "VOD Senkronizasyonu" to "VOD Sync",
    "Varsayılan Altyazı Dili" to "Default Subtitle Language",
    "Varsayılan Oynatma Hızı" to "Default Playback Speed",
    "Varsayılan Oynatıcı" to "Default Player",
    "Varsayılan Ses Dili" to "Default Audio Language",
    "Varsayılan Video Kalitesi" to "Default Video Quality",
    "Varsayılan altyazı dili kaydedildi" to "Default subtitle language saved",
    "Varsayılan ses dili kaydedildi" to "Default audio language saved",
    "Vazgeç" to "Cancel",
    "Vurgu Rengi" to "Accent Color",
    "Xtream Ekle" to "Add Xtream",
    "Yeni liste adı" to "New list name",
    "Yeni özel grup" to "New custom group",
    "Yukarı taşı" to "Move up",
    "Zaman Dilimi (hızlı seçim)" to "Time Zone (quick pick)",
    "Zaman Dilimi Ofseti" to "Time Zone Offset",
    "Çift Dokunma Atlama" to "Double-tap Seek",
    "Çözücü (Decoder)" to "Decoder",
    "Önbelleği Temizle" to "Clear Cache",
    "Önce Gizlilik & Güvenlik bölümünden bir PIN belirlemelisin" to "You must first set a PIN in Privacy & Security",
    "Önerilen: Türkiye EPG'si küçük (166KB) ve hızlıdır. epg.pw Lite dünya kanallarını içerir (1.7MB). İndirilen EPG cihaza kaydedilir, bir daha indirilmez." to "Recommended: the Turkey EPG is small (166KB) and fast. epg.pw Lite covers worldwide channels (1.7MB). The downloaded EPG is saved on the device and not re-downloaded.",
    "Özel Gruplar" to "Custom Groups",
    "Özel Listelerim" to "My Custom Lists",
    "İngilizce şimdilik ana menü ve ayar başlıklarını çevirir (tam çeviri sonraki sürümde)." to "English is now available across the entire interface.",
    "İstemediğin kanal gruplarını tek tek kapat — Canlı TV listesinde ve ana sayfada görünmez." to "Turn off channel groups you don't want — they disappear from Live TV and Home.",
    "İstemediğin kategorileri tek tek kapat — Filmler/Diziler listelerinde ve ana sayfada görünmez." to "Turn off categories you don't want — they disappear from Movies/Series lists and Home.",
    "İzleme Geçmişi" to "Watch History",
    "İzleme Geçmişini Temizle" to "Clear Watch History",
    "İzleme geçmişi temizlendi" to "Watch history cleared",
    "İzleme geçmişi temizlensin mi?" to "Clear watch history?",
    "İzleme geçmişini Trakt.tv hesabına senkronize eder. Bağlamak için bir Trakt Client ID gerekir " to "Syncs your watch history to your Trakt.tv account. A Trakt Client ID is required to connect ",
    "İçerik kare hızına göre ekran yenileme modunu ayarlar (TV box'larda akıcılık)." to "Sets the screen refresh mode based on the content frame rate (smoothness on TV boxes).",
    "Şimdi Senkronize Et" to "Sync Now",
    "−50 ms" to "−50 ms",
    "Bağlı" to "Connected",
    "Kalan süre" to "Time remaining",
    "Portaldan eksik çekildi" to "Incompletely fetched from portal",
    "Son senkron" to "Last sync",
    "Sunucu engeli yok." to "No server block.",
    "Sunucu istekleri engelledi" to "The server has blocked requests",
    "Tür" to "Type",
    "bağlantı başarılı" to "connection successful",
    "dk önce" to "min ago",
    "içerik senkronize edildi" to "items synced",
    "içerik yüklendi" to "items loaded",
    "kategori" to "categories",
    "sn önce" to "s ago",
    "İngilizce" to "English",
    "Şimdi Senkronize Et'e basın." to "Press Sync Now.",
    "#EXTM3U biçimindeki kanal listeleri" to "Channel lists in #EXTM3U format",
    "+18 İçerikler" to "Adult Content",
    "+18'i PIN ile Kilitle" to "Lock Adult Content with PIN",
    "1080p" to "1080p",
    "24 Hz" to "24 Hz",
    "25 Hz" to "25 Hz",
    "30 Hz" to "30 Hz",
    "480p" to "480p",
    "50 Hz" to "50 Hz",
    "60 Hz" to "60 Hz",
    "720p" to "720p",
    "AC3/EAC3/DTS sesi AV alıcıya ham geçirir. Kapalıyken ses cihazda çözülür." to "Passes AC3/EAC3/DTS audio raw to the AV receiver. When off, audio is decoded on the device.",
    "AMOLED" to "AMOLED",
    "Akış kesilince en fazla 3 kez otomatik yeniden dener" to "Automatically retries up to 3 times when the stream drops",
    "Altyazı Boyutu" to "Subtitle Size",
    "Altyazılar" to "Subtitles",
    "Ana sayfada son izlenen canlı kanalların bulunduğu satır gösterilsin" to "Show a row of recently watched live channels on Home",
    "Ana sayfanın üstündeki büyük kaydırmalı tanıtım" to "The large scrollable banner at the top of Home",
    "Arayüz yazı ölçeği (1.00 = varsayılan)." to "Interface font scale (1.00 = default).",
    "Arka Planda Oynatmaya Devam Et" to "Keep Playing in Background",
    "Audio Passthrough" to "Audio Passthrough",
    "Açık" to "Light",
    "Açılışta Son Kanalı Oynat" to "Play Last Channel on Start",
    "Bölüm bitince sıradaki bölüm otomatik oynatılır (binge mod)" to "When an episode ends, the next one plays automatically (binge mode)",
    "CEA-608/708 — yayın içine gömülü kapalı altyazılar" to "CEA-608/708 — closed captions embedded in the broadcast",
    "Canlı TV Otomatik Yeniden Bağlan" to "Live TV Auto Reconnect",
    "Canlı yayın takılmalarını azaltmak için tampon süresi (saniye)." to "Buffer duration (seconds) to reduce live stream stuttering.",
    "Cihaz Açılışında Başlat" to "Start on Device Boot",
    "DVB ve Blu-ray (PGS) gömülü altyazılar" to "DVB and Blu-ray (PGS) embedded subtitles",
    "Detay ekranında TMDB fragmanlarını göster" to "Show TMDB trailers on the details screen",
    "Detay ekranında TMDB oyuncu görsellerini kullan" to "Use TMDB cast images on the details screen",
    "Donanım" to "Hardware",
    "Ekranı Açık Tut" to "Keep Screen On",
    "English" to "English",
    "Film ve bölümler kaldığı konumdan başlar" to "Movies and episodes resume from where you left off",
    "Fragmanlar" to "Trailers",
    "HLS" to "HLS",
    "Harici Oynatıcı" to "External Player",
    "Hero Tanıtım Banner" to "Hero Banner",
    "Kaldığın Yerden Devam Et" to "Resume from Last Position",
    "Kanal +/- ve medya sonraki/önceki tuşlarıyla zapping (TV box)" to "Zap with channel +/- and media next/previous keys (TV box)",
    "Kanal Numaraları" to "Channel Numbers",
    "Kanal listelerinde kanal numarası rozetlerini göster/gizle" to "Show/hide channel number badges in channel lists",
    "Kanal Ön Yükleme (Zapping)" to "Channel Preload (Zapping)",
    "Kapalı" to "Off",
    "Kapalı Altyazılar (CC)" to "Closed Captions (CC)",
    "Kapalıyken açıklama (desc) metinleri bellekte/önbellekte tutulmaz" to "When off, description (desc) texts are not kept in memory/cache",
    "Kartlar" to "Cards",
    "Kaydırarak parlaklık / ses / ileri-geri kontrolü" to "Swipe controls for brightness / volume / seek",
    "Kompakt" to "Compact",
    "Kontrol Paneli Saydamlığı" to "Control Panel Transparency",
    "Koyu" to "Dark",
    "Kumanda Kanal Tuşları" to "Remote Channel Keys",
    "Liste" to "List",
    "M3U Listeleri" to "M3U Lists",
    "MPEG-TS" to "MPEG-TS",
    "Mobil veride büyük katalog indirilmez (manuel \"Şimdi Senkronize Et\" yine çalışır)" to "Large catalogs are not downloaded on mobile data (manual \"Sync Now\" still works)",
    "Otomatik" to "Auto",
    "Otomatik Senkron" to "Auto Sync",
    "Otomatik Sonraki Bölüm" to "Auto Next Episode",
    "Oynatma Tamponu (Buffer)" to "Playback Buffer",
    "Oynatıcı Jestleri" to "Player Gestures",
    "Oynatıcı açıkken ekran uyumaz" to "Screen doesn't sleep while the player is open",
    "Oynatıcıdaki altyazı yazı boyutu (varsayılan 16)." to "Subtitle font size in the player (default 16).",
    "Oynatıcıdaki üst/alt panellerin opaklığı (düşük değer = daha saydam)." to "Opacity of the player's top/bottom panels (lower value = more transparent).",
    "Oynatıcıdan çıkınca küçük pencere devam eder" to "Keeps playing in a small window when leaving the player",
    "Oyuncu Fotoğrafları" to "Cast Photos",
    "PiP dışında uygulama arka plana geçince oynatma duraklatılır" to "Playback pauses when the app goes to background (except PiP)",
    "Picture-in-Picture" to "Picture-in-Picture",
    "Program Açıklamalarını Sakla" to "Keep Program Descriptions",
    "Sabit Yatay" to "Locked Landscape",
    "Serbest" to "Free",
    "Sistem" to "System",
    "Son İzlenen Kanallar (Ana Sayfa)" to "Recently Watched Channels (Home)",
    "Stalker Portallar" to "Stalker Portals",
    "Stalker portalları ardışık isteklere duyarlıdır. İstekler arası bekleme (ms)." to "Stalker portals are sensitive to sequential requests. Delay between requests (ms).",
    "Sunucu + kullanıcı adı + şifre ile Xtream paneli" to "Xtream panel with server + username + password",
    "Sıradaki kanal önceden tamponlanır — kanal değiştirme hızlanır" to "The next channel is buffered in advance — channel switching is faster",
    "TV box açılınca uygulama otomatik başlar (Açılışta Son Kanalı Oynat ile birlikte kullanılabilir)" to "App starts automatically when the TV box turns on (can be combined with Play Last Channel on Start)",
    "Türkçe" to "Turkish",
    "Uygulama açılınca son izlenen canlı kanal otomatik oynatılır" to "The last watched live channel plays automatically when the app opens",
    "Uygulama açılışında katalog arka planda otomatik senkronlanır" to "The catalog syncs automatically in the background when the app opens",
    "Varsa altyazıları göster" to "Show subtitles if available",
    "Xtream Codes" to "Xtream Codes",
    "Yalnızca Wi-Fi'da Senkronla" to "Sync Only on Wi-Fi",
    "Yayın Altyazıları (DVB/PGS)" to "Broadcast Subtitles (DVB/PGS)",
    "Yazı Boyutu" to "Font Size",
    "Yazılım" to "Software",
    "Yerleşik (ExoPlayer)" to "Built-in (ExoPlayer)",
    "Yetişkin içerikler, Gizlilik & Güvenlik'teki PIN girilmeden görünmez (oturumluk açılır)" to "Adult content stays hidden until the PIN from Privacy & Security is entered (unlocks for the session)",
    "Yetişkin içerikli kategorileri göster" to "Show adult content categories",
    "http://ip:port/c biçimindeki Stalker portal profilleri" to "Stalker portal profiles in the http://ip:port/c format",
    "Önce Harici" to "External First",
    "Önce Portal" to "Portal First",
    "İstek Aralığı (Rate Limit)" to "Request Interval (Rate Limit)",
    "İçeriğe Uy" to "Match Content",
    "1 gün" to "1 day",
    "1. Kaynak bilgilerin (portal URL, MAC, kullanıcı adı/şifre) yalnızca bu cihazda saklanır " to "1. Your source info (portal URL, MAC, username/password) is stored only on this device ",
    "2. Hesap oluşturup giriş yaparsan verilerin (kaynaklar, ayarlar, favoriler, izleme geçmişi) " to "2. If you create an account and sign in, your data (sources, settings, favorites, watch history) ",
    "3 gün" to "3 days",
    "3. İzleme verileri üçüncü taraflarla paylaşılmaz (uygulama aracılığıyla değil).\n\n" to "3. Watch data is not shared with third parties (not through the app).\n\n",
    "4. TMDB anahtarını kendin eklersen, zenginleştirme istekleri doğrudan themoviedb.org'a gider.\n\n" to "4. If you add your own TMDB key, enrichment requests go directly to themoviedb.org.\n\n",
    "5. Uygulamayı kaldırdığında cihazdaki tüm veriler silinir; bulut yedeği hesapta kalır." to "5. When you uninstall the app, all data on the device is deleted; the cloud backup stays in your account.",
    "7 gün" to "7 days",
    "Altın" to "Gold",
    "Ana Sayfa" to "Home",
    "Anahtarı Test Et" to "Test Key",
    "Ayarlara erişmek için PIN'i gir." to "Enter the PIN to access settings.",
    "Açık kaynak bileşenler: ExoPlayer (media3), Coil, OkHttp, kotlinx.serialization." to "Open source components: ExoPlayer (media3), Coil, OkHttp, kotlinx.serialization.",
    "Bağlantı reddedildi veya süresi doldu." to "Connection rejected or expired.",
    "Boşsa özellikler kapalıdır." to "If empty, the features are disabled.",
    "Bu uygulama kişisel kullanım için geliştirilmiştir. " to "This app is developed for personal use. ",
    "Canlı" to "Live",
    "Canlı TV" to "Live TV",
    "Dizi" to "Series",
    "Diziler" to "Series",
    "Film" to "Movie",
    "Filmler" to "Movies",
    "Firebase hesabının bulut depolamasına yedeklenir ve cihazlar arasında senkronlanır.\n\n" to "is backed up to your Firebase account's cloud storage and synced across devices.\n\n",
    "Geçerli bir http(s) URL girin" to "Enter a valid http(s) URL",
    "Geçerli bir http(s) sunucu adresi girin" to "Enter a valid http(s) server address",
    "Güncel APK'yı indirip kurmak ister misin?" to "Do you want to download and install the current APK?",
    "Güncel sürümdesiniz" to "You are up to date",
    "Güncelleme kontrol edilemedi" to "Update check failed",
    "Hiçbir içerik uygulama tarafından barındırılmaz; yalnızca kullanıcının " to "No content is hosted by the app; only sources ",
    "Kapatılan türler oynatıcıdaki altyazı listesinden de çıkar." to "Disabled types are also removed from the player's subtitle list.",
    "Kaydetmeden önce doğrulanır." to "It is verified before saving.",
    "Kod alınamadı. Client ID geçerli mi kontrol edin." to "Could not get the code. Check if the Client ID is valid.",
    "Kod alınıyor…" to "Getting code…",
    "Kodun süresi doldu — bağlantıyı yeniden deneyin." to "The code expired — try the connection again.",
    "Kontrol ediliyor…" to "Checking…",
    "Kullanıcı adı gerekli" to "Username required",
    "Kırmızı" to "Red",
    "M3U kaynağı eklendi — Canlı TV'de yükleniyor" to "M3U source added — loading in Live TV",
    "M3U listesi" to "M3U list",
    "M3U listesinin adresini girin (#EXTM3U içeren dosya). Kanal kategorileri group-title'dan; " to "Enter the address of the M3U list (a file containing #EXTM3U). Channel categories come from group-title; ",
    "Mavi" to "Blue",
    "Mor" to "Purple",
    "Onay bekleniyor… Pencere otomatik kapanır." to "Waiting for approval… The window closes automatically.",
    "Stalker portal, M3U ve Xtream Codes destekli bir IPTV oynatıcıdır. " to "It is an IPTV player with Stalker portal, M3U and Xtream Codes support. ",
    "Test ediliyor…" to "Testing…",
    "Trakt bağlantısı kuruldu ✓" to "Trakt connection established ✓",
    "Turuncu" to "Orange",
    "Tüm Kaynakları Test Et" to "Test All Sources",
    "Varsayılan" to "Default",
    "Xtream Codes sunucusu, kullanıcı adı ve şifre girin (ör: http://sunucu:8080). " to "Enter the Xtream Codes server, username and password (e.g. http://server:8080). ",
    "Xtream doğrulaması başarısız — sunucu, kullanıcı adı veya şifre hatalı" to "Xtream verification failed — server, username or password is incorrect",
    "Xtream kaynağı" to "Xtream source",
    "Xtream kaynağı eklendi — Canlı TV'de yükleniyor" to "Xtream source added — loading in Live TV",
    "Yedek geri yüklendi ✓" to "Backup restored ✓",
    "Yedek geçersiz" to "Invalid backup",
    "Yedek okunamadı" to "Could not read backup",
    "Yeşil" to "Green",
    "Yok" to "None",
    "\"dizi/series\" grubu Diziler, diğerleri Filmler sekmesinde görünür. İstersen listeyi " to "the \"series\" group appears in Series, the rest in Movies. You can also ",
    "ağ hatası" to "network error",
    "doğrudan İçerik alanına da yapıştırabilirsin (URL boşsa bu içerik kullanılır)." to "paste the list directly into the Content field (used if the URL is empty).",
    "eklediği kaynaklar oynatılır. Tüm veriler cihazda saklanır.\n\n" to "added by the user are played. All data is stored on the device.\n\n",
    "portal toplamı" to "portal total",
    "trakt.tv/activate adresinde aşağıdaki kodu girin:" to "Enter the following code at trakt.tv/activate:",
    "ve yalnızca kendi IPTV sağlayıcına gönderilir.\n\n" to "and sent only to your own IPTV provider.\n\n",
    "yayınlandı" to "released",
    "Ör: sağlayıcının EPG linki ya da iptv-org/epg çıktısı (.xml / .xml.gz)." to "E.g. the provider's EPG link or an iptv-org/epg export (.xml / .xml.gz).",
    "öğe Trakt geçmişine eklendi" to "items added to Trakt history",
    "İsim" to "Name",
    "✓ Anahtar geçerli" to "✓ Key valid",
    "✗ Geçersiz anahtar" to "✗ Invalid key",
    "dizi" to "series",
    "film" to "movie",
    "kanal" to "channels",
    "● Aktif" to "● Active",
    "Başarısız" to "Failed",
    "Kaydediliyor…" to "Recording…",
    "Planlandı" to "Scheduled",
    "Tamamlandı" to "Completed",
    "İptal" to "Cancelled",
    "Henüz özel grup yok. Kanal listesinde bir kanala uzun basıp \"Yeni grup oluştur\" ile ekleyebilirsin." to "No custom groups yet. Long-press a channel in the channel list and use \"Create group\" to add one.",
    "Uzun bas → \"Ana Sayfadan Kaldır\" ile gizlenen medya buradan geri getirilebilir." to "Long-press → Media hidden via \"Remove from Home\" can be brought back here.",
    "\"Önce Harici\": portal EPG'si eksik/hatalıysa XMLTV önce denenir." to "\"External First\": if the portal EPG is missing/broken, XMLTV is tried first.",
    "Henüz kayıt yok. Oynatıcıdaki rehberden bir programa \"Kaydet\" deyip planlayabilirsin." to "No recordings yet. Say \"Record\" on a program in the player's guide to schedule one.",
    "\"Harici\" seçilirse içerikler sistem oynatıcısında (MXPlayer/VLC vb.) açılır." to "If \"External\" is selected, content opens in the system player (MXPlayer/VLC etc.).",
    "Geçmişi Senkronize Et" to "Sync History",
    "Senkronize ediliyor…" to "Syncing…",
    "✓ Bağlantı başarılı" to "✓ Connection successful",
    "Portal Düzenle" to "Edit Portal",
    "Yeni Stalker Portal" to "New Stalker Portal",
    "M3U Düzenle" to "Edit M3U",
    "Xtream Düzenle" to "Edit Xtream",
    "Sonra" to "Later",
    "Evet, Sıfırla" to "Yes, Reset",
    "Vazgeç" to "Cancel",
    "Tüm veriler sıfırlanacak" to "All data will be reset",
    "PIN'i unuttuysan tek seçenek tüm verileri silmek: portallar, ayarlar, izleme geçmişi ve katalog silinir. Devam edilsin mi?" to "If you forgot your PIN, the only option is to delete all data: portals, settings, watch history and catalog will be removed. Continue?",
    "Hepsini Göster (" to "Show All (",
    "Yeni sürüm var!" to "New version available!",
    "Yanlış PIN" to "Wrong PIN",
    "Kilidi Aç" to "Unlock",
    "PIN'i unuttum — tüm verileri sıfırla" to "Forgot PIN — reset all data",
    "Düzenle" to "Edit",
    "Gizlilik Anlaşması" to "Privacy Agreement",
    "MAC (boş olabilir)" to "MAC (can be empty)",
    "Kullanıcı adı (opsiyonel)" to "Username (optional)",
    "Şifre (opsiyonel)" to "Password (optional)",
    "Kullanıcı adı" to "Username",
    "Şifre" to "Password",
    "Trakt.tv Bağlantısı" to "Trakt.tv Connection",
    "İçerik (isteğe bağlı — #EXTM3U metni)" to "Content (optional — #EXTM3U text)",
    "Kapat" to "Close",
    "İndir" to "Download",
    "Lisans" to "License",
    "Geri" to "Back",
    "Yenile" to "Refresh",
    "PIN Kilidi" to "PIN Lock",
    "4 haneli PIN" to "4-digit PIN",
    "Sunucu (http://host:port)" to "Server (http://host:port)",
    "Adres: " to "Address: ",
)

private fun str(lang: String, text: String): String =
    if (lang == "en") L10nLocal[text] ?: text else text

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
    onOpenPlayer: () -> Unit = {},
    onBack: () -> Unit = {},
    onRestartSetup: () -> Unit = {},
    onOpenProfiles: () -> Unit = {}
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
    val lang = settings.language
    fun t(text: String) = com.stalkerapp.util.L10n.t(settings.language, text)

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

    // Kanal yönetimi (ad düzenleyici + özel gruplar) alanları.
    var stripPrefixesText by remember { mutableStateOf(vm.store.channelCustomization().stripPrefixes.joinToString(", ")) }
    var stripSuffixesText by remember { mutableStateOf(vm.store.channelCustomization().stripSuffixes.joinToString(", ")) }
    var newChannelGroupName by remember { mutableStateOf("") }
    var channelCustVersion by remember { mutableStateOf(0) }
    // Görünüm: yazı ölçeği kaydırıcısı.
    var uiFontScale by remember(settings.uiFontScale) { mutableFloatStateOf(settings.uiFontScale) }

    // Dialog'lar
    var showPortalDialog by remember { mutableStateOf(false) }
    var editingPortal by remember { mutableStateOf<Portal?>(null) }
    var showM3uDialog by remember { mutableStateOf(false) }
    var editingM3u by remember { mutableStateOf<M3uSource?>(null) }
    var showXtreamDialog by remember { mutableStateOf(false) }
    var editingXtream by remember { mutableStateOf<XtreamSource?>(null) }
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
                    restoreMessage = str(lang, "Yedek okunamadı")
                } else {
                    restoreMessage = if (vm.restoreBackup(json)) str(lang, "Yedek geri yüklendi ✓") else str(lang, "Yedek geçersiz")
                }
            }
        }
    }

    fun shareBackup() {
        val json = vm.backupJson()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Portio " + t("yedeği"))
            putExtra(Intent.EXTRA_TEXT, json)
        }
        runCatching { context.startActivity(Intent.createChooser(send, t("Yedeği Paylaş"))) }
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
                    updateMessage = str(lang, "Güncel sürümdesiniz") + " (v${BuildConfig.VERSION_NAME})"
                }
            } catch (e: Exception) {
                updateMessage = str(lang, "Güncelleme kontrol edilemedi") + ": ${e.message ?: str(lang, "ağ hatası")}"
            } finally {
                checkingUpdate = false
            }
        }
    }

    // PIN girilmediyse ayarlar kilitli kalır (yalnızca kilit ekranı gösterilir).
    if (!pinUnlocked) {
        PinLockOverlay(
            modifier = modifier,
            lang = lang,
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
                    }) { Text(str(lang, "Evet, Sıfırla")) }
                },
                dismissButton = { TextButton(onClick = { showPinReset = false }) { Text(str(lang, "Vazgeç")) } },
                title = { Text(str(lang, "Tüm veriler sıfırlanacak")) },
                text = {
                    Text(str(lang, "PIN'i unuttuysan tek seçenek tüm verileri silmek: portallar, ayarlar, izleme geçmişi ve katalog silinir. Devam edilsin mi?"))
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
            Text(
                com.stalkerapp.util.L10n.t(settings.language, "Ayarlar"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                com.stalkerapp.util.L10n.t(settings.language, "Bir bölüm seç:"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SettingsNavRow(Icons.Default.Tv, com.stalkerapp.util.L10n.t(settings.language, "Playlist & Kaynaklar"), com.stalkerapp.util.L10n.t(settings.language, "Stalker portal, M3U ve Xtream kaynakları")) { currentSection = "playlist" }
            SettingsNavRow(Icons.Default.VideoLibrary, com.stalkerapp.util.L10n.t(settings.language, "Kütüphane & İçerik"), com.stalkerapp.util.L10n.t(settings.language, "+18, gizlenen kategoriler, ana sayfa, VOD senkronu")) { currentSection = "content" }
            SettingsNavRow(Icons.Default.Star, com.stalkerapp.util.L10n.t(settings.language, "Kütüphanem"), com.stalkerapp.util.L10n.t(settings.language, "Favoriler, Sonra İzle, özel listeler")) { currentSection = "library" }
            SettingsNavRow(Icons.Default.VolumeUp, com.stalkerapp.util.L10n.t(settings.language, "Oynatıcı"), com.stalkerapp.util.L10n.t(settings.language, "Kalite, altyazı, çözücü, jestler")) { currentSection = "player" }
            SettingsNavRow(Icons.Default.Palette, com.stalkerapp.util.L10n.t(settings.language, "Görünüm & Cihaz"), com.stalkerapp.util.L10n.t(settings.language, "Tema, vurgu rengi, yazı ölçeği, TV")) { currentSection = "appearance" }
            SettingsNavRow(Icons.Default.Link, com.stalkerapp.util.L10n.t(settings.language, "Entegrasyonlar"), com.stalkerapp.util.L10n.t(settings.language, "TMDB, OpenSubtitles ve harici servisler")) { currentSection = "integrations" }
            SettingsNavRow(Icons.Default.Wifi, com.stalkerapp.util.L10n.t(settings.language, "VPN & Ağ"), com.stalkerapp.util.L10n.t(settings.language, "DNS-over-HTTPS, SOCKS5 Proxy")) { currentSection = "network" }
            SettingsNavRow(Icons.Default.Storage, com.stalkerapp.util.L10n.t(settings.language, "Önbellek & İndirmeler"), com.stalkerapp.util.L10n.t(settings.language, "Akıllı önbellek, çevrimdışı indirme kotası")) { currentSection = "cache" }
            SettingsNavRow(Icons.Default.Person, com.stalkerapp.util.L10n.t(settings.language, "Hesap"), com.stalkerapp.util.L10n.t(settings.language, "Profil ve hesap ayarları")) { currentSection = "account" }
            SettingsNavRow(Icons.Default.VerifiedUser, com.stalkerapp.util.L10n.t(settings.language, "Gizlilik & Güvenlik"), com.stalkerapp.util.L10n.t(settings.language, "PIN kilidi, gizlilik anlaşması")) { currentSection = "privacy" }
            SettingsNavRow(Icons.Default.Info, com.stalkerapp.util.L10n.t(settings.language, "Hakkında & Destek"), com.stalkerapp.util.L10n.t(settings.language, "Sürüm, güncelleme, destek")) { currentSection = "about" }
            // Alt boşluk: içerik yüzen cam pill'in arkasından akıyor (scroll altı boş kalmasın).
            Spacer(Modifier.height(96.dp))
        }
        return
    }

    // ================= BÖLÜM SAYFASI =================
    SettingsPage(
        lang = lang,
        icon = when (currentSection) {
            "playlist" -> Icons.Default.Tv
            "content" -> Icons.Default.VideoLibrary
            "library" -> Icons.Default.Star
            "player" -> Icons.Default.VolumeUp
            "appearance" -> Icons.Default.Palette
            "integrations" -> Icons.Default.Link
            "account" -> Icons.Default.Person
            "privacy" -> Icons.Default.VerifiedUser
            else -> Icons.Default.Info
        },
        title = when (currentSection) {
            "playlist" -> com.stalkerapp.util.L10n.t(settings.language, "Playlist & Kaynaklar")
            "content" -> com.stalkerapp.util.L10n.t(settings.language, "Kütüphane & İçerik")
            "library" -> com.stalkerapp.util.L10n.t(settings.language, "Kütüphanem")
            "player" -> com.stalkerapp.util.L10n.t(settings.language, "Oynatıcı")
            "appearance" -> com.stalkerapp.util.L10n.t(settings.language, "Görünüm & Cihaz")
            "integrations" -> com.stalkerapp.util.L10n.t(settings.language, "Entegrasyonlar")
            "account" -> com.stalkerapp.util.L10n.t(settings.language, "Hesap")
            "privacy" -> com.stalkerapp.util.L10n.t(settings.language, "Gizlilik & Güvenlik")
            else -> com.stalkerapp.util.L10n.t(settings.language, "Hakkında & Destek")
        },
        subtitle = when (currentSection) {
            "playlist" -> str(lang, "Stalker portal, M3U listesi ve Xtream Codes kaynaklarını yönet")
            "content" -> str(lang, "+18, gizlenen kategoriler, ana sayfa ve VOD senkron ayarları")
            "library" -> str(lang, "Favoriler, Sonra İzle ve özel listelerin")
            "player" -> str(lang, "Kalite, altyazı, çözücü ve jest ayarları")
            "appearance" -> str(lang, "Tema, vurgu rengi, yazı ölçeği ve TV davranışı")
            "integrations" -> str(lang, "TMDB ve diğer dış servisler")
            "account" -> str(lang, "Profil ve hesap ayarları")
            "privacy" -> str(lang, "PIN kilidi ve gizlilik tercihleri")
            else -> str(lang, "Sürüm, güncelleme ve destek")
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
                        // İçerik dosyada saklanır; yüzlerce MB'lık listelerde iki kez
                        // tam parse yerine tek geçişte canlı/film/dizi sayılır.
                        val file = vm.store.m3uContentFileFor(s.id)
                        val (live, movies, series) = runCatching {
                            withContext(Dispatchers.IO) { M3uParser.countTypes(file) }
                        }.getOrDefault(Triple(0, 0, 0))
                        sourceStats[s.id] = SourceStats(live = live, movies = movies, series = series)
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
                    str(lang, "Stalker portal, M3U listesi ve Xtream Codes kaynaklarını buradan yönetirsin. ") +
                        str(lang, "Kapatılan kaynak türü Canlı TV, Filmler ve Dizilerde kullanılmaz."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ---- Aktif kaynak özeti ----
                val activeSourceName = when (activeKind) {
                    "m3u" -> m3uSources.firstOrNull { it.id == activeSourceId }?.name
                        ?: str(lang, "M3U listesi")
                    "xtream" -> xtreamSources.firstOrNull { it.id == activeSourceId }?.name
                        ?: str(lang, "Xtream kaynağı")
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
                SectionHeader(Icons.Default.Tv, str(lang, "Aktif Kaynak"))
                Text(
                    activeSourceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    str(lang, "Tür") + ": $activeKindLabel" + if (activeStats != null) {
                        "  •  " + str(lang, "Canlı") + ": ${activeStats.live}  •  " + str(lang, "Film") + ": ${activeStats.movies}  •  " + str(lang, "Dizi") + ": ${activeStats.series}"
                    } else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Stalker dışı aktif kaynakta katalog boşsa yeniden çekme kısayolu.
                if (activeKind == "m3u" || activeKind == "xtream") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { scope.launch { vm.ensureExternalVodCatalog(force = true) } }
                        ) { Text(str(lang, "Film & Dizileri Yenile")) }
                        OutlinedButton(
                            onClick = {
                                scope.launch { vm.loadChannelsForActiveSource(appProfile) }
                                vm.showMessage(str(lang, "Kanal listesi yenileniyor"))
                            }
                        ) { Text(str(lang, "Kanal Listesini Yenile")) }
                    }
                }

                // ---- Hızlı kaynak değiştirici ----
                if (portals.isNotEmpty() || m3uSources.isNotEmpty() || xtreamSources.isNotEmpty()) {
                    Text(str(lang, "Hızlı Kaynak Değiştir"), style = MaterialTheme.typography.titleSmall)
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
                    title = str(lang, "Stalker Portallar"),
                    desc = str(lang, "http://ip:port/c biçimindeki Stalker portal profilleri"),
                    checked = settings.stalkerEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(stalkerEnabled = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Link,
                    title = str(lang, "M3U Listeleri"),
                    desc = str(lang, "#EXTM3U biçimindeki kanal listeleri"),
                    checked = settings.m3uEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(m3uEnabled = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.PlayArrow,
                    title = str(lang, "Xtream Codes"),
                    desc = str(lang, "Sunucu + kullanıcı adı + şifre ile Xtream paneli"),
                    checked = settings.xtreamEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(xtreamEnabled = it)) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ---- Stalker portallar ----
                if (settings.stalkerEnabled) {
                    SourceGroupTitle(lang, "Stalker Portallar", activeKind == "stalker")
                    if (portals.isEmpty()) {
                        Text(str(lang, "Kayıtlı portal yok"), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        portals.forEach { p ->
                            val isActive = activeKind == "stalker" && p.id == activeId
                            SourceRow(
                                lang = lang,
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
                        Text(str(lang, "Stalker Portal Ekle"))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }

                // ---- M3U ----
                if (settings.m3uEnabled) {
                    SourceGroupTitle(lang, "M3U Listeleri", activeKind == "m3u")
                    if (m3uSources.isEmpty()) {
                        Text(str(lang, "Kayıtlı M3U kaynağı yok"), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        m3uSources.forEach { s ->
                            val st = sourceStats[s.id]
                            SourceRow(
                                lang = lang,
                                name = s.name.ifBlank { s.url },
                                subtitle = buildString {
                                    append(s.url)
                                    st?.let {
                                        append("  •  ${it.live} " + str(lang, "kanal"))
                                        if (it.movies > 0) append("  •  ${it.movies} " + str(lang, "film"))
                                        if (it.series > 0) append("  •  ${it.series} " + str(lang, "dizi"))
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
                        Text(str(lang, "M3U Ekle"))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }

                // ---- Xtream ----
                if (settings.xtreamEnabled) {
                    SourceGroupTitle(lang, "Xtream Codes", activeKind == "xtream")
                    if (xtreamSources.isEmpty()) {
                        Text(str(lang, "Kayıtlı Xtream kaynağı yok"), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        xtreamSources.forEach { s ->
                            val st = sourceStats[s.id]
                            SourceRow(
                                lang = lang,
                                name = s.name.ifBlank { s.server },
                                subtitle = buildString {
                                    append(s.server)
                                    append("  •  ${s.username}")
                                    st?.let {
                                        append("  •  ${it.live} " + str(lang, "kanal"))
                                        if (it.movies > 0) append("  •  ${it.movies} " + str(lang, "film"))
                                        if (it.series > 0) append("  •  ${it.series} " + str(lang, "dizi"))
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
                        Text(str(lang, "Xtream Ekle"))
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
                ) { Text(if (testingAll) str(lang, "Test ediliyor…") else str(lang, "Tüm Kaynakları Test Et")) }
                testResults?.forEach { (name, err) ->
                    Text(
                        if (err == null) "✓ $name — " + str(lang, "bağlantı başarılı") else "✗ $name — $err",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (err == null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    )
                }
            }
            "content" -> {

                ToggleRow(
                    icon = Icons.Default.Lock,
                    title = str(lang, "+18 İçerikler"),
                    desc = str(lang, "Yetişkin içerikli kategorileri göster"),
                    checked = settings.adultContentEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(adultContentEnabled = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Pin,
                    title = str(lang, "+18'i PIN ile Kilitle"),
                    desc = str(lang, "Yetişkin içerikler, Gizlilik & Güvenlik'teki PIN girilmeden görünmez (oturumluk açılır)"),
                    checked = settings.lockAdultWithPin,
                    onCheckedChange = {
                        if (it && settings.pin.isBlank()) {
                            vm.showMessage(str(lang, "Önce Gizlilik & Güvenlik bölümünden bir PIN belirlemelisin"))
                        } else {
                            vm.saveSettings(settings.copy(lockAdultWithPin = it))
                            if (!it) vm.lockAdult()
                        }
                    }
                )
                ToggleRow(
                    icon = Icons.Default.Tag,
                    title = str(lang, "Kanal Numaraları"),
                    desc = str(lang, "Kanal listelerinde kanal numarası rozetlerini göster/gizle"),
                    checked = !settings.hideChannelNumbers,
                    onCheckedChange = { vm.saveSettings(settings.copy(hideChannelNumbers = !it)) }
                )
                ToggleRow(
                    icon = Icons.Default.History,
                    title = str(lang, "Son İzlenen Kanallar (Ana Sayfa)"),
                    desc = str(lang, "Ana sayfada son izlenen canlı kanalların bulunduğu satır gösterilsin"),
                    checked = settings.recentChannelsOnHome,
                    onCheckedChange = { vm.saveSettings(settings.copy(recentChannelsOnHome = it)) }
                )

                // ---- Gizlenen canlı TV grupları ----
                Text(
                    str(lang, "Gizlenecek Canlı TV Grupları"),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    str(lang, "İstemediğin kanal gruplarını tek tek kapat — Canlı TV listesinde ve ana sayfada görünmez."),
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
                        str(lang, "Kanal grupları yükleniyor…"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val visibleGroups = groups.filter { it.id != 0L }
                    if (visibleGroups.isEmpty()) {
                        Text(
                            str(lang, "Aktif kaynakta grup bulunamadı. Canlı TV'ye kaynak eklerseniz gruplar burada listelenir."),
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

                // ---- Kanal Yönetimi: ad düzenleyici, özel gruplar, açılışta son kanal ----
                SectionHeader(Icons.Default.Tv, str(lang, "Kanal Yönetimi"))
                Text(
                    str(lang, "Kanal adlarını düzenle, özel gruplar oluştur ve kanalları özel gruplara taşı."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(str(lang, "Ad Düzenleyici (Önek / Sonek)"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "Kanal adlarının başından/sonundan silinecek kısımlar (virgülle ayırın, ör. HD, FHD, TR)."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = stripPrefixesText,
                    onValueChange = { stripPrefixesText = it.take(100) },
                    label = { Text(str(lang, "Silinecek önekler")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = stripSuffixesText,
                    onValueChange = { stripSuffixesText = it.take(100) },
                    label = { Text(str(lang, "Silinecek sonekler")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val c = vm.store.channelCustomization()
                        vm.store.saveChannelCustomization(c.copy(
                            stripPrefixes = stripPrefixesText.split(',').map { it.trim() }.filter { it.isNotBlank() },
                            stripSuffixes = stripSuffixesText.split(',').map { it.trim() }.filter { it.isNotBlank() }
                        ))
                        vm.showMessage(str(lang, "Kanal adı düzenleyici kaydedildi"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(str(lang, "Ad Düzenleyiciyi Uygula")) }

                val channelCust = remember(channelCustVersion) { vm.store.channelCustomization() }
                Text(str(lang, "Özel Gruplar"), style = MaterialTheme.typography.titleSmall)
                if (channelCust.customGroups.isEmpty()) {
                    Text(
                        str(lang, "Henüz özel grup yok. Aşağıdan yeni bir grup oluşturabilirsin."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    channelCust.customGroups.forEach { g ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                g.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                val c = vm.store.channelCustomization()
                                vm.store.saveChannelCustomization(c.copy(
                                    customGroups = c.customGroups.filterNot { it.id == g.id },
                                    channelGroup = c.channelGroup.filterValues { it != g.name },
                                    channelOrder = c.channelOrder - g.name,
                                    groupOrder = c.groupOrder - g.name
                                ))
                                channelCustVersion++
                            }) { Text(str(lang, "Sil")) }
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newChannelGroupName,
                        onValueChange = { newChannelGroupName = it.take(30) },
                        label = { Text(str(lang, "Yeni özel grup")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            val name = newChannelGroupName.trim()
                            if (name.isNotBlank()) {
                                val c = vm.store.channelCustomization()
                                if (c.customGroups.none { it.name == name }) {
                                    vm.store.saveChannelCustomization(c.copy(
                                        customGroups = c.customGroups + CustomChannelGroup(
                                            id = "g_" + System.currentTimeMillis().toString(36),
                                            name = name
                                        )
                                    ))
                                    channelCustVersion++
                                }
                                newChannelGroupName = ""
                            }
                        },
                        enabled = newChannelGroupName.trim().isNotBlank()
                    ) { Text(str(lang, "Ekle")) }
                }

                ToggleRow(
                    icon = Icons.Default.Refresh,
                    title = str(lang, "Açılışta Son Kanalı Oynat"),
                    desc = str(lang, "Uygulama açılınca son izlenen canlı kanal otomatik oynatılır"),
                    checked = settings.resumeLastChannel,
                    onCheckedChange = { vm.saveSettings(settings.copy(resumeLastChannel = it)) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ---- Ana sayfadan gizlenenler (geri göster) ----
                if (settings.hiddenFromHome.isNotEmpty()) {
                    SectionHeader(Icons.Default.Home, str(lang, "Ana Sayfadan Gizlenenler"))
                    Text(
                        str(lang, "Uzun bas → \"Ana Sayfadan Kaldır\" ile gizlenen medya buradan geri getirilebilir."),
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
                                vm.showMessage(str(lang, "Ana sayfada tekrar görünüyor"))
                            }) { Text(str(lang, "Geri Göster")) }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ---- İzleme geçmişi ----
                SectionHeader(Icons.Default.Schedule, str(lang, "İzleme Geçmişi"))
                Text(
                    str(lang, "Film/bölüm ilerlemeleri ve izlendi işaretleri silinir. Ana sayfadaki Son İzlenenler / İzlemeye Devam bölümleri boşalır."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { showClearHistory = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(str(lang, "İzleme Geçmişini Temizle")) }
                if (showClearHistory) {
                    AlertDialog(
                        onDismissRequest = { showClearHistory = false },
                        confirmButton = {
                            TextButton(onClick = {
                                showClearHistory = false
                                vm.clearWatchHistory()
                                vm.showMessage(str(lang, "İzleme geçmişi temizlendi"))
                            }) { Text(str(lang, "Temizle")) }
                        },
                        dismissButton = { TextButton(onClick = { showClearHistory = false }) { Text(str(lang, "Vazgeç")) } },
                        title = { Text(str(lang, "İzleme geçmişi temizlensin mi?")) },
                        text = { Text(str(lang, "Tüm ilerlemeler ve izlendi işaretleri silinir. Bu işlem geri alınamaz.")) }
                    )
                }
                // ---- Gizlenen VOD kategorileri (tek tek aç/kapat) ----
                Text(str(lang, "Gizlenen Kategoriler"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "İstemediğin kategorileri tek tek kapat — Filmler/Diziler listelerinde ve ana sayfada görünmez."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val allCats = catalog.categories.filter { it.id != 0L }
                val hiddenCatSet = remember(settings.hiddenCategories) { settings.hiddenCategories.toSet() }
                if (allCats.isEmpty()) {
                    Text(
                        str(lang, "Katalog yüklenince kategoriler burada listelenir (VOD senkronu bekleniyor)."),
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
                            vm.showMessage(str(lang, "Gizlenen kategoriler tekrar gösteriliyor"))
                        }) { Text(str(lang, "Hepsini Göster (") + hiddenCatSet.size.toString() + ")") }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ---- Ana sayfa düzeni (anasayfa dili: cam pill seçenekler) ----
                Text(str(lang, "Ana Sayfa Düzeni"), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("rows" to str(lang, "Kartlar"), "compact" to str(lang, "Kompakt"), "list" to str(lang, "Liste")).forEach { (key, label) ->
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
                    title = str(lang, "Hero Tanıtım Banner"),
                    desc = str(lang, "Ana sayfanın üstündeki büyük kaydırmalı tanıtım"),
                    checked = settings.heroEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(heroEnabled = it)) }
                )
                Text(str(lang, "Bölüm Başına Öğe Sayısı"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "Popüler Filmler/Diziler ve favori bölümlerinde gösterilecek öğe sayısı."),
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
                Text(str(lang, "Açılış Sekmesi"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "Uygulama açıldığında hangi sekme gösterilsin."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to str(lang, "Ana Sayfa"), 1 to str(lang, "Canlı TV"), 2 to str(lang, "Filmler"), 3 to str(lang, "Diziler")).forEach { (key, label) ->
                        GlassChip(
                            selected = settings.defaultTab == key,
                            onClick = { vm.saveSettings(settings.copy(defaultTab = key)) },
                            label = label
                        )
                    }
                }

                // ---- Ana sayfa bölüm sırası ----
                Text(
                    str(lang, "Ana Sayfa Bölüm Sırası"),
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
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = str(lang, "Yukarı taşı"), modifier = Modifier.size(18.dp)) }
                        IconButton(
                            onClick = {
                                val newOrder = order.toMutableList()
                                if (idx < order.size - 1) {
                                    val tmp = newOrder[idx]; newOrder[idx] = newOrder[idx + 1]; newOrder[idx + 1] = tmp
                                    vm.saveSettings(settings.copy(homeSectionOrder = newOrder))
                                }
                            },
                            enabled = idx < order.size - 1
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = str(lang, "Aşağı taşı"), modifier = Modifier.size(18.dp)) }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ---- EPG ----
                SectionHeader(Icons.Default.CalendarMonth, str(lang, "EPG Rehberi"))
                Text(
                    str(lang, "Portal kendi EPG'sini vermiyorsa harici XMLTV linki kullanılır (xmltv_id eşleşmesi). ") +
                        str(lang, "Ör: sağlayıcının EPG linki ya da iptv-org/epg çıktısı (.xml / .xml.gz)."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = epgUrl,
                    onValueChange = { epgUrl = it },
                    label = { Text(str(lang, "Harici EPG (XMLTV) URL")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Hazır EPG kaynakları: tek dokunuşla seç, sonra Kaydet.
                val trEpg = "https://epgshare01.online/epgshare01/epg_ripper_TR1.xml.gz"
                // epg.pw'nin tam "All" dosyası yüzlerce MB'dir ve telefonlarda pratik
                // değildir — çip, çalışan küçük "Lite" sürümüne işaret eder.
                val allEpg = "https://epg.pw/xmltv/epg_lite.xml.gz"
                Text(
                    str(lang, "Önerilen: Türkiye EPG'si küçük (166KB) ve hızlıdır. epg.pw Lite dünya kanallarını içerir (1.7MB). İndirilen EPG cihaza kaydedilir, bir daha indirilmez."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(
                        selected = epgUrl.trim() == trEpg,
                        onClick = { epgUrl = trEpg },
                        label = str(lang, "Türkiye (önerilen)")
                    )
                    GlassChip(
                        selected = epgUrl.trim() == allEpg,
                        onClick = { epgUrl = allEpg },
                        label = str(lang, "Tüm dünya (epg.pw Lite)")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.saveSettings(settings.copy(epgUrl = epgUrl.trim()))
                            vm.repository.clearEpgCache()
                            vm.showMessage(str(lang, "EPG kaynağı kaydedildi"))
                        },
                        enabled = epgUrl.trim().isNotEmpty()
                    ) { Text(str(lang, "Kaydet")) }
                    OutlinedButton(onClick = {
                        vm.repository.clearEpgCache()
                        vm.showMessage(str(lang, "EPG önbelleği temizlendi (bir sonraki rehber görünümünde yeniden çekilir)"))
                    }) { Text(str(lang, "Önbelleği Temizle")) }
                }

                Text(str(lang, "Güncelleme Sıklığı"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "Harici XMLTV ne sıklıkta yeniden indirilsin (saat)."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "1 saat", 6 to "6 saat", 12 to "12 saat", 24 to "24 saat").forEach { (key, label) ->
                        GlassChip(
                            selected = settings.epgRefreshHours == key,
                            onClick = { vm.saveSettings(settings.copy(epgRefreshHours = key)) },
                            label = label
                        )
                    }
                }

                Text(str(lang, "Geçmiş Gün Sayısı"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "Rehberde bugünden önceki kaç günün programları gösterilsin (catch-up rehberi)."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to str(lang, "Yok"), 1 to str(lang, "1 gün"), 3 to str(lang, "3 gün"), 7 to str(lang, "7 gün")).forEach { (key, label) ->
                        GlassChip(
                            selected = settings.epgPastDays == key,
                            onClick = { vm.saveSettings(settings.copy(epgPastDays = key)) },
                            label = label
                        )
                    }
                }

                Text(str(lang, "EPG Kaynak Önceliği"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "\"Önce Harici\": portal EPG'si eksik/hatalıysa XMLTV önce denenir."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("portal" to str(lang, "Önce Portal"), "external" to str(lang, "Önce Harici")).forEach { (key, label) ->
                        GlassChip(
                            selected = settings.epgSourcePriority == key,
                            onClick = { vm.saveSettings(settings.copy(epgSourcePriority = key)) },
                            label = label
                        )
                    }
                }

                ToggleRow(
                    icon = Icons.Default.Info,
                    title = str(lang, "Program Açıklamalarını Sakla"),
                    desc = str(lang, "Kapalıyken açıklama (desc) metinleri bellekte/önbellekte tutulmaz"),
                    checked = settings.epgKeepDescriptions,
                    onCheckedChange = { vm.saveSettings(settings.copy(epgKeepDescriptions = it)) }
                )

                Text(str(lang, "Zaman Dilimi (hızlı seçim)"), style = MaterialTheme.typography.titleSmall)
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
                SectionHeader(Icons.Default.Refresh, str(lang, "VOD Senkronizasyonu"))
                ToggleRow(
                    icon = Icons.Default.Refresh,
                    title = str(lang, "Otomatik Senkron"),
                    desc = str(lang, "Uygulama açılışında katalog arka planda otomatik senkronlanır"),
                    checked = settings.autoSyncVod,
                    onCheckedChange = { vm.saveSettings(settings.copy(autoSyncVod = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Wifi,
                    title = str(lang, "Yalnızca Wi-Fi'da Senkronla"),
                    desc = str(lang, "Mobil veride büyük katalog indirilmez (manuel \"Şimdi Senkronize Et\" yine çalışır)"),
                    checked = settings.wifiOnlySync,
                    onCheckedChange = { vm.saveSettings(settings.copy(wifiOnlySync = it)) }
                )
                when (catalog.status) {
                    VodCatalogStatus.Syncing -> {
                        val ratio = if (catalog.totalCategories > 0) catalog.doneCategories.toFloat() / catalog.totalCategories else 0f
                        LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                        val portalPart = if (catalog.portalTotal > 0) " • " + str(lang, "portal toplamı") + ": ${catalog.portalTotal}" else ""
                        Text(
                            "${catalog.loadedCount} " + str(lang, "içerik yüklendi") + "$portalPart • ${catalog.doneCategories}/${catalog.totalCategories} " + str(lang, "kategori"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            str(lang, "Arka planda devam ediyor; uygulama kapansa bile kaldığı yerden sürdürülür."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    VodCatalogStatus.Ready -> {
                        Text(
                            "✓ ${catalog.loadedCount} " + str(lang, "içerik senkronize edildi"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (catalog.portalTotal > 0 && catalog.loadedCount < catalog.portalTotal * 0.95) {
                            Text(
                                "⚠ " + str(lang, "Portaldan eksik çekildi") + " (${catalog.loadedCount} / ${catalog.portalTotal}). " + str(lang, "Şimdi Senkronize Et'e basın."),
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
                        Text(str(lang, "Senkronizasyon hatası oluştu."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    else -> Text(str(lang, "Katalog henüz yüklenmedi."), style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { if (appProfile != null) vm.syncVodCatalog(appProfile, force = true) },
                        enabled = appProfile != null
                    ) { Text(str(lang, "Şimdi Senkronize Et")) }
                    OutlinedButton(onClick = { vm.resetVodCatalog() }) { Text(str(lang, "Kataloğu Sıfırla")) }
                }
            }
            "library" -> {
                Text(
                    str(lang, "Sonra izle, izlediklerin, favorilerin ve özel listelerin tek ekranda."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onOpenLibrary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(str(lang, "Kütüphanemi Aç"))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SectionHeader(Icons.Default.ListAlt, str(lang, "Özel Listelerim"))
                if (userLists.isEmpty()) {
                    Text(
                        str(lang, "Henüz liste yok. Aşağıdan yeni bir liste oluştur; içerik detayından listeye ekleyebilirsin."),
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
                                Icon(Icons.Default.Delete, contentDescription = str(lang, "Listeyi sil"), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newListName,
                        onValueChange = { newListName = it.take(30) },
                        label = { Text(str(lang, "Yeni liste adı")) },
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
                    ) { Text(str(lang, "Oluştur")) }
                }
            }
            "player" -> {

                ToggleRow(
                    icon = Icons.Default.PlayArrow,
                    title = str(lang, "Otomatik Sonraki Bölüm"),
                    desc = str(lang, "Bölüm bitince sıradaki bölüm otomatik oynatılır (binge mod)"),
                    checked = settings.bingeMode,
                    onCheckedChange = { vm.saveSettings(settings.copy(bingeMode = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.FastForward,
                    title = str(lang, "İntro Atlama Butonu"),
                    desc = str(lang, "Dizi bölümlerinde tespit edilirse 'İntroya Atla' butonu çıkar (TMDB anahtarı gerektirir)"),
                    checked = settings.skipIntroEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(skipIntroEnabled = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.FastForward,
                    title = str(lang, "Outro Atlama Butonu"),
                    desc = str(lang, "Bölüm sonunda 'Sonraki Bölüme Geç' butonu çıkar"),
                    checked = settings.skipOutroEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(skipOutroEnabled = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Schedule,
                    title = str(lang, "Picture-in-Picture"),
                    desc = str(lang, "Oynatıcıdan çıkınca küçük pencere devam eder"),
                    checked = settings.pipEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(pipEnabled = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Speed,
                    title = str(lang, "Oynatıcı Jestleri"),
                    desc = str(lang, "Kaydırarak parlaklık / ses / ileri-geri kontrolü"),
                    checked = settings.gesturesEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(gesturesEnabled = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Info,
                    title = str(lang, "Altyazılar"),
                    desc = str(lang, "Varsa altyazıları göster"),
                    checked = settings.subtitlesEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(subtitlesEnabled = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.PlayArrow,
                    title = str(lang, "Kaldığın Yerden Devam Et"),
                    desc = str(lang, "Film ve bölümler kaldığı konumdan başlar"),
                    checked = settings.resumePlayback,
                    onCheckedChange = { vm.saveSettings(settings.copy(resumePlayback = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Refresh,
                    title = str(lang, "Canlı TV Otomatik Yeniden Bağlan"),
                    desc = str(lang, "Akış kesilince en fazla 3 kez otomatik yeniden dener"),
                    checked = settings.autoRetryLive,
                    onCheckedChange = { vm.saveSettings(settings.copy(autoRetryLive = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Speed,
                    title = str(lang, "Ekranı Açık Tut"),
                    desc = str(lang, "Oynatıcı açıkken ekran uyumaz"),
                    checked = settings.keepScreenOn,
                    onCheckedChange = { vm.saveSettings(settings.copy(keepScreenOn = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Forward10,
                    title = str(lang, "Kanal Ön Yükleme (Zapping)"),
                    desc = str(lang, "Sıradaki kanal önceden tamponlanır — kanal değiştirme hızlanır"),
                    checked = settings.zappingPrefetch,
                    onCheckedChange = { vm.saveSettings(settings.copy(zappingPrefetch = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.PlayArrow,
                    title = str(lang, "Arka Planda Oynatmaya Devam Et"),
                    desc = str(lang, "PiP dışında uygulama arka plana geçince oynatma duraklatılır"),
                    checked = settings.backgroundPlayback,
                    onCheckedChange = { vm.saveSettings(settings.copy(backgroundPlayback = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Tv,
                    title = str(lang, "Kumanda Kanal Tuşları"),
                    desc = str(lang, "Kanal +/- ve medya sonraki/önceki tuşlarıyla zapping (TV box)"),
                    checked = settings.remoteChannelKeys,
                    onCheckedChange = { vm.saveSettings(settings.copy(remoteChannelKeys = it)) }
                )

                // ---- Kontrol paneli saydamlığı ----
                SliderSetting(
                    icon = Icons.Default.Opacity,
                    title = str(lang, "Kontrol Paneli Saydamlığı"),
                    description = str(lang, "Oynatıcıdaki üst/alt panellerin opaklığı (düşük değer = daha saydam)."),
                    value = settings.playerPanelAlpha,
                    valueRange = 0.6f..1f,
                    steps = 3,
                    valueText = "${(settings.playerPanelAlpha * 100).toInt()}%",
                    onChange = { vm.saveSettings(settings.copy(playerPanelAlpha = it)) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ---- Varsayılan ses/altyazı dili (ISO kodu) ----
                Text(str(lang, "Varsayılan Ses Dili"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "ISO kodu (ör. tr, en, de). Boş bırakılırsa otomatik seçilir."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = prefAudioLang,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isLetter() || c == '-' }) prefAudioLang = it },
                    label = { Text(str(lang, "Ses dili kodu (örn. tr)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        vm.saveSettings(settings.copy(preferredAudioLang = prefAudioLang.trim().lowercase()))
                        vm.showMessage(str(lang, "Varsayılan ses dili kaydedildi"))
                    },
                    enabled = prefAudioLang != settings.preferredAudioLang,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(str(lang, "Ses Dili Kaydet")) }

                Text(str(lang, "Varsayılan Altyazı Dili"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "ISO kodu (ör. tr, en). Boş bırakılırsa otomatik seçilir."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = prefSubtitleLang,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isLetter() || c == '-' }) prefSubtitleLang = it },
                    label = { Text(str(lang, "Altyazı dili kodu (örn. tr)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        vm.saveSettings(settings.copy(preferredSubtitleLang = prefSubtitleLang.trim().lowercase()))
                        vm.showMessage(str(lang, "Varsayılan altyazı dili kaydedildi"))
                    },
                    enabled = prefSubtitleLang != settings.preferredSubtitleLang,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(str(lang, "Altyazı Dili Kaydet")) }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ---- Oynatıcı yönü / kontrol süreleri ----
                Text(str(lang, "Oynatıcı Yönü"), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "auto" to str(lang, "Otomatik"),
                        "landscape" to str(lang, "Sabit Yatay"),
                        "sensor" to str(lang, "Serbest")
                    ).forEach { (key, label) ->
                        GlassChip(
                            selected = settings.playerOrientation == key,
                            onClick = { vm.saveSettings(settings.copy(playerOrientation = key)) },
                            label = label
                        )
                    }
                }
                Text(str(lang, "Kontrol Gizleme Süresi"), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3 to "3 sn", 5 to "5 sn", 10 to "10 sn").forEach { (key, label) ->
                        GlassChip(
                            selected = settings.controlsTimeoutSec == key,
                            onClick = { vm.saveSettings(settings.copy(controlsTimeoutSec = key)) },
                            label = label
                        )
                    }
                }
                Text(str(lang, "Çift Dokunma Atlama"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "Sol/sağ yarıya çift dokununca ileri-geri atlama miktarı."),
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

                Text(str(lang, "Varsayılan Oynatma Hızı"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "Her oynatmada bu hız uygulanır (oynatıcı içinden de değiştirilebilir)."),
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
                    title = str(lang, "Altyazı Boyutu"),
                    description = str(lang, "Oynatıcıdaki altyazı yazı boyutu (varsayılan 16)."),
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

                Text(str(lang, "Altyazı Türleri"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "Kapalı altyazılar (CC) ve yayın altyazıları (DVB/PGS) ayrı ayrı kapatılabilir. ") +
                        str(lang, "Kapatılan türler oynatıcıdaki altyazı listesinden de çıkar."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ToggleRow(
                    icon = Icons.Default.Info,
                    title = str(lang, "Kapalı Altyazılar (CC)"),
                    desc = str(lang, "CEA-608/708 — yayın içine gömülü kapalı altyazılar"),
                    checked = "cc" in settings.subtitleTypes,
                    onCheckedChange = { on ->
                        val s = if (on) settings.subtitleTypes + "cc" else settings.subtitleTypes - "cc"
                        vm.saveSettings(settings.copy(subtitleTypes = s))
                    }
                )
                ToggleRow(
                    icon = Icons.Default.Info,
                    title = str(lang, "Yayın Altyazıları (DVB/PGS)"),
                    desc = str(lang, "DVB ve Blu-ray (PGS) gömülü altyazılar"),
                    checked = "dvbsub" in settings.subtitleTypes,
                    onCheckedChange = { on ->
                        val s = if (on) settings.subtitleTypes + "dvbsub" else settings.subtitleTypes - "dvbsub"
                        vm.saveSettings(settings.copy(subtitleTypes = s))
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(str(lang, "Varsayılan Video Kalitesi"), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("auto" to str(lang, "Otomatik"), "1080p" to str(lang, "1080p"), "720p" to str(lang, "720p"), "480p" to str(lang, "480p")).forEach { (key, label) ->
                        GlassChip(
                            selected = settings.defaultQuality == key,
                            onClick = { vm.saveSettings(settings.copy(defaultQuality = key)) },
                            label = label
                        )
                    }
                }

                Text(str(lang, "Çözücü (Decoder)"), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("auto" to str(lang, "Otomatik"), "hardware" to str(lang, "Donanım"), "software" to str(lang, "Yazılım")).forEach { (key, label) ->
                        GlassChip(
                            selected = settings.decoder == key,
                            onClick = { vm.saveSettings(settings.copy(decoder = key)) },
                            label = label
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(str(lang, "Akış Formatı (Zorla)"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "Kanal açılmıyorsa veya takılıyorsa akışı zorla HLS ya da MPEG-TS olarak çözmeyi dener."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("auto" to str(lang, "Otomatik"), "hls" to str(lang, "HLS"), "ts" to str(lang, "MPEG-TS")).forEach { (key, label) ->
                        GlassChip(
                            selected = settings.streamFormat == key,
                            onClick = { vm.saveSettings(settings.copy(streamFormat = key)) },
                            label = label
                        )
                    }
                }

                ToggleRow(
                    icon = Icons.Default.VolumeUp,
                    title = str(lang, "Audio Passthrough"),
                    desc = str(lang, "AC3/EAC3/DTS sesi AV alıcıya ham geçirir. Kapalıyken ses cihazda çözülür."),
                    checked = settings.audioPassthrough,
                    onCheckedChange = { vm.saveSettings(settings.copy(audioPassthrough = it)) }
                )

                Text(str(lang, "Auto Frame Rate (AFR)"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "İçerik kare hızına göre ekran yenileme modunu ayarlar (TV box'larda akıcılık)."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "off" to str(lang, "Kapalı"),
                        "match" to str(lang, "İçeriğe Uy"),
                        "24" to str(lang, "24 Hz"),
                        "25" to str(lang, "25 Hz"),
                        "30" to str(lang, "30 Hz"),
                        "50" to str(lang, "50 Hz"),
                        "60" to str(lang, "60 Hz"),
                        "120" to str(lang, "120 Hz"),
                        "144" to str(lang, "144 Hz")
                    ).forEach { (key, label) ->
                        GlassChip(
                            selected = settings.afrMode == key,
                            onClick = { vm.saveSettings(settings.copy(afrMode = key)) },
                            label = label
                        )
                    }
                }

                Text(str(lang, "A/V Senkron (Ses Gecikmesi)"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "Sesin videoya göre gecikmesi (ms). Pozitif = ses gecikir. Oynatıcı içinden de ayarlanır."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassChip(
                        selected = false,
                        onClick = { vm.saveSettings(settings.copy(audioDelayMs = (settings.audioDelayMs - 50).coerceIn(-500, 500))) },
                        label = str(lang, "−50 ms")
                    )
                    Text(
                        "${if (settings.audioDelayMs > 0) "+" else ""}${settings.audioDelayMs} ms",
                        style = MaterialTheme.typography.titleMedium
                    )
                    GlassChip(
                        selected = false,
                        onClick = { vm.saveSettings(settings.copy(audioDelayMs = (settings.audioDelayMs + 50).coerceIn(-500, 500))) },
                        label = str(lang, "+50 ms")
                    )
                    if (settings.audioDelayMs != 0) {
                        GlassChip(
                            selected = false,
                            onClick = { vm.saveSettings(settings.copy(audioDelayMs = 0)) },
                            label = str(lang, "Sıfırla")
                        )
                    }
                }

                Text(str(lang, "Varsayılan Oynatıcı"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "\"Harici\" seçilirse içerikler sistem oynatıcısında (MXPlayer/VLC vb.) açılır."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("builtin" to str(lang, "Yerleşik (ExoPlayer)"), "external" to str(lang, "Harici Oynatıcı")).forEach { (key, label) ->
                        GlassChip(
                            selected = settings.defaultPlayer == key,
                            onClick = { vm.saveSettings(settings.copy(defaultPlayer = key)) },
                            label = label
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SliderSetting(
                    icon = Icons.Default.Speed,
                    title = str(lang, "İstek Aralığı (Rate Limit)"),
                    description = str(lang, "Stalker portalları ardışık isteklere duyarlıdır. İstekler arası bekleme (ms)."),
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
                    title = str(lang, "Oynatma Tamponu (Buffer)"),
                    description = str(lang, "Canlı yayın takılmalarını azaltmak için tampon süresi (saniye)."),
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
                Text(str(lang, "Cooldown Yönetimi"), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (cooldown > 0) str(lang, "Sunucu istekleri engelledi") + ". " + str(lang, "Kalan süre") + ": ${cooldown}s"
                    else str(lang, "Sunucu engeli yok."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (cooldown > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { vm.clearCooldown() },
                    enabled = cooldown > 0,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(str(lang, "Cooldown'u Temizle")) }

                Text(str(lang, "Zaman Dilimi Ofseti"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "EPG kaymalarını düzeltmek için sunucu ile kendi saatin arasındaki fark (+3, -2 vb.)."),
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
            "appearance" -> {

                Text(str(lang, "Tema"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "AMOLED modda arka plan tam siyahtır (OLED ekranlar için)."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "system" to str(lang, "Sistem"),
                        "light" to str(lang, "Açık"),
                        "dark" to str(lang, "Koyu"),
                        "amoled" to str(lang, "AMOLED")
                    ).forEach { (key, label) ->
                        GlassChip(
                            selected = settings.themeMode == key,
                            onClick = { vm.saveSettings(settings.copy(themeMode = key)) },
                            label = label
                        )
                    }
                }

                Text(str(lang, "Vurgu Rengi"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "Ana aksan rengi; bileşenlerin seçili/kutucuk renklerini etkiler."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        0L to str(lang, "Varsayılan"),
                        0xFF4FC3F7L to str(lang, "Mavi"),
                        0xFF66BB6AL to str(lang, "Yeşil"),
                        0xFFAB47BCL to str(lang, "Mor"),
                        0xFFEF5350L to str(lang, "Kırmızı"),
                        0xFFFFA726L to str(lang, "Turuncu"),
                        0xFFFFCA28L to str(lang, "Altın")
                    ).forEach { (color, label) ->
                        GlassChip(
                            selected = settings.accentColor == color,
                            onClick = { vm.saveSettings(settings.copy(accentColor = color)) },
                            label = label
                        )
                    }
                }

                SliderSetting(
                    icon = Icons.Default.TextFields,
                    title = str(lang, "Yazı Boyutu"),
                    description = str(lang, "Arayüz yazı ölçeği (1.00 = varsayılan)."),
                    value = uiFontScale,
                    valueRange = 0.85f..1.4f,
                    steps = 10,
                    valueText = "%.2f×".format(uiFontScale),
                    onChange = {
                        uiFontScale = it
                        vm.saveSettings(settings.copy(uiFontScale = it))
                    }
                )

                ToggleRow(
                    icon = Icons.Default.PowerSettingsNew,
                    title = str(lang, "Cihaz Açılışında Başlat"),
                    desc = str(lang, "TV box açılınca uygulama otomatik başlar (Açılışta Son Kanalı Oynat ile birlikte kullanılabilir)"),
                    checked = settings.startOnBoot,
                    onCheckedChange = { vm.saveSettings(settings.copy(startOnBoot = it)) }
                )

                Text(str(lang, "Android TV"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "Uygulama TV box launcher'larında görünür ve kumanda ile gezinilebilir. ") +
                        str(lang, "Kanal +/- / medya tuşları zapping için kullanılır (Oynatıcı ayarlarından kapatılabilir)."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(str(lang, "Dil"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "İngilizce şimdilik ana menü ve ayar başlıklarını çevirir (tam çeviri sonraki sürümde)."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("tr" to str(lang, "Türkçe"), "en" to str(lang, "English")).forEach { (key, label) ->
                        GlassChip(
                            selected = settings.language == key,
                            onClick = { vm.saveSettings(settings.copy(language = key)) },
                            label = label
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
            "integrations" -> {
                Text(
                    str(lang, "TMDB (themoviedb.org) anahtarı: oyuncu fotoğrafları, fragman ve gerçek bölüm adları. ") +
                        str(lang, "Boşsa özellikler kapalıdır."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = tmdbKey,
                    onValueChange = { tmdbKey = it },
                    label = { Text(str(lang, "TMDB API Anahtarı")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        vm.saveSettings(settings.copy(tmdbApiKey = tmdbKey.trim()))
                        vm.showMessage(str(lang, "TMDB anahtarı kaydedildi"))
                    },
                    enabled = tmdbKey.trim().isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(str(lang, "Kaydet")) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                testingTmdb = true
                                tmdbTest = null
                                val ok = app.tmdb.testKey(tmdbKey.trim())
                                tmdbTest = if (ok) str(lang, "✓ Anahtar geçerli") else str(lang, "✗ Geçersiz anahtar")
                                testingTmdb = false
                            }
                        },
                        enabled = tmdbKey.trim().isNotEmpty() && !testingTmdb
                    ) { Text(if (testingTmdb) str(lang, "Test ediliyor…") else str(lang, "Anahtarı Test Et")) }
                    OutlinedButton(onClick = {
                        app.tmdb.clearCache()
                        runCatching { coil.Coil.imageLoader(context).memoryCache?.clear() }
                        runCatching { coil.Coil.imageLoader(context).diskCache?.clear() }
                        vm.showMessage(str(lang, "Görsel ve TMDB önbelleği temizlendi"))
                    }) { Text(str(lang, "Önbelleği Temizle")) }
                }
                if (tmdbTest != null) {
                    Text(
                        tmdbTest.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (tmdbTest?.startsWith("✓") == true) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(str(lang, "TMDB Dili"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "Başlık, fragman ve oyuncu bilgilerinin dili (posterler etkilenmez)."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("tr" to str(lang, "Türkçe"), "en" to str(lang, "English")).forEach { (key, label) ->
                        GlassChip(
                            selected = settings.tmdbLanguage == key,
                            onClick = { vm.saveSettings(settings.copy(tmdbLanguage = key)) },
                            label = label
                        )
                    }
                }
                ToggleRow(
                    icon = Icons.Default.PlayArrow,
                    title = str(lang, "Fragmanlar"),
                    desc = str(lang, "Detay ekranında TMDB fragmanlarını göster"),
                    checked = settings.tmdbTrailers,
                    onCheckedChange = { vm.saveSettings(settings.copy(tmdbTrailers = it)) }
                )
                ToggleRow(
                    icon = Icons.Default.Person,
                    title = str(lang, "Oyuncu Fotoğrafları"),
                    desc = str(lang, "Detay ekranında TMDB oyuncu görsellerini kullan"),
                    checked = settings.tmdbPeople,
                    onCheckedChange = { vm.saveSettings(settings.copy(tmdbPeople = it)) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                Text(str(lang, "OpenSubtitles.com"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "OpenSubtitles REST API anahtarı. Boş bırakırsanız uygulamanın sınırlı varsayılan anahtarı kullanılır."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                var osKey by remember(settings.openSubtitlesApiKey) { mutableStateOf(settings.openSubtitlesApiKey) }
                OutlinedTextField(
                    value = osKey,
                    onValueChange = { osKey = it },
                    label = { Text(str(lang, "API Anahtarı")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        vm.saveSettings(settings.copy(openSubtitlesApiKey = osKey.trim()))
                        vm.showMessage(str(lang, "OpenSubtitles anahtarı kaydedildi"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(str(lang, "Kaydet")) }

            }
            "network" -> {
                Text(str(lang, "DNS üzerinden HTTPS (DoH)"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "İnternet sağlayıcısı DNS bazlı engelleme yapıyorsa DoH ile bu engelleri aşabilirsiniz."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ToggleRow(
                    icon = Icons.Default.Security,
                    title = str(lang, "DoH Etkinleştir"),
                    desc = str(lang, "Özel DNS sunucusunu kullan"),
                    checked = settings.dohEnabled,
                    onCheckedChange = { vm.saveSettings(settings.copy(dohEnabled = it)) }
                )
                if (settings.dohEnabled) {
                    var dohUrl by remember(settings.dohUrl) { mutableStateOf(settings.dohUrl) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "https://cloudflare-dns.com/dns-query" to "Cloudflare",
                            "https://dns.google/dns-query" to "Google"
                        ).forEach { (url, label) ->
                            GlassChip(
                                selected = dohUrl == url,
                                onClick = { dohUrl = url; vm.saveSettings(settings.copy(dohUrl = url)) },
                                label = label
                            )
                        }
                    }
                    OutlinedTextField(
                        value = dohUrl,
                        onValueChange = { dohUrl = it },
                        label = { Text(str(lang, "DoH Sunucu URL")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { vm.saveSettings(settings.copy(dohUrl = dohUrl.trim())) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(str(lang, "URL'yi Kaydet")) }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                Text(str(lang, "SOCKS5 Proxy"), style = MaterialTheme.typography.titleSmall)
                Text(
                    str(lang, "Tüm bağlantıyı bir SOCKS5 proxy üzerinden geçirir. Boş bırakırsanız proxy kullanılmaz."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                var socksProxy by remember(settings.socksProxy) { mutableStateOf(settings.socksProxy) }
                var socksPort by remember(settings.socksPort) { mutableStateOf(settings.socksPort.toString()) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = socksProxy,
                        onValueChange = { socksProxy = it },
                        label = { Text(str(lang, "Proxy Adresi (örn. 192.168.1.50)")) },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = socksPort,
                        onValueChange = { socksPort = it },
                        label = { Text(str(lang, "Port")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    onClick = {
                        val portInt = socksPort.toIntOrNull() ?: 0
                        vm.saveSettings(settings.copy(socksProxy = socksProxy.trim(), socksPort = portInt))
                        vm.showMessage(str(lang, "Proxy ayarları kaydedildi"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(str(lang, "Kaydet")) }
            }
            "cache" -> {
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
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
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
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
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
                                // Önce bu hesabın son verisini buluta yaz, sonra çık.
                                firebase.pushBackup(vm.store)
                                firebase.signOut()
                                // Misafir depoya dön (oturum kapalıyken görünen veri temiz kalır).
                                vm.store.setAccount(null)
                                vm.refreshFlows()
                                vm.showMessage(t("Çıkış yapıldı"))
                                // signOut tamamlandıktan sonra çağrılır — yönlendirme
                                // artık oturumun kapalı olduğunu görüp Giriş ekranına gider.
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
                    onClick = { restoreLauncher.launch(arrayOf("text/*", "application/json")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(t("Yedekten Geri Yükle")) }
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
            "privacy" -> {
                Text(
                    if (FirebaseSyncManager.instance.isSignedIn) {
                        t("Verilerin, oturum açtığın Google/Firebase hesabının bulut depolamasında saklanır ve cihazlar arasında senkronlanır. Hesaptan çıkınca bulut kopyan kalır.")
                    } else {
                        t("Tüm verilerin (portallar, izleme geçmişi, listeler) yalnızca bu cihazda saklanır. Hesap oluşturup giriş yaparsan verilerin buluta senkronlanır.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { showPrivacy = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(t("Gizlilik Anlaşması'nı Oku")) }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SectionHeader(Icons.Default.Lock, t("PIN Kilidi"))
                Text(
                    t("Ayarlara giriş için 4 haneli PIN istenir. PIN boş bırakılırsa kilit kaldırılır. PIN unutulursa tek çözüm tüm verileri sıfırlamaktır."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pinNew,
                    onValueChange = { if (it.length == 4 && it.all(Char::isDigit)) pinNew = it },
                    label = { Text(t("4 haneli PIN")) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        vm.saveSettings(settings.copy(pin = pinNew))
                        vm.showMessage(if (pinNew.isBlank()) t("PIN kilidi kaldırıldı") else t("PIN kaydedildi"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(t("PIN'i Kaydet")) }
            }
            "about" -> {
                Text("Portio v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                Text(
                    t("Stalker portal, M3U ve Xtream Codes destekli IPTV oynatıcı — tüm içeriğin tek uygulamada."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { checkForUpdate() }, enabled = !checkingUpdate) {
                        Text(if (checkingUpdate) t("Kontrol ediliyor…") else t("Güncelleme Kontrol Et"))
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
                    t("Sürüm") + " ${BuildConfig.VERSION_NAME} (" + t("kod") + " ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { showLicense = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(t("Lisans & Açık Kaynak")) }
                if (showLicense) {
                    AlertDialog(
                        onDismissRequest = { showLicense = false },
                        confirmButton = { TextButton(onClick = { showLicense = false }) { Text(str(lang, "Kapat")) } },
                        title = { Text(str(lang, "Lisans")) },
                        text = {
                            Text(
                                str(lang, "Bu uygulama kişisel kullanım için geliştirilmiştir. ") +
                                    str(lang, "Stalker portal, M3U ve Xtream Codes destekli bir IPTV oynatıcıdır. ") +
                                    str(lang, "Hiçbir içerik uygulama tarafından barındırılmaz; yalnızca kullanıcının ") +
                                    str(lang, "eklediği kaynaklar oynatılır. Tüm veriler cihazda saklanır.\n\n") +
                                    str(lang, "Açık kaynak bileşenler: ExoPlayer (media3), Coil, OkHttp, kotlinx.serialization.")
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
            lang = lang,
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
            lang = lang,
            initial = editingM3u,
            onDismiss = { showM3uDialog = false },
            onSave = { source ->
                vm.saveM3uSource(source)
                showM3uDialog = false
                if (editingM3u == null) {
                    vm.setActiveSource("m3u", source.id)
                    vm.showMessage(str(lang, "M3U kaynağı eklendi — Canlı TV'de yükleniyor"))
                }
            }
        )
    }

    if (showXtreamDialog) {
        XtreamDialog(
            lang = lang,
            initial = editingXtream,
            onDismiss = { showXtreamDialog = false },
            onSave = { source ->
                vm.saveXtreamSource(source)
                showXtreamDialog = false
                if (editingXtream == null) {
                    vm.setActiveSource("xtream", source.id)
                    vm.showMessage(str(lang, "Xtream kaynağı eklendi — Canlı TV'de yükleniyor"))
                }
            }
        )
    }

    if (showPrivacy) {
        PrivacyDialog(lang = lang, onDismiss = { showPrivacy = false })
    }

    updateDialog?.let { info ->
        UpdateDialog(
            info = info,
            lang = lang,
            onDismiss = { updateDialog = null },
            onUpdateNow = {
                updateDialog = null
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
                }
            },
            onRemindLater = {
                updateDialog = null
                vm.store.setUpdateRemindTs(System.currentTimeMillis() + 24 * 60 * 60 * 1000L)
            },
            onNeverAsk = {
                updateDialog = null
                vm.store.setUpdateSkipVersion(info.version)
                vm.showMessage(str(lang, "Bu sürüm için bir daha sorulmayacak"))
            }
        )
    }
}

// ---------- Yardımcı bileşenler ----------

/** Ayar satırı: her satır kendi kartında, ikon + başlık + açıklama + anahtar. */
@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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

/**
 * Bölüm sayfası kabuğu: geri oku + ikonlu başlık + açıklama + kaydırılabilir
 * içerik kartı. İçerik tek büyük kart yerine bölümlere ayrılmış kartlardan
 * oluşur (her ayar grubu kendi kartında — daha okunaklı ve kullanışlı).
 */
@Composable
private fun SettingsPage(
    lang: String,
    icon: ImageVector,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
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
        // Sabit başlık satırı: geri oku + ikonlu büyük başlık.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = str(lang, "Geri"), modifier = Modifier.size(24.dp))
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        HorizontalDivider()
        // Bölüm içerikleri: her mantıksal grup kendi kartında görünür.
        // (Bölümler zaten SectionHeader ile ayrılmış — kartlar arası boşluk
        // okunabilirliği artırır.)
        content()
        Spacer(modifier = Modifier.height(96.dp))
    }
}

/** PIN kilit ekranı: ayarlara giriş yalnızca doğru PIN ile açılır. */
@Composable
private fun PinLockOverlay(
    modifier: Modifier = Modifier,
    lang: String,
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
        Text(str(lang, "PIN Kilidi"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            str(lang, "Ayarlara erişmek için PIN'i gir."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
            label = { Text(str(lang, "4 haneli PIN")) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = error,
            supportingText = if (error) {
                { Text(str(lang, "Yanlış PIN"), color = MaterialTheme.colorScheme.error) }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onUnlock(pin) },
            enabled = pin.length == 4,
            modifier = Modifier.fillMaxWidth()
        ) { Text(str(lang, "Kilidi Aç")) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onResetRequest) {
            Text(str(lang, "PIN'i unuttum — tüm verileri sıfırla"), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SourceGroupTitle(lang: String, title: String, isActive: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (isActive) {
            Text(str(lang, "● Aktif"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SourceRow(
    lang: String,
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
                Text(str(lang, "Test ediliyor…"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (testState != null) {
            val ok = testState == "ok"
            Text(
                if (ok) str(lang, "✓ Bağlantı başarılı") else "✗ $testState",
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
                    Text(if (refreshing) "…" else str(lang, "Yenile"))
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
                Text(str(lang, "Düzenle"))
            }
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                Text(str(lang, "Sil"))
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
private fun PrivacyDialog(lang: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(str(lang, "Kapat")) } },
        title = { Text(str(lang, "Gizlilik Anlaşması")) },
        text = {
            Text(
                str(lang, "1. Kaynak bilgilerin (portal URL, MAC, kullanıcı adı/şifre) yalnızca bu cihazda saklanır ") +
                    str(lang, "ve yalnızca kendi IPTV sağlayıcına gönderilir.\n\n") +
                    str(lang, "2. Hesap oluşturup giriş yaparsan verilerin (kaynaklar, ayarlar, favoriler, izleme geçmişi) ") +
                    str(lang, "Firebase hesabının bulut depolamasına yedeklenir ve cihazlar arasında senkronlanır.\n\n") +
                    str(lang, "3. İzleme verileri üçüncü taraflarla paylaşılmaz (uygulama aracılığıyla değil).\n\n") +
                    str(lang, "4. TMDB anahtarını kendin eklersen, zenginleştirme istekleri doğrudan themoviedb.org'a gider.\n\n") +
                    str(lang, "5. Uygulamayı kaldırdığında cihazdaki tüm veriler silinir; bulut yedeği hesapta kalır."),
                style = MaterialTheme.typography.bodySmall
            )
        }
    )
}

@Composable
private fun PortalEditDialog(
    lang: String,
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
    
    // MAC Profiles listesi dialogu için
    var showMacProfilesDialog by remember { mutableStateOf(false) }

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
            }) { Text(str(lang, "Kaydet")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(lang, "İptal")) } },
        title = { Text(if (initial == null) str(lang, "Yeni Stalker Portal") else str(lang, "Portal Düzenle")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(str(lang, "İsim")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Portal URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(value = mac, onValueChange = { mac = it }, label = { Text(str(lang, "MAC (boş olabilir)")) }, singleLine = true, modifier = Modifier.weight(1f))
                    if (initial != null) {
                        IconButton(onClick = { showMacProfilesDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = str(lang, "MAC Profilleri"))
                        }
                    }
                }
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text(str(lang, "Kullanıcı adı (opsiyonel)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(str(lang, "Şifre (opsiyonel)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    )

    if (showMacProfilesDialog && initial != null) {
        var newMacName by remember { mutableStateOf("") }
        var newMacAddress by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMacProfilesDialog = false },
            confirmButton = { TextButton(onClick = { showMacProfilesDialog = false }) { Text(str(lang, "Kapat")) } },
            title = { Text(str(lang, "MAC Profilleri")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        str(lang, "Bu portala ait farklı MAC adresleri kaydedip hızlıca geçiş yapabilirsiniz."),
                        style = MaterialTheme.typography.bodySmall
                    )
                    initial.macProfiles.forEach { profile ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(profile.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(profile.mac, style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = {
                                mac = profile.mac
                                showMacProfilesDialog = false
                            }) { Text(str(lang, "Seç")) }
                            IconButton(onClick = {
                                val newList = initial.macProfiles.filter { it.mac != profile.mac }
                                onSave(initial.copy(macProfiles = newList))
                            }) { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(str(lang, "Yeni MAC Profili Ekle"), style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = newMacName, onValueChange = { newMacName = it }, label = { Text(str(lang, "Profil Adı")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = newMacAddress, onValueChange = { newMacAddress = it }, label = { Text(str(lang, "MAC Adresi")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        if (newMacName.isNotBlank() && newMacAddress.isNotBlank()) {
                            val profile = com.stalkerapp.data.MacProfile(name = newMacName.trim(), mac = newMacAddress.trim())
                            val newList = initial.macProfiles + profile
                            onSave(initial.copy(macProfiles = newList))
                            newMacName = ""
                            newMacAddress = ""
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(str(lang, "Ekle")) }
                }
            }
        )
    }
}

@Composable
private fun M3uDialog(
    lang: String,
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
                if (!trimmed.startsWith("http")) { error = str(lang, "Geçerli bir http(s) URL girin"); return@TextButton }
                val id = initial?.id ?: ("m3u_" + trimmed.hashCode().toString() + System.currentTimeMillis().toString().takeLast(4))
                onSave(
                    M3uSource(
                        id = id,
                        name = name.ifBlank { trimmed },
                        url = trimmed,
                        content = content.trim()
                    )
                )
            }) { Text(str(lang, "Kaydet")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(lang, "İptal")) } },
        title = { Text(if (initial == null) str(lang, "M3U Ekle") else str(lang, "M3U Düzenle")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    str(lang, "M3U listesinin adresini girin (#EXTM3U içeren dosya). Kanal kategorileri group-title'dan; ") +
                        str(lang, "\"dizi/series\" grubu Diziler, diğerleri Filmler sekmesinde görünür. İstersen listeyi ") +
                        str(lang, "doğrudan İçerik alanına da yapıştırabilirsin (URL boşsa bu içerik kullanılır)."),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(str(lang, "İsim")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("M3U URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(str(lang, "İçerik (isteğe bağlı — #EXTM3U metni)")) },
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
    lang: String,
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
                if (!srv.startsWith("http")) { error = str(lang, "Geçerli bir http(s) sunucu adresi girin"); return@TextButton }
                if (username.trim().isBlank()) { error = str(lang, "Kullanıcı adı gerekli"); return@TextButton }
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
                        error = str(lang, "Xtream doğrulaması başarısız — sunucu, kullanıcı adı veya şifre hatalı")
                    }
                }
            }) { Text(if (checking) str(lang, "Kontrol ediliyor…") else str(lang, "Kaydet")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(lang, "İptal")) } },
        title = { Text(if (initial == null) str(lang, "Xtream Ekle") else str(lang, "Xtream Düzenle")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    str(lang, "Xtream Codes sunucusu, kullanıcı adı ve şifre girin (ör: http://sunucu:8080). ") +
                        str(lang, "Kaydetmeden önce doğrulanır."),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(str(lang, "İsim")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = server, onValueChange = { server = it }, label = { Text(str(lang, "Sunucu (http://host:port)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text(str(lang, "Kullanıcı adı")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(str(lang, "Şifre")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    )
}

