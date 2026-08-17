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
    val SERIES_KEYWORDS = listOf(
        "dizi", "diziler", "series", "serien", "seriale", "serial", "tv show", "tv shows", "tv series"
    )
}

/**
 * M3U listesi ayrıştırıcısı. Standart `#EXTM3U` / `#EXTINF` biçimini okur:
 * `#EXTINF:-1 tvg-id="..." tvg-logo="..." group-title="...",Kanal Adı` + URL satırı.
 */
object M3uParser {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
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

    /** Bir M3U girdisinin türü: canlı kanal, film veya dizi. */
    private enum class M3uEntryType { LIVE, MOVIE, SERIES }

    /** Film/VOD dosya uzantıları: böyle bir URL'ye sahip girdi canlı kanal değildir. */
    private val VOD_FILE_EXTENSIONS = listOf(
        ".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".webm",
        ".m4v", ".mpg", ".mpeg", ".3gp", ".vob", ".divx", ".ogv"
    )

    /** Film/VOD grup anahtar kelimeleri: grup adı bunlardan birini içeriyorsa film sayılır. */
    private val MOVIE_KEYWORDS = listOf(
        "film", "filmler", "movie", "movies", "sinema", "cinema", "kino", "vod", "pelicula", "filme"
    )

    /**
     * VOD isim sinyalleri: parantez içi yıl "(1994)", " - 1994", ".1987" gibi
     * biçimler. Canlı kanal adlarında ("FIFA World Cup 2022" gibi) yıl genelde
     * düz boşlukla yazılır ve bu desene takılmaz.
     */
    private val YEAR_IN_NAME = Regex("""\((19\d\d|20\d\d)\)?|[-–.]\s*(19\d\d|20\d\d)\s*$""")

    /** Dizi bölüm sinyali: "S01E05", "S1 E5" vb. */
    private val EPISODE_IN_NAME = Regex("""\bS\d{1,2}\s*E\d{1,3}\b""", RegexOption.IGNORE_CASE)

    /**
     * Tür tabanlı VOD grupları (yıl sinyali olmayan film/dizi grupları — ör:
     * "DE ✪ DRAMA", "DE ✪ KOMODIE"). Canlı kanal paketlerinde (ülke/spor/radyo)
     * bu kelimeler nadiren bulunur, bu yüzden güvenle VOD sayılabilir.
     */
    private val VOD_GROUP_KEYWORDS = listOf(
        "drama", "komödie", "komedie", "comedy", "thriller", "krimi", "crime", "horror",
        "korku", "sci-fi", "scifi", "science fiction", "fantasy", "abenteuer", "adventure",
        "anime", "cartoon", "belgesel", "dokumentation", "documentary", "doku", "kungfu",
        "liebesfilm", "romantik", "biopic", "netflix", "disney", "hbo", "imdb",
        "amazon prime", "apple tv"
    )

    /** VOD grubunun dizi mi film mi olduğunu belirleyen anahtarlar. */
    private val VOD_SERIES_GROUP_KEYWORDS = listOf(
        "serie", "dizi", "anime", "netflix", "disney", "hbo", "amazon", "cartoon",
        "animation", "animasyon"
    )

