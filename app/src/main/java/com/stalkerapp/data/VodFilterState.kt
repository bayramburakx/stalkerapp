package com.stalkerapp.data

/**
 * VOD kataloğu için gelişmiş filtre/sıralama durumu.
 * VodScreen'de kullanıcının seçtiği filtreleri tutar (yıl aralığı, IMDb puanı,
 * dil ve sıralama). Filtre yokken tüm katalog gösterilir.
 */
data class VodFilterState(
    /** Yıl aralığı (null = filtre yok). */
    val yearRange: IntRange? = null,
    /** Minimum IMDb puanı 0.0-10.0 (varsayılan 0 = filtre yok). */
    val minRating: Float = 0f,
    /** Dil filtresi (ISO kodu ör. "tr", "en"). Boş = tümü. */
    val language: String = "",
    /** Sıralama modu. */
    val sortMode: SortMode = SortMode.DEFAULT
) {
    val isActive: Boolean
        get() = yearRange != null || minRating > 0f || language.isNotBlank() || sortMode != SortMode.DEFAULT
}

/** VOD listesi sıralama modları. */
enum class SortMode {
    DEFAULT, A_Z, Z_A, NEWEST, HIGHEST_RATED
}

private val YEAR_REGEX = Regex("""(19\d\d|20\d\d)""")

/** Filtre geçerli mi kontrol eder. */
fun VodFilterState.matches(
    name: String = "",
    year: String,
    rating: String,
    language: String = ""
): Boolean {
    if (yearRange != null) {
        val yInt = year.trim().take(4).toIntOrNull()
            ?: YEAR_REGEX.find(name)?.value?.toIntOrNull()
        if (yInt == null || yInt !in yearRange) return false
    }

    if (minRating > 0f) {
        val cleanRating = rating.replace(',', '.').substringBefore('/').trim()
        val rFloat = cleanRating.toFloatOrNull()
        if (rFloat == null || rFloat < minRating) return false
    }

    if (this.language.isNotBlank()) {
        val l = this.language.trim().lowercase()
        val langOk = language.lowercase().contains(l) ||
            name.lowercase().contains("($l)") ||
            name.lowercase().contains("[$l]") ||
            (l == "tr" && (name.contains("türkçe", ignoreCase = true) || name.contains("dublaj", ignoreCase = true)))
        if (!langOk) return false
    }

    return true
}