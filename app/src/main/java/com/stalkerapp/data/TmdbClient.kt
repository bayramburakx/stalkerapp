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

/** TMDB'den alınan tek bölüm bilgisi (gerçek ad + küçük resim). */
data class TmdbEpisodeInfo(
    val name: String = "",
    val stillPath: String = ""
)

/** Bir film/dizi için TMDB zenginleştirmesi: puan + fragman + oyuncu fotoğrafları. */
data class TmdbEnrichment(
    val rating: Double = 0.0,
    /** YouTube video id'si; boşsa fragman yok demektir. */
    val trailerKey: String = "",
    val cast: List<TmdbPerson> = emptyList(),
    /**
     * TMDB özeti (synopsis). Xtream filmleri `plot`'u boş döndürür — bu durumda
     * detay ekranı özeti buradan gösterir.
     */
    val overview: String = "",
    /** TMDB oyuncu adları (panel `cast`'ı boşsa oyuncular bölümü buradan doldurulur). */
    val actorNames: List<String> = emptyList()
)

/**
 * TMDB API istemcisi. Anahtar yoksa tüm çağrılar boş sonuç döner (özellik
 * sessizce kapalı kalır). Yanıtlar oturum boyunca bellek içinde önbelleklenir.
 */
class TmdbClient(
    private val keyProvider: () -> String,
    private val languageProvider: () -> String = { "tr" }
) {

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Fragman akışı dış sunuculardan (Piped) çekilir: bu sunucular yavaş veya
    // geçici kapalı olabildiği için kısa zaman aşımıyla ayrı bir istemci kullanılır
    // — böylece kullanıcı uzun süre bekleyip YouTube'a düşmez.
    private val streamHttp = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val cache = mutableMapOf<String, Any>()

    fun hasKey(): Boolean = keyProvider().isNotBlank()

    /** Anahtarın geçerli olup olmadığını hafif bir istekle doğrular (Ayarlar → Entegrasyonlar). */
    suspend fun testKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) false
        else runCatching {
            val req = Request.Builder()
                .url("https://api.themoviedb.org/3/configuration?api_key=$apiKey")
                .build()
            okHttp.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** TMDB yanıt önbelleğini temizler. */
    fun clearCache() {
        cache.clear()
    }

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
        val url = "https://api.themoviedb.org/3/$type/$tmdbId?api_key=$apiKey&append_to_response=videos,credits&language=${languageProvider()}"
        val obj = getJson(url) ?: return TmdbEnrichment()
        val rating = (obj["vote_average"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0
        val trailerKey = extractTrailer(obj["videos"])
        val cast = extractCast(obj["credits"])
        val overview = (obj["overview"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val actorNames = cast.map { it.name }.filter { it.isNotBlank() }.distinct().take(20)
        return TmdbEnrichment(rating, trailerKey, cast, overview, actorNames).also { cache[cacheKey] = it }
    }

    /**
     * Bir dizinin belirli bir sezonunun gerçek posterini döner (TMDB sezon
     * posterleri dizi başına farklıdır; portal genelde aynı afişi tekrarlar).
     * Boş dönerse arayan taraf portaldaki afişe düşer.
     */
    suspend fun seasonPoster(tmdbId: Long, seasonNumber: Int, apiKey: String): String {
        val cacheKey = "tv:$tmdbId:season:$seasonNumber"
        cache[cacheKey]?.let { return it as String }
        if (tmdbId <= 0 || apiKey.isBlank()) return ""
        val url = "https://api.themoviedb.org/3/tv/$tmdbId/season/$seasonNumber?api_key=$apiKey&language=${languageProvider()}"
        val obj = getJson(url)
        val path = (obj?.get("poster_path") as? JsonPrimitive)?.contentOrNull.orEmpty()
        // Ağ hatasıyla (obj == null) boş değer kalıcı olarak önbelleğe alınmasın.
        if (obj != null) cache[cacheKey] = path
        return path
    }

    /**
     * Bir bölümün TMDB bilgisi: gerçek bölüm adı + küçük resim (still). Tek
     * API çağrısı ile ikisi birden alınır (portaldaki bölümlerde ad yoktur;
     * anahtar girilince buradan gelir). Sonuç oturum boyunca önbelleklenir.
     */
    suspend fun episodeInfo(tmdbId: Long, seasonNumber: Int, episodeNumber: Int, apiKey: String): TmdbEpisodeInfo {
        val cacheKey = "tv:$tmdbId:s$seasonNumber:e$episodeNumber"
        cache[cacheKey]?.let { return it as TmdbEpisodeInfo }
        if (tmdbId <= 0 || apiKey.isBlank()) return TmdbEpisodeInfo()
        val url = "https://api.themoviedb.org/3/tv/$tmdbId/season/$seasonNumber/episode/$episodeNumber?api_key=$apiKey&language=${languageProvider()}"
        val obj = getJson(url)
        val info = TmdbEpisodeInfo(
            name = (obj?.get("name") as? JsonPrimitive)?.contentOrNull.orEmpty(),
            stillPath = (obj?.get("still_path") as? JsonPrimitive)?.contentOrNull.orEmpty()
        )
        // Ağ hatasıyla (obj == null) boş değer kalıcı olarak önbelleğe alınmasın.
        if (obj != null) cache[cacheKey] = info
        return info
    }

    /**
     * Başlığa göre TMDB film/dizi kimliğini arar. Xtream panelleri `tmdb_id`
     * alanını çoğu zaman boş/0 döndürür — bu durumda detay ekranı ad + yıla
     * göre eşleştirme yapıp buradan kimliği alır (oyuncu fotoğrafları, sezon
     * kapakları ve fragmanlar için). Bulunamazsa 0 döner.
     */
    suspend fun searchTitle(name: String, year: String, isSeries: Boolean, apiKey: String): Long {
        val type = if (isSeries) "tv" else "movie"
        val cacheKey = "search:$type:$name:$year"
        cache[cacheKey]?.let { return it as Long }
        if (apiKey.isBlank() || name.isBlank()) return 0
        val y = year.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }
        val url = buildString {
            append("https://api.themoviedb.org/3/search/$type?api_key=$apiKey&query=")
            append(Uri.encode(name))
            if (y != null) {
                append(if (isSeries) "&first_air_date_year=$y" else "&year=$y")
            }
            append("&language=${languageProvider()}")
        }
        val obj = getJson(url) ?: return 0
        val results = obj["results"] as? JsonArray ?: return 0
        // Tam ad eşleşmesi önceliklidir; yoksa ilk sonuç kabul edilir.
        val id = results.mapNotNull { it as? JsonObject }.firstNotNullOfOrNull { o ->
            val n = (o["name"] as? JsonPrimitive)?.contentOrNull
                ?: (o["title"] as? JsonPrimitive)?.contentOrNull
                ?: return@firstNotNullOfOrNull null
            if (n.equals(name.trim(), ignoreCase = true)) {
                (o["id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: return@firstNotNullOfOrNull null
            } else null
        } ?: results.mapNotNull { it as? JsonObject }.firstNotNullOfOrNull { o ->
            (o["id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        } ?: 0
        cache[cacheKey] = id
        return id
    }

    /** İsme göre kişi ara; fotoğraf yolu + TMDB kişi id'si döner. */
    suspend fun searchPerson(name: String, apiKey: String): TmdbPerson? {
        val cacheKey = "person:$name"
        cache[cacheKey]?.let { return it as TmdbPerson? }
        if (apiKey.isBlank()) return null
        val url = "https://api.themoviedb.org/3/search/person?api_key=$apiKey&query=${Uri.encode(name)}&language=${languageProvider()}"
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

    /**
     * Bir YouTube fragmanın doğrudan oynatılabilir video akışını döner.
     * TMDB videoları barındırmaz — yalnızca YouTube video kimliğini verir;
     * videonun kendisi YouTube'da. WebView ile site açmak yerine video akışını
     * Piped (YouTube ön yüzü) genel örneklerinden çekip ExoPlayer ile oynatırız.
     * Hiçbir örnek çalışmazsa boş döner (çağıran YouTube uygulamasına düşer).
     */
    suspend fun youtubeStreamUrl(videoId: String): String? {
        if (videoId.isBlank()) return null
        val instances = listOf(
            "https://api.piped.private.coffee",
            "https://pipedapi.adminforge.de",
            "https://piped-api.lunar.icu",
            "https://pipedapi.drgns.space",
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.leptons.xyz",
            "https://pipedapi.ducks.party"
        )
        for (inst in instances) {
            val obj = runCatching { getStreamJson("$inst/streams/$videoId") }.getOrNull() ?: continue
            val streams = obj["videoStreams"] as? JsonArray ?: continue
            // En yüksek çözünürlükteki akışı seç (videoOnly olmayan tercih edilir).
            val picked = streams.mapNotNull { it as? JsonObject }
                .filter { (it["url"] as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true }
                .sortedWith(
                    compareByDescending<JsonObject> { (it["videoOnly"] as? JsonPrimitive)?.contentOrNull == "true" }
                        .thenByDescending { (it["quality"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0 }
                )
                .firstOrNull()
            (picked?.get("url") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    /** Dış sunucu isteği: kısa zaman aşımı, JSON yanıtı beklenmezse null. */
    private suspend fun getStreamJson(url: String): JsonObject? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            streamHttp.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val text = r.body?.string().orEmpty()
                if (text.isBlank()) return@use null
                json.parseToJsonElement(text).jsonObject
            }
        }.getOrNull()
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
