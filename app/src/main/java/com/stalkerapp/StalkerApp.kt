package com.stalkerapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.stalkerapp.data.EpgReminder
import com.stalkerapp.data.FirebaseSyncManager
import com.stalkerapp.data.PortalRepository
import com.stalkerapp.data.StalkerClient
import com.stalkerapp.data.Store
import com.stalkerapp.data.TmdbClient
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.VodSyncManager
import com.stalkerapp.ui.VodSyncService
import com.stalkerapp.data.CacheManager
import com.stalkerapp.data.OfflineDownloadManager
import com.stalkerapp.util.HdmiCecManager
import com.stalkerapp.util.L10n
import com.stalkerapp.util.isWifiConnected
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StalkerApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64 * 1024 * 1024)
                    .build()
            }
            .allowRgb565(true)
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()
    }

    lateinit var store: Store
        private set
    lateinit var repository: PortalRepository
        private set
    lateinit var vodSyncManager: VodSyncManager
        private set
    lateinit var tmdb: TmdbClient
        private set
    lateinit var firebase: FirebaseSyncManager
        private set
    lateinit var cacheManager: CacheManager
        private set

    /** Uygulama geneli coroutine scope (arka plan görevleri + bulut senkron). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("StalkerApp", "Uncaught exception on thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        store = Store(this)
        Store.activeStore = store
        cacheManager = CacheManager(this)
        repository = PortalRepository(store, StalkerClient { store.settings() }, cacheManager)
        vodSyncManager = VodSyncManager(Dispatchers.IO, repository, store)
        tmdb = TmdbClient(
            keyProvider = { store.settings().tmdbApiKey },
            languageProvider = { store.settings().tmdbLanguage }
        )
        PlaybackManager.init(this, store, repository)
        firebase = FirebaseSyncManager.init(this)
        instance = this

        // Yeni yöneticiler
        HdmiCecManager.init(this)
        OfflineDownloadManager.init(this, store.settings().maxOfflineStorageMb)

        // Akıllı önbellek: disk kotası + aktif kaynak sağlık kontrolü.
        appScope.launch {
            runCatching {
                cacheManager.enforceQuota(store.settings().maxOfflineStorageMb * 1024L * 1024L)
                store.activePortal()?.let { p ->
                    cacheManager.checkSourceHealth(p.id, p.url, p.name)
                }
            }
        }

        // Oturum açıksa veri deposunu o hesaba bağla (her hesabın kendi verisi).
        if (firebase.isSignedIn) {
            store.setAccount(firebase.currentUser.value?.uid)
        }
        // Zamanlanmış görev: 30 sn'de bir EPG program hatırlatıcıları kontrol edilir
        // (başlama vaktine yaklaşınca bildirim gönderilir).
        startReminderChecker()

        // Auto-login: diske kayıtlı profil ile doğrudan Ana Sayfa'ya başlanır
        // (login ekranı atlanır). Arka planda sessizce yeniden bağlanılır:
        // token + sunucu profili tazelenir; bağlantı başarısız olursa bile
        // diskteki profil ile devam edilir (MAC parametreleri çoğu istek için
        // yeterlidir).
        val portalId = store.activePortalId().orEmpty()
        if (portalId.isNotBlank()) {
            repository.restoreProfileFromDisk()
            val portal = store.activePortal()
            if (portal != null) {
                appScope.launch { runCatching { repository.connect(portal) } }
            }
            // Resume an interrupted sync (or run the first one) in the background.
            // Kütüphane & İçerik ayarlarına saygı gösterilir: otomatik senkron
            // kapalıysa ya da "yalnızca Wi-Fi" açıkken Wi-Fi yoksa başlatılmaz.
            if (needsSync(portalId)) {
                val s = store.settings()
                val wifiOk = !s.wifiOnlySync || isWifiConnected()
                if (s.autoSyncVod && wifiOk) {
                    repository.cachedProfile()?.let { VodSyncService.start(this) }
                }
            }
        }
    }

    private fun needsSync(portalId: String): Boolean {
        // A complete catalog has a meta file at the current version. Without it
        // (never synced, interrupted mid-sync, or stale format) we resume/sync.
        val meta = store.loadVodCatalogMeta(portalId) ?: return true
        return meta.version < Store.VOD_CATALOG_VERSION
    }

    // ---------- EPG program hatırlatıcıları ----------

    private fun startReminderChecker() {
        appScope.launch {
            while (true) {
                runCatching { checkEpgReminders() }
                delay(30_000)
            }
        }
    }

    private fun checkEpgReminders() {
        val now = System.currentTimeMillis() / 1000
        var list = store.epgReminders()
        if (list.isEmpty()) return
        val toFire = list.filter { !it.fired && it.startTs in (now - 60)..(now + 30) }
        toFire.forEach { postReminderNotification(it) }
        if (toFire.isNotEmpty()) {
            list = list.map { if (it in toFire) it.copy(fired = true) else it }
            store.saveEpgReminders(list)
        }
        // 24 saatten eski (tetiklenmiş) hatırlatıcıları temizle.
        val pruned = list.filter { it.fired && now - it.startTs > 86400 }
        if (pruned.isNotEmpty()) {
            store.saveEpgReminders(list.filterNot { it in pruned })
        }
    }

    private fun postReminderNotification(r: EpgReminder) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    REMINDER_CHANNEL,
                    L10n.t(store.settings().language, "Program Hatırlatıcıları"),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { setShowBadge(true) }
            )
        }
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this,
            r.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(r.programName)
            .setContentText("${r.channelName} ${L10n.t(store.settings().language, "kanalında şimdi başladı")}")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(r.id.hashCode(), notif)
    }

    companion object {
        const val REMINDER_CHANNEL = "epg_reminders"
        lateinit var instance: StalkerApp
            private set
    }
}
