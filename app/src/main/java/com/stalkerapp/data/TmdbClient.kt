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
    val actorNames: List<String> = emptyList(),
    /** Yönetmen(ler) — panel `director`'ı boşsa detay ekranı bunu kullanır. */
    val director: String = "",
    /** Çıkış yılı (YYY) — panel `year`/`first_air_date` boşsa arayüz burayı gösterir. */
    val year: String = "",
    /** TMDB poster yolu (ör. "/abc.jpg"); panel afişi boşsa buradan tam URL üretilir. */
    val posterPath: String = ""
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
    private val posterCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun hasKey(): Boolean = keyProvider().isNotBlank()

    fun getCachedPoster(name: String, isSeries: Boolean): String? {
        val key = "${if (isSeries) "tv" else "movie"}:${name.trim().lowercase()}"
        return posterCache[key]
    }

    /**
     * Dizi veya film için TMDB posterini çözer. API anahtarı giriliyse TMDB araması
     * yapar, bulunamazsa veya anahtar yoksa [fallbackPoster] döner.
     */
    suspend fun resolvePoster(
        name: String,
        year: String,
        isSeries: Boolean,
        fallbackPoster: String,
        apiKey: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || name.isBlank()) return@withContext fallbackPoster
        val key = "${if (isSeries) "tv" else "movie"}:${name.trim().lowercase()}"
        posterCache[key]?.let { if (it.isNotBlank()) return@withContext it }

        val id = searchTitle(name, year, isSeries, apiKey)
        if (id > 0) {
            val enr = enrich(id, isSeries, apiKey)
            if (enr.posterPath.isNotBlank()) {
                val fullUrl = photoUrl(enr.posterPath, large = true)
                posterCache[key] = fullUrl
                return@withContext fullUrl
            }
        }
        if (fallbackPoster.isNotBlank()) {
            posterCache[key] = fallbackPoster
        }
        return@withContext fallbackPoster
    }

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
        posterCache.clear()
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
        val director = extractDirector(obj["credits"])
        // Xtream panelleri yılı boş döndürebilir — TMDB'nin yayın tarihinden tamamlanır.
        val dateKey = if (isSeries) "first_air_date" else "release_date"
        val year = (obj[dateKey] as? JsonPrimitive)?.contentOrNull.orEmpty().take(4)
        val posterPath = (obj["poster_path"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return TmdbEnrichment(rating, trailerKey, cast, overview, actorNames, director, year, posterPath)
            .also { cache[cacheKey] = it }
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
     * Başlığa göre TMDB film/dizi kimliğini arar. M3U ve Xtream panelleri `tmdb_id`
     * alanını çoğu zaman boş/0 döndürür veya başlıkta IPTV etiketleri (TR |, 1080p,
     * Dublaj vb.) taşır. Başlık temizlenip ad + yıla göre aranır. Bulunamazsa 0 döner.
     */
    suspend fun searchTitle(name: String, year: String, isSeries: Boolean, apiKey: String): Long {
        val type = if (isSeries) "tv" else "movie"
        val cacheKey = "search:$type:$name:$year"
        cache[cacheKey]?.let { return it as Long }
        if (apiKey.isBlank() || name.isBlank()) return 0

        val (cleanedName, extractedYear) = cleanTitle(name)
        val searchName = cleanedName.ifBlank { name.trim() }
        val targetYear = year.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }
            ?: extractedYear

        // 1. Temizlenmiş ad + yıl ile dene
        var id = queryTmdb(type, searchName, targetYear, apiKey)

        // 2. Bulunamazsa ve yıl verilmişse yıl olmadan temiz adla dene
        if (id <= 0 && targetYear != null) {
            id = queryTmdb(type, searchName, null, apiKey)
        }

        // 3. Bulunamazsa orijinal ad + yıl ile dene
        if (id <= 0 && searchName != name.trim()) {
            id = queryTmdb(type, name.trim(), targetYear, apiKey)
        }

        // 4. Bulunamazsa orijinal adla yıl olmadan dene
        if (id <= 0 && searchName != name.trim() && targetYear != null) {
            id = queryTmdb(type, name.trim(), null, apiKey)
        }

        if (id > 0) {
            cache[cacheKey] = id
        }
        return id
    }

    private suspend fun queryTmdb(type: String, query: String, year: String?, apiKey: String): Long {
        if (query.isBlank()) return 0
        val url = buildString {
            append("https://api.themoviedb.org/3/search/$type?api_key=$apiKey&query=")
            append(Uri.encode(query))
            if (year != null) {
                append(if (type == "tv") "&first_air_date_year=$year" else "&year=$year")
            }
            append("&language=${languageProvider()}")
        }
        val obj = getJson(url) ?: return 0
        val results = obj["results"] as? JsonArray ?: return 0
        if (results.isEmpty()) return 0
        return resolveId(results, query)
    }

    /** Arama sonuçlarından TMDB kimliğini çözer: tam ad eşleşmesi önceliklidir, yoksa ilk sonuç. */
    private fun resolveId(results: JsonArray, name: String): Long {
        val objs = results.mapNotNull { it as? JsonObject }
        val target = name.trim().lowercase()
        // Tam ad eşleşmesini öncele.
        objs.firstNotNullOfOrNull { o ->
            val n = (o["name"] as? JsonPrimitive)?.contentOrNull
                ?: (o["title"] as? JsonPrimitive)?.contentOrNull
                ?: (o["original_name"] as? JsonPrimitive)?.contentOrNull
                ?: (o["original_title"] as? JsonPrimitive)?.contentOrNull
                ?: return@firstNotNullOfOrNull null
            if (n.trim().lowercase() == target) {
                (o["id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
            } else null
        }?.let { return it }
        // Aksi halde ilk sonucu kabul et.
        return objs.firstNotNullOfOrNull { o ->
            (o["id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        } ?: 0
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

    /** Yönetmen(leri) — crew içindeki job "Director"/"Yönetmen" olan kişiler. */
    private fun extractDirector(credits: JsonElement?): String {
        val arr = (credits as? JsonObject)?.get("crew") as? JsonArray ?: return ""
        return arr.mapNotNull { v ->
            val o = v as? JsonObject ?: return@mapNotNull null
            val job = (o["job"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            if (job.equals("Director", ignoreCase = true) || job.equals("Yönetmen", ignoreCase = true)) {
                (o["name"] as? JsonPrimitive)?.contentOrNull
            } else null
        }.filter { it.isNotBlank() }.joinToString(", ")
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        const val IMAGE_BASE = "https://image.tmdb.org/t/p/w185"
        const val IMAGE_BASE_LARGE = "https://image.tmdb.org/t/p/w342"

        /** TMDB görsel yolu -> tam URL (boşsa boş döner). */
        fun photoUrl(path: String, large: Boolean = false): String =
            if (path.isBlank()) "" else (if (large) IMAGE_BASE_LARGE else IMAGE_BASE) + path

        /**
         * M3U ve IPTV başlıklarını TMDB aramasına uygun hale getirir.
         * "TR | Inception (2010) [1080p] [Türkçe Dublaj]" -> ("Inception", "2010")
         */
        fun cleanTitle(rawName: String): Pair<String, String?> {
            var name = rawName.trim()
            val yearRegex = Regex("""[\(\[\s\-_–](19\d\d|20\d\d)[\)\]\s\-_–]?""")
            val foundYear = yearRegex.findAll(name).lastOrNull()?.groupValues?.getOrNull(1)

            // IPTV ön ekleri: "TR | ", "[TR] ", "VIP | ", "FILM | ", "4K | " vb.
            name = name.replace(Regex("""^(\[[^\]]+\]|\([^)]+\)|[A-Za-z0-9_#\s+-]{1,12}[:|-])\s*"""), "")

            // Çözünürlük ve kodek etiketleri
            val tagsRegex = Regex("""(?i)\b(4k|uhd|fhd|hd|sd|1080p|1080i|720p|480p|2160p|hevc|x264|x265|h264|h265|10bit|bluray|blu-ray|web-dl|webrip|dvdrip|remux|hdr|dolby|atmos|aac|ac3|dts|xvid)\b""")
            name = name.replace(tagsRegex, "")

            // Dublaj / altyazı ve sürüm etiketleri
            val langRegex = Regex("""(?i)\b(dublaj|altyazili|altyazılı|turkce|türkçe|dual|multi|multisub|multi-sub|tr-en|en-tr|original|extended|unrated|directors cut|director's cut)\b""")
            name = name.replace(langRegex, "")

            // Köşeli ve normal parantez içi ek bilgiler
            name = name.replace(Regex("""\[[^\]]*\]"""), "")
            name = name.replace(Regex("""\((19\d\d|20\d\d)[^)]*\)"""), "")
            name = name.replace(Regex("""\([^)]*(dublaj|altyaz|1080|720|4k|fhd|hevc|uhd)[^)]*\)""", RegexOption.IGNORE_CASE), "")

            // Dizi bölüm kalıpları
            name = name.replace(Regex("""(?i)\b(s\d{1,2}\s*[.x_–-]?\s*e\d{1,3}|\d{1,2}\s*x\s*\d{1,3}|\d{1,2}\.?\s*sezon|\d{1,3}\.?\s*b[oö]l[uü]m|season\s*\d+|episode\s*\d+|ep\s*\d+)\b"""), "")

            // Nokta ve alt çizgileri boşluğa çevir
            name = name.replace('.', ' ').replace('_', ' ')

            // Baştaki/sondaki tire ve noktalama işaretlerini temizle
            name = name.replace(Regex("""\s*[-–:]+\s*$"""), "")
            name = name.replace(Regex("""^\s*[-–:]+\s*"""), "")
            name = name.replace(Regex("""\s+"""), " ").trim()

            return name to foundYear
        }
    }
}
