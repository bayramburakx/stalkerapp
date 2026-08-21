package com.stalkerapp.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.lang.reflect.Method

/**
 * HDMI-CEC yöneticisi (reflection ile).
 *
 * HDMI-CEC API'si (android.hardware.hdmi) sistem API'si olduğundan,
 * derleme zamanında mevcut olmayabilir. Bu nedenle reflection ile
 * çalışma zamanında erişilir.
 */
object HdmiCecManager {

    private const val TAG = "HdmiCecManager"

    private var hdmiManager: Any? = null
    private var playbackClient: Any? = null

    // Reflection cache
    private var hdmiControlManagerClass: Class<*>? = null
    private var playbackClientClass: Class<*>? = null
    private var oneTouchPlayMethod: Method? = null
    private var sendKeyEventMethod: Method? = null
    private var queryDisplayStatusMethod: Method? = null
    private var getPlaybackClientMethod: Method? = null

    private fun initReflection(): Boolean {
        if (hdmiControlManagerClass != null) return true
        return try {
            val controlClass = Class.forName("android.hardware.hdmi.HdmiControlManager")
            val clientClass = Class.forName("android.hardware.hdmi.HdmiPlaybackClient")
            hdmiControlManagerClass = controlClass
            playbackClientClass = clientClass
            
            // HdmiControlManager.getPlaybackClient()
            getPlaybackClientMethod = controlClass.getMethod("getPlaybackClient")
            
            // HdmiPlaybackClient.oneTouchPlay(OneTouchPlayCallback)
            oneTouchPlayMethod = clientClass.getMethod("oneTouchPlay", Class.forName("android.hardware.hdmi.HdmiPlaybackClient\$OneTouchPlayCallback"))
            
            // HdmiPlaybackClient.sendKeyEvent(int, boolean)
            sendKeyEventMethod = clientClass.getMethod("sendKeyEvent", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            
            // HdmiPlaybackClient.queryDisplayStatus(DisplayStatusCallback)
            queryDisplayStatusMethod = clientClass.getMethod("queryDisplayStatus", Class.forName("android.hardware.hdmi.HdmiPlaybackClient\$DisplayStatusCallback"))
            
            true
        } catch (e: Exception) {
            Log.d("HdmiCecManager", "Reflection init failed: ${e.message}")
            false
        }
    }

    /** Uygulama başlangıcında çağrılmalı. */
    fun init(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        runCatching {
            if (!initReflection()) return@runCatching
            
            val hdmiManagerObj = context.getSystemService("hdmi_control")
            if (hdmiManagerObj != null) {
                hdmiManager = hdmiManagerObj
                playbackClient = getPlaybackClientMethod?.invoke(hdmiManagerObj)
            }
        }.onFailure {
            Log.d("HdmiCecManager", "HDMI-CEC init failed: ${it.message}")
        }
    }

    /** TV'yi açar ve bu cihaza geçer (One Touch Play). */
    fun oneTouchPlay() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (playbackClient == null || oneTouchPlayMethod == null) return
        
        runCatching {
            oneTouchPlayMethod?.invoke(playbackClient, createOneTouchPlayCallback())
        }.onFailure {
            Log.d("HdmiCecManager", "One Touch Play failed: ${it.message}")
        }
    }

    private fun createOneTouchPlayCallback(): Any {
        // Create a dynamic proxy for OneTouchPlayCallback
        val callbackInterface = Class.forName("android.hardware.hdmi.HdmiPlaybackClient\$OneTouchPlayCallback")
        return java.lang.reflect.Proxy.newProxyInstance(
            callbackInterface.classLoader,
            arrayOf(callbackInterface),
            { _, method, args ->
                if (method.name == "onComplete") {
                    val result = args[0] as Int
                    android.util.Log.d("HdmiCecManager", "One Touch Play completed: $result")
                }
                null
            }
        )
    }

    /** TV'yi bekleme moduna alır. */
    fun standby() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (playbackClient == null || sendKeyEventMethod == null) return
        
        runCatching {
            // KEYCODE_POWER_OFF_FUNCTION = 150 (approx)
            sendKeyEventMethod?.invoke(playbackClient, 150, true)
        }.onFailure {
            Log.d("HdmiCecManager", "Standby failed: ${it.message}")
        }
    }

    fun queryDisplayStatus(callback: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            callback(true)
            return
        }
        if (playbackClient == null || queryDisplayStatusMethod == null) {
            callback(true)
            return
        }
        runCatching {
            // Create DisplayStatusCallback proxy
            val callbackInterface = Class.forName("android.hardware.hdmi.HdmiPlaybackClient\$DisplayStatusCallback")
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                callbackInterface.classLoader,
                arrayOf(callbackInterface),
                { _, method, args ->
                    if (method.name == "onComplete") {
                        val status = args[0] as Int
                        // DISPLAY_STATUS_ON = 1
                        callback(status == 1)
                    }
                    null
                }
            )
            queryDisplayStatusMethod?.invoke(playbackClient, proxy)
        }.onFailure {
            callback(true)
        }
    }

    fun isSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return playbackClient != null
    }
}