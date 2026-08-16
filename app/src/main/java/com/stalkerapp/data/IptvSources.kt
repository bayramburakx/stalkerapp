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
 * Stalker dışı (M3U / Xtream) kaynakların VOD kataloğu için ortak id düzeni.
 * Canlı kanal, film ve dizi id'leri aynı katalogda çakışmasın diye her türe
 * ayrı bir taban ayrılır; [realId] gerçek (portal) id'yi geri verir.
 */
object ExternalVod {
    /** Xtream film (VOD) katalog id tabanı. */
    const val XTREAM_VOD_BASE = 50_000_000_000L
    /** Xtream dizi katalog id tabanı (film ve canlı kanal id'lerinden ayrı). */
    const val XTREAM_SERIES_BASE = 60_000_000_000L
    /** Xtream dizi kategori id tabanı (Stalker dizi kategorileriyle aynı düzen). */
    const val SERIES_CAT_BASE = 100_000L

    fun isXtreamSeries(id: Long): Boolean = id >= XTREAM_SERIES_BASE
    fun isXtreamVod(id: Long): Boolean = id in XTREAM_VOD_BASE until XTREAM_SERIES_BASE
    fun isExternal(id: Long): Boolean = isXtreamVod(id) || isXtreamSeries(id)

    fun realId(id: Long): Long = when {
        id >= XTREAM_SERIES_BASE -> id - XTREAM_SERIES_BASE
        id >= XTREAM_VOD_BASE -> id - XTREAM_VOD_BASE
        else -> id
    }

    /** Dizi kategori id'sini ad alanına taşır (film kategorileriyle çakışmasın). */
    fun seriesCatId(realCatId: Long): Long = SERIES_CAT_BASE + realCatId
    fun realSeriesCatId(catId: Long): Long =
        if (catId >= SERIES_CAT_BASE) catId - SERIES_CAT_BASE else catId

