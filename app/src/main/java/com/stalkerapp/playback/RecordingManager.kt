package com.stalkerapp.playback

import android.content.Context
import android.os.Environment
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Cihaz üzerinde kayıt (recording): akış URL'sini bir dosyaya indirir.
 * Zamanlanmış kayıtlar StalkerApp'teki zamanlayıcı tarafından başlatılır;
 * bu sınıf yalnızca indirme işlemini yönetir. MPEG-TS / ilerlemeli akışlarda
 * güvenilirdir; HLS kaynaklarda bazı sunucularda çalışmayabilir.
 */
object RecordingManager {

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = ConcurrentHashMap<String, Job>()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Kayıt dosyalarının bulunduğu dizin (Movies/recordings). */
    fun recordingsDir(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        return File(base, "recordings").apply { mkdirs() }
    }

    fun fileFor(context: Context, id: String): File = File(recordingsDir(context), "$id.ts")

    /** [recording]'in akışını indirmeye başlar. Zaten aktifse no-op. */
    fun start(recordingId: String, url: String, stopTs: Long): Boolean {
        val ctx = appContext ?: return false
        if (active.containsKey(recordingId)) return true
        val file = fileFor(ctx, recordingId)
        val job = scope.launch {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@launch
                    val source = resp.body?.source() ?: return@launch
                    file.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (isActive) {
                            // Bitiş zamanı geldiyse kaydı kapat.
                            if (stopTs > 0 && System.currentTimeMillis() / 1000 >= stopTs) break
                            val n = source.read(buffer, 0, buffer.size)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                        }
                    }
                }
            } catch (_: Exception) {
                // Aktif iptal edildiğinde (stop) dosya silinmez; durum arayan tarafça güncellenir.
            } finally {
                active.remove(recordingId)
            }
        }
        active[recordingId] = job
        return true
    }

    /** Aktif indirmeyi durdurur. */
    fun stop(recordingId: String) {
        active.remove(recordingId)?.cancel()
    }

    fun isRecording(recordingId: String): Boolean = active.containsKey(recordingId)
}
