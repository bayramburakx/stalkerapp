package com.stalkerapp.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Portal(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val mac: String = "",
    val username: String = "",
    val password: String = ""
)

@Serializable
data class UserProfile(
    /**
     * Profil kimliği. Boşsa/eski sürümse ilk profil "default" (eski tek profil)
     * kimliğiyle kaydedilir — böylece mevcut kullanıcıların favori/geçmiş verisi
     * olduğu gibi kalır (profil bazlı anahtar izolasyonu yalnızca diğer profillere uygulanır).
     */
    val id: String = "",
    val name: String = "",
    /** Profil avatarı olarak gösterilen emoji (ör. "😀"). */
    val avatar: String = "😀"
)

/** Kullanıcının oluşturduğu özel liste (Kütüphanem). */
@Serializable
data class UserList(
    val id: String = "",
    val name: String = "",
    val itemIds: List<Long> = emptyList()
)

@Serializable
data class Settings(
    val timezoneOffset: Int = 0,
    val requestIntervalMs: Long = 100,
    val cooldownMs: Long = 300_000L,
    val maxBufferMs: Int = 60_000,
    /** Bölüm bitince sıradaki bölüm otomatik oynatılsın mı (binge mod). */
    val bingeMode: Boolean = false,
    /** TMDB API anahtarı (oyuncu fotoğrafları + fragman için). Boşsa özellik kapalıdır. */
    val tmdbApiKey: String = "",
    /**
     * Harici EPG (XMLTV) URL'si. Portalın kendi EPG'si yoksa buradan çekilir
     * (kanallar `xmltv_id` ile eşleştirilir). Ör: sağlayıcının EPG linki ya da
     * kendi barındırdığın bir XMLTV dosyası (ör. iptv-org/epg çıktısı).
     * Varsayılan: Türkiye kanallarını içeren küçük (166KB) bir XMLTV kaynağı.
     */
    val epgUrl: String = "https://epgshare01.online/epgshare01/epg_ripper_TR1.xml.gz",

    // ---------- Kaynak anahtarları (Playlist & Kaynaklar) ----------
    /** Stalker portal kaynağı etkin mi? */
    val stalkerEnabled: Boolean = true,
    /** M3U kaynakları etkin mi? */
    val m3uEnabled: Boolean = true,
    /** Xtream kaynakları etkin mi? */
    val xtreamEnabled: Boolean = true,

    // ---------- İçerik (Kütüphane & İçerik) ----------
    /** +18 içerikler gösterilsin mi? */
    val adultContentEnabled: Boolean = false,
    /** Kullanıcının gizlediği kategori başlıkları (VOD listelerinde ve ana sayfada filtrelenir). */
    val hiddenCategories: List<String> = emptyList(),
    /** Ana sayfada "Son İzlenenler" / "İzlemeye Devam" bölümlerinde gösterilmeyecek medya ID'leri. */
    val hiddenFromHome: List<Long> = emptyList(),
    /** Kullanıcının gizlediği canlı TV grup başlıkları (kanal listesinden ve filtre çiplerinden gizlenir). */
    val hiddenChannelGroups: List<String> = emptyList(),
    /** Ana sayfa düzeni: "rows" (varsayılan), "compact", "list". */
    val homeLayout: String = "rows",
    /** Ana sayfa bölüm sırası (HomeDashboardScreen bölüm adları). */
    val homeSectionOrder: List<String> = emptyList(),
    /** Ana sayfada üstteki büyük kaydırmalı tanıtım (hero) gösterilsin mi? */
    val heroEnabled: Boolean = true,
    /** Ana sayfa bölümlerinde (Popüler Filmler/Diziler vb.) gösterilecek öğe sayısı. */
    val homeSectionSize: Int = 20,
    /** Uygulama açılışında hangi sekme açılsın: 0=Ana, 1=Canlı, 2=Film, 3=Dizi. */
    val defaultTab: Int = 0,
    /** Uygulama açılınca VOD kataloğu arka planda otomatik senkronlansın mı? */
    val autoSyncVod: Boolean = true,
    /** VOD kataloğu senkronu yalnızca Wi-Fi (veya kablolu) bağlantıda çalışsın mı? */
    val wifiOnlySync: Boolean = false,

    // ---------- Oynatıcı ----------
    /** Varsayılan video kalitesi ("auto", "2160p", "1080p", "720p", "480p"). */
    val defaultQuality: String = "auto",
    /** Bildirim/oynatıcıda PiP (picture-in-picture) etkin mi? */
    val pipEnabled: Boolean = true,
    /** Oynatıcıda kaydırma jestleri (parlaklık/ses/ileri geri) etkin mi? */
    val gesturesEnabled: Boolean = true,
    /** Altyazılar gösterilsin mi? */
    val subtitlesEnabled: Boolean = true,
    /** Çözücü: "auto", "hardware", "software". */
    val decoder: String = "auto",
    /** Film/bölüm kaldığı yerden otomatik devam etsin mi? */
    val resumePlayback: Boolean = true,
    /** Canlı TV akışı kesilince otomatik yeniden bağlanmaya çalışılsın mı? */
    val autoRetryLive: Boolean = true,
    /** Oynatıcı açıkken ekran uyusun mu (false = ekran açık kalır)? */
    val keepScreenOn: Boolean = true,
    /** Sonraki kanal ön yüklemesi (zapping gecikmesini azaltır) açık mı? */
    val zappingPrefetch: Boolean = true,
    /** Uygulama arka plana geçince (PiP değilken) oynatmaya devam edilsin mi? */
    val backgroundPlayback: Boolean = true,
    /** Oynatıcı yönü: "auto" (sensör yatay), "landscape" (sabit yatay), "sensor" (serbest). */
    val playerOrientation: String = "auto",
    /** Oynatıcı kontrolleri kaç saniye sonra gizlensin? (3/5/10) */
    val controlsTimeoutSec: Int = 5,
    /** Çift dokunma ile ileri/geri atlama miktarı (saniye). */
    val doubleTapSeekSec: Int = 10,
    /** Varsayılan ses dili (ISO kodu, ör. "tr"). Boş = otomatik. */
    val preferredAudioLang: String = "",
    /** Varsayılan altyazı dili (ISO kodu, ör. "tr"). Boş = otomatik. */
    val preferredSubtitleLang: String = "",
    /** Varsayılan oynatma hızı (0.75, 1.0, 1.25, 1.5, 2.0). */
    val playbackSpeed: Float = 1f,
    /** Altyazı yazı boyutu (dp). */
    val subtitleSize: Int = 16,
    /** A/V senkron: ses gecikmesi (ms). Pozitif = ses videoya göre gecikir. */
    val audioDelayMs: Int = 0,
    /** Akış formatı zorlama: "auto", "hls" (M3U8), "ts" (MPEG-TS). */
    val streamFormat: String = "auto",
    /** Auto Frame Rate: "off", "match" (içerik kare hızına uy) veya "24"/"25"/"30"/"50"/"60". */
    val afrMode: String = "off",
    /** Audio passthrough (AC3/EAC3/DTS ham bitstream geçişi) açık mı? */
    val audioPassthrough: Boolean = true,
    /** Varsayılan oynatıcı: "builtin" (yerleşik ExoPlayer) veya "external" (sistem oynatıcısı). */
    val defaultPlayer: String = "builtin",
    /** Uygulama açılınca son izlenen canlı kanal otomatik oynatılsın mı? */
    val resumeLastChannel: Boolean = false,

    // ---------- EPG ----------
    /** Harici EPG (XMLTV) yeniden indirme aralığı (saat). */
    val epgRefreshHours: Int = 6,
    /** EPG geçmiş gün sayısı (bugünden önceki günlerin programları). */
    val epgPastDays: Int = 3,
    /** EPG kaynak önceliği: "portal" (önce portal EPG) veya "external" (önce XMLTV). */
    val epgSourcePriority: String = "portal",
    /** Program açıklamaları (desc) tutulsun mu? Kapalıyken bellek/önbellekten atılır. */
    val epgKeepDescriptions: Boolean = true,

    // ---------- Görünüm & Cihaz ----------
    /** Tema: "system" | "light" | "dark" | "amoled". */
    val themeMode: String = "system",
    /** Vurgu rengi (ARGB int). 0 = varsayılan (siyah/beyaz palet). */
    val accentColor: Long = 0L,
    /** Arayüz yazı boyutu ölçeği (0.85 - 1.4). */
    val uiFontScale: Float = 1f,
    /** Cihaz açılışında uygulamayı otomatik başlat (TV box'lar). */
    val startOnBoot: Boolean = false,
    /** Kumanda kanal +/- ve medya tuşlarıyla kanal değiştirme (zapping). */
    val remoteChannelKeys: Boolean = true,

    // ---------- Entegrasyonlar ----------
    /** TMDB istek dili ("tr", "en" vb.). */
    val tmdbLanguage: String = "tr",
    /** Detay ekranında TMDB fragmanları gösterilsin mi? */
    val tmdbTrailers: Boolean = true,
    /** Detay ekranında TMDB oyuncu fotoğrafları gösterilsin mi? */
    val tmdbPeople: Boolean = true,

    // ---------- Gizlilik ----------
    /** Ayarlar girişi için 4 haneli PIN. Boşsa PIN kilidi kapalıdır. */
    val pin: String = ""
)

