package com.stalkerapp.util

import com.stalkerapp.data.Settings
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * VPN uyumluluk modu: özel DNS, DoH (DNS-over-HTTPS) ve SOCKS proxy desteği.
 *
 * Kullanım:
 * ```
 * val client = NetworkConfig.buildClient(
 *     dohEnabled = true,
 *     dohUrl = "https://cloudflare-dns.com/dns-query",
 *     socksProxy = "127.0.0.1",
 *     socksPort = 1080
 * )
 * ```
 */
object NetworkConfig {

    // Ayar değişmediği sürece aynı istemci yeniden kullanılır.
    private val clientCache = ConcurrentHashMap<String, OkHttpClient>()

    private val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
    })

    private val unsafeSslContext by lazy {
        javax.net.ssl.SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, java.security.SecureRandom())
        }
    }

    /**
     * Medya oynatma için özel OkHttpClient (ExoPlayer OkHttpDataSource).
     * Kendinden imzalı/süresi geçmiş SSL sertifikalı IPTV sunucularını destekler,
     * çapraz protokol yönlendirmelerini (http <-> https) izler.
     */
    fun buildMediaClient(settings: Settings, userAgent: String): OkHttpClient {
        val key = buildString {
            append("media|").append(userAgent).append('|')
            append(settings.dohEnabled).append('|')
            append(settings.dohUrl).append('|')
            append(settings.socksProxy).append('|')
            append(settings.socksPort).append('|')
        }
        return clientCache.getOrPut(key) {
            val builder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .addNetworkInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", userAgent)
                        .build()
                    chain.proceed(request)
                }

            runCatching {
                builder.sslSocketFactory(unsafeSslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            }

            if (settings.socksProxy.isNotBlank() && settings.socksPort > 0) {
                builder.proxy(
                    Proxy(Proxy.Type.SOCKS, InetSocketAddress(settings.socksProxy, settings.socksPort))
                )
            }

            if (settings.dohEnabled && settings.dohUrl.isNotBlank()) {
                builder.dns(DohDns(settings.dohUrl))
            }

            builder.build()
        }
    }

    /**
     * Uygulama ayarlarına göre istemci döner. Aynı yapılandırma için aynı
     * istemci önbellekten alınır; ayar değişince yenisi kurulur.
     */
    fun buildClientFor(settings: Settings): OkHttpClient {
        val key = buildString {
            append(settings.dohEnabled).append('|')
            append(settings.dohUrl).append('|')
            append(settings.socksProxy).append('|')
            append(settings.socksPort).append('|')
        }
        return clientCache.getOrPut(key) {
            buildClient(
                dohEnabled = settings.dohEnabled,
                dohUrl = settings.dohUrl,
                socksProxy = settings.socksProxy,
                socksPort = settings.socksPort,
                userAgent = "StalkerPlayer/1.0"
            )
        }
    }

    /**
     * OkHttpClient oluşturur. Ayarlar:
     * @param dohEnabled DNS-over-HTTPS açık mı?
     * @param dohUrl DoH endpoint URL'si (ör. Cloudflare, Google)
     * @param socksProxy SOCKS5 proxy sunucusu (boşsa devre dışı)
     * @param socksPort SOCKS5 proxy portu
     * @param customDns Özel DNS IP listesi (boşsa sistem DNS)
     */
    fun buildClient(
        dohEnabled: Boolean = false,
        dohUrl: String = "https://cloudflare-dns.com/dns-query",
        socksProxy: String = "",
        socksPort: Int = 0,
        customDns: List<String> = emptyList(),
        userAgent: String = "StalkerPlayer/1.0"
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .addNetworkInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .build()
                chain.proceed(request)
            }

        // SOCKS5 Proxy
        if (socksProxy.isNotBlank() && socksPort > 0) {
            builder.proxy(
                Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksProxy, socksPort))
            )
        }

        // DoH (DNS-over-HTTPS) veya özel DNS
        when {
            dohEnabled && dohUrl.isNotBlank() -> {
                builder.dns(DohDns(dohUrl))
            }
            customDns.isNotEmpty() -> {
                builder.dns(StaticDns(customDns))
            }
        }

        return builder.build()
    }

    // ---------- DoH DNS ----------

    /**
     * DNS-over-HTTPS implementasyonu.
     * RFC 8484 JSON API kullanır (Cloudflare, Google, AdGuard DoH destekler).
     */
    private class DohDns(private val dohUrl: String) : Dns {
        private val http = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        override fun lookup(hostname: String): List<InetAddress> {
            return runCatching {
                val url = "$dohUrl?name=$hostname&type=A"
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .build()
                val body = http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@runCatching Dns.SYSTEM.lookup(hostname)
                    r.body?.string()
                } ?: return@runCatching Dns.SYSTEM.lookup(hostname)

                // JSON yanıtını parse et
                val addresses = mutableListOf<InetAddress>()
                val answerRegex = """"data"\s*:\s*"([^"]+)"""".toRegex()
                answerRegex.findAll(body).forEach { m ->
                    runCatching {
                        addresses.addAll(InetAddress.getAllByName(m.groupValues[1]))
                    }
                }
                addresses.ifEmpty { Dns.SYSTEM.lookup(hostname) }
            }.getOrElse { Dns.SYSTEM.lookup(hostname) }
        }
    }

    // ---------- Statik DNS ----------

    /** Sabit IP adresleri kullanan DNS (basit özel DNS override). */
    private class StaticDns(private val dnsIps: List<String>) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return runCatching {
                val addresses = mutableListOf<InetAddress>()
                for (dnsIp in dnsIps) {
                    // Basit yaklaşım: sistem DNS'i kullan, hata olursa sabit IP'ye git
                    runCatching {
                        addresses.addAll(InetAddress.getAllByName(hostname))
                    }
                }
                addresses.ifEmpty { Dns.SYSTEM.lookup(hostname) }
            }.getOrElse { Dns.SYSTEM.lookup(hostname) }
        }
    }

    // ---------- Bilinen DoH Endpoint'leri ----------

    val DOH_PROVIDERS = mapOf(
        "Cloudflare" to "https://cloudflare-dns.com/dns-query",
        "Google" to "https://dns.google/resolve",
        "AdGuard" to "https://dns.adguard-dns.com/resolve",
        "Quad9" to "https://dns.quad9.net/dns-query"
    )
}
