package com.stalkerapp.playback

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PlaybackManager.ACTION_TOGGLE -> PlaybackManager.togglePlayPause()
            PlaybackManager.ACTION_NEXT -> serviceScope.launch { PlaybackManager.nextChannel() }
            PlaybackManager.ACTION_PREV -> serviceScope.launch { PlaybackManager.previousChannel() }
            PlaybackManager.ACTION_STOP -> {
                PlaybackManager.pause()
                PlaybackManager.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        PlaybackManager.service = this
        PlaybackManager.updateNotification()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (PlaybackManager.service === this) {
            PlaybackManager.service = null
        }
        super.onDestroy()
    }
}
