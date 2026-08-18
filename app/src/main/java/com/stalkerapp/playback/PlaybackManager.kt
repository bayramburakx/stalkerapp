package com.stalkerapp.playback

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.TsExtractor
import com.google.android.gms.cast.framework.CastContext
import com.stalkerapp.R
import com.stalkerapp.cast.CastManager
import com.stalkerapp.data.Channel
import com.stalkerapp.data.Episode
import com.stalkerapp.data.PortalRepository
import com.stalkerapp.data.Profile
import com.stalkerapp.data.Store
import com.stalkerapp.data.VodItem
import com.stalkerapp.util.L10n
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object ChannelQueue {
    var channels: List<Channel> = emptyList()
    var index: Int = 0
    var profile: Profile? = null

    val current: Channel? get() = channels.getOrNull(index)
    val next: Channel? get() = channels.getOrNull(index + 1)
    val previous: Channel? get() = channels.getOrNull(index - 1)
}

/**
 * VOD/dizi oynatma kuyruğu: binge modu ve "sonraki bölüm" için bölüm listesini
 * tutar. Bölüm oynatılmadan önce [PlaybackManager.playEpisode] ile doldurulur.
 */
object VodQueue {
    var item: VodItem? = null
    var profile: Profile? = null
    var season: Long = 0
    var episodes: List<Episode> = emptyList()
    var index: Int = 0

    val current: Episode? get() = episodes.getOrNull(index)
    val next: Episode? get() = episodes.getOrNull(index + 1)
    val hasNext: Boolean get() = index + 1 < episodes.size
}

object PlaybackManager {

    const val NOTIFICATION_ID = 1001
    const val ACTION_TOGGLE = "com.stalkerapp.action.TOGGLE"
    const val ACTION_NEXT = "com.stalkerapp.action.NEXT"
    const val ACTION_PREV = "com.stalkerapp.action.PREV"
    const val ACTION_STOP = "com.stalkerapp.action.STOP"
    const val CHANNEL_ID = "stalker_playback"

    private lateinit var appContext: Context
    private lateinit var store: Store
    private lateinit var repository: PortalRepository

    private fun l10n(text: String): String = L10n.t(store.settings().language, text)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var activePlayer: ExoPlayer? = null
    private var standbyPlayer: ExoPlayer? = null

    // Chromecast: CastPlayer (media3-cast) içeriği TV'ye gönderir. Google Play
    // servisleri yoksa kurulamaz ve null kalır (yayın özelliği kapalı olur).
    private var castPlayer: CastPlayer? = null
    private val castListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()
    // Yayın oturumu aktif mi? SessionAvailabilityListener ile güncellenir.
    @Volatile private var castSessionActive = false
    // stop() sırasında yayın kesilirken yerel oynatıcıyı yeniden başlatmayı önler.
    private var stopping = false

    // Son oynatılan içeriğin kapak görseli — yayına aktarılırken tekrar kurulur.
    private var currentArtwork: String = ""

    // Ana sayfadaki "Son İzlenen Kanallar" satırı için son oynatılan kanallar (10).
    private val _recentChannels = MutableStateFlow<List<Channel>>(emptyList())
    val recentChannels: StateFlow<List<Channel>> = _recentChannels

    private fun trackRecentChannel(ch: Channel) {
        val updated = (listOf(ch) + _recentChannels.value.filter { it.id != ch.id }).take(10)
        _recentChannels.value = updated
    }

    // Yapısal oynatıcı ayarları (çözücü, akış formatı, passthrough, tampon)
    // değişince oyuncu yeniden kurulur; aksi halde yeni değerler uygulanmazdı
    // (media source factory / renderer'lar kurulumda sabitlenir).
    private var lastPlayerConfig: String = ""

    // Varsayılan oynatıcı "harici" olduğunda içerik sistem oynatıcısına gönderilir;
    // PlayerScreen bu bayrağı görünce kendini kapatır (boş ekranda kalmaz).
    @Volatile private var externalPlaybackLaunched = false

    private var vodPlayback: Boolean = false
    fun isVod(): Boolean = vodPlayback

    // Canlı TV: geçici akış kesintilerinde otomatik yeniden deneme sayacı.
    // Kullanıcı kanal değiştirdiğinde sıfırlanır; peş peşe en fazla 3 deneme.
    private var liveRetryCount = 0

    private val playerListeners = CopyOnWriteArrayList<(Player?) -> Unit>()
    private var stateListeners = CopyOnWriteArrayList<(Boolean, Boolean) -> Unit>()
    private val errorListeners = CopyOnWriteArrayList<(String?) -> Unit>()

    /** CastPlayer (media3-cast) oynatma durumu dinleyicisi (bildirim + durum). */
    private val castListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            notifyStateChanged()
            if (playbackState == Player.STATE_ENDED && vodPlayback) {
                markCurrentEpisodeWatched()
                if (store.settings().bingeMode && VodQueue.hasNext) {
                    playNextEpisode(auto = false)
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateNotification()
        }
    }

