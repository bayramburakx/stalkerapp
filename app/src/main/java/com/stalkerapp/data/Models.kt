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
data class Settings(
    val timezoneOffset: Int = 0,
    val requestIntervalMs: Long = 700,
    val cooldownMs: Long = 300_000L,
    val maxBufferMs: Int = 60_000
)

@Serializable
data class ServerInfo(
    val address: String = "",
    val city: String = ""
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
    @SerialName("tv_archive_duration") val archiveDuration: Int = 0
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
    @SerialName("series") val isSeries: Boolean = false,
    val cmd: String = "",
    @SerialName("selected_season") val selectedSeason: String = "",
    @SerialName("series_data") val seriesData: String = ""
)

@Serializable
data class Season(
    val id: Long = 0,
    val name: String = ""
)

@Serializable
data class Episode(
    val id: Long = 0,
    val name: String = "",
    @SerialName("episode_number") val episodeNumber: Int = 0,
    val cmd: String = ""
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
    val isCurrent: Boolean = false
)
