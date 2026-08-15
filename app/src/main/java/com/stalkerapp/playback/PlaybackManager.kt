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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.session.MediaSession
import com.stalkerapp.R
import com.stalkerapp.data.Channel
import com.stalkerapp.data.Episode
import com.stalkerapp.data.PortalRepository
import com.stalkerapp.data.Profile
import com.stalkerapp.data.Store
import com.stalkerapp.data.VodItem
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var activePlayer: ExoPlayer? = null
    private var standbyPlayer: ExoPlayer? = null

    private var vodPlayback: Boolean = false
    fun isVod(): Boolean = vodPlayback

    private val playerListeners = CopyOnWriteArrayList<(ExoPlayer?) -> Unit>()
    private var stateListeners = CopyOnWriteArrayList<(Boolean, Boolean) -> Unit>()
    private val errorListeners = CopyOnWriteArrayList<(String?) -> Unit>()

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

    @Volatile var errorMessage: String? = null
        private set

    fun init(context: Context, store: Store, repository: PortalRepository) {
        appContext = context.applicationContext
        this.store = store
        this.repository = repository
        createNotificationChannel()
        // Bölüm %85 izlendiğinde otomatik "izlendi" işareti (ekran arka planda
        // olsa da, PiP/arka plan oynatmada bile) — 5 sn'de bir kontrol edilir.
        scope.launch {
            while (true) {
                delay(5000)
                checkAutoWatched()
            }
        }
    }

    /** Bölüm %85+ izlendiyse otomatik "izlendi" işaretler (tekrar tekrar işaretlemez). */
    private fun checkAutoWatched() {
        val p = activePlayer ?: return
        if (!vodPlayback) return
        val cur = VodQueue.current ?: return
        val item = VodQueue.item ?: return
        if (!item.isSeries && item.seriesId <= 0) return
        val dur = p.duration
        val pos = p.currentPosition
        if (dur > 0 && pos >= dur * 0.85) {
            val key = "${item.id}:${VodQueue.season}:${cur.episodeNumber}"
            if (!store.isEpisodeWatched(key)) store.markEpisodeWatched(key)
        }
    }

    val player: ExoPlayer?
        get() = activePlayer

    private fun ensureActivePlayer(): ExoPlayer {
        activePlayer?.let { return it }
        val p = buildPlayer()
        activePlayer = p
        attachListener(p)
        notifyPlayerChanged()
        return p
    }

    private fun buildPlayer(): ExoPlayer {
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setTsExtractorMode(TsExtractor.MODE_MULTI_PMT)
            .setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES)

        val bufferMs = store.settings().maxBufferMs.coerceAtMost(30_000)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                5_000,
                bufferMs,
                1_000,
                2_000
            )
            .build()

        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setUserAgent("Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(appContext, httpDataSourceFactory)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))

        return ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setRenderersFactory(
                DefaultRenderersFactory(appContext)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            )
            .build()
            .apply {
                playWhenReady = true
            }
    }

    // ---------- Public playback control ----------

    fun playChannel(
        channels: List<Channel>,
        index: Int,
        profile: Profile,
        logo: String = "",
        subtitle: String = ""
    ) {
        ChannelQueue.channels = channels
        ChannelQueue.index = index
        ChannelQueue.profile = profile
        vodPlayback = false
        val ch = channels.getOrNull(index) ?: return
        scope.launch {
            val url = try {
                repository.channelStreamUrl(ch, profile)
            } catch (e: Exception) {
                setError("Akış alınamadı: ${e.message ?: e::class.simpleName}")
                return@launch
            }
            if (url.isBlank()) {
                setError("Kanal akış URL'si boş")
                return@launch
            }
            playInternal(url, ch.name, logo.ifEmpty { ch.logo }, subtitle.ifEmpty { ch.tvGenreTitle }, isVod = false)
        }
    }

    fun play(
        url: String,
        title: String,
        artwork: String = "",
        subtitle: String = "",
        startPositionMs: Long = 0
    ) {
        // Film oynatımı: önceki bir diziden kalma bölüm kuyruğu temizlenir,
        // böylece oynatıcıda "Sonraki Bölüm" butonu yalnızca gerçek dizilerde görünür.
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
        vodPlayback = isVod
        currentStreamUrl = url
        currentTitle = title
        currentSubtitle = subtitle
        val p = ensureActivePlayer()
        val item = mediaItem(url, title, artwork)
        p.setMediaItem(item)
        p.prepare()
        p.playWhenReady = true
        p.seekTo(startPositionMs.coerceAtLeast(0))
        startService()
        updateNotification()
    }

    /**
     * Bir dizi bölümünü kuyruk bilgisiyle oynatır (binge modu / sonraki bölüm için).
     * Kuyruk, [VodQueue] içinde tutulur; altyazıda "S1B3" gibi etiket gösterilir.
     */
    fun playEpisode(
        item: VodItem,
        profile: Profile,
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
        scope.launch {
            val url = try {
                repository.vodStreamUrl(item, profile, ep)
            } catch (e: Exception) {
                setError("Akış alınamadı: ${e.message ?: e::class.simpleName}")
                return@launch
            }
            if (url.isBlank()) {
                setError("Akış URL'si boş")
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
        val profile = VodQueue.profile ?: return false
        val ep = VodQueue.current ?: return false
        scope.launch {
            val url = try {
                repository.vodStreamUrl(item, profile, ep)
            } catch (e: Exception) {
                setError("Akış alınamadı: ${e.message ?: e::class.simpleName}")
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
        activePlayer?.seekTo(positionMs)
    }

    fun seekForward(ms: Long = 10_000L) {
        val p = activePlayer ?: return
        val dur = if (p.duration > 0) p.duration else Long.MAX_VALUE
        val newPos = (p.currentPosition + ms).coerceAtMost(dur)
        p.seekTo(newPos)
    }

    fun seekBack(ms: Long = 10_000L) {
        val p = activePlayer ?: return
        val newPos = (p.currentPosition - ms).coerceAtLeast(0L)
        p.seekTo(newPos)
    }

    fun nextChannel(): Boolean {
        val channels = ChannelQueue.channels
        val nextIndex = ChannelQueue.index + 1
        if (nextIndex >= channels.size) return false
        val profile = ChannelQueue.profile ?: return false
        playChannel(channels, nextIndex, profile)
        return true
    }

    fun previousChannel(): Boolean {
        val channels = ChannelQueue.channels
        val prevIndex = ChannelQueue.index - 1
        if (prevIndex < 0) return false
        val profile = ChannelQueue.profile ?: return false
        playChannel(channels, prevIndex, profile)
        return true
    }

    fun togglePlayPause() {
        val p = activePlayer ?: return
        p.playWhenReady = !p.playWhenReady
    }

    fun setPlaybackSpeed(speed: Float) {
        activePlayer?.setPlaybackSpeed(speed)
    }

    fun isPlaying(): Boolean = activePlayer?.playWhenReady == true

    fun pause() {
        activePlayer?.pause()
    }

    fun stop() {
        // Oynatıcıdan çıkılırken son konum %85+ ise bölüm izlendi işaretlenir
        // (5 sn'lik döngü henüz çalışmadan çıkılmış olabilir; stop konumu sıfırlar).
        checkAutoWatched()
        activePlayer?.stop()
        standbyPlayer?.stop()
        stopService()
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
                result[lang] = label
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
                    "Oynatma",
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
        val p = activePlayer ?: return
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

        val isPlaying = activePlayer?.playWhenReady == true
        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(title.ifBlank { "Stalker Player" })
            .setContentText(subtitle.ifBlank { "Oynatılıyor…" })
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(action("Önceki", R.drawable.ic_prev, ACTION_PREV))
            .addAction(action(if (isPlaying) "Duraklat" else "Oynat", playPauseIcon, ACTION_TOGGLE))
            .addAction(action("Sonraki", R.drawable.ic_next, ACTION_NEXT))
            .addAction(action("Kapat", R.drawable.ic_close, ACTION_STOP))
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
                            // sıradaki bölüm otomatik oynatılır.
                            markCurrentEpisodeWatched()
                            if (store.settings().bingeMode && VodQueue.hasNext) {
                                playNextEpisode(auto = false)
                            } else {
                                service?.stopSelf()
                            }
                        } else {
                            service?.stopSelf()
                        }
                    }
                    Player.STATE_IDLE -> {
                        service?.stopSelf()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                notifyStateChanged()
                updateNotification()
            }

            override fun onPlayerError(error: PlaybackException) {
                setError(error.message ?: "Oynatma hatası")
                notifyStateChanged()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateNotification()
            }
        })
    }

    fun addPlayerListener(listener: (ExoPlayer?) -> Unit) {
        playerListeners.add(listener)
        listener(activePlayer)
    }

    fun removePlayerListener(listener: (ExoPlayer?) -> Unit) {
        playerListeners.remove(listener)
    }

    fun addStateListener(listener: (Boolean, Boolean) -> Unit) {
        stateListeners.add(listener)
    }

    fun removeStateListener(listener: (Boolean, Boolean) -> Unit) {
        stateListeners.remove(listener)
    }

    private fun notifyPlayerChanged() {
        playerListeners.forEach { it(activePlayer) }
    }

    private fun notifyStateChanged() {
        val p = activePlayer
        val isPlaying = p?.playWhenReady == true && p.playbackState == Player.STATE_READY
        val buffering = p?.playbackState == Player.STATE_BUFFERING
        stateListeners.forEach { it(isPlaying, buffering) }
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
