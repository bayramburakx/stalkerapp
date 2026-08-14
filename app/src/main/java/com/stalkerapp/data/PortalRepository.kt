package com.stalkerapp.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun JsonElement?.asJsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

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
    private val vodCache = mutableMapOf<String, MutableMap<String, List<VodItem>>>()
    private val vodTotals = mutableMapOf<String, Int>()
    private val vodItemsById = mutableMapOf<String, MutableMap<Long, VodItem>>()
    private val epgCache = mutableMapOf<Long, List<EpgProgram>>()

    private fun vodKey(categoryId: Long, search: String): String =
        "$categoryId|${search.trim().lowercase()}"

    private val _status = MutableStateFlow<PortalStatus>(PortalStatus.Idle)
    val status: StateFlow<PortalStatus> = _status

    fun cooldownRemainingSeconds(): Long = client.cooldownRemainingSeconds()

    fun clearCooldown() = client.clearCooldown()

    fun cachedProfile(): Profile? {
        val portal = store.activePortal() ?: return null
        return profiles[portal.id]
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

    suspend fun loadEpg(profile: Profile, channelId: Long): List<EpgProgram> {
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
        val programs = data.mapNotNull { p ->
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
        epgCache[channelId] = programs
        return programs
    }

    /**
     * Fast "all" pass (Tivimate-style): requests a very large `per_page` so the
     * whole library comes back in one or two requests instead of slowly paging
     * with cooldown. Returns the items plus the portal-reported total (0 if unknown).
     */
    suspend fun fetchAllVod(profile: Profile, perPage: Int = 100000): Pair<List<VodItem>, Int> {
        val out = mutableListOf<VodItem>()
        var total = 0
        var page = 1
        var guard = 0
        while (guard < 64) {
            guard++
            val (list, t) = fetchVodPage(profile, 0, page, perPage)
            if (t > 0) total = t
            if (list.isEmpty()) break
            out += list
            if (total > 0 && out.size >= total) break
            page++
        }
        return out to total
    }

    /** Pages a single VOD category fully and returns its items (category stamped). */
    suspend fun fetchVodCategory(profile: Profile, catId: Long, perPage: Int = 5000): List<VodItem> {
        val out = mutableListOf<VodItem>()
        var page = 1
        var guard = 0
        while (guard < 4000) {
            guard++
            val (list, total) = fetchVodPage(profile, catId, page, perPage)
            if (list.isEmpty()) {
                if (page > 1) break
                page++
                continue
            }
            out += list.map { if (it.categoryId == 0L) it.copy(categoryId = catId.toInt()) else it }
            if (total > 0 && out.size >= total) break
            page++
        }
        return out
    }

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

        val body = buildMap {
            put("page", page.toString())
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
        vodCache.clear()
        vodTotals.clear()
        vodItemsById.clear()
        epgCache.clear()
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
            while (guard < 5000) {
                guard++
                val (list, total) = fetchVodPage(profile, catId, page, pp)
                if (unitTotal == 0 && total > 0) {
                    unitTotal = total
                    totalPagesEst += (total / pp) + 1
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
            // Reliable fallback: page every known category.
            cats.forEach { runCatching { pageUnit(it.id, perPage) } }
        } else {
            // Top up series coverage cheaply.
            cats.filter { it.id in seriesCatIds }.forEach { runCatching { pageUnit(it.id, perPage) } }
        }
        return all.values.toList()
    }

    private suspend fun fetchVodPage(
        profile: Profile,
        categoryId: Long,
        page: Int,
        perPage: Int
    ): Pair<List<VodItem>, Int> {
        repeat(3) { attempt ->
            try {
                val resp = client.request(
                    profile.baseUrl,
                    "portal.php?type=vod&action=get_ordered_list",
                    "POST",
                    tokenFor(profile),
                    buildMap {
                        put("page", page.toString())
                        // Omit the category param for the "all" query (catId <= 0).
                        if (categoryId > 0) put("category", categoryId.toString())
                        if (perPage > 0) put("per_page", perPage.toString())
                    }
                )
                return parseVodList(resp) to parseTotal(resp)
            } catch (e: StalkerException) {
                if (e.isCooldown) {
                    delay(client.cooldownRemainingMs() + 1000)
                } else throw e
            }
        }
        // Some portals reject `per_page`; retry once without it.
        if (perPage > 0) {
            runCatching {
                val resp = client.request(
                    profile.baseUrl,
                    "portal.php?type=vod&action=get_ordered_list",
                    "POST",
                    tokenFor(profile),
                    buildMap {
                        put("page", page.toString())
                        if (categoryId > 0) put("category", categoryId.toString())
                    }
                )
                return parseVodList(resp) to parseTotal(resp)
            }
        }
        return Pair(emptyList<VodItem>(), 0)
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

    suspend fun loadSeasons(profile: Profile, vodId: Long): List<Season> {
        val resp = client.request(
            profile.baseUrl,
            "portal.php?type=vod&action=get_season_list",
            "POST",
            tokenFor(profile),
            mapOf("movie_id" to vodId.toString())
        )
        return parseSeasons(resp)
    }

    suspend fun loadEpisodes(profile: Profile, vodId: Long, seasonId: Long): List<Episode> {
        val resp = client.request(
            profile.baseUrl,
            "portal.php?type=vod&action=get_episodes",
            "POST",
            tokenFor(profile),
            mapOf("movie_id" to vodId.toString(), "season_id" to seasonId.toString())
        )
        return parseEpisodes(resp)
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

        val cmdCandidates = buildList {
            if (!episode?.cmd.isNullOrBlank()) add(episode!!.cmd)
            if (item.cmd.isNotBlank()) add(item.cmd)
            add("/media/${item.id}.mp4")
        }

        for (cmd in cmdCandidates) {
            tryCreateLink(profile, cmd, item.id, seriesParam)?.let { return it }
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
            return fixLocalhost("$s/media/${item.id}.mp4", profile)
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
                archiveDuration = o["tv_archive_duration"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toIntOrNull() ?: 0
            )
        }.filter { it.id > 0 }
    }

    private fun parseVodList(el: JsonElement): List<VodItem> {
        return parseDataArray(el).mapNotNull { o ->
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
                genres = o["genres"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                actors = o["actors"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                isSeries = o["is_series"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toBooleanStrictOrNull() == true,
                cmd = o["cmd"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                selectedSeason = o["selected_season"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                seriesData = o["series_data"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty()
            )
        }.filter { it.id > 0 }
    }

    private fun parseVodInfo(el: JsonElement): VodItem? {
        val root = el as? JsonObject ?: return null
        val obj = (root["data"] as? JsonObject) ?: root
        val str = { key: String -> obj[key]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty() }
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
            genres = str("genre").ifBlank { str("genres") },
            actors = str("actors"),
            isSeries = str("is_series").toBooleanStrictOrNull() == true
        )
    }

    private fun parseSeasons(el: JsonElement): List<Season> {
        return parseDataArray(el).mapNotNull { o ->
            Season(
                id = o["id"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toLongOrNull() ?: 0,
                name = o["name"]?.asJsonPrimitiveOrNull()?.contentOrNull ?: ""
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
                cmd = o["cmd"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty()
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
