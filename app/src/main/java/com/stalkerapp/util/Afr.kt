package com.stalkerapp.util

import android.app.Activity
import android.os.Build
import android.view.Display
import kotlin.math.abs

/**
 * Auto Frame Rate (AFR): içerik kare hızına göre ekranın yenileme modunu
 * ayarlar (TV box'larda 24/25/50/60 Hz içerikte akıcılık için).
 * `preferredDisplayModeId` (API 23+) kullanılır; desteklenmeyen cihazlarda
 * sessizce hiçbir şey yapmaz.
 */
object Afr {

    // Uygulanan ekran modu kimliği (0 = uygulanmadı / varsayılan).
    private var appliedModeId = 0

    /**
     * [mode]: "off" | "match" (içerik kare hızına en yakın standart değer) |
     * sabit Hz ("24", "25", "30", "50", "60").
     */
    fun apply(activity: Activity?, mode: String, videoFrameRate: Float) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (mode == "off") return
        val target = when (mode) {
            "match" -> roundFrameRate(videoFrameRate) ?: return
            else -> mode.toIntOrNull() ?: return
        }
        val display = activity.windowManager.defaultDisplay
        var best: Display.Mode? = null
        for (m in display.supportedModes) {
            if (best == null || abs(m.refreshRate - target) < abs(best.refreshRate - target)) {
                best = m
            }
        }
        val id = best?.modeId ?: return
        if (id == appliedModeId) return
        appliedModeId = id
        activity.window.attributes = activity.window.attributes.apply {
            preferredDisplayModeId = id
        }
    }

    /** Oynatıcıdan çıkınca varsayılan (otomatik) ekran moduna döner. */
    fun clear(activity: Activity?) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (appliedModeId == 0) return
        appliedModeId = 0
        activity.window.attributes = activity.window.attributes.apply {
            preferredDisplayModeId = 0
        }
    }

    /** Kare hızını en yakın standart değere yuvarlar (24/25/30/50/60). */
    private fun roundFrameRate(rate: Float): Int? {
        if (rate <= 0f) return null
        val targets = intArrayOf(24, 25, 30, 50, 60)
        return targets.minByOrNull { abs(it - rate) }
    }
}
