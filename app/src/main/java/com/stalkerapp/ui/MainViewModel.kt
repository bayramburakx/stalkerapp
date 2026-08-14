package com.stalkerapp.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.Genre
import com.stalkerapp.data.Portal
import com.stalkerapp.data.PortalRepository
import com.stalkerapp.data.PortalStatus
import com.stalkerapp.data.Profile
import com.stalkerapp.data.Settings
import com.stalkerapp.data.Store
import com.stalkerapp.data.VodItem
import kotlinx.coroutines.Job
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

    // ---------- VOD catalog (background sync) ----------
    private val _vodCatalog = MutableStateFlow(VodCatalogState())
    val vodCatalog: StateFlow<VodCatalogState> = _vodCatalog
    private var vodSyncJob: Job? = null

    fun syncVodCatalog(profile: Profile, force: Boolean = false) {
        if (_vodCatalog.value.status == VodCatalogStatus.Syncing && !force) return
        vodSyncJob?.cancel()
        vodSyncJob = viewModelScope.launch {
            _vodCatalog.value = _vodCatalog.value.copy(status = VodCatalogStatus.Syncing)
            val acc = mutableListOf<VodItem>()
            try {
                val portalId = profile.portal?.id ?: ""
                repository.syncVodCatalog(
                    profile,
                    onItem = { acc.add(it) },
                    onProgress = { done, total, loaded ->
                        _vodCatalog.value = _vodCatalog.value.copy(
                            doneCategories = done,
                            totalCategories = total,
                            loadedCount = loaded,
                            allItems = acc.toList()
                        )
                    }
                )
                val cats = runCatching { repository.loadVodCategories(profile) }.getOrDefault(emptyList())
                store.saveVodCatalog(portalId, acc, cats)
                _vodCatalog.value = VodCatalogState(
                    status = VodCatalogStatus.Ready,
                    doneCategories = cats.size,
                    totalCategories = cats.size,
                    loadedCount = acc.size,
                    allItems = acc,
                    categories = cats,
                    lastSync = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                if (_vodCatalog.value.status != VodCatalogStatus.Ready) {
                    _vodCatalog.value = _vodCatalog.value.copy(status = VodCatalogStatus.Error)
                }
                showMessage("VOD senkron hatası: ${e.message}")
            }
        }
    }

    /**
     * Ensures the VOD catalog is available. On app launch this just loads the
     * persisted disk cache (instant, NO network sync) so the user is not forced
     * to re-sync every time. A background sync only runs once when there is no
     * cache at all (e.g. first run or after switching to a new portal). Use
     * [syncVodCatalog] for an explicit manual refresh.
     */
    fun syncVodIfNeeded(profile: Profile) {
        val cur = _vodCatalog.value
        if (cur.status == VodCatalogStatus.Syncing) return
        val portalId = profile.portal?.id ?: ""
        val cached = store.loadVodCatalog(portalId)
        if (cached != null && cached.first.isNotEmpty()) {
            _vodCatalog.value = VodCatalogState(
                status = VodCatalogStatus.Ready,
                doneCategories = cached.second.size,
                totalCategories = cached.second.size,
                loadedCount = cached.first.size,
                allItems = cached.first,
                categories = cached.second,
                lastSync = cached.third
            )
            return
        }
        syncVodCatalog(profile, force = true)
    }

    fun resetVodCatalog() {
        vodSyncJob?.cancel()
        _vodCatalog.value = VodCatalogState()
    }

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

    /** Switches the active portal, reconnects, and re-syncs the VOD catalog. */
    suspend fun switchPortal(portal: Portal): Result<Unit> = runCatching {
        store.setActivePortalId(portal.id)
        repository.clearCaches()
        repository.connect(portal)
        resetVodCatalog()
        repository.cachedProfile()?.let { syncVodIfNeeded(it) }
    }

    fun launchSwitch(portal: Portal, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            switchPortal(portal).onFailure { showMessage("Portal değiştirilemedi: ${it.message}") }
            onDone()
        }
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

enum class VodCatalogStatus { Idle, Syncing, Ready, Error }

data class VodCatalogState(
    val status: VodCatalogStatus = VodCatalogStatus.Idle,
    val doneCategories: Int = 0,
    val totalCategories: Int = 0,
    val loadedCount: Int = 0,
    val allItems: List<VodItem> = emptyList(),
    val categories: List<Genre> = emptyList(),
    val lastSync: Long = 0
) {
    val byId: Map<Long, VodItem> get() = allItems.associateBy { it.id }
    val isSeriesItem: (VodItem) -> Boolean = { item ->
        item.isSeries || item.seriesData.isNotBlank() || item.selectedSeason.isNotBlank() ||
            categories.any { c -> c.id == item.categoryId && c.title.contains("dizi", ignoreCase = true) }
    }
}

@Composable
fun rememberMainViewModel(app: StalkerApp): MainViewModel =
    viewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity) {
        MainViewModel(app)
    }
