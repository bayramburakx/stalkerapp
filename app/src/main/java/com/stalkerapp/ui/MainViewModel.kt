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
import com.stalkerapp.data.M3uParser
import com.stalkerapp.data.M3uSource
import com.stalkerapp.data.Portal
import com.stalkerapp.data.PortalRepository
import com.stalkerapp.data.PortalStatus
import com.stalkerapp.data.Profile
import com.stalkerapp.data.Settings
import com.stalkerapp.data.Store
import com.stalkerapp.data.VodItem
import com.stalkerapp.data.XtreamClient
import com.stalkerapp.data.XtreamSource
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

    // Ana sayfadaki "Canlı TV" önizlemesi için kanal listesi. Sekmeler arası
    // geçişlerde her seferinde ağ isteği yapılmaması için bir kez yüklenip
    // önbellekte tutulur (uygulama açık kaldığı sürece).
    private val _homeChannels = MutableStateFlow<List<Channel>?>(null)
    val homeChannels: StateFlow<List<Channel>?> = _homeChannels

    // İzlenme işaretleri (override + bölüm) her değişimde arttırılır; ekranlar
    // bu sürümü dinleyip Store'dan taze okur, böylece rozetler anlık güncellenir.
    private val _watchedVersion = MutableStateFlow(0)
    val watchedVersion: StateFlow<Int> = _watchedVersion

    fun toggleWatched(id: Long): Boolean {
        val added = store.toggleWatchedOverride(id)
        _watchedVersion.value++
        return added
    }

    fun bumpWatched() {
        _watchedVersion.value++
    }

    suspend fun loadHomeChannels(profile: Profile) {
        if (_homeChannels.value == null) {
            _homeChannels.value =
                runCatching { repository.loadChannels(profile, 0).take(30) }.getOrNull()
        }
    }

    // ---------- M3U / Xtream kaynakları ----------
    // Oturum içinde bir kez yüklenip önbellekte tutulur (sekmeler arası geçişte
    // tekrar indirme olmaz).
    private val m3uCache = mutableMapOf<String, Pair<List<Genre>, List<Channel>>>()
    private val xtreamCache = mutableMapOf<String, Pair<List<Genre>, List<Channel>>>()

    fun m3uSources(): List<M3uSource> = store.m3uSources()
    fun xtreamSources(): List<XtreamSource> = store.xtreamSources()

    fun activeSourceKind(): String = store.activeSourceKind()
    fun activeSourceId(): String? = store.activeSourceId()

    fun setActiveSource(kind: String, id: String?) {
        store.setActiveSource(kind, id)
        if (kind != "stalker") _homeChannels.value = null
    }

    fun saveM3uSource(source: M3uSource) {
        val list = store.m3uSources().toMutableList()
        val idx = list.indexOfFirst { it.id == source.id }
        if (idx >= 0) list[idx] = source else list.add(source)
        store.saveM3uSources(list)
        m3uCache.remove(source.id)
    }

    fun deleteM3uSource(id: String) {
        store.saveM3uSources(store.m3uSources().filterNot { it.id == id })
        m3uCache.remove(id)
        if (store.activeSourceKind() == "m3u" && store.activeSourceId() == id) {
            store.setActiveSource("stalker", null)
        }
    }

    fun saveXtreamSource(source: XtreamSource) {
        val list = store.xtreamSources().toMutableList()
        val idx = list.indexOfFirst { it.id == source.id }
        if (idx >= 0) list[idx] = source else list.add(source)
        store.saveXtreamSources(list)
        xtreamCache.remove(source.id)
    }

    fun deleteXtreamSource(id: String) {
        store.saveXtreamSources(store.xtreamSources().filterNot { it.id == id })
        xtreamCache.remove(id)
        if (store.activeSourceKind() == "xtream" && store.activeSourceId() == id) {
            store.setActiveSource("stalker", null)
        }
    }

    /** M3U kaynağının kanallarını yükler (gerekirse indirir + çözer). */
    suspend fun loadM3uChannels(source: M3uSource): Pair<List<Genre>, List<Channel>> {
        m3uCache[source.id]?.let { return it }
        var content = source.content
        if (content.isBlank() && source.url.isNotBlank()) {
            content = M3uParser.fetch(source.url).orEmpty()
            if (content.isNotBlank()) {
                saveM3uSource(source.copy(content = content))
            }
        }
        val channels = M3uParser.parse(content, source.id)
        // group-title'lar kategori olarak kullanılır.
        val groups = channels.map { it.tvGenreTitle }.filter { it.isNotBlank() }.distinct().sorted()
        val genres = listOf(Genre(0, "Tümü")) +
            groups.mapIndexed { i, g -> Genre((i + 1).toLong(), g) }
        val result = genres to channels
        m3uCache[source.id] = result
        return result
    }

    /** Xtream kaynağının canlı kanallarını yükler (kategoriler + kanallar). */
    suspend fun loadXtreamChannels(source: XtreamSource): Pair<List<Genre>, List<Channel>> {
        xtreamCache[source.id]?.let { return it }
        val client = XtreamClient()
        val cats = client.liveCategories(source)
        val channels = client.liveStreams(source)
        val genres = listOf(Genre(0, "Tümü")) + cats
        val result = genres to channels
        xtreamCache[source.id] = result
        return result
    }

    /** Aktif kaynağın kanallarını yükler (Stalker profil veya m3u/xtream). */
    suspend fun loadChannelsForActiveSource(profile: Profile?): Pair<List<Genre>, List<Channel>>? {
        val kind = store.activeSourceKind()
        val id = store.activeSourceId()
        return when (kind) {
            "m3u" -> store.m3uSources().firstOrNull { it.id == id }?.let { loadM3uChannels(it) }
            "xtream" -> store.xtreamSources().firstOrNull { it.id == id }?.let { loadXtreamChannels(it) }
            else -> {
                val p = profile ?: return null
                val cats = runCatching { repository.loadGenres(p) }.getOrDefault(emptyList())
                val channels = runCatching { repository.loadChannels(p, 0) }.getOrDefault(emptyList())
                listOf(Genre(0, "Tümü")) + cats to channels
            }
        }
    }

    // ---------- VOD catalog (background sync) ----------
    // The sync is owned by the app-lifetime VodSyncManager so it keeps running
    // while the app is backgrounded and resumes from a checkpoint after a restart.
    val vodCatalog: StateFlow<VodCatalogState> = StalkerApp.instance.vodSyncManager.progress

    fun syncVodCatalog(profile: Profile, force: Boolean = false) {
        StalkerApp.instance.vodSyncManager.ensureSynced(profile, force)
        VodSyncService.start(app)
    }

    /**
     * Ensures the VOD catalog is available. On app launch this just loads the
     * persisted disk cache (instant, NO network sync) when it is already
     * complete, so the user is not forced to re-sync every time. A background
     * sync (in a foreground service, so it survives being backgrounded or closed)
     * only runs when there is no complete cache yet, or to resume an
     * interrupted sync from its checkpoint.
     */
    fun syncVodIfNeeded(profile: Profile) {
        val portalId = profile.portal?.id ?: return
        // Completion is decided by the catalog meta file (written only at the
        // end of a successful sync). A running/interrupted sync has chunks but
        // no meta yet, so it resumes from its chunks instead of restarting.
        val meta = store.loadVodCatalogMeta(portalId)
        val staleCatalog = meta != null && meta.version < Store.VOD_CATALOG_VERSION
        if (meta != null && !staleCatalog) {
            StalkerApp.instance.vodSyncManager.publishCached(profile)
            return
        }
        StalkerApp.instance.vodSyncManager.ensureSynced(profile, force = staleCatalog)
        VodSyncService.start(app)
    }

    fun resetVodCatalog() {
        StalkerApp.instance.vodSyncManager.reset()
        StalkerApp.instance.store.clearVodCatalog(profileId())
    }

    private fun profileId(): String {
        return StalkerApp.instance.repository.cachedProfile()?.portal?.id ?: ""
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

/**
 * Katalog durumu. `byId` ve `seriesCategoryIds` yapım sırasında bir kez
 * önceden hesaplanır: 80k+ öğeli bir katalogda bunları her erişimde yeniden
 * üretmek (associateBy / kategori taraması) ana iş parçacığını kilitler ve
 * sayfa/sekme geçişlerinde donmaya yol açar.
 */
data class VodCatalogState(
    val status: VodCatalogStatus = VodCatalogStatus.Idle,
    val doneCategories: Int = 0,
    val totalCategories: Int = 0,
    val loadedCount: Int = 0,
    val allItems: List<VodItem> = emptyList(),
    val categories: List<Genre> = emptyList(),
    val portalTotal: Int = 0,
    val lastSync: Long = 0,
    val byId: Map<Long, VodItem> = emptyMap(),
    val seriesCategoryIds: Set<Long> = emptySet()
) {
    val isSeriesItem: (VodItem) -> Boolean = { item ->
        item.isSeries || item.seriesRef.isNotBlank() || item.seriesData.isNotBlank() || item.selectedSeason.isNotBlank() ||
            item.categoryId in seriesCategoryIds
    }

    companion object {
        val seriesKeywords = listOf("dizi", "series", "serial", "diziler")

        fun of(
            status: VodCatalogStatus = VodCatalogStatus.Idle,
            doneCategories: Int = 0,
            totalCategories: Int = 0,
            loadedCount: Int = 0,
            allItems: List<VodItem> = emptyList(),
            categories: List<Genre> = emptyList(),
            portalTotal: Int = 0,
            lastSync: Long = 0
        ): VodCatalogState = VodCatalogState(
            status = status,
            doneCategories = doneCategories,
            totalCategories = totalCategories,
            loadedCount = loadedCount,
            allItems = allItems,
            categories = categories,
            portalTotal = portalTotal,
            lastSync = lastSync,
            byId = allItems.associateBy { it.id },
            seriesCategoryIds = categories
                .filter { c -> seriesKeywords.any { kw -> c.title.contains(kw, ignoreCase = true) } }
                .map { it.id }
                .toSet()
        )
    }
}

@Composable
fun rememberMainViewModel(app: StalkerApp): MainViewModel =
    viewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity) {
        MainViewModel(app)
    }
