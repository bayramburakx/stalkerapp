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
import androidx.media3.exoplayer.scheduler.Requirements
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

        val dataSourceFactory = DefaultDataSource.Factory(context)
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
            requirements = Requirements(Requirements.NETWORK)
            resumeDownloads()
        }

        downloadManager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
                refreshState()
            }
            override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
                refreshState()
            }
        })

        loadMeta()
    }

    /** İndirme kuyruğuna ekler. */
    suspend fun enqueue(entry: DownloadEntry) = withContext(Dispatchers.IO) {
        val list = _downloads.value.toMutableList()
        if (list.any { it.id == entry.id }) return@withContext
        list.add(entry)
        _downloads.value = list
        saveMeta(list)

        // DownloadHelper ile request üret (HLS/DASH/TS için stream keys + mime
        // tespiti); hazırlanamazsa düz dosya isteğine düş (progressive indirme).
        // prepare() Looper gerektirdiğinden ana iş parçacığında koşulur.
        val request = withContext(Dispatchers.Main) {
            runCatching {
                DownloadHelper.forMediaItem(context, MediaItem.fromUri(entry.url))
                    .apply { prepare() }
                    .let { helper -> helper.getDownloadRequest(entry.id).also { helper.release() } }
            }.getOrElse {
                DownloadRequest.Builder(entry.id, Uri.parse(entry.url)).build()
            }
        }
        downloadManager.addDownload(request)
    }

    /** İndirmeyi iptal et / sil. */
    fun cancel(id: String) {
        downloadManager.removeDownload(id)
        val list = _downloads.value.filter { it.id != id }
        _downloads.value = list
        saveMeta(list)
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
        val dlMap = downloadManager.currentDownloads.associateBy { it.request.id }
        val updated = _downloads.value.map { entry ->
            val dl = dlMap[entry.id]
            if (dl != null) {
                entry.copy(
                    state = when (dl.state) {
                        Download.STATE_DOWNLOADING -> "downloading"
                        Download.STATE_COMPLETED -> "completed"
                        Download.STATE_FAILED -> "failed"
                        Download.STATE_QUEUED -> "queued"
                        Download.STATE_STOPPED -> "queued"
                        else -> entry.state
                    },
                    progressPct = if (dl.percentDownloaded >= 0) dl.percentDownloaded else entry.progressPct,
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
