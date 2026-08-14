package com.stalkerapp

import android.app.Application
import com.stalkerapp.data.PortalRepository
import com.stalkerapp.data.StalkerClient
import com.stalkerapp.data.Store
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.VodSyncManager
import com.stalkerapp.ui.VodSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StalkerApp : Application() {

    lateinit var store: Store
        private set
    lateinit var repository: PortalRepository
        private set
    lateinit var vodSyncManager: VodSyncManager
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        repository = PortalRepository(store, StalkerClient { store.settings() })
        vodSyncManager = VodSyncManager(Dispatchers.IO, repository, store)
        PlaybackManager.init(this, store, repository)
        instance = this

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
                scope.launch { runCatching { repository.connect(portal) } }
            }
            // Resume an interrupted sync (or run the first one) in the background.
            if (needsSync(portalId)) {
                repository.cachedProfile()?.let { VodSyncService.start(this) }
            }
        }
    }

    private fun needsSync(portalId: String): Boolean {
        // A complete catalog has a meta file at the current version. Without it
        // (never synced, interrupted mid-sync, or stale format) we resume/sync.
        val meta = store.loadVodCatalogMeta(portalId) ?: return true
        return meta.version < Store.VOD_CATALOG_VERSION
    }

    companion object {
        lateinit var instance: StalkerApp
            private set
    }
}
