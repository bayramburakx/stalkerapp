package com.stalkerapp.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** TMDB'den zenginleştirilmiş bir oyuncu (fotoğraf yolu + rol adı). */
data class TmdbPerson(
    val id: Long = 0,
    val name: String = "",
    /** TMDB profil görseli yolu (ör. "/abc.jpg"); [photoUrl] ile tam URL üretilir. */
    val photoPath: String = "",
    val character: String = ""
)

/** Bir film/dizi için TMDB zenginleştirmesi: puan + fragman + oyuncu fotoğrafları. */
data class TmdbEnrichment(
    val rating: Double = 0.0,
    /** YouTube video id'si; boşsa fragman yok demektir. */
    val trailerKey: String = "",
    val cast: List<TmdbPerson> = emptyList()
)

/**
 * TMDB API istemcisi. Anahtar yoksa tüm çağrılar boş sonuç döner (özellik
 * sessizce kapalı kalır). Yanıtlar oturum boyunca bellek içinde önbelleklenir.
 */
class TmdbClient(private val keyProvider: () -> String) {

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val cache = mutableMapOf<String, Any>()

    fun hasKey(): Boolean = keyProvider().isNotBlank()

    private suspend fun getJson(url: String): JsonObject? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url).build()
            okHttp.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val text = r.body?.string().orEmpty()
                if (text.isBlank()) return@use null
                json.parseToJsonElement(text).jsonObject
            }
        }.getOrNull()
    }

    /**
     * Film ya da dizi için TMDB puanı, fragman (YouTube) ve oyuncu fotoğraflarını
     * getirir. `append_to_response=videos,credits` tek istekte alınır.
     */
    suspend fun enrich(tmdbId: Long, isSeries: Boolean, apiKey: String): TmdbEnrichment {
        val cacheKey = "${if (isSeries) "tv" else "movie"}-$tmdbId"
        cache[cacheKey]?.let { return it as TmdbEnrichment }
        if (tmdbId <= 0 || apiKey.isBlank()) return TmdbEnrichment()
        val type = if (isSeries) "tv" else "movie"
        val url = "https://api.themoviedb.org/3/$type/$tmdbId?api_key=$apiKey&append_to_response=videos,credits&language=tr-TR"
        val obj = getJson(url) ?: return TmdbEnrichment()
        val rating = (obj["vote_average"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0
        val trailerKey = extractTrailer(obj["videos"])
        val cast = extractCast(obj["credits"])
        return TmdbEnrichment(rating, trailerKey, cast).also { cache[cacheKey] = it }
    }

    /** İsme göre kişi ara; fotoğraf yolu + TMDB kişi id'si döner. */
    suspend fun searchPerson(name: String, apiKey: String): TmdbPerson? {
        val cacheKey = "person:$name"
        cache[cacheKey]?.let { return it as TmdbPerson? }
        if (apiKey.isBlank()) return null
        val url = "https://api.themoviedb.org/3/search/person?api_key=$apiKey&query=${Uri.encode(name)}&language=tr-TR"
        val obj = getJson(url) ?: return null
        val results = obj["results"] as? JsonArray ?: return null
        val first = results.firstOrNull { it is JsonObject } as? JsonObject ?: return null
        val person = TmdbPerson(
            id = (first["id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0,
            name = (first["name"] as? JsonPrimitive)?.contentOrNull ?: name,
            photoPath = (first["profile_path"] as? JsonPrimitive)?.contentOrNull ?: ""
        )
        cache[cacheKey] = person
        return person
    }

    private fun extractTrailer(videos: JsonElement?): String {
        val arr = (videos as? JsonObject)?.get("results") as? JsonArray ?: return ""
        for (v in arr) {
            val o = v as? JsonObject ?: continue
            val site = (o["site"] as? JsonPrimitive)?.contentOrNull
            val type = (o["type"] as? JsonPrimitive)?.contentOrNull
            if (site.equals("YouTube", true) &&
                (type.equals("Trailer", true) || type.equals("Teaser", true))
            ) {
                (o["key"] as? JsonPrimitive)?.contentOrNull?.let { return it }
            }
        }
        return ""
    }

    private fun extractCast(credits: JsonElement?): List<TmdbPerson> {
        val arr = (credits as? JsonObject)?.get("cast") as? JsonArray ?: return emptyList()
        return arr.mapNotNull { v ->
            val o = v as? JsonObject ?: return@mapNotNull null
            val name = (o["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            TmdbPerson(
                id = (o["id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0,
                name = name,
                photoPath = (o["profile_path"] as? JsonPrimitive)?.contentOrNull ?: "",
                character = (o["character"] as? JsonPrimitive)?.contentOrNull ?: ""
            )
        }.distinctBy { it.id }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        const val IMAGE_BASE = "https://image.tmdb.org/t/p/w185"
        const val IMAGE_BASE_LARGE = "https://image.tmdb.org/t/p/w342"

        /** TMDB görsel yolu -> tam URL (boşsa boş döner). */
        fun photoUrl(path: String, large: Boolean = false): String =
            if (path.isBlank()) "" else (if (large) IMAGE_BASE_LARGE else IMAGE_BASE) + path
    }
}
