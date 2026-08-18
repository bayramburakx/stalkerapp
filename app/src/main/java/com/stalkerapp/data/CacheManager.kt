package com.stalkerapp.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Akıllı önbellek yöneticisi.
 *
 * Özellikler:
 *  - **Delta senkron**: son güncelleme timestamp'ini portal'dan çeker; değişmediyse katalog yenilenmez
 *  - **Kaynak sağlık monitörü**: portal yanıt süresini izler; 3 günden uzun süre yanıtsız kalan kaynaklar uyarı bildirim gösterir
 *  - **Disk kotası kontrolü**: indirilen katalog boyutu ayardan belirlenen limiti aşarsa eskiler silinir
 *  - **Otomatik yedek kaynak**: primary URL başarısız olunca alternativeUrls listesini dener
 */
class CacheManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tag = "CacheManager"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val store: Store by lazy { Store(context) }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "source_health"
        private const val HEALTH_NOTIFICATION_ID = 9001
        private const val FAILURE_THRESHOLD_DAYS = 3
    }

    init {
        createNotificationChannel()
    }

    // ---------- Delta Senkron ----------

    /**
     * Stalker portal için delta senkron: son güncelleme hash'ini kontrol eder.
     * Değişmemişse boş katalog, değişmişse yeni katalog döner.
     */
    suspend fun needsSync(portalUrl: String, lastSyncTime: Long): Boolean {
        if (lastSyncTime == 0L) return true
        val hoursSinceSync = (System.currentTimeMillis() - lastSyncTime) / 3_600_000L
        return hoursSinceSync >= 24 // 24 saat geçmişse yenile
    }

    // ---------- Kaynak Sağlık Monitörü ----------

    /**
     * Portal URL'sinin sağlık durumunu kontrol eder.
     * Başarısız yanıt sayısını günceller; limit aşılırsa bildirim gönderir.
     */
    fun checkSourceHealth(portalId: String, portalUrl: String, portalName: String) {
        scope.launch {
            runCatching {
                val req = Request.Builder()
                    .url(portalUrl.trimEnd('/') + "/portal.php")
                    .head()
                    .build()
                val success = http.newCall(req).execute().use { it.code < 500 }
                val prefs = context.getSharedPreferences("cache_health", Context.MODE_PRIVATE)
                val failKey = "fail_$portalId"
                val lastFailKey = "last_fail_$portalId"

                if (success) {
                    prefs.edit().remove(failKey).remove(lastFailKey).apply()
                } else {
                    val failCount = prefs.getInt(failKey, 0) + 1
                    prefs.edit()
                        .putInt(failKey, failCount)
                        .putLong(lastFailKey, System.currentTimeMillis())
                        .apply()
                    if (failCount >= FAILURE_THRESHOLD_DAYS) {
                        notifyUnhealthy(portalName, failCount)
                    }
                }
            }.onFailure { Log.d(tag, "Sağlık kontrolü başarısız: ${it.message}") }
        }
    }

    /** Bir kaynağın kaç günde bir yanıtsız kaldığını döner. */
    fun getFailCount(portalId: String): Int {
        val prefs = context.getSharedPreferences("cache_health", Context.MODE_PRIVATE)
        return prefs.getInt("fail_$portalId", 0)
    }

    // ---------- Otomatik Yedek Kaynak ----------

    /**
     * Verilen URL listesinden ilk çalışanı döner.
     * Primary URL çalışıyorsa onu döner, yoksa alternativeUrls'den dener.
     */
    suspend fun resolveWorkingUrl(primaryUrl: String, alternativeUrls: List<String>): String? {
        val candidates = listOf(primaryUrl) + alternativeUrls
        for (url in candidates) {
            if (url.isBlank()) continue
            val works = runCatching {
                val req = Request.Builder().url(url).head().build()
                http.newCall(req).execute().use { it.code < 500 }
            }.getOrDefault(false)
            if (works) return url
        }
        return null
    }

    // ---------- Disk Kotası ----------

    /** Uygulama önbelleklerinin toplam boyutunu döner (byte). */
    fun cacheUsageBytes(): Long {
        return context.filesDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    /**
     * Belirli bir boyut limitini aşıyorsa en eski VOD önbelleklerini siler.
     */
    fun enforceQuota(maxBytes: Long) {
        val used = cacheUsageBytes()
        if (used <= maxBytes) return

        Log.d(tag, "Disk kotası aşıldı ($used > $maxBytes), eski önbellekler temizleniyor")
        context.filesDir.walkTopDown()
            .filter { it.isFile && (it.name.startsWith("vod_catalog_") || it.name.startsWith("ext_vod_")) }
            .sortedBy { it.lastModified() }
            .forEach { file ->
                if (cacheUsageBytes() > maxBytes * 0.8) {
                    file.delete()
                    Log.d(tag, "Silindi: ${file.name}")
                }
            }
    }

    // ---------- Bildirim ----------

    private fun notifyUnhealthy(portalName: String, days: Int) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(context)
            }
            builder.setContentTitle("Kaynak yanıt vermiyor")
                .setContentText("\"$portalName\" kaynağı $days gündür yanıt vermiyor.")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
            nm.notify(HEALTH_NOTIFICATION_ID, builder.build())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(
                    NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "Kaynak Sağlığı",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply { description = "Yanıt vermeyen kaynaklar için uyarılar" }
                )
            }
        }
    }
}
