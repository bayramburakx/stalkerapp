package com.stalkerapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Cihaz açılışında uygulamayı otomatik başlatır (TV box'lar için).
 * Yalnızca Ayarlar → Görünüm & Cihaz → "Cihaz Açılışında Başlat" açıksa çalışır.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }
        val app = context.applicationContext as StalkerApp
        if (!app.store.settings().startOnBoot) return
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(launch) }
    }
}
