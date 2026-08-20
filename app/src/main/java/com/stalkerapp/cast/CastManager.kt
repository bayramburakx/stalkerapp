package com.stalkerapp.cast

import android.content.Context
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouter.RouteInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Chromecast cihaz keşfi ve bağlantı yönetimi.
 *
 * Oynatma tarafı [androidx.media3.cast.CastPlayer] (media3-cast) ile yapılır;
 * bu sınıf yalnızca MediaRouter üzerinden yakındaki yayın cihazlarını bulur,
 * kullanıcının seçtiği rotayı Cast framework'üne bırakır ve cihaz listesini
 * Compose'a [routes] StateFlow'u olarak sunar. `MediaRouteButton` yerine
 * kendi Compose dialog'umuz kullanıldığı için AppCompat teması gerekmez.
 */
object CastManager {

    /** Varsayılan medya alıcısı için cast kontrol kategorisi. */
    private const val CAST_CATEGORY = "com.google.android.gms.cast.CATEGORY_CAST"

    data class CastRoute(
        val id: String,
        val name: String,
        val selected: Boolean,
        val connecting: Boolean,
        internal val info: RouteInfo
    )

    private var router: MediaRouter? = null

    private val selector = MediaRouteSelector.Builder()
        .addControlCategory(CAST_CATEGORY)
        .build()

    private val _routes = MutableStateFlow<List<CastRoute>>(emptyList())
    val routes: StateFlow<List<CastRoute>> = _routes.asStateFlow()

    private val callback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, info: RouteInfo) = refresh()
        override fun onRouteRemoved(router: MediaRouter, info: RouteInfo) = refresh()
        override fun onRouteChanged(router: MediaRouter, info: RouteInfo) = refresh()
        override fun onRouteSelected(router: MediaRouter, info: RouteInfo, reason: Int) = refresh()
        override fun onRouteUnselected(router: MediaRouter, info: RouteInfo, reason: Int) = refresh()
    }

    fun init(context: Context) {
        if (router != null) return
        router = MediaRouter.getInstance(context.applicationContext)
    }

    /** Yayın cihazı aramayı başlatır (aktif tarama). Dialog açıkken çağrılır. */
    fun startDiscovery() {
        val r = router ?: return
        // Modern androidx.mediarouter'da keşif addCallback bayraklarıyla yapılır
        // (eski setDiscoveryRequest API'si kaldırıldı).
        r.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        refresh()
    }

    /** Yayın cihazı aramayı durdurur (pil tasarrufu). Dialog kapandığında çağrılır. */
    fun stopDiscovery() {
        val r = router ?: return
        r.removeCallback(callback)
        _routes.value = emptyList()
    }

    /** Seçilen cihaza bağlan — Cast framework'ü oturumu kurar, CastPlayer devralır. */
    fun connect(route: CastRoute) {
        com.stalkerapp.playback.PlaybackManager.userInitiatedCast = true
        router?.selectRoute(route.info)
    }

    /** Bağlantıyı keser; CastPlayer `onCastSessionUnavailable` ile yerel oynatıcıya döner. */
    fun disconnect() {
        com.stalkerapp.playback.PlaybackManager.userInitiatedCast = false
        router?.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
    }

    private fun refresh() {
        val r = router ?: return
        _routes.value = r.routes
            .filter { it.matchesSelector(selector) && it.name?.isNotBlank() == true }
            .map { CastRoute(it.id, it.name.toString(), it.isSelected, it.isConnecting, it) }
    }
}
