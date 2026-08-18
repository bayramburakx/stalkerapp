package com.stalkerapp.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.content.pm.ActivityInfo
import android.view.WindowManager
import java.io.File
import kotlin.math.abs
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tv
import com.stalkerapp.StalkerApp
import com.stalkerapp.cast.CastManager
import com.stalkerapp.data.Channel
import com.stalkerapp.data.EpgReminder
import com.stalkerapp.data.Profile
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxHeight
import com.stalkerapp.playback.ChannelQueue
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.playback.VodQueue
import com.stalkerapp.ui.cast.CastDialog
import com.stalkerapp.ui.components.ChannelLogo
import com.stalkerapp.ui.components.ChannelRow
import com.stalkerapp.ui.components.resolveUrl
import com.stalkerapp.util.Afr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val L10nLocal: Map<String, String> = mapOf(
    // Türkçe -> English
    "Yayınlanıyor…" to "Streaming…",
    "TV'de yayınlanıyor" to "Streaming on TV",
    "Parlaklık" to "Brightness",
    "Ses" to "Audio",
    "Geri" to "Back",
    "Kanal" to "Channel",
    "Favori" to "Favorite",
    "Uyku zamanlayıcısı" to "Sleep timer",
    "Oynatıcı seç" to "Choose player",
    "Harici oynatıcı" to "External player",
    "Kilit" to "Lock",
    "PiP (Resim içinde resim)" to "PiP (Picture in picture)",
    "Bilgi" to "Info",
    "Ayarlar" to "Settings",
    "10 sn Geri" to "10 sec Back",
    "Oynat/Duraklat" to "Play/Pause",
    "10 sn İleri" to "10 sec Forward",
    "Ses Dili" to "Audio Language",
    "Altyazı" to "Subtitles",
    "Sonraki Bölüm" to "Next Episode",
    "● CANLI" to "● LIVE",
    "(timeshift: geri sarılabilir)" to "(timeshift: rewindable)",
    "Canlıya Dön" to "Return to Live",
    "Önceki" to "Previous",
    "30 sn geri sar" to "Rewind 30 sec",
    "Sonraki" to "Next",
    "Rehber" to "Guide",
    "Kanallar" to "Channels",
    "Kiliti aç" to "Unlock",
    "Bölüm sonu" to "End of episode",
    "Uyku Zamanlayıcısı" to "Sleep Timer",
    "Aktif: " to "Active: ",
    "Bölüm sonunda dur" to "Stop at end of episode",
    "kaldı" to "left",
    "15 dk" to "15 min",
    "30 dk" to "30 min",
    "1 saat" to "1 hour",
    "1.5 saat" to "1.5 hours",
    "Geçerli bölüm/medya bitince kapanır" to "Turns off when the current episode/media ends",
    "Kapat" to "Close",
    "Varsayılan (Otomatik)" to "Default (Automatic)",
    "Ses izi bulunamadı" to "No audio track found",
    "Altyazılar" to "Subtitles",
    "Altyazı yok (Kapat)" to "No subtitles (Off)",
    "● ŞİMDİ" to "● NOW",
    "🔔 Hatırlatma ayarlandı (dokun: kaldır)" to "🔔 Reminder set (tap to remove)",
    "🔔 Başlayınca Bildir" to "🔔 Notify When It Starts",
    "⏺ Kayıt planlandı (dokun: iptal)" to "⏺ Recording scheduled (tap to cancel)",
    "⏺ Kaydet (cihaza)" to "⏺ Record (to device)",
    "▶ Geçmiş Yayını İzle (catch-up)" to "▶ Watch Past Broadcast (catch-up)",
    "EPG verisi yok" to "No EPG data",
    "%.1f sn" to "%.1f s",
    "Sığdır" to "Fit",
    "Doldur" to "Fill",
    "Yakınlaştır" to "Zoom",
    "Oynatma Hızı" to "Playback Speed",
    "Normal" to "Normal",
    "Görüntü Oranı" to "Aspect Ratio",
    "A/V Senkron (Ses Gecikmesi)" to "A/V Sync (Audio Delay)",
    "Pozitif = ses gecikir, negatif = ses öne alınır." to "Positive = audio is delayed, negative = audio is ahead.",
    "Sıfırla (0 ms)" to "Reset (0 ms)",
    "Binge Modu" to "Binge Mode",
    "Bölüm bitince sıradaki bölüm otomatik oynatılır" to "When an episode ends, the next episode plays automatically",
    "Kanal ara…" to "Search channels…",
    "Kanal akış URL'si boş" to "Channel stream URL is empty",
    "Akış URL'si boş" to "Stream URL is empty",
    "Harici oynatıcı bulunamadı (video desteği olan bir uygulama yükleyin)" to "No external player found (install an app that supports video)",
    "Oynatma hatası" to "Playback error"
)
private fun str(lang: String, text: String): String =
    if (lang == "en") L10nLocal[text] ?: text else text