    /** Dizi olarak sayılacak grup/kategori başlığı anahtar kelimeleri. */
    val SERIES_KEYWORDS = listOf("dizi", "series", "serial", "diziler", "tv show", "tv shows")
}

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

    /**
     * M3U içeriğinden film/dizi kataloğu üretir. group-title'lar kategori olur;
     * "dizi/series" içeren gruplar dizi, diğerleri film sayılır (her öğe tek
     * dosyalıdır — bölüm bilgisi standart M3U'da yoktur).
     * Dönüş: (kategoriler, öğeler).
     */
    fun parseVod(text: String, sourceId: String): Pair<List<Genre>, List<VodItem>> {
        val channels = parse(text, sourceId)
        if (channels.isEmpty()) return listOf(Genre(0, "Tümü")) to emptyList()
        val groups = channels.map { it.tvGenreTitle }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val groupIds = groups.mapIndexed { i, g -> g to (i + 1).toLong() }.toMap()
        val items = channels.map { ch ->
            val isSeries = ch.tvGenreTitle.let { t ->
                t.isNotBlank() && ExternalVod.SERIES_KEYWORDS.any { t.contains(it, ignoreCase = true) }
            }
            VodItem(
                id = ch.id,
                categoryId = groupIds[ch.tvGenreTitle] ?: 0L,
                name = ch.name,
                originalName = ch.name,
                poster = ch.logo,
                description = "",
                year = "",
                cmd = ch.cmd,
                isSeries = isSeries
            )
        }
        val genres = listOf(Genre(0, "Tümü")) +
            groups.map { g -> Genre(groupIds.getValue(g), g) }
        return genres to items
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

    /**
     * Canlı kanal için doğrudan oynatılabilir akış URL'si. Standart Xtream
     * biçimi `/live/` önekini kullanır (panellerin büyük çoğunluğu bunu kabul
     * eder; öneksiz biçim bazı panellerde akışı reddediyordu).
     */
    fun streamUrl(source: XtreamSource, streamId: Long): String {
        val base = apiBase(source)
        return "$base/live/${source.username}/${source.password}/$streamId.m3u8"
    }

    // ---------- VOD / Diziler ----------

    /** Film (VOD) kategorileri. */
    suspend fun vodCategories(source: XtreamSource): List<Genre> =
        getCategories(source, "get_vod_categories")

    /** Dizi kategorileri. */
    suspend fun seriesCategories(source: XtreamSource): List<Genre> =
        getCategories(source, "get_series_categories")

    private suspend fun getCategories(source: XtreamSource, action: String): List<Genre> {
        val el = getJson("${playerApi(source)}&action=$action") ?: return emptyList()
        return (el as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { o ->
            val id = (o["category_id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                ?: return@mapNotNull null
            Genre(
                id = id,
                title = (o["category_name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            )
        }
    }

    /**
     * Tüm filmleri çeker ([categoryId] > 0 ise o kategori). Çoğu panel `page`
     * parametresi olmadan tümünü tek yanıtta döndürür; bazıları sayfalıdır —
     * boş gelirse sayfa sayfa denenir (yinelenen sayfa korumasıyla).
     */
    suspend fun vodStreams(source: XtreamSource, categoryId: Long = 0): List<VodItem> =
        pageAll(source, "get_vod_streams", categoryId) { o -> vodItemFrom(source, o) }

    /** Tüm dizileri çeker ([categoryId] > 0 ise o kategori). */
    suspend fun seriesStreams(source: XtreamSource, categoryId: Long = 0): List<VodItem> =
        pageAll(source, "get_series", categoryId) { o -> seriesItemFrom(o) }

    private suspend fun pageAll(
        source: XtreamSource,
        action: String,
        categoryId: Long,
        mapItem: (JsonObject) -> VodItem?
    ): List<VodItem> {
        val catParam = if (categoryId > 0) "&category_id=$categoryId" else ""
        val base = "${playerApi(source)}&action=$action$catParam"
        val first = fetchList(base)
        if (first.isNotEmpty()) return mapItems(first, mapItem)

        // Sayfalı fallback (bazı paneller page'siz boş döner).
        val out = mutableListOf<VodItem>()
        val seen = HashSet<Long>()
        var page = 1
        var dupStreak = 0
        while (page <= 200) {
            val items = mapItems(fetchList("$base&page=$page"), mapItem)
            if (items.isEmpty()) break
            var added = 0
            items.forEach { if (seen.add(it.id)) { out += it; added++ } }
            if (added == 0) {
                if (++dupStreak >= 2) break
                page++
                continue
            }
            dupStreak = 0
            page++
        }
        return out
    }

    private fun mapItems(objs: List<JsonObject>, mapItem: (JsonObject) -> VodItem?): List<VodItem> {
        val out = mutableListOf<VodItem>()
        val seen = HashSet<Long>()
        objs.forEach { o ->
            val item = mapItem(o) ?: return@forEach
            if (seen.add(item.id)) out += item
        }
        return out
    }

    private suspend fun fetchList(url: String): List<JsonObject> {
        val el = getJson(url) ?: return emptyList()
        return when (el) {
            is JsonArray -> el.mapNotNull { it as? JsonObject }
            is JsonObject -> (el["available_channels"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }.orEmpty()
            else -> emptyList()
        }
    }

    /** get_vod_streams öğesini [VodItem]'e çevirir (id ad alanına taşınır). */
    private fun vodItemFrom(source: XtreamSource, o: JsonObject): VodItem? {
        val streamId = (o["stream_id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
            ?: return null
        val ext = (o["container_extension"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val name = (o["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return VodItem(
            id = ExternalVod.XTREAM_VOD_BASE + streamId,
            categoryId = (o["category_id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0,
            name = name,
            originalName = name,
            poster = (o["stream_icon"] as? JsonPrimitive)?.contentOrNull
                ?: (o["backdrop_path"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            description = (o["plot"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            year = (o["release_date"] as? JsonPrimitive)?.contentOrNull.orEmpty().take(4),
            director = (o["director"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            rating = (o["rating"] as? JsonPrimitive)?.contentOrNull
                ?: (o["rating_5based"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            genres = (o["genre"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            actors = (o["cast"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            tmdbId = (o["tmdb_id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0,
            isSeries = false,
            cmd = vodPlayUrl(source, streamId, ext)
        )
    }

    /** get_series öğesini [VodItem]'e çevirir (id + kategori ad alanına taşınır). */
    private fun seriesItemFrom(o: JsonObject): VodItem? {
        val seriesId = (o["series_id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
            ?: return null
        val name = (o["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return VodItem(
            id = ExternalVod.XTREAM_SERIES_BASE + seriesId,
            categoryId = ExternalVod.seriesCatId(
                (o["category_id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0
            ),
            name = name,
            originalName = name,
            poster = (o["cover"] as? JsonPrimitive)?.contentOrNull
                ?: (o["backdrop_path"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            description = (o["plot"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            year = (o["first_air_date"] as? JsonPrimitive)?.contentOrNull.orEmpty().take(4),
            director = (o["director"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            rating = (o["rating"] as? JsonPrimitive)?.contentOrNull
                ?: (o["rating_5based"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            genres = (o["genre"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            actors = (o["cast"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            tmdbId = (o["tmdb_id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0,
            isSeries = true
        )
    }

    /**
     * Bir dizinin sezon + bölüm yapısını `get_series_info`'dan döner.
     * Boşsa dizi boş/erişilemez demektir.
     */
    suspend fun seriesInfo(source: XtreamSource, seriesId: Long): List<XtreamSeasonInfo> {
        val el = getJson("${playerApi(source)}&action=get_series_info&series_id=$seriesId")
            ?: return emptyList()
        val obj = el as? JsonObject ?: return emptyList()
        val seasonsArr = obj["seasons"] as? JsonArray ?: return emptyList()
        return seasonsArr.mapNotNull { it as? JsonObject }.mapNotNull { s ->
            val num = (s["season_number"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                ?: return@mapNotNull null
            val eps = (s["episodes"] as? JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }
                .mapNotNull { e ->
                    val eid = (e["id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                        ?: return@mapNotNull null
                    val info = e["info"] as? JsonObject
                    XtreamEpisodeInfo(
                        id = eid,
                        number = (e["episode_num"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0,
                        name = (e["title"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                        thumb = info?.get("movie_image")?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                        container = (e["container_extension"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    )
                }
            XtreamSeasonInfo(
                number = num,
                name = (s["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    .ifBlank { "Sezon $num" },
                episodes = eps
            )
        }.sortedBy { it.number }
    }

    /** Film için doğrudan oynatılabilir URL. */
    fun vodPlayUrl(source: XtreamSource, streamId: Long, container: String = ""): String {
        val e = container.ifBlank { "mkv" }.removePrefix(".")
        val base = apiBase(source)
        return "$base/movie/${source.username}/${source.password}/$streamId.$e"
    }

    /** Dizi bölümü için doğrudan oynatılabilir URL. */
    fun episodePlayUrl(
        source: XtreamSource,
        seriesId: Long,
        seasonNum: Long,
        episodeNum: Int,
        container: String = ""
    ): String {
        val e = container.ifBlank { "mkv" }.removePrefix(".")
        val base = apiBase(source)
        return "$base/series/${source.username}/${source.password}/$seriesId/$seasonNum/$episodeNum.$e"
    }

    companion object {
        const val LIVE_CATEGORY_ALL = 0L
    }
}

/** Xtream dizisinin bir sezonu (bölümleriyle birlikte). */
data class XtreamSeasonInfo(
    val number: Int = 0,
    val name: String = "",
    val episodes: List<XtreamEpisodeInfo> = emptyList()
)

/** Xtream dizisinin bir bölümü. */
data class XtreamEpisodeInfo(
    val id: Long = 0,
    val number: Int = 0,
    val name: String = "",
    val thumb: String = "",
    val container: String = ""
)
