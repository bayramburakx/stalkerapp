package com.stalkerapp.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Portal
import com.stalkerapp.data.PortalRepository
import com.stalkerapp.data.PortalStatus
import com.stalkerapp.data.Profile
import com.stalkerapp.data.Settings
import com.stalkerapp.data.Store
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val app: StalkerApp) : ViewModel() {

    val store: Store get() = app.store
    val repository: PortalRepository get() = app.repository

    val portalStatus: StateFlow<PortalStatus> = repository.status

    private val _settings = MutableStateFlow(store.settings())
    val settings: StateFlow<Settings> = _settings

    private val _favorites = MutableStateFlow(store.favorites())
    val favorites: StateFlow<Set<String>> = _favorites

    private val _favoriteChannels = MutableStateFlow(store.favoriteChannels())
    val favoriteChannels: StateFlow<List<Channel>> = _favoriteChannels

    private val _favoriteVods = MutableStateFlow(store.favoriteVods())
    val favoriteVods: StateFlow<List<VodItem>> = _favoriteVods

    private val _cooldown = MutableStateFlow(repository.cooldownRemainingSeconds())
    val cooldownSeconds: StateFlow<Long> = _cooldown

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    init {
        viewModelScope.launch {
            while (true) {
                _cooldown.value = repository.cooldownRemainingSeconds()
                delay(1000)
            }
        }
    }

    suspend fun connect(portal: Portal): Result<Profile> =
        runCatching { repository.connect(portal) }

    fun saveSettings(s: Settings) {
        store.saveSettings(s)
        _settings.value = s
    }

    fun savePortal(portal: Portal) {
        store.savePortal(portal)
    }

    fun deletePortal(id: String) {
        store.deletePortal(id)
    }

    fun toggleFavorite(key: String): Boolean {
        val added = store.toggleFavorite(key)
        _favorites.value = store.favorites()
        _favoriteChannels.value = store.favoriteChannels()
        _favoriteVods.value = store.favoriteVods()
        return added
    }

    fun toggleFavoriteChannel(channel: Channel): Boolean {
        val added = store.toggleFavoriteChannel(channel)
        _favoriteChannels.value = store.favoriteChannels()
        _favorites.value = store.favorites()
        return added
    }

    fun toggleFavoriteVod(vod: VodItem): Boolean {
        val added = store.toggleFavoriteVod(vod)
        _favoriteVods.value = store.favoriteVods()
        _favorites.value = store.favorites()
        return added
    }

    fun isFavoriteChannel(id: Long): Boolean = store.isFavoriteChannel(id)
    fun isFavoriteVod(id: Long): Boolean = store.isFavoriteVod(id)

    fun clearCooldown() {
        repository.clearCooldown()
        _cooldown.value = 0
    }

    fun showMessage(msg: String?) {
        _statusMessage.value = msg
    }
}
