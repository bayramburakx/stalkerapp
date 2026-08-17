package com.stalkerapp.playback

import android.app.Service
import android.content.Intent
import android.os.IBinder

class PlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PlaybackManager.ACTION_TOGGLE -> PlaybackManager.togglePlayPause()
            PlaybackManager.ACTION_NEXT -> PlaybackManager.nextChannel()
            PlaybackManager.ACTION_PREV -> PlaybackManager.previousChannel()
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
        if (PlaybackManager.service === this) {
            PlaybackManager.service = null
        }
        // DİKKAT: Burada PlaybackManager.stop() ÇAĞRILMAZ. Servis yalnızca açık
        // durdurma yollarıyla (kullanıcı çıkışı / ACTION_STOP) oynatmayı durdurur.
        // Geçici olaylar (ör. canlı akış hatası sonrası STATE_IDLE geçişi) servisi
        // stopSelf ile yok edebilir; burada oynatıcıyı durdurmak "önce 5-10 sn oynayıp
        // sonra siyah ekran" sorununa yol açıyordu (retry akışı da ölüyordu).
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
