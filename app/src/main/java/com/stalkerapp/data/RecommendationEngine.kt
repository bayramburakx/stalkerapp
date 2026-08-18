package com.stalkerapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Kişiselleştirilmiş içerik öneri motoru.
 *
 * Algoritma:
 * 1. İzleme geçmişinden tür/kategori ağırlıklarını çıkart
 * 2. En çok izlenen 3 tür için TMDB /discover/movie ve /discover/tv çağrısı yap
 * 3. Sonuçları birleştir, zaten izlenenleri filtrele, skora göre sırala
 */
class RecommendationEngine(
    private val tmdbApiKey: String,
    private val language: String = "tr"
) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    data class Recommendation(
        val tmdbId: Long,
        val title: String,
        val posterPath: String,
        val rating: Double,
        val year: String,
        val isSeries: Boolean,
        val score: Double = 0.0
    )

    /** TMDB tür adı → ID eşlemesi (sık kullanılan). */
    private val genreMap = mapOf(
        "aksiyon" to 28, "action" to 28,
        "komedi" to 35, "comedy" to 35,
        "dram" to 18, "drama" to 18,
        "gerilim" to 53, "thriller" to 53,
        "korku" to 27, "horror" to 27,
        "bilim kurgu" to 878, "sci-fi" to 878,
        "animasyon" to 16, "animation" to 16,
        "belgesel" to 99, "documentary" to 99,
        "aile" to 10751, "family" to 10751,
        "macera" to 12, "adventure" to 12,
        "romantik" to 10749, "romance" to 10749,
        "suç" to 80, "crime" to 80
    )

    /**
     * İzleme geçmişine göre öneriler üretir.
     * @param watchedItems Son izlenen VodItem listesi (kategori/tür bilgisi içermeli)
     * @param watchedIds Zaten izlenmiş ID'ler (filtrelemek için)
     */
    suspend fun getRecommendations(
        watchedItems: List<VodItem>,
        watchedIds: Set<Long> = emptySet()
    ): List<Recommendation> = withContext(Dispatchers.IO) {
        if (tmdbApiKey.isBlank()) return@withContext emptyList()

        // Tür ağırlıklarını çıkart
        val genreWeights = mutableMapOf<Int, Double>()
        for (item in watchedItems) {
            val genres = item.genres.split(",").map { it.trim().lowercase() }
            for (g in genres) {
                val id = genreMap.entries.firstOrNull { g.contains(it.key) }?.value ?: continue
                genreWeights[id] = (genreWeights[id] ?: 0.0) + 1.0
            }
        }

        // En yüksek ağırlıklı 3 tür
        val topGenres = genreWeights.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        if (topGenres.isEmpty()) return@withContext getPopular()

        val result = mutableListOf<Recommendation>()
        val genreIds = topGenres.joinToString(",")

        // Film önerileri
        runCatching {
            val url = "https://api.themoviedb.org/3/discover/movie" +
                "?api_key=$tmdbApiKey&with_genres=$genreIds&language=$language" +
                "&sort_by=popularity.desc&vote_count.gte=100&page=1"
            result.addAll(parseDiscoverMovies(url, watchedIds, isSeries = false, genreWeights))
        }

        // Dizi önerileri
        runCatching {
            val url = "https://api.themoviedb.org/3/discover/tv" +
                "?api_key=$tmdbApiKey&with_genres=$genreIds&language=$language" +
                "&sort_by=popularity.desc&vote_count.gte=50&page=1"
            result.addAll(parseDiscoverMovies(url, watchedIds, isSeries = true, genreWeights))
        }

        result.sortedByDescending { it.score }.distinctBy { it.tmdbId }.take(30)
    }

    private suspend fun getPopular(): List<Recommendation> {
        val url = "https://api.themoviedb.org/3/movie/popular" +
            "?api_key=$tmdbApiKey&language=$language&page=1"
        return runCatching { parseDiscoverMovies(url, emptySet(), false, emptyMap()) }
            .getOrDefault(emptyList())
    }

    private suspend fun parseDiscoverMovies(
        url: String,
        watchedIds: Set<Long>,
        isSeries: Boolean,
        weights: Map<Int, Double>
    ): List<Recommendation> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).build()
        val body = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@withContext emptyList()
            r.body?.string()
        } ?: return@withContext emptyList()

        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return@withContext emptyList()

        val results = obj["results"]?.jsonArray ?: return@withContext emptyList()

        results.mapNotNull { el ->
            runCatching {
                val o = el.jsonObject
                val id = o["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@runCatching null
                if (id in watchedIds) return@runCatching null
                val title = o["title"]?.jsonPrimitive?.contentOrNull
                    ?: o["name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
                val poster = o["poster_path"]?.jsonPrimitive?.contentOrNull?.let {
                    "https://image.tmdb.org/t/p/w500$it"
                } ?: ""
                val rating = o["vote_average"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                val year = (o["release_date"] ?: o["first_air_date"])?.jsonPrimitive
                    ?.contentOrNull?.take(4) ?: ""
                val popularity = o["popularity"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                val score = rating * 10 + popularity * 0.01

                Recommendation(id, title, poster, rating, year, isSeries, score)
            }.getOrNull()
        }
    }
}

/**
 * Katalog üzerinden çalışan kolaylık işlevi: izleme geçmişinden tür
 * ağırlıklarını çıkarıp TMDB discover'dan öneriler üretir ve sonuçları
 * katalogdaki gerçek VodItem'lara eşler (Anasayfa → "Senin İçin" satırı).
 */
suspend fun generateRecommendations(
    catalogItems: List<VodItem>,
    watchedIds: Set<Long>,
    apiKey: String,
    language: String = "tr"
): List<VodItem> {
    if (apiKey.isBlank() || catalogItems.isEmpty()) return emptyList()
    val watched = catalogItems.filter { it.id in watchedIds }
    if (watched.isEmpty()) return emptyList()
    val watchedTmdb = watched.mapNotNull { it.tmdbId.takeIf { t -> t > 0 } }.toSet()
    val recs = RecommendationEngine(apiKey, language).getRecommendations(watched, watchedTmdb)
    return recs.mapNotNull { r ->
        catalogItems.firstOrNull { it.tmdbId == r.tmdbId }
            ?: catalogItems.firstOrNull {
                it.name.contains(r.title, ignoreCase = true) ||
                    r.title.contains(it.name, ignoreCase = true)
            }
    }.distinctBy { it.id }
}
