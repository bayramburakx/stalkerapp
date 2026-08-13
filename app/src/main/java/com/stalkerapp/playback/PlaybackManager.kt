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
import com.stalkerapp.data.PortalRepository
import com.stalkerapp.data.Profile
import com.stalkerapp.data.Store
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object ChannelQueue {
    var channels: List<Channel> = emptyList()
    var index: Int = 0
    var profile: Profile? = null

    val current: Channel? get() = channels.getOrNull(index)
    val next: Channel? get() = channels.getOrNull(index + 1)
    val previous: Channel? get() = channels.getOrNull(index - 1)
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
    private var mediaSession: MediaSession? = null

    private val playerListeners = CopyOnWriteArrayList<(ExoPlayer?) -> Unit>()
    private var stateListeners = CopyOnWriteArrayList<(Boolean, Boolean) -> Unit>()

    @Volatile var service: PlaybackService? = null

    var currentTitle: String = ""
    var currentSubtitle: String = ""
        private set

    @Volatile var errorMessage: String? = null
        private set

    fun init(context: Context, store: Store, repository: PortalRepository) {
        appContext = context.applicationContext
        this.store = store
        this.repository = repository
        createNotificationChannel()
    }

    val player: ExoPlayer?
        get() = activePlayer

    private fun ensureActivePlayer(): ExoPlayer {
        activePlayer?.let { return it }
        val p = buildPlayer()
        activePlayer = p
        attachListener(p)
        mediaSession = MediaSession.Builder(appContext, p).build()
        notifyPlayerChanged()
        return p
    }

    private fun ensureStandbyPlayer(): ExoPlayer {
        standbyPlayer?.let { return it }
        val p = buildPlayer()
        standbyPlayer = p
        return p
    }

    private fun buildPlayer(): ExoPlayer {
        val extractorsFactory = DefaultExtractorsFactory()
            .setTsExtractorMode(TsExtractor.MODE_MULTI_PMT)
            .setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES)

        val bufferMs = store.settings().maxBufferMs
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,
                bufferMs,
                2_500,
                5_000
            )
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(appContext, extractorsFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))

        return ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setRenderersFactory(
                DefaultRenderersFactory(appContext)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
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
        val ch = channels.getOrNull(index) ?: return
        scope.launch {
            val url = try {
                repository.channelStreamUrl(ch, profile)
            } catch (e: Exception) {
                errorMessage = "Akış alınamadı: ${e.message ?: e::class.simpleName}"
                return@launch
            }
            if (url.isBlank()) {
                errorMessage = "Kanal akış URL'si boş"
                return@launch
            }
            play(url, ch.name, logo.ifEmpty { ch.logo }, subtitle.ifEmpty { ch.tvGenreTitle })
            prebufferNext()
        }
    }

    fun play(url: String, title: String, artwork: String = "", subtitle: String = "") {
        errorMessage = null
        currentTitle = title
        currentSubtitle = subtitle
        val p = ensureActivePlayer()
        val item = mediaItem(url, title, artwork)
        p.setMediaItem(item)
        p.prepare()
        p.playWhenReady = true
        p.seekTo(0)
        startService()
        updateNotification()
    }

    suspend fun prebufferNext() {
        val next = ChannelQueue.next ?: return
        val profile = ChannelQueue.profile ?: return
        val url = try {
            repository.channelStreamUrl(next, profile)
        } catch (e: Exception) {
            return
        }
        if (url.isBlank()) return
        val sp = ensureStandbyPlayer()
        sp.stop()
        sp.setMediaItem(mediaItem(url, next.name, next.logo))
        sp.prepare()
        sp.playWhenReady = false
        sp.seekTo(0)
    }

    fun nextChannel(): Boolean {
        if (ChannelQueue.next == null) return false
        ChannelQueue.index += 1
        swapPlayers()
        scope.launch { prebufferNext() }
        return true
    }

    fun previousChannel(): Boolean {
        if (ChannelQueue.previous == null) return false
        ChannelQueue.index -= 1
        swapPlayers()
        scope.launch { prebufferNext() }
        return true
    }

    private fun swapPlayers() {
        val active = ensureActivePlayer()
        val standby = ensureStandbyPlayer()
        val wasPlaying = active.playWhenReady
        active.stop()
        active.pause()
        standby.playWhenReady = wasPlaying
        val tmp = activePlayer
        activePlayer = standbyPlayer
        standbyPlayer = tmp
        currentTitle = ChannelQueue.current?.name ?: currentTitle
        notifyPlayerChanged()
        notifyStateChanged()
        updateNotification()
    }

    fun togglePlayPause() {
        val p = activePlayer ?: return
        p.playWhenReady = !p.playWhenReady
    }

    fun isPlaying(): Boolean = activePlayer?.playWhenReady == true

    fun pause() {
        activePlayer?.pause()
    }

    fun stop() {
        activePlayer?.stop()
        standbyPlayer?.stop()
        stopService()
    }

    fun release() {
        activePlayer?.release()
        standbyPlayer?.release()
        mediaSession?.release()
        activePlayer = null
        standbyPlayer = null
        mediaSession = null
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
                .setMediaSession(mediaSession?.sessionCompatToken)
                .setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    // ---------- Listeners ----------

    private fun attachListener(p: Player) {
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                notifyStateChanged()
                when (playbackState) {
                    Player.STATE_IDLE, Player.STATE_ENDED -> {
                        service?.stopSelf()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                notifyStateChanged()
                updateNotification()
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = error.message ?: "Oynatma hatası"
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
            .setMimeType(MimeTypes.APPLICATION_MP2T)
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
