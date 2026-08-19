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
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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
        "dizi", "diziler", "series", "serien", "seriale", "serial", "serials", "tv show", "tv shows", "tv series",
        "sezon", "season", "netflix", "exxen", "blutv", "gain", "disney", "prime video", "apple tv", "hbo", "tod", "tabii", "anime"
    )
}

/**
 * M3U listesi ayrıştırıcısı. Standart `#EXTM3U` / `#EXTINF` biçimini okur:
 * `#EXTINF:-1 tvg-id="..." tvg-logo="..." group-title="...",Kanal Adı` + URL satırı.
 */
object M3uParser {

    private val defaultHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // DoH/SOCKS ayarları aktifse NetworkConfig istemcisi kullanılır.
    private val http: OkHttpClient
        get() {
            val st = Store.activeStore?.settings() ?: Settings()
            return if (st.dohEnabled || st.socksProxy.isNotBlank()) {
                com.stalkerapp.util.NetworkConfig.buildClientFor(st)
            } else defaultHttp
        }

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

    private val ATTR_REGEX = Regex("""([a-zA-Z0-9_-]+)="([^"]*)"""")

    /** #EXTINF satırındaki tvg-* özniteliklerini okur. */
    private fun parseAttributes(extinf: String): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        ATTR_REGEX.findAll(extinf).forEach { m ->
            attrs[m.groupValues[1]] = m.groupValues[2]
        }
        return attrs
    }

    /** Bir M3U girdisinin türü: canlı kanal, film veya dizi. */
    private enum class M3uEntryType { LIVE, MOVIE, SERIES }

    /**
     * VOD dosya uzantıları: URL bu uzantılardan biriyle bitiyorsa içerik
     * kesin olarak canlı yayın değildir (film/dizi).
     */
    private val VOD_FILE_EXTENSIONS = listOf(
        ".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".webm",
        ".m4v", ".mpg", ".mpeg", ".3gp", ".vob", ".divx", ".ogv"
    )

    private val LIVE_GROUP_KEYWORDS = listOf(
        "canlı", "live", "haber", "spor", "sport", "sports", "ulusal", "yerel", "belgesel tv",
        "müzik", "music", "radio", "radyo", "yayın", "yayini", "tv", "channels", "kanallar"
    )

    /** Film/VOD grup anahtar kelimeleri: grup adı bunlardan birini içeriyorsa film sayılır. */
    private val MOVIE_KEYWORDS = listOf(
        "film", "filmler", "movie", "movies", "sinema", "cinema", "kino", "vod", "pelicula", "filme",
        "video club", "filmizle", "filmler hd", "turk film", "türk film", "on demand", "video on demand",
        "action", "aksiyon", "comedy", "komedi", "horror", "korku", "drama", "gerilim", "thriller",
        "bilim kurgu", "sci-fi", "romantik", "box office", "vizyon"
    )

