package com.stalkerapp.util

/**
 * Hafif çeviri yardımcısı: ana arayüz (alt menü, ayar bölüm başlıkları) için
 * Türkçe / İngilizce çeviriler. Uygulamanın kalan metinleri şimdilik Türkçedir
 * (tam uluslararasılaştırma bir sonraki aşama). Ayarlar → Görünüm & Cihaz → Dil.
 */
object L10n {

    fun t(lang: String, key: String): String =
        if (lang == "en") EN[key] ?: key else TR[key] ?: key

    private val TR = mapOf(
        "nav.home" to "Ana Sayfa",
        "nav.live" to "Canlı TV",
        "nav.movies" to "Filmler",
        "nav.series" to "Diziler",
        "nav.settings" to "Ayarlar",
        "nav.search" to "Ara",
        "settings.playlist" to "Playlist & Kaynaklar",
        "settings.content" to "Kütüphane & İçerik",
        "settings.library" to "Kütüphanem",
        "settings.player" to "Oynatıcı",
        "settings.appearance" to "Görünüm & Cihaz",
        "settings.integrations" to "Entegrasyonlar",
        "settings.account" to "Hesap",
        "settings.privacy" to "Gizlilik & Güvenlik",
        "settings.about" to "Hakkında & Destek",
        "settings.title" to "Ayarlar",
        "settings.select" to "Bir bölüm seç:"
    )

    private val EN = mapOf(
        "nav.home" to "Home",
        "nav.live" to "Live TV",
        "nav.movies" to "Movies",
        "nav.series" to "Series",
        "nav.settings" to "Settings",
        "nav.search" to "Search",
        "settings.playlist" to "Playlists & Sources",
        "settings.content" to "Library & Content",
        "settings.library" to "My Library",
        "settings.player" to "Player",
        "settings.appearance" to "Appearance & Device",
        "settings.integrations" to "Integrations",
        "settings.account" to "Account",
        "settings.privacy" to "Privacy & Security",
        "settings.about" to "About & Support",
        "settings.title" to "Settings",
        "settings.select" to "Pick a section:"
    )
}
