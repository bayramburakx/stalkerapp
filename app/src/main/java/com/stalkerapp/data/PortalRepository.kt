package com.stalkerapp.data

import android.util.Base64
import android.util.Xml
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

fun JsonElement?.asJsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

/**
 * Catalog-id base for series items on portals with a separate `type=series`
 * library (ids come back as "seriesId:fileId"). Adding this base keeps series
 * ids from colliding with plain VOD ids in the catalog, and lets
 * [PortalRepository.realSeriesId] recover the real id for API calls.
 */
private const val SERIES_ID_BASE = 10_000_000_000L

/** Catalog-id base for `type=series` category ids (kept apart from VOD category ids). */
private const val SERIES_CAT_BASE = 100_000L

sealed class PortalStatus {
    data object Idle : PortalStatus()
    data class Connecting(val portalName: String) : PortalStatus()
    data class Connected(val profile: Profile) : PortalStatus()
    data class Error(val message: String) : PortalStatus()
}

class PortalRepository(
    private val store: Store,
    private val client: StalkerClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val handshakeTokens = mutableMapOf<String, String>()
    private val streamTokens = mutableMapOf<String, String>()
    private val profiles = mutableMapOf<String, Profile>()
    private val genresCache = mutableMapOf<String, List<Genre>>()
    private val channelsCache = mutableMapOf<String, MutableMap<Long, List<Channel>>>()
    private val vodGenresCache = mutableMapOf<String, List<Genre>>()
    private val seriesGenresCache = mutableMapOf<String, List<Genre>>()
    // Series season structure: seriesId -> (season number, episode numbers).
    // This portal has no get_season_list/get_episodes; seasons come back as
    // get_ordered_list&movie_id=<seriesId> items whose `series` array lists the
    // episode numbers. Cache them so loadEpisodes can build playable episodes.
    private val seriesSeasonsCache = mutableMapOf<Long, List<Pair<Long, List<Int>>>>()
    private val vodCache = mutableMapOf<String, MutableMap<String, List<VodItem>>>()
    private val vodTotals = mutableMapOf<String, Int>()
    private val vodItemsById = mutableMapOf<String, MutableMap<Long, VodItem>>()
    private val epgCache = mutableMapOf<Long, List<EpgProgram>>()
    // Harici (XMLTV) EPG: xmltv_id -> programlar. 6 saatte bir yeniden indirilir.
    private val externalEpgMutex = Mutex()
    private var externalEpg: Map<String, List<EpgProgram>> = emptyMap()
    private var externalEpgUrl = ""
    private var externalEpgAt = 0L
    private val externalHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    // Which query parameter filters VOD lists by category on this portal
    // ("category" by default; some portals use "genre" or need the category
    // number). Discovered by [probeVodCategoryParam].
    @Volatile private var vodCategoryParam: String? = null
    // Which parameter this portal uses to page VOD lists. Standard middleware
    // honors "page", but modified panels often ignore it (and `per_page`),
    // returning the same first 14 items forever — those page via "p" instead.
    // Discovered by [probeVodPageParam]; also records the observed page size
    // and portal total so callers can adapt to tiny-page portals.
    @Volatile private var vodPageParam: String? = null
    @Volatile private var vodPageSize: Int = 0
    @Volatile private var vodTotal: Int = 0

    private fun vodKey(categoryId: Long, search: String): String =
        "$categoryId|${search.trim().lowercase()}"

    private val _status = MutableStateFlow<PortalStatus>(PortalStatus.Idle)
    val status: StateFlow<PortalStatus> = _status

    fun cooldownRemainingSeconds(): Long = client.cooldownRemainingSeconds()

    fun clearCooldown() = client.clearCooldown()

    /** Lets the sync clear the adaptive rate-limit backoff when it finishes. */
    fun resetAdaptiveInterval() = client.resetAdaptiveInterval()

    fun cachedProfile(): Profile? {
        val portal = store.activePortal() ?: return null
        return profiles[portal.id]
    }

    /**
     * Restores the last connected profile from disk so the app can start straight
     * into Home without a login round-trip (auto-login). Also re-arms the device
     * MAC so portal requests carry the auth params the middleware expects.
     */
    fun restoreProfileFromDisk(): Profile? {
        val portal = store.activePortal() ?: return null
        val saved = store.loadProfile(portal.id) ?: return null
        profiles[portal.id] = saved
        client.setDevice(saved.mac.ifEmpty { portal.mac })
        return saved
    }

    suspend fun channelStreamUrl(ch: Channel, profile: Profile): String {
        val base = profile.baseUrl
        val resp = try {
            client.request(
                base,
                "portal.php?type=itv&action=create_link",
                "POST",
                tokenFor(profile),
                mapOf(
                    "cmd" to ch.cmd.ifEmpty { "http://localhost/ch/${ch.id}_" },
                    "series" to "0",
                    "forced_storage" to "0",
                    "disable_ad" to "1"
                )
            )
        } catch (e: Exception) {
            null
        }
        if (resp != null) {
            StalkerClient.extractUrl(resp)?.let { return rewriteLocalhost(it, profile) }
        }
        StalkerClient.parseCmd(ch.cmd)?.let { return rewriteLocalhost(it, profile) }
        val server = profile.serverAddress
        if (server.isNotBlank()) {
            val s = if (server.startsWith("http")) server else "http://$server"
            return "$s/${ch.id}"
        }
        throw StalkerException("Kanal akış URL'si alınamadı")
    }

    private fun rewriteLocalhost(url: String, profile: Profile): String {
        if (!url.contains("localhost") && !url.contains("127.0.0.1")) return url
        val host = baseHost(profile.baseUrl)
        if (host.isBlank()) return url
        return url.replace(
            Regex("https?://(localhost|127\\.0\\.0\\.1)(:\\d+)?", setOf(RegexOption.IGNORE_CASE)),
            "http://$host"
        )
    }

    private fun baseHost(baseUrl: String): String {
        return Regex("https?://([^/]+)").find(baseUrl)?.groupValues?.get(1).orEmpty()
    }

    fun cachedChannels(genreId: Long): List<Channel>? {
        val portal = store.activePortal() ?: return null
        return channelsCache[portal.id]?.get(genreId)
    }

    fun findChannelById(id: Long): Channel? {
        channelsCache.values.forEach { genreMap ->
            genreMap.values.forEach { list ->
                list.firstOrNull { it.id == id }?.let { return it }
            }
        }
        return store.favoriteChannels().firstOrNull { it.id == id }
    }

    fun findVodById(id: Long): VodItem? {
        vodItemsById.values.forEach { map ->
            map[id]?.let { return it }
        }
        return store.favoriteVods().firstOrNull { it.id == id }
    }

    suspend fun connect(portal: Portal): Profile {
        _status.value = PortalStatus.Connecting(portal.name)
        return try {
            val base = StalkerClient.normalizeBase(portal.url)
            val mac = portal.mac.ifEmpty { StalkerClient.generateMac() }
            client.setDevice(mac)
            val handshake = client.request(
                base,
                "portal.php?type=stb&action=handshake",
                method = "GET",
                token = ""
            )
            val hToken = handshake.jsonObject["token"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty()
            if (hToken.isEmpty()) {
                throw StalkerException("Handshake başarısız: token alınamadı. Sunucu erişimi engelliyor olabilir.")
            }

            val body = buildMap {
                put("login", mac)
                put("sn", mac)
                put("mac", mac)
                put("hw_version", "1.0.0")
                put("hw_id", mac.replace(":", ""))
                put("app_name", "StalkerPlayer")
                if (portal.username.isNotBlank()) put("username", portal.username)
                if (portal.password.isNotBlank()) put("password", portal.password)
            }

            val profileResp = client.request(
                base,
                "portal.php?type=stb&action=get_profile",
                method = "POST",
                token = hToken,
                body = body
            )
            val profile = parseProfile(profileResp, base, portal, mac)

            val profileObj = profileResp.jsonObject
            val profileError = profileObj["error"]?.asJsonPrimitiveOrNull()?.contentOrNull
                ?: profileObj["message"]?.asJsonPrimitiveOrNull()?.contentOrNull
            if (!profileError.isNullOrBlank() && profile.serverAddress.isBlank() && profile.mac.isBlank()) {
                throw StalkerException(
                    "Portal MAC'i kabul etmedi: ${profileError}. Portalda bu MAC'in kayıtlı ve aktif olduğundan emin olun."
                )
            }

            if (profile.serverAddress.isBlank() && profile.mac.isBlank()) {
                throw StalkerException(
                    "Profil alınamadı. Portal adresi doğru mu? Portal, bu MAC ile kayıtlı bir cihaz bekliyor olabilir."
                )
            }

            val streamToken = try {
                val tResp = client.request(
                    base,
                    "portal.php?type=stb&action=get_token",
                    method = "POST",
                    token = hToken,
                    body = body
                )
                tResp.jsonObject["token"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty()
            } catch (e: Exception) {
                ""
            }

            handshakeTokens[portal.id] = hToken
            streamTokens[portal.id] = streamToken
            profiles[portal.id] = profile
            store.saveProfile(profile)
            store.savePortal(portal.copy(mac = mac))
            store.setActivePortalId(portal.id)
            _status.value = PortalStatus.Connected(profile)
            profile
        } catch (e: Exception) {
            _status.value = PortalStatus.Error(e.message ?: "Bağlantı hatası")
            throw e
        }
    }

    private fun tokenFor(profile: Profile): String =
        handshakeTokens[profile.portal?.id] ?: ""

    // ---------- LIVE TV ----------

    suspend fun loadGenres(profile: Profile): List<Genre> {
        genresCache[profile.portal?.id].let { if (it != null) return it }
        val resp = client.request(
            profile.baseUrl,
            "portal.php?type=itv&action=get_genres",
            "POST",
            tokenFor(profile),
            mapOf("js" to "1")
        )
        val list = parseGenres(resp)
        genresCache[profile.portal?.id ?: ""] = list
        return list
    }

    suspend fun loadChannels(profile: Profile, genreId: Long = 0): List<Channel> {
        val pid = profile.portal?.id ?: ""
        val genreCache = channelsCache.getOrPut(pid) { mutableMapOf() }
        genreCache[genreId]?.let { return it }

        val all = genreCache.getOrElse(0L) {
            val resp = client.request(
                profile.baseUrl,
                "portal.php?type=itv&action=get_all_channels",
                "POST",
                tokenFor(profile),
                mapOf(
                    "period" to "1",
                    "page" to "0",
                    "force_ch_link_check" to "0"
                )
            )
            parseChannels(resp).also { genreCache[0L] = it }
        }

        val result = if (genreId <= 0) all else all.filter { it.tvGenreId == genreId }
        genreCache[genreId] = result
        return result
    }

    suspend fun loadEpg(profile: Profile, channel: Channel): List<EpgProgram> {
        val channelId = channel.id
        epgCache[channelId]?.let { return it }
        val zone = portalZone(profile)
        val now = System.currentTimeMillis() / 1000
        val from = now - 3 * 3600
        val to = now + 24 * 3600
        val resp = client.request(
            profile.baseUrl,
            "portal.php?type=itv&action=get_epg_info",
            "POST",
            tokenFor(profile),
            mapOf(
                "ch_id" to channelId.toString(),
                "period" to "240",
                "from" to from.toString(),
                "to" to to.toString()
            )
        )
        val offsetHours = store.settings().timezoneOffset
        val data = parseDataArray(resp)
        var programs = data.mapNotNull { p ->
            val o = p.jsonObject
            val startStr = o["start"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty()
            val stopStr = o["stop"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty()
            if (startStr.isEmpty()) return@mapNotNull null
            val startTs = epgToEpoch(startStr, zone)
            val stopTs = epgToEpoch(stopStr, zone)
            EpgProgram(
                chId = o["ch_id"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toLongOrNull() ?: channelId,
                name = o["name"]?.asJsonPrimitiveOrNull()?.contentOrNull ?: "—",
                start = startStr,
                stop = stopStr,
                desc = o["descr"]?.asJsonPrimitiveOrNull()?.contentOrNull
                    ?: o["desc"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                category = o["category"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                startTs = startTs + offsetHours * 3600,
                stopTs = stopTs + offsetHours * 3600,
                isCurrent = startTs <= System.currentTimeMillis() / 1000 &&
                    System.currentTimeMillis() / 1000 < stopTs
            )
        }
        // Sıra: portalın kendi EPG'si → harici XMLTV (Ayarlar'da URL varsa) →
        // varsayılan (kanalın adı). Böylece rehber hiçbir zaman boş kalmaz.
        if (programs.isEmpty()) {
            programs = externalEpgFor(profile, channel)
        }
        if (programs.isEmpty()) {
            programs = defaultEpg(profile, channel)
        }
        epgCache[channelId] = programs
        return programs
    }

    /**
     * Ayarlardaki harici XMLTV URL'sinden kanalın programlarını döner.
     * Kanal `xmltv_id` ile eşleştirilir. Dosya 6 saatte bir yeniden indirilir
     * (ilk istekte). URL boşsa ya da kanal eşleşmezse boş döner.
     */
    private suspend fun externalEpgFor(profile: Profile, channel: Channel): List<EpgProgram> {
        val url = store.settings().epgUrl.trim()
        if (url.isBlank()) return emptyList()
        val xmltvId = channel.xmltvId.ifBlank {
            // Kanal önbellekte değilse (örn. ana sayfadan doğrudan oynatılan
            // favori kanal) kanal listesinden bul.
            channelsCache[profile.portal?.id]?.values?.asSequence()
                ?.flatten()?.firstOrNull { it.id == channel.id }?.xmltvId.orEmpty()
        }
        if (xmltvId.isBlank()) return emptyList()
        ensureExternalEpg(url)
        return externalEpg[xmltvId].orEmpty()
    }

    /** Harici EPG'yi gerekirse (ilk kez / 6 saat geçti / URL değişti) indirip ayrıştırır. */
    private suspend fun ensureExternalEpg(url: String) {
        val stale = externalEpgUrl != url ||
            System.currentTimeMillis() - externalEpgAt > 6 * 3600_000L ||
            externalEpg.isEmpty()
        if (!stale) return
        externalEpgMutex.withLock {
            val staleAgain = externalEpgUrl != url ||
                System.currentTimeMillis() - externalEpgAt > 6 * 3600_000L ||
                externalEpg.isEmpty()
            if (!staleAgain) return@withLock
            externalEpg = downloadAndParseXmltv(url)
            externalEpgUrl = url
            externalEpgAt = System.currentTimeMillis()
        }
    }

    /**
     * XMLTV dosyasını akış olarak indirip ayrıştırır (gzip destekli). Büyük
     * dosyalarda tüm içerik belleğe alınmaz — XmlPullParser ile satır satır okunur.
     */
    private suspend fun downloadAndParseXmltv(url: String): Map<String, List<EpgProgram>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url)
                    .header("User-Agent", "StalkerPlayer/1.0")
                    .build()
                externalHttp.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@use emptyMap()
                    val raw = r.body?.byteStream() ?: return@use emptyMap()
                    val input = if (url.contains(".gz", ignoreCase = true)) {
                        GZIPInputStream(raw)
                    } else raw
                    parseXmltv(input)
                }
            }.getOrDefault(emptyMap())
        }
    }

    private fun parseXmltv(input: java.io.InputStream): Map<String, List<EpgProgram>> {
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")
        val out = mutableMapOf<String, MutableList<EpgProgram>>()
        var chId = ""
        var startRaw = ""
        var stopRaw = ""
        var title = StringBuilder()
        var desc = StringBuilder()
        var inTitle = false
        var inDesc = false
        val now = System.currentTimeMillis() / 1000
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        chId = parser.getAttributeValue(null, "channel") ?: ""
                        startRaw = parser.getAttributeValue(null, "start") ?: ""
                        stopRaw = parser.getAttributeValue(null, "stop") ?: ""
                        title = StringBuilder()
                        desc = StringBuilder()
                    }
                    "title" -> inTitle = true
                    "desc", "sub-title" -> inDesc = true
                }
                XmlPullParser.TEXT -> {
                    if (inTitle) title.append(parser.text)
                    if (inDesc) desc.append(parser.text)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "title" -> inTitle = false
                    "desc", "sub-title" -> inDesc = false
                    "programme" -> {
                        if (chId.isNotBlank() && startRaw.isNotBlank()) {
                            val startTs = xmltvTimeToEpoch(startRaw)
                            val stopTs = xmltvTimeToEpoch(stopRaw)
                            if (startTs > 0) {
                                val list = out.getOrPut(chId) { mutableListOf() }
                                list += EpgProgram(
                                    chId = 0,
                                    name = title.toString().trim().ifBlank { "—" },
                                    start = startRaw,
                                    stop = stopRaw,
                                    desc = desc.toString().trim(),
                                    startTs = startTs,
                                    stopTs = stopTs,
                                    isCurrent = startTs <= now && now < stopTs
                                )
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        input.close()
        return out.mapValues { (_, v) -> v.sortedBy { it.startTs } }
    }

    /** XMLTV zamanı ("20260815183000 +0200") -> epoch saniye. Zaman dilimi yoksa UTC varsayılır. */
    private fun xmltvTimeToEpoch(raw: String): Long {
        val t = raw.trim()
        if (t.length < 14) return 0
        val zonePart = t.substring(14).trim().ifBlank { "+0000" }
        return runCatching {
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")
                .parse(t.take(14) + " " + zonePart)
                .let { java.time.ZonedDateTime.from(it) }
                .toEpochSecond()
        }.getOrDefault(0)
    }

    /**
     * Portal EPG'si olmayan kanallar için varsayılan program. Gerçek dışı
     * program adları üretmek yerine ("Günaydın Programı" gibi) kanalın kendi
     * adı tek program olarak gösterilir — rehber boş kalmaz ama yalan bir
     * program listesi de sunulmaz (şu an oynayan = kanalın yayını).
     */
    private fun defaultEpg(profile: Profile, channel: Channel): List<EpgProgram> {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis() / 1000
        val todayStart = runCatching {
            java.time.LocalDate.now(zone).atStartOfDay(zone).toEpochSecond()
        }.getOrDefault(now - (now % 86400))
        val name = channel.name.ifBlank {
            channelsCache[profile.portal?.id]?.values?.asSequence()
                ?.flatten()?.firstOrNull { it.id == channel.id }?.name.orEmpty()
        }.ifBlank { "Yayın" }
        return listOf(
            EpgProgram(
                chId = channel.id,
                name = name,
                start = "",
                stop = "",
                startTs = todayStart,
                stopTs = todayStart + 86400,
                isCurrent = true,
                isDefault = true
            )
        )
    }

    /**
     * Fast "all" pass (Tivimate-style): requests a very large `per_page` so the
     * whole library comes back in one or two requests instead of slowly paging
     * with cooldown. Returns the items plus the portal-reported total (0 if unknown).
     */
    suspend fun fetchAllVod(profile: Profile, perPage: Int = 100000): Pair<List<VodItem>, Int> {
        val out = mutableListOf<VodItem>()
        val seen = HashSet<Long>()
        var total = 0
        var page = 1
        var guard = 0
        var dupStreak = 0
        var pageSize = 0
        var maxPages = 2000
        while (guard < maxPages) {
            guard++
            val (list, t) = fetchVodPage(profile, page, perPage, emptyMap())
            if (pageSize == 0 && list.isNotEmpty()) pageSize = list.size
            if (t > 0) total = t
            if (list.isEmpty()) break
            if (total > 0 && pageSize > 0) {
                val needed = (total / pageSize) + 20
                // Portals that ignore `per_page` return tiny pages (e.g. 14),
                // so a full sequential enumeration needs thousands of requests.
                // Cap the warm-up; callers fall back to parallel per-category
                // paging, which is complete on such portals.
                maxPages = if (pageSize <= 30 && needed > 150) 150 else minOf(needed, 2000)
            }
            var added = 0
            list.forEach { item ->
                if (seen.add(item.id)) {
                    out += item
                    added++
                }
            }
            if (added == 0) {
                // Some portals shuffle/repeat pages; only stop after two
                // consecutive duplicate pages, otherwise we'd bail early with
                // a tiny subset (e.g. 2.5k of 80k items).
                if (++dupStreak >= 2) break
                page++
                continue
            }
            dupStreak = 0
            if (out.size > 300_000) break
            page++
        }
        return out to total
    }

    /** Pages a single VOD category fully and returns its items (category stamped). */
    suspend fun fetchVodCategory(
        profile: Profile,
        catId: Long,
        perPage: Int = 5000,
        categoryParam: String = "category"
    ): List<VodItem> {
        val out = mutableListOf<VodItem>()
        val seen = HashSet<Long>()
        var page = 1
        var guard = 0
        var dupStreak = 0
        var pageSize = 0
        var maxPages = 2000
        while (guard < maxPages) {
            guard++
            val (list, total) = fetchVodPage(profile, page, perPage, mapOf(categoryParam to catId.toString()))
            if (pageSize == 0 && list.isNotEmpty()) pageSize = list.size
            if (list.isEmpty()) {
                if (page > 1) break
                page++
                continue
            }
            if (total > 0 && pageSize > 0) maxPages = minOf((total / pageSize) + 20, 2000)
            var added = 0
            list.forEach { item ->
                if (seen.add(item.id)) {
                    out += if (item.categoryId == 0L) item.copy(categoryId = catId) else item
                    added++
                }
            }
            if (added == 0) {
                // Only stop after two consecutive fully-duplicate pages so a
                // transient page re-order doesn't truncate the category.
                if (++dupStreak >= 2) break
                page++
                continue
            }
            dupStreak = 0
            if (out.size > 300_000) break
            page++
        }
        return out
    }

    /**
     * Pages VOD search results for [search] (server-side `search` param). Used as
     * a last-resort enumeration strategy on portals whose plain list paging is
     * capped or broken: iterating letters/digits usually exposes the whole
     * library. Items keep whatever cat_id the portal reports.
     */
    suspend fun fetchVodSearch(profile: Profile, search: String, perPage: Int = 5000): List<VodItem> {
        val out = mutableListOf<VodItem>()
        val seen = HashSet<Long>()
        var page = 1
        var guard = 0
        var dupStreak = 0
        var pageSize = 0
        var maxPages = 2000
        while (guard < maxPages) {
            guard++
            val (list, total) = fetchVodPage(profile, page, perPage, mapOf("search" to search))
            if (pageSize == 0 && list.isNotEmpty()) pageSize = list.size
            if (list.isEmpty()) {
                if (page > 1) break
                page++
                continue
            }
            if (total > 0 && pageSize > 0) {
                val needed = (total / pageSize) + 20
                // Last-resort strategy: cap each token so a pathological portal
                // (tiny pages, broken filters) can't trigger unbounded paging.
                maxPages = minOf(needed, 300)
            }
            var added = 0
            list.forEach { item ->
                if (seen.add(item.id)) {
                    out += item
                    added++
                }
            }
            if (added == 0) {
                if (++dupStreak >= 2) break
                page++
                continue
            }
            dupStreak = 0
            if (out.size > 300_000) break
            page++
        }
        return out
    }

    /**
     * Figures out which parameter this portal uses to filter VOD lists by
     * category ("category" by default, but some use "genre", and a few need the
     * category *number* instead of its id). The winner is cached per portal.
     */
    suspend fun probeVodCategoryParam(profile: Profile): String {
        vodCategoryParam?.let { return it }
        val cats = runCatching { loadVodCategories(profile) }.getOrDefault(emptyList())
        // Skip the "All" pseudo-category (id 0) some panels expose — it is just
        // the unfiltered list and tells us nothing about the filter param.
        val probeCat = cats.firstOrNull { it.id != 0L }
        if (probeCat == null) {
            vodCategoryParam = "category"
            return "category"
        }
        // Items already visible via the "all" query (first page is enough: if
        // the category filter works it returns different items than the plain
        // first page; if it is ignored it returns the very same page).
        val allIds = runCatching { fetchVodPage(profile, 1, 500, emptyMap()).first }
            .getOrDefault(emptyList()).map { it.id }.toHashSet()
        val candidates = listOf(
            "category" to probeCat.id.toString(),
            "category" to probeCat.number.toString(),
            "genre" to probeCat.id.toString()
        )
        for ((param, value) in candidates) {
            val items = runCatching {
                fetchVodPage(profile, 1, 500, mapOf(param to value)).first
            }.getOrDefault(emptyList())
            if (items.size > 2 && items.any { it.id !in allIds }) {
                vodCategoryParam = param
                return param
            }
        }
        vodCategoryParam = "category"
        return "category"
    }

    /**
     * Detects which parameter this portal uses to page VOD lists. Standard
     * middleware honors `page`; some modified panels ignore it entirely (and
     * ignore `per_page`, hard-capping at ~14 items) and page via `p` instead.
     * The winner is cached per portal, along with the observed page size and
     * portal total. Returns "page" without caching when the probe requests
     * failed (e.g. during a cooldown) so a later call can re-probe.
     */
    suspend fun probeVodPageParam(profile: Profile): String {
        vodPageParam?.let { return it }
        suspend fun probe(param: String, pageVal: Int, perPage: Int): List<Long> = runCatching {
            val resp = client.request(
                profile.baseUrl,
                "portal.php?type=vod&action=get_ordered_list",
                "POST",
                tokenFor(profile),
                vodListBody(emptyMap(), pageVal, perPage, param)
            )
            val items = parseVodList(resp)
            if (pageVal == 1 && param == "page") {
                // Keep the largest observed page size: the per_page=5000 probe
                // shows the real page size on portals that honor it, and the
                // portal's cap (e.g. 14) on portals that ignore it.
                if (items.size > vodPageSize) vodPageSize = items.size
                if (vodTotal == 0) vodTotal = parseTotal(resp)
            }
            items.map { it.id }
        }.getOrDefault(emptyList())
        var sawItems = false
        for (param in listOf("page", "p")) {
            val p1 = probe(param, 1, 5000)
            val p2 = probe(param, 2, 5000)
            if (p1.isNotEmpty()) sawItems = true
            if (p1.isNotEmpty() && p1 != p2) {
                vodPageParam = param
                return param
            }
        }
        // The per_page=5000 probes may have failed even though the portal is
        // fine (some reject large per_page values); re-measure the page size
        // with the original format so [vodPageSize] is still meaningful.
        if (vodPageSize == 0) probe("page", 1, 0)
        // Neither param advanced the list. If the requests succeeded the portal
        // simply ignores paging (dup-streak guards stop the loops); if they
        // failed, leave it uncached so we re-probe after the cooldown clears.
        if (sawItems) vodPageParam = "page"
        return "page"
    }

    /** Observed VOD page size (0 until [probeVodPageParam] has run). */
    fun vodPageSize(): Int = vodPageSize

    /** Portal-reported VOD total (0 until [probeVodPageParam] has run). */
    fun vodPortalTotal(): Int = vodTotal

    // ---------- VOD ----------

    suspend fun loadVodCategories(profile: Profile): List<Genre> {
        vodGenresCache[profile.portal?.id].let { if (it != null) return it }
        val resp = client.request(
            profile.baseUrl,
            "portal.php?type=vod&action=get_categories",
            "POST",
            tokenFor(profile),
            mapOf("js" to "1")
        )
        val list = parseGenres(resp)
        vodGenresCache[profile.portal?.id ?: ""] = list
        return list
    }

    /**
     * Categories of the separate `type=series` library. Some portals (this one
     * included) keep series in their own namespace with ids like "22695:22695"
     * and their own categories, completely outside `type=vod`. Category ids are
     * namespaced by [SERIES_CAT_BASE] so they can't collide with VOD categories
     * in the shared catalog.
     */
    /**
     * Categories of the separate `type=series` library. Some portals (this one
     * included) keep series in their own namespace with ids like "22695:22695"
     * and their own categories, completely outside `type=vod`. Category ids are
     * namespaced by [SERIES_CAT_BASE] so they can't collide with VOD categories
     * in the shared catalog.
     */
    suspend fun loadSeriesCategories(profile: Profile): List<Genre> {
        val pid = profile.portal?.id ?: ""
        seriesGenresCache[pid]?.let { return it }
        // Cooldown-aware: the portal may be rate-limiting right after the heavy
        // VOD sync, and this call must not fail the whole series pass silently.
        var resp: JsonElement? = null
        repeat(4) {
            if (resp == null) {
                try {
                    resp = client.request(
                        profile.baseUrl,
                        "portal.php?type=series&action=get_categories",
                        "POST",
                        tokenFor(profile),
                        mapOf("js" to "1")
                    )
                } catch (e: StalkerException) {
                    if (e.isCooldown) delay(client.cooldownRemainingMs() + 1000)
                }
            }
        }
        resp ?: return emptyList()
        val list = parseGenres(resp).mapNotNull { g ->
            if (g.id <= 0) null else g.copy(id = SERIES_CAT_BASE + g.id)
        }
        seriesGenresCache[pid] = list
        return list
    }

    /** Real (portal) category id for a namespaced series category id. */
    fun realSeriesCatId(catId: Long): Long =
        if (catId >= SERIES_CAT_BASE) catId - SERIES_CAT_BASE else catId

    /**
     * Pages a single series category fully (the separate `type=series` library).
     * Items get namespaced ids (series id base) and category ids so they can live
     * next to VOD items in the catalog without collisions.
     */
    suspend fun fetchSeriesCategory(
        profile: Profile,
        catId: Long,
        perPage: Int = 5000
    ): List<VodItem> {
        val realCat = realSeriesCatId(catId)
        val out = mutableListOf<VodItem>()
        val seen = HashSet<Long>()
        var page = 1
        var guard = 0
        var dupStreak = 0
        var pageSize = 0
        var maxPages = 2000
        while (guard < maxPages) {
            guard++
            val (list, total) = fetchVodPage(profile, page, perPage, mapOf("category" to realCat.toString()), series = true)
            if (pageSize == 0 && list.isNotEmpty()) pageSize = list.size
            if (list.isEmpty()) {
                if (page > 1) break
                page++
                continue
            }
            if (total > 0 && pageSize > 0) maxPages = minOf((total / pageSize) + 20, 2000)
            var added = 0
            list.forEach { item ->
                if (seen.add(item.id)) {
                    // Namespace the portal category id so it matches the catalog's
                    // series categories (SERIES_CAT_BASE + real id).
                    val realCat = if (item.categoryId != 0L) item.categoryId else realSeriesCatId(catId)
                    out += item.copy(categoryId = SERIES_CAT_BASE + realCat)
                    added++
                }
            }
            if (added == 0) {
                if (++dupStreak >= 2) break
                page++
                continue
            }
            dupStreak = 0
            if (out.size > 300_000) break
            page++
        }
        return out
    }

    /** Real series id for a namespaced catalog id (0 if not namespaced). */
    fun realSeriesId(vodId: Long): Long =
        if (vodId >= SERIES_ID_BASE) vodId - SERIES_ID_BASE else 0

    suspend fun loadVodList(
        profile: Profile,
        categoryId: Long = 0,
        page: Int = 1,
        search: String = ""
    ): List<VodItem> {
        val pid = profile.portal?.id ?: ""
        val cache = vodCache.getOrPut(pid) { mutableMapOf() }
        val key = vodKey(categoryId, search)
        if (page <= 1 && cache[key] != null) return cache[key]!!

        val pageParam = probeVodPageParam(profile)
        val body = buildMap {
            put(pageParam, page.toString())
            if (categoryId > 0) put("category", categoryId.toString())
            if (search.isNotBlank()) put("search", search.trim())
        }
        val resp = client.request(
            profile.baseUrl,
            "portal.php?type=vod&action=get_ordered_list",
            "POST",
            tokenFor(profile),
            body
        )
        val list = parseVodList(resp)
        vodTotals["$pid:$key"] = parseTotal(resp)
        if (page <= 1) {
            cache[key] = list
        } else {
            cache[key] = (cache[key] ?: emptyList()) + list
        }
        list.forEach { vodItemsById.getOrPut(pid) { mutableMapOf() }[it.id] = it }
        return list
    }

    fun vodTotal(profile: Profile, categoryId: Long = 0, search: String = ""): Int {
        val pid = profile.portal?.id ?: ""
        return vodTotals["$pid:${vodKey(categoryId, search)}"] ?: 0
    }

    /** Clears all in-memory caches (used when switching active portal). */
    fun clearCaches() {
        handshakeTokens.clear()
        streamTokens.clear()
        profiles.clear()
        genresCache.clear()
        channelsCache.clear()
        vodGenresCache.clear()
        seriesGenresCache.clear()
        seriesSeasonsCache.clear()
        vodCache.clear()
        vodTotals.clear()
        vodItemsById.clear()
        epgCache.clear()
        vodCategoryParam = null
        vodPageParam = null
        vodPageSize = 0
        vodTotal = 0
    }

    /** Clears the EPG cache so programs are re-fetched (portal + harici XMLTV) on next view. */
    fun clearEpgCache() {
        epgCache.clear()
        externalEpg = emptyMap()
        externalEpgUrl = ""
        externalEpgAt = 0L
    }

    /** Force refresh EPG for the given channels (clears and re-fetches in background). */
    suspend fun refreshEpg(profile: Profile, channels: List<Channel>) {
        epgCache.clear()
        channels.take(40).forEach { ch ->
            runCatching { loadEpg(profile, ch) }
        }
    }

    /**
     * Loads the COMPLETE VOD catalog in the background, fast and complete.
     *
     * Strategy (matches how clients like Tivimate enumerate the whole library):
     *  1) A full "all items" pass is attempted first. For "all", the `category`
     *     parameter is OMITTED (passing `category=0` makes the portal treat it as
     *     a real — usually empty — category, which is what caused only ~14 items
     *     to be returned). Paging uses the portal-reported `total` to know when
     *     the whole library is exhausted. This is what makes the real total
     *     (e.g. 81k) reachable and keeps the request count minimal.
     *  2) The known categories are fetched first so we can derive `isSeries` from
     *     any category whose title contains "dizi". Series items get their category
     *     id stamped (so filtering works) and `isSeries` set.
     *  3) If the all-pass under-delivers (e.g. the portal ignores `page` and keeps
     *     returning the same first page), we fall back to iterating EVERY category,
     *     which is the reliable way to enumerate the full library on such portals.
     *     Otherwise we only top up the "dizi" categories (cheap) to guarantee
     *     series coverage — this is the key speed fix versus paging ALL categories
     *     unconditionally.
     *
     * Per-unit page accounting (not the global accumulator) decides when a unit is
     * exhausted, so we never stop after the first page. Items stream in via
     * [onItem] (live display) and [onProgress].
     */
    suspend fun syncVodCatalog(
        profile: Profile,
        perPage: Int = 5000,
        onItem: (VodItem) -> Unit = {},
        onProgress: (donePages: Int, totalPages: Int, loadedItems: Int) -> Unit = { _, _, _ -> }
    ): List<VodItem> {
        val cats = runCatching { loadVodCategories(profile) }.getOrDefault(emptyList())
        val seriesCatIds = cats.filter { it.title.contains("dizi", ignoreCase = true) }.map { it.id }.toSet()
        val all = LinkedHashMap<Long, VodItem>()
        var totalPagesEst = 0
        var donePages = 0
        fun report() = onProgress(donePages, if (totalPagesEst > 0) totalPagesEst else 1, all.size)

        // Returns the total_items reported by the portal for this unit (0 if unknown).
        suspend fun pageUnit(catId: Long, pp: Int): Int {
            var page = 1
            var catCount = 0
            var unitTotal = 0
            var emptyStreak = 0
            var guard = 0
            var pageSize = 0
            var maxPages = 2000
            while (guard < maxPages) {
                guard++
                val (list, total) = fetchVodPage(
                    profile,
                    page,
                    pp,
                    if (catId != 0L) mapOf("category" to catId.toString()) else emptyMap()
                )
                if (pageSize == 0 && list.isNotEmpty()) pageSize = list.size
                if (unitTotal == 0 && total > 0) {
                    unitTotal = total
                    val eff = if (pageSize > 0) pageSize else pp
                    totalPagesEst += (total / eff) + 1
                }
                if (unitTotal > 0 && pageSize > 0) {
                    val needed = (unitTotal / pageSize) + 20
                    // Same tiny-page guard as [fetchAllVod]: portals that ignore
                    // `per_page` need thousands of pages, so cap the warm-up and
                    // let the caller fall back to parallel per-category paging.
                    maxPages = if (pageSize <= 30 && needed > 150) 150 else minOf(needed, 2000)
                }
                if (list.isEmpty()) {
                    emptyStreak++
                    if (emptyStreak >= 2) break
                    page++
                    donePages++
                    report()
                    continue
                }
                emptyStreak = 0
                var added = 0
                list.forEach { item ->
                    if (!all.containsKey(item.id)) {
                        var stamped = item
                        if (catId != 0L && stamped.categoryId == 0L) stamped = stamped.copy(categoryId = catId)
                        if (seriesCatIds.contains(stamped.categoryId) && !stamped.isSeries) {
                            stamped = stamped.copy(isSeries = true)
                        }
                        all[stamped.id] = stamped
                        added++
                        onItem(stamped)
                    }
                }
                catCount += list.size
                donePages++
                report()
                if (total > 0 && catCount >= total) break
                if (added == 0) break
                page++
            }
            return unitTotal
        }

        // Pass 1: everything (omit category → true "all").
        val allPassTotal = runCatching { pageUnit(0, perPage) }.getOrDefault(0)
        // Decide whether the all-pass actually enumerated the library.
        val underDelivered = all.size < 200 || (allPassTotal > 0 && allPassTotal > all.size + 500)
        if (underDelivered) {
            // Reliable fallback: page every known category (skipping the "All"
            // pseudo-category, which is just the unfiltered list again).
            cats.filter { it.id != 0L }.forEach { runCatching { pageUnit(it.id, perPage) } }
        } else {
            // Top up series coverage cheaply.
            cats.filter { it.id in seriesCatIds }.forEach { runCatching { pageUnit(it.id, perPage) } }
        }
        return all.values.toList()
    }

    private fun vodListBody(
        params: Map<String, String>,
        page: Int,
        perPage: Int,
        pageParam: String = "page"
    ): Map<String, String> = buildMap {
        put(pageParam, page.toString())
        params.forEach { (k, v) -> put(k, v) }
        if (perPage > 0) put("per_page", perPage.toString())
    }

    /**
     * One get_ordered_list page. Tries the requested `per_page` first, then
     * smaller sizes, and ALWAYS falls back to the original format (no per_page)
     * — the most compatible option, even if the per_page variants failed. A
     * global cooldown is waited out at most once per variant so a rate-limited
     * portal doesn't silently kill the enumeration.
     */
    private suspend fun fetchVodPage(
        profile: Profile,
        page: Int,
        perPage: Int,
        params: Map<String, String>,
        series: Boolean = false
    ): Pair<List<VodItem>, Int> {
        // Which page param this portal honors ("page" or "p") — cached after
        // the first call, so this is cheap inside paging loops.
        val pageParam = probeVodPageParam(profile)
        val type = if (series) "series" else "vod"
        suspend fun requestWith(pp: Int): JsonElement = client.request(
            profile.baseUrl,
            "portal.php?type=$type&action=get_ordered_list",
            "POST",
            tokenFor(profile),
            vodListBody(params, page, pp, pageParam)
        )

        val perPageOptions = buildList {
            add(perPage)
            if (perPage > 2000) add(2000)
            if (perPage > 500) add(500)
            if (perPage > 100) add(100)
        }.distinct()

        val parser = if (series) ::parseSeriesList else ::parseVodList

        var waitedOnce = false
        for (pp in perPageOptions) {
            try {
                val resp = requestWith(pp)
                return parser(resp) to parseTotal(resp)
            } catch (e: StalkerException) {
                if (e.isCooldown && !waitedOnce) {
                    waitedOnce = true
                    delay(client.cooldownRemainingMs() + 1000)
                    continue // retry (next smaller size) after the cooldown clears
                }
                // non-cooldown failure, or already waited: try the next size
            }
        }

        // Original format (no per_page) — try it even if per_page variants failed.
        try {
            val resp = requestWith(0)
            return parser(resp) to parseTotal(resp)
        } catch (e: StalkerException) {
            if (e.isCooldown) {
                delay(client.cooldownRemainingMs() + 1000)
                try {
                    val resp = requestWith(0)
                    return parser(resp) to parseTotal(resp)
                } catch (_: StalkerException) {
                    return Pair(emptyList<VodItem>(), 0)
                }
            }
            return Pair(emptyList<VodItem>(), 0)
        }
    }

    suspend fun vodById(profile: Profile, id: Long): VodItem? {
        vodItemsById[profile.portal?.id]?.get(id)?.let { return it }
        val resp = client.request(
            profile.baseUrl,
            "portal.php?type=vod&action=get_ordered_list",
            "POST",
            tokenFor(profile),
            mapOf("vod_id" to id.toString())
        )
        val item = parseVodList(resp).firstOrNull()
        item?.let { vodItemsById.getOrPut(profile.portal?.id ?: "") { mutableMapOf() }[it.id] = it }
        return item
    }

    /**
     * Fetches the detailed VOD info (`get_info`), which usually carries richer
     * metadata than the list endpoint — most importantly the cast (`actors`).
     * Returns null on any failure so callers can fall back to the list item.
     */
    suspend fun vodInfo(profile: Profile, id: Long): VodItem? {
        return runCatching {
            val resp = client.request(
                profile.baseUrl,
                "portal.php?type=vod&action=get_info",
                "POST",
                tokenFor(profile),
                mapOf("movie_id" to id.toString())
            )
            parseVodInfo(resp)
        }.getOrNull()
    }

    /**
     * Loads the seasons of a series. Standard middleware exposes them via
     * `get_season_list`; modified panels (this one included) return nothing there
     * and instead list seasons through `type=series&action=get_ordered_list`
     * with `movie_id=<seriesId>` (each season item carries its episode numbers in
     * the `series` array). Tries the standard call first, then the fallback.
     */
    suspend fun loadSeasons(profile: Profile, vodId: Long): List<Season> {
        val realId = realSeriesId(vodId).takeIf { it > 0 } ?: vodId
        val standard = runCatching {
            val resp = client.request(
                profile.baseUrl,
                "portal.php?type=vod&action=get_season_list",
                "POST",
                tokenFor(profile),
                mapOf("movie_id" to vodId.toString())
            )
            parseSeasons(resp)
        }.getOrDefault(emptyList())
        if (standard.isNotEmpty()) return standard

        // Fallback for the separate type=series library.
        val resp = client.request(
            profile.baseUrl,
            "portal.php?type=series&action=get_ordered_list",
            "POST",
            tokenFor(profile),
            mapOf("movie_id" to realId.toString())
        )
        val seasons = parseDataArray(resp).mapNotNull { o ->
            val rawId = o["id"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty()
            val seasonNum = rawId.substringAfter(':').toLongOrNull() ?: 0
            if (seasonNum <= 0) return@mapNotNull null
            val name = o["name"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty()
                .ifBlank { "Sezon $seasonNum" }
            val episodeNums = (o["series"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }
                .orEmpty()
            seriesSeasonsCache[realId] = seriesSeasonsCache[realId].orEmpty() + (seasonNum to episodeNums)
            Season(
                id = seasonNum,
                name = name,
                // Portal sezon başına gerçek bir görsel vermiyor (hepsi aynı
                // dizi afişi); yine de varsa kullan, yoksa TMDB'ye düşülür.
                poster = o["screenshot_uri"]?.asJsonPrimitiveOrNull()?.contentOrNull
                    ?: o["pic"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty()
            )
        }.sortedBy { it.id }
        return seasons
    }

    /**
     * Loads the episodes of a season. Standard middleware uses `get_episodes`;
     * on panels without it, episodes are reconstructed from the season's episode
     * numbers (cached by [loadSeasons]) and stream via a create_link cmd built
     * from `{"series_id":..,"season_num":..,"episode_num":..,"type":"series"}`.
     */
    suspend fun loadEpisodes(profile: Profile, vodId: Long, seasonId: Long): List<Episode> {
        val realId = realSeriesId(vodId).takeIf { it > 0 } ?: vodId
        val standard = runCatching {
            val resp = client.request(
                profile.baseUrl,
                "portal.php?type=vod&action=get_episodes",
                "POST",
                tokenFor(profile),
                mapOf("movie_id" to vodId.toString(), "season_id" to seasonId.toString())
            )
            parseEpisodes(resp)
        }.getOrDefault(emptyList())
        if (standard.isNotEmpty()) return standard

        val episodeNums = seriesSeasonsCache[realId]
            ?.firstOrNull { it.first == seasonId }?.second
            ?: emptyList()
        return episodeNums.map { n ->
            val cmd = seriesEpisodeCmd(realId, seasonId, n)
            Episode(
                id = n.toLong(),
                // UI renders "${episodeNumber}. ${name}" — keep the number out of
                // the name so fallback episodes show "1. Bölüm", not "1. 1. Bölüm".
                name = "",
                episodeNumber = n,
                cmd = cmd
            )
        }
    }

    /**
     * Bir sezonun bölüm numaralarını döner. [loadSeasons] tüm sezonların bölüm
     * listesini önbelleğe alır; eksikse önce sezonlar yüklenir (ağ isteği).
     * "Sezonu izlendi işaretle" gibi işlemler için kullanılır.
     */
    suspend fun seasonEpisodeNumbers(profile: Profile, vodId: Long, seasonId: Long): List<Int> {
        val realId = realSeriesId(vodId).takeIf { it > 0 } ?: vodId
        val cached = seriesSeasonsCache[realId]?.firstOrNull { it.first == seasonId }?.second
        if (cached != null) return cached
        loadSeasons(profile, vodId)
        return seriesSeasonsCache[realId]?.firstOrNull { it.first == seasonId }?.second.orEmpty()
    }

    /** create_link cmd for one series episode (base64 JSON, as the portal expects). */
    private fun seriesEpisodeCmd(seriesId: Long, seasonNum: Long, episodeNum: Int): String {
        val payload = """{"series_id":$seriesId,"season_num":$seasonNum,"episode_num":$episodeNum,"type":"series"}"""
        return Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    suspend fun vodStreamUrl(
        item: VodItem,
        profile: Profile,
        episode: Episode? = null
    ): String {
        val seriesParam = when {
            episode != null -> (episode.episodeNumber.takeIf { it > 0 } ?: 1).toString()
            item.isSeries -> "1"
            else -> "0"
        }

        // create_link wants the portal's own id: the real series id for the
        // separate series library (the namespaced catalog id would be rejected).
        val apiId = if (item.seriesId > 0) item.seriesId else item.id

        val cmdCandidates = buildList {
            if (!episode?.cmd.isNullOrBlank()) add(episode!!.cmd)
            if (item.cmd.isNotBlank()) add(item.cmd)
            add("/media/$apiId.mp4")
        }

        for (cmd in cmdCandidates) {
            tryCreateLink(profile, cmd, apiId, seriesParam)?.let { return it }
        }

        episode?.cmd?.takeIf { it.isNotBlank() }?.let {
            StalkerClient.parseCmd(it)?.let { u -> return fixLocalhost(u, profile) }
        }
        item.cmd.takeIf { it.isNotBlank() }?.let {
            StalkerClient.parseCmd(it)?.let { u -> return fixLocalhost(u, profile) }
        }
        val server = profile.serverAddress
        if (server.isNotBlank()) {
            val s = if (server.startsWith("http")) server else "http://$server"
            return fixLocalhost("$s/media/$apiId.mp4", profile)
        }
        throw StalkerException("VOD akış URL'si alınamadı")
    }

    private suspend fun tryCreateLink(
        profile: Profile,
        cmd: String,
        vodId: Long,
        seriesParam: String
    ): String? {
        val resp = try {
            client.request(
                profile.baseUrl,
                "portal.php?type=vod&action=create_link",
                "POST",
                tokenFor(profile),
                mapOf(
                    "cmd" to cmd,
                    "vod_id" to vodId.toString(),
                    "file_id" to vodId.toString(),
                    "series" to seriesParam,
                    "forced_storage" to "0",
                    "disable_ad" to "1"
                )
            )
        } catch (e: Exception) {
            null
        }
        return resp?.let { StalkerClient.extractUrl(it)?.let { u -> fixLocalhost(u, profile) } }
    }

    private fun fixLocalhost(url: String, profile: Profile): String {
        return if (url.contains("localhost", true) || url.contains("127.0.0.1", true)) {
            rewriteLocalhost(url, profile)
        } else url
    }

    // ---------- Parsers ----------

    private fun parseProfile(resp: JsonElement, base: String, portal: Portal, mac: String): Profile {
        val obj = resp.jsonObject
        val serverInfo = (obj["server_info"] as? JsonArray)?.mapNotNull { e ->
            val o = e.jsonObject
            ServerInfo(
                address = o["address"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                city = o["city"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty()
            )
        }.orEmpty()
        val tz = obj["timezone"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty()
        return Profile(
            mac = mac,
            timezone = tz,
            serverInfo = serverInfo,
            baseUrl = base,
            portal = portal
        )
    }

    private fun parseDataArray(el: JsonElement): List<JsonObject> {
        return when (el) {
            is JsonObject -> (el["data"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            is JsonArray -> el.mapNotNull { it as? JsonObject }
            else -> emptyList()
        }
    }

    private fun parseTotal(el: JsonElement): Int {
        val obj = el as? JsonObject ?: return 0
        val t = obj["total_items"]?.asJsonPrimitiveOrNull()?.contentOrNull
            ?: obj["total"]?.asJsonPrimitiveOrNull()?.contentOrNull
        return t?.toIntOrNull() ?: 0
    }

    private fun parseGenres(el: JsonElement): List<Genre> {
        return parseDataArray(el).mapNotNull { o ->
            Genre(
                id = o["id"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toLongOrNull() ?: 0,
                title = o["title"]?.asJsonPrimitiveOrNull()?.contentOrNull ?: "—",
                censored = o["censored"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toBooleanStrictOrNull() == true,
                number = o["number"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toIntOrNull() ?: 0
            )
        }
    }

    private fun parseChannels(el: JsonElement): List<Channel> {
        return parseDataArray(el).mapNotNull { o ->
            Channel(
                id = o["id"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toLongOrNull() ?: 0,
                name = o["name"]?.asJsonPrimitiveOrNull()?.contentOrNull ?: "",
                number = o["number"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toIntOrNull() ?: 0,
                logo = o["logo"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                cmd = o["cmd"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                tvGenreId = o["tv_genre_id"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toLongOrNull() ?: 0,
                tvGenreTitle = o["tv_genre_title"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                isTvArchive = o["is_tv_archive"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toBooleanStrictOrNull() == true,
                archiveDuration = o["tv_archive_duration"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toIntOrNull() ?: 0,
                xmltvId = o["xmltv_id"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty()
            )
        }.filter { it.id > 0 }
    }

    /**
     * Parses items from the separate `type=series` library. Series ids come back
     * as "seriesId:fileId"; the catalog id is namespaced by [SERIES_ID_BASE] so
     * series can't collide with plain VOD ids, and the real series id is kept in
     * [VodItem.seriesId] for season/episode/stream calls.
     */
    private fun parseSeriesList(el: JsonElement): List<VodItem> {
        return parseDataArray(el).mapNotNull { o ->
            val rawId = o["id"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty()
            val seriesId = rawId.substringBefore(':').toLongOrNull() ?: 0
            if (seriesId <= 0) return@mapNotNull null
            val name = o["name"]?.asJsonPrimitiveOrNull()?.contentOrNull ?: ""
            val year = (o["year"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty())
                .takeIf { it.isNotBlank() }
                ?.let { if (it.length >= 4 && it.substring(0, 4).all { ch -> ch.isDigit() }) it.take(4) else it }
                .orEmpty()
            VodItem(
                id = SERIES_ID_BASE + seriesId,
                categoryId = o["category_id"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toLongOrNull()
                    ?: o["cat_id"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toLongOrNull() ?: 0,
                name = name,
                originalName = o["o_name"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                sname = o["sname"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                poster = o["screenshot_uri"]?.asJsonPrimitiveOrNull()?.contentOrNull
                    ?: o["pic"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                description = o["description"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                year = year,
                director = o["director"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                country = o["country"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                rating = o["rating_imdb"]?.asJsonPrimitiveOrNull()?.contentOrNull
                    ?: o["rating"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                genres = o["genres_str"]?.asJsonPrimitiveOrNull()?.contentOrNull
                    ?: o["genres"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                actors = o["actors"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                tmdbId = o["tmdb_id"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toLongOrNull() ?: 0,
                isSeries = true,
                seriesId = seriesId
            )
        }.filter { it.id > 0 }
    }

    private fun parseVodList(el: JsonElement): List<VodItem> {
        return parseDataArray(el).mapNotNull { o ->
            val seriesRef = o["series"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty()
            VodItem(
                id = o["id"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toLongOrNull() ?: 0,
                categoryId = o["cat_id"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toLongOrNull()
                    ?: o["category_id"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toLongOrNull() ?: 0,
                name = o["name"]?.asJsonPrimitiveOrNull()?.contentOrNull ?: "",
                originalName = o["o_name"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                sname = o["sname"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                poster = o["screenshot_uri"]?.asJsonPrimitiveOrNull()?.contentOrNull
                    ?: o["pic"]?.asJsonPrimitiveOrNull()?.contentOrNull
                    ?: o["poster"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                description = o["description"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                year = o["year"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                director = o["director"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                country = o["country"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                rating = o["rating"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                genres = o["genres"]?.asJsonPrimitiveOrNull()?.contentOrNull
                    ?: o["genres_str"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                actors = o["actors"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                duration = o["time"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                writers = o["writers"]?.asJsonPrimitiveOrNull()?.contentOrNull
                    ?: o["writer"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                seriesRef = seriesRef,
                isSeries = seriesRef.isNotBlank(),
                cmd = o["cmd"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                selectedSeason = o["selected_season"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                seriesData = o["series_data"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                tmdbId = o["tmdb_id"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toLongOrNull() ?: 0
            )
        }.filter { it.id > 0 }
    }

    private fun parseVodInfo(el: JsonElement): VodItem? {
        val root = el as? JsonObject ?: return null
        val obj = (root["data"] as? JsonObject) ?: root
        val str = { key: String -> obj[key]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty() }
        val seriesRef = str("series")
        return VodItem(
            id = str("id").toLongOrNull() ?: 0,
            name = str("name"),
            originalName = str("o_name"),
            poster = str("screenshot_uri").ifBlank { str("pic") },
            description = str("description"),
            year = str("year"),
            director = str("director"),
            country = str("country"),
            rating = str("rating_imdb").ifBlank { str("rating") },
            genres = str("genres_str").ifBlank { str("genre") }.ifBlank { str("genres") },
            actors = str("actors"),
            duration = str("time"),
            writers = str("writers").ifBlank { str("writer") },
            seriesRef = seriesRef,
            isSeries = seriesRef.isNotBlank(),
            tmdbId = str("tmdb_id").toLongOrNull() ?: 0
        )
    }

    private fun parseSeasons(el: JsonElement): List<Season> {
        return parseDataArray(el).mapNotNull { o ->
            Season(
                id = o["id"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toLongOrNull() ?: 0,
                name = o["name"]?.asJsonPrimitiveOrNull()?.contentOrNull ?: "",
                poster = o["screenshot_uri"]?.asJsonPrimitiveOrNull()?.contentOrNull
                    ?: o["pic"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty()
            )
        }
    }

    private fun parseEpisodes(el: JsonElement): List<Episode> {
        return parseDataArray(el).mapNotNull { o ->
            Episode(
                id = o["id"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toLongOrNull() ?: 0,
                name = o["name"]?.asJsonPrimitiveOrNull()?.contentOrNull ?: "",
                episodeNumber = o["episode_number"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toIntOrNull()
                    ?: o["series_number"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toIntOrNull() ?: 0,
                cmd = o["cmd"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                thumb = o["screenshot_uri"]?.asJsonPrimitiveOrNull()?.contentOrNull
                    ?: o["pic"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty()
            )
        }
    }

    // ---------- Time helpers ----------

    private val epgFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun portalZone(profile: Profile): ZoneId? {
        val tz = profile.timezone
        if (tz.isNotBlank()) {
            runCatching { return ZoneId.of(tz) }.onFailure { }
        }
        return null
    }

    private fun epgToEpoch(t: String, zone: ZoneId?): Long {
        if (zone == null) return 0
        return runCatching {
            LocalDateTime.parse(t, epgFormatter).atZone(zone).toEpochSecond()
        }.getOrDefault(0)
    }

    fun formatEpoch(ts: Long): String {
        if (ts == 0L) return ""
        return runCatching {
            java.time.Instant.ofEpochSecond(ts)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrDefault("")
    }
}