    /**
     * Yayın oturumu durumu (media3-cast `SessionAvailabilityListener`): oturum
     * bağlanınca aktif içeriği TV'ye aktarır, bağlantı kesilince telefon
     * oynatıcısı kaldığı yerden sürer.
     */
    private val sessionAvailabilityListener = object : SessionAvailabilityListener {
        override fun onCastSessionAvailable() {
            castSessionActive = true
            // Aktif içeriği TV'ye gönder; telefondaki oynatmayı duraklat
            // (aksi halde iki cihazdan aynı anda ses çıkar).
            if (currentStreamUrl.isNotBlank()) {
                val item = mediaItem(currentStreamUrl, currentTitle, currentArtwork)
                val pos = activePlayer?.currentPosition ?: 0L
                castPlayer?.setMediaItem(item, pos)
                castPlayer?.prepare()
                castPlayer?.play()
            }
            activePlayer?.pause()
            notifyCastChanged()
        }

        override fun onCastSessionUnavailable() {
            castSessionActive = false
            if (!stopping) {
                // Yayın bitti/kesildi: telefon oynatıcısı kaldığı yerden sürsün.
                val pos = castPlayer?.currentPosition ?: 0L
                activePlayer?.let { p ->
                    if (pos > 0) p.seekTo(pos)
                    p.play()
                }
            }
            notifyCastChanged()
        }
    }

    fun addErrorListener(l: (String?) -> Unit) {
        errorListeners.add(l)
        l(errorMessage)
    }

    fun removeErrorListener(l: (String?) -> Unit) {
        errorListeners.remove(l)
    }

    private fun setError(msg: String?) {
        errorMessage = msg
        errorListeners.forEach { it(msg) }
    }

    @Volatile var service: PlaybackService? = null

    var currentTitle: String = ""
    var currentSubtitle: String = ""
        private set

    var currentStreamUrl: String = ""
        private set

    var currentVodId: Long = 0

    // Oynatılan VOD öğesinin anlık görüntüsü: katalog senkronu tamamlanmasa da
    // ilerleme kaydına öğe bilgisi (ad/afiş/tür) yazılabilsin — ana sayfa
    // "İzlemeye Devam / Son İzlenenler" listeleri katalog byId'sine bağımlı kalmaz.
    var currentVodItem: com.stalkerapp.data.VodItem? = null

    @Volatile var errorMessage: String? = null
        private set

    fun init(context: Context, store: Store, repository: PortalRepository) {
        appContext = context.applicationContext
        this.store = store
        this.repository = repository
        createNotificationChannel()
        CastManager.init(appContext)
        // Chromecast: CastPlayer'ı kur (Google Play servisleri/uyumlu cihaz
        // yoksa sessizce null kalır; yayın özelliği devre dışı olur).
        castPlayer = runCatching {
            CastPlayer(CastContext.getSharedInstance(appContext)).apply {
                addListener(castListener)
                setSessionAvailabilityListener(sessionAvailabilityListener)
            }
        }.getOrNull()
        // Uygulama açılırken oturum zaten bağlıysa (CastContext hatırlar) durumu yakala.
        castSessionActive = castPlayer?.isCastSessionAvailable() == true
        // Bölüm %85 izlendiğinde otomatik "izlendi" işareti (ekran arka planda
        // olsa da, PiP/arka plan oynatmada bile) — 5 sn'de bir kontrol edilir.
        scope.launch {
            while (true) {
                delay(5000)
                checkAutoWatched()
            }
        }
    }

    /** İçerik %85+ izlendiyse otomatik "izlendi" işaretler (tekrar tekrar işaretlemez). */
    private fun checkAutoWatched() {
        val p = activePlayer ?: return
        if (!vodPlayback) return
        val dur = p.duration
        val pos = p.currentPosition
        if (dur <= 0 || pos <= 0 || pos < dur * 0.85) return
        val cur = VodQueue.current
        val item = VodQueue.item
        if (cur != null && item != null && (item.isSeries || item.seriesId > 0)) {
            // Dizi bölümü: bölüm anahtarıyla işaretle.
            val key = "${item.id}:${VodQueue.season}:${cur.episodeNumber}"
            if (!store.isEpisodeWatched(key)) store.markEpisodeWatched(key)
        } else if (currentVodId != 0L) {
            // Film: ilerlemeyi kalıcı kaydet — "izlendi" işareti positionMs >= %85
            // üzerinden hesaplandığı için film buradan izlendi sayılır.
            val prog = store.loadVodProgress()[currentVodId]
            if (prog == null || prog.positionMs < dur * 0.85) {
                store.saveVodProgress(currentVodId, pos, dur, currentVodItem)
            }
        }
    }

    val player: ExoPlayer?
        get() = activePlayer

    private fun ensureActivePlayer(): ExoPlayer {
        val key = playerConfigKey()
        val existing = activePlayer
        if (existing != null) {
            if (key == lastPlayerConfig) return existing
            // Yapısal ayarlar değişti: oyuncuyu yeni ayarlarla yeniden kur.
            existing.release()
            activePlayer = null
            notifyPlayerChanged()
        }
        val p = buildPlayer()
        activePlayer = p
        lastPlayerConfig = key
        attachListener(p)
        notifyPlayerChanged()
        return p
    }

    /** Oynatıcıyı yeniden kurmayı gerektiren ayarların özeti (değişirse rebuild). */
    private fun playerConfigKey(): String {
        val st = store.settings()
        return buildString {
            append(st.decoder).append('|')
            append(st.streamFormat).append('|')
            append(st.audioPassthrough).append('|')
            append(st.maxBufferMs).append('|')
            // Aktif kaynak değişince UA da değişir; oyuncu yeniden kurulur.
            append(store.activeSourceKind())
        }
    }

    private fun buildPlayer(): ExoPlayer {
        val st = store.settings()

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setTsExtractorMode(TsExtractor.MODE_MULTI_PMT)
            .setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES)