@Serializable
data class ServerInfo(
    val address: String = "",
    val city: String = ""
)

/** M3U tabanlı IPTV kaynağı (URL üzerinden çekilen #EXTM3U listesi). */
@Serializable
data class M3uSource(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    /** Son başarılı indirmenin içeriği — çevrimdışı/tekrar açılışta indirme gerekmez. */
    val content: String = ""
)

/** Xtream Codes tabanlı IPTV kaynağı (sunucu + kullanıcı adı + şifre). */
@Serializable
data class XtreamSource(
    val id: String = "",
    val name: String = "",
    /** Ör: "http://host:port" (player_api.php otomatik eklenir). */
    val server: String = "",
    val username: String = "",
    val password: String = ""
)

@Serializable
data class Profile(
    val mac: String = "",
    val timezone: String = "",
    val serverInfo: List<ServerInfo> = emptyList(),
    val baseUrl: String = "",
    val portal: Portal? = null
) {
    val serverAddress: String
        get() = serverInfo.firstOrNull()?.address.orEmpty()
            .takeIf { it.isNotBlank() }
            ?: baseUrl.substringAfter("://")
}

@Serializable
data class Genre(
    val id: Long = 0,
    val title: String = "",
    @SerialName("censored") val censored: Boolean = false,
    val number: Int = 0
)

