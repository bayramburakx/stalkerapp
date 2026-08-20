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
import com.stalkerapp.util.L10n

class Store(private val context: Context) {

    /**
     * Aktif hesabın UID'si. Boş/null = misafir (giriş yapılmamış) depo.
     * Farklı hesapların verileri ayrı SharedPreferences dosyalarında tutulur
     * (Stremio tarzı: her hesabın kendi profilleri/favorileri/geçmişi olur).
     * Hesap değişince [setAccount] çağrılmalı; ardından akışlar tazelenmeli.
     */
    private var accountUid: String? = null

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(
            accountUid?.let { "stalker_app_prefs_$it" } ?: "stalker_app_prefs",
            Context.MODE_PRIVATE
        )
    private val json = Json { ignoreUnknownKeys = true }

    /** Aktif hesap UID'si (null = misafir). */
    fun currentAccount(): String? = accountUid

    /** Veri deposunu hesaba bağlar; null = misafir depoya döner. */
    fun setAccount(uid: String?) {
        accountUid = uid
    }

    /** Hesap bağımsız global depo (onboarding bayrağı vb. cihaz seviyesi veriler). */
    private val globalPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("stalker_app_global", Context.MODE_PRIVATE)
    }

    /** Misafir (hesaba bağlı olmayan) veri deposu boş mu? İlk girişte taşınacak veri var mı diye bakılır. */
    fun guestHasData(): Boolean {
        val guest = context.getSharedPreferences("stalker_app_prefs", Context.MODE_PRIVATE)
        return guest.all.isNotEmpty()
    }

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
        val list = json.decodeFromString(
            ListSerializer(M3uSource.serializer()),
            prefs.getString(KEY_M3U_SOURCES, "[]").orEmpty()
        )
        // Eski sürümlerde içerik SharedPreferences'ta saklanırdı; büyük listeler
        // her okumada ana iş parçacığını kilitleyip sayfa geçişlerini yavaşlatırdı.
        // İçerik dosyaya taşınıp listeden boşaltılır (bir kez, sonra otomatik).
        val big = list.filter { it.content.length > 64 * 1024 }
        if (big.isNotEmpty()) {
            big.forEach { saveM3uContent(it.id, it.content) }
            saveM3uSources(list)
        }
        list
    }.getOrDefault(emptyList())

    /**
     * M3U listesi içeriği (indirilen #EXTM3U metni) SharedPreferences yerine
     * diskte ayrı bir dosyada tutulur. Büyük listeler (100MB+) SharedPreferences'a
     * yazılamaz (ANR/OOM) ve her okumada ana iş parçacığını kilitlerdi; dosya
     * hem kaydetmeyi hem okumayı güvenli yapar. Eski sürümlerden gelen
     * `content` alanındaki veriler ilk okumada dosyaya taşınır (geriye dönük).
     */
    fun m3uContentFile(id: String): File = File(context.filesDir, "m3u_content_$id.m3u")

    /** M3U içerik dosyasının yolunu döndürür (streaming indirme/parse için). */
    fun m3uContentFileFor(id: String): File = m3uContentFile(id)

    fun saveM3uContent(id: String, content: String) {
        if (content.isBlank()) return
        runCatching { m3uContentFile(id).writeText(content) }
    }

    /** Kayıtlı M3U içeriğini döner; yoksa/boşsa eski `content` alanına bakar. */
    fun loadM3uContent(source: M3uSource): String {
        if (source.id.isNotBlank()) {
            runCatching {
                val f = m3uContentFile(source.id)
                if (f.exists()) return f.readText()
            }
        }
        return source.content
    }

    fun deleteM3uContent(id: String) {
        runCatching { m3uContentFile(id).delete() }
        deleteExternalCaches(id)
    }

    /** Harici kaynağın disk önbelleklerini (kanal + katalog) temizler. */
    fun deleteExternalCaches(sourceId: String) {
        runCatching { externalCacheFile("ch", sourceId).delete() }
        runCatching { externalCacheFile("vod", sourceId).delete() }
        runCatching { externalCacheTsvFile("ch", sourceId).delete() }
        runCatching { externalCacheTsvFile("vod", sourceId).delete() }
    }

    // ---------- Harici (M3U/Xtream) katalog & kanal disk önbelleği ----------
    // Uygulama her açıldığında Xtream kataloğu (68k öğe) ve M3U kanal listesi
    // yeniden çekilip çözülüyordu ("her açılışta yenileniyor"). Çözülen katalog
    // dosyaya yazılır; açılışta önbellekten okunur, ağ isteği yapılmaz.

    private fun externalCacheFile(kind: String, sourceId: String): File =
        File(context.filesDir, "ext_${kind}_$sourceId.json")

    private fun externalCacheTsvFile(kind: String, sourceId: String): File =
        File(context.filesDir, "ext_${kind}_$sourceId.tsv")

    // Sınıflandırma/parse mantığı değişince eski önbellekler bayat kalır (ör:
    // eski sürümlerde M3U film/dizileri canlı TV'ye atıyordu ve boş katalog
    // önbelleğe yazılmıştı). Sürüm eşleşmezse önbellek yok sayılır, yeniden
    // ayrıştırılır.
    private val EXTERNAL_CACHE_VERSION = 5

    fun saveExternalVodCache(sourceId: String, genres: List<Genre>, items: List<VodItem>) {
        runCatching {
            // Boş katalog önbelleğe yazılmaz — yanlış sınıflandırma sonrası
            // "VOD yok" kalıcı hale gelmesin; bir sonraki açılışta yeniden parse edilsin.
            if (items.isEmpty()) return
            // Devasa M3U katalogları (400k+ öğe): tek dev JSON'a dönüştürmek bellek
            // taşırır — satır satır TSV önbelleğe yazılır (her satır bir öğe).
            // Okuma da satır satırdır; böylece ikinci açılışta 134MB liste yeniden
            // ayrıştırılmaz ve Filmler/Diziler anında dolu görünür.
            if (items.size > 120_000) {
                saveExternalVodCacheTsv(sourceId, genres, items)
                return
            }
            val f = externalCacheFile("vod", sourceId)
            f.writeText(json.encodeToString(
                ExternalVodCacheDto.serializer(),
                ExternalVodCacheDto(EXTERNAL_CACHE_VERSION, genres, items)
            ))
        }
    }

    fun loadExternalVodCache(sourceId: String): Pair<List<Genre>, List<VodItem>>? = runCatching {
        val f = externalCacheFile("vod", sourceId)
        if (f.exists()) {
            val dto = json.decodeFromString(ExternalVodCacheDto.serializer(), f.readText())
            // Bayat önbellek (eski sürüm formatı veya farklı sınıflandırma) yok sayılır.
            if (dto.version != EXTERNAL_CACHE_VERSION) return null
            if (dto.items.isEmpty()) return null
            return dto.genres to dto.items
        }
        val tsv = externalCacheTsvFile("vod", sourceId)
        if (tsv.exists()) return loadExternalVodCacheTsv(tsv)
        null
    }.getOrNull()

    // ---------- TSV (satır satır) VOD önbelleği — devasa M3U katalogları ----------
    // Satır: id\tcategoryId\tisSeries(0/1)\tname\tposter\tcmd
    // Metin alanları kaçar: \\ → \\, tab → \t, yeni satır → \n, satır başı → \r.

    private fun tsvEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")

    private fun tsvUnescape(s: String): String {
        if ('\\' !in s) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> { sb.append('\t'); i += 2; continue }
                    'n' -> { sb.append('\n'); i += 2; continue }
                    'r' -> { sb.append('\r'); i += 2; continue }
                    '\\' -> { sb.append('\\'); i += 2; continue }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun saveExternalVodCacheTsv(sourceId: String, genres: List<Genre>, items: List<VodItem>) {
        val f = externalCacheTsvFile("vod", sourceId)
        f.bufferedWriter(Charsets.UTF_8).use { w ->
            // İlk satır: sürüm + kategori listesi (küçük — JSON).
            w.write("V\t")
            w.write(EXTERNAL_CACHE_VERSION.toString())
            w.write("\tG\t")
            w.write(json.encodeToString(ListSerializer(Genre.serializer()), genres))
            w.newLine()
            items.forEach { it ->
                w.write(it.id.toString())
                w.write("\t")
                w.write(it.categoryId.toString())
                w.write("\t")
                w.write(if (it.isSeries) "1" else "0")
                w.write("\t")
                w.write(tsvEscape(it.name))
                w.write("\t")
                w.write(tsvEscape(it.poster))
                w.write("\t")
                w.write(tsvEscape(it.cmd))
                w.newLine()
            }
        }
    }

    private fun loadExternalVodCacheTsv(f: File): Pair<List<Genre>, List<VodItem>>? {
        var genres = emptyList<Genre>()
        val items = ArrayList<VodItem>(200_000)
        f.bufferedReader(Charsets.UTF_8).use { r ->
            var first = true
            for (line in r.lineSequence()) {
                if (line.isBlank()) continue
                if (first) {
                    first = false
                    if (line.startsWith("V\t")) {
                        val parts = line.split('\t', limit = 3)
                        val v = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        // Bayat sürüm (veya eski "G\t" formatı) → önbelleği yok say.
                        if (v != EXTERNAL_CACHE_VERSION) return null
                        val g = parts.getOrNull(2) ?: continue
                        if (g.startsWith("G\t")) {
                            genres = runCatching {
                                json.decodeFromString(ListSerializer(Genre.serializer()), g.substring(2))
                            }.getOrDefault(emptyList())
                        }
                    } else if (line.startsWith("G\t")) {
                        // Sürümsüz eski format — bayat, yeniden ayrıştır.
                        return null
                    }
                    continue
                }
                val p = line.split('\t')
                if (p.size < 6) continue
                val id = p[0].toLongOrNull() ?: continue
                items.add(
                    VodItem(
                        id = id,
                        categoryId = p[1].toLongOrNull() ?: 0L,
                        name = tsvUnescape(p[3]),
                        originalName = tsvUnescape(p[3]),
                        poster = tsvUnescape(p[4]),
                        cmd = tsvUnescape(p[5]),
                        isSeries = p[2] == "1"
                    )
                )
            }
        }
        if (items.isEmpty()) return null
        return genres to items
    }

    fun saveExternalChannelCache(sourceId: String, genres: List<Genre>, channels: List<Channel>) {
        runCatching {
            if (channels.size > 50_000) return
            val f = externalCacheFile("ch", sourceId)
            f.writeText(json.encodeToString(ExternalChannelCacheDto.serializer(), ExternalChannelCacheDto(genres, channels)))
        }
    }

    fun loadExternalChannelCache(sourceId: String): Pair<List<Genre>, List<Channel>>? = runCatching {
        val f = externalCacheFile("ch", sourceId)
        if (!f.exists()) return null
        val dto = json.decodeFromString(ExternalChannelCacheDto.serializer(), f.readText())
        dto.genres to dto.channels
    }.getOrNull()

    /**
     * M3U kaynaklarını kaydeder; içerik alanı dosyaya yazılıp listeden
     * boşaltılır (SharedPreferences şişmesin). Eski içerik alanı da temizlenir.
     */
    fun saveM3uSources(list: List<M3uSource>) {
        list.forEach { s -> saveM3uContent(s.id, s.content) }
        prefs.edit().putString(
            KEY_M3U_SOURCES,
            json.encodeToString(ListSerializer(M3uSource.serializer()), list.map { it.copy(content = "") })
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

    /** Onboarding bayrağı cihaz seviyesinde global tutulur (hesaptan bağımsız). */
    fun isOnboardingDone(): Boolean = globalPrefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(done: Boolean) {
        globalPrefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
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

    // ---------- Kullanıcı profilleri (çoklu profil) ----------

    /**
     * Tüm profiller. İlk çağrıda eski tek-profil verisi "default" kimliğiyle
     * listeye taşınır (geriye dönük uyumluluk; mevcut veri kaybolmaz).
     */
    fun userProfiles(): List<UserProfile> {
        val stored = runCatching {
            json.decodeFromString(
                ListSerializer(UserProfile.serializer()),
                prefs.getString(KEY_USER_PROFILES, "").orEmpty()
            )
        }.getOrDefault(emptyList())
        if (stored.isNotEmpty()) return stored
        val legacy = runCatching {
            json.decodeFromString(UserProfile.serializer(), prefs.getString(KEY_USER_PROFILE, "").orEmpty())
        }.getOrDefault(UserProfile())
        val list = listOf(if (legacy.id.isBlank()) legacy.copy(id = DEFAULT_PROFILE_ID) else legacy)
        saveUserProfiles(list)
        return list
    }

    fun saveUserProfiles(list: List<UserProfile>) {
        prefs.edit().putString(
            KEY_USER_PROFILES,
            json.encodeToString(ListSerializer(UserProfile.serializer()), list)
        ).apply()
    }

    fun activeProfileId(): String =
        prefs.getString(KEY_ACTIVE_PROFILE, "").orEmpty().ifBlank { DEFAULT_PROFILE_ID }

    fun setActiveProfileId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE, id).apply()
    }

    /** Aktif profil. Liste boşsa (ilk kurulum) varsayılan profil döner. */
    fun activeUserProfile(): UserProfile =
        userProfiles().firstOrNull { it.id == activeProfileId() } ?: userProfiles().first()

    /**
     * Yeni profil oluşturur ve aktif yapar. İlk profil her zaman "default"
     * kimliğini alır (eski veri anahtarlarıyla eşleşir); sonrakiler benzersiz id alır.
     */
    fun addProfile(name: String, avatar: String): UserProfile {
        val id = if (userProfiles().none { it.id == DEFAULT_PROFILE_ID }) {
            DEFAULT_PROFILE_ID
        } else {
            "p_" + System.currentTimeMillis().toString(36) + "_" + (name.hashCode() and 0xFFFF).toString(16)
        }
        val p = UserProfile(id = id, name = name.ifBlank { L10n.t(settings().language, "İzleyici") }, avatar = avatar.ifBlank { "😀" })
        val list = userProfiles().toMutableList()
        list.add(p)
        saveUserProfiles(list)
        setActiveProfileId(id)
        return p
    }

    fun switchProfile(id: String) {
        if (userProfiles().any { it.id == id }) setActiveProfileId(id)
    }

    /** Kullanıcı profilini siler; aktifse ilk kalan profile geçer (son profil silinemez). */
    fun deleteUserProfile(id: String) {
        val list = userProfiles().filterNot { it.id == id }
        if (list.isEmpty()) return
        saveUserProfiles(list)
        if (activeProfileId() == id) setActiveProfileId(list.first().id)
        // O profilin favori/geçmiş/liste verilerini de temizle (yetim anahtar kalmasın).
        if (id != DEFAULT_PROFILE_ID) {
            prefs.edit().apply {
                PROFILE_SCOPED_BASES.forEach { base -> remove(base + "_p" + id) }
            }.apply()
        }
    }

    /**
     * Profil bazlı veriler için SharedPreferences anahtarı. Aktif profil
     * "default" (eski tek profil) ise anahtar değişmez; diğer profillerde
     * "_p<id>" son eki eklenir → her profilin kendi favorileri/geçmişi olur.
     */
    private fun scoped(key: String): String {
        val id = prefs.getString(KEY_ACTIVE_PROFILE, "").orEmpty()
        return if (id.isBlank() || id == DEFAULT_PROFILE_ID) key else "${key}_p$id"
    }

    // ---------- Yedekleme / geri yükleme ----------

    /** İzleme geçmişini (film/bölüm ilerlemeleri + izlendi işaretleri) temizler. */
    fun clearWatchHistory() {
        prefs.edit()
            .remove(scoped(KEY_VOD_PROGRESS))
            .remove(scoped(KEY_EPISODE_PROGRESS))
            .remove(scoped(KEY_WATCHED_OVERRIDES))
            .remove(scoped(KEY_WATCHED_EPISODES))
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

    // ---------- Kanal yönetimi (özel gruplar / sıralama / ad / logo) ----------

    fun channelCustomization(): ChannelCustomization =
        runCatching {
            json.decodeFromString(
                ChannelCustomization.serializer(),
                prefs.getString(KEY_CHANNEL_CUSTOMIZATION, "").orEmpty()
            )
        }.getOrDefault(ChannelCustomization())

    fun saveChannelCustomization(c: ChannelCustomization) {
        prefs.edit().putString(
            KEY_CHANNEL_CUSTOMIZATION,
            json.encodeToString(ChannelCustomization.serializer(), c)
        ).apply()
    }

    /** Son izlenen canlı kanal: "kaynakTürü|kaynakId|kanalId" biçiminde (açılışta devam için). */
    fun lastLiveChannel(): String? = prefs.getString(KEY_LAST_LIVE_CHANNEL, null)

    fun saveLastLiveChannel(kind: String, sourceId: String, channelId: Long) {
        prefs.edit().putString(KEY_LAST_LIVE_CHANNEL, "$kind|$sourceId|$channelId").apply()
    }

    // ---------- EPG program hatırlatıcıları ----------

    fun epgReminders(): List<EpgReminder> = runCatching {
        json.decodeFromString(
            ListSerializer(EpgReminder.serializer()),
            prefs.getString(KEY_EPG_REMINDERS, "[]").orEmpty()
        )
    }.getOrDefault(emptyList())

    fun saveEpgReminders(list: List<EpgReminder>) {
        prefs.edit().putString(
            KEY_EPG_REMINDERS,
            json.encodeToString(ListSerializer(EpgReminder.serializer()), list)
        ).apply()
    }

    /** Yeni hatırlatıcı ekler (aynı program için tekrarlamaz). */
    fun addEpgReminder(r: EpgReminder) {
        val list = epgReminders().toMutableList()
        if (list.any { it.channelId == r.channelId && it.startTs == r.startTs }) return
        list.add(r)
        saveEpgReminders(list)
    }

    fun removeEpgReminder(channelId: Long, startTs: Long) {
        saveEpgReminders(epgReminders().filterNot { it.channelId == channelId && it.startTs == startTs })
    }

    // ---------- Uygulama içi güncelleme bildirimi (cihaz seviyesinde global) ----------

    /** Kullanıcının "bir daha sorma" dediği sürüm (bu sürüm tekrar sorulmaz). */
    fun updateSkipVersion(): String = globalPrefs.getString(KEY_UPDATE_SKIP_VERSION, "").orEmpty()

    fun setUpdateSkipVersion(version: String) {
        globalPrefs.edit().putString(KEY_UPDATE_SKIP_VERSION, version).apply()
    }

    /** "Sonra hatırlat" seçilince bir sonraki soruş zamanı (epoch ms). */
    fun updateRemindTs(): Long = globalPrefs.getLong(KEY_UPDATE_REMIND_TS, 0L)

    fun setUpdateRemindTs(ts: Long) {
        globalPrefs.edit().putLong(KEY_UPDATE_REMIND_TS, ts).apply()
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
        // Profil bazlı (çoklu profil) veriler: "<temel>_p<id>" son ekli anahtarlar da yedeklenir.
        prefs.all.keys
            .filter { k -> PROFILE_SCOPED_BASES.any { k.startsWith("${it}_p") } }
            .forEach { k ->
                prefs.getString(k, null)?.let { raw ->
                    runCatching { data[k] = json.parseToJsonElement(raw) }
                }
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

    // ---------- Kullanıcı profili (eski tek-profil API'si, uyumluluk için korunur) ----------

    /** Aktif profil (eski API: artık çoklu profilin aktif olanını döndürür). */
    fun userProfile(): UserProfile = activeUserProfile()

    /**
     * Profili kaydeder/üzerine yazar ve listeye yansıtır. Kimlik boşsa "default"
     * kabul edilir. Tek profil varsa (ilk kurulum) otomatik aktif yapılır.
     */
    fun saveUserProfile(profile: UserProfile) {
        val withId = if (profile.id.isBlank()) profile.copy(id = DEFAULT_PROFILE_ID) else profile
        val list = userProfiles().toMutableList()
        val idx = list.indexOfFirst { it.id == withId.id }
        if (idx >= 0) list[idx] = withId else list.add(withId)
        saveUserProfiles(list)
        prefs.edit().putString(KEY_USER_PROFILE, json.encodeToString(UserProfile.serializer(), withId)).apply()
        if (list.size == 1) setActiveProfileId(withId.id)
    }

    // ---------- Sonra izle (Kütüphanem) ----------

    fun watchLater(): List<VodItem> = runCatching {
        json.decodeFromString(ListSerializer(VodItem.serializer()), prefs.getString(scoped(KEY_WATCH_LATER), "[]").orEmpty())
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
        prefs.edit().putString(scoped(KEY_WATCH_LATER), json.encodeToString(ListSerializer(VodItem.serializer()), list)).apply()
        return added
    }

    // ---------- Özel listeler (Kütüphanem) ----------

    fun userLists(): List<UserList> = runCatching {
        json.decodeFromString(ListSerializer(UserList.serializer()), prefs.getString(scoped(KEY_USER_LISTS), "[]").orEmpty())
    }.getOrDefault(emptyList())

    fun saveUserLists(list: List<UserList>) {
        prefs.edit().putString(scoped(KEY_USER_LISTS), json.encodeToString(ListSerializer(UserList.serializer()), list)).apply()
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

    private fun favoritesKey(): String = prefs.getString(scoped(KEY_FAVORITES), "[]").orEmpty()

    fun favorites(): Set<String> = runCatching {
        json.decodeFromString(ListSerializer(String.serializer()), favoritesKey()).toSet()
    }.getOrDefault(emptySet())

    private fun saveFavorites(set: Set<String>) {
        prefs.edit().putString(scoped(KEY_FAVORITES), json.encodeToString(ListSerializer(String.serializer()), set.toList())).apply()
    }

    fun isFavorite(key: String): Boolean = key in favorites()

    fun favoriteChannels(): List<Channel> = runCatching {
        json.decodeFromString(ListSerializer(Channel.serializer()), prefs.getString(scoped(KEY_FAVORITE_CHANNELS), "[]").orEmpty())
    }.getOrDefault(emptyList())

    fun saveFavoriteChannels(list: List<Channel>) {
        prefs.edit().putString(scoped(KEY_FAVORITE_CHANNELS), json.encodeToString(ListSerializer(Channel.serializer()), list)).apply()
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
        // Ana ekran widget'ı favorileri gösterir — değişince yenile.
        runCatching { com.stalkerapp.widget.FavoritesWidgetProvider.notifyChanged(context) }
        return added
    }

    fun favoriteVods(): List<VodItem> = runCatching {
        json.decodeFromString(ListSerializer(VodItem.serializer()), prefs.getString(scoped(KEY_FAVORITE_VODS), "[]").orEmpty())
    }.getOrDefault(emptyList())

    fun saveFavoriteVods(list: List<VodItem>) {
        prefs.edit().putString(scoped(KEY_FAVORITE_VODS), json.encodeToString(ListSerializer(VodItem.serializer()), list)).apply()
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
        val result = HashMap<Long, List<VodItem>>()
        dir.listFiles().orEmpty()
            .filter { it.isFile && it.extension == "json" && it.name != META_FILE }
            .forEach { f ->
                f.nameWithoutExtension.toLongOrNull()?.let { catId ->
                    try {
                        val items = json.decodeFromString(ListSerializer(VodItem.serializer()), f.readText())
                        result[catId] = items
                    } catch (_: Throwable) {}
                }
            }
        result
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

    /** Merged catalog (items streamed from chunks into single list to prevent OOM). */
    fun loadVodCatalog(portalId: String): Triple<List<VodItem>, List<Genre>, Long>? = runCatching {
        val meta = loadVodCatalogMeta(portalId) ?: return@runCatching null
        val dir = catalogDir(portalId)
        if (!dir.exists()) return@runCatching null
        val capacity = minOf(meta.count.coerceAtLeast(100), 200_000)
        val seen = HashSet<Long>(capacity)
        val items = ArrayList<VodItem>(capacity)
        dir.listFiles().orEmpty()
            .filter { it.isFile && it.extension == "json" && it.name != META_FILE }
            .forEach { f ->
                try {
                    val list = json.decodeFromString(ListSerializer(VodItem.serializer()), f.readText())
                    for (item in list) {
                        if (seen.add(item.id)) {
                            items.add(item)
                        }
                    }
                } catch (_: Throwable) {}
            }
        Triple(items, meta.categories, meta.ts)
    }.getOrNull()

    // Tamamlanan kategoriler portala (ve profile) göre ayrı tutulur — aksi halde
    // ikinci bir portala geçilince ilk portalın "tamam" listesi kendi kategorileriyle
    // karışır ve bazı kategoriler hiç çekilmeden katalog eksik kalırdı.
    private fun doneCatsKey(portalId: String): String = "${scoped(KEY_VOD_DONE_CATS)}:$portalId"

    fun saveVodCatalogDoneCats(portalId: String, doneCatIds: List<Long>) {
        prefs.edit().putString(doneCatsKey(portalId), json.encodeToString(ListSerializer(Long.serializer()), doneCatIds)).apply()
    }

    fun loadVodCatalogDoneCats(portalId: String): Set<Long> = runCatching {
        json.decodeFromString(ListSerializer(Long.serializer()), prefs.getString(doneCatsKey(portalId), "[]").orEmpty()).toSet()
    }.getOrDefault(emptySet())

    fun clearVodCatalogDoneCats(portalId: String) {
        prefs.edit().remove(doneCatsKey(portalId)).apply()
    }

    // ---------- İzlenme işaretleri ----------

    /** VOD'lar için elle "izlendi/izlenmedi" işaretleri (katalog id). */
    fun watchedOverrides(): Set<Long> = runCatching {
        json.decodeFromString(ListSerializer(Long.serializer()), prefs.getString(scoped(KEY_WATCHED_OVERRIDES), "[]").orEmpty()).toSet()
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
            scoped(KEY_WATCHED_OVERRIDES),
            json.encodeToString(ListSerializer(Long.serializer()), set.toList())
        ).apply()
        return added
    }

    /** İzlenen bölümler: "<vodId>:<sezon>:<bölümNo>" anahtarları. */
    fun watchedEpisodes(): Set<String> = runCatching {
        json.decodeFromString(ListSerializer(String.serializer()), prefs.getString(scoped(KEY_WATCHED_EPISODES), "[]").orEmpty()).toSet()
    }.getOrDefault(emptySet())

    fun isEpisodeWatched(key: String): Boolean = key in watchedEpisodes()

    fun markEpisodeWatched(key: String) {
        val set = watchedEpisodes().toMutableSet()
        set.add(key)
        prefs.edit().putString(
            scoped(KEY_WATCHED_EPISODES),
            json.encodeToString(ListSerializer(String.serializer()), set.toList())
        ).apply()
    }

    fun clearEpisodeWatched(key: String) {
        val set = watchedEpisodes().toMutableSet()
        if (set.remove(key)) {
            prefs.edit().putString(
                scoped(KEY_WATCHED_EPISODES),
                json.encodeToString(ListSerializer(String.serializer()), set.toList())
            ).apply()
        }
    }

    /** Bölüm bazlı izleme ilerlemesi ("<vodId>:<sezon>:<bölümNo>" -> ilerleme). */
    fun episodeProgress(): Map<String, VodProgress> = runCatching {
        json.decodeFromString(
            MapSerializer(String.serializer(), VodProgress.serializer()),
            prefs.getString(scoped(KEY_EPISODE_PROGRESS), "{}").orEmpty()
        )
    }.getOrDefault(emptyMap())

    fun saveEpisodeProgress(
        key: String,
        positionMs: Long,
        durationMs: Long,
        item: VodItem? = null,
        episodeLabel: String = ""
    ) {
        val map = episodeProgress().toMutableMap()
        val prev = map[key]
        map[key] = VodProgress(
            positionMs = positionMs,
            durationMs = durationMs,
            lastUpdated = System.currentTimeMillis(),
            // Dizi anlık görüntüsü: katalog senkronu olmasa da "İzlemeye Devam /
            // Son İzlenenler" bölüm kartını gösterebilsin.
            name = item?.name?.ifBlank { prev?.name.orEmpty() } ?: prev?.name.orEmpty(),
            poster = item?.poster?.ifBlank { prev?.poster.orEmpty() } ?: prev?.poster.orEmpty(),
            isSeries = true,
            categoryId = item?.categoryId ?: prev?.categoryId ?: 0,
            sourceKey = activeSourceKey(),
            episodeLabel = episodeLabel.ifBlank { prev?.episodeLabel.orEmpty() }
        )
        prefs.edit().putString(
            scoped(KEY_EPISODE_PROGRESS),
            json.encodeToString(MapSerializer(String.serializer(), VodProgress.serializer()), map)
        ).apply()
    }

    fun clearEpisodeProgress(key: String) {
        val map = episodeProgress().toMutableMap()
        if (map.remove(key) != null) {
            prefs.edit().putString(
                scoped(KEY_EPISODE_PROGRESS),
                json.encodeToString(MapSerializer(String.serializer(), VodProgress.serializer()), map)
            ).apply()
        }
    }

    /**
     * Aktif kaynağın anahtarı: "m3u|<id>", "xtream|<id>" veya "stalker|<portalId>".
     * İzleme ilerlemeleri bu anahtarla etiketlenir; kaynak silinince/değişince
     * ana sayfadaki "İzlemeye Devam / Son İzlenenler" eski kaynağın içeriğini
     * göstermez (kaynak bazlı izolasyon).
     */
    fun activeSourceKey(): String = when (activeSourceKind()) {
        "m3u" -> "m3u|" + (activeSourceId() ?: "")
        "xtream" -> "xtream|" + (activeSourceId() ?: "")
        else -> "stalker|" + (activePortalId() ?: "")
    }

    /** Bir kaynağa ait tüm izleme ilerlemelerini (film + bölüm) siler. */
    fun purgeProgressForSource(sourceKey: String) {
        if (sourceKey.isBlank()) return
        val vp = loadVodProgress().filterValues { it.sourceKey == sourceKey }
        if (vp.isNotEmpty()) {
            val rest = loadVodProgress().filterValues { it.sourceKey != sourceKey }
            prefs.edit().putString(
                scoped(KEY_VOD_PROGRESS),
                json.encodeToString(MapSerializer(Long.serializer(), VodProgress.serializer()), rest)
            ).apply()
        }
        val ep = episodeProgress().filterValues { it.sourceKey == sourceKey }
        if (ep.isNotEmpty()) {
            val rest = episodeProgress().filterValues { it.sourceKey != sourceKey }
            prefs.edit().putString(
                scoped(KEY_EPISODE_PROGRESS),
                json.encodeToString(MapSerializer(String.serializer(), VodProgress.serializer()), rest)
            ).apply()
        }
    }

    fun saveVodProgress(id: Long, positionMs: Long, durationMs: Long, item: VodItem? = null) {
        val map = loadVodProgress().toMutableMap()
        val prev = map[id]
        map[id] = VodProgress(
            positionMs = positionMs,
            durationMs = durationMs,
            lastUpdated = System.currentTimeMillis(),
            // Öğe anlık görüntüsü: katalog henüz senkronize olmasa bile ana sayfa
            // "İzlemeye Devam / Son İzlenenler" listelerini gösterebilsin (byId'ye
            // bağımlılık kalkar). Eski kayıttaki bilgiler korunur.
            name = item?.name?.ifBlank { prev?.name.orEmpty() } ?: prev?.name.orEmpty(),
            poster = item?.poster?.ifBlank { prev?.poster.orEmpty() } ?: prev?.poster.orEmpty(),
            isSeries = item?.isSeries ?: prev?.isSeries ?: false,
            categoryId = item?.categoryId ?: prev?.categoryId ?: 0,
            sourceKey = activeSourceKey()
        )
        prefs.edit().putString(
            scoped(KEY_VOD_PROGRESS),
            json.encodeToString(MapSerializer(Long.serializer(), VodProgress.serializer()), map)
        ).apply()
    }

    fun loadVodProgress(): Map<Long, VodProgress> = runCatching {
        json.decodeFromString(
            MapSerializer(Long.serializer(), VodProgress.serializer()),
            prefs.getString(scoped(KEY_VOD_PROGRESS), "{}").orEmpty()
        )
    }.getOrDefault(emptyMap())

    fun clearVodProgress(id: Long) {
        val map = loadVodProgress().toMutableMap()
        if (map.remove(id) != null) {
            prefs.edit().putString(
                scoped(KEY_VOD_PROGRESS),
                json.encodeToString(MapSerializer(Long.serializer(), VodProgress.serializer()), map)
            ).apply()
        }
    }

    companion object {
        /** Uygulama genelinde erişilebilen son Store örneği (NetworkConfig vb. için). */
        @Volatile var activeStore: Store? = null

        /** Bump to force a full re-sync of catalogs saved by older app versions. */
        const val VOD_CATALOG_VERSION = 9
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
        private const val KEY_USER_PROFILES = "user_profiles"
        private const val KEY_ACTIVE_PROFILE = "active_profile"
        private const val KEY_WATCH_LATER = "watch_later"
        private const val KEY_USER_LISTS = "user_lists"
        private const val KEY_CHANNEL_CUSTOMIZATION = "channel_customization"
        private const val KEY_LAST_LIVE_CHANNEL = "last_live_channel"
        private const val KEY_EPG_REMINDERS = "epg_reminders"
        private const val KEY_UPDATE_SKIP_VERSION = "update_skip_version"
        private const val KEY_UPDATE_REMIND_TS = "update_remind_ts"

        /** Varsayılan profil kimliği (eski tek profil). Bu profilde veri anahtarları izole edilmez. */
        const val DEFAULT_PROFILE_ID = "default"

        /** Profil bazlı (çoklu profil) verilerin temel anahtarları. */
        private val PROFILE_SCOPED_BASES = setOf(
            KEY_FAVORITES, KEY_FAVORITE_CHANNELS, KEY_FAVORITE_VODS,
            KEY_VOD_PROGRESS, KEY_EPISODE_PROGRESS, KEY_WATCHED_OVERRIDES,
            KEY_WATCHED_EPISODES, KEY_WATCH_LATER, KEY_USER_LISTS
        )

        /** Yedeklemeye dahil edilen tüm SharedPreferences anahtarları. */
        private val BACKUP_KEYS = setOf(
            KEY_PORTALS, KEY_ACTIVE_PORTAL, KEY_ONBOARDING_DONE, KEY_PROFILES,
            KEY_SETTINGS, KEY_FAVORITES, KEY_FAVORITE_CHANNELS, KEY_FAVORITE_VODS,
            KEY_VOD_PROGRESS, KEY_WATCHED_OVERRIDES, KEY_WATCHED_EPISODES,
            KEY_EPISODE_PROGRESS, KEY_M3U_SOURCES, KEY_XTREAM_SOURCES,
            KEY_ACTIVE_SOURCE_KIND, KEY_ACTIVE_SOURCE_ID, KEY_USER_PROFILE,
            KEY_USER_PROFILES, KEY_ACTIVE_PROFILE,
            KEY_WATCH_LATER, KEY_USER_LISTS, KEY_CHANNEL_CUSTOMIZATION,
            KEY_LAST_LIVE_CHANNEL, KEY_EPG_REMINDERS
        )

        /** JSON olarak kodlanmış (putString + serialize) yedek anahtarları. */
        private val JSON_BACKUP_KEYS = BACKUP_KEYS - setOf(
            KEY_ACTIVE_PORTAL, KEY_ACTIVE_SOURCE_KIND, KEY_ACTIVE_SOURCE_ID,
            KEY_ACTIVE_PROFILE, KEY_ONBOARDING_DONE
        )

        /** Düz metin olarak saklanan yedek anahtarları. */
        private val PLAIN_BACKUP_KEYS = setOf(
            KEY_ACTIVE_PORTAL, KEY_ACTIVE_SOURCE_KIND, KEY_ACTIVE_SOURCE_ID, KEY_ACTIVE_PROFILE,
            KEY_LAST_LIVE_CHANNEL
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
data class ExternalVodCacheDto(
    val version: Int = 0,
    val genres: List<Genre>,
    val items: List<VodItem>
)

@kotlinx.serialization.Serializable
data class ExternalChannelCacheDto(val genres: List<Genre>, val channels: List<Channel>)

@kotlinx.serialization.Serializable
data class CatalogMetaFile(
    val categories: List<Genre>,
    val itemCount: Int,
    val ts: Long,
    val version: Int = 1
)

@kotlinx.serialization.Serializable
data class VodProgress(
    val positionMs: Long,
    val durationMs: Long,
    val lastUpdated: Long = 0,
    /** Katalog senkronu beklenmeden ana sayfada gösterilebilmesi için öğe anlık görüntüsü. */
    val name: String = "",
    val poster: String = "",
    val isSeries: Boolean = false,
    val categoryId: Long = 0,
    /** İlerlemenin ait olduğu kaynak ("m3u|id", "xtream|id", "stalker|portalId"). */
    val sourceKey: String = "",
    /** Dizi bölümleri için etiket (ör: "S1 E3"). */
    val episodeLabel: String = ""
) {
    /** Katalog byId'sinde öğe yoksa ana sayfa için yeterli bir [VodItem] üretir. */
    fun toVodItem(id: Long): VodItem? =
        if (name.isBlank()) null
        else VodItem(
            id = id,
            categoryId = categoryId,
            name = name,
            originalName = name,
            poster = poster,
            isSeries = isSeries
        )
}
