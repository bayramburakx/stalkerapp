package com.stalkerapp.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

fun JsonElement?.asJsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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
    private val vodCache = mutableMapOf<String, MutableMap<Long, List<VodItem>>>()
    private val vodItemsById = mutableMapOf<String, MutableMap<Long, VodItem>>()
    private val epgCache = mutableMapOf<Long, List<EpgProgram>>()

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
        val resp = client.request(
            base,
            "portal.php?type=itv&action=create_link",
            "POST",
            tokenFor(profile),
            mapOf(
                "cmd" to ch.cmd.ifEmpty { "http://localhost/ch/${ch.id}_" },
                "series" to "0"
            )
        )
        StalkerClient.urlFromJson(resp.jsonObject)?.let { return it }
        if (resp is JsonObject) {
            resp["js"]?.let {
                if (it is JsonObject) StalkerClient.urlFromJson(it)?.let { u -> return u }
            }
        }
        StalkerClient.parseCmd(ch.cmd)?.let { return rewriteLocalhost(it, profile) }
        val server = profile.serverAddress
        if (server.isBlank()) return ""
        val s = if (server.startsWith("http")) server else "http://$server"
        return "$s/${ch.id}"
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
        val portal = store.activePortal() ?: return null
        channelsCache[portal.id]?.values?.forEach { list ->
            list.firstOrNull { it.id == id }?.let { return it }
        }
        return null
    }

    fun findVodById(id: Long): VodItem? {
        val portal = store.activePortal() ?: return null
        return vodItemsById[portal.id]?.get(id)
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

    suspend fun loadVodList(profile: Profile, categoryId: Long = 0, page: Int = 1): List<VodItem> {
        vodCache.getOrPut(profile.portal?.id ?: "") { mutableMapOf() }
            .get(categoryId)?.let { if (page <= 1) return it }

        val body = buildMap {
            put("page", page.toString())
            if (categoryId > 0) put("category", categoryId.toString())
        }
        val resp = client.request(
            profile.baseUrl,
            "portal.php?type=vod&action=get_ordered_list",
            "POST",
            tokenFor(profile),
            body
        )
        val list = parseVodList(resp)
        if (page <= 1) {
            vodCache.getOrPut(profile.portal?.id ?: "") { mutableMapOf() }[categoryId] = list
        }
        list.forEach { vodItemsById.getOrPut(profile.portal?.id ?: "") { mutableMapOf() }[it.id] = it }
        return list
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
        if (!episode?.cmd.isNullOrBlank() && episode != null) {
            StalkerClient.parseCmd(episode.cmd)?.let { return rewriteLocalhost(it, profile) }
        }
        StalkerClient.parseCmd(item.cmd)?.let { return rewriteLocalhost(it, profile) }
        val resp = client.request(
            profile.baseUrl,
            "portal.php?type=vod&action=create_link",
            "POST",
            tokenFor(profile),
            mapOf(
                "vod_id" to item.id.toString(),
                "series" to if (item.isSeries) "1" else "0"
            )
        )
        StalkerClient.urlFromJson(resp.jsonObject)?.let { return rewriteLocalhost(it, profile) }
        if (resp is JsonObject) {
            resp["js"]?.let {
                if (it is JsonObject) StalkerClient.urlFromJson(it)?.let { u -> return rewriteLocalhost(u, profile) }
            }
        }
        val server = profile.serverAddress
        if (server.isNotBlank()) {
            val s = if (server.startsWith("http")) server else "http://$server"
            return "$s/${item.id}"
        }
        throw StalkerException("VOD akış URL'si alınamadı")
    }

    // ---------- Parsers ----------

    private fun parseProfile(resp: JsonElement, base: String, portal: Portal, mac: String): Profile {
        val obj = resp.jsonObject
        val serverInfo = obj["server_info"]?.jsonArray?.mapNotNull { e ->
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
            is JsonObject -> el["data"]?.jsonArray?.mapNotNull { it as? JsonObject }.orEmpty()
            is JsonArray -> el.mapNotNull { it as? JsonObject }
            else -> emptyList()
        }
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
                name = o["name"]?.asJsonPrimitiveOrNull()?.contentOrNull ?: "",
                originalName = o["o_name"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                sname = o["sname"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                poster = o["poster"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                description = o["description"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                year = o["year"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                director = o["director"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                country = o["country"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                rating = o["rating"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                genres = o["genres"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                isSeries = o["series"]?.asJsonPrimitiveOrNull()?.contentOrNull?.toBooleanStrictOrNull() == true,
                cmd = o["cmd"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                selectedSeason = o["selected_season"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty(),
                seriesData = o["series_data"]?.asJsonPrimitiveOrNull()?.contentOrNull.orEmpty()
            )
        }.filter { it.id > 0 }
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
