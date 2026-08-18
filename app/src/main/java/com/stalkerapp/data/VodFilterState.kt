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

/** Filtre geçerli mi kontrol eder. */
fun VodFilterState.matches(
    year: String,
    rating: String,
    language: String = ""
): Boolean {
    val yearInt = year.trim().take(4).toIntOrNull()
    if (yearRange != null && yearInt != null && yearInt !in yearRange) return false
    val ratingFloat = rating.toFloatOrNull()
    if (minRating > 0f && ratingFloat != null && ratingFloat < minRating) return false
    if (this.language.isNotBlank() && language.isNotBlank() &&
        !language.contains(this.language, ignoreCase = true)) return false
    return true
}