package com.stalkerapp.playback

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

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
    private val passthrough: Boolean
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
}
