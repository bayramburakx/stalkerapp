package com.stalkerapp.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Store(context: Context) {

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

    fun toggleFavorite(key: String): Boolean {
        val favs = favorites().toMutableSet()
        val added = if (key in favs) {
            favs.remove(key); false
        } else {
            favs.add(key); true
        }
        saveFavorites(favs)
        return added
    }

    companion object {
        private const val KEY_PORTALS = "portals"
        private const val KEY_ACTIVE_PORTAL = "active_portal"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_FAVORITES = "favorites"
    }
}
