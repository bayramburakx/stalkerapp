package com.stalkerapp.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import com.stalkerapp.R

/**
 * ExoPlayer DownloadService subclass.
 * AndroidManifest.xml'de kayıtlıdır.
 * Arka planda indirme işlemlerini yönetir.
 */
@UnstableApi
class AppDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    R.string.app_name,
    0
) {

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 8001
        const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"
    }

    override fun onCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                "İndirmeler",
                NotificationManager.IMPORTANCE_LOW
            )
            nm?.createNotificationChannel(channel)
        }
        super.onCreate()
    }

    override fun getDownloadManager(): DownloadManager {
        val context = applicationContext
        OfflineDownloadManager.init(context)
        return OfflineDownloadManager.getDownloadManager()
    }

    override fun getScheduler() = null

    override fun getForegroundNotification(
        downloads: MutableList<androidx.media3.exoplayer.offline.Download>,
        notMetRequirements: Int
    ): Notification {
        val inProgress = downloads.count { it.state == androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING }
        val completed = downloads.count { it.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED }
        val active = downloads.firstOrNull { it.state == androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING }
        val pct = active?.percentDownloaded?.toInt()?.coerceIn(0, 100) ?: 0

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val subtext = if (active != null && pct > 0) "%$pct indiriliyor ($inProgress/$completed)" else "$inProgress indiriliyor, $completed tamamlandı"
        return builder
            .setContentTitle("Portio İndirme")
            .setContentText(subtext)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
    }
}
