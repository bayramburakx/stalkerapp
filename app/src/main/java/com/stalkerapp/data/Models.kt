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
    /** Varsayılan oynatma hızı (0.75, 1.0, 1.25, 1.5, 2.0). */
    val playbackSpeed: Float = 1f,
    /** Altyazı yazı boyutu (dp). */
    val subtitleSize: Int = 16,

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

@Serializable
data class Episode(
    val id: Long = 0,
    val name: String = "",
    @SerialName("episode_number") val episodeNumber: Int = 0,
    val cmd: String = "",
    /** Portal'ın bölüm küçük resmi (yoksa TMDB'den çekilir). */
    val thumb: String = ""
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