    /**
     * Girdiyi canlı/film/dizi olarak sınıflandırır. Öncelik: açık `tvg-type`
     * özniteliği, ardından isimdeki dizi bölümü/yıl desenleri, sonra URL dosya
     * uzantısı ve grup anahtar kelimeleri. İsim sinyalleri önemlidir: token bazlı
     * URL kullanan sağlayıcılarda (dosya uzantısı yok) film/dizi girdileri isimden
     * ayırt edilir — aksi halde hepsi canlı TV'ye düşerdi.
     */
    private fun classifyEntry(attrs: Map<String, String>, url: String, name: String = ""): M3uEntryType {
        when (attrs["tvg-type"]?.lowercase()?.trim().orEmpty()) {
            "vod", "movie", "film", "movies", "movie_live" -> return M3uEntryType.MOVIE
            "series", "tvshow", "show", "dizi", "serial", "serie", "serien" -> return M3uEntryType.SERIES
            "live", "radio", "tv", "channel", "iptv" -> return M3uEntryType.LIVE

        }
        val group = attrs["group-title"].orEmpty().lowercase()
        val u = url.lowercase()
        // Dizi bölümü deseni ("S01E05") → kesin dizi.
        if (EPISODE_IN_NAME.containsMatchIn(name)) return M3uEntryType.SERIES
        // URL bir VOD dosya uzantısıyla bitiyorsa kesin olarak canlı değildir.
        val fileLike = VOD_FILE_EXTENSIONS.any { u.endsWith(it) }
        // İsimde yıl sinyali ("(1994)", " - 1994") → VOD (film/dizi).
        val hasYear = YEAR_IN_NAME.containsMatchIn(name)
        if (fileLike || hasYear) {
            return if (ExternalVod.SERIES_KEYWORDS.any { group.contains(it) }) M3uEntryType.SERIES
            else M3uEntryType.MOVIE
        }
        // Uzantı/yıl sinyali yoksa grup adı anahtar kelimelerine bakılır —
        // film/dizi içeriği canlı TV'ye değil Filmler/Diziler kataloğuna düşsün.
        if (ExternalVod.SERIES_KEYWORDS.any { group.contains(it) }) return M3uEntryType.SERIES
        if (MOVIE_KEYWORDS.any { group.contains(it) }) return M3uEntryType.MOVIE
        // Tür tabanlı VOD grupları (yıl sinyali olmayan film/dizi grupları).
        if (VOD_GROUP_KEYWORDS.any { group.contains(it) }) {
            return if (VOD_SERIES_GROUP_KEYWORDS.any { group.contains(it) }) M3uEntryType.SERIES
            else M3uEntryType.MOVIE
        }
        return M3uEntryType.LIVE
    }

    private data class ParsedEntry(
        val name: String,
        val url: String,
        val logo: String,
        val group: String,
        val xmltvId: String,
        val type: M3uEntryType,
        val attrs: Map<String, String> = emptyMap()
    )

