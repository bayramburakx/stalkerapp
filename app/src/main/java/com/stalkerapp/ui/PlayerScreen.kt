package com.stalkerapp.ui

import android.app.Activity
import android.content.Context
import android.util.TypedValue
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
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
import android.net.Uri
import android.os.BatteryManager
import android.content.pm.ActivityInfo
import android.view.WindowManager
import kotlin.math.abs
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
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
            Afr.apply(activity, settings.afrMode, fps)
            delay(2000)
        }
    }

    DisposableEffect(Unit) {
        onDispose { Afr.clear(activity) }
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
                if (dur > 0 && pos > 0) {
                    val cur = VodQueue.current
                    if (cur != null) {
                        // Bölüm bazlı ilerleme (devam et / binge için).
                        app.store.saveEpisodeProgress(
                            "${PlaybackManager.currentVodId}:${VodQueue.season}:${cur.episodeNumber}",
                            pos, dur
                        )
                    } else if (pos < dur * 0.95) {
                        app.store.saveVodProgress(PlaybackManager.currentVodId, pos, dur)
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
                if (dur > 0 && pos > 0) {
                    val cur = VodQueue.current
                    if (cur != null) {
                        app.store.saveEpisodeProgress(
                            "${PlaybackManager.currentVodId}:${VodQueue.season}:${cur.episodeNumber}",
                            pos, dur
                        )
                    } else {
                        app.store.saveVodProgress(PlaybackManager.currentVodId, pos, dur)
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
            onToggle = { PlaybackManager.setSubtitlesEnabled(it) }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(lang, "Uyku Zamanlayıcısı")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (current != 0L) {
                    Text(
                        "${str(lang, "Aktif: ")}${if (current < 0) str(lang, "Bölüm sonunda dur") else "${sleepLabel(current, lang)} ${str(lang, "kaldı")}"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                listOf(15 to str(lang, "15 dk"), 30 to str(lang, "30 dk"), 60 to str(lang, "1 saat"), 90 to str(lang, "1.5 saat")).forEach { (m, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        trailingContent = if (current == m * 60L) {
                            { Text("✓", color = MaterialTheme.colorScheme.primary) }
                        } else null,
                        modifier = Modifier.clickable { onMinutes(m) }
                    )
                }
                ListItem(
                    headlineContent = { Text(str(lang, "Bölüm sonunda dur")) },
                    supportingContent = { Text(str(lang, "Geçerli bölüm/medya bitince kapanır")) },
                    trailingContent = if (current < 0) {
                        { Text("✓", color = MaterialTheme.colorScheme.primary) }
                    } else null,
                    modifier = Modifier.clickable(onClick = onUntilEnd)
                )
                if (current != 0L) {
                    ListItem(
                        headlineContent = { Text(str(lang, "Kapat")) },
                        modifier = Modifier.clickable(onClick = onCancel)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str(lang, "Kapat")) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTracksSheet(lang: String, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    var tracks by remember { mutableStateOf(PlaybackManager.availableTracks(C.TRACK_TYPE_AUDIO)) }
    LaunchedEffect(Unit) {
        tracks = PlaybackManager.availableTracks(C.TRACK_TYPE_AUDIO)
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Text(str(lang, "Ses Dili"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            ListItem(
                headlineContent = { Text(str(lang, "Varsayılan (Otomatik)")) },
                modifier = Modifier.clickable { onSelect(null) }
            )
            tracks.forEach { (lang, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    supportingContent = { Text(lang) },
                    modifier = Modifier.clickable { onSelect(lang) }
                )
            }
            if (tracks.isEmpty()) {
                Text(
                    str(lang, "Ses izi bulunamadı"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
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
    onToggle: (Boolean) -> Unit
) {
    var tracks by remember { mutableStateOf(PlaybackManager.availableTracks(C.TRACK_TYPE_TEXT)) }
    LaunchedEffect(Unit) {
        tracks = PlaybackManager.availableTracks(C.TRACK_TYPE_TEXT)
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Text(str(lang, "Altyazı"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            ListItem(
                headlineContent = { Text(str(lang, "Altyazılar")) },
                trailingContent = {
                    Switch(checked = enabled, onCheckedChange = { onToggle(it) })
                }
            )
            ListItem(
                headlineContent = { Text(str(lang, "Altyazı yok (Kapat)")) },
                modifier = Modifier.clickable { onSelect(null) }
            )
            tracks.forEach { (lang, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    supportingContent = { Text(lang) },
                    modifier = Modifier.clickable { onSelect(lang) }
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
    // Hatırlatıcı ekle/kaldır sonrası listeyi tazele.
    var remindersVersion by remember { mutableStateOf(0) }
    val reminders = remember(remindersVersion) { vm.store.epgReminders() }
    val nowTs = System.currentTimeMillis() / 1000
    // Catch-up URL çözmek için (onClick içinde @Composable çağrısı yapılamaz).
    val epgScope = rememberCoroutineScope()

    LaunchedEffect(channel.id) {
        try {
            programs = vm.repository.loadEpg(profile, channel)
        } catch (e: Exception) {
            error = e.message
        }
    }

    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Text(
                "EPG — ${channel.name}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
                else -> androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.height(400.dp)
                ) {
                    items(programs.orEmpty()) { p ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (p.isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "${vm.repository.formatEpoch(p.startTs)} — ${vm.repository.formatEpoch(p.stopTs)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (p.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (p.isCurrent) {
                                    Text(
                                        str(lang, "● ŞİMDİ"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                p.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (p.isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            if (p.desc.isNotBlank()) {
                                Text(
                                    p.desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // Program hatırlatıcısı: gelecekteki programlara "başlayınca bildir".
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
                                        if (reminded) str(lang, "🔔 Hatırlatma ayarlandı (dokun: kaldır)") else str(lang, "🔔 Başlayınca Bildir"),
                                        color = if (reminded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                            // Catch-up: geçmişteki programı şimdi izle (sunucu destekliyorsa).
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
                        HorizontalDivider()
                    }
                }
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerInfoSheet(
    lang: String,
    channel: Channel?,
    profile: Profile?,
    onDismiss: () -> Unit
) {
    if (channel == null) {
        onDismiss()
        return
    }

    val rows = remember { mutableStateListOf<Pair<String, String>>() }

    fun rebuild() {
        // Yayın (cast) sırasında bu panel yerel ExoPlayer bilgisini gösterir
        // (videoFormat/audioFormat yalnızca ExoPlayer'da vardır).
        val p = PlaybackManager.player
        val v = p?.videoFormat
        val a = p?.audioFormat
        val url = PlaybackManager.currentStreamUrl
        val bufSec = (p?.totalBufferedDuration ?: 0) / 1000f
        rows.clear()
        rows.add("source" to "tv")
        rows.add(
            "video" to buildString {
                append(if (v?.height != null && v.height > 0) "${v.height}p" else "—")
                append(" | ")
                append(mimeLabel(v?.sampleMimeType))
                append(" | ")
                append(if (v != null) "hw" else "—")
            }
        )
        rows.add("Engine" to "exo player")
        rows.add("audio" to mimeLabel(a?.sampleMimeType))
        rows.add("BUFFER" to str(lang, "%.1f sn").format(bufSec))
        rows.add("DECODER" to (v?.codecs ?: "MediaCodec"))
        if (url.isNotBlank()) rows.add("URL" to url)
    }

    LaunchedEffect(Unit) {
        rebuild()
        while (true) {
            delay(1000)
            rebuild()
        }
    }

    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(logo = resolveUrl(channel.logo, profile?.baseUrl.orEmpty()), modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(channel.name, style = MaterialTheme.typography.titleLarge)
                    if (channel.tvGenreTitle.isNotBlank()) {
                        Text(
                            channel.tvGenreTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            rows.forEach { (k, v) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        k,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(90.dp)
                    )
                    Text(
                        v,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun mimeLabel(mime: String?): String {
    return when (mime) {
        "video/avc" -> "h.264"
        "video/hevc" -> "hevc"
        "video/av01" -> "av1"
        "video/vp9" -> "vp9"
        "video/mp4v-es" -> "mpeg-4"
        "video/mpeg", "video/mpeg2" -> "mpeg-2"
        "audio/mp4a-latm", "audio/aac" -> "aac-lc"
        "audio/mpeg" -> "mp3"
        "audio/ac3" -> "ac3"
        "audio/eac3" -> "eac3"
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
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Text(str(lang, "Oynatma Hızı"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            speeds.forEach { s ->
                ListItem(
                    headlineContent = { Text("${if (s == 1f) str(lang, "Normal") else s}⨉") },
                    trailingContent = if (s == currentSpeed) {
                        { Text("✓", color = MaterialTheme.colorScheme.primary) }
                    } else null,
                    modifier = Modifier.clickable { onSpeed(s) }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(str(lang, "Görüntü Oranı"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            aspects.forEach { (label, mode) ->
                ListItem(
                    headlineContent = { Text(label) },
                    trailingContent = if (mode == currentAspect) {
                        { Text("✓", color = MaterialTheme.colorScheme.primary) }
                    } else null,
                    modifier = Modifier.clickable { onAspect(mode) }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                str(lang, "A/V Senkron (Ses Gecikmesi)"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            Text(
                str(lang, "Pozitif = ses gecikir, negatif = ses öne alınır."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = { onDelay((audioDelayMs - 50).coerceIn(-500, 500)) }) {
                    Text("−50 ms")
                }
                Text(
                    "${if (audioDelayMs > 0) "+" else ""}$audioDelayMs ms",
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedButton(onClick = { onDelay((audioDelayMs + 50).coerceIn(-500, 500)) }) {
                    Text("+50 ms")
                }
            }
            TextButton(
                onClick = { onDelay(0) },
                enabled = audioDelayMs != 0,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) { Text(str(lang, "Sıfırla (0 ms)")) }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ListItem(
                headlineContent = { Text(str(lang, "Binge Modu")) },
                supportingContent = { Text(str(lang, "Bölüm bitince sıradaki bölüm otomatik oynatılır")) },
                trailingContent = { Switch(checked = binge, onCheckedChange = onBinge) }
            )
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
