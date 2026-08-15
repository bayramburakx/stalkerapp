package com.stalkerapp.data

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

/**
 * M3U listesi ayrıştırıcısı. Standart `#EXTM3U` / `#EXTINF` biçimini okur:
 * `#EXTINF:-1 tvg-id="..." tvg-logo="..." group-title="...",Kanal Adı` + URL satırı.
 */
object M3uParser {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** M3U içeriğini URL'den indirir; başarısız olursa null. */
    suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url)
                .header("User-Agent", "StalkerPlayer/1.0")
                .build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                r.body?.string()
            }
        }.getOrNull()
    }

    /** #EXTINF satırındaki tvg-* özniteliklerini okur. */
    private fun parseAttributes(extinf: String): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        val regex = Regex("""([a-zA-Z0-9_-]+)="([^"]*)"""")
        regex.findAll(extinf).forEach { m ->
            attrs[m.groupValues[1]] = m.groupValues[2]
        }
        return attrs
    }

    fun parse(text: String, sourceId: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = text.split("\n").map { it.trim() }
        var i = 0
        var index = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXTINF")) {
                val attrs = parseAttributes(line)
                // Başlık: son virgülden sonrası.
                val name = line.substringAfterLast(",", "").trim()
                    .ifBlank { attrs["tvg-name"].orEmpty() }
                var url = ""
                var j = i + 1
                while (j < lines.size && lines[j].isNotBlank() && !lines[j].startsWith("#")) {
                    if (url.isEmpty()) url = lines[j]
                    j++
                }
                i = j
                if (url.isNotBlank() && name.isNotBlank()) {
                    index++
                    channels += Channel(
                        id = (sourceId + "|" + url).hashCode().toLong().and(0xFFFFFFFFL).let { if (it == 0L) 1L else it },
                        name = name,
                        number = index,
                        logo = attrs["tvg-logo"].orEmpty(),
                        cmd = url,
                        tvGenreTitle = attrs["group-title"].orEmpty(),
                        xmltvId = attrs["tvg-id"].orEmpty()
                    )
                }
            } else {
                i++
            }
        }
        return channels
    }
}

/**
 * Xtream Codes API istemcisi. `player_api.php` uç noktasını kullanır
 * (standart Xtream paneli). Canlı kanallar için kategori + kanal listesi
 * ve doğrudan oynatılabilir akış URL'si üretir.
 */
class XtreamClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private fun apiBase(source: XtreamSource): String {
        var server = source.server.trim().trimEnd('/')
        if (!server.startsWith("http")) server = "http://$server"
        return server
    }

    private fun playerApi(source: XtreamSource): String =
        "${apiBase(source)}/player_api.php?username=${source.username}&password=${source.password}"

    private suspend fun getJson(url: String): JsonElement? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url)
                .header("User-Agent", "StalkerPlayer/1.0")
                .build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val text = r.body?.string().orEmpty()
                if (text.isBlank()) return@use null
                json.parseToJsonElement(text)
            }
        }.getOrNull()
    }

    /** Kullanıcı adı/şifre geçerli mi? (user_info + server_info döner.) */
    suspend fun validate(source: XtreamSource): Boolean {
        val el = getJson(playerApi(source)) ?: return false
        val obj = el as? JsonObject ?: return false
        return obj["user_info"] != null && obj["server_info"] != null
    }

    /** Canlı TV kategorileri. */
    suspend fun liveCategories(source: XtreamSource): List<Genre> {
        val el = getJson("${playerApi(source)}&action=get_live_categories") ?: return emptyList()
        return (el as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { o ->
            val id = (o["category_id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                ?: return@mapNotNull null
            Genre(
                id = id,
                title = (o["category_name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            )
        }
    }

    /** Canlı kanallar (sayfalı). `page=-1` tümünü tek istekte döndürür (çoğu panelde çalışır). */
    suspend fun liveStreams(source: XtreamSource, page: Int = -1): List<Channel> {
        val url = "${playerApi(source)}&action=get_live_streams" +
            if (page > 0) "&page=$page" else ""
        val el = getJson(url) ?: return emptyList()
        val raw = when (el) {
            is JsonArray -> el
            is JsonObject -> el["available_channels"] as? JsonArray ?: return emptyList()
            else -> return emptyList()
        }
        return raw.mapNotNull { it as? JsonObject }.mapNotNull { o ->
            val id = (o["stream_id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                ?: return@mapNotNull null
            Channel(
                id = id,
                name = (o["name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                number = (o["num"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0,
                logo = (o["stream_icon"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                cmd = streamUrl(source, id),
                tvGenreId = (o["category_id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0,
                tvGenreTitle = (o["category_name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            )
        }
    }

    /** Canlı kanal için doğrudan oynatılabilir akış URL'si. */
    fun streamUrl(source: XtreamSource, streamId: Long): String {
        val base = apiBase(source)
        return "$base/${source.username}/${source.password}/$streamId.m3u8"
    }

    companion object {
        const val LIVE_CATEGORY_ALL = 0L
    }
}
