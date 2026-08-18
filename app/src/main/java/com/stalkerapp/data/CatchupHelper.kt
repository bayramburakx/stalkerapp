package com.stalkerapp.data

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Catch-up (geçmiş yayın izleme) ve timeshift URL oluşturma yardımcısı.
 *
 * Farklı panel türleri için farklı URL formatları:
 *  - Stalker: `/play/...?utc=START&lutc=END`
 *  - Xtream:  `http://server/timeshift/user/pass/DURM/START/STREAM_ID.ts`
 *  - M3U XMLTV catchup: `{catchup-source}?utc={utc}&utcend={utcend}`
 */
object CatchupHelper {

    /** Kanal izin veriyor mu? */
    fun channelSupportsCatchup(channel: Channel): Boolean =
        channel.isTvArchive && channel.archiveDuration > 0

    /**
     * Stalker portal için catch-up URL'si üretir.
     * Panel'in standart timeshift formatı: cmd + `?utc=<start>&lutc=<end>&from=<start>&to=<end>`
     */
    fun buildStalkerCatchupUrl(
        baseCmd: String,
        startUnix: Long,
        stopUnix: Long
    ): String {
        val sep = if (baseCmd.contains('?')) "&" else "?"
        return "$baseCmd${sep}utc=$startUnix&lutc=$stopUnix" +
            "&from=$startUnix&to=$stopUnix"
    }

    /**
     * Xtream Codes için timeshift URL üretir.
     * Format: `http://SERVER/timeshift/USER/PASS/DURATION/START/STREAM_ID.ts`
     */
    fun buildXtreamCatchupUrl(
        server: String,
        username: String,
        password: String,
        streamId: String,
        startUnix: Long,
        durationMinutes: Int = 60
    ): String {
        val startFormatted = formatXtreamDate(startUnix)
        return "$server/timeshift/$username/$password/$durationMinutes/$startFormatted/$streamId.ts"
    }

    /**
     * M3U XMLTV catch-up URL şablonu çözümleme.
     * Örnek şablon: `http://host/play?utc={utc}&utcend={utcend}&ch={id}`
     */
    fun buildM3uCatchupUrl(
        template: String,
        channelId: String,
        startUnix: Long,
        stopUnix: Long,
        channelName: String = ""
    ): String {
        val durationSec = (stopUnix - startUnix).coerceAtLeast(60)
        val startDate = Date(startUnix * 1000)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return template
            .replace("{utc}", startUnix.toString())
            .replace("{utcend}", stopUnix.toString())
            .replace("{id}", channelId)
            .replace("{channel}", URLEncoder.encode(channelName, "UTF-8"))
            .replace("{start}", sdf.format(startDate))
            .replace("{duration}", durationSec.toString())
            .replace("{offset}", "0")
    }

    /**
     * Geçmiş gün listesi döner (EPG takvim seçici için).
     * Dizin 0 = bugün, 1 = dün, ...
     */
    fun pastDaysList(maxDays: Int = 7): List<DayEntry> {
        val result = mutableListOf<DayEntry>()
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale("tr"))
        val now = System.currentTimeMillis()
        for (i in 0 until maxDays) {
            val ts = now - i * 86_400_000L
            val date = Date(ts)
            result.add(
                DayEntry(
                    label = if (i == 0) "Bugün" else if (i == 1) "Dün" else sdf.format(date),
                    startOfDayUnix = startOfDayUnix(ts),
                    daysAgo = i
                )
            )
        }
        return result
    }

    data class DayEntry(
        val label: String,
        val startOfDayUnix: Long,
        val daysAgo: Int
    )

    private fun startOfDayUnix(epochMs: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = epochMs
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 1000
    }

    private fun formatXtreamDate(unix: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(unix * 1000))
    }
}
