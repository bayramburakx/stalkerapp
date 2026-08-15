package com.stalkerapp.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.stalkerapp.R
import com.stalkerapp.StalkerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the VOD catalog sync alive while the app is in
 * the background and even after it is closed (START_STICKY), so a large sync
 * resumes from its checkpoint instead of restarting from zero.
 */
class VodSyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification("VOD senkronize ediliyor..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val profile = StalkerApp.instance.repository.cachedProfile()
        if (profile == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        StalkerApp.instance.vodSyncManager.ensureSynced(profile)
        scope.launch {
            StalkerApp.instance.vodSyncManager.progress.collectLatest { state ->
                val text = when (state.status) {
                    VodCatalogStatus.Ready -> "VOD senkron tamamlandı (${state.loadedCount} öğe)"
                    VodCatalogStatus.Error -> "VOD senkron hatası"
                    VodCatalogStatus.Syncing -> "VOD yükleniyor: ${state.loadedCount} öğe (${state.doneCategories}/${state.totalCategories})"
                    else -> "VOD hazırlanıyor..."
                }
                updateNotification(text)
                if (state.status == VodCatalogStatus.Ready || state.status == VodCatalogStatus.Error) {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        stopSelf()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "VOD Senkronizasyonu",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle("Stalker")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        const val NOTIF_ID = 1002
        const val CHANNEL_ID = "vod_sync"
        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, VodSyncService::class.java))
        }
    }
}
