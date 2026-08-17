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
import com.stalkerapp.playback.RecordingManager
import com.stalkerapp.ui.VodSyncManager
import com.stalkerapp.ui.VodSyncService
import com.stalkerapp.util.isWifiConnected
import com.stalkerapp.util.L10n
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StalkerApp : Application() {

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

    /** Uygulama geneli coroutine scope (arka plan görevleri + bulut senkron). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        repository = PortalRepository(store, StalkerClient { store.settings() })
        vodSyncManager = VodSyncManager(Dispatchers.IO, repository, store)
        tmdb = TmdbClient(
            keyProvider = { store.settings().tmdbApiKey },
            languageProvider = { store.settings().tmdbLanguage }
        )
        PlaybackManager.init(this, store, repository)
        RecordingManager.init(this)
        firebase = FirebaseSyncManager.init(this)
        instance = this
        // Oturum açıksa veri deposunu o hesaba bağla (her hesabın kendi verisi).
        if (firebase.isSignedIn) {
            store.setAccount(firebase.currentUser.value?.uid)
        }
        // Zamanlanmış görevler: 30 sn'de bir kontrol edilir — EPG program
        // hatırlatıcıları (başlama vaktine yaklaşınca bildirim) ve planlı
        // kayıtlar (başlama/bitiş zamanında akış indirme).
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
                runCatching { checkRecordings() }
                delay(30_000)
            }
        }
    }

    /**
     * Planlı kayıtları yönetir: başlama zamanı gelenleri başlatır (akış URL'sini
     * o anda çözer — token'lar bayatlamış olabilir), bitiş zamanı gelenleri kapatır.
     */
    private fun checkRecordings() {
        val now = System.currentTimeMillis() / 1000
        val recs = store.recordings()
        if (recs.isEmpty()) return
        // Başlama zamanı geçmiş ama bitiş zamanı gelmemiş planlı kayıtlar.
        recs.filter { it.status == "scheduled" && now >= it.startTs && now < it.stopTs }.forEach { r ->
            appScope.launch {
                val url = r.streamUrl.ifBlank {
                    runCatching { repository.channelStreamUrl(r.channel, repository.cachedProfile()) }
                        .getOrNull().orEmpty()
                }
                if (url.isBlank()) {
                    store.updateRecording(r.copy(status = "failed"))
                } else {
                    RecordingManager.start(r.id, url, r.stopTs)
                    store.updateRecording(r.copy(status = "recording"))
                }
            }
        }
        // Bitiş zamanı geçen aktif kayıtlar → tamamlandı.
        recs.filter { it.status == "recording" && now >= it.stopTs }.forEach { r ->
            RecordingManager.stop(r.id)
            val file = RecordingManager.fileFor(this, r.id)
            store.updateRecording(
                r.copy(
                    status = if (file.exists() && file.length() > 0) "done" else "failed",
                    filePath = file.absolutePath
                )
            )
        }
        // Başlama zamanı da geçmiş (uygulama kapalıyken kaçırılan) planlı kayıtlar.
        recs.filter { it.status == "scheduled" && now >= it.stopTs }.forEach { r ->
            store.updateRecording(r.copy(status = "cancelled"))
        }
        // 7 günden eski tamamlanmış kayıt kayıtlarını temizle.
        val old = recs.filter { it.status == "done" && now - it.stopTs > 7 * 86400 }
        if (old.isNotEmpty()) {
            store.saveRecordings(recs.filterNot { it in old })
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
