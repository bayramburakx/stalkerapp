package com.stalkerapp.playback

import android.content.Context
import android.os.Looper
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.text.SubtitleDecoderFactory
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import java.util.ArrayList

/**
 * A/V senkron gecikmesinin canlı değeri. Oynatıcı içinden (+/-50 ms) veya
 * Ayarlar'dan değiştirilir; [AudioSyncSink.getCurrentPositionUs] her karede
 * bu değeri okur (oyuncuyu yeniden kurmadan anında etki eder).
 */
object AudioSyncState {
    /** Ses gecikmesi (µs). Pozitif = ses videoya göre gecikir. */
    @Volatile var delayUs: Long = 0L
}

/**
 * [DefaultAudioSink]'i saran ve `getCurrentPositionUs`'a ayarlanabilir bir
 * ofset uygulayan ses çıkışı. ExoPlayer videoyu ses saatine göre senkronlar;
 * pozitif ofset sesin gecikmesini (negatif ise öne alınmasını) sağlar.
 */
class AudioSyncSink(
    private val delegate: DefaultAudioSink
) : AudioSink by delegate {

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val real = delegate.getCurrentPositionUs(sourceEnded)
        if (real == AudioSink.CURRENT_POSITION_NOT_SET) return real
        return real + AudioSyncState.delayUs
    }
}

/**
 * [DefaultRenderersFactory] alt sınıfı: ses çıkışını [AudioSyncSink] ile sarar
 * (A/V sync) ve audio passthrough ayarını uygular. Passthrough kapalıyken ses
 * çıkışı ham AC3/EAC3/DTS bitstream geçişini desteklemez; kod çözücüye düşer.
 */
class SyncRenderersFactory(
    context: Context,
    private val passthrough: Boolean,
    private val subtitleTypes: Set<String> = setOf("cc", "dvbsub")
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        val builder = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        if (!passthrough) {
            // Cihaz desteklese bile ham bitstream geçilmez (uyumsuz AV alıcılarda
            // sessiz sesi önler; ses cihazda çözülür).
            builder.setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
        }
        return AudioSyncSink(builder.build())
    }

    /**
     * Altyazı türü filtresi (Ayarlar → Oynatıcı → Altyazı Türleri):
     * kapalı altyazı (CEA-608/708) ve yayın altyazıları (DVB/PGS) ayrı ayrı
     * kapatılabilir. Kapalı türler oynatıcıya hiç sunulmaz (izlenebilir
     * altyazı listesinden de çıkar).
     */
    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>
    ) {
        out.add(TextRenderer(output, outputLooper, FilteringSubtitleDecoderFactory(subtitleTypes)))
    }
}

/**
 * [SubtitleDecoderFactory.DEFAULT]'ı saran ve etkin olmayan türlerin
 * altyazı formatlarını desteklenmez işaretleyen fabrika.
 */
private class FilteringSubtitleDecoderFactory(
    private val enabledTypes: Set<String>
) : SubtitleDecoderFactory {

    override fun supportsFormat(format: Format): Boolean {
        if (!SubtitleDecoderFactory.DEFAULT.supportsFormat(format)) return false
        return when (format.sampleMimeType) {
            MimeTypes.APPLICATION_CEA608,
            MimeTypes.APPLICATION_MP4CEA608,
            MimeTypes.APPLICATION_CEA708 -> "cc" in enabledTypes

            MimeTypes.APPLICATION_DVBSUBS,
            MimeTypes.APPLICATION_PGS -> "dvbsub" in enabledTypes

            else -> true
        }
    }

    override fun createDecoder(format: Format): androidx.media3.extractor.text.SubtitleDecoder {
        return SubtitleDecoderFactory.DEFAULT.createDecoder(format)
    }
}
