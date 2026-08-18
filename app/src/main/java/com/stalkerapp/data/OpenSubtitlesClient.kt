package com.stalkerapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * OpenSubtitles REST API v3 istemcisi.
 *
 * API belgeleri: https://opensubtitles.stoplight.io/docs/opensubtitles-api/
 *
 * Özellikler:
 *  - TMDB ID veya film/dizi adı ile arama
 *  - SRT/VTT/ASS formatı desteği
 *  - Dil filtreleme
 *  - API anahtarı ayarlardan alınır
 */
class OpenSubtitlesClient(private val apiKey: String = "") {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val BASE_URL = "https://api.opensubtitles.com/api/v1"
        // Ücretsiz geliştirici anahtarı (rate-limited); kullanıcı kendi anahtarını girer
        const val DEFAULT_API_KEY = "lBe0VKZR7PoHzAoUcVGbJwn2dkEMl0C"
    }

    @Serializable
    data class SubtitleEntry(
        val fileId: Long,
        val fileName: String,
        val language: String,
        val languageName: String,
        val downloadCount: Long = 0,
        val format: String = "srt",
        val fps: Float = 0f,
        val fromTrusted: Boolean = false,
        val rating: Float = 0f
    )

    private fun effectiveKey(): String = apiKey.ifBlank { DEFAULT_API_KEY }

    /**
     * Altyazı arar.
     * @param tmdbId TMDB ID'si (film veya dizi)
     * @param query Alternatif arama metni (tmdbId yoksa)
     * @param season Sezon (dizi için)
     * @param episode Bölüm (dizi için)
     * @param languages Dil kodları virgülle ayrılmış (ör. "tr,en")
     */
    suspend fun search(
        tmdbId: Long = 0,
        query: String = "",
        season: Int? = null,
        episode: Int? = null,
        languages: String = "tr,en"
    ): List<SubtitleEntry> = withContext(Dispatchers.IO) {
        if (effectiveKey().isBlank()) return@withContext emptyList()
        runCatching {
            val url = StringBuilder("$BASE_URL/subtitles?")
            if (tmdbId > 0) url.append("tmdb_id=$tmdbId&")
            if (query.isNotBlank()) url.append("query=${java.net.URLEncoder.encode(query, "UTF-8")}&")
            if (season != null) url.append("season_number=$season&")
            if (episode != null) url.append("episode_number=$episode&")
            url.append("languages=$languages&order_by=download_count&order_direction=desc")

            val req = Request.Builder()
                .url(url.toString())
                .header("Api-Key", effectiveKey())
                .header("Content-Type", "application/json")
                .header("User-Agent", "Portio v1.0")
                .build()

            val body = http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext emptyList()
                r.body?.string()
            } ?: return@withContext emptyList()

            val obj = json.parseToJsonElement(body).jsonObject
            val dataArr = obj["data"]?.jsonArray ?: return@withContext emptyList()

            dataArr.mapNotNull { el ->
                runCatching {
                    val attrs = el.jsonObject["attributes"]?.jsonObject ?: return@runCatching null
                    val files = attrs["files"]?.jsonArray ?: return@runCatching null
                    val firstFile = files.firstOrNull()?.jsonObject ?: return@runCatching null
                    val fileId = firstFile["file_id"]?.jsonPrimitive?.content?.toLongOrNull()
                        ?: return@runCatching null
                    val fileName = firstFile["file_name"]?.jsonPrimitive?.contentOrNull ?: ""
                    val lang = attrs["language"]?.jsonPrimitive?.contentOrNull ?: ""
                    val langName = attrs["language"]?.jsonPrimitive?.contentOrNull ?: lang
                    val dlCount = attrs["download_count"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0
                    val format = attrs["format"]?.jsonPrimitive?.contentOrNull ?: "srt"
                    val fps = attrs["fps"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                    val trusted = attrs["from_trusted"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val rating = attrs["ratings"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f

                    SubtitleEntry(fileId, fileName, lang, langName, dlCount, format, fps, trusted, rating)
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Altyazı dosyasını indirir.
     * @param fileId SubtitleEntry.fileId
     * @return SRT/VTT içeriği (indirme başarısız olursa null)
     */
    suspend fun download(fileId: Long): String? = withContext(Dispatchers.IO) {
        if (effectiveKey().isBlank()) return@withContext null
        runCatching {
            // Adım 1: Download link al
            val body = FormBody.Builder()
                .add("file_id", fileId.toString())
                .build()
            val req = Request.Builder()
                .url("$BASE_URL/download")
                .post(body)
                .header("Api-Key", effectiveKey())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "Portio v1.0")
                .build()

            val resp = http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                r.body?.string()
            } ?: return@withContext null

            val downloadUrl = json.parseToJsonElement(resp).jsonObject["link"]
                ?.jsonPrimitive?.contentOrNull ?: return@withContext null

            // Adım 2: İndir
            val dlReq = Request.Builder().url(downloadUrl).build()
            http.newCall(dlReq).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.string()
            }
        }.getOrNull()
    }
}
