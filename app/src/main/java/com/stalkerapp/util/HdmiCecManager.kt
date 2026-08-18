package com.stalkerapp.util

import android.content.Context
import android.hardware.hdmi.HdmiControlManager
import android.hardware.hdmi.HdmiPlaybackClient
import android.os.Build
import android.util.Log

/**
 * HDMI-CEC yöneticisi.
 *
 * `HdmiControlManager` API (API 17+, ancak sistem imzası gerektiren bazı
 * operasyonlar için API 28+). Normal uygulamalar:
 *  - `HdmiPlaybackClient.sendKeyEvent()` — TV'ye tuş basma simüle eder
 *  - `HdmiPlaybackClient.oneTouchPlay()` — TV'yi açıp bu kaynağa geçer
 *  - `HdmiPlaybackClient.queryDisplayStatus()` — TV'nin açık olup olmadığını sorgular
 *
 * Gerçek CEC komut dinleme (ses seviyesi vb.) sistem uygulaması gerektirir.
 * Bu implementasyon desteklenen operasyonları kullanır, diğerlerini sessizce atlar.
 */
object HdmiCecManager {

    private const val TAG = "HdmiCecManager"

    private var hdmiManager: HdmiControlManager? = null
    private var playbackClient: HdmiPlaybackClient? = null

    /** Uygulama başlangıcında çağrılmalı. */
    fun init(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        runCatching {
            hdmiManager = context.getSystemService(Context.HDMI_CONTROL_SERVICE) as? HdmiControlManager
            playbackClient = hdmiManager?.playbackClient
        }.onFailure {
            Log.d(TAG, "HDMI-CEC init başarısız: ${it.message}")
        }
    }

    /**
     * TV'yi açar ve bu cihaza geçer (One Touch Play).
     * Oynatma başladığında çağrılabilir.
     */
    fun oneTouchPlay() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        runCatching {
            playbackClient?.oneTouchPlay(object : HdmiPlaybackClient.OneTouchPlayCallback {
                override fun onComplete(result: Int) {
                    Log.d(TAG, "One Touch Play tamamlandı: $result")
                }
            })
        }.onFailure {
            Log.d(TAG, "One Touch Play başarısız: ${it.message}")
        }
    }

    /**
     * TV'yi bekleme moduna alır.
     * Uygulama kapatılırken / kullanıcı talep edince çağrılabilir.
     */
    fun standby() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        runCatching {
            playbackClient?.sendKeyEvent(HdmiPlaybackClient.KEYCODE_POWER_OFF_FUNCTION, true)
        }.onFailure {
            Log.d(TAG, "Standby başarısız: ${it.message}")
        }
    }

    /**
     * TV'nin açık olup olmadığını sorgular.
     * Sonuç asenkron olarak [callback]'e gönderilir.
     */
    fun queryDisplayStatus(callback: (isOn: Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            callback(true)
            return
        }
        runCatching {
            playbackClient?.queryDisplayStatus(object : HdmiPlaybackClient.DisplayStatusCallback {
                override fun onComplete(status: Int) {
                    callback(status == HdmiPlaybackClient.DISPLAY_STATUS_ON)
                }
            })
        }.onFailure {
            callback(true) // bilinmiyorsa açık say
        }
    }

    /** HDMI-CEC desteği var mı? */
    fun isSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return playbackClient != null
    }
}
