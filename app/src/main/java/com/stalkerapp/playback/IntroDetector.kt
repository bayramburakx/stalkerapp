package com.stalkerapp.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Intro/Outro tespiti: TMDB ve akıllı varsayılan aralıklar ile dizi bölümlerinde
 * intro ve outro aralıklarını tespit eder. Stalker, Xtream ve M3U tüm dizi kaynaklarında çalışır.
 */
object IntroDetector {

    data class IntroRange(
        /** Intro başlangıcı (ms) — varsayılan 0. */
        val startMs: Long = 0L,
        /** Intro bitişi (ms) — "Atla" buraya seek eder. */
        val endMs: Long = 85_000L,
        /** Outro başlangıcı (ms). -1 = outro tespiti yok. */
        val outroStartMs: Long = -1L
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    // Bellek içi önbellek: "tmdbId:season:episode" → IntroRange
    private val cache = mutableMapOf<String, IntroRange?>()

    /**
     * Belirli bir bölüm için intro aralığını döner.
     *
     * @param tmdbId TMDB dizi ID'si (0 ise akıllı varsayılan kullanılır)
     * @param season Sezon numarası
     * @param episode Bölüm numarası
     * @param apiKey TMDB API anahtarı (isteğe bağlı)
     * @param durationMs Oynatıcıdan alınan bölüm süresi (ms)
     */
    suspend fun detect(
        tmdbId: Long,
        season: Int,
        episode: Int,
        apiKey: String,
        durationMs: Long
    ): IntroRange {
        val effectiveDuration = if (durationMs > 0) durationMs else 25 * 60_000L
        val defaultIntroEnd = minOf((effectiveDuration * 0.15).toLong(), 90_000L).coerceAtLeast(60_000L)
        val defaultOutroStart = if (effectiveDuration > 120_000L) (effectiveDuration * 0.94).toLong() else -1L

        val fallback = IntroRange(
            startMs = 0L,
            endMs = defaultIntroEnd,
            outroStartMs = defaultOutroStart
        )

        if (apiKey.isBlank() || tmdbId <= 0L || season <= 0 || episode <= 0) {
            return fallback
        }

        val cacheKey = "$tmdbId:$season:$episode"
        cache[cacheKey]?.let { return it ?: fallback }

        val result = runCatching {
            fetchIntroRange(tmdbId, season, episode, apiKey, effectiveDuration)
        }.getOrNull() ?: fallback

        cache[cacheKey] = result
        return result
    }

    private suspend fun fetchIntroRange(
        tmdbId: Long,
        season: Int,
        episode: Int,
        apiKey: String,
        durationMs: Long
    ): IntroRange? = withContext(Dispatchers.IO) {
        val url = "https://api.themoviedb.org/3/tv/$tmdbId/season/$season/episode/$episode" +
            "?api_key=$apiKey&language=en-US"
        val req = Request.Builder().url(url).build()
        val body = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@withContext null
            r.body?.string()
        } ?: return@withContext null

        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return@withContext null

        val runtimeMin = obj["runtime"]?.jsonPrimitive?.content?.toLongOrNull()
        val effectiveDuration = if (runtimeMin != null && runtimeMin > 0) {
            runtimeMin * 60_000L
        } else {
            durationMs.takeIf { it > 0 } ?: (25 * 60_000L)
        }

        val introStart = 0L
        val introEnd = minOf((effectiveDuration * 0.16).toLong(), 110_000L)
            .coerceAtLeast(60_000L)

        val outroStart = (effectiveDuration * 0.93).toLong()

        IntroRange(
            startMs = introStart,
            endMs = introEnd,
            outroStartMs = if (effectiveDuration > 120_000L) outroStart else -1L
        )
    }

    /** Önbelleği temizler (portal değişince / ayarlar sıfırlanınca). */
    fun clearCache() = cache.clear()
}