        val bufferMs = st.maxBufferMs.coerceAtMost(30_000)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                5_000,
                bufferMs,
                1_000,
                2_000
            )
            .build()

        // Stalker portal akışları bazı STB portallarının beklediği MAG User-Agent'i
        // ile açılır; ancak çoğu Xtream/M3U paneli/CDN'si MAG/STB UA'sını 401 ile
        // reddeder. Bu yüzden dış kaynaklarda genel bir Android/browser UA kullanılır.
        val isExternalSource = store.activeSourceKind() in setOf("m3u", "xtream")
        val userAgent = if (isExternalSource)
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        else
            "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"

        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setUserAgent(userAgent)

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(appContext, httpDataSourceFactory)

        // Akış formatı zorlama (Ayarlar → Oynatıcı): bazı sağlayıcılarda kanal
        // açılmıyorsa veya catch-up'ta sıçramada sorun varsa akış zorla HLS ya da
        // MPEG-TS olarak çözülür. "auto" türe göre otomatik algılar.
        val mediaSourceFactory = when (st.streamFormat) {
            "hls" -> HlsMediaSource.Factory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))
            "ts" -> ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))
            else -> DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
                .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))
        }

        val renderers = SyncRenderersFactory(
            appContext,
            passthrough = st.audioPassthrough,
            subtitleTypes = st.subtitleTypes
        )
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            // "hardware" sıkı modda HW başarısızsa yazılıma düşmez; auto/yazılımda
            // HW çökmesini önlemek için yazılım çözücüye geri düşülür (beyaz/kara ekran).
            .setEnableDecoderFallback(st.decoder != "hardware")

        return ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setRenderersFactory(renderers)
            .build()
            .apply {
                playWhenReady = true
                // A/V senkron: oyuncu kurulurken son ayardan başlatılır; oynatıcı
                // içinden de değiştirilebilir (anında uygulanır).
                AudioSyncState.delayUs = st.audioDelayMs * 1000L
                // Varsayılan kalite (Ayarlar → Oynatıcı): çözünürlük üst sınırı olarak uygulanır.
                val maxRes = when (st.defaultQuality) {
                    "1080p" -> 1920 to 1080
                    "720p" -> 1280 to 720
                    "480p" -> 854 to 480
                    else -> null
                }
                if (maxRes != null) {
                    trackSelectionParameters = trackSelectionParameters
                        .buildUpon()
                        .setMaxVideoSize(maxRes.first, maxRes.second)
                        .build()
                }
                // Altyazılar ayardan kapalıysa metin parçaları baştan devre dışı.
                if (!st.subtitlesEnabled) {
                    trackSelectionParameters = trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                }
                // Varsayılan ses/altyazı dili (Oynatıcı ayarları): ISO kodu
                // verilmişse o dil öncelikli seçilir, boşsa otomatik kalır.
                if (st.preferredAudioLang.isNotBlank()) {
                    trackSelectionParameters = trackSelectionParameters
                        .buildUpon()
                        .setPreferredAudioLanguage(st.preferredAudioLang)
                        .build()
                }
                if (st.preferredSubtitleLang.isNotBlank()) {
                    trackSelectionParameters = trackSelectionParameters
                        .buildUpon()
                        .setPreferredTextLanguage(st.preferredSubtitleLang)
                        .build()
                }
            }
    }

    // ---------- Public playback control ----------

    fun playChannel(
        channels: List<Channel>,
        index: Int,
        profile: Profile?,
        logo: String = "",
        subtitle: String = ""
    ) {
        // Yeni kanal seçimi: önceki oturumdan kalma "sonraki kanal" ön-buffer'ı
        // varsa serbest bırak (aksi halde arka planda ses vermeye devam edebilir).
        releaseStandby()
        // Kullanıcı kanal seçti/kanal değiştirdi: otomatik retry sayacı sıfırlanır.
        liveRetryCount = 0
        ChannelQueue.channels = channels
        ChannelQueue.index = index
        ChannelQueue.profile = profile
        vodPlayback = false
        val ch = channels.getOrNull(index) ?: return
        scope.launch {
            val url = try {
                repository.channelStreamUrl(ch, profile)
            } catch (e: Exception) {
                setError(l10n("Akış alınamadı") + ": ${e.message ?: e::class.simpleName}")
                return@launch
            }
            if (url.isBlank()) {
                setError(l10n("Kanal akış URL'si boş"))
                return@launch
            }
            // "Açılışta son kanalı oynat" için son izlenen canlı kanalı kaydet.
            store.saveLastLiveChannel(store.activeSourceKind(), store.activeSourceId() ?: "", ch.id)
            trackRecentChannel(ch)
            playInternal(url, ch.name, logo.ifEmpty { ch.logo }, subtitle.ifEmpty { ch.tvGenreTitle }, isVod = false)
            prepareNextChannelForZapping()
        }
    }

    fun play(
        url: String,
        title: String,
        artwork: String = "",
        subtitle: String = "",
        startPositionMs: Long = 0
    ) {
        // Kanal ön-buffer'ı varsa serbest bırak: VOD başlarken arka planda
        // eski kanal sesi çalmamalı.
        releaseStandby()
        // Film oynatımı: önceki bir diziden kalma bölüm kuyruğu temizlenir,
        // böylece oynatıcıda "Sonraki Bölüm" butonu yalnızca gerçek dizilerde görünür.
        // currentVodId SIFIRLANMAZ: çağıran (VodDetailScreen) film id'sini önceden
        // atar ve ilerleme kaydı bu id'ye bağlıdır — sıfırlanırsa film ilerlemesi
        // asla kaydedilmez (resume + "İzlemeye Devam" + izlendi işareti kırılırdı).
        VodQueue.item = null
        VodQueue.episodes = emptyList()
        playInternal(url, title, artwork, subtitle, isVod = true, startPositionMs = startPositionMs)
    }

    private fun playInternal(
        url: String,
        title: String,
        artwork: String = "",
        subtitle: String = "",
        isVod: Boolean,
        startPositionMs: Long = 0
    ) {
        setError(null)
        // Varsayılan oynatıcı "Harici" ise içerik sistem oynatıcısında açılır
        // (Ayarlar → Oynatıcı → Varsayılan Oynatıcı). PlayerScreen bu bayrağı
        // görüp kendini kapatır; yerel oynatma başlatılmaz.
        if (store.settings().defaultPlayer == "external") {
            externalPlaybackLaunched = runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setType("video/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
            }.isSuccess
            if (!externalPlaybackLaunched) {
                setError(l10n("Harici oynatıcı bulunamadı (video desteği olan bir uygulama yükleyin)"))
            }
            stopping = true
            activePlayer?.stop()
            standbyPlayer?.stop()
            stopService()
            return
        }
        stopping = false
        vodPlayback = isVod
        currentStreamUrl = url
        currentTitle = title
        currentSubtitle = subtitle
        currentArtwork = artwork
        val item = mediaItem(url, title, artwork)
        // Yayın oturumu bağlıysa içerik doğrudan TV'ye gönderilir.
        if (isCasting()) {
            val cp = castPlayer
            if (cp != null) {
                cp.setMediaItem(item)
                cp.prepare()
                cp.playWhenReady = true
                cp.seekTo(startPositionMs.coerceAtLeast(0))
                cp.setPlaybackSpeed(store.settings().playbackSpeed.coerceIn(0.5f, 2f))
                activePlayer?.pause()
                startService()
                updateNotification()
                return
            }
        }
        val p = ensureActivePlayer()
        p.setMediaItem(item)
        p.prepare()
        p.playWhenReady = true
        p.seekTo(startPositionMs.coerceAtLeast(0))
        // Varsayılan oynatma hızı (Ayarlar → Oynatıcı).
        p.setPlaybackSpeed(store.settings().playbackSpeed.coerceIn(0.5f, 2f))
        startService()
        updateNotification()
    }

    /**
     * Bir dizi bölümünü kuyruk bilgisiyle oynatır (binge modu / sonraki bölüm için).
     * Kuyruk, [VodQueue] içinde tutulur; altyazıda "S1B3" gibi etiket gösterilir.
     */
    fun playEpisode(
        item: VodItem,
        profile: Profile?,
        episodes: List<Episode>,
        season: Long,
        index: Int,
        startPositionMs: Long = 0
    ) {
        val ep = episodes.getOrNull(index) ?: return
        VodQueue.item = item
        VodQueue.profile = profile
        VodQueue.episodes = episodes
        VodQueue.season = season
        VodQueue.index = index
        currentVodItem = item
        scope.launch {
            val url = try {
                repository.vodStreamUrl(item, profile, ep)
            } catch (e: Exception) {
                setError(l10n("Akış alınamadı") + ": ${e.message ?: e::class.simpleName}")
                return@launch
            }
            if (url.isBlank()) {
                setError(l10n("Akış URL'si boş"))
                return@launch
            }
            currentVodId = item.id
            val subtitle = buildString {
                append("S${VodQueue.season}B${ep.episodeNumber}")
                if (ep.name.isNotBlank()) append(" · ").append(ep.name)
            }
            playInternal(url, item.name, item.poster, subtitle, isVod = true, startPositionMs = startPositionMs)
        }
    }

    /** Sıradaki bölümü oynatır; [auto] ise önceki bölüm izlendi işaretlenir. */
    fun playNextEpisode(auto: Boolean = false): Boolean {
        if (!vodPlayback || !VodQueue.hasNext) return false
        if (auto) markCurrentEpisodeWatched()
        VodQueue.index++
        val item = VodQueue.item ?: return false
        // Profil M3U/Xtream kaynaklarında null olabilir — URL'yi repository
        // aktif kaynağa göre çözer (Stalker dışı kaynaklar profil istemez).
        val profile = VodQueue.profile
        val ep = VodQueue.current ?: return false
        currentVodItem = item
        scope.launch {
            val url = try {
                repository.vodStreamUrl(item, profile, ep)
            } catch (e: Exception) {
                setError(l10n("Akış alınamadı") + ": ${e.message ?: e::class.simpleName}")
                return@launch
            }
            currentVodId = item.id
            val subtitle = buildString {
                append("S${VodQueue.season}B${ep.episodeNumber}")
                if (ep.name.isNotBlank()) append(" · ").append(ep.name)
            }
            playInternal(url, item.name, item.poster, subtitle, isVod = true)
        }
        return true
    }

    /** İzlenmekte olan bölümü "izlendi" olarak işaretler. */
    private fun markCurrentEpisodeWatched() {
        val cur = VodQueue.current ?: return
        val item = VodQueue.item ?: return
        if (item.seriesId > 0 || item.isSeries) {
            store.markEpisodeWatched("${item.id}:${VodQueue.season}:${cur.episodeNumber}")
        }
    }

    fun seekTo(positionMs: Long) {
        displayPlayer()?.seekTo(positionMs)
    }

    fun seekForward(ms: Long = 10_000L) {
        val p = displayPlayer() ?: return
        val dur = if (p.duration > 0) p.duration else Long.MAX_VALUE
        val newPos = (p.currentPosition + ms).coerceAtMost(dur)
        p.seekTo(newPos)
    }

    fun seekBack(ms: Long = 10_000L) {
        val p = displayPlayer() ?: return
        val newPos = (p.currentPosition - ms).coerceAtLeast(0L)
        p.seekTo(newPos)
    }

    fun nextChannel(): Boolean {
        val channels = ChannelQueue.channels
        val nextIndex = ChannelQueue.index + 1
        if (nextIndex >= channels.size) return false
        val profile = ChannelQueue.profile
        // Hazır bekleyen (ön tampon) bir sonraki kanal varsa, referansları takasla
        // ve anında zapping yap (yeniden akış çekmeye gerek yok).
        val standby = standbyPlayer
        if (standby != null) {
            try {
                val tmp = activePlayer
                activePlayer = standby
                standbyPlayer = null
                // Eski oynatıcıyı release() ile serbest bırak (stop() yerine):
                // stop() dinleyicide STATE_IDLE tetikler ve eski akışın kaynakları
                // (decoder/bağlantı) takas sonrası boşta kalır.
                tmp?.release()
                // Standby sessiz (playWhenReady=false) kuruldu; takas edilince çalsın.
                activePlayer!!.playWhenReady = true
                attachListener(activePlayer!!)
                notifyPlayerChanged()
                ChannelQueue.index = nextIndex
                updateNotification()
                prepareNextChannelForZapping()
                return true
            } catch (_: Exception) {
                // Takas başarısızsa normal akışa geri dön.
                standbyPlayer = null
            }
        }
        playChannel(channels, nextIndex, profile)
        return true
    }

    /**
     * Canlı TV'de zapping gecikmesini azaltmak için sıradaki kanalı önceden
     * hazırlar (ön tampon / pre-buffer). Standby ExoPlayer'ı oluşturur ve
     * bir sonraki kanalın akışını çekip [prepare] eder. Her şey try/catch ile
     * sarılır; hazırlanamazsa sessizce yok sayılır (normal akış etkilenmez).
     */
    private fun prepareNextChannelForZapping() {
        // Ayardan kapatılabilir (Oynatıcı → Kanal Ön Yükleme). Kapalıyken sıradaki
        // kanal önceden hazırlanmaz; kanal değişince normal akışla açılır.
        if (!store.settings().zappingPrefetch) return
        // Xtream panelleri bağlantı limiti uygular (max_connections); ön yükleme
        // sıradaki kanal için ekstra bir akış bağlantısı açıp AKTİF kanalın
        // bağlantısını düşürebilir → "belli bir süre sonra hiçbir şey oynatmıyor".
        if (store.activeSourceKind() == "xtream") return
        val next = ChannelQueue.next ?: return
        val queue = ChannelQueue.channels
        scope.launch {
            try {
                val url = repository.channelStreamUrl(next, ChannelQueue.profile) ?: return@launch
                if (url.isBlank()) return@launch
                if (standbyPlayer != null) return@launch
                val p = buildPlayer()
                // Ön-tampon yalnızca: playWhenReady=false olduğu için ses çıkmaz ve
                // audio-focus alınmaz. buildPlayer() playWhenReady=true kurar; bu
                // yüzden değer prepare() ÖNCESİNDE kapatılmalı — aksi halde hazırlık
                // sırasında sıradaki kanal bir anlığına çalmaya başlayabilir.
                p.playWhenReady = false
                p.setMediaItem(mediaItem(url, next.name, next.logo))
                p.prepare()
                // Bu sırada kuyruk değiştiyse (yeni kanal seçildi) standby artık
                // geçersizdir; boşa kurulan player'ı serbest bırak.
                if (ChannelQueue.channels !== queue) {
                    p.release()
                    return@launch
                }
                standbyPlayer = p
            } catch (_: Exception) {
                standbyPlayer = null
            }
        }
    }

    private fun releaseStandby() {
        standbyPlayer?.release()
        standbyPlayer = null
    }

    fun previousChannel(): Boolean {
        val channels = ChannelQueue.channels
        val prevIndex = ChannelQueue.index - 1
        if (prevIndex < 0) return false
        // Xtream/M3U'da profil null olabilir; playChannel zaten Profile? kabul eder.
        playChannel(channels, prevIndex, ChannelQueue.profile)
        return true
    }

    fun togglePlayPause() {
        val p = displayPlayer() ?: return
        p.playWhenReady = !p.playWhenReady
    }

    fun setPlaybackSpeed(speed: Float) {
        displayPlayer()?.setPlaybackSpeed(speed)
    }

    fun isPlaying(): Boolean = displayPlayer()?.playWhenReady == true

    fun pause() {
        displayPlayer()?.pause()
    }

    /**
     * A/V senkron: ses gecikmesini (ms) anında uygular ve ayarlara kaydeder.
     * Pozitif = ses videoya göre gecikir. (-500..+500 ms)
     */
    fun setAudioDelayMs(ms: Int) {
        AudioSyncState.delayUs = ms * 1000L
        val st = store.settings()
        if (st.audioDelayMs != ms) {
            store.saveSettings(st.copy(audioDelayMs = ms.coerceIn(-500, 500)))
        }
    }

    /** Harici oynatıcıya içerik gönderildi mi? (PlayerScreen bu bayrağı izler.) */
    fun hasExternalLaunch(): Boolean = externalPlaybackLaunched

    /** Harici oynatma bayrağını okuyup temizler (tek seferlik). */
    fun consumeExternalLaunch(): Boolean {
        if (!externalPlaybackLaunched) return false
        externalPlaybackLaunched = false
        return true
    }

    // ---------- Uyku zamanlayıcısı ----------

    // Kalan süre (sn). 0 = kapalı; -1 = "bölüm sonunda dur" modu.
    @Volatile private var sleepRemainingSec = 0L
    private var sleepUntilEpisodeEnd = false
    private var sleepJob: Job? = null
    private val sleepListeners = CopyOnWriteArrayList<(Long) -> Unit>()

    /** Uyku zamanlayıcısını ayarlar (dakika). 0 = kapat. Süre dolunca oynatma durur. */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepUntilEpisodeEnd = false
        if (minutes <= 0) {
            sleepRemainingSec = 0
            notifySleepChanged()
            return
        }
        sleepRemainingSec = minutes * 60L
        notifySleepChanged()
        sleepJob = scope.launch {
            while (sleepRemainingSec > 0) {
                delay(1000)
                sleepRemainingSec--
                notifySleepChanged()
                if (sleepRemainingSec <= 0) {
                    // Süre doldu: oynatmayı durdur (bildirim de kapanır).
                    pause()
                    stopService()
                }
            }
        }
    }

    /** "Bölüm sonunda dur": geçerli bölüm/medya bitince oynatma kapanır. */
    fun setSleepUntilEpisodeEnd() {
        sleepJob?.cancel()
        sleepRemainingSec = -1
        sleepUntilEpisodeEnd = true
        notifySleepChanged()
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepRemainingSec = 0
        sleepUntilEpisodeEnd = false
        notifySleepChanged()
    }

    /** Kalan süre (sn); -1 = bölüm sonu modu; 0 = kapalı. */
    fun sleepTimerRemainingSec(): Long = sleepRemainingSec

    fun addSleepListener(l: (Long) -> Unit) {
        sleepListeners.add(l)
        l(sleepRemainingSec)
    }

    fun removeSleepListener(l: (Long) -> Unit) {
        sleepListeners.remove(l)
    }

    private fun notifySleepChanged() {
        sleepListeners.forEach { it(sleepRemainingSec) }
    }

    /** Zamanlayıcıyı bitirir (bölüm sonu modu tetiklendiğinde çağrılır). */
    private fun finishSleepTimer() {
        sleepJob?.cancel()
        sleepRemainingSec = 0
        sleepUntilEpisodeEnd = false
        notifySleepChanged()
    }

    // ---------- Chromecast ----------

    /** Yayın oturumu bağlı mı? (İçerik TV'de oynatılıyor.) */
    fun isCasting(): Boolean = castSessionActive

    /**
     * UI'nin izlemesi gereken oynatıcı: yayın sırasında CastPlayer, aksi halde
     * yerel ExoPlayer. Durum/ilerleme/kontroller bu oynatıcıya bağlanmalıdır.
     */
    fun displayPlayer(): Player? = if (isCasting()) castPlayer else activePlayer

    fun addCastListener(listener: (Boolean) -> Unit) {
        castListeners.add(listener)
        listener(isCasting())
    }

    fun removeCastListener(listener: (Boolean) -> Unit) {
        castListeners.remove(listener)
    }

    private fun notifyCastChanged() {
        val casting = isCasting()
        castListeners.forEach { it(casting) }
        // UI'nin bağladığı oynatıcı değişti (yerel ↔ TV): tüm dinleyicileri uyar.
        notifyPlayerChanged()
        notifyStateChanged()
    }

    /** "Arka planda oynatmaya devam et" ayarı (MainActivity.onStop tarafından okunur). */
    fun isBackgroundPlaybackEnabled(): Boolean = store.settings().backgroundPlayback

    /**
     * Oynatıcı serbest bırakılmadan ÖNCE son konumu kalıcı kaydeder (çıkışta).
     * [stop] oynatıcıyı release eder; bu yüzden PlayerScreen'in onDispose'ındaki
     * kayıt çalışmaz — kayıt stop'tan önce burada yapılmalı.
     */
    fun saveProgressBeforeExit() {
        val p = activePlayer ?: return
        if (!vodPlayback || currentVodId == 0L) return
        val pos = p.currentPosition
        val dur = p.duration
        if (dur <= 0 || pos <= 0) return
        val cur = VodQueue.current
        if (cur != null) {
            store.saveEpisodeProgress(
                "${currentVodId}:${VodQueue.season}:${cur.episodeNumber}",
                pos, dur,
                currentVodItem,
                "S${VodQueue.season}E${cur.episodeNumber}"
            )
        } else {
            store.saveVodProgress(currentVodId, pos, dur, currentVodItem)
        }
    }

    fun stop() {
        // Oynatıcıdan çıkılırken son konum %85+ ise bölüm izlendi işaretlenir
        // (5 sn'lik döngü henüz çalışmadan çıkılmış olabilir; stop konumu sıfırlar).
        checkAutoWatched()
        stopping = true
        externalPlaybackLaunched = false
        // Yayın varsa TV'deki oynatmayı da durdur (bağlantıyı kes).
        if (isCasting()) {
            CastManager.disconnect()
            castPlayer?.stop()
        }
        activePlayer?.stop()
        standbyPlayer?.stop()
        stopService()
        // Kaynak sızıntısını önlemek için oyuncuları tamamen serbest bırak.
        release()
    }

    fun release() {
        activePlayer?.release()
        standbyPlayer?.release()
        activePlayer = null
        standbyPlayer = null
        notifyPlayerChanged()
    }

    // ---------- Track selection (audio / subtitle) ----------

    fun availableTracks(type: Int): List<Pair<String, String>> {
        val p = activePlayer ?: return emptyList()
        val result = LinkedHashMap<String, String>()
        for (group in p.currentTracks.groups) {
            if (group.type != type) continue
            for (i in 0 until group.mediaTrackGroup.length) {
                val f = group.mediaTrackGroup.getFormat(i)
                val lang = f.language ?: "und"
                val label = f.label?.ifBlank { null } ?: lang
                result[lang] = if (lang in result) {
                    val existing = result[lang]!!
                    if (existing.contains(label)) existing else "$existing / $label"
                } else label
            }
        }
        return result.toList()
    }

    fun setAudioLanguage(lang: String?) {
        val p = activePlayer ?: return
        val b = p.trackSelectionParameters.buildUpon()
        if (lang.isNullOrBlank()) b.setPreferredAudioLanguage(null)
        else b.setPreferredAudioLanguage(lang)
        p.trackSelectionParameters = b.build()
    }

    fun setSubtitleLanguage(lang: String?) {
        val p = activePlayer ?: return
        val b = p.trackSelectionParameters.buildUpon()
        if (lang.isNullOrBlank()) b.setPreferredTextLanguage(null)
        else b.setPreferredTextLanguage(lang)
        b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, lang == null)
        p.trackSelectionParameters = b.build()
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        val p = activePlayer ?: return
        val b = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
        p.trackSelectionParameters = b.build()
    }

    fun subtitlesEnabled(): Boolean {
        val p = activePlayer ?: return false
        for (g in p.currentTracks.groups) {
            if (g.type == C.TRACK_TYPE_TEXT && g.isSelected) return true
        }
        return false
    }

    // ---------- PiP ----------

    fun enterPip(activity: Activity?) {
        if (activity == null) return
        // PiP ayardan kapatılmışsa devreye girmez (Ayarlar → Oynatıcı).
        if (!store.settings().pipEnabled) return
        // PiP YALNIZCA içerik gerçekten oynatılırken girilir: aktif oynatıcı
        // yoksa, oynatma duraklatılmışsa ya da medya hazır değilse uygulama
        // alta alınsa bile PiP'e geçilmez (önceden boş ekran PiP'e alıyordu).
        val p = activePlayer ?: return
        if (!p.playWhenReady) return
        if (p.playbackState != Player.STATE_READY && p.playbackState != Player.STATE_BUFFERING) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity.isInPictureInPictureMode.not()) {
            runCatching {
                activity.enterPictureInPictureMode(
                    android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(android.util.Rational(16, 9))
                        .build()
                )
            }
        }
    }

    // ---------- Service / notification ----------

    private fun createNotificationChannel() {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    l10n("Oynatma"),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
            )
        }
    }

    private fun startService() {
        val intent = Intent(appContext, PlaybackService::class.java)
        ContextCompat.startForegroundService(appContext, intent)
    }

    private fun stopService() {
        service?.stopSelf()
    }

    fun updateNotification() {
        val s = service ?: return
        val p = displayPlayer() ?: return
        val metadata = p.mediaMetadata
        val notif = buildNotification(
            s,
            metadata.title?.toString() ?: currentTitle,
            metadata.artist?.toString() ?: currentSubtitle
        )
        s.startForeground(NOTIFICATION_ID, notif)
    }

    private fun buildNotification(context: Context, title: String, subtitle: String): Notification {
        val intent = Intent(context, com.stalkerapp.MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val action = { name: String, icon: Int, act: String ->
            val pi = PendingIntent.getService(
                context,
                act.hashCode(),
                Intent(context, PlaybackService::class.java).setAction(act),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action(icon, name, pi)
        }

        val isPlaying = displayPlayer()?.playWhenReady == true
        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val smallIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title.ifBlank { "Portio" })
            .setContentText(subtitle.ifBlank { l10n("Oynatılıyor…") })
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(action(l10n("Önceki"), R.drawable.ic_prev, ACTION_PREV))
            .addAction(action(if (isPlaying) l10n("Duraklat") else l10n("Oynat"), playPauseIcon, ACTION_TOGGLE))
            .addAction(action(l10n("Sonraki"), R.drawable.ic_next, ACTION_NEXT))
            .addAction(action(l10n("Kapat"), R.drawable.ic_close, ACTION_STOP))
            .setStyle(MediaNotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    // ---------- Listeners ----------

    private fun attachListener(p: Player) {
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                notifyStateChanged()
                when (playbackState) {
                    Player.STATE_ENDED -> {
                        if (vodPlayback) {
                            // Bölüm bitince izlendi işaretle; binge mod açıksa
                            // sıradaki bölüm otomatik oynatılır. Uyku zamanlayıcısı
                            // "bölüm sonunda dur" modundaysa binge kapalı gibi davran.
                            markCurrentEpisodeWatched()
                            if (sleepUntilEpisodeEnd) {
                                finishSleepTimer()
                                stopService()
                            } else if (store.settings().bingeMode && VodQueue.hasNext) {
                                playNextEpisode(auto = false)
                            } else {
                                stopService()
                            }
                        } else {
                            // Canlı akış sağlayıcı tarafından kapatıldı (bağlantı bitti):
                            // hata yoluyla aynı otomatik yeniden bağlanma akışını tetikle.
                            // Token bazlı linkler (Stalker) veya CDN kesintileri bu şekilde
                            // kendiliğinden düzelir; servis durdurulmaz, oynatma öldürülmez.
                            maybeAutoRetryLive()
                        }
                    }
                    Player.STATE_IDLE -> {
                        // Geçici IDLE geçişlerinde (akış hatası sonrası oynatıcının
                        // kendini sıfırlaması vb.) servisi DURDURMA — aksi halde servis
                        // yok olup oynatma komple ölür (siyah ekran). Servis yalnızca
                        // açık durdurma yollarıyla kapatılır.
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Başarılı bir yeniden bağlantı sonrası retry sayacını sıfırla.
                if (isPlaying && p.playbackState == Player.STATE_READY) {
                    liveRetryCount = 0
                }
                // Canlı TV'de oynatma durakladığında/takıldığında ön yüklü sonraki
                // kanal player'ını serbest bırak: iki oynatıcının aynı anda video
                // decoder tutması bazı cihazlarda aktif görüntünün kararmasına yol
                // açar. Akış toparlanınca zapping için yeniden hazırlanır.
                if (!isPlaying && !vodPlayback) {
                    releaseStandby()
                }
                notifyStateChanged()
                updateNotification()
            }

            override fun onPlayerError(error: PlaybackException) {
                // Yeniden deneme sırasında ön yüklü sonraki kanal player'ı kaynak
                // tutmasın (decoder çekişmesi yeniden bağlantıyı bozabilir).
                releaseStandby()
                // Yalnızca bağlantı/timeout tipi hatalarda (veya IOException nedeniyle)
                // otomatik yeniden dene; diğer fatal hatalarda doğrudan hata göster.
                val isConnectionOrTimeout = error.errorCodeName.contains("CONNECTION", ignoreCase = true) ||
                    error.errorCodeName.contains("TIMEOUT", ignoreCase = true) ||
                    error.cause is java.io.IOException
                // Canlı TV (vod değil): geçici kesintilerde otomatik yeniden dener.
                // Yeni bir create_link çağrısı taze play_token üretir; en fazla 3
                // deneme, ardından hata kullanıcıya gösterilir. Ayarlardan kapatılabilir.
                if (!vodPlayback && isConnectionOrTimeout && maybeAutoRetryLive()) return
                liveRetryCount = 0
                setError(error.message ?: l10n("Oynatma hatası"))
                notifyStateChanged()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateNotification()
            }
        })
    }

    fun addPlayerListener(listener: (Player?) -> Unit) {
        playerListeners.add(listener)
        listener(displayPlayer())
    }

    fun removePlayerListener(listener: (Player?) -> Unit) {
        playerListeners.remove(listener)
    }

    fun addStateListener(listener: (Boolean, Boolean) -> Unit) {
        stateListeners.add(listener)
    }

    fun removeStateListener(listener: (Boolean, Boolean) -> Unit) {
        stateListeners.remove(listener)
    }

    private fun notifyPlayerChanged() {
        playerListeners.forEach { it(displayPlayer()) }
    }

    private fun notifyStateChanged() {
        val p = displayPlayer()
        val isPlaying = p?.playWhenReady == true && p.playbackState == Player.STATE_READY
        val buffering = p?.playbackState == Player.STATE_BUFFERING
        stateListeners.forEach { it(isPlaying, buffering) }
    }

    /**
     * Canlı TV akışı kesildiğinde (hata veya sağlayıcının bağlantıyı kapatması)
     * taze URL ile otomatik yeniden bağlanmayı dener. En fazla 3 deneme; her
     * başarılı oynatma [liveRetryCount]'u sıfırlar (uzun süreli stabil akışta
     * sayaç birikmez). Dönen değer: yeniden deneme başlatıldı mı?
     */
    private fun maybeAutoRetryLive(): Boolean {
        if (vodPlayback || stopping) return false
        if (!store.settings().autoRetryLive || ChannelQueue.channels.isEmpty()) return false
        if (liveRetryCount >= 3) return false
        liveRetryCount++
        setError(null)
        notifyStateChanged()
        retryLiveChannel()
        return true
    }

    /** Canlı TV kanalını yeni akış URL'siyle (taze play_token) yeniden oynatmayı dener. */
    private fun retryLiveChannel() {
        val ch = ChannelQueue.current ?: return
        scope.launch {
            // Uyarlanabilir bekleme: paneller bağlantı limiti uyguladığında eski
            // oturumun sunucuda kapanması için daha uzun beklenir (2sn, 4sn, 6sn).
            delay(2000L * liveRetryCount.coerceAtLeast(1))
            // Bu sırada kullanıcı oynatmayı durdurduysa (stop() çağrıldıysa)
            // yeniden başlatma — oynatma öldürülmüş olabilir.
            if (stopping) return@launch
            val url = try {
                repository.channelStreamUrl(ch, ChannelQueue.profile)
            } catch (e: Exception) {
                setError(l10n("Akış alınamadı") + ": ${e.message ?: e::class.simpleName}")
                return@launch
            }
            if (url.isBlank()) {
                setError(l10n("Kanal akış URL'si boş"))
                return@launch
            }
            playInternal(url, ch.name, ch.logo, ch.tvGenreTitle, isVod = false)
        }
    }

    private fun mediaItem(url: String, title: String, artwork: String = ""): MediaItem {
        val builder = MediaItem.Builder().setUri(url)
        val lower = url.lowercase()
        val mimeType = when {
            lower.contains(".m3u8") || lower.contains("/hls/") -> MimeTypes.APPLICATION_M3U8
            lower.contains(".mpd") -> MimeTypes.APPLICATION_MPD
            lower.contains(".ism") -> MimeTypes.APPLICATION_SS
            lower.contains(".mp4") -> MimeTypes.VIDEO_MP4
            lower.contains(".mkv") -> MimeTypes.VIDEO_MATROSKA
            lower.contains(".ts") || lower.endsWith("/ts") -> MimeTypes.VIDEO_MP2T
            else -> null
        }
        if (mimeType != null) {
            builder.setMimeType(mimeType)
        }
        val metaBuilder = MediaMetadata.Builder().setTitle(title)
        val resolvedArtwork = resolveUrl(artwork)
        if (!resolvedArtwork.isNullOrBlank()) {
            runCatching { metaBuilder.setArtworkUri(Uri.parse(resolvedArtwork)) }
        }
        builder.setMediaMetadata(metaBuilder.build())
        return builder.build()
    }

    private fun resolveUrl(url: String): String? {
        if (url.isBlank()) return null
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        val profile = ChannelQueue.profile ?: return null
        val base = profile.baseUrl
        if (base.isBlank()) return null
        return base.trimEnd('/') + "/" + url.trimStart('/')
    }

    fun dpToPx(dp: Int): Int = (dp * Resources.getSystem().displayMetrics.density).toInt()
}
