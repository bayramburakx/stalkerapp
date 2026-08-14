package com.stalkerapp

import android.app.Application
import com.stalkerapp.data.PortalRepository
import com.stalkerapp.data.StalkerClient
import com.stalkerapp.data.Store
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.ui.VodSyncManager
import com.stalkerapp.ui.VodSyncService
import kotlinx.coroutines.Dispatchers

class StalkerApp : Application() {

    lateinit var store: Store
        private set
    lateinit var repository: PortalRepository
        private set
    lateinit var vodSyncManager: VodSyncManager
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        repository = PortalRepository(store, StalkerClient { store.settings() })
        vodSyncManager = VodSyncManager(Dispatchers.IO, repository, store)
        PlaybackManager.init(this, store, repository)
        instance = this

        // Resume an interrupted sync (or run the first one) in the background.
        val portalId = store.activePortalId().orEmpty()
        if (portalId.isNotBlank() && needsSync(portalId)) {
            repository.cachedProfile()?.let { VodSyncService.start(this) }
        }
    }

    private fun needsSync(portalId: String): Boolean {
        val cached = store.loadVodCatalog(portalId)
        val doneCats = store.loadVodCatalogDoneCats(portalId)
        return cached == null || cached.first.isEmpty() || doneCats.isNotEmpty()
    }

    companion object {
        lateinit var instance: StalkerApp
            private set
    }
}