@Serializable
data class Channel(
    val id: Long = 0,
    val name: String = "",
    val number: Int = 0,
    val logo: String = "",
    val cmd: String = "",
    @SerialName("tv_genre_id") val tvGenreId: Long = 0,
    @SerialName("tv_genre_title") val tvGenreTitle: String = "",
    @SerialName("is_tv_archive") val isTvArchive: Boolean = false,
    @SerialName("tv_archive_duration") val archiveDuration: Int = 0,
    /** Harici XMLTV EPG eşleştirmesi için kanal kimliği (ör. "TRT1.tr"). */
    @SerialName("xmltv_id") val xmltvId: String = ""
)

@Serializable
data class VodItem(
    val id: Long = 0,
    @SerialName("cat_id") val categoryId: Long = 0,
    val name: String = "",
    @SerialName("o_name") val originalName: String = "",
    @SerialName("sname") val sname: String = "",
    val poster: String = "",
    val description: String = "",
    val year: String = "",
    val director: String = "",
    val country: String = "",
    val rating: String = "",
    val genres: String = "",
    val actors: String = "",
    @SerialName("time") val duration: String = "",
    @SerialName("writers") val writers: String = "",
    @SerialName("series") val seriesRef: String = "",
    val cmd: String = "",
    @SerialName("selected_season") val selectedSeason: String = "",
    @SerialName("series_data") val seriesData: String = "",
    @SerialName("tmdb_id") val tmdbId: Long = 0,
    var isSeries: Boolean = false,
    /** Real series id on portals with a separate series library (`type=series`); 0 for plain VOD. */
    val seriesId: Long = 0
)

@Serializable
data class Season(
    val id: Long = 0,
    val name: String = "",
    /** Portal'ın sezon/afiş görseli (yoksa TMDB'den çekilir). */
    val poster: String = ""
)