    private val YEAR_IN_NAME = Regex("""\((19\d\d|20\d\d)\)?|[-–.]\s*(19\d\d|20\d\d)\s*$""")
    private val EPISODE_IN_NAME = Regex("""\bS\d{1,2}\s*E\d{1,3}\b""", RegexOption.IGNORE_CASE)

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
        "animation", "animasyon", "exxen", "blutv", "gain"
    )

    /**
     * Girdiyi canlı/film/dizi olarak sınıflandırır.
     */
    private fun classifyEntry(attrs: Map<String, String>, url: String, name: String = ""): M3uEntryType {
        val typeAttr = attrs["tvg-type"]?.lowercase()?.trim().orEmpty()
            .ifBlank { attrs["type"]?.lowercase()?.trim().orEmpty() }
        when (typeAttr) {
            "vod", "movie", "film", "movies", "movie_live", "video" -> return M3uEntryType.MOVIE
            "series", "tvshow", "show", "dizi", "serial", "serie", "serien", "tv series" -> return M3uEntryType.SERIES
            "live", "radio", "tv", "channel", "iptv", "catchup", "timeshift" -> return M3uEntryType.LIVE
        }

        val u = url.lowercase()
        // 1) URL path kontrolü:
        if (u.contains("/live/") || u.contains("/stream/live") || u.contains("type=live")) {
            return M3uEntryType.LIVE
        }
        if (u.contains("type=series") || u.contains("action=series") || u.contains("/series/") || u.contains("/serie/") || u.contains("/dizi/") || u.contains("/serials/") || u.contains("/serial/")) {
            return M3uEntryType.SERIES
        }
        if (u.contains("type=movie") || u.contains("type=vod") || u.contains("action=vod") || u.contains("/movie/") || u.contains("/movies/") || u.contains("/vod/") || u.contains("/film/") || u.contains("/filme/") || u.contains("/films/") || u.contains("/cinema/")) {
            return M3uEntryType.MOVIE
        }

        val group = attrs["group-title"].orEmpty().lowercase()
            .ifBlank { attrs["group"]?.lowercase().orEmpty() }
        val nameLower = name.lowercase()

        // 2) Dizi bölüm sinyali:
        val hasEpisode = (nameLower.contains('s') || nameLower.contains("sezon") || nameLower.contains("season") || nameLower.contains('x')) &&
            EPISODE_IN_NAME.containsMatchIn(name)
        if (hasEpisode) return M3uEntryType.SERIES

        // 3) Canlı kanal grup kontrolü:
        val isExplicitLiveGroup = LIVE_GROUP_KEYWORDS.any { group.contains(it) } &&
            !ExternalVod.SERIES_KEYWORDS.any { group.contains(it) } &&
            !MOVIE_KEYWORDS.any { group.contains(it) }
        if (isExplicitLiveGroup) return M3uEntryType.LIVE

        // 4) VOD dosya uzantısı / yıl kontrolü:
        val fileLike = VOD_FILE_EXTENSIONS.any { u.endsWith(it) }
        val hasYear = (name.contains('(') || name.contains('-') || name.contains('.')) && YEAR_IN_NAME.containsMatchIn(name)
        if (fileLike || hasYear) {
            return if (ExternalVod.SERIES_KEYWORDS.any { group.contains(it) }) M3uEntryType.SERIES
            else M3uEntryType.MOVIE
        }

        // 5) Canlı yayın akışları (.m3u8, .ts, vb.): grup adı film/dizi içerse dahi
        // dosya uzantısı/yıl/bölüm deseni yoksa 7/24 lineer televizyon kanalıdır (Canlı TV).
        if (u.endsWith(".m3u8") || u.endsWith(".ts")) {
            return M3uEntryType.LIVE
        }

        // 6) Grup adı anahtarları:
        if (ExternalVod.SERIES_KEYWORDS.any { group.contains(it) }) return M3uEntryType.SERIES
        if (MOVIE_KEYWORDS.any { group.contains(it) }) return M3uEntryType.MOVIE
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
     * #EXTINF bloklarını tür etiketiyle birlikte satır satır (lazy) çözer.
     */
    private fun forEachEntry(lines: Sequence<String>, onEntry: (ParsedEntry) -> Unit) {
        var pending: ParsedEntry? = null
        var currentExtGrp = ""
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#EXTGRP:") -> {
                    currentExtGrp = trimmed.removePrefix("#EXTGRP:").trim()
                }
                pending != null -> {
                    if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                        if (pending.url.isEmpty()) pending = pending.copy(url = trimmed)
                    } else if (trimmed.startsWith("#EXTINF")) {
                        finishEntry(pending)?.let(onEntry)
                        pending = parseExtinf(trimmed, currentExtGrp)
                    }
                }
                trimmed.startsWith("#EXTINF") -> {
                    pending = parseExtinf(trimmed, currentExtGrp)
                }
            }
            if (pending != null && pending.url.isNotEmpty()) {
                finishEntry(pending)?.let(onEntry)
                pending = null
            }
        }
        if (pending != null) finishEntry(pending)?.let(onEntry)
    }

    /** Bir #EXTINF satırını öznitelikler + başlık ile ayrıştırır. */
    private fun parseExtinf(line: String, extGrp: String = ""): ParsedEntry {
        val attrs = parseAttributes(line)
        val name = line.substringAfterLast(",", "").trim()
            .ifBlank { attrs["tvg-name"]?.trim().orEmpty() }
            .ifBlank { attrs["name"]?.trim().orEmpty() }
        val group = attrs["group-title"]?.trim().orEmpty().ifBlank { extGrp }
        return ParsedEntry(
            name = name,
            url = "",
            logo = attrs["tvg-logo"].orEmpty(),
            group = group,
            xmltvId = attrs["tvg-id"].orEmpty(),
            type = M3uEntryType.LIVE,
            attrs = attrs
        )
    }

    /** Girdiyi kapatır; URL + isim doluysa sınıflandırılmış haliyle döner (yoksa null). */
    private fun finishEntry(e: ParsedEntry): ParsedEntry? {
        if (e.url.isNotBlank()) {
            val validName = e.name.ifBlank {
                e.attrs["tvg-name"]?.trim().orEmpty()
                    .ifBlank { e.attrs["name"]?.trim().orEmpty() }
                    .ifBlank {
                        val fn = e.url.substringAfterLast('/').substringBefore('?')
                        if (fn.isNotBlank()) fn.substringBeforeLast('.') else "Video"
                    }
            }
            // #EXTGRP veya parseExtinf'ten gelen grup adı attrs'ta olmayabilir —
            // sınıflandırmaya dahil et (aksi halde VOD grupları canlıya düşer).
            val attrsWithGroup = if (e.group.isNotBlank() && e.attrs["group-title"].isNullOrBlank()) {
                e.attrs + ("group-title" to e.group)
            } else e.attrs
            return e.copy(
                name = validName,
                type = classifyEntry(attrsWithGroup, e.url, validName),
                attrs = emptyMap()
            )
        }
        return null
    }

    private fun channelFrom(e: ParsedEntry, sourceId: String, number: Int): Channel = Channel(
        id = (sourceId + "|" + e.url).hashCode().toLong().and(0xFFFFFFFFL).let { if (it == 0L) 1L else it },
        name = e.name,
        number = number,
        logo = e.logo,
        cmd = e.url,
        tvGenreTitle = e.group,
        xmltvId = e.xmltvId
    )

    /** Yalnızca canlı kanalları döndürür (film/dizi girdileri ayrılır). */
    fun parse(text: String, sourceId: String): List<Channel> {
        val out = ArrayList<Channel>()
        var number = 1
        forEachEntry(text.lineSequence()) { e ->
            if (e.type == M3uEntryType.LIVE) {
                out.add(channelFrom(e, sourceId, number))
                number++
            }
        }
        return out
    }

    /** Aynı işi diskteki dosyadan yapar (büyük listelerde dev String'den kaçınır). */
    fun parseFile(file: File, sourceId: String): List<Channel> {
        val out = ArrayList<Channel>()
        var number = 1
        file.bufferedReader(Charsets.UTF_8).use { r ->
            forEachEntry(r.lineSequence()) { e ->
                if (e.type == M3uEntryType.LIVE) {
                    out.add(channelFrom(e, sourceId, number))
                    number++
                }
            }
        }
        return out
    }

    private val m3uSeriesSeasons: MutableMap<Long, List<Season>> = ConcurrentHashMap()
    private val m3uSeriesEpisodes: MutableMap<Long, Map<Long, List<Episode>>> = ConcurrentHashMap()

    fun getSeasons(vodId: Long): List<Season>? = m3uSeriesSeasons[vodId]
    fun getEpisodes(vodId: Long, seasonId: Long): List<Episode>? = m3uSeriesEpisodes[vodId]?.get(seasonId)
    fun clearCache() {
        m3uSeriesSeasons.clear()
        m3uSeriesEpisodes.clear()
    }

    private data class ParsedM3uEpisode(
        val seriesTitle: String,
        val seasonNum: Int,
        val epNum: Int,
        val epTitle: String
    )

    private val S_E_REGEX_1 = Regex("""^(.*?)\s*[-–._/]?\s*S(\d{1,2})\s*[.x_–-]?\s*E(\d{1,3})\s*[-–._:]?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val S_E_REGEX_2 = Regex("""^(.*?)\s*[-–._/]?\s*(\d{1,2})\.?\s*Sezon\s*[-–._/]?\s*(\d{1,3})\.?\s*Bölüm\s*[-–._:]?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val S_E_REGEX_3 = Regex("""^(.*?)\s*[-–._/]?\s*Season\s*(\d{1,2})\s*[-–._/]?\s*Episode\s*(\d{1,3})\s*[-–._:]?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val S_E_REGEX_4 = Regex("""^(.*?)\s*[-–._/]?\s*(\d{1,2})x(\d{1,3})\s*[-–._:]?\s*(.*)$""", RegexOption.IGNORE_CASE)

    private fun parseM3uEpisodeName(rawName: String): ParsedM3uEpisode {
        val trimmed = rawName.trim()
        val lower = trimmed.lowercase()
        if (!lower.contains('s') && !lower.contains("sezon") && !lower.contains("season") && !lower.contains('x')) {
            return ParsedM3uEpisode(trimmed, 1, 1, trimmed)
        }
        for (rgx in listOf(S_E_REGEX_1, S_E_REGEX_2, S_E_REGEX_3, S_E_REGEX_4)) {
            val m = rgx.matchEntire(trimmed)
            if (m != null) {
                val rawSeries = m.groupValues[1].trim().removeSuffix("-").removeSuffix(":").removeSuffix("–").trim()
                val s = m.groupValues[2].toIntOrNull() ?: 1
                val e = m.groupValues[3].toIntOrNull() ?: 1
                val title = m.groupValues[4].trim().removePrefix("-").removePrefix(":").removePrefix("–").trim()
                val finalSeries = if (rawSeries.isNotBlank()) rawSeries else trimmed
                val finalTitle = if (title.isNotBlank()) title else "$e. Bölüm"
                return ParsedM3uEpisode(finalSeries, s, e, finalTitle)
            }
        }
        return ParsedM3uEpisode(trimmed, 1, 1, trimmed)
    }

    /**
     * M3U içeriğinden film/dizi kataloğu üretir (canlı kanallar dahil edilmez).
     * Dizi bölümleri dizi adı altında sezon ve bölüm olarak gruplanır (tek kart).
     */
    fun parseVodFile(file: File, sourceId: String): Pair<List<Genre>, List<VodItem>> {
        val groupIds = LinkedHashMap<String, Long>()
        var nextGroupId = 1L
        val movies = ArrayList<VodItem>()

        data class EpAcc(
            val originalSeriesTitle: String,
            val gid: Long,
            val logo: String,
            val seasonNum: Int,
            val epNum: Int,
            val epTitle: String,
            val url: String
        )
        val seriesMap = LinkedHashMap<String, MutableList<EpAcc>>()

        file.bufferedReader(Charsets.UTF_8).use { reader ->
            forEachEntry(reader.lineSequence()) { e ->
                if (e.type == M3uEntryType.MOVIE) {
                    val groupName = e.group.ifBlank { "Filmler" }
                    val gid = groupIds.getOrPut(groupName) { nextGroupId++ }
                    val yearFound = YEAR_IN_NAME.find(e.name)?.groupValues?.getOrNull(1) ?: ""
                    movies.add(
                        VodItem(
                            id = (sourceId + "|movie|" + e.url).hashCode().toLong().and(0xFFFFFFFFL).let { if (it == 0L) 1L else it },
                            categoryId = gid,
                            name = e.name,
                            originalName = e.name,
                            poster = e.logo,
                            description = "",
                            year = yearFound,
                            cmd = e.url,
                            isSeries = false
                        )
                    )
                } else if (e.type == M3uEntryType.SERIES) {
                    val groupName = e.group.ifBlank { "Diziler" }
                    val gid = groupIds.getOrPut(groupName) { nextGroupId++ }
                    val epInfo = parseM3uEpisodeName(e.name)
                    val key = "$gid|${epInfo.seriesTitle.lowercase()}"
                    seriesMap.getOrPut(key) { mutableListOf() }.add(
                        EpAcc(
                            originalSeriesTitle = epInfo.seriesTitle,
                            gid = gid,
                            logo = e.logo,
                            seasonNum = epInfo.seasonNum,
                            epNum = epInfo.epNum,
                            epTitle = epInfo.epTitle,
                            url = e.url
                        )
                    )
                }
            }
        }

        val seriesVodItems = ArrayList<VodItem>()
        for ((_, epList) in seriesMap) {
            val first = epList.first()
            val seriesId = (sourceId + "|series|" + first.originalSeriesTitle.lowercase()).hashCode().toLong().and(0xFFFFFFFFL).let { if (it == 0L) 1L else it }
            val logo = epList.firstOrNull { it.logo.isNotBlank() }?.logo.orEmpty()
            val yearFound = YEAR_IN_NAME.find(first.originalSeriesTitle)?.groupValues?.getOrNull(1) ?: ""

            val seasonsGrouped = epList.groupBy { it.seasonNum }
            val seasons = seasonsGrouped.keys.sorted().map { sNum ->
                Season(
                    id = sNum.toLong(),
                    name = "$sNum. Sezon",
                    poster = logo
                )
            }
            val seasonEpisodes = seasonsGrouped.mapValues { (sNum, eps) ->
                eps.sortedBy { it.epNum }.map { ep ->
                    Episode(
                        id = (sourceId + "|ep|" + ep.url).hashCode().toLong().and(0xFFFFFFFFL).let { if (it == 0L) 1L else it },
                        name = ep.epTitle,
                        episodeNumber = ep.epNum,
                        cmd = ep.url,
                        thumb = ep.logo.ifBlank { logo }
                    )
                }
            }.mapKeys { it.key.toLong() }

            m3uSeriesSeasons[seriesId] = seasons
            m3uSeriesEpisodes[seriesId] = seasonEpisodes

            seriesVodItems.add(
                VodItem(
                    id = seriesId,
                    categoryId = first.gid,
                    name = first.originalSeriesTitle,
                    originalName = first.originalSeriesTitle,
                    poster = logo,
                    description = "",
                    year = yearFound,
                    cmd = epList.firstOrNull()?.url.orEmpty(),
                    isSeries = true
                )
            )
        }

        val allVod = movies + seriesVodItems
        val genres = listOf(Genre(0, "Tümü")) +
            groupIds.entries.map { Genre(it.value, it.key) }
        return genres to allVod
    }

    /** String tabanlı VOD kataloğu (küçük listeler / geriye dönük). */
    fun parseVod(text: String, sourceId: String): Pair<List<Genre>, List<VodItem>> {
        val groupIds = LinkedHashMap<String, Long>()
        var nextGroupId = 1L
        val movies = ArrayList<VodItem>()

        data class EpAcc(
            val originalSeriesTitle: String,
            val gid: Long,
            val logo: String,
            val seasonNum: Int,
            val epNum: Int,
            val epTitle: String,
            val url: String
        )
        val seriesMap = LinkedHashMap<String, MutableList<EpAcc>>()

        forEachEntry(text.lineSequence()) { e ->
            if (e.type == M3uEntryType.MOVIE) {
                val groupName = e.group.ifBlank { "Filmler" }
                val gid = groupIds.getOrPut(groupName) { nextGroupId++ }
                val yearFound = YEAR_IN_NAME.find(e.name)?.groupValues?.getOrNull(1) ?: ""
                movies.add(
                    VodItem(
                        id = (sourceId + "|movie|" + e.url).hashCode().toLong().and(0xFFFFFFFFL).let { if (it == 0L) 1L else it },
                        categoryId = gid,
                        name = e.name,
                        originalName = e.name,
                        poster = e.logo,
                        description = "",
                        year = yearFound,
                        cmd = e.url,
                        isSeries = false
                    )
                )
            } else if (e.type == M3uEntryType.SERIES) {
                val groupName = e.group.ifBlank { "Diziler" }
                val gid = groupIds.getOrPut(groupName) { nextGroupId++ }
                val epInfo = parseM3uEpisodeName(e.name)
                val key = "$gid|${epInfo.seriesTitle.lowercase()}"
                seriesMap.getOrPut(key) { mutableListOf() }.add(
                    EpAcc(
                        originalSeriesTitle = epInfo.seriesTitle,
                        gid = gid,
                        logo = e.logo,
                        seasonNum = epInfo.seasonNum,
                        epNum = epInfo.epNum,
                        epTitle = epInfo.epTitle,
                        url = e.url
                    )
                )
            }
        }

        val seriesVodItems = ArrayList<VodItem>()
        for ((_, epList) in seriesMap) {
            val first = epList.first()
            val seriesId = (sourceId + "|series|" + first.originalSeriesTitle.lowercase()).hashCode().toLong().and(0xFFFFFFFFL).let { if (it == 0L) 1L else it }
            val logo = epList.firstOrNull { it.logo.isNotBlank() }?.logo.orEmpty()
            val yearFound = YEAR_IN_NAME.find(first.originalSeriesTitle)?.groupValues?.getOrNull(1) ?: ""

            val seasonsGrouped = epList.groupBy { it.seasonNum }
            val seasons = seasonsGrouped.keys.sorted().map { sNum ->
                Season(
                    id = sNum.toLong(),
                    name = "$sNum. Sezon",
                    poster = logo
                )
            }
            val seasonEpisodes = seasonsGrouped.mapValues { (sNum, eps) ->
                eps.sortedBy { it.epNum }.map { ep ->
                    Episode(
                        id = (sourceId + "|ep|" + ep.url).hashCode().toLong().and(0xFFFFFFFFL).let { if (it == 0L) 1L else it },
                        name = ep.epTitle,
                        episodeNumber = ep.epNum,
                        cmd = ep.url,
                        thumb = ep.logo.ifBlank { logo }
                    )
                }
            }.mapKeys { it.key.toLong() }

            m3uSeriesSeasons[seriesId] = seasons
            m3uSeriesEpisodes[seriesId] = seasonEpisodes

            seriesVodItems.add(
                VodItem(
                    id = seriesId,
                    categoryId = first.gid,
                    name = first.originalSeriesTitle,
                    originalName = first.originalSeriesTitle,
                    poster = logo,
                    description = "",
                    year = yearFound,
                    cmd = epList.firstOrNull()?.url.orEmpty(),
                    isSeries = true
                )
            )
        }

        val allVod = movies + seriesVodItems
        val genres = listOf(Genre(0, "Tümü")) +
            groupIds.entries.map { Genre(it.value, it.key) }
        return genres to allVod
    }

    /** Tek geçişte canlı/film/dizi sayılarını döndürür (kaynak istatistikleri). */
    fun countTypes(file: File): Triple<Int, Int, Int> {
        var live = 0
        var movies = 0
        var series = 0
        file.bufferedReader(Charsets.UTF_8).use { r ->
            forEachEntry(r.lineSequence()) { e ->
                when (e.type) {
                    M3uEntryType.LIVE -> live++
                    M3uEntryType.MOVIE -> movies++
                    M3uEntryType.SERIES -> series++
                }
            }
        }
        return Triple(live, movies, series)
    }

    /**
     * M3U içeriğini URL'den doğrudan [dest] dosyasına akış olarak indirir.
     * Yüzlerce MB'lık listelerde `body.string()` dev bir String kurup bellek
     * taşmasına yol açıyordu; bu sürüm diskten akışla yazar.
     */
    suspend fun fetchToFile(url: String, dest: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url)
                .header("User-Agent", "IPTVSmartersPro/3.1.5 (Linux; Android 12)")
                .header("Accept-Encoding", "gzip, deflate")
                .build()
            val resp = http.newCall(req).execute()
            resp.use { r ->
                if (!r.isSuccessful) return@use false
                val body = r.body ?: return@use false
                body.byteStream().use { input ->
                    dest.outputStream().use { out -> input.copyTo(out, 256 * 1024) }
                }
                return@use true
            }
        }.getOrDefault(false)
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

    private val defaultHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // DoH/SOCKS ayarları aktifse NetworkConfig istemcisi kullanılır.
    private val http: OkHttpClient
        get() {
            val st = Store.activeStore?.settings() ?: Settings()
            return if (st.dohEnabled || st.socksProxy.isNotBlank()) {
                com.stalkerapp.util.NetworkConfig.buildClientFor(st)
            } else defaultHttp
        }

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

    /**
     * Panel `{"user_info":{"auth":0}}` (veya `{"auth":0}`) döndürebilir; bu HTTP
     * 200 ile gelir ama aslında başarısız senkronizasyondur. Boş katalog yerine
     * hata olarak ele alınmalı. `max_connections` aşımında sık görülür.
     */
    private fun isAuthFailure(el: JsonElement?): Boolean {
        val obj = el as? JsonObject ?: return false
        val ui = obj["user_info"] as? JsonObject
        if (ui != null) {
            val auth = (ui["auth"] as? JsonPrimitive)?.contentOrNull
            if (auth == "0") return true
        }
        val topAuth = (obj["auth"] as? JsonPrimitive)?.contentOrNull
        if (topAuth == "0") return true
        return false
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
                    val parsed = json.parseToJsonElement(text)
                    if (isAuthFailure(parsed)) throw XtreamApiException("auth failed (auth=0): ${r.request.url}")
                    return@withContext parsed
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
                val parsed = json.parseToJsonElement(text)
                if (isAuthFailure(parsed)) return@use null
                parsed
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
                    container = (e["container_extension"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    // Bazı paneller bölüm başına doğrudan oynatılabilir URL döner
                    // ("direct_source") — varsa URL buradan alınır, panel URL
                    // kurgusu devre dışı kalır.
                    directSource = (e["direct_source"] as? JsonPrimitive)?.contentOrNull.orEmpty()
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

    /**
     * Bir filmin zengin bilgisini `get_vod_info`'dan döner. Liste (get_vod_streams)
     * yalnız temel alanlar taşır; asıl plot/cast/director/genre ve — önemlisi —
     * `tmdb_id` tek tek get_vod_info çağrısında gelir. Kaynak doldurmadıysa alanlar
     * boş kalır (TMDB fallback'ini çağıran taraf tamamlar). Başarısızlıkta null.
     */
    suspend fun vodInfo(source: XtreamSource, vodId: Long): VodItem? {
        if (vodId <= 0) return null
        val el = getJson("${playerApi(source)}&action=get_vod_info&vod_id=$vodId") ?: return null
        val obj = el as? JsonObject ?: return null
        val info = obj["info"] as? JsonObject ?: return null
        val movie = obj["movie_data"] as? JsonObject
        val streamId = (movie?.get("stream_id") as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
            ?: vodId
        val name = (info["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (isSeparatorRow(name)) return null
        val ext = (movie?.get("container_extension") as? JsonPrimitive)?.contentOrNull.orEmpty()
        val backdrop = (info["backdrop_path"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val coverBig = (info["cover_big"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val movieImage = (info["movie_image"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return VodItem(
            id = ExternalVod.XTREAM_VOD_BASE + streamId,
            categoryId = (info["category_id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0,
            name = name,
            originalName = (info["o_name"] as? JsonPrimitive)?.contentOrNull.orEmpty().ifBlank { name },
            poster = coverBig.ifBlank { movieImage }.ifBlank { backdrop },
            description = (info["plot"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            year = (info["releasedate"] as? JsonPrimitive)?.contentOrNull.orEmpty().take(4),
            director = (info["director"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            country = (info["country"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            rating = (info["rating"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            genres = (info["genre"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            actors = (info["cast"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            duration = (info["duration"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            tmdbId = (info["tmdb_id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0,
            isSeries = false,
            cmd = vodPlayUrl(source, streamId, ext)
        )
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

    /**
     * Bölüm-id tabanlı dizi URL'si: `series/kullanıcı/şifre/BÖLÜM_ID.uzantı`.
     * Bazı paneller (reseller/dengeleyici kurulumlar) standart sezon/bölüm
     * yolunu reddedip (HTTP 401) yalnızca bu formatı kabul eder — panelin kendi
     * M3U dışa aktarımında da aynı format kullanılır.
     */
    fun episodePlayUrlByEpisodeId(
        source: XtreamSource,
        episodeId: Long,
        container: String = ""
    ): String {
        val e = container.ifBlank { "mkv" }.removePrefix(".")
        val base = apiBase(source)
        return "$base/series/${source.username}/${source.password}/$episodeId.$e"
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
    val container: String = "",
    val directSource: String = ""
)
