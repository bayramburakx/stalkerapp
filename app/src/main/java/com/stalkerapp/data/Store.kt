package com.stalkerapp.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

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

    fun savePortal(portal: Portal) {
        val list = portals().toMutableList()
        val idx = list.indexOfFirst { it.id == portal.id }
        if (idx >= 0) list[idx] = portal else list.add(portal)
        savePortals(list)
    }

    fun deletePortal(id: String) {
        savePortals(portals().filterNot { it.id == id })
        if (activePortalId() == id) setActivePortalId(null)
    }

    fun settings(): Settings = runCatching {
        json.decodeFromString(Settings.serializer(), prefs.getString(KEY_SETTINGS, "").orEmpty())
    }.getOrDefault(Settings())

    fun saveSettings(settings: Settings) {
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(Settings.serializer(), settings)).apply()
    }

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

    fun saveVodProgress(id: Long, positionMs: Long, durationMs: Long) {
        val map = loadVodProgress().toMutableMap()
        map[id] = VodProgress(positionMs, durationMs)
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
        const val VOD_CATALOG_VERSION = 6
        private const val META_FILE = "meta.json"

        private const val KEY_PORTALS = "portals"
        private const val KEY_ACTIVE_PORTAL = "active_portal"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_FAVORITE_CHANNELS = "favorite_channels"
        private const val KEY_FAVORITE_VODS = "favorite_vods"
        private const val KEY_VOD_PROGRESS = "vod_progress"
        private const val KEY_VOD_DONE_CATS = "vod_done_cats"
    }
}

@kotlinx.serialization.Serializable
data class CatalogMetaFile(
    val categories: List<Genre>,
    val itemCount: Int,
    val ts: Long,
    val version: Int = 1
)

@kotlinx.serialization.Serializable
data class VodProgress(val positionMs: Long, val durationMs: Long)
