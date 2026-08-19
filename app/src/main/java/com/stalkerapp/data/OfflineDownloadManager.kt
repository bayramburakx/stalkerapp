package com.stalkerapp.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executor

/**
 * Offline indirme yöneticisi.
 * ExoPlayer `DownloadManager` üzerinde wrapper — VOD'ları Wi-Fi'de indirir,
 * uçakta/çevrimdışı izlemeye olanak tanır.
 *
 * Disk kotası: ayarlardan belirlenebilir (varsayılan 2 GB).
 */
object OfflineDownloadManager {

    private lateinit var downloadManager: DownloadManager
    private lateinit var downloadCache: SimpleCache
    private lateinit var context: Context

    private val _downloads = MutableStateFlow<List<DownloadEntry>>(emptyList())
    val downloads: StateFlow<List<DownloadEntry>> = _downloads.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    private const val META_FILE = "offline_downloads.json"

    @Serializable
    data class DownloadEntry(
        val id: String,
        val title: String,
        val poster: String = "",
        val url: String,
        val addedAt: Long = System.currentTimeMillis(),
        /** "downloading" | "completed" | "failed" | "queued" */
        val state: String = "queued",
        val progressPct: Float = 0f,
        val fileSizeBytes: Long = 0L,
        val isSeries: Boolean = false,
        val episodeLabel: String = "",
        /** İndirilen segment dosyasının yerel path'i */
        val localPath: String = ""
    )

    /** ExoPlayer cache'i ilklendirir. Uygulama başlangıcında çağrılmalı. */
    @Synchronized
    fun init(ctx: Context, maxCacheMb: Long = 2048L) {
        if (::context.isInitialized) return
        context = ctx.applicationContext

        val cacheDir = File(context.filesDir, "offline_cache")
        val databaseProvider = StandaloneDatabaseProvider(context)
        val maxBytes = maxCacheMb * 1024L * 1024L
        downloadCache = SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(maxBytes), databaseProvider)

        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(dataSourceFactory)
            .setCacheWriteDataSinkFactory(null)

        downloadManager = DownloadManager(
            context,
            databaseProvider,
            downloadCache,
            dataSourceFactory,
            Executor(Runnable::run)
        ).apply {
            requirements = Requirements(0)
            resumeDownloads()
        }

        downloadManager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
                refreshState()
                startProgressTracker()
            }
            override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
                refreshState()
            }
        })

        loadMeta()
        startProgressTracker()
    }

    private var trackerJob: Job? = null

    private fun startProgressTracker() {
        trackerJob?.cancel()
        trackerJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                if (!::downloadManager.isInitialized) break
                val current = downloadManager.currentDownloads
                refreshState()
                val active = current.any {
                    it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED || it.state == Download.STATE_RESTARTING
                }
                if (!active && current.isNotEmpty()) {
                    delay(1500)
                    refreshState()
                    break
                }
                if (current.isEmpty()) break
                delay(500)
            }
        }
    }

    /** İndirme kuyruğuna ekler. */
    suspend fun enqueue(entry: DownloadEntry) = withContext(Dispatchers.IO) {
        val list = _downloads.value.toMutableList()
        val idx = list.indexOfFirst { it.id == entry.id }
        if (idx >= 0) {
            list[idx] = entry.copy(state = "downloading", progressPct = 0f)
        } else {
            list.add(entry.copy(state = "downloading", progressPct = 0f))
        }
        _downloads.value = list
        saveMeta(list)

        val request = DownloadRequest.Builder(entry.id, Uri.parse(entry.url)).build()
        if (::downloadManager.isInitialized) {
            downloadManager.addDownload(request)
            downloadManager.resumeDownloads()
            startProgressTracker()
        }
        runCatching {
            DownloadService.sendAddDownload(
                context,
                AppDownloadService::class.java,
                request,
                false
            )
            DownloadService.sendResumeDownloads(
                context,
                AppDownloadService::class.java,
                false
            )
        }
    }

    /** İndirmeyi iptal et / sil. */
    fun cancel(id: String) {
        downloadManager.removeDownload(id)
        val list = _downloads.value.filter { it.id != id }
        _downloads.value = list
        saveMeta(list)
    }

    /** Tüm indirmeleri temizler. */
    fun clearAllDownloads() {
        if (::downloadManager.isInitialized) {
            downloadManager.removeAllDownloads()
        }
        _downloads.value = emptyList()
        saveMeta(emptyList())
        runCatching {
            val cacheDir = File(context.filesDir, "offline_cache")
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
        }
    }

    /** ExoPlayer download cache referansı (PlayerScreen'de offline oynatma için). */
    fun getCache(): SimpleCache? = if (::downloadCache.isInitialized) downloadCache else null

    /** Paylaşılan DownloadManager (AppDownloadService aynı yöneticiyi kullanır). */
    fun getDownloadManager(): DownloadManager {
        if (!::downloadManager.isInitialized) {
            init(context)
        }
        return downloadManager
    }

    /**
     * İndirilmiş içerikten oynatma için CacheDataSource.Factory.
     * Önce önbellekten beslenir; eksik parçalar ağdan çekilir (karma mod).
     */
    fun cacheDataSourceFactory(): androidx.media3.datasource.cache.CacheDataSource.Factory {
        val cache = getCache()!!
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
            .setCacheWriteDataSinkFactory(null)
    }

    /** Bir öğenin indirilip indirilmediğini kontrol eder. */
    fun isDownloaded(id: String): Boolean =
        _downloads.value.any { it.id == id && it.state == "completed" }

    /** Disk kullanımı (byte). */
    fun usedDiskBytes(): Long = if (::downloadCache.isInitialized) downloadCache.cacheSpace else 0L

    private fun refreshState() {
        if (!::downloadManager.isInitialized) return
        val currentMap = downloadManager.currentDownloads.associateBy { it.request.id }
        val updated = _downloads.value.map { entry ->
            val dl = currentMap[entry.id]
                ?: runCatching { downloadManager.downloadIndex.getDownload(entry.id) }.getOrNull()
            if (dl != null) {
                val isCompleted = dl.state == Download.STATE_COMPLETED ||
                    (dl.percentDownloaded >= 99.0f && dl.bytesDownloaded > 0)
                entry.copy(
                    state = when {
                        isCompleted -> "completed"
                        dl.state == Download.STATE_DOWNLOADING -> "downloading"
                        dl.state == Download.STATE_FAILED -> "failed"
                        dl.state == Download.STATE_QUEUED -> "queued"
                        dl.state == Download.STATE_STOPPED -> "queued"
                        else -> entry.state
                    },
                    progressPct = if (isCompleted) 100f else if (dl.percentDownloaded >= 0) dl.percentDownloaded else entry.progressPct,
                    fileSizeBytes = if (dl.bytesDownloaded > 0) dl.bytesDownloaded else entry.fileSizeBytes
                )
            } else entry
        }
        _downloads.value = updated
        saveMeta(updated)
    }

    private fun metaFile(): File = File(context.filesDir, META_FILE)

    private fun loadMeta() {
        runCatching {
            val f = metaFile()
            if (f.exists()) {
                _downloads.value = json.decodeFromString(
                    ListSerializer(DownloadEntry.serializer()), f.readText()
                )
            }
        }
    }

    private fun saveMeta(list: List<DownloadEntry>) {
        runCatching {
            metaFile().writeText(json.encodeToString(ListSerializer(DownloadEntry.serializer()), list))
        }
    }
}
