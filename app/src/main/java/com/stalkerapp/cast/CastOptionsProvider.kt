package com.stalkerapp.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Google Cast framework ayarları. Uygulamanın kayıtlı bir alıcı uygulaması
 * olmadığı için Google'ın varsayılan medya alıcısı kullanılır; HLS/DASH ve
 * ilerlemeli MP4/WebM akışlarını Chromecast/TV cihazlarında oynatır.
 *
 * AndroidManifest'te `OPTIONS_PROVIDER_CLASS_NAME` metadata'sıyla kayıtlıdır;
 * `CastContext.getSharedInstance(context)` bu sınıfı okuyarak seçenekleri kurar.
 */
class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(DEFAULT_MEDIA_RECEIVER_APP_ID)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider> = emptyList()

    companion object {
        /** Google'ın varsayılan medya alıcı uygulama kimliği. */
        const val DEFAULT_MEDIA_RECEIVER_APP_ID = "CC1AD845"
    }
}
