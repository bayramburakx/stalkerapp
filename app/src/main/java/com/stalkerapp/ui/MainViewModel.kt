package com.stalkerapp.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stalkerapp.StalkerApp
import com.stalkerapp.data.Channel
import com.stalkerapp.data.ExternalVod
import com.stalkerapp.data.Genre
import com.stalkerapp.data.M3uParser
import com.stalkerapp.data.M3uSource
import com.stalkerapp.data.Portal
import com.stalkerapp.data.PortalRepository
import com.stalkerapp.data.PortalStatus
import com.stalkerapp.data.Profile
import com.stalkerapp.data.Settings
import com.stalkerapp.data.Store
import com.stalkerapp.data.UserList
import com.stalkerapp.data.UserProfile
import com.stalkerapp.data.VodItem
import com.stalkerapp.data.XtreamClient
import com.stalkerapp.data.XtreamSource
import com.stalkerapp.playback.PlaybackManager
import com.stalkerapp.util.isWifiConnected
import com.stalkerapp.util.L10n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainViewModel(private val app: StalkerApp) : ViewModel() {

    val store: Store get() = app.store
    val repository: PortalRepository get() = app.repository

    val portalStatus: StateFlow<PortalStatus> = repository.status

    private val _settings = MutableStateFlow(store.settings())
    val settings: StateFlow<Settings> = _settings

    private val _favorites = MutableStateFlow(store.favorites())
    val favorites: StateFlow<Set<String>> = _favorites

    // Yetişkin içerik kilidi: oturum boyunca PIN girildiyse kilit açık kalır.
    private val _adultUnlocked = MutableStateFlow(false)
    val adultUnlocked: StateFlow<Boolean> = _adultUnlocked

    /** PIN doğruysa yetişkin içeriği açar (oturumluk). */
    fun unlockAdult(pin: String): Boolean {
        val ok = pin == store.settings().pin && pin.isNotBlank()
        if (ok) _adultUnlocked.value = true
        return ok
    }

    fun lockAdult() {
        _adultUnlocked.value = false
    }

    private val _favoriteChannels = MutableStateFlow(store.favoriteChannels())
    val favoriteChannels: StateFlow<List<Channel>> = _favoriteChannels

    private val _favoriteVods = MutableStateFlow(store.favoriteVods())
    val favoriteVods: StateFlow<List<VodItem>> = _favoriteVods

    private val _cooldown = MutableStateFlow(repository.cooldownRemainingSeconds())
    val cooldownSeconds: StateFlow<Long> = _cooldown

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    // Ana sayfadaki "Canlı TV" önizlemesi için kanal listesi. Sekmeler arası
    // geçişlerde her seferinde ağ isteği yapılmaması için bir kez yüklenip
    // önbellekte tutulur (uygulama açık kaldığı sürece).
    private val _homeChannels = MutableStateFlow<List<Channel>?>(null)
    val homeChannels: StateFlow<List<Channel>?> = _homeChannels

    // İzlenme işaretleri (override + bölüm) her değişimde arttırılır; ekranlar
    // bu sürümü dinleyip Store'dan taze okur, böylece rozetler anlık güncellenir.
    private val _watchedVersion = MutableStateFlow(0)
    val watchedVersion: StateFlow<Int> = _watchedVersion

    fun toggleWatched(id: Long): Boolean {
        val added = store.toggleWatchedOverride(id)
        _watchedVersion.value++
        return added
    }

    fun bumpWatched() {
        _watchedVersion.value++
    }

    /** Medyanın izleme ilerlemesini siler (film progress + dizi bölüm progress). */
    fun removeProgress(itemId: Long) {
        store.clearVodProgress(itemId)
        // Dizi ise bu dizinin tüm bölüm ilerlemelerini de temizle.
        store.episodeProgress().keys.filter { it.startsWith("$itemId:") }.forEach { key ->
            store.clearEpisodeProgress(key)
        }
        _watchedVersion.value++
    }

    /** Medyayı ana sayfadaki "Son İzlenenler" / "İzlemeye Devam" bölümlerinden gizler. */
    fun hideFromHome(itemId: Long) {
        val current = store.settings().hiddenFromHome
        if (itemId in current) return
        store.saveSettings(store.settings().copy(hiddenFromHome = current + itemId))
        _settings.value = store.settings()
    }

    /** Store'daki tüm StateFlow'ları diske göre tazeler (geri yükleme / sıfırlama sonrası). */
    fun refreshFlows() {
        _settings.value = store.settings()
        _favorites.value = store.favorites()
        _favoriteChannels.value = store.favoriteChannels()
        _favoriteVods.value = store.favoriteVods()
        _watchLater.value = store.watchLater()
        _userLists.value = store.userLists()
        _profiles.value = store.userProfiles()
        _userProfile.value = store.activeUserProfile()
        _watchedVersion.value++
    }

    fun backupJson(): String = store.backupJson()

    /** Yedek JSON'u geri yükler; başarılıysa akışları tazeler ve true döner. */
    fun restoreBackup(json: String): Boolean {
        val ok = store.restoreJson(json)
        if (ok) refreshFlows()
        return ok
    }

    /** Tüm uygulama verilerini siler ve akışları sıfırlar. */
    fun clearAllData() {
        store.clearAllData()
        refreshFlows()
    }

    /** İzleme geçmişini (ilerlemeler + izlendi işaretleri) temizler. */
    fun clearWatchHistory() {
        store.clearWatchHistory()
        _watchedVersion.value++
    }

    suspend fun loadHomeChannels(profile: Profile?) {
        if (_homeChannels.value == null) {
            // M3U/Xtream aktifken profil null olabilir; kanallar aktif kaynaktan
            // yüklenir (Stalker, M3U veya Xtream fark etmez).
            _homeChannels.value =
                runCatching { loadChannelsForActiveSource(profile)?.second?.take(30) }.getOrNull()
        }
    }

    // ---------- Kullanıcı profilleri (çoklu profil) ----------

    private val _profiles = MutableStateFlow(store.userProfiles())
    val profiles: StateFlow<List<UserProfile>> = _profiles

    private val _userProfile = MutableStateFlow(store.activeUserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile

    fun saveUserProfile(profile: UserProfile) {
        store.saveUserProfile(profile)
        _userProfile.value = store.activeUserProfile()
        _profiles.value = store.userProfiles()
    }

    /** Yeni profil oluşturur ve aktif yapar. */
    fun addProfile(name: String, avatar: String) {
        store.addProfile(name, avatar)
        refreshProfileFlows()
    }

    /** Aktif profili değiştirir; favoriler/geçmiş o profilin verilerine geçer. */
    fun switchProfile(id: String) {
        store.switchProfile(id)
        refreshProfileFlows()
    }

    /** Profili siler; aktifse ilk kalan profile döner. */
    fun deleteProfile(id: String) {
        store.deleteUserProfile(id)
        refreshProfileFlows()
    }

    /** Profil değişince profil bazlı tüm akışları (favoriler, geçmiş, listeler) tazeler. */
    private fun refreshProfileFlows() {
        _profiles.value = store.userProfiles()
        _userProfile.value = store.activeUserProfile()
        _favorites.value = store.favorites()
        _favoriteChannels.value = store.favoriteChannels()
        _favoriteVods.value = store.favoriteVods()
        _watchLater.value = store.watchLater()
        _userLists.value = store.userLists()
        _watchedVersion.value++
    }

    // ---------- Sonra izle / özel listeler (Kütüphanem) ----------

    private val _watchLater = MutableStateFlow(store.watchLater())
    val watchLater: StateFlow<List<VodItem>> = _watchLater

    private val _userLists = MutableStateFlow(store.userLists())
    val userLists: StateFlow<List<UserList>> = _userLists

    fun toggleWatchLater(vod: VodItem): Boolean {
        val added = store.toggleWatchLater(vod)
        _watchLater.value = store.watchLater()
        return added
    }

    fun addUserList(name: String) {
        store.addUserList(name)
        _userLists.value = store.userLists()
    }

    fun deleteUserList(id: String) {
        store.deleteUserList(id)
        _userLists.value = store.userLists()
    }

    fun toggleInUserList(listId: String, vod: VodItem) {
        store.toggleInUserList(listId, vod)
        _userLists.value = store.userLists()
    }

    // ---------- M3U / Xtream kaynakları ----------
    // Oturum içinde bir kez yüklenip önbellekte tutulur (sekmeler arası geçişte
    // tekrar indirme olmaz).
    private val m3uCache = mutableMapOf<String, Pair<List<Genre>, List<Channel>>>()
    private val xtreamCache = mutableMapOf<String, Pair<List<Genre>, List<Channel>>>()

    // Silinen M3U kaynaklarının izleri: arka planda hâlâ süren indirme/parse
    // (testM3u, loadM3uChannels vb.) bitince kaynağı GERİ EKLEMESİN.
    private val deletedM3uIds = HashSet<String>()

    // M3U içerik dosyasına aynı anda iki coroutine yazmasın (134MB liste iki kez
    // indirilip bozuk dosya oluşturabiliyordu) — tekilleştirme kilidi.
    private val m3uContentMutex = Mutex()

    /** İçerik dosyasını (gerekirse indirerek) hazırlar; hazırsa true döner. */
    private suspend fun ensureM3uContentFile(source: M3uSource): Boolean = m3uContentMutex.withLock {
        val file = store.m3uContentFileFor(source.id)
        if (file.exists() && file.length() > 0) return@withLock true
        if (source.url.isBlank()) return@withLock false
        M3uParser.fetchToFile(source.url, file)
    }

    // Kaynak listeleri/anahtarları değişince ayarlar ekranının yeniden okuması
    // için sürüm sayaçı (kompozisyon içinde doğrudan prefs okumak donmalara yol
    // açıyordu; ekranlar bu sürümü dinleyip `remember` içinde taze okur).
    private val _sourcesVersion = MutableStateFlow(0)
    val sourcesVersion: StateFlow<Int> = _sourcesVersion

    fun m3uSources(): List<M3uSource> = store.m3uSources()
    fun xtreamSources(): List<XtreamSource> = store.xtreamSources()
    fun portals(): List<Portal> = store.portals()
    fun activePortalId(): String? = store.activePortalId()

    fun activeSourceKind(): String = store.activeSourceKind()
    fun activeSourceId(): String? = store.activeSourceId()

    /** Verilen kaynak türü Ayarlar'daki anahtarıyla kapalıysa null döner. */
    fun enabledSourceKind(): String? {
        val s = store.settings()
        return when {
            activeSourceKind() == "m3u" && s.m3uEnabled -> "m3u"
            activeSourceKind() == "xtream" && s.xtreamEnabled -> "xtream"
            activeSourceKind() == "stalker" && s.stalkerEnabled -> "stalker"
            activeSourceKind() != "stalker" && s.stalkerEnabled -> "stalker"
            else -> null
        }
    }

    fun setActiveSource(kind: String, id: String?) {
        val prevKind = store.activeSourceKind()
        val prevId = store.activeSourceId()
        store.setActiveSource(kind, id)
        _sourcesVersion.value++
        if (kind != "stalker") {
            _homeChannels.value = null
            // Stalker dışı kaynağa geçişte eski Stalker VOD katalogunu sıfırla.
            StalkerApp.instance.vodSyncManager.reset()
            // Farklı bir M3U/Xtream kaynağına geçildiyse eski dış kataloğu
            // temizle — aksi halde önceki kaynağın içeriği gösterilir (bayat).
            if (prevKind != kind || prevId != id) {
                _externalCatalog.value = VodCatalogState()
            }
            // M3U/Xtream kataloğunu arka planda kur (Filmler/Diziler anında dolu olsun).
            viewModelScope.launch { ensureExternalVodCatalog() }
        }
    }

    fun saveM3uSource(source: M3uSource) {
        // Silinen kaynak arka planda süren bir test/indirme tarafından yeniden
        // eklenmesin (kullanıcı "Sil" bastığında kaynak anlık kaybolmalı).
        if (source.id in deletedM3uIds) return
        // İçerik Store tarafında dosyaya yazılır; listeye boş content ile eklenir.
        // Yüzlerce MB'lık içerik ana iş parçacığını kilitlemesin diye dosya
        // yazımı arka planda (IO) yapılır — liste kaydı senkron güncellenir.
        if (source.content.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                store.saveM3uContent(source.id, source.content)
            }
        }
        val list = store.m3uSources().toMutableList()
        val idx = list.indexOfFirst { it.id == source.id }
        if (idx >= 0) list[idx] = source.copy(content = "") else list.add(source.copy(content = ""))
        store.saveM3uSources(list)
        m3uCache.remove(source.id)
        m3uVodCache.remove(source.id)
        _sourcesVersion.value++
        if (store.activeSourceKind() == "m3u" && store.activeSourceId() == source.id) {
            viewModelScope.launch { ensureExternalVodCatalog(force = true) }
        }
    }

    fun deleteM3uSource(id: String) {
        // Tombstone: süren arka plan işlemleri bitince kaynağı geri eklemesin.
        deletedM3uIds += id
        store.saveM3uSources(store.m3uSources().filterNot { it.id == id })
        store.deleteM3uContent(id)
        m3uCache.remove(id)
        m3uVodCache.remove(id)
        // Bu kaynağa ait izleme ilerlemelerini de sil — ana sayfada eski kaynağın
        // "Son İzlenenler / İzlemeye Devam" içeriği kalmasın.
        store.purgeProgressForSource("m3u|$id")
        if (store.activeSourceKind() == "m3u" && store.activeSourceId() == id) {
            // Aktif kaynak silindiyse Stalker'a dön (katalog akışını da tazeler).
            setActiveSource("stalker", null)
        }
        _sourcesVersion.value++
    }

    fun saveXtreamSource(source: XtreamSource) {
        val list = store.xtreamSources().toMutableList()
        val idx = list.indexOfFirst { it.id == source.id }
        if (idx >= 0) list[idx] = source else list.add(source)
        store.saveXtreamSources(list)
        xtreamCache.remove(source.id)
        xtreamVodCache.remove(source.id)
        _sourcesVersion.value++
        if (store.activeSourceKind() == "xtream" && store.activeSourceId() == source.id) {
            viewModelScope.launch { ensureExternalVodCatalog(force = true) }
        }
    }

    fun deleteXtreamSource(id: String) {
        store.saveXtreamSources(store.xtreamSources().filterNot { it.id == id })
        // Disk önbellekleri de sil — kaynak yeniden eklenirse bayat veri okunmasın.
        store.deleteExternalCaches(id)
        xtreamCache.remove(id)
        xtreamVodCache.remove(id)
        store.purgeProgressForSource("xtream|$id")
        if (store.activeSourceKind() == "xtream" && store.activeSourceId() == id) {
            // Aktif kaynak silindiyse Stalker'a dön (katalog akışını da tazeler).
            setActiveSource("stalker", null)
        }
        _sourcesVersion.value++
    }

    // ---------- Kaynak testi (Playlist & Kaynaklar) ----------

    /** Stalker portal bağlantısını dener; başarılıysa null, hata mesajı varsa döner. */
    suspend fun testPortal(portal: Portal): String? {
        // activate=false: test aktif portalı değiştirmez — aksi halde "Tüm
        // Kaynakları Test Et" uygulamayı sessizce son test edilen portala
        // geçirip katalogu sıfırlıyordu.
        return runCatching { repository.connect(portal, activate = false) }
            .fold(
                onSuccess = { null },
                onFailure = { it.message ?: it::class.simpleName ?: L10n.t(store.settings().language, "Bilinmeyen hata") }
            )
    }

    /** M3U URL'sini indirmeyi dener; içerik alınamazsa hata döner. */
    suspend fun testM3u(source: M3uSource): String? {
        if (source.url.isBlank()) return L10n.t(store.settings().language, "URL boş")
        // Kaynak bu sırada silinmiş olabilir — test etme, yeniden ekleme.
        if (store.m3uSources().none { it.id == source.id }) return null
        val file = store.m3uContentFileFor(source.id)
        val ok = withContext(Dispatchers.IO) { M3uParser.fetchToFile(source.url, file) }
        if (!ok) return L10n.t(store.settings().language, "İndirilemedi (URL geçersiz veya erişilemiyor)")
        // Parse + dosya yazımı ana iş parçacığını kilitlemesin (yüzlerce MB
        // liste ANR'a yol açıyordu) — tamamı IO'da yapılır.
        val count = withContext(Dispatchers.IO) {
            runCatching { M3uParser.parseFile(file, source.id).size }.getOrDefault(0)
        }
        val vodCount = withContext(Dispatchers.IO) {
            runCatching { M3uParser.parseVodFile(file, source.id).second.size }.getOrDefault(0)
        }
        if (count == 0 && vodCount == 0) {
            return L10n.t(store.settings().language, "Kanal veya VOD bulunamadı (geçerli M3U değil?)")
        }
        // İçerik dosyada; listedeki kayıt boş content ile güncellenir. Kaynak bu
        // sırada silindiyse saveM3uSource tombstone nedeniyle eklemez.
        saveM3uSource(source.copy(content = ""))
        // İçerik değişti: kaynak aktifse dış katalog bayat kalmasın (bir sonraki
        // açılışta yeniden kurulur).
        if (store.activeSourceKind() == "m3u" && store.activeSourceId() == source.id) {
            _externalCatalog.value = VodCatalogState()
        }
        return null
    }

    /** Xtream sunucusunu doğrular; başarılıysa null, hata döner. */
    suspend fun testXtream(source: XtreamSource): String? {
        return runCatching { XtreamClient().validate(source) }
            .fold(
                onSuccess = { ok -> if (ok) null else L10n.t(store.settings().language, "Kullanıcı adı/şifre geçersiz") },
                onFailure = { it.message ?: it::class.simpleName ?: L10n.t(store.settings().language, "Bilinmeyen hata") }
            )
    }

    /** M3U kaynağının kanallarını yükler (gerekirse indirir + çözer). */
    suspend fun loadM3uChannels(source: M3uSource): Pair<List<Genre>, List<Channel>> {
        m3uCache[source.id]?.let { return it }
        // 134MB listeyi her açılışta yeniden ayrıştırmamak için çözülen kanallar
        // disk önbelleğine yazılır (ilk açılışta oluşturulur, sonra hazır okunur).
        store.loadExternalChannelCache(source.id)?.let {
            m3uCache[source.id] = it
            return it
        }
        // İndirme, dosya okuma ve ayrıştırma ana iş parçacığını kilitleyip ANR'a
        // yol açıyordu (yüzlerce MB liste) — tamamı IO'da, dosyadan akışla.
        val result = withContext(Dispatchers.IO) {
            val ok = ensureM3uContentFile(source)
            val channels = if (ok) {
                runCatching { M3uParser.parseFile(store.m3uContentFileFor(source.id), source.id) }
                    .getOrDefault(emptyList())
            } else {
                M3uParser.parse(source.content, source.id)
            }
            // group-title'lar kategori olarak kullanılır.
            val groups = channels.map { it.tvGenreTitle }.filter { it.isNotBlank() }.distinct().sorted()
            val genres = listOf(Genre(0, "Tümü")) +
                groups.mapIndexed { i, g -> Genre((i + 1).toLong(), g) }
            store.saveExternalChannelCache(source.id, genres, channels)
            genres to channels
        }
        m3uCache[source.id] = result
        if (_externalCatalog.value.loadedCount == 0 && enabledSourceKind() == "m3u") {
            viewModelScope.launch { ensureExternalVodCatalog() }
        }
        return result
    }

    /** Xtream kaynağının canlı kanallarını yükler (kategoriler + kanallar). */
    suspend fun loadXtreamChannels(source: XtreamSource): Pair<List<Genre>, List<Channel>> {
        xtreamCache[source.id]?.let { return it }
        // Uygulama her açılışında ağ isteği tekrarlanmasın — disk önbelleği kullan.
        store.loadExternalChannelCache(source.id)?.let {
            xtreamCache[source.id] = it
            return it
        }
        val client = XtreamClient()
        val cats = client.liveCategories(source)
        val channels = client.liveStreams(source)
        val genres = listOf(Genre(0, "Tümü")) + cats
        val result = genres to channels
        xtreamCache[source.id] = result
        withContext(Dispatchers.IO) { store.saveExternalChannelCache(source.id, genres, channels) }
        return result
    }

    // ---------- M3U / Xtream VOD kataloğu ----------

    private val m3uVodCache = mutableMapOf<String, Pair<List<Genre>, List<VodItem>>>()
    private val xtreamVodCache = mutableMapOf<String, Pair<List<Genre>, List<VodItem>>>()

    /** M3U kaynağının film/dizi kataloğunu yükler (gerekirse içeriği indirir). */
    suspend fun loadM3uVod(source: M3uSource): Pair<List<Genre>, List<VodItem>> {
        m3uVodCache[source.id]?.let { return it }
        // Makul boyutlu M3U katalogları disk önbelleğinden okunur (devasa
        // 120k+ listeler önbelleklenmez, her açılışta parse edilir). Okuma
        // diskten + JSON/TSV çözme ana iş parçacığını kilitlemesin (ANR).
        withContext(Dispatchers.IO) { store.loadExternalVodCache(source.id) }?.let { cached ->
            if (cached.second.isNotEmpty()) {
                m3uVodCache[source.id] = cached
                return cached
            }
        }
        // İndirme/okuma + ayrıştırma ana iş parçacığını kilitlemesin — dosyadan
        // akışla, dev String kurulmadan çalışır.
        val result = withContext(Dispatchers.IO) {
            val ok = ensureM3uContentFile(source)
            val parsedOk: Boolean
            val r = if (ok) {
                val attempt = runCatching {
                    M3uParser.parseVodFile(store.m3uContentFileFor(source.id), source.id)
                }
                parsedOk = attempt.isSuccess
                attempt.getOrDefault(listOf(Genre(0, "Tümü")) to emptyList())
            } else {
                // İçerik dosyaya inemedi; source.content varsa ondan parse edilir.
                parsedOk = source.content.isNotBlank()
                M3uParser.parseVod(source.content, source.id)
            }
            // Boş katalog disk önbelleğine YALNIZCA ayrıştırma GERÇEKTEN başarılıysa
            // yazılır. Ayrıştırma hatası (ör. 400k+ öğelik listede bellek/okuma
            // sorunu) runCatching tarafından boş sonuca dönüştürülür; bu boş sonuç
            // önbelleğe yazılırsa "VOD bulunamadı" kalıcı olur ve kaynak düzelse
            // bile asla yüklenmez. Hata durumunda önbelleğe yazılmaz; böylece
            // sonraki açılışta yeniden deneme yapılır.
            if (parsedOk || r.second.isNotEmpty()) {
                store.saveExternalVodCache(source.id, r.first, r.second)
            }
            r
        }
        m3uVodCache[source.id] = result
        return result
    }

    /** Xtream kaynağının film + dizi kataloğunu yükler. */
    suspend fun loadXtreamVod(source: XtreamSource): Pair<List<Genre>, List<VodItem>> {
        xtreamVodCache[source.id]?.let { return it }
        // Katalog her açılışta yeniden çekilmesin (68k öğe ≈ 30MB) — disk önbelleği.
        // Okuma diskten + JSON çözme ana iş parçacığını kilitlemesin (ANR).
        withContext(Dispatchers.IO) { store.loadExternalVodCache(source.id) }?.let { cached ->
            if (cached.second.isNotEmpty()) {
                xtreamVodCache[source.id] = cached
                return cached
            }
        }
        // fetchVodCatalog ağ/HTTP hatasında XtreamApiException fırlatır;
        // ensureExternalVodCatalog bunu yakalayıp Error durumuna düşürür.
        val result = XtreamClient().fetchVodCatalog(source)
        xtreamVodCache[source.id] = result
        withContext(Dispatchers.IO) { store.saveExternalVodCache(source.id, result.first, result.second) }
        return result
    }

    /** Aktif M3U/Xtream kaynağının VOD kataloğunu yükler (kaynak bulunamazsa boş). */
    private suspend fun loadExternalCatalog(kind: String, id: String): Pair<List<Genre>, List<VodItem>> =
        when (kind) {
            "m3u" -> store.m3uSources().firstOrNull { it.id == id }?.let { loadM3uVod(it) }
            "xtream" -> store.xtreamSources().firstOrNull { it.id == id }?.let { loadXtreamVod(it) }
            else -> null
        } ?: (emptyList<Genre>() to emptyList<VodItem>())

    // Dış katalog kurulumu tekilleştirilir: Home + Filmler/Diziler aynı anda
    // çağırınca iki coroutine dev kataloğu iki kez parse edip OOM'a yol açabiliyordu.
    private val externalCatalogMutex = Mutex()

    /**
     * Aktif M3U/Xtream kaynağı için VOD kataloğunu kurup arayüze yayınlar.
     * Filmler/Diziler sekmeleri ve ana sayfa bölümleri bu akışı okur.
     * Geçici ağ hatasında bir kez yeniden dener; yine başarısızsa eski hazır
     * katalog varsa onu korur (ekranda "VOD senkron hatası" görünmez), yoksa
     * Error durumuna düşer.
     */
    suspend fun ensureExternalVodCatalog(force: Boolean = false) {
        val kind = enabledSourceKind()
        if (kind != "m3u" && kind != "xtream") return
        val ready = _externalCatalog.value.status == VodCatalogStatus.Ready &&
            _externalCatalog.value.loadedCount > 0
        if (!force && ready) return
        externalCatalogMutex.withLock {
            // Kilit beklerken başka çağrı kataloğu kurmuş olabilir — yeniden kontrol et.
            val reReady = _externalCatalog.value.status == VodCatalogStatus.Ready &&
                _externalCatalog.value.loadedCount > 0
            if (!force && reReady) return@withLock
            val id = store.activeSourceId() ?: return@withLock
            val prev = _externalCatalog.value
            _externalCatalog.value = prev.copy(status = VodCatalogStatus.Syncing)
            var result: Pair<List<Genre>, List<VodItem>>? = null
            repeat(2) { attempt ->
                try {
                    result = loadExternalCatalog(kind, id)
                } catch (e: Exception) {
                    if (attempt == 0) delay(1500)
                }
            }
            if (result != null) {
                val (genres, items) = result!!
                // 400k+ öğelik M3U kataloğunda associateBy + kategori hesapları ana
                // iş parçacığını kilitleyip donmaya yol açıyordu — arka planda kurulur.
                val state = try {
                    withContext(Dispatchers.Default) {
                        VodCatalogState.of(
                            status = VodCatalogStatus.Ready,
                            doneCategories = genres.size,
                            totalCategories = genres.size,
                            loadedCount = items.size,
                            allItems = items,
                            categories = genres,
                            lastSync = System.currentTimeMillis()
                        )
                    }
                } catch (e: Exception) {
                    // Katalog kurulamadı (bellek vb.): Syncing'de takılı kalmasın,
                    // önceki hazır durumu koru ya da boş Ready yayınla.
                    if (prev.status == VodCatalogStatus.Ready && prev.loadedCount > 0) prev
                    else VodCatalogState.of(
                        status = VodCatalogStatus.Ready,
                        doneCategories = genres.size,
                        totalCategories = genres.size,
                        loadedCount = items.size,
                        allItems = emptyList(),
                        categories = genres,
                        lastSync = System.currentTimeMillis()
                    )
                }
                _externalCatalog.value = state
            } else {
                // Geçici hata: eski hazır katalog varsa Error'a düşürme, onu koru.
                _externalCatalog.value = if (prev.status == VodCatalogStatus.Ready && prev.loadedCount > 0) {
                    prev
                } else {
                    prev.copy(status = VodCatalogStatus.Error)
                }
            }
        }
    }

    /** M3U kaynağının içeriğini yeniden indirir ve katalogları tazeler. */
    fun refreshM3u(source: M3uSource) {
        viewModelScope.launch {
            val file = store.m3uContentFileFor(source.id)
            val ok = if (source.url.isNotBlank()) {
                withContext(Dispatchers.IO) { M3uParser.fetchToFile(source.url, file) }
            } else false
            if (ok) {
                // Disk önbellekleri bayat — sil ki yeni içerikle yeniden kurulsun.
                store.deleteExternalCaches(source.id)
                m3uCache.remove(source.id)
                m3uVodCache.remove(source.id)
                ensureExternalVodCatalog(force = true)
            }
        }
    }

    /** Xtream kaynağının önbelleklerini temizleyip katalogları yeniden çeker. */
    fun refreshXtream(source: XtreamSource) {
        viewModelScope.launch {
            // Disk önbellekleri bayat — sil ki yeniden çekilsin.
            store.deleteExternalCaches(source.id)
            xtreamCache.remove(source.id)
            xtreamVodCache.remove(source.id)
            ensureExternalVodCatalog(force = true)
        }
    }

    /**
     * "Açılışta son kanalı oynat" ayarı açıksa son izlenen canlı kanalı otomatik
     * başlatır. Yalnızca kaydedilen kaynak hâlâ aktifse çalışır (kaynak değişmişse
     * sessizce atlanır); hata durumunda da sessizce vazgeçer.
     */
    fun resumeLastLiveChannelIfEnabled(profile: Profile?) {
        if (!store.settings().resumeLastChannel) return
        val last = store.lastLiveChannel() ?: return
        val parts = last.split('|')
        if (parts.size < 3) return
        val kind = parts[0]
        val sourceId = parts[1]
        if (kind != store.activeSourceKind()) return
        if (sourceId != (store.activeSourceId() ?: "")) return
        val channelId = parts[2].toLongOrNull() ?: return
        viewModelScope.launch {
            val loaded = loadChannelsForActiveSource(profile) ?: return@launch
            val channels = loaded.second
            val idx = channels.indexOfFirst { it.id == channelId }
            if (idx >= 0) {
                PlaybackManager.playChannel(channels, idx, profile)
            }
        }
    }

    /**
     * Ana ekran widget'ı / derin bağlantıdan gelen kanal id'sini oynatır.
     * Kaynak hâlâ aktifse kanalı bulup başlatır (hata durumunda sessizce geçer).
     */
    fun playChannelById(channelId: Long) {
        if (channelId <= 0) return
        viewModelScope.launch {
            val profile = repository.cachedProfile()
            val loaded = loadChannelsForActiveSource(profile) ?: return@launch
            val channels = loaded.second
            val idx = channels.indexOfFirst { it.id == channelId }
            if (idx >= 0) {
                PlaybackManager.playChannel(channels, idx, profile)
            }
        }
    }

    /** Aktif kaynağın kanallarını yükler (Stalker profil veya m3u/xtream). */
    suspend fun loadChannelsForActiveSource(profile: Profile?): Pair<List<Genre>, List<Channel>>? {
        val kind = enabledSourceKind() ?: return null
        val id = store.activeSourceId()
        return when (kind) {
            "m3u" -> store.m3uSources().firstOrNull { it.id == id }?.let { loadM3uChannels(it) }
            "xtream" -> store.xtreamSources().firstOrNull { it.id == id }?.let { loadXtreamChannels(it) }
            else -> {
                val p = profile ?: return null
                val cats = runCatching { repository.loadGenres(p) }.getOrDefault(emptyList())
                val channels = runCatching { repository.loadChannels(p, 0) }.getOrDefault(emptyList())
                listOf(Genre(0, "Tümü")) + cats to channels
            }
        }
    }

    // ---------- VOD catalog (background sync) ----------
    // Stalker kataloğu, uygulama ömrü boyunca yaşayan VodSyncManager'a aittir;
    // M3U/Xtream kataloğu ise aktif kaynağa göre burada kurulur. [vodCatalog]
    // aktif kaynak türüne göre doğru akışı döndürür; ekranlar bunu her zaman
    // koleksiyonlar — böylece Filmler/Diziler sekmeleri M3U ve Xtream'de de dolu olur.
    private val _externalCatalog = MutableStateFlow(VodCatalogState())
    val externalCatalog: StateFlow<VodCatalogState> = _externalCatalog

    val vodCatalog: StateFlow<VodCatalogState>
        get() = when (enabledSourceKind()) {
            "m3u", "xtream" -> _externalCatalog
            else -> StalkerApp.instance.vodSyncManager.progress
        }

    fun syncVodCatalog(profile: Profile, force: Boolean = false) {
        // M3U/Xtream aktifken katalog burada kurulur; Stalker portal için
        // ayrı arka plan yöneticisi kullanılır. "Şimdi Senkronize Et" butonu
        // aktif kaynağın kataloğunu tazelemelidir.
        when (enabledSourceKind()) {
            "m3u", "xtream" -> viewModelScope.launch { ensureExternalVodCatalog(force = true) }
            else -> {
                StalkerApp.instance.vodSyncManager.ensureSynced(profile, force)
                VodSyncService.start(app)
            }
        }
    }

    /**
     * Ensures the VOD catalog is available. On app launch this just loads the
     * persisted disk cache (instant, NO network sync) when it is already
     * complete, so the user is not forced to re-sync every time. A background
     * sync (in a foreground service, so it survives being backgrounded or closed)
     * only runs when there is no complete cache yet, or to resume an
     * interrupted sync from its checkpoint.
     */
    fun syncVodIfNeeded(profile: Profile) {
        val portalId = profile.portal?.id ?: return
        // Completion is decided by the catalog meta file (written only at the
        // end of a successful sync). A running/interrupted sync has chunks but
        // no meta yet, so it resumes from its chunks instead of restarting.
        val meta = store.loadVodCatalogMeta(portalId)
        val staleCatalog = meta != null && meta.version < Store.VOD_CATALOG_VERSION
        if (meta != null && !staleCatalog) {
            StalkerApp.instance.vodSyncManager.publishCached(profile)
            return
        }
        // Kütüphane & İçerik ayarları: otomatik senkron kapalıysa ya da
        // "yalnızca Wi-Fi" açıkken Wi-Fi yoksa arka plan senkronu başlatılmaz
        // (kullanıcı isterse "Şimdi Senkronize Et" ile manuel başlatır).
        if (!shouldAutoSyncVod()) return
        StalkerApp.instance.vodSyncManager.ensureSynced(profile, force = staleCatalog)
        VodSyncService.start(app)
    }

    /** VOD otomatik senkronu şu an çalışabilir mi? (autoSyncVod + wifiOnlySync) */
    fun shouldAutoSyncVod(): Boolean {
        val s = store.settings()
        if (!s.autoSyncVod) return false
        if (s.wifiOnlySync && !app.isWifiConnected()) return false
        return true
    }

    fun resetVodCatalog() {
        StalkerApp.instance.vodSyncManager.reset()
        StalkerApp.instance.store.clearVodCatalog(profileId())
    }

    private fun profileId(): String {
        return StalkerApp.instance.repository.cachedProfile()?.portal?.id ?: ""
    }

    init {
        viewModelScope.launch {
            while (true) {
                _cooldown.value = repository.cooldownRemainingSeconds()
                delay(1000)
            }
        }
    }

    suspend fun connect(portal: Portal): Result<Profile> =
        runCatching { repository.connect(portal) }

    fun saveSettings(s: Settings) {
        store.saveSettings(s)
        _settings.value = s
    }

    fun savePortal(portal: Portal) {
        store.savePortal(portal)
    }

    fun deletePortal(id: String) {
        store.deletePortal(id)
        // Portal silinince o portalın izleme ilerlemelerini de temizle.
        store.purgeProgressForSource("stalker|$id")
    }

    /** Switches the active portal, reconnects, and re-syncs the VOD catalog. */
    suspend fun switchPortal(portal: Portal): Result<Unit> = runCatching {
        val prevId = store.activePortalId()
        store.setActivePortalId(portal.id)
        repository.clearCaches()
        try {
            repository.connect(portal)
        } catch (e: Exception) {
            // Bağlantı başarısızsa önceki portalı geri yükle — aksi halde uygulama
            // aktif portalı bozuk/erişilemez birine sabitler ve her şey boş görünür.
            store.setActivePortalId(prevId)
            repository.clearCaches()
            repository.restoreProfileFromDisk()
            throw e
        }
        resetVodCatalog()
        repository.cachedProfile()?.let { syncVodIfNeeded(it) }
    }

    fun launchSwitch(portal: Portal, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            switchPortal(portal).onFailure { showMessage(L10n.t(store.settings().language, "Portal değiştirilemedi") + ": ${it.message}") }
            onDone()
        }
    }

    fun toggleFavorite(key: String): Boolean {
        val added = store.toggleFavorite(key)
        _favorites.value = store.favorites()
        _favoriteChannels.value = store.favoriteChannels()
        _favoriteVods.value = store.favoriteVods()
        return added
    }

    fun toggleFavoriteChannel(channel: Channel): Boolean {
        val added = store.toggleFavoriteChannel(channel)
        _favoriteChannels.value = store.favoriteChannels()
        _favorites.value = store.favorites()
        return added
    }

    fun toggleFavoriteVod(vod: VodItem): Boolean {
        val added = store.toggleFavoriteVod(vod)
        _favoriteVods.value = store.favoriteVods()
        _favorites.value = store.favorites()
        return added
    }

    fun isFavoriteChannel(id: Long): Boolean = store.isFavoriteChannel(id)
    fun isFavoriteVod(id: Long): Boolean = store.isFavoriteVod(id)

    fun clearCooldown() {
        repository.clearCooldown()
        _cooldown.value = 0
    }

    fun showMessage(msg: String?) {
        _statusMessage.value = msg
    }
}

