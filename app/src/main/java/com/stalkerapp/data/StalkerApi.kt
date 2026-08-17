package com.stalkerapp.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import com.stalkerapp.util.L10n
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

    private fun l10n(text: String): String = L10n.t(settingsProvider().language, text)

    @Volatile private var lastRequestAt = 0L
    @Volatile private var cooldownUntil = 0L
    @Volatile private var deviceMac: String = ""
    // Raised after each cooldown so the client stays under the portal's real
    // rate limit instead of hammering it again (default interval is often too
    // fast for strict portals). Reset by [clearCooldown] or [resetAdaptiveInterval].
    @Volatile private var adaptiveIntervalMs = 0L

    fun setDevice(mac: String) {
        deviceMac = mac
    }

    fun cooldownRemainingMs(): Long =
        (cooldownUntil - System.currentTimeMillis()).coerceAtLeast(0)

    fun cooldownRemainingSeconds(): Long = cooldownRemainingMs() / 1000

    fun triggerCooldown(durationMs: Long = settingsProvider().cooldownMs) {
        cooldownUntil = System.currentTimeMillis() + durationMs
        val base = settingsProvider().requestIntervalMs.coerceAtLeast(0)
        adaptiveIntervalMs = if (adaptiveIntervalMs == 0L) (base * 2).coerceAtLeast(250)
        else (adaptiveIntervalMs * 2).coerceAtMost(1500)
        Log.w("StalkerClient", "Cooldown triggered for ${durationMs / 1000}s (interval now ${adaptiveIntervalMs}ms)")
    }

    fun clearCooldown() {
        cooldownUntil = 0
        adaptiveIntervalMs = 0
    }

    /** Resets the adaptive interval after a sync finishes so browsing isn't slowed. */
    fun resetAdaptiveInterval() {
        adaptiveIntervalMs = 0
    }

    private suspend fun throttle() {
        val interval = maxOf(settingsProvider().requestIntervalMs.coerceAtLeast(0), adaptiveIntervalMs)
        throttleMutex.withLock {
            val now = System.currentTimeMillis()
            val wait = interval - (now - lastRequestAt)
            if (wait > 0) {
                delay(wait)
            }
            lastRequestAt = System.currentTimeMillis()
        }
    }

    private val throttleMutex = Mutex()

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
                l10n("Sunucu istekleri geçici olarak engelledi") + ". ${remaining / 1000} " + l10n("sn sonra tekrar deneyin") + ".",
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
                        l10n("Sunucu adresi bulunamadı (DNS). Portal adresini kontrol edin.")
                    is java.net.ConnectException ->
                        l10n("Sunucuya bağlanılamadı. Adres/port yanlış veya sunucu çalışmıyor.")
                    is java.net.SocketTimeoutException ->
                        l10n("Sunucu yanıt vermedi (zaman aşımı). Ağ bağlantınızı ve adresi kontrol edin.")
                    else ->
                        l10n("Sunucuya ulaşılamadı") + " (${e::class.simpleName ?: l10n("ağ hatası")}). " + l10n("İnternet bağlantınızı ve portal adresini kontrol edin")
                }
                throw StalkerException(msg)
            }
        }

        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        val payload = root?.get("js") ?: root

        val errNode = (root as? JsonObject)?.get("error") ?: (payload as? JsonObject)?.get("error")
        val hasError = (errNode as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true

        // Sunucu `error` alanı döndürdüğünde de (örn. rate-limit, geçici
        // portal hatası) global cooldown tetikle ve tekrar dene. Bu sayede
        // büyük VOD senkronizasyonları portalı bastırmaz; cooldown sonrası
        // sayfalar doğru şekilde çekilir. (Daha önce yalnızca 429/403/451'e
        // sınırlanması VOD kataloğunun eksik gelmesine yol açıyordu.)
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

        return payload ?: throw StalkerException(l10n("Geçersiz sunucu yanıtı"))
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
            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                return trimmed.substringBefore(" ").substringBefore("\"").substringBefore("'").trimEnd(',', ';')
            }
            if (trimmed.startsWith("ffmpeg", ignoreCase = true) || trimmed.startsWith("auto", ignoreCase = true)) {
                val idx = trimmed.indexOf("http")
                if (idx >= 0) {
                    val sub = trimmed.substring(idx)
                    return sub.substringBefore(" ").substringBefore("\"").substringBefore("'").trimEnd(',', ';')
                }
            }
            val match = Regex("https?://[^\\s\"',;]+").find(trimmed)
            return match?.value
        }

        fun urlFromJson(obj: JsonObject): String? {
            (obj["cmd"] as? JsonPrimitive)?.contentOrNull?.let { parseCmd(it)?.let { u -> return u } }
            (obj["url"] as? JsonPrimitive)?.contentOrNull?.let { parseCmd(it)?.let { u -> return u } }
            (obj["link"] as? JsonPrimitive)?.contentOrNull?.let { parseCmd(it)?.let { u -> return u } }
            (obj["data"] as? JsonPrimitive)?.contentOrNull?.let { parseCmd(it)?.let { u -> return u } }
            (obj["stream_url"] as? JsonPrimitive)?.contentOrNull?.let { parseCmd(it)?.let { u -> return u } }
            return null
        }

        fun extractUrl(el: JsonElement?): String? {
            if (el == null) return null
            when (el) {
                is JsonPrimitive -> {
                    parseCmd(el.contentOrNull)?.let { return it }
                }
                is JsonObject -> {
                    urlFromJson(el)?.let { return it }
                    el["js"]?.let { extractUrl(it)?.let { u -> return u } }
                    el["data"]?.let { extractUrl(it)?.let { u -> return u } }
                    el["results"]?.let { extractUrl(it)?.let { u -> return u } }
                }
                is kotlinx.serialization.json.JsonArray -> {
                    for (item in el) {
                        extractUrl(item)?.let { return it }
                    }
                }
            }
            return null
        }
    }
}
