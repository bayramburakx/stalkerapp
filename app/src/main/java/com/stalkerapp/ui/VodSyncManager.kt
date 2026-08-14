package com.stalkerapp.ui

import com.stalkerapp.data.Genre
import com.stalkerapp.data.PortalRepository
import com.stalkerapp.data.Profile
import com.stalkerapp.data.Store
import com.stalkerapp.data.VodItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * Owns the (potentially long-running) VOD catalog sync. Lives on an
 * application-lifetime [CoroutineScope] so it keeps running while the app is
 * backgrounded, and can resume from a persisted checkpoint after the process
 * restarts (app closed / killed). UI observes [progress].
 */
class VodSyncManager(
    context: CoroutineContext,
    private val repository: PortalRepository,
    private val store: Store
) {
    private val scope = CoroutineScope(SupervisorJob() + context)
    private val _progress = MutableStateFlow(VodCatalogState())
    val progress: StateFlow<VodCatalogState> = _progress.asStateFlow()

    private var job: kotlinx.coroutines.Job? = null
    private val mutex = Mutex()
    private val checkpointLock = Mutex()
    private val seriesKeywords = listOf("dizi", "series", "serial", "diziler")
    private var lastCheckpointTs = 0L

    fun isSeriesItem(item: VodItem): Boolean {
        if (item.isSeries || item.seriesRef.isNotBlank()) return true
        val title = _progress.value.categories.find { it.id == item.categoryId }?.title
        val t = title ?: item.categoryId.toString()
        return seriesKeywords.any { t.contains(it, ignoreCase = true) }
    }

    /** Starts (or resumes) syncing. No-op while a sync is already in progress. */
    fun ensureSynced(profile: Profile, force: Boolean = false) {
        if (_progress.value.status == VodCatalogStatus.Syncing && !force) return
        job?.cancel()
        job = scope.launch { runSync(profile, force) }
    }

    /** Publishes an already-complete cached catalog to the UI without any network. */
    fun publishCached(profile: Profile) {
        val portalId = profile.portal?.id ?: return
        val cached = store.loadVodCatalog(portalId) ?: return
        _progress.value = VodCatalogState(
            status = VodCatalogStatus.Ready,
            doneCategories = cached.second.size,
            totalCategories = cached.second.size,
            loadedCount = cached.first.size,
            allItems = cached.first,
            categories = cached.second,
            lastSync = cached.third
        )
    }

    /** Cancels any running sync and clears the in-memory progress. */
    fun reset() {
        job?.cancel()
        _progress.value = VodCatalogState()
    }

    private fun stamp(item: VodItem, seriesCatIds: Set<Long>): VodItem {
        val isSeries = item.isSeries || item.seriesRef.isNotBlank() || seriesCatIds.contains(item.categoryId)
        return if (isSeries == item.isSeries) item else item.copy(isSeries = isSeries)
    }

    private suspend fun checkpoint(portalId: String, all: Map<Long, VodItem>, cats: List<Genre>, doneCatIds: Set<Long>) {
        val now = System.currentTimeMillis()
        if (now - lastCheckpointTs < 5000 && doneCatIds.isNotEmpty()) return
        lastCheckpointTs = now
        checkpointLock.withLock { store.saveVodPartial(portalId, all.values.toList(), cats, doneCatIds.toList()) }
    }

    private suspend fun runSync(profile: Profile, force: Boolean) {
        val portalId = profile.portal?.id ?: ""
        val cached = store.loadVodCatalog(portalId)
        val partial = store.loadVodPartial(portalId)
        val baseItems = if (force) emptyList() else (cached?.first ?: partial?.items ?: emptyList())
        val doneCats = if (force) mutableSetOf() else (
            store.loadVodCatalogDoneCats(portalId) + (partial?.doneCatIds ?: emptyList())
        ).toMutableSet()
        var cats = if (cached != null && cached.second.isNotEmpty()) cached.second
        else {
            var loaded: List<Genre> = emptyList()
            repeat(3) {
                if (loaded.isEmpty()) {
                    loaded = runCatching { repository.loadVodCategories(profile) }.getOrDefault(emptyList())
                }
            }
            loaded
        }
        if (force) {
            store.clearVodCatalogDoneCats(portalId)
            store.clearVodPartial(portalId)
        }

        _progress.value = _progress.value.copy(
            status = VodCatalogStatus.Syncing,
            categories = cats,
            allItems = baseItems,
            loadedCount = baseItems.size,
            doneCategories = doneCats.size,
            totalCategories = cats.size
        )

        try {
            val seriesCatIds = cats.filter { it.title.let { t -> seriesKeywords.any { kw -> t.contains(kw, true) } } }
                .map { it.id }.toSet()
            val all = ConcurrentHashMap<Long, VodItem>()
            baseItems.forEach { all[it.id] = it }
            val remaining = cats.filter { force || it.id !in doneCats }

            // 1) Fast single pass (Tivimate-style: one huge per_page request, paged
            // until the portal stops returning items). We trust it only when it
            // actually retrieved a substantial number of items (and got close to
            // the portal-reported total when one is available) — the portal's
            // `total_items` is often wrong, so it must not gate completion.
            var singleOk = false
            if (force || baseItems.isEmpty()) {
                runCatching {
                    val (items, allTotal) = repository.fetchAllVod(profile, 100000)
                    items.forEach { all[it.id] = stamp(it, seriesCatIds) }
                    singleOk = items.size >= 5000 &&
                        if (allTotal > 0) items.size >= allTotal * 0.95
                        else items.size >= 10000
                    _progress.value = _progress.value.copy(
                        loadedCount = all.size,
                        doneCategories = cats.size,
                        totalCategories = cats.size,
                        allItems = all.values.toList()
                    )
                    if (singleOk) {
                        store.saveVodCatalog(portalId, all.values.toList(), cats)
                        store.clearVodCatalogDoneCats(portalId)
                        store.clearVodPartial(portalId)
                    }
                }
            }

            // 2) Parallel per-category fetch for any gaps (Tivimate fetches per
            // category). Modest concurrency: too many parallel requests can trip
            // the portal's rate limiter and stall the whole sync on cooldowns.
            if (!singleOk) {
                val sem = Semaphore(8)
                coroutineScope {
                    remaining.forEach { cat ->
                        launch {
                            sem.acquire()
                            try {
                                val items = repository.fetchVodCategory(profile, cat.id, 5000)
                                items.forEach { all[it.id] = stamp(it, seriesCatIds) }
                                val doneNow = (store.loadVodCatalogDoneCats(portalId) + cat.id).toMutableSet().also {
                                    store.saveVodCatalogDoneCats(portalId, it.toList())
                                }
                                checkpoint(portalId, all, cats, doneNow)
                                mutex.withLock {
                                    _progress.value = _progress.value.copy(
                                        doneCategories = doneNow.size,
                                        loadedCount = all.size,
                                        allItems = all.values.toList()
                                    )
                                }
                            } catch (_: Exception) {
                            } finally {
                                sem.release()
                            }
                        }
                    }
                }
            }

            store.saveVodCatalog(portalId, all.values.toList(), cats)
            store.clearVodCatalogDoneCats(portalId)
            store.clearVodPartial(portalId)
            cats = store.loadVodCatalog(portalId)?.second ?: cats
            _progress.value = _progress.value.copy(
                status = VodCatalogStatus.Ready,
                doneCategories = cats.size,
                totalCategories = cats.size,
                loadedCount = all.size,
                allItems = all.values.toList(),
                categories = cats,
                lastSync = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            if (_progress.value.status != VodCatalogStatus.Ready) {
                _progress.value = _progress.value.copy(status = VodCatalogStatus.Error)
            }
        }
    }
}
