package com.stalkerapp.util

import android.app.Activity
import android.os.Build
import android.view.Display
import android.view.Surface
import kotlin.math.abs

/**
 * Auto Frame Rate (AFR): içerik kare hızına göre ekranın yenileme modunu
 * ayarlar (TV box'larda 24/25/50/60 Hz içerikte akıcılık için).
 * `preferredDisplayModeId` (API 23+) kullanılır; desteklenmeyen cihazlarda
 * sessizce hiçbir şey yapmaz.
 *
 * API 30+ cihazlarda `Surface.setFrameRate()` ile daha hassas mod değişimi.
 */
object Afr {

    // Uygulanan ekran modu kimliği (0 = uygulanmadı / varsayılan)
    private var appliedModeId = 0
    private var appliedSurface: Surface? = null

    /**
     * [mode]: "off" | "match" (içerik kare hızına en yakın standart değer) |
     * sabit Hz ("24", "25", "30", "50", "60", "120", "144").
     */
    fun apply(activity: Activity?, mode: String, videoFrameRate: Float, surface: Surface? = null) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (mode == "off") return

        val target = when (mode) {
            "match" -> roundFrameRate(videoFrameRate) ?: return
            else -> mode.toIntOrNull() ?: return
        }

        // API 30+: Surface.setFrameRate() ile daha doğru frame rate sinyalleme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && surface != null) {
            runCatching {
                surface.setFrameRate(target.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
                appliedSurface = surface
            }
        }

        // preferredDisplayModeId: ekran yenileme hızını değiştir (API 23+)
        val display = activity.windowManager.defaultDisplay
        var best: Display.Mode? = null
        for (m in display.supportedModes) {
            // Çözünürlük tutarlılığı: mevcut çözünürlüğü koru, sadece yenileme hızı değişsin
            val sameResolution = m.physicalWidth == display.mode.physicalWidth &&
                m.physicalHeight == display.mode.physicalHeight
            if (!sameResolution && best != null) continue
            if (best == null ||
                abs(m.refreshRate - target) < abs(best.refreshRate - target) ||
                (sameResolution && !isCurrentResolution(best, display))
            ) {
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

        // Surface frame rate sıfırla
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                appliedSurface?.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
            }
            appliedSurface = null
        }

        // Display mode sıfırla
        if (appliedModeId == 0) return
        appliedModeId = 0
        activity.window.attributes = activity.window.attributes.apply {
            preferredDisplayModeId = 0
        }
    }

    /** Kare hızını en yakın standart değere yuvarlar (24/25/30/50/60/120/144). */
    private fun roundFrameRate(rate: Float): Int? {
        if (rate <= 0f) return null
        val targets = intArrayOf(24, 25, 30, 50, 60, 90, 120, 144)
        return targets.minByOrNull { abs(it - rate) }
    }

    private fun isCurrentResolution(mode: Display.Mode, display: Display): Boolean {
        return mode.physicalWidth == display.mode.physicalWidth &&
            mode.physicalHeight == display.mode.physicalHeight
    }

    /** Cihazın desteklediği yenileme hızları (Ayarlar → AFR açılır listesi için). */
    fun supportedRefreshRates(activity: Activity?): List<Int> {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return listOf(24, 25, 30, 50, 60)
        }
        return activity.windowManager.defaultDisplay.supportedModes
            .map { it.refreshRate.toInt() }
            .distinct()
            .sorted()
    }
}