/** Kullanıcının oluşturduğu özel kanal grubu (kanal yönetimi). */
@Serializable
data class CustomChannelGroup(
    val id: String = "",
    val name: String = ""
)

/**
 * Kanal yönetimi özelleştirmeleri: özel gruplar, manuel sıralama, ad düzenleyici
 * (önek/sonek temizleme), özel logolar. Cihaz genelinde saklanır (profil bazlı
 * değil) — TiviMate'teki "per-playlist" kanal yapılandırmasına benzer.
 */
@Serializable
data class ChannelCustomization(
    /** Kullanıcının oluşturduğu özel gruplar. */
    val customGroups: List<CustomChannelGroup> = emptyList(),
    /** Kanalın görüneceği grup adı (kanal id -> grup adı). Özel gruba taşıma için. */
    val channelGroup: Map<String, String> = emptyMap(),
    /** Grup adı -> manuel sıralanmış kanal id listesi. */
    val channelOrder: Map<String, List<Long>> = emptyMap(),
    /** Grup sıralaması (grup başlıkları, ilk sıradan sona). */
    val groupOrder: List<String> = emptyList(),
    /** Ad düzenleyici: silinecek önekler (ör. "HD ", "TR "). */
    val stripPrefixes: List<String> = emptyList(),
    /** Ad düzenleyici: silinecek sonekler (ör. " HD", " FHD"). */
    val stripSuffixes: List<String> = emptyList(),
    /** Kanal başına özel logo (kanal id -> logo URL). */
    val customLogos: Map<String, String> = emptyMap(),
    /** Kanal başına EPG eşleştirme (kanal id -> xmltv_id). Boşsa ad/xmltv_id ile otomatik eşleşir. */
    val channelEpgIds: Map<String, String> = emptyMap()
)

@Serializable
data class Episode(
    val id: Long = 0,
    val name: String = "",
    @SerialName("episode_number") val episodeNumber: Int = 0,
    val cmd: String = "",
    /** Portal'ın bölüm küçük resmi (yoksa TMDB'den çekilir). */
    val thumb: String = ""
)

/**
 * Cihaz üzerinde kayıt (recording): akış [startTs]'de [stopTs]'ye kadar bir
 * dosyaya indirilir. Sunucu kaydı yerine cihaz kaydı — MPEG-TS / ilerlemeli
 * akışlarda güvenilir, HLS'de bazı kaynaklarda çalışmayabilir.
 */
@Serializable
data class Recording(
    val id: String = "",
    val channelId: Long = 0,
    val channelName: String = "",
    val programName: String = "",
    val startTs: Long = 0,
    val stopTs: Long = 0,
    /** "scheduled" | "recording" | "done" | "failed" | "cancelled" */
    val status: String = "scheduled",
    /** İndirilen dosyanın yolu (tamamlanınca dolar). */
    val filePath: String = "",
    /** Başlangıçta çözülen akış URL'si (elle başlatılan kayıtlarda hazır gelir). */
    val streamUrl: String = "",
    /** Kaydın ait olduğu kaynak türü ("stalker"|"m3u"|"xtream"). */
    val sourceKind: String = "",
    /** URL'yi başlangıçta çözmek için gereken kanal bilgisi. */
    val channel: Channel = Channel()
)

/** EPG program hatırlatıcısı: program başlayınca bildirim gönderilir. */
@Serializable
data class EpgReminder(
    val id: String = "",
    val channelId: Long = 0,
    val channelName: String = "",
    val programName: String = "",
    /** Programın başlama zamanı (epoch sn). */
    val startTs: Long = 0,
    /** Bildirim gönderildi mi? */
    val fired: Boolean = false
)

@Serializable
data class EpgProgram(
    @SerialName("ch_id") val chId: Long = 0,
    val name: String = "",
    val start: String = "",
    val stop: String = "",
    val desc: String = "",
    val category: String = "",
    val startTs: Long = 0,
    val stopTs: Long = 0,
    val isCurrent: Boolean = false,
    /** Portal EPG'si yoksa üretilen varsayılan programlar için true. */
    val isDefault: Boolean = false
)
