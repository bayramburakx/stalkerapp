package com.stalkerapp.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class StalkerException(message: String, val isCooldown: Boolean = false) : Exception(message)

class StalkerClient(private val settingsProvider: () -> Settings) {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile private var lastRequestAt = 0L
    @Volatile private var cooldownUntil = 0L
    @Volatile private var deviceMac: String = ""

    fun setDevice(mac: String) {
        deviceMac = mac
    }

    fun cooldownRemainingMs(): Long =
        (cooldownUntil - System.currentTimeMillis()).coerceAtLeast(0)

    fun cooldownRemainingSeconds(): Long = cooldownRemainingMs() / 1000

    fun triggerCooldown(durationMs: Long = settingsProvider().cooldownMs) {
        cooldownUntil = System.currentTimeMillis() + durationMs
        Log.w("StalkerClient", "Cooldown triggered for ${durationMs / 1000}s")
    }

    fun clearCooldown() {
        cooldownUntil = 0
    }

    private suspend fun throttle() {
        val interval = settingsProvider().requestIntervalMs.coerceAtLeast(0)
        val now = System.currentTimeMillis()
        val wait = interval - (now - lastRequestAt)
        if (wait > 0) {
            withContext(Dispatchers.IO) { Thread.sleep(wait) }
        }
        lastRequestAt = System.currentTimeMillis()
    }

    /**
     * Makes a Stalker portal request. Unwraps the `js` response envelope that
     * Stalker middleware returns for `JsHttpRequest=1-xml`.
     */
    suspend fun request(
        base: String,
        endpoint: String,
        method: String = "POST",
        token: String? = null,
        body: Map<String, String> = emptyMap()
    ): JsonElement {
        val remaining = cooldownRemainingMs()
        if (remaining > 0) {
            throw StalkerException(
                "Sunucu istekleri geçici olarak engelledi. ${remaining / 1000} sn sonra tekrar deneyin.",
                isCooldown = true
            )
        }

        throttle()

        val separator = if (endpoint.contains("?")) "&" else "?"
        val sb = StringBuilder("$base/$endpoint")
            .append(separator)
            .append("preferred_api_version=1")
            .append("&JsHttpRequest=1-xml")
            .append("&token=${token.orEmpty()}")
        if (deviceMac.isNotEmpty()) {
            sb.append("&device_id=1")
                .append("&hw_version=1.0.0")
                .append("&mac=$deviceMac")
                .append("&login=$deviceMac")
                .append("&sn=$deviceMac")
        }

        val builder = Request.Builder().url(sb.toString())
        val request = if (method.equals("POST", ignoreCase = true)) {
            val form = FormBody.Builder().apply { body.forEach { (k, v) -> add(k, v) } }.build()
            builder.post(form).build()
        } else {
            builder.get().build()
        }

        val (respCode, text) = withContext(Dispatchers.IO) {
            try {
                okHttp.newCall(request).execute().use { r ->
                    r.code to (r.body?.string().orEmpty())
                }
            } catch (e: java.io.IOException) {
                val msg = when (e) {
                    is java.net.UnknownHostException ->
                        "Sunucu adresi bulunamadı (DNS). Portal adresini kontrol edin."
                    is java.net.ConnectException ->
                        "Sunucuya bağlanılamadı. Adres/port yanlış veya sunucu çalışmıyor."
                    is java.net.SocketTimeoutException ->
                        "Sunucu yanıt vermedi (zaman aşımı). Ağ bağlantınızı ve adresi kontrol edin."
                    else ->
                        "Sunucuya ulaşılamadı (${e::class.simpleName ?: "ağ hatası"}). İnternet bağlantınızı ve portal adresini kontrol edin."
                }
                throw StalkerException(msg)
            }
        }

        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        val payload = root?.get("js") ?: root

        val hasError = root?.get("error") != null || payload?.let {
            (it as? JsonObject)?.get("error") != null
        } == true

        if (respCode == 429 || respCode == 403 || respCode == 451 || hasError) {
            triggerCooldown()
            throw StalkerException(
                "Sunucu istekleri engelledi (cooldown). ${settingsProvider().cooldownMs / 1000} sn bekleniyor.",
                isCooldown = true
            )
        }

        if (respCode !in 200..299) {
            throw StalkerException("HTTP $respCode")
        }

        return payload ?: throw StalkerException("Geçersiz sunucu yanıtı")
    }

    companion object {
        fun normalizeBase(url: String): String {
            var base = url.trim()
            if (base.isEmpty()) return base
            if (!base.startsWith("http")) base = "http://$base"
            base = base.trimEnd('/')
            if (base.endsWith("portal.php", ignoreCase = true)) {
                base = base.dropLast("portal.php".length).trimEnd('/')
            }
            if (base.endsWith("stb_auth.php", ignoreCase = true)) {
                base = base.dropLast("stb_auth.php".length).trimEnd('/')
            }
            return base
        }

        fun generateMac(): String {
            val bytes = List(3) { Random.nextInt(0, 256) }
            val hex = bytes.joinToString("") { "%02X".format(it) }
            return "00:1A:79:$hex"
        }

        fun parseCmd(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim()
            if (trimmed.startsWith("ffmpeg", ignoreCase = true)) {
                val idx = trimmed.indexOf("http")
                return trimmed.substring(idx).substringBefore(' ')
            }
            val match = Regex("https?://\\S+").find(trimmed)
            return match?.value?.trimEnd(',', ';')
        }

        fun urlFromJson(obj: JsonObject): String? {
            obj["cmd"]?.jsonPrimitive?.contentOrNull?.let { return parseCmd(it) }
            obj["url"]?.jsonPrimitive?.contentOrNull?.let { return parseCmd(it) }
            obj["cmd"].let { }
            return null
        }
    }
}
