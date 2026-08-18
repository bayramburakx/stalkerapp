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
 * Intro/Outro tespiti: TMDB episode detail endpoint üzerinden bölüm süresini
 * çekip portal'dan gelen bölüm süresiyle karşılaştırarak intro aralığını tahmin eder.
 *
 * TMDB v4 "episode groups" veya "content_ratings" endpointlerinde intro timestamp
 * bulunmadığından pratik yaklaşım: bölüm süresinin ilk %25'i ve son %10'u intro/outro
 * olarak işaretlenir (özelleştirilebilir), kullanıcı "Atla" butonuyla bu bölümü geçer.
 *
 * Gelişmiş (isteğe bağlı): Bazı kaynaklar [tvdb-api] veya
 * prelude.xyz gibi hizmetlerden intro timestamp çekebilir.
 */
object IntroDetector {

    data class IntroRange(
        /** Intro başlangıcı (ms). */
        val startMs: Long,
        /** Intro bitişi (ms) — "Atla" buraya seek eder. */
        val endMs: Long,
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
     * @param tmdbId TMDB dizi ID'si
     * @param season Sezon numarası
     * @param episode Bölüm numarası
     * @param apiKey TMDB API anahtarı (boşsa null döner)
     * @param durationMs Oynatıcıdan alınan bölüm süresi (ms)
     */
    suspend fun detect(
        tmdbId: Long,
        season: Int,
        episode: Int,
        apiKey: String,
        durationMs: Long
    ): IntroRange? {
        if (apiKey.isBlank() || tmdbId <= 0L || season <= 0 || episode <= 0) return null
        val cacheKey = "$tmdbId:$season:$episode"
        cache[cacheKey]?.let { return it }

        val result = runCatching {
            fetchIntroRange(tmdbId, season, episode, apiKey, durationMs)
        }.getOrNull()
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
        // TMDB'den bölüm detayını al (runtime alanı dakika cinsinden)
        val url = "https://api.themoviedb.org/3/tv/$tmdbId/season/$season/episode/$episode" +
            "?api_key=$apiKey&language=en-US"
        val req = Request.Builder().url(url).build()
        val body = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@withContext null
            r.body?.string()
        } ?: return@withContext null

        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return@withContext null

        // TMDB runtime alanı (dakika). Yoksa oynatıcı süresini kullan.
        val runtimeMin = obj["runtime"]?.jsonPrimitive?.content?.toLongOrNull()
        val effectiveDuration = if (runtimeMin != null && runtimeMin > 0) {
            runtimeMin * 60_000L
        } else {
            durationMs.takeIf { it > 0 } ?: return@withContext null
        }

        // Intro tahmini: bölümün 0:30 → %18 arası (tipik dizi introsu 20-90 sn)
        val introStart = 15_000L // 15 sn
        val introEnd = minOf((effectiveDuration * 0.18).toLong(), 120_000L)
            .coerceAtLeast(30_000L)

        // Outro: son %8
        val outroStart = (effectiveDuration * 0.92).toLong()

        IntroRange(
            startMs = introStart,
            endMs = introEnd,
            outroStartMs = if (effectiveDuration > 120_000L) outroStart else -1L
        )
    }

    /** Önbelleği temizler (portal değişince / ayarlar sıfırlanınca). */
    fun clearCache() = cache.clear()
}