    /**
     * #EXTINF bloklarını tür etiketiyle birlikte çözer. Yüzlerce MB'lık
     * listelerde `split("\n")` 400k+ String üretip bellek taşmasına yol
     * açabiliyordu — bunun yerine satır satır (lazy) okunur.
     */
    private fun parseAll(text: String): List<ParsedEntry> {
        val out = mutableListOf<ParsedEntry>()
        val lines = text.lineSequence()
        var pending: ParsedEntry? = null
        for (line in lines) {
            val trimmed = line.trim()
            if (pending != null) {
                // #EXTINF'ten sonra gelen boş olmayan, # ile başlamayan satır URL'dir.
                if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                    if (pending.url.isEmpty()) pending = pending.copy(url = trimmed)
                } else if (trimmed.startsWith("#EXTINF")) {
                    // Ardışık #EXTINF (URL'siz girdi): öncekini kapat, yenisini başlat.
                    finishEntry(out, pending)
                    pending = parseExtinf(trimmed)
                }
            } else if (trimmed.startsWith("#EXTINF")) {
                pending = parseExtinf(trimmed)
            }
            // URL alındıysa girdiyi kapat (sonraki #EXTINF'te de kapatılır).
            if (pending != null && pending.url.isNotEmpty()) {
                finishEntry(out, pending)
                pending = null
            }
        }
        if (pending != null) finishEntry(out, pending)
        return out
    }

    /** Bir #EXTINF satırını öznitelikler + başlık ile ayrıştırır. */
    private fun parseExtinf(line: String): ParsedEntry {
        val attrs = parseAttributes(line)
        val name = line.substringAfterLast(",", "").trim()
            .ifBlank { attrs["tvg-name"].orEmpty() }
        return ParsedEntry(
            name = name,
            url = "",
            logo = attrs["tvg-logo"].orEmpty(),
            group = attrs["group-title"].orEmpty(),
            xmltvId = attrs["tvg-id"].orEmpty(),
            type = classifyEntry(attrs, "", name),
            attrs = attrs
        )
    }

    private fun finishEntry(out: MutableList<ParsedEntry>, e: ParsedEntry) {
        if (e.url.isNotBlank() && e.name.isNotBlank()) {
            // URL geldiğine göre sınıflandırmayı URL + isim ile birlikte yeniden
            // yap (dosya uzantısı ve isimdeki yıl/bölüm desenleri URL'ye bağlı
            // olabilir). attrs artık gerekmiyor — 400k+ girdide bellekten düşür.
            out += e.copy(type = classifyEntry(e.attrs, e.url, e.name), attrs = emptyMap())
        }
    }

    /** Yalnızca canlı kanalları döndürür (film/dizi girdileri ayrılır). */
    fun parse(text: String, sourceId: String): List<Channel> {
        return parseAll(text)
            .filter { it.type == M3uEntryType.LIVE }
            .mapIndexed { index, e ->
                Channel(
                    id = (sourceId + "|" + e.url).hashCode().toLong().and(0xFFFFFFFFL).let { if (it == 0L) 1L else it },
                    name = e.name,
                    number = index + 1,
                    logo = e.logo,
                    cmd = e.url,
                    tvGenreTitle = e.group,
                    xmltvId = e.xmltvId
                )
            }
    }

    /**
     * M3U içeriğinden film/dizi kataloğu üretir (canlı kanallar dahil edilmez).
     * group-title'lar kategori olur; dizi olarak sınıflananlar dizi, diğerleri
     * film sayılır (her öğe tek dosyalıdır — bölüm bilgisi standart M3U'da yoktur).
     * Dönüş: (kategoriler, öğeler).
     */
    fun parseVod(text: String, sourceId: String): Pair<List<Genre>, List<VodItem>> {
        val entries = parseAll(text).filter { it.type != M3uEntryType.LIVE }
        if (entries.isEmpty()) return listOf(Genre(0, "Tümü")) to emptyList()
        val groups = entries.map { it.group }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val groupIds = groups.mapIndexed { i, g -> g to (i + 1).toLong() }.toMap()
        val items = entries.map { e ->
            VodItem(
                id = (sourceId + "|" + e.url).hashCode().toLong().and(0xFFFFFFFFL).let { if (it == 0L) 1L else it },
                categoryId = groupIds[e.group] ?: 0L,
                name = e.name,
                originalName = e.name,
                poster = e.logo,
                description = "",
                year = "",
                cmd = e.url,
                isSeries = e.type == M3uEntryType.SERIES
            )
        }
        val genres = listOf(Genre(0, "Tümü")) +
            groups.map { g -> Genre(groupIds.getValue(g), g) }
        return genres to items
    }
}

/**
 * Xtream API isteği ağ/HTTP hatası nedeniyle başarısız olduğunda fırlatılır
 * (VOD senkronu bunu yakalayıp "Senkronizasyon hatası" olarak gösterir).
 */
class XtreamApiException(message: String) : Exception(message)

/**
 * Xtream Codes API istemcisi. `player_api.php` uç noktasını kullanır
 * (standart Xtream paneli). Canlı kanallar için kategori + kanal listesi
 * ve doğrudan oynatılabilir akış URL'si üretir.
 */
class XtreamClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
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

    /** Panel ayraç satırları ("----- ITALIA -----", "END OF LIST") gerçek içerik değildir. */
    private fun isSeparatorRow(name: String): Boolean {
        val t = name.trim()
        if (t.equals("END OF LIST", ignoreCase = true)) return true
        return t.startsWith("-----")
    }

    private fun parseCategories(el: JsonElement): List<Genre> =
        (el as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { o ->
            val id = (o["category_id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                ?: return@mapNotNull null
            Genre(
                id = id,
                title = (o["category_name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            )
        }

    /** Başarısızlıkta [XtreamApiException] fırlatan sürüm (VOD senkronu için). */
    private suspend fun getJsonOrThrow(url: String): JsonElement = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        repeat(2) {
            try {
                val req = Request.Builder().url(url)
                    .header("User-Agent", "StalkerPlayer/1.0")
                    .build()
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw XtreamApiException("HTTP ${r.code} (${r.request.url})")
                    val text = r.body?.string().orEmpty()
                    if (text.isBlank()) throw XtreamApiException("empty response: ${r.request.url}")
                    return@withContext json.parseToJsonElement(text)
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: XtreamApiException("request failed: $url")
    }

    private suspend fun fetchListOrThrow(url: String): List<JsonObject> {
        val el = getJsonOrThrow(url)
        return when (el) {
            is JsonArray -> el.mapNotNull { it as? JsonObject }
            is JsonObject -> (el["available_channels"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }.orEmpty()
            else -> emptyList()
        }
    }

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
        return parseCategories(el)
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
            val name = (o["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (isSeparatorRow(name)) return@mapNotNull null
            Channel(
                id = id,
                name = name,
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
        return parseCategories(el)
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

    /**
     * VOD senkronu için film + dizi kataloğunu tek akışta çeker. Ağ/HTTP
     * hatasında (geçici 401/taşma dahil) bir kez yeniden dener ve hâlâ
     * başarısızsa [XtreamApiException] fırlatır — böylece gerçek arıza
     * "Senkronizasyon hatası" olarak görünür, boş panel yanıtıyla karışmaz.
     */
    suspend fun fetchVodCatalog(source: XtreamSource): Pair<List<Genre>, List<VodItem>> {
        val vcats = parseCategories(
            getJsonOrThrow("${playerApi(source)}&action=get_vod_categories")
        )
        val scats = parseCategories(
            getJsonOrThrow("${playerApi(source)}&action=get_series_categories")
        )
        val vods = pageAllOrThrow(source, "get_vod_streams") { o -> vodItemFrom(source, o) }
        val series = pageAllOrThrow(source, "get_series") { o -> seriesItemFrom(o) }
        val genres = listOf(Genre(0, "Tümü")) +
            vcats +
            scats.map { it.copy(id = ExternalVod.seriesCatId(it.id)) }
        return genres to (vods + series)
    }

    private suspend fun pageAllOrThrow(
        source: XtreamSource,
        action: String,
        categoryId: Long = 0,
        mapItem: (JsonObject) -> VodItem?
    ): List<VodItem> {
        val catParam = if (categoryId > 0) "&category_id=$categoryId" else ""
        val base = "${playerApi(source)}&action=$action$catParam"
        val first = fetchListOrThrow(base)
        if (first.isNotEmpty()) return mapItems(first, mapItem)

        // Sayfalı fallback (bazı paneller page'siz boş döner).
        val out = mutableListOf<VodItem>()
        val seen = HashSet<Long>()
        var page = 1
        var dupStreak = 0
        while (page <= 200) {
            val items = mapItems(fetchListOrThrow("$base&page=$page"), mapItem)
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
        if (isSeparatorRow(name)) return null
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
        if (isSeparatorRow(name)) return null
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
        // Bazı paneller sezon içi bölümleri üst seviye "episodes" sözlüğünde döner
        // ({ sezonNo: [bölüm...] }); standart paneller seasons[].episodes kullanır.
        val episodesBySeason: Map<String, JsonArray> = (obj["episodes"] as? JsonObject).orEmpty()
            .mapNotNull { (k, v) -> (v as? JsonArray)?.let { k to it } }
            .toMap()
        return seasonsArr.mapNotNull { it as? JsonObject }.mapNotNull { s ->
            val num = (s["season_number"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                ?: return@mapNotNull null
            val inline = (s["episodes"] as? JsonArray).orEmpty()
            val eps = if (inline.isNotEmpty()) inline
            else episodesBySeason[num.toString()] ?: JsonArray(emptyList())
            val parsed = eps.mapNotNull { it as? JsonObject }.mapNotNull { e ->
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
                episodes = parsed
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
