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
    // Sync sırasında UI yayınlarını throttle eder: her kategori bitişinde 80k+
    // öğelik allItems listesini yayınlamak ana sayfa/VOD filtrelerini sürekli
    // yeniden hesaplatıp sayfa geçişlerini takıldırıyordu. En fazla ~700ms'de
    // bir tam liste yayınlanır; aradaki yayınlarda yalnızca sayaçlar güncellenir.
    private var lastUiPublish = 0L
    private val seriesKeywords = listOf("dizi", "series", "serial", "diziler")
    // Last-resort enumeration strategy: single letters/digits via the portal's
    // `search` param. Many portals cap plain list paging but paginate search
    // results fine, which exposes the whole library.
    private val searchTokens: List<String> =
        ('a'..'z').map { it.toString() } + ('0'..'9').map { it.toString() } +
            listOf("ç", "ğ", "ı", "ö", "ş", "ü")

    fun isSeriesItem(item: VodItem): Boolean {
        if (item.id >= PortalRepository.SERIES_ID_BASE || ExternalVod.isXtreamSeries(item.id)) return true
        if (item.isSeries && (item.seriesRef.isNotBlank() || item.seriesData.isNotBlank() || item.selectedSeason.isNotBlank())) return true
        if (item.seriesRef.isNotBlank() && item.seriesRef != "[]" && item.seriesRef != "0" && item.seriesRef != "null") return true
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
        _progress.value = VodCatalogState.of(
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
        val isSeries = (item.isSeries && (item.seriesRef.isNotBlank() || item.seriesData.isNotBlank() || item.selectedSeason.isNotBlank())) ||
            (item.seriesRef.isNotBlank() && item.seriesRef != "[]" && item.seriesRef != "0" && item.seriesRef != "null") ||
            seriesCatIds.contains(item.categoryId)
        return if (isSeries == item.isSeries) item else item.copy(isSeries = isSeries)
    }

    private suspend fun runSync(profile: Profile, force: Boolean) {
        val portalId = profile.portal?.id ?: ""
        // Resume from per-category chunk files (written as each category
        // completes) instead of a giant single-file checkpoint.
        val cached = if (force) null else store.loadVodCatalog(portalId)
        val chunks = if (cached != null) emptyMap() else store.loadVodCatalogChunks(portalId)
        val baseItems = if (force) emptyList()
        else (cached?.first ?: chunks.values.flatten().distinctBy { it.id })
        val doneCats = if (force) mutableSetOf() else (
            store.loadVodCatalogDoneCats(portalId) + chunks.keys
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
            // Also clears done-cats internally.
            store.clearVodCatalog(portalId)
        }

        _progress.value = state(
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
            // Skip the "All" pseudo-category (id 0) some panels expose: it is
            // just the unfiltered list, so paging it would re-fetch the whole
            // library once more for nothing.
            val remaining = cats.filter { it.id != 0L && (force || it.id !in doneCats) }

            var portalTotal = 0

            // 0) Separate series library FIRST. Portals like this one keep series
            // in their own `type=series` namespace (thousands of titles with real
            // season/episode structures) that the VOD passes never see — without
            // this pass the Dizi tab only shows the handful of dated episodes in
            // the VOD "DIZILERI" categories. It runs BEFORE the heavy VOD passes
            // so the portal hasn't been rate-limited into a cooldown yet (a
            // silent failure here used to leave the catalog permanently series-less
            // because the completed meta short-circuits future syncs). Series
            // category ids are namespaced so they can't collide with VOD
            // categories in chunks/done-cats.
            runCatching {
                val seriesCats = repository.loadSeriesCategories(profile)
                if (seriesCats.isNotEmpty()) {
                    cats = cats + seriesCats
                    val seriesRemaining = seriesCats.filter { it.id != 0L && (force || it.id !in doneCats) }
                    if (seriesRemaining.isNotEmpty()) {
                        val sem = Semaphore(8)
                        coroutineScope {
                            seriesRemaining.forEach { cat ->
                                launch {
                                    sem.acquire()
                                    try {
                                        val items = repository.fetchSeriesCategory(profile, cat.id, 5000)
                                        items.forEach { all[it.id] = it }
                                        store.saveVodCategoryChunk(portalId, cat.id, items)
                                        mutex.withLock {
                                            val doneNow = (store.loadVodCatalogDoneCats(portalId) + cat.id).toMutableSet().also {
                                                store.saveVodCatalogDoneCats(portalId, it.toList())
                                            }
                                            _progress.value = state(
                                                doneCategories = doneNow.size,
                                                loadedCount = all.size,
                                                allItems = all.values.toList(),
                                                categories = cats,
                                                totalCategories = cats.size,
                                                portalTotal = portalTotal
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
                }
            }

            // 1) Fast single pass (Tivimate-style: one huge per_page request, paged
            // until the portal stops returning items). We trust it only when it
            // actually retrieved a substantial number of items (and got close to
            // the portal-reported total when one is available) — the portal's
            // `total_items` is often wrong, so it must not gate completion.
            // Portals that ignore `per_page` (hard ~14-item pages) would need
            // thousands of sequential requests for this pass, so we skip it and
            // go straight to the parallel per-category enumeration (step 2),
            // which is complete on such portals.
            var singleOk = false
            val tinyPages = runCatching {
                repository.probeVodPageParam(profile)
                repository.vodPageSize() in 1..30
            }.getOrDefault(false)
            portalTotal = repository.vodPortalTotal()
            if (!tinyPages && (force || baseItems.isEmpty())) {
                runCatching {
                    val (items, allTotal) = repository.fetchAllVod(profile, 100000)
                    if (allTotal > 0) portalTotal = allTotal
                    items.forEach { all[it.id] = stamp(it, seriesCatIds) }
                    singleOk = items.size >= 5000 &&
                        if (allTotal > 0) items.size >= allTotal * 0.95
                        else items.size >= 10000
                    _progress.value = state(
                        loadedCount = all.size,
                        doneCategories = cats.size,
                        totalCategories = cats.size,
                        allItems = all.values.toList(),
                        portalTotal = portalTotal
                    )
                    if (singleOk) {
                        store.saveVodCatalog(portalId, all.values.toList(), cats)
                        store.clearVodCatalogDoneCats(portalId)
                    }
                }
            }

            if (!singleOk) {
                val saneTotal = if (portalTotal in 1..300_000) portalTotal else 0
                val target = if (saneTotal > 0) (saneTotal * 0.98).toInt() else Int.MAX_VALUE
                val complete = all.size >= target || (saneTotal <= 0 && all.size >= 10000)

                // 2) Parallel per-category fetch for any gaps (Tivimate fetches per
                // category), using the filter parameter this portal actually accepts.
                // Modest concurrency: too many parallel requests can trip the portal's
                // rate limiter and stall the whole sync on cooldowns.
                if (!complete) {
                    val categoryParam = repository.probeVodCategoryParam(profile)
                    val sem = Semaphore(8)
                    coroutineScope {
                        remaining.forEach { cat ->
                            launch {
                                sem.acquire()
                                try {
                                    val items = repository.fetchVodCategory(profile, cat.id, 5000, categoryParam)
                                    items.forEach { all[it.id] = stamp(it, seriesCatIds) }
                                    // Persist this category immediately (small chunk
                                    // file) so a process kill mid-sync only loses the
                                    // in-flight category, never the whole library.
                                    store.saveVodCategoryChunk(portalId, cat.id, items)
                                    mutex.withLock {
                                        val doneNow = (store.loadVodCatalogDoneCats(portalId) + cat.id).toMutableSet().also {
                                            store.saveVodCatalogDoneCats(portalId, it.toList())
                                        }
                                        _progress.value = state(
                                            doneCategories = doneNow.size,
                                            loadedCount = all.size,
                                            allItems = all.values.toList(),
                                            portalTotal = portalTotal
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

                // 3) Letter/digit search enumeration as a last resort — catches
                // portals whose plain list paging is capped or whose category
                // filter is ignored (both return the same tiny subset forever).
                val target3 = if (saneTotal > 0) target else Int.MAX_VALUE
                if (all.size < target3 && (saneTotal > 0 || all.size < 5000)) {
                    val sem = Semaphore(6)
                    coroutineScope {
                        searchTokens.forEach { token ->
                            launch {
                                sem.acquire()
                                try {
                                    val items = repository.fetchVodSearch(profile, token, 5000)
                                    items.forEach { all[it.id] = stamp(it, seriesCatIds) }
                                    mutex.withLock {
                                        _progress.value = state(
                                            loadedCount = all.size,
                                            allItems = all.values.toList(),
                                            portalTotal = portalTotal
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
            }

            store.saveVodCatalog(portalId, all.values.toList(), cats)
            store.clearVodCatalogDoneCats(portalId)
            cats = store.loadVodCatalog(portalId)?.second ?: cats
            _progress.value = state(
                status = VodCatalogStatus.Ready,
                doneCategories = cats.size,
                totalCategories = cats.size,
                loadedCount = all.size,
                allItems = all.values.toList(),
                categories = cats,
                portalTotal = portalTotal,
                lastSync = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            if (_progress.value.status != VodCatalogStatus.Ready) {
                _progress.value = state(status = VodCatalogStatus.Error)
            }
        } finally {
            repository.resetAdaptiveInterval()
            if (_progress.value.status == VodCatalogStatus.Syncing) {
                _progress.value = state(status = VodCatalogStatus.Idle)
            }
        }
    }

    /**
     * Kataloğun yeni bir sürümünü yayınlar; `byId` ve `seriesCategoryIds`
     * önceden hesaplanır (eski `copy` çağrıları bunları bayat bırakırdı).
     * Senkron SIRASINDA (Syncing) her kategori bitişinde 80k+ öğenin yeniden
     * associateBy edilmesi ana iş parçacığını kilitler ve telefonu ısıtır —
     * bu yüzden Syncing yayınlarında mevcut byId korunur; byId yalnızca
     * Ready/İlk yayında bir kez kurulur.
     */
    private fun state(
        status: VodCatalogStatus = _progress.value.status,
        doneCategories: Int = _progress.value.doneCategories,
        totalCategories: Int = _progress.value.totalCategories,
        loadedCount: Int = _progress.value.loadedCount,
        allItems: List<VodItem> = _progress.value.allItems,
        categories: List<Genre> = _progress.value.categories,
        portalTotal: Int = _progress.value.portalTotal,
        lastSync: Long = _progress.value.lastSync
    ): VodCatalogState {
        val prev = _progress.value
        // Syncing yayınlarında byId/seriesCategoryIds yeniden hesaplanmaz;
        // son hazır kataloğun haritası kullanılır (yarım liste için de faydalı).
        if (status == VodCatalogStatus.Syncing) {
            val now = System.currentTimeMillis()
            // 80k+ öğelik listeyi her kategori bitişinde yayınlamak ana iş
            // parçacığında ağır filtreleri (ana sayfa/VOD) sürekli tetikliyordu.
            // En fazla ~700ms'de bir tam liste yayınlanır; aradakilerde liste
            // korunur, yalnızca ilerleme sayaçları güncellenir.
            val publish = now - lastUiPublish >= 700
            if (publish) lastUiPublish = now
            return prev.copy(
                status = status,
                doneCategories = doneCategories,
                totalCategories = totalCategories,
                loadedCount = loadedCount,
                allItems = if (publish) allItems else prev.allItems,
                categories = categories,
                portalTotal = portalTotal,
                lastSync = lastSync
            )
        }
        return VodCatalogState.of(
            status = status,
            doneCategories = doneCategories,
            totalCategories = totalCategories,
            loadedCount = loadedCount,
            allItems = allItems,
            categories = categories,
            portalTotal = portalTotal,
            lastSync = lastSync
        )
    }
}
