package com.stalkerapp.data

/**
 * Kanal yönetimi özelleştirmelerini kanal listelerine uygular. Saf fonksiyonlar —
 * ekranlar bunları her liste çiziminde çağırır; özelleştirme [Store] içinde
 * [ChannelCustomization] olarak saklanır.
 */
object ChannelCustomizer {

    /** Ad düzenleyici: yapılandırılmış önek/sonekleri adın başından/sonundan temizler. */
    fun displayName(name: String, c: ChannelCustomization): String {
        var n = name
        for (p in c.stripPrefixes) {
            if (p.isNotBlank() && n.startsWith(p)) {
                n = n.removePrefix(p)
                break
            }
        }
        for (s in c.stripSuffixes) {
            if (s.isNotBlank() && n.endsWith(s)) {
                n = n.removeSuffix(s)
                break
            }
        }
        return n.trim()
    }

    /** Kanalın görüneceği grup adı: özel gruba taşındıysa o grup, değilse tür adı. */
    fun groupOf(ch: Channel, c: ChannelCustomization): String =
        c.channelGroup[ch.id.toString()]?.takeIf { it.isNotBlank() } ?: ch.tvGenreTitle

    /** Özel logo atandıysa onu, yoksa kanalın kendi logosunu döndürür. */
    fun logoOf(ch: Channel, c: ChannelCustomization): String =
        c.customLogos[ch.id.toString()]?.takeIf { it.isNotBlank() } ?: ch.logo

    /** Özelleştirmeleri tek kanala uygular (liste çiziminden önce copy + dönüşüm). */
    fun apply(ch: Channel, c: ChannelCustomization): Channel = ch.copy(
        name = displayName(ch.name, c),
        logo = logoOf(ch, c),
        tvGenreTitle = groupOf(ch, c)
    )

    /** Grubun manuel sıralamasını uygular; sırada olmayan kanallar sonda kalır. */
    fun sortedChannels(channels: List<Channel>, groupTitle: String, c: ChannelCustomization): List<Channel> {
        val order = c.channelOrder[groupTitle]
        if (order.isNullOrEmpty()) return channels
        val byId = channels.associateBy { it.id }
        val sorted = order.mapNotNull { byId[it] }
        val rest = channels.filter { it.id !in order }
        return sorted + rest
    }

    /** Grup sıralaması uygular: groupOrder'daki gruplar önce, kalanlar özgün sırada sonra. */
    fun sortedGenres(genres: List<Genre>, c: ChannelCustomization): List<Genre> {
        if (c.groupOrder.isEmpty()) return genres
        val byTitle = genres.associateBy { it.title }
        val sorted = c.groupOrder.mapNotNull { byTitle[it] }
        val rest = genres.filter { it.title !in c.groupOrder }
        return sorted + rest
    }
}
