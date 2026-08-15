package com.stalkerapp.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class Store(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("stalker_app_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun portals(): List<Portal> = runCatching {
        json.decodeFromString(
            ListSerializer(Portal.serializer()),
            prefs.getString(KEY_PORTALS, "[]").orEmpty()
        )
    }.getOrDefault(emptyList())

    fun savePortals(list: List<Portal>) {
        prefs.edit().putString(KEY_PORTALS, json.encodeToString(ListSerializer(Portal.serializer()), list)).apply()
    }

    fun activePortalId(): String? = prefs.getString(KEY_ACTIVE_PORTAL, null)

    fun setActivePortalId(id: String?) {
        prefs.edit().putString(KEY_ACTIVE_PORTAL, id).apply()
    }

    fun activePortal(): Portal? = activePortalId()?.let { id -> portals().firstOrNull { it.id == id } }

    // ---------- M3U / Xtream kaynakları ----------

    fun m3uSources(): List<M3uSource> = runCatching {
        json.decodeFromString(
            ListSerializer(M3uSource.serializer()),
            prefs.getString(KEY_M3U_SOURCES, "[]").orEmpty()
        )
    }.getOrDefault(emptyList())

    fun saveM3uSources(list: List<M3uSource>) {
        prefs.edit().putString(
            KEY_M3U_SOURCES,
            json.encodeToString(ListSerializer(M3uSource.serializer()), list)
        ).apply()
    }

    fun xtreamSources(): List<XtreamSource> = runCatching {
        json.decodeFromString(
            ListSerializer(XtreamSource.serializer()),
            prefs.getString(KEY_XTREAM_SOURCES, "[]").orEmpty()
        )
    }.getOrDefault(emptyList())

    fun saveXtreamSources(list: List<XtreamSource>) {
        prefs.edit().putString(
            KEY_XTREAM_SOURCES,
            json.encodeToString(ListSerializer(XtreamSource.serializer()), list)
        ).apply()
    }

    /** Aktif kanal kaynağı: "stalker", "m3u" veya "xtream". */
    fun activeSourceKind(): String = prefs.getString(KEY_ACTIVE_SOURCE_KIND, "stalker").orEmpty()

    fun activeSourceId(): String? = prefs.getString(KEY_ACTIVE_SOURCE_ID, null)

    fun setActiveSource(kind: String, id: String?) {
        prefs.edit()
            .putString(KEY_ACTIVE_SOURCE_KIND, kind)
            .apply {
                if (id == null) remove(KEY_ACTIVE_SOURCE_ID) else putString(KEY_ACTIVE_SOURCE_ID, id)
            }
            .apply()
    }

    fun isOnboardingDone(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(done: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    /** Persists the last connected profile so the app can start straight into Home
     *  without a login round-trip (auto-login). */
    fun saveProfile(profile: Profile) {
        val pid = profile.portal?.id ?: return
        val map = loadProfiles().toMutableMap()
        map[pid] = profile
        prefs.edit().putString(
            KEY_PROFILES,
            json.encodeToString(MapSerializer(String.serializer(), Profile.serializer()), map)
        ).apply()
    }

    fun loadProfiles(): Map<String, Profile> = runCatching {
        json.decodeFromString(
            MapSerializer(String.serializer(), Profile.serializer()),
            prefs.getString(KEY_PROFILES, "{}").orEmpty()
        )
    }.getOrDefault(emptyMap())

    fun loadProfile(portalId: String): Profile? = loadProfiles()[portalId]

    fun deleteProfile(portalId: String) {
        val map = loadProfiles().toMutableMap()
        if (map.remove(portalId) != null) {
            prefs.edit().putString(
                KEY_PROFILES,
                json.encodeToString(MapSerializer(String.serializer(), Profile.serializer()), map)
            ).apply()
        }
    }

    fun savePortal(portal: Portal) {
        val list = portals().toMutableList()
        val idx = list.indexOfFirst { it.id == portal.id }
        if (idx >= 0) list[idx] = portal else list.add(portal)
        savePortals(list)
    }

    fun deletePortal(id: String) {
        savePortals(portals().filterNot { it.id == id })
        deleteProfile(id)
        if (activePortalId() == id) setActivePortalId(null)
    }

    fun settings(): Settings = runCatching {
        json.decodeFromString(Settings.serializer(), prefs.getString(KEY_SETTINGS, "").orEmpty())
    }.getOrDefault(Settings())

    fun saveSettings(settings: Settings) {
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(Settings.serializer(), settings)).apply()
    }

    /** İndirilen harici EPG'nin disk önbellek dosyası (uygulama yeniden açılınca yeniden indirme yok). */
    fun epgCacheFile(): File = File(context.cacheDir, "external_epg.json")

    fun clearEpgCacheFile() {
        runCatching { epgCacheFile().delete() }
    }

    // ---------- Yedekleme / geri yükleme ----------

    /** İzleme geçmişini (film/bölüm ilerlemeleri + izlendi işaretleri) temizler. */
    fun clearWatchHistory() {
        prefs.edit()
            .remove(KEY_VOD_PROGRESS)
            .remove(KEY_EPISODE_PROGRESS)
            .remove(KEY_WATCHED_OVERRIDES)
            .remove(KEY_WATCHED_EPISODES)
            .apply()
    }

    /** Tüm uygulama verilerini siler (portallar, ayarlar, geçmiş, katalog, önbellek). */
    fun clearAllData() {
        prefs.edit().clear().apply()
        clearEpgCacheFile()
        runCatching {
            context.filesDir.listFiles().orEmpty()
                .filter { it.name.startsWith("vod_catalog_") || it.name.startsWith("vod_partial_") }
                .forEach { it.deleteRecursively() }
        }
    }

    /** Tüm kullanıcı verilerini tek JSON olarak dışa aktarır (yedek). */
    fun backupJson(): String {
        val data = LinkedHashMap<String, JsonElement>()
        JSON_BACKUP_KEYS.forEach { k ->
            prefs.getString(k, null)?.let { raw ->
                runCatching { data[k] = json.parseToJsonElement(raw) }
            }
        }
        PLAIN_BACKUP_KEYS.forEach { k ->
            prefs.getString(k, null)?.let { data[k] = JsonPrimitive(it) }
        }
        if (prefs.contains(KEY_ONBOARDING_DONE)) {
            data[KEY_ONBOARDING_DONE] = JsonPrimitive(prefs.getBoolean(KEY_ONBOARDING_DONE, false))
        }
        return json.encodeToString(AppBackup.serializer(), AppBackup(version = 1, data = data))
    }

    /** Yedek JSON'u geri yükler. Başarılıysa true döner. */
    fun restoreJson(jsonStr: String): Boolean = runCatching {
        val backup = json.decodeFromString(AppBackup.serializer(), jsonStr)
        if (backup.data.isEmpty()) return@runCatching false
        prefs.edit().apply {
            clear()
            backup.data.forEach { (k, v) ->
                when (k) {
                    KEY_ONBOARDING_DONE -> putBoolean(k, (v as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: true)
                    in PLAIN_BACKUP_KEYS -> putString(k, (v as? JsonPrimitive)?.contentOrNull.orEmpty())
                    else -> putString(k, v.toString())
                }
            }
        }.apply()
        clearEpgCacheFile()
        true
    }.getOrDefault(false)

    // ---------- Kullanıcı profili ----------

    fun userProfile(): UserProfile = runCatching {
        json.decodeFromString(UserProfile.serializer(), prefs.getString(KEY_USER_PROFILE, "").orEmpty())
    }.getOrDefault(UserProfile())

    fun saveUserProfile(profile: UserProfile) {
        prefs.edit().putString(KEY_USER_PROFILE, json.encodeToString(UserProfile.serializer(), profile)).apply()
    }

    // ---------- Sonra izle (Kütüphanem) ----------

    fun watchLater(): List<VodItem> = runCatching {
        json.decodeFromString(ListSerializer(VodItem.serializer()), prefs.getString(KEY_WATCH_LATER, "[]").orEmpty())
    }.getOrDefault(emptyList())

    fun isWatchLater(id: Long): Boolean = watchLater().any { it.id == id }

    fun toggleWatchLater(vod: VodItem): Boolean {
        val list = watchLater().toMutableList()
        val idx = list.indexOfFirst { it.id == vod.id }
        val added = if (idx >= 0) {
            list.removeAt(idx); false
        } else {
            list.add(0, vod); true
        }
        prefs.edit().putString(KEY_WATCH_LATER, json.encodeToString(ListSerializer(VodItem.serializer()), list)).apply()
        return added
    }

    // ---------- Özel listeler (Kütüphanem) ----------

    fun userLists(): List<UserList> = runCatching {
        json.decodeFromString(ListSerializer(UserList.serializer()), prefs.getString(KEY_USER_LISTS, "[]").orEmpty())
    }.getOrDefault(emptyList())

    fun saveUserLists(list: List<UserList>) {
        prefs.edit().putString(KEY_USER_LISTS, json.encodeToString(ListSerializer(UserList.serializer()), list)).apply()
    }

    fun addUserList(name: String): UserList {
        val list = userLists().toMutableList()
        val newList = UserList(
            id = "ul_" + System.currentTimeMillis().toString() + name.hashCode().toString().takeLast(4),
            name = name
        )
        list.add(newList)
        saveUserLists(list)
        return newList
    }

    fun deleteUserList(id: String) {
        saveUserLists(userLists().filterNot { it.id == id })
    }

    fun toggleInUserList(listId: String, vod: VodItem): Boolean {
        val lists = userLists().toMutableList()
        val idx = lists.indexOfFirst { it.id == listId }
        if (idx < 0) return false
        val items = lists[idx].itemIds.toMutableList()
        val added = if (vod.id in items) {
            items.remove(vod.id); false
        } else {
            items.add(vod.id); true
        }
        lists[idx] = lists[idx].copy(itemIds = items)
        saveUserLists(lists)
        return added
    }

    fun isInUserList(listId: String, itemId: Long): Boolean =
        userLists().firstOrNull { it.id == listId }?.itemIds?.contains(itemId) == true

    private fun favoritesKey(): String = prefs.getString(KEY_FAVORITES, "[]").orEmpty()

    fun favorites(): Set<String> = runCatching {
        json.decodeFromString(ListSerializer(String.serializer()), favoritesKey()).toSet()
    }.getOrDefault(emptySet())

    private fun saveFavorites(set: Set<String>) {
        prefs.edit().putString(KEY_FAVORITES, json.encodeToString(ListSerializer(String.serializer()), set.toList())).apply()
    }

    fun isFavorite(key: String): Boolean = key in favorites()

    fun favoriteChannels(): List<Channel> = runCatching {
        json.decodeFromString(ListSerializer(Channel.serializer()), prefs.getString(KEY_FAVORITE_CHANNELS, "[]").orEmpty())
    }.getOrDefault(emptyList())

    fun saveFavoriteChannels(list: List<Channel>) {
        prefs.edit().putString(KEY_FAVORITE_CHANNELS, json.encodeToString(ListSerializer(Channel.serializer()), list)).apply()
        val favKeys = favorites().filterNot { it.startsWith("ch:") }.toMutableSet()
        list.forEach { favKeys.add("ch:${it.id}") }
        saveFavorites(favKeys)
    }

    fun isFavoriteChannel(id: Long): Boolean =
        isFavorite("ch:$id") || favoriteChannels().any { it.id == id }

    fun toggleFavoriteChannel(channel: Channel): Boolean {
        val list = favoriteChannels().toMutableList()
        val idx = list.indexOfFirst { it.id == channel.id }
        val added = if (idx >= 0) {
            list.removeAt(idx)
            false
        } else {
            list.add(0, channel)
            true
        }
        saveFavoriteChannels(list)
        return added
    }

    fun favoriteVods(): List<VodItem> = runCatching {
        json.decodeFromString(ListSerializer(VodItem.serializer()), prefs.getString(KEY_FAVORITE_VODS, "[]").orEmpty())
    }.getOrDefault(emptyList())

    fun saveFavoriteVods(list: List<VodItem>) {
        prefs.edit().putString(KEY_FAVORITE_VODS, json.encodeToString(ListSerializer(VodItem.serializer()), list)).apply()
        val favKeys = favorites().filterNot { it.startsWith("vod:") }.toMutableSet()
        list.forEach { favKeys.add("vod:${it.id}") }
        saveFavorites(favKeys)
    }

    fun isFavoriteVod(id: Long): Boolean =
        isFavorite("vod:$id") || favoriteVods().any { it.id == id }

    fun toggleFavoriteVod(vod: VodItem): Boolean {
        val list = favoriteVods().toMutableList()
        val idx = list.indexOfFirst { it.id == vod.id }
        val added = if (idx >= 0) {
            list.removeAt(idx)
            false
        } else {
            list.add(0, vod)
            true
        }
        saveFavoriteVods(list)
        return added
    }

    fun toggleFavorite(key: String): Boolean {
        val favs = favorites().toMutableSet()
        val added = if (key in favs) {
            favs.remove(key); false
        } else {
            favs.add(key); true
        }
        saveFavorites(favs)
        if (key.startsWith("ch:") && !added) {
            val id = key.removePrefix("ch:").toLongOrNull()
            if (id != null) saveFavoriteChannels(favoriteChannels().filterNot { it.id == id })
        } else if (key.startsWith("vod:") && !added) {
            val id = key.removePrefix("vod:").toLongOrNull()
            if (id != null) saveFavoriteVods(favoriteVods().filterNot { it.id == id })
        }
        return added
    }

    // ---------- VOD catalog (chunked persistence) ----------
    // The catalog is stored as one small JSON file per category plus a meta
    // file, instead of a single giant blob. A full 80k+ library would otherwise
    // be a 100MB+ single-file write/read that easily fails (OOM) on devices,
    // silently losing the catalog and forcing a full re-sync on every launch.

    private fun catalogDir(portalId: String): File =
        File(context.filesDir, "vod_catalog_$portalId")

    /** Writes (or overwrites) one category's items as a small chunk file. */
    fun saveVodCategoryChunk(portalId: String, catId: Long, items: List<VodItem>) {
        runCatching {
            val dir = catalogDir(portalId)
            dir.mkdirs()
            File(dir, "$catId.json").writeText(
                json.encodeToString(ListSerializer(VodItem.serializer()), items)
            )
        }
    }

    /** Reads all saved category chunks (catId -> items). */
    fun loadVodCatalogChunks(portalId: String): Map<Long, List<VodItem>> = runCatching {
        val dir = catalogDir(portalId)
        if (!dir.exists()) return@runCatching emptyMap()
        dir.listFiles().orEmpty()
            .filter { it.isFile && it.extension == "json" && it.name != META_FILE }
            .mapNotNull { f ->
                f.nameWithoutExtension.toLongOrNull()?.let { catId ->
                    val items = runCatching {
                        json.decodeFromString(ListSerializer(VodItem.serializer()), f.readText())
                    }.getOrDefault(emptyList())
                    catId to items
                }
            }
            .toMap()
    }.getOrDefault(emptyMap())

    /** Writes the completion meta (categories, item count, version, timestamp). */
    fun saveVodCatalogMeta(portalId: String, categories: List<Genre>, itemCount: Int) {
        runCatching {
            val dir = catalogDir(portalId)
            dir.mkdirs()
            File(dir, META_FILE).writeText(
                json.encodeToString(
                    CatalogMetaFile.serializer(),
                    CatalogMetaFile(categories, itemCount, System.currentTimeMillis(), VOD_CATALOG_VERSION)
                )
            )
        }
    }

    fun loadVodCatalogMeta(portalId: String): CatalogMetaFile? = runCatching {
        val file = File(catalogDir(portalId), META_FILE)
        if (!file.exists()) return@runCatching null
        json.decodeFromString(CatalogMetaFile.serializer(), file.readText())
    }.getOrNull()

    /** Removes all catalog data (chunks, meta, legacy single-file, done-cats). */
    fun clearVodCatalog(portalId: String) {
        catalogDir(portalId).deleteRecursively()
        runCatching { File(context.filesDir, "vod_catalog_$portalId.json").delete() }
        runCatching { File(context.filesDir, "vod_partial_$portalId.json").delete() }
        clearVodCatalogDoneCats(portalId)
    }

    /** Saves the complete catalog as per-category chunks + meta (old signature kept). */
    fun saveVodCatalog(portalId: String, items: List<VodItem>, categories: List<Genre>) {
        items.groupBy { it.categoryId }.forEach { (catId, list) ->
            saveVodCategoryChunk(portalId, catId, list)
        }
        saveVodCatalogMeta(portalId, categories, items.size)
    }

    /** Version of the saved catalog meta, 0 if none exists. */
    fun loadVodCatalogVersion(portalId: String): Int =
        loadVodCatalogMeta(portalId)?.version ?: 0

    /** Merged catalog (items from all chunks, categories + timestamp from meta). */
    fun loadVodCatalog(portalId: String): Triple<List<VodItem>, List<Genre>, Long>? {
        val meta = loadVodCatalogMeta(portalId) ?: return null
        val items = loadVodCatalogChunks(portalId).values
            .flatten()
            .distinctBy { it.id }
        return Triple(items, meta.categories, meta.ts)
    }

    fun saveVodCatalogDoneCats(portalId: String, doneCatIds: List<Long>) {
        prefs.edit().putString(KEY_VOD_DONE_CATS, json.encodeToString(ListSerializer(Long.serializer()), doneCatIds)).apply()
    }

    fun loadVodCatalogDoneCats(portalId: String): Set<Long> = runCatching {
        json.decodeFromString(ListSerializer(Long.serializer()), prefs.getString(KEY_VOD_DONE_CATS, "[]").orEmpty()).toSet()
    }.getOrDefault(emptySet())

    fun clearVodCatalogDoneCats(portalId: String) {
        prefs.edit().remove(KEY_VOD_DONE_CATS).apply()
    }

    // ---------- İzlenme işaretleri ----------

    /** VOD'lar için elle "izlendi/izlenmedi" işaretleri (katalog id). */
    fun watchedOverrides(): Set<Long> = runCatching {
        json.decodeFromString(ListSerializer(Long.serializer()), prefs.getString(KEY_WATCHED_OVERRIDES, "[]").orEmpty()).toSet()
    }.getOrDefault(emptySet())

    fun isWatchedOverride(id: Long): Boolean = id in watchedOverrides()

    fun toggleWatchedOverride(id: Long): Boolean {
        val set = watchedOverrides().toMutableSet()
        val added = if (id in set) {
            set.remove(id); false
        } else {
            set.add(id); true
        }
        prefs.edit().putString(
            KEY_WATCHED_OVERRIDES,
            json.encodeToString(ListSerializer(Long.serializer()), set.toList())
        ).apply()
        return added
    }

    /** İzlenen bölümler: "<vodId>:<sezon>:<bölümNo>" anahtarları. */
    fun watchedEpisodes(): Set<String> = runCatching {
        json.decodeFromString(ListSerializer(String.serializer()), prefs.getString(KEY_WATCHED_EPISODES, "[]").orEmpty()).toSet()
    }.getOrDefault(emptySet())

    fun isEpisodeWatched(key: String): Boolean = key in watchedEpisodes()

    fun markEpisodeWatched(key: String) {
        val set = watchedEpisodes().toMutableSet()
        set.add(key)
        prefs.edit().putString(
            KEY_WATCHED_EPISODES,
            json.encodeToString(ListSerializer(String.serializer()), set.toList())
        ).apply()
    }

    fun clearEpisodeWatched(key: String) {
        val set = watchedEpisodes().toMutableSet()
        if (set.remove(key)) {
            prefs.edit().putString(
                KEY_WATCHED_EPISODES,
                json.encodeToString(ListSerializer(String.serializer()), set.toList())
            ).apply()
        }
    }

    /** Bölüm bazlı izleme ilerlemesi ("<vodId>:<sezon>:<bölümNo>" -> ilerleme). */
    fun episodeProgress(): Map<String, VodProgress> = runCatching {
        json.decodeFromString(
            MapSerializer(String.serializer(), VodProgress.serializer()),
            prefs.getString(KEY_EPISODE_PROGRESS, "{}").orEmpty()
        )
    }.getOrDefault(emptyMap())

    fun saveEpisodeProgress(key: String, positionMs: Long, durationMs: Long) {
        val map = episodeProgress().toMutableMap()
        map[key] = VodProgress(positionMs, durationMs, System.currentTimeMillis())
        prefs.edit().putString(
            KEY_EPISODE_PROGRESS,
            json.encodeToString(MapSerializer(String.serializer(), VodProgress.serializer()), map)
        ).apply()
    }

    fun clearEpisodeProgress(key: String) {
        val map = episodeProgress().toMutableMap()
        if (map.remove(key) != null) {
            prefs.edit().putString(
                KEY_EPISODE_PROGRESS,
                json.encodeToString(MapSerializer(String.serializer(), VodProgress.serializer()), map)
            ).apply()
        }
    }

    fun saveVodProgress(id: Long, positionMs: Long, durationMs: Long) {
        val map = loadVodProgress().toMutableMap()
        map[id] = VodProgress(positionMs, durationMs, System.currentTimeMillis())
        prefs.edit().putString(
            KEY_VOD_PROGRESS,
            json.encodeToString(MapSerializer(Long.serializer(), VodProgress.serializer()), map)
        ).apply()
    }

    fun loadVodProgress(): Map<Long, VodProgress> = runCatching {
        json.decodeFromString(
            MapSerializer(Long.serializer(), VodProgress.serializer()),
            prefs.getString(KEY_VOD_PROGRESS, "{}").orEmpty()
        )
    }.getOrDefault(emptyMap())

    fun clearVodProgress(id: Long) {
        val map = loadVodProgress().toMutableMap()
        if (map.remove(id) != null) {
            prefs.edit().putString(
                KEY_VOD_PROGRESS,
                json.encodeToString(MapSerializer(Long.serializer(), VodProgress.serializer()), map)
            ).apply()
        }
    }

    companion object {
        /** Bump to force a full re-sync of catalogs saved by older app versions. */
        const val VOD_CATALOG_VERSION = 7
        private const val META_FILE = "meta.json"

        private const val KEY_PORTALS = "portals"
        private const val KEY_ACTIVE_PORTAL = "active_portal"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_FAVORITE_CHANNELS = "favorite_channels"
        private const val KEY_FAVORITE_VODS = "favorite_vods"
        private const val KEY_VOD_PROGRESS = "vod_progress"
        private const val KEY_VOD_DONE_CATS = "vod_done_cats"
        private const val KEY_WATCHED_OVERRIDES = "watched_overrides"
        private const val KEY_WATCHED_EPISODES = "watched_episodes"
        private const val KEY_EPISODE_PROGRESS = "episode_progress"
        private const val KEY_M3U_SOURCES = "m3u_sources"
        private const val KEY_XTREAM_SOURCES = "xtream_sources"
        private const val KEY_ACTIVE_SOURCE_KIND = "active_source_kind"
        private const val KEY_ACTIVE_SOURCE_ID = "active_source_id"
        private const val KEY_USER_PROFILE = "user_profile"
        private const val KEY_WATCH_LATER = "watch_later"
        private const val KEY_USER_LISTS = "user_lists"

        /** Yedeklemeye dahil edilen tüm SharedPreferences anahtarları. */
        private val BACKUP_KEYS = setOf(
            KEY_PORTALS, KEY_ACTIVE_PORTAL, KEY_ONBOARDING_DONE, KEY_PROFILES,
            KEY_SETTINGS, KEY_FAVORITES, KEY_FAVORITE_CHANNELS, KEY_FAVORITE_VODS,
            KEY_VOD_PROGRESS, KEY_WATCHED_OVERRIDES, KEY_WATCHED_EPISODES,
            KEY_EPISODE_PROGRESS, KEY_M3U_SOURCES, KEY_XTREAM_SOURCES,
            KEY_ACTIVE_SOURCE_KIND, KEY_ACTIVE_SOURCE_ID, KEY_USER_PROFILE,
            KEY_WATCH_LATER, KEY_USER_LISTS
        )

        /** JSON olarak kodlanmış (putString + serialize) yedek anahtarları. */
        private val JSON_BACKUP_KEYS = BACKUP_KEYS - setOf(
            KEY_ACTIVE_PORTAL, KEY_ACTIVE_SOURCE_KIND, KEY_ACTIVE_SOURCE_ID, KEY_ONBOARDING_DONE
        )

        /** Düz metin olarak saklanan yedek anahtarları. */
        private val PLAIN_BACKUP_KEYS = setOf(
            KEY_ACTIVE_PORTAL, KEY_ACTIVE_SOURCE_KIND, KEY_ACTIVE_SOURCE_ID
        )
    }
}

@kotlinx.serialization.Serializable
data class AppBackup(
    val version: Int = 1,
    /** Anahtar -> JSON değer (SharedPreferences anahtarları). */
    val data: Map<String, JsonElement> = emptyMap()
)

@kotlinx.serialization.Serializable
data class CatalogMetaFile(
    val categories: List<Genre>,
    val itemCount: Int,
    val ts: Long,
    val version: Int = 1
)

@kotlinx.serialization.Serializable
data class VodProgress(val positionMs: Long, val durationMs: Long, val lastUpdated: Long = 0)