private enum class GestureMode { BRIGHTNESS, VOLUME, SEEK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(navController: NavHostController) {
    val context = LocalContext.current
    val activity = context as? Activity
    val app = context.applicationContext as StalkerApp
    val vm: MainViewModel = rememberMainViewModel(app)
    val profile = vm.repository.cachedProfile()
    val lang = vm.store.settings().language

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var overlayVisible by remember { mutableStateOf(true) }
    var currentChannel by remember { mutableStateOf(ChannelQueue.current) }
    val favChannels by vm.favoriteChannels.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isFav = remember(favChannels, currentChannel) {
        currentChannel != null && favChannels.any { it.id == currentChannel?.id }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var showTracks by remember { mutableStateOf(false) }
    var showSubs by remember { mutableStateOf(false) }
    var showEpg by remember { mutableStateOf(false) }
    // Uyku zamanlayıcısı: kalan saniye (0=kapalı, -1=bölüm sonu).
    var sleepRemaining by remember { mutableStateOf(PlaybackManager.sleepTimerRemainingSec()) }
    var showSleepDialog by remember { mutableStateOf(false) }
    // Canlı yayında timeshift: akış geri sarılabilir mi?
    var liveSeekable by remember { mutableStateOf(false) }

    // -------- Intro atlama --------
    var introRange by remember { mutableStateOf<com.stalkerapp.playback.IntroDetector.IntroRange?>(null) }
    var showSkipIntro by remember { mutableStateOf(false) }
    var showSkipOutro by remember { mutableStateOf(false) }

    // -------- Numara tuşu ile kanal (Android TV) --------
    var numpadBuffer by remember { mutableStateOf("") }
    var numpadVisible by remember { mutableStateOf(false) }

    // -------- Catch-up takvim --------
    var showCatchupCalendar by remember { mutableStateOf(false) }

    // -------- OpenSubtitles altyazı arama --------
    var showSubtitleSearch by remember { mutableStateOf(false) }

    val subtitleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // Ignore if provider doesn't support persistable permission
            }
            PlaybackManager.setExternalSubtitle(uri)
        }
    }
    var showInfo by remember { mutableStateOf(false) }
    var showPlayerSettings by remember { mutableStateOf(false) }
    var showChannels by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(nowTime()) }
    var battery by remember { mutableStateOf(100) }
    var playbackSpeed by remember { mutableStateOf(app.store.settings().playbackSpeed.coerceIn(0.5f, 2f)) }
    var aspectMode by remember { mutableStateOf(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }
    val isLive = !PlaybackManager.isVod()

    // Chromecast durumu ve keşfedilen yayın cihazları.
    var isCasting by remember { mutableStateOf(PlaybackManager.isCasting()) }
    var castDialogVisible by remember { mutableStateOf(false) }
    val castRoutes by CastManager.routes.collectAsStateWithLifecycle()

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val audioMax = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1
    var volume by remember {
        mutableStateOf(audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0)
    }
    val initBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
    var brightness by remember { mutableStateOf(if (initBrightness < 0f) 0.5f else initBrightness) }
    var gestureMode by remember { mutableStateOf<GestureMode?>(null) }
    var gestureStartPos by remember { mutableStateOf(0L) }
    var gestureStartX by remember { mutableStateOf(0f) }
    var gestureStartY by remember { mutableStateOf(0f) }
    var gestureStartVolume by remember { mutableStateOf(0) }
    var gestureStartBrightness by remember { mutableStateOf(0.5f) }
    var gestureText by remember { mutableStateOf<String?>(null) }

    val playerView = remember {
        PlayerView(context).apply {
            useController = false
            // Altyazı boyutu (Ayarlar → Oynatıcı → Altyazı Boyutu).
            subtitleView?.apply {
                setStyle(CaptionStyleCompat(
                    CaptionStyleCompat.DEFAULT.foregroundColor,
                    CaptionStyleCompat.DEFAULT.backgroundColor,
                    CaptionStyleCompat.DEFAULT.windowColor,
                    CaptionStyleCompat.DEFAULT.edgeType,
                    CaptionStyleCompat.DEFAULT.edgeColor,
                    CaptionStyleCompat.DEFAULT.typeface
                ))
                setFixedTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    app.store.settings().subtitleSize.coerceIn(10, 32).toFloat()
                )
            }
        }
    }

    DisposableEffect(Unit) {
        val listener: (Player?) -> Unit = { p ->
            playerView.player = p
            currentChannel = ChannelQueue.current
        }
        PlaybackManager.addPlayerListener(listener)
        playerView.player = PlaybackManager.displayPlayer()
        onDispose {
            PlaybackManager.removePlayerListener(listener)
        }
    }

    val stateListener: (Boolean, Boolean) -> Unit = { playing, buffering ->
        isPlaying = playing
        isBuffering = buffering
    }
    DisposableEffect(Unit) {
        PlaybackManager.addStateListener(stateListener)
        onDispose { PlaybackManager.removeStateListener(stateListener) }
    }

    DisposableEffect(Unit) {
        val el: (String?) -> Unit = { error = it }
        PlaybackManager.addErrorListener(el)
        onDispose { PlaybackManager.removeErrorListener(el) }
    }

    DisposableEffect(Unit) {
        val cl: (Boolean) -> Unit = { isCasting = it }
        PlaybackManager.addCastListener(cl)
        onDispose { PlaybackManager.removeCastListener(cl) }
    }

    // Uyku zamanlayıcısı kalan süresi (oynatıcıda chip olarak gösterilir).
    DisposableEffect(Unit) {
        val sl: (Long) -> Unit = { sleepRemaining = it }
        PlaybackManager.addSleepListener(sl)
        onDispose { PlaybackManager.removeSleepListener(sl) }
    }

    // Yayın dialog'u açıkken cihaz ara; kapandığında taramayı durdur (pil tasarrufu).
    LaunchedEffect(castDialogVisible) {
        if (castDialogVisible) {
            CastManager.startDiscovery()
        } else {
            CastManager.stopDiscovery()
        }
    }

    fun exitPlayer() {
        // Önce son konumu KAYDET + ana sayfayı tazele: stop() oynatıcıyı release
        // eder, bu yüzden onDispose'daki kayıt çalışmaz (player null olur).
        // Kayıtsız çıkışta "İzlemeye Devam / Son İzlenenler" boş kalıyordu.
        if (PlaybackManager.player != null && PlaybackManager.isVod() && PlaybackManager.currentVodId != 0L) {
            PlaybackManager.saveProgressBeforeExit()
            vm.bumpWatched()
        }
        PlaybackManager.stop()
        if (!navController.popBackStack()) {
            navController.navigate("home") {
                popUpTo("login") { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    // Varsayılan oynatıcı "Harici" ise içerik sistem oynatıcısında açılır; bu
    // ekran yalnızca akış çözülürken açık kalır, içerik gönderilince kapanır.
    LaunchedEffect(Unit) {
        if (app.store.settings().defaultPlayer == "external") {
            var waited = 0
            while (waited < 8000 && !PlaybackManager.hasExternalLaunch()) {
                delay(100)
                waited += 100
            }
            if (PlaybackManager.consumeExternalLaunch()) {
                exitPlayer()
            }
        }
    }

    // Auto Frame Rate (AFR): içerik kare hızına göre ekran yenileme modunu
    // ayarlar (Ayarlar → Oynatıcı). Çıkışta varsayılan moda döner.
    LaunchedEffect(settings.afrMode) {
        while (true) {
            val p = PlaybackManager.player
            val fps = p?.videoFormat?.frameRate ?: 0f
            // API 30+: Surface.setFrameRate ile de sinyalle (120/144 Hz dahil).
            val surface = (playerView.videoSurfaceView as? android.view.SurfaceView)?.holder?.surface
            Afr.apply(activity, settings.afrMode, fps, surface)
            delay(2000)
        }
    }

    DisposableEffect(Unit) {
        onDispose { Afr.clear(activity) }
    }

    // -------- Intro atlama tespiti --------
    // VOD bölümü oynatılırken TMDB ve akıllı tahminle intro aralığını çek.
    LaunchedEffect(PlaybackManager.currentVodId, VodQueue.season, VodQueue.current?.episodeNumber) {
        introRange = null
        showSkipIntro = false
        showSkipOutro = false
        if (!PlaybackManager.isVod()) return@LaunchedEffect
        val cur = VodQueue.current ?: return@LaunchedEffect
        if (!settings.skipIntroEnabled && !settings.skipOutroEnabled) return@LaunchedEffect
        val tmdbId = PlaybackManager.currentVodItem?.tmdbId ?: 0L
        val key = app.store.settings().tmdbApiKey
        val dur = runCatching {
            var waited = 0
            while (PlaybackManager.player?.duration?.let { it <= 0 } != false && waited < 5000) {
                delay(300); waited += 300
            }
            PlaybackManager.player?.duration ?: 0L
        }.getOrDefault(0L)
        val sNum = VodQueue.season.toInt().coerceAtLeast(1)
        val epNum = cur.episodeNumber.coerceAtLeast(1)
        introRange = com.stalkerapp.playback.IntroDetector.detect(
            tmdbId = tmdbId,
            season = sNum,
            episode = epNum,
            apiKey = key,
            durationMs = dur
        )
    }

    // Intro/outro aralığında "Atla" butonunu göster
    LaunchedEffect(position, introRange) {
        val range = introRange ?: return@LaunchedEffect
        showSkipIntro = settings.skipIntroEnabled &&
            position >= range.startMs && position < range.endMs
        showSkipOutro = settings.skipOutroEnabled && range.outroStartMs > 0 &&
            position >= range.outroStartMs
    }

    // -------- Numara tuşu ile kanal (Android TV) --------
    LaunchedEffect(numpadBuffer) {
        if (numpadBuffer.isBlank()) return@LaunchedEffect
        delay(1500) // 1.5 sn bekle — kullanıcı daha fazla rakam girebilir
        val channelNum = numpadBuffer.toIntOrNull()
        if (channelNum != null) {
            val idx = ChannelQueue.channels.indexOfFirst { it.number == channelNum }
            if (idx >= 0) {
                val ch = ChannelQueue.channels.getOrNull(idx) ?: return@LaunchedEffect
                PlaybackManager.playChannel(ChannelQueue.channels, idx, ChannelQueue.profile)
                currentChannel = ch
                error = PlaybackManager.errorMessage
            }
        }
        numpadBuffer = ""
        numpadVisible = false
    }

    BackHandler(enabled = true) {
        when {
            showTracks -> showTracks = false
            showSubs -> showSubs = false
            showEpg -> showEpg = false
            showInfo -> showInfo = false
            showPlayerSettings -> showPlayerSettings = false
            showChannels -> showChannels = false
            else -> exitPlayer()
        }
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Ayarlardan kapatılabilir: oynatıcıda ekran uyusun mu?
        if (app.store.settings().keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, true) }
        }
    }

    // Oynatıcı yönü (Oynatıcı ayarları): otomatik (sensör yatay) / sabit yatay / serbest.
    val orientationMode = remember { app.store.settings().playerOrientation }
    DisposableEffect(Unit) {
        activity?.requestedOrientation = when (orientationMode) {
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "sensor" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Parlaklık jestiyle değiştirilen ekran parlaklığını çıkışta orijinaline döndür.
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                act.window.attributes = act.window.attributes.apply {
                    screenBrightness = initBrightness
                }
            }
        }
    }

    // Kontrol gizleme süresi (Oynatıcı ayarları → 3/5/10 sn).
    val controlsTimeoutMs = remember {
        app.store.settings().controlsTimeoutSec.coerceIn(3, 10) * 1000L
    }
    LaunchedEffect(overlayVisible) {
        if (overlayVisible && !locked) {
            delay(controlsTimeoutMs)
            overlayVisible = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            clock = nowTime()
            delay(1000)
        }
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                battery = if (scale > 0) (level * 100 / scale) else level
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(aspectMode) {
        playerView.resizeMode = aspectMode
    }

    LaunchedEffect(playbackSpeed) {
        PlaybackManager.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(Unit) {
        while (true) {
            val p = PlaybackManager.displayPlayer()
            if (!seeking && p != null) {
                position = p.currentPosition
                duration = if (p.duration > 0) p.duration else 0L
            }
            // Canlı yayın geri sarılabilir mi? (timeshift: sunucu destekliyorsa)
            liveSeekable = p?.isCurrentWindowSeekable == true && !PlaybackManager.isVod()
            delay(300)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            val p = PlaybackManager.displayPlayer()
            if (p != null && PlaybackManager.isVod() && PlaybackManager.currentVodId != 0L) {
                val pos = p.currentPosition
                val dur = p.duration
                // Süre bilinmiyorsa (C.TIME_UNSET) bile ilerleme kaydedilir:
                // aksi halde süresi çözülemeyen akışlarda (ör. .ts) "kaldığı
                // yerden devam" için hiç veri kaydedilmezdi.
                if (pos > 0) {
                    val cur = VodQueue.current
                    if (cur != null) {
                        // Bölüm bazlı ilerleme (devam et / binge için).
                        app.store.saveEpisodeProgress(
                            "${PlaybackManager.currentVodId}:${VodQueue.season}:${cur.episodeNumber}",
                            pos, if (dur > 0) dur else 0L,
                            PlaybackManager.currentVodItem,
                            "S${VodQueue.season}E${cur.episodeNumber}"
                        )
                    } else if (dur <= 0 || pos < dur * 0.95) {
                        app.store.saveVodProgress(
                            PlaybackManager.currentVodId, pos, if (dur > 0) dur else 0L, PlaybackManager.currentVodItem
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val p = PlaybackManager.displayPlayer()
            if (p != null && PlaybackManager.isVod() && PlaybackManager.currentVodId != 0L) {
                val pos = p.currentPosition
                val dur = p.duration
                if (pos > 0) {
                    val cur = VodQueue.current
                    if (cur != null) {
                        app.store.saveEpisodeProgress(
                            "${PlaybackManager.currentVodId}:${VodQueue.season}:${cur.episodeNumber}",
                            pos, if (dur > 0) dur else 0L,
                            PlaybackManager.currentVodItem,
                            "S${VodQueue.season}E${cur.episodeNumber}"
                        )
                    } else {
                        app.store.saveVodProgress(
                            PlaybackManager.currentVodId, pos, if (dur > 0) dur else 0L, PlaybackManager.currentVodItem
                        )
                    }
                    // Ana sayfa/Kütüphane "İzlemeye Devam" & "Son İzlenenler"
                    // listelerinin tazelenmesi için watchedVersion'ı arttır.
                    vm.bumpWatched()
                }
            }
        }
    }

    val queueChannels = ChannelQueue.channels
    val queueProfile = ChannelQueue.profile

    fun switchTo(index: Int) {
        val ch = queueChannels.getOrNull(index) ?: return
        // Profil Stalker dışı (Xtream/M3U) kaynaklarda null olabilir; playChannel
        // zaten Profile? kabul eder (cmd doğrudan oynatılabilir URL'dir).
        PlaybackManager.playChannel(queueChannels, index, queueProfile)
        currentChannel = ch
        error = PlaybackManager.errorMessage
    }

    // Kumanda tuşları: kanal +/- ve medya sonraki/önceki ile zapping (Android TV).
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown || !settings.remoteChannelKeys) return@onKeyEvent false
                when (ev.key) {
                    Key.ChannelUp -> {
                        if (ChannelQueue.index > 0) switchTo(ChannelQueue.index - 1)
                        true
                    }
                    Key.ChannelDown -> {
                        if (ChannelQueue.index + 1 < ChannelQueue.channels.size) switchTo(ChannelQueue.index + 1)
                        true
                    }
                    Key.MediaPrevious -> {
                        if (ChannelQueue.index > 0) switchTo(ChannelQueue.index - 1)
                        true
                    }
                    Key.MediaNext -> {
                        if (ChannelQueue.index + 1 < ChannelQueue.channels.size) switchTo(ChannelQueue.index + 1)
                        true
                    }
                    // Numara tuşları ile kanal girişi (Android TV uzaktan kumanda)
                    Key.Zero, Key.One, Key.Two, Key.Three, Key.Four,
                    Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine -> {
                        if (isLive && settings.tvNumberKeyChannel) {
                            val digit = when (ev.key) {
                                Key.Zero -> "0"; Key.One -> "1"; Key.Two -> "2"
                                Key.Three -> "3"; Key.Four -> "4"; Key.Five -> "5"
                                Key.Six -> "6"; Key.Seven -> "7"; Key.Eight -> "8"
                                Key.Nine -> "9"; else -> ""
                            }
                            numpadBuffer = (numpadBuffer + digit).takeLast(4)
                            numpadVisible = true
                        }
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (!locked) overlayVisible = !overlayVisible },
                    onDoubleTap = { offset ->
                        if (!locked && !isLive && settings.gesturesEnabled) {
                            val seekMs = app.store.settings().doubleTapSeekSec.coerceIn(5, 60) * 1000L
                            when {
                                offset.x < size.width / 3 -> PlaybackManager.seekBack(seekMs)
                                offset.x > size.width * 2 / 3 -> PlaybackManager.seekForward(seekMs)
                                else -> PlaybackManager.togglePlayPause()
                            }
                        }
                    }
                )
            }
            .pointerInput(settings.gesturesEnabled) {
                if (!settings.gesturesEnabled) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        gestureMode = null
                        gestureStartPos = PlaybackManager.displayPlayer()?.currentPosition ?: 0L
                    },
                    onDrag = { change, dragAmount ->
                        if (locked) return@detectDragGestures
                        change.consume()
                        val p = PlaybackManager.displayPlayer() ?: return@detectDragGestures
                        val dur = if (p.duration > 0) p.duration else 0L
                        if (gestureMode == null) {
                            val horizontal = abs(dragAmount.x) > abs(dragAmount.y)
                            // SÜRE bilinmeyen (dur<=0) VOD'da SEEK moduna girme;
                            // aksi halde sürükleme çalışmaz.
                            gestureMode = if (!isLive && horizontal && p.duration > 0) {
                                GestureMode.SEEK
                            } else if (!isLive && horizontal) {
                                if (change.position.x < size.width / 2) GestureMode.BRIGHTNESS else GestureMode.VOLUME
                            } else if (change.position.x < size.width / 2) {
                                GestureMode.BRIGHTNESS
                            } else {
                                GestureMode.VOLUME
                            }
                            gestureStartX = change.position.x
                            gestureStartY = change.position.y
                            gestureStartVolume = volume
                            gestureStartBrightness = brightness
                        }
                        when (gestureMode) {
                            GestureMode.SEEK -> {
                                seeking = true
                                val deltaMs = (((change.position.x - gestureStartX) / size.width) * dur).toLong()
                                position = (gestureStartPos + deltaMs).coerceIn(0L, dur)
                                gestureText = "${formatMs(position)} / ${formatMs(dur)}"
                            }
                            GestureMode.BRIGHTNESS -> {
                                val newB = (gestureStartBrightness - ((change.position.y - gestureStartY) / size.height))
                                    .coerceIn(0.05f, 1f)
                                brightness = newB
                                activity?.let { act ->
                                    act.window.attributes = act.window.attributes.apply {
                                        screenBrightness = newB
                                    }
                                }
                                gestureText = str(lang, "Parlaklık") + "%${(newB * 100).toInt()}"
                            }
                            GestureMode.VOLUME -> {
                                val newV = (gestureStartVolume - (((change.position.y - gestureStartY) / size.height) * audioMax).toInt())
                                    .coerceIn(0, audioMax)
                                volume = newV
                                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, newV, 0)
                                val pct = if (audioMax > 0) (newV * 100 / audioMax) else 0
                                gestureText = str(lang, "Ses") + " %$pct"
                            }
                            null -> {}
                        }
                    },
                    onDragEnd = {
                        if (gestureMode == GestureMode.SEEK) {
                            PlaybackManager.seekTo(position)
                            seeking = false
                        }
                        gestureMode = null
                        gestureText = null
                    },
                    onDragCancel = {
                        if (gestureMode == GestureMode.SEEK) seeking = false
                        gestureMode = null
                        gestureText = null
                    }
                )
            }
    ) {
        AndroidView(factory = { playerView }, modifier = Modifier.fillMaxSize())

        // Yayın (Chromecast) sırasında yerel ekran boş kalmasın — başlık + durum göster.
        if (isCasting) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.CastConnected,
                    contentDescription = null,
                    tint = Color(0xFF4FC3F7),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = PlaybackManager.currentTitle.ifBlank { str(lang, "Yayınlanıyor…") },
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = str(lang, "TV'de yayınlanıyor"),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (gestureText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    gestureText.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        // ---------- SKIP INTRO / OUTRO butonu ----------
        if (showSkipIntro && !isLive) {
            androidx.compose.material3.Button(
                onClick = {
                    val target = introRange?.endMs ?: 85_000L
                    PlaybackManager.seekTo(target)
                    showSkipIntro = false
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 80.dp),
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.9f),
                    contentColor = Color.Black
                )
            ) {
                Text("⏭ " + str(lang, "İntroyu Atla"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }

        if (showSkipOutro && !isLive) {
            androidx.compose.material3.Button(
                onClick = {
                    // Outro: bir sonraki bölüme geç (binge mod gibi)
                    val nextEp = VodQueue.next
                    if (nextEp != null) {
                        PlaybackManager.playNextEpisode()
                    }
                    showSkipOutro = false
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 80.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.85f),
                    contentColor = Color.Black
                )
            ) {
                Text("⏭ Sonraki Bölüm", style = MaterialTheme.typography.labelLarge)
            }
        }

        // ---------- Numara tuşu kanal göstergesi (Android TV) ----------
        if (numpadVisible && numpadBuffer.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 32.dp)
                    .background(Color.Black.copy(0.75f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    numpadBuffer,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Normal
                )
            }
        }

        // ---------- CENTER CONTROLS (Film/Dizi) ----------
        // Durdur / geri sar / ileri sar ikonları altta değil ekranın ortasında
        // gösterilir (Netflix tarzı); altta yalnızca ses + altyazı kalır.
        if (overlayVisible && !isLive && !isBuffering) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                IconButton(
                    onClick = { PlaybackManager.seekBack(10_000L) },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Default.Replay10,
                        contentDescription = str(lang, "10 sn Geri"),
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
                IconButton(
                    onClick = { PlaybackManager.togglePlayPause() },
                    modifier = Modifier.size(88.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = str(lang, "Oynat/Duraklat"),
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
                IconButton(
                    onClick = { PlaybackManager.seekForward(10_000L) },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Default.Forward10,
                        contentDescription = str(lang, "10 sn İleri"),
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
                // Sonraki Bölüm: yalnızca dizilerde gösterilir.
                val queueIsSeries = VodQueue.item?.isSeries == true || (VodQueue.item?.seriesId ?: 0L) > 0
                if (VodQueue.hasNext && queueIsSeries) {
                    IconButton(
                        onClick = { PlaybackManager.playNextEpisode() },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = str(lang, "Sonraki Bölüm"),
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }
        }

        if (overlayVisible) {
            // ---------- TOP BAR ----------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.85f * settings.playerPanelAlpha), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { exitPlayer() },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = str(lang, "Geri"),
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    if (isLive) {
                        ChannelLogo(
                            logo = resolveUrl(currentChannel?.logo ?: "", profile?.baseUrl.orEmpty()),
                            modifier = Modifier.size(34.dp)
                        )
                        // Canlı TV: kanal adı + tür + numara üst barda gösterilir.
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 6.dp)
                        ) {
                            Text(
                                text = currentChannel?.name ?: PlaybackManager.currentTitle,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentChannel != null && isLive) {
                                Text(
                                    text = "${currentChannel?.tvGenreTitle ?: ""}  •  ${str(lang, "Kanal")} ${currentChannel?.number ?: ""}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        // Film/Dizi: üst barda başlık gösterilmez (sadece geri + butonlar).
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(clock, color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${battery}%", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(4.dp))
                        if (isLive) {
                            IconButton(
                                onClick = {
                                    currentChannel?.let { vm.toggleFavoriteChannel(it) }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = str(lang, "Favori"),
                                    tint = if (isFav) Color(0xFFFF5252) else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { castDialogVisible = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                                contentDescription = "Chromecast",
                                tint = if (isCasting) Color(0xFF4FC3F7) else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        // Uyku zamanlayıcısı: aktifken kalan süre ikonun altında görünür.
                        IconButton(
                            onClick = { showSleepDialog = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Bedtime,
                                    contentDescription = str(lang, "Uyku zamanlayıcısı"),
                                    tint = if (sleepRemaining != 0L) Color(0xFFFFB74D) else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                if (sleepRemaining != 0L) {
                                    Text(
                                        sleepLabel(sleepRemaining, lang),
                                        color = Color(0xFFFFB74D),
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                val url = PlaybackManager.currentStreamUrl
                                if (url.isNotBlank()) {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { setType("video/*") }
                                        context.startActivity(Intent.createChooser(intent, str(lang, "Oynatıcı seç")))
                                    }
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = str(lang, "Harici oynatıcı"), tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        // Lock icon and PiP icon side-by-side
                        IconButton(
                            onClick = { locked = true; overlayVisible = false },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = str(lang, "Kilit"), tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        IconButton(
                            onClick = { PlaybackManager.enterPip(activity) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.PictureInPictureAlt, contentDescription = str(lang, "PiP (Resim içinde resim)"), tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        IconButton(
                            onClick = { showInfo = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = str(lang, "Bilgi"), tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        IconButton(
                            onClick = { showPlayerSettings = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = str(lang, "Ayarlar"), tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            // ---------- BOTTOM BAR ----------
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f * settings.playerPanelAlpha))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (!isLive) {
                    // Film/Dizi alt barı: solda süre, sağda yalnızca ses + altyazı
                    // (oynat/durdur, geri/ileri sar ikonları ekranın ORTASINDA —
                    // Netflix tarzı). En altta ince, dokunulabilir progress çizgisi.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatMs(position)} / ${formatMs(duration)}",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showTracks = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = str(lang, "Ses Dili"),
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            IconButton(
                                onClick = { showSubs = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Subtitles,
                                    contentDescription = str(lang, "Altyazı"),
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Netflix tarzı ince progress çizgisi: dokun/sürükle → atla.
                    val progressFraction = if (duration > 0) {
                        (position.toFloat() / duration).coerceIn(0f, 1f)
                    } else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                            .pointerInput(duration) {
                                detectTapGestures { offset ->
                                    if (duration > 0) {
                                        val target = (offset.x / size.width * duration).toLong().coerceIn(0L, duration)
                                        PlaybackManager.seekTo(target)
                                        position = target
                                    }
                                }
                            }
                            .pointerInput(duration) {
                                detectDragGestures(
                                    onDragStart = { seeking = true },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        if (duration > 0) {
                                            position = (change.position.x / size.width * duration)
                                                .toLong().coerceIn(0L, duration)
                                        }
                                    },
                                    onDragEnd = {
                                        PlaybackManager.seekTo(position)
                                        seeking = false
                                    },
                                    onDragCancel = { seeking = false }
                                )
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .fillMaxHeight()
                                .background(Color(0xFFE50914))
                        )
                    }
                } else {
                    // LIVE TV CONTROLS
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        Text(str(lang, "● CANLI"), color = Color(0xFFFF5252), style = MaterialTheme.typography.labelMedium)
                        if (liveSeekable) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                str(lang, "(timeshift: geri sarılabilir)"),
                                color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    // Canlı yayın geri sarılabilirken (timeshift) ilerleme çubuğu
                    // + "Canlıya Dön" — sunucu desteklemiyorsa gizlenir.
                    if (liveSeekable) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = formatMs(position),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Slider(
                                value = position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                                onValueChange = {
                                    seeking = true
                                    position = it.toLong()
                                },
                                onValueChangeFinished = {
                                    PlaybackManager.seekTo(position)
                                    seeking = false
                                },
                                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { PlaybackManager.seekTo((duration - 1000).coerceAtLeast(0L)) }) {
                                Text(str(lang, "Canlıya Dön"), color = Color(0xFFFF5252), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val idx = ChannelQueue.index - 1
                                    if (idx >= 0) switchTo(idx)
                                },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = str(lang, "Önceki"),
                                    tint = Color.White,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                            IconButton(
                                onClick = { PlaybackManager.togglePlayPause() },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = str(lang, "Oynat/Duraklat"),
                                    tint = Color.White,
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                            // Timeshift: canlı yayında 30 sn geri sar (sunucu destekliyorsa).
                            if (liveSeekable) {
                                IconButton(
                                    onClick = { PlaybackManager.seekBack(30_000L) },
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Replay10,
                                        contentDescription = str(lang, "30 sn geri sar"),
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    val idx = ChannelQueue.index + 1
                                    if (idx < ChannelQueue.channels.size) switchTo(idx)
                                },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = str(lang, "Sonraki"),
                                    tint = Color.White,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                            IconButton(
                                onClick = { showTracks = true },
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = str(lang, "Ses"),
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            IconButton(
                                onClick = { showSubs = true },
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    Icons.Default.Subtitles,
                                    contentDescription = str(lang, "Altyazı"),
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        // Right side: Rehber and Kanallar only for Live TV
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clickable { showEpg = true }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Tv,
                                        contentDescription = str(lang, "Rehber"),
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(str(lang, "Rehber"), color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            // Geçmiş yayın (catch-up): kanal arşivi varsa takvim açılır.
                            if (currentChannel?.isTvArchive == true && (currentChannel?.archiveDuration ?: 0) > 0) {
                                Box(
                                    modifier = Modifier
                                        .clickable { showCatchupCalendar = true }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                            contentDescription = str(lang, "Geçmiş Yayın"),
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(str(lang, "Geçmiş"), color = Color.White, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clickable { showChannels = true }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.List,
                                        contentDescription = str(lang, "Kanallar"),
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(str(lang, "Kanallar"), color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // ---------- LOCK OVERLAY ----------
        if (locked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.TopStart
            ) {
                IconButton(
                    onClick = { locked = false; overlayVisible = true },
                    modifier = Modifier.statusBarsPadding().padding(8.dp)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = str(lang, "Kiliti aç"), tint = Color.White)
                }
            }
        }

        if (error != null) {
            Text(
                text = error.orEmpty(),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )
        }
    }

    if (showTracks) {
        AudioTracksSheet(
            lang = lang,
            onDismiss = { showTracks = false },
            onSelect = { lang ->
                PlaybackManager.setAudioLanguage(lang)
                showTracks = false
            }
        )
    }

    if (showSubs) {
        SubtitleSheet(
            lang = lang,
            onDismiss = { showSubs = false },
            onSelect = { lang ->
                PlaybackManager.setSubtitleLanguage(lang)
                showSubs = false
            },
            enabled = PlaybackManager.subtitlesEnabled(),
            onToggle = { PlaybackManager.setSubtitlesEnabled(it) },
            onPickFile = { subtitleLauncher.launch(arrayOf("*/*")) }
        )
    }

    if (showEpg) {
        EpgSheet(
            lang = lang,
            channel = currentChannel,
            profile = profile,
            onDismiss = { showEpg = false },
            vm = vm
        )
    }

    if (showInfo) {
        PlayerInfoSheet(
            lang = lang,
            channel = currentChannel,
            profile = profile,
            onDismiss = { showInfo = false }
        )
    }

    if (showPlayerSettings) {
        PlayerSettingsSheet(
            lang = lang,
            currentSpeed = playbackSpeed,
            currentAspect = aspectMode,
            binge = settings.bingeMode,
            audioDelayMs = settings.audioDelayMs,
            onSpeed = { playbackSpeed = it; showPlayerSettings = false },
            onAspect = { aspectMode = it; showPlayerSettings = false },
            onBinge = { vm.saveSettings(settings.copy(bingeMode = it)) },
            onDelay = { PlaybackManager.setAudioDelayMs(it) },
            onDismiss = { showPlayerSettings = false }
        )
    }

    ChannelListPanel(
        lang = lang,
        visible = showChannels,
        currentId = currentChannel?.id,
        onClose = { showChannels = false },
        onSelect = { idx -> switchTo(idx); showChannels = false }
    )

    if (castDialogVisible) {
        CastDialog(
            routes = castRoutes,
            isCasting = isCasting,
            onConnect = { CastManager.connect(it) },
            onDisconnect = { CastManager.disconnect() },
            onDismiss = { castDialogVisible = false }
        )
    }

    if (showSleepDialog) {
        SleepTimerDialog(
            lang = lang,
            current = sleepRemaining,
            onMinutes = { minutes -> PlaybackManager.setSleepTimer(minutes); showSleepDialog = false },
            onUntilEnd = { PlaybackManager.setSleepUntilEpisodeEnd(); showSleepDialog = false },
            onCancel = { PlaybackManager.cancelSleepTimer(); showSleepDialog = false },
            onDismiss = { showSleepDialog = false }
        )
    }

    // Geçmiş yayın (catch-up): gün ve saat seçici → arşiv akışını başlatır.
    if (showCatchupCalendar && currentChannel != null) {
        CatchupPickerDialog(
            lang = lang,
            channel = currentChannel!!,
            profile = profile,
            vm = vm,
            onDismiss = { showCatchupCalendar = false },
            onPlay = { url, title ->
                PlaybackManager.play(url, title, currentChannel?.logo.orEmpty())
                showCatchupCalendar = false
            }
        )
    }

    // Çevrimiçi altyazı arama (OpenSubtitles): sonuç indirilip geçici dosyaya yazılır.
    if (showSubtitleSearch) {
        SubtitleSearchDialog(
            lang = lang,
            apiKey = settings.openSubtitlesApiKey,
            languages = settings.openSubtitlesLanguages,
            title = PlaybackManager.currentTitle,
            tmdbId = PlaybackManager.currentVodItem?.tmdbId ?: 0L,
            season = VodQueue.season.toInt().takeIf { s -> s > 0 },
            episode = VodQueue.current?.episodeNumber?.toInt(),
            onDismiss = { showSubtitleSearch = false },
            onApply = { srt ->
                runCatching {
                    val f = File(context.cacheDir, "os_sub_${System.currentTimeMillis()}.srt")
                    f.writeText(srt)
                    PlaybackManager.setExternalSubtitle(Uri.fromFile(f))
                }
                showSubtitleSearch = false
            }
        )
    }
}

private fun nowTime(): String {
    return runCatching {
        java.time.LocalTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("")
}

private fun formatMs(ms: Long): String {
    if (ms < 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}

private fun sleepLabel(sec: Long, lang: String): String = when {
    sec < 0 -> str(lang, "Bölüm sonu")
    sec >= 3600 -> "%d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
    else -> "%d:%02d".format(sec / 60, sec % 60)
}

@Composable
private fun SleepTimerDialog(
    lang: String,
    current: Long,
    onMinutes: (Int) -> Unit,
    onUntilEnd: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val presets = listOf(15, 30, 45, 60, 90, 120)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF18181B),
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("⏰", style = MaterialTheme.typography.titleLarge)
                Text(
                    str(lang, "Uyku Zamanlayıcısı"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (current != 0L) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${str(lang, "Aktif: ")}${if (current < 0) str(lang, "Bölüm sonunda dur") else "${sleepLabel(current, lang)} ${str(lang, "kaldı")}"}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onCancel,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(str(lang, "İptal Et"), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                Text(
                    str(lang, "Süre Seç"),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )

                // 3 sütunlu süre chip'leri
                val rows = presets.chunked(3)
                rows.forEach { rowList ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowList.forEach { m ->
                            val active = current == m * 60L
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (active) MaterialTheme.colorScheme.primary else Color(0xFF27272A),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onMinutes(m) }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (m >= 60) "${m / 60} ${str(lang, "saat")}" else "$m ${str(lang, "dk")}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        color = if (active) Color.White else Color.White.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bölüm sonunda dur
                val untilEndActive = current < 0
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (untilEndActive) MaterialTheme.colorScheme.primary else Color(0xFF27272A),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onUntilEnd)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                str(lang, "Bölüm sonunda dur"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (untilEndActive) Color.White else Color.White.copy(alpha = 0.9f)
                            )
                            Text(
                                str(lang, "Geçerli bölüm/medya bitince kapanır"),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (untilEndActive) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.5f)
                            )
                        }
                        if (untilEndActive) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(str(lang, "Kapat"), color = Color.White.copy(alpha = 0.8f))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTracksSheet(lang: String, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    var tracks by remember { mutableStateOf(PlaybackManager.availableTracks(C.TRACK_TYPE_AUDIO)) }
    LaunchedEffect(Unit) {
        tracks = PlaybackManager.availableTracks(C.TRACK_TYPE_AUDIO)
    }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141416),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) {
                Text(
                    str(lang, "Ses Parçaları"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${tracks.size + 1} " + str(lang, "seçenek"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            // Varsayılan
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF222226),
                modifier = Modifier.fillMaxWidth().clickable { onSelect(null) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🌐", modifier = Modifier.padding(end = 12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            str(lang, "Varsayılan (Otomatik)"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            str(lang, "Sistem / akış varsayılan dili"),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            tracks.forEach { (tLang, label) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF222226),
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(tLang) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔊", modifier = Modifier.padding(end = 12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            if (tLang.isNotBlank()) {
                                Text(
                                    tLang.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            if (tracks.isEmpty()) {
                Text(
                    str(lang, "Ek ses parçası bulunamadı"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSheet(
    lang: String,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onPickFile: () -> Unit
) {
    var tracks by remember { mutableStateOf(PlaybackManager.availableTracks(C.TRACK_TYPE_TEXT)) }
    LaunchedEffect(Unit) {
        tracks = PlaybackManager.availableTracks(C.TRACK_TYPE_TEXT)
    }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141416),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                str(lang, "Altyazı Ayarları"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Aç / Kapat Kartı
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF222226),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            str(lang, "Altyazılar"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            if (enabled) str(lang, "Açık") else str(lang, "Kapalı"),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (enabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f)
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { onToggle(it) })
                }
            }

            // Hızlı Butonlar: Dosyadan Seç
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF27272A),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            onDismiss()
                            onPickFile()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("📁 ", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            str(lang, "Dosyadan Ekle"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF27272A),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(null) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("🚫 ", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            str(lang, "Altyazıyı Kapat"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Text(
                str(lang, "Mevcut Altyazı Parçaları"),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 6.dp)
            )

            tracks.forEach { (tLang, label) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF222226),
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(tLang) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💬", modifier = Modifier.padding(end = 12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            if (tLang.isNotBlank()) {
                                Text(
                                    tLang.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            if (tracks.isEmpty()) {
                Text(
                    str(lang, "Gömülü altyazı bulunamadı. Yukarıdaki butondan altyazı dosyası (.srt/.vtt) seçebilirsin."),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpgSheet(
    lang: String,
    channel: Channel?,
    profile: com.stalkerapp.data.Profile?,
    onDismiss: () -> Unit,
    vm: MainViewModel
) {
    if (channel == null) {
        onDismiss()
        return
    }
    var programs by remember { mutableStateOf<List<com.stalkerapp.data.EpgProgram>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var remindersVersion by remember { mutableStateOf(0) }
    val reminders = remember(remindersVersion) { vm.store.epgReminders() }
    val nowTs = System.currentTimeMillis() / 1000
    val epgScope = rememberCoroutineScope()

    LaunchedEffect(channel.id) {
        try {
            programs = vm.repository.loadEpg(profile, channel)
        } catch (e: Exception) {
            error = e.message
        }
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141416),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "EPG — ${channel.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
            when {
                programs == null && error == null -> CircularProgressIndicator(
                    modifier = Modifier.padding(24.dp).align(Alignment.CenterHorizontally)
                )
                error != null -> Text(
                    error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
                programs.orEmpty().isEmpty() -> Text(
                    str(lang, "EPG verisi yok"),
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(16.dp)
                )
                else -> androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.height(420.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(programs.orEmpty()) { p ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (p.isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color(0xFF222226),
                            border = if (p.isCurrent) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${vm.repository.formatEpoch(p.startTs)} — ${vm.repository.formatEpoch(p.stopTs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (p.isCurrent) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                    )
                                    if (p.isCurrent) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                " ● " + str(lang, "CANLI") + " ",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    p.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                if (p.desc.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        p.desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.6f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (!p.isDefault && p.startTs > nowTs) {
                                    val reminded = reminders.any { it.channelId == channel.id && it.startTs == p.startTs }
                                    TextButton(
                                        onClick = {
                                            if (reminded) {
                                                vm.store.removeEpgReminder(channel.id, p.startTs)
                                            } else {
                                                vm.store.addEpgReminder(
                                                    EpgReminder(
                                                        id = "r_${channel.id}_${p.startTs}",
                                                        channelId = channel.id,
                                                        channelName = channel.name,
                                                        programName = p.name,
                                                        startTs = p.startTs
                                                    )
                                                )
                                            }
                                            remindersVersion++
                                        },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            if (reminded) str(lang, "🔔 Hatırlatma ayarlandı (kaldır)") else str(lang, "🔔 Başlayınca Bildir"),
                                            color = if (reminded) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                                if (!p.isDefault && p.stopTs <= nowTs) {
                                    TextButton(
                                        onClick = {
                                            epgScope.launch {
                                                val url = vm.repository.catchupUrl(channel, profile, p.startTs)
                                                if (!url.isNullOrBlank()) {
                                                    PlaybackManager.play(url, "${channel.name} — ${p.name}", subtitle = vm.repository.formatEpoch(p.startTs))
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            str(lang, "▶ Geçmiş Yayını İzle (catch-up)"),
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerInfoSheet(
    lang: String,
    channel: Channel?,
    profile: Profile?,
    onDismiss: () -> Unit
) {
    val rows = remember { mutableStateListOf<Pair<String, String>>() }
    val p = PlaybackManager.player
    val v = p?.videoFormat
    val a = p?.audioFormat
    val url = PlaybackManager.currentStreamUrl

    fun rebuild() {
        val play = PlaybackManager.player
        val vid = play?.videoFormat
        val aud = play?.audioFormat
        val bufSec = (play?.totalBufferedDuration ?: 0) / 1000f
        rows.clear()
        rows.add(str(lang, "Çözünürlük") to if (vid?.height != null && vid.height > 0) "${vid.width}x${vid.height}" else "—")
        rows.add(str(lang, "Video Codec") to mimeLabel(vid?.sampleMimeType))
        rows.add(str(lang, "Ses Codec") to mimeLabel(aud?.sampleMimeType))
        rows.add(str(lang, "Önbellek (Buffer)") to "%.1f sn".format(bufSec))
        rows.add(str(lang, "Dekoder") to (vid?.codecs ?: "MediaCodec (HW)"))
        rows.add(str(lang, "Kanal Sayısı") to "${aud?.channelCount ?: 2} Kanal")
        if (url.isNotBlank()) rows.add("URL" to url)
    }

    LaunchedEffect(Unit) {
        rebuild()
        while (true) {
            delay(1000)
            rebuild()
        }
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141416),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (channel != null) {
                    ChannelLogo(logo = resolveUrl(channel.logo, profile?.baseUrl.orEmpty()), modifier = Modifier.size(52.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(channel.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        if (channel.tvGenreTitle.isNotBlank()) {
                            Text(channel.tvGenreTitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                } else {
                    Text(
                        PlaybackManager.currentTitle.ifBlank { "Medya Bilgileri" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

            // Tech specs grid (2 sütunlu kartlar)
            val nonUrlRows = rows.filter { it.first != "URL" }
            val pairs = nonUrlRows.chunked(2)
            pairs.forEach { pairList ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pairList.forEach { (label, value) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF222226),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(4.dp))
                                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                    if (pairList.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (url.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF222226),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Stream URL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            url,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun mimeLabel(mime: String?): String {
    return when (mime) {
        "video/avc" -> "H.264 (AVC)"
        "video/hevc" -> "H.265 (HEVC)"
        "video/av01" -> "AV1"
        "video/vp9" -> "VP9"
        "video/mp4v-es" -> "MPEG-4"
        "video/mpeg", "video/mpeg2" -> "MPEG-2"
        "audio/mp4a-latm", "audio/aac" -> "AAC-LC"
        "audio/mpeg" -> "MP3"
        "audio/ac3" -> "Dolby Digital (AC3)"
        "audio/eac3" -> "Dolby Digital Plus (E-AC3)"
        else -> mime ?: "—"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(
    lang: String,
    currentSpeed: Float,
    currentAspect: Int,
    binge: Boolean,
    audioDelayMs: Int,
    onSpeed: (Float) -> Unit,
    onAspect: (Int) -> Unit,
    onBinge: (Boolean) -> Unit,
    onDelay: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    val aspects = listOf(
        str(lang, "Sığdır") to AspectRatioFrameLayout.RESIZE_MODE_FIT,
        str(lang, "Doldur") to AspectRatioFrameLayout.RESIZE_MODE_FILL,
        str(lang, "Yakınlaştır") to AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    )
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141416),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                str(lang, "Oynatıcı Ayarları"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Oynatma Hızı
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(str(lang, "Oynatma Hızı"), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    speeds.forEach { s ->
                        val selected = s == currentSpeed
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF222226),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSpeed(s) }
                        ) {
                            Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    if (s == 1f) "1.0x" else "${s}x",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color.White else Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // Görüntü Oranı
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(str(lang, "Görüntü Oranı"), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    aspects.forEach { (label, mode) ->
                        val selected = mode == currentAspect
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF222226),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onAspect(mode) }
                        ) {
                            Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color.White else Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // A/V Senkron
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF222226),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        str(lang, "Ses Senkronu (A/V Gecikmesi)"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF2E2E34),
                            modifier = Modifier.clickable { onDelay((audioDelayMs - 50).coerceIn(-500, 500)) }
                        ) {
                            Text("−50 ms", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White)
                        }
                        Text(
                            "${if (audioDelayMs > 0) "+" else ""}$audioDelayMs ms",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF2E2E34),
                            modifier = Modifier.clickable { onDelay((audioDelayMs + 50).coerceIn(-500, 500)) }
                        ) {
                            Text("+50 ms", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White)
                        }
                    }
                    if (audioDelayMs != 0) {
                        TextButton(
                            onClick = { onDelay(0) },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(str(lang, "Sıfırla (0 ms)"), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Binge Modu
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF222226),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            str(lang, "Binge Modu"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            str(lang, "Bölüm bitince sıradaki bölüm otomatik başlar"),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                    Switch(checked = binge, onCheckedChange = onBinge)
                }
            }
        }
    }
}

@Composable
fun ChannelListPanel(
    lang: String,
    visible: Boolean,
    currentId: Long? = null,
    onClose: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val channels = ChannelQueue.channels
    val profile = ChannelQueue.profile
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(visible) {
        if (visible) {
            val idx = channels.indexOfFirst { it.id == currentId }
            if (idx >= 0) listState.scrollToItem(idx)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it })
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
            Box(modifier = Modifier.fillMaxSize().clickable { onClose() })
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(340.dp)
                    .align(Alignment.CenterStart),
                color = Color(0xFF141414)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            str(lang, "Kanallar"),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = str(lang, "Kapat"), tint = Color.White)
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(str(lang, "Kanal ara…")) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    val filtered = if (query.isBlank()) channels else channels.filter {
                        it.name.contains(query.trim(), ignoreCase = true)
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { it.id }) { ch ->
                            ChannelRow(
                                channel = ch,
                                baseUrl = profile?.baseUrl.orEmpty(),
                                highlight = ch.id == currentId
                            ) {
                                onSelect(channels.indexOfFirst { c -> c.id == ch.id })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Catch-up takvim seçici: arşiv günü + saat seçilip arşiv akışı başlatılır. */
@Composable
private fun CatchupPickerDialog(
    lang: String,
    channel: Channel,
    profile: Profile?,
    vm: MainViewModel,
    onDismiss: () -> Unit,
    onPlay: (String, String) -> Unit
) {
    val days = remember { com.stalkerapp.data.CatchupHelper.pastDaysList(7) }
    var selectedDay by remember { mutableStateOf(0) }
    var hour by remember { mutableStateOf(20) }
    var playing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(lang, "Geçmiş Yayın (Catch-up)")) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("${channel.name}", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(days.size) { i ->
                        val d = days[i]
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (i == selectedDay) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { selectedDay = i }
                        ) {
                            Text(
                                d.label,
                                color = if (i == selectedDay) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("$hour:00", style = MaterialTheme.typography.titleLarge)
                Slider(
                    value = hour.toFloat(),
                    onValueChange = { hour = it.toInt() },
                    valueRange = 0f..23f,
                    steps = 22
                )
                Text(
                    str(lang, "Seçilen saatten 1 saatlik arşiv yayını başlatılır."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !playing,
                onClick = {
                    playing = true
                    scope.launch {
                        try {
                            val dayStart = days[selectedDay].startOfDayUnix + hour * 3600
                            val liveUrl = vm.repository.channelStreamUrl(channel, profile)
                            val url = com.stalkerapp.data.CatchupHelper.buildStalkerCatchupUrl(
                                liveUrl, dayStart, dayStart + 3600
                            )
                            onPlay(url, "${channel.name} — ${days[selectedDay].label} $hour:00")
                        } catch (e: Exception) {
                            playing = false
                        }
                    }
                }
            ) { Text(if (playing) str(lang, "Başlatılıyor…") else str(lang, "İzle")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(lang, "İptal")) } }
    )
}

/** OpenSubtitles arama sonuçlarını listeler; seçilen altyazı indirilip uygulanır. */
@Composable
private fun SubtitleSearchDialog(
    lang: String,
    apiKey: String,
    languages: String,
    title: String,
    tmdbId: Long,
    season: Int?,
    episode: Int?,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    val client = remember { com.stalkerapp.data.OpenSubtitlesClient(apiKey) }
    var results by remember { mutableStateOf<List<com.stalkerapp.data.OpenSubtitlesClient.SubtitleEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        results = client.search(tmdbId, title, season, episode, languages)
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text(str(lang, "Çevrimiçi Altyazı Ara")) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                when {
                    downloading -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    results == null && error == null -> CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp)
                    )
                    error != null -> Text(
                        error.orEmpty(),
                        color = MaterialTheme.colorScheme.error
                    )
                    results.orEmpty().isEmpty() -> Text(
                        str(lang, "Altyazı bulunamadı. API anahtarı Ayarlar → Altyazı'dan eklenebilir."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> results.orEmpty().take(15).forEach { s ->
                        ListItem(
                            headlineContent = { Text(s.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("${s.languageName} • ${s.format.uppercase()} • ${s.downloadCount} indirme") },
                            trailingContent = {
                                Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.clickable(enabled = !downloading) {
                                downloading = true
                                scope.launch {
                                    val content = client.download(s.fileId)
                                    downloading = false
                                    if (content == null) {
                                        error = str(lang, "İndirme başarısız")
                                    } else {
                                        onApply(content)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(lang, "Kapat")) } }
    )
}