enum class VodCatalogStatus { Idle, Syncing, Ready, Error }

/**
 * Katalog durumu. `byId` ve `seriesCategoryIds` yapım sırasında bir kez
 * önceden hesaplanır: 80k+ öğeli bir katalogda bunları her erişimde yeniden
 * üretmek (associateBy / kategori taraması) ana iş parçacığını kilitler ve
 * sayfa/sekme geçişlerinde donmaya yol açar.
 */
data class VodCatalogState(
    val status: VodCatalogStatus = VodCatalogStatus.Idle,
    val doneCategories: Int = 0,
    val totalCategories: Int = 0,
    val loadedCount: Int = 0,
    val allItems: List<VodItem> = emptyList(),
    val movies: List<VodItem> = emptyList(),
    val series: List<VodItem> = emptyList(),
    val categories: List<Genre> = emptyList(),
    val portalTotal: Int = 0,
    val lastSync: Long = 0,
    val byId: Map<Long, VodItem> = emptyMap(),
    val seriesCategoryIds: Set<Long> = emptySet()
) {
    val isSeriesItem: (VodItem) -> Boolean = { item ->
        if (com.stalkerapp.data.ExternalVod.isXtreamVod(item.id)) false
        else if (com.stalkerapp.data.ExternalVod.isXtreamSeries(item.id)) true
        else if (item.id in com.stalkerapp.data.PortalRepository.SERIES_ID_BASE until com.stalkerapp.data.ExternalVod.XTREAM_VOD_BASE) true
        else item.isSeries ||
            (item.seriesRef.isNotBlank() && item.seriesRef != "[]" && item.seriesRef != "0" && item.seriesRef != "null") ||
            (item.seriesData.isNotBlank() && item.seriesData != "[]" && item.seriesData != "0" && item.seriesData != "null") ||
            (item.selectedSeason.isNotBlank() && item.selectedSeason != "0" && item.selectedSeason != "null") ||
            item.categoryId in seriesCategoryIds
    }

    companion object {
        val seriesKeywords = listOf(
            "dizi", "series", "serial", "diziler", "show", "tv show", "season", "sezon",
            "serien", "seriale", "telenovela", "anime", "exxen", "blutv", "gain", "tod",
            "tabii", "netflix dizi", "yerli dizi", "yabanci dizi", "yabancı dizi"
        )

        fun of(
            status: VodCatalogStatus = VodCatalogStatus.Idle,
            doneCategories: Int = 0,
            totalCategories: Int = 0,
            loadedCount: Int = 0,
            allItems: List<VodItem> = emptyList(),
            categories: List<Genre> = emptyList(),
            portalTotal: Int = 0,
            lastSync: Long = 0
        ): VodCatalogState {
            val seriesCatIds = categories
                .filter { c -> seriesKeywords.any { kw -> c.title.contains(kw, ignoreCase = true) } }
                .map { it.id }
                .toSet()

            val isSeries: (VodItem) -> Boolean = { item ->
                if (com.stalkerapp.data.ExternalVod.isXtreamVod(item.id)) false
                else if (com.stalkerapp.data.ExternalVod.isXtreamSeries(item.id)) true
                else if (item.id in com.stalkerapp.data.PortalRepository.SERIES_ID_BASE until com.stalkerapp.data.ExternalVod.XTREAM_VOD_BASE) true
                else item.isSeries ||
                    (item.seriesRef.isNotBlank() && item.seriesRef != "[]" && item.seriesRef != "0" && item.seriesRef != "null") ||
                    (item.seriesData.isNotBlank() && item.seriesData != "[]" && item.seriesData != "0" && item.seriesData != "null") ||
                    (item.selectedSeason.isNotBlank() && item.selectedSeason != "0" && item.selectedSeason != "null") ||
                    item.categoryId in seriesCatIds
            }

            val mList = ArrayList<VodItem>(allItems.size)
            val sList = ArrayList<VodItem>(allItems.size / 4)
            for (item in allItems) {
                if (isSeries(item)) sList.add(item) else mList.add(item)
            }

            val finalSeriesCatIds = sList.map { it.categoryId }.toSet().ifEmpty { seriesCatIds }

            return VodCatalogState(
                status = status,
                doneCategories = doneCategories,
                totalCategories = totalCategories,
                loadedCount = loadedCount,
                allItems = allItems,
                movies = mList,
                series = sList,
                categories = categories,
                portalTotal = portalTotal,
                lastSync = lastSync,
                byId = allItems.associateBy { it.id },
                seriesCategoryIds = finalSeriesCatIds
            )
        }
    }
}

@Composable
fun rememberMainViewModel(app: StalkerApp): MainViewModel =
    viewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity) {
        MainViewModel(app)
    }
