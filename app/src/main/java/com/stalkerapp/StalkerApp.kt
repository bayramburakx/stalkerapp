package com.stalkerapp

import android.app.Application
import com.stalkerapp.data.PortalRepository
import com.stalkerapp.data.StalkerClient
import com.stalkerapp.data.Store
import com.stalkerapp.playback.PlaybackManager

class StalkerApp : Application() {

    lateinit var store: Store
        private set
    lateinit var repository: PortalRepository
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        repository = PortalRepository(store, StalkerClient { store.settings() })
        PlaybackManager.init(this, store, repository)
    }
}
