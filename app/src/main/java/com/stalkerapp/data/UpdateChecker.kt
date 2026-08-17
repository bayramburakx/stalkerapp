package com.stalkerapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** GitHub'daki güncel sürüm bilgisi. */
data class UpdateInfo(
    val version: String,
    val url: String,
    val publishedAt: String
)

/**
 * Uygulama içi güncelleme kontrolü. APK, GitHub Releases'in "latest" etiketli
 * yayınına yüklenir; yayın adı "Stalker Player v1.0.2 – Güncel APK" biçiminde
 * sürüm içerir. Bu sürüm, uygulamanın BuildConfig.VERSION_NAME değeriyle
 * karşılaştırılır. Genel repo API'si kimlik doğrulaması gerektirmez.
 */
class UpdateChecker(private val repo: String = "bayramburakx/stalkerapp") {

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** En son yayınlanan (draft/prerelease olmayan) sürümü döner. */
    suspend fun latest(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$repo/releases?per_page=1")
                .header("Accept", "application/vnd.github+json")
                .build()
            okHttp.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val text = r.body?.string().orEmpty()
                val arr = Json.parseToJsonElement(text) as? JsonArray ?: return@use null
                val release = arr.firstOrNull { it is JsonObject } as? JsonObject ?: return@use null
                if ((release["draft"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() == true) return@use null
                if ((release["prerelease"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() == true) return@use null
                val name = (release["name"] as? JsonPrimitive)?.contentOrNull ?: ""
                val version = Regex("v?(\\d+\\.\\d+\\.\\d+)").find(name)?.groupValues?.get(1)
                    ?: return@use null
                val assetUrl = (release["assets"] as? JsonArray)
                    ?.firstOrNull { a ->
                        val n = ((a as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull
                        n?.endsWith(".apk") == true
                    }
                    ?.let { a ->
                        ((a as JsonObject)["browser_download_url"] as? JsonPrimitive)?.contentOrNull
                    }
                    ?: "https://github.com/$repo/releases/download/latest/app-release.apk"
                UpdateInfo(
                    version = version,
                    url = assetUrl,
                    publishedAt = (release["published_at"] as? JsonPrimitive)?.contentOrNull ?: ""
                )
            }
        }.getOrNull()
    }

    companion object {
        /** Semver karşılaştırması: latest > current ise true. */
        fun isNewer(latest: String, current: String): Boolean {
            // Long kullanılır: CI'dan gelen büyük yapı numaraları (ör. 1.0.20260817)
            // Int'e sığmayabilir ve karşılaştırma yanlış sonuç verirdi.
            val parse = { v: String -> v.split(".").mapNotNull { it.toLongOrNull() } }
            val a = parse(latest)
            val b = parse(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0L }
                val y = b.getOrElse(i) { 0L }
                if (x != y) return x > y
            }
            return false
        }
    }
}
