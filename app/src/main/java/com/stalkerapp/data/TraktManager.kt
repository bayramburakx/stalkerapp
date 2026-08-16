package com.stalkerapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Trakt.tv entegrasyonu (cihaz akışı — OAuth).
 *
 * Akış: [requestDeviceCode] ile kullanıcı kodu alınır, kullanıcı
 * verification_url adresinde kodu girer, ardından [pollToken] erişim
 * belirteci dönene dek yoklanır. [syncWatched] izlenen filmleri/dizileri
 * Trakt geçmişine yazar.
 */
object TraktManager {

    private const val API = "https://api.trakt.tv"
    private val JSON = MediaType.Companion.toMediaType("application/json")

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class DeviceCodeResult(
        val deviceCode: String = "",
        val userCode: String = "",
        val verificationUrl: String = "https://trakt.tv/activate",
        val intervalSec: Int = 5
    )

    /** Cihaz akışı için kullanıcı kodu ister. */
    suspend fun requestDeviceCode(clientId: String): DeviceCodeResult = withContext(Dispatchers.IO) {
        if (clientId.isBlank()) return@withContext DeviceCodeResult()
        runCatching {
            val body = buildJsonObject { put("client_id", clientId) }
                .toString().toRequestBody(JSON)
            val req = Request.Builder()
                .url("$API/oauth/device/code")
                .post(body)
                .build()
            okHttp.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use DeviceCodeResult()
                val o = runCatching {
                    Json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
                }.getOrNull() ?: return@use DeviceCodeResult()
                DeviceCodeResult(
                    deviceCode = o["device_code"]?.contentOrNull.orEmpty(),
                    userCode = o["user_code"]?.contentOrNull.orEmpty(),
                    verificationUrl = o["verification_url"]?.contentOrNull
                        ?: "https://trakt.tv/activate",
                    intervalSec = o["interval"]?.contentOrNull?.toIntOrNull() ?: 5
                )
            }
        }.getOrDefault(DeviceCodeResult())
    }

    /**
     * Kullanıcı kodu onaylayana dek yoklar. Dönüş:
     * - "ok:<access_token>:<username>" → bağlantı tamam
     * - "pending" → henüz onaylanmadı (tekrar yokla)
     * - "expired" / "denied" / hata mesajı → başarısız
     */
    suspend fun pollToken(clientId: String, deviceCode: String): String = withContext(Dispatchers.IO) {
        if (deviceCode.isBlank()) return@withContext "expired"
        runCatching {
            val body = buildJsonObject {
                put("code", deviceCode)
                put("client_id", clientId)
            }.toString().toRequestBody(JSON)
            val req = Request.Builder()
                .url("$API/oauth/device/token")
                .post(body)
                .build()
            okHttp.newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (r.code == 400 && text.contains("pending", ignoreCase = true)) return@use "pending"
                if (!r.isSuccessful) return@use "expired"
                val o = runCatching {
                    Json.parseToJsonElement(text).jsonObject
                }.getOrNull() ?: return@use "expired"
                val token = o["access_token"]?.contentOrNull.orEmpty()
                val username = o["user"]?.jsonObject?.get("username")?.contentOrNull.orEmpty()
                if (token.isBlank()) "expired" else "ok:$token:$username"
            }
        }.getOrDefault("expired")
    }

    /**
     * İzlenen film/dizi TMDB id'lerini Trakt geçmişine yazar.
     * Başarılıysa null, değilse hata mesajı döner.
     */
    suspend fun syncWatched(
        clientId: String,
        accessToken: String,
        movieTmdbIds: List<Long>,
        showTmdbIds: List<Long>
    ): String? = withContext(Dispatchers.IO) {
        if (clientId.isBlank() || accessToken.isBlank()) return@withContext "Trakt bağlı değil"
        if (movieTmdbIds.isEmpty() && showTmdbIds.isEmpty()) return@withContext "Senkronize edilecek içerik yok"
        runCatching {
            val body = buildJsonObject {
                putJsonArray("movies") {
                    movieTmdbIds.distinct().forEach { id ->
                        add(buildJsonObject { putJsonArray("ids") { add(buildJsonObject { put("tmdb", id) }) } })
                    }
                }
                putJsonArray("shows") {
                    showTmdbIds.distinct().forEach { id ->
                        add(buildJsonObject { putJsonArray("ids") { add(buildJsonObject { put("tmdb", id) }) } })
                    }
                }
            }.toString().toRequestBody(JSON)
            val req = Request.Builder()
                .url("$API/sync/history")
                .post(body)
                .header("Content-Type", "application/json")
                .header("trakt-api-version", "2")
                .header("trakt-api-key", clientId)
                .header("Authorization", "Bearer $accessToken")
                .build()
            okHttp.newCall(req).execute().use { r ->
                if (r.isSuccessful) {
                    val o = runCatching {
                        Json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
                    }.getOrNull()
                    val added = o?.get("added")?.jsonObject?.get("movies")?.contentOrNull
                        ?: o?.get("added")?.jsonObject?.get("shows")?.contentOrNull
                    null
                } else {
                    "Trakt hatası (${r.code})"
                }
            }
        }.getOrNull()
    }

    /** Trakt bağlantısını iptal eder (belirteci geçersiz kılar). */
    suspend fun revoke(clientId: String, accessToken: String) = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                put("access_token", accessToken)
                put("client_id", clientId)
            }.toString().toRequestBody(JSON)
            val req = Request.Builder()
                .url("$API/oauth/revoke")
                .post(body)
                .build()
            okHttp.newCall(req).execute().close()
        }
    }

    /** OAuth erişim belirtecinin geçerli olup olmadığını hafifçe doğrular. */
    suspend fun testToken(clientId: String, accessToken: String): Boolean = withContext(Dispatchers.IO) {
        if (clientId.isBlank() || accessToken.isBlank()) false
        else runCatching {
            val req = Request.Builder()
                .url("$API/users/me")
                .header("trakt-api-version", "2")
                .header("trakt-api-key", clientId)
                .header("Authorization", "Bearer $accessToken")
                .build()
            okHttp.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** Akış yoklamaları arasında bekleme yardımcısı. */
    suspend fun waitForNextPoll(intervalSec: Int) {
        delay((intervalSec.coerceIn(2, 15) * 1000L))
    }
}
