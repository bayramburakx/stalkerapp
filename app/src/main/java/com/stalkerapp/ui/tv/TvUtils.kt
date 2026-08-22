package com.stalkerapp.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Cihazın bir Android TV / TV Box olup olmadığını tespit eder.
 */
fun isTvDevice(context: Context): Boolean {
    val pm = context.packageManager
    if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
    if (pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) return true
    val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

/**
 * TV kumandasında "Seç / OK / D-Pad Center" tuşuna basılıp basılmadığını kontrol eder.
 */
fun isTvSelectKey(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyUp) return false
    val code = event.nativeKeyEvent.keyCode
    return code == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
        code == android.view.KeyEvent.KEYCODE_ENTER ||
        code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER ||
        code == android.view.KeyEvent.KEYCODE_BUTTON_A
}

/**
 * TV kumandasında "Geri" tuşuna basılıp basılmadığını kontrol eder.
 */
fun isTvBackKey(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyUp) return false
    val code = event.nativeKeyEvent.keyCode
    return code == android.view.KeyEvent.KEYCODE_BACK || code == android.view.KeyEvent.KEYCODE_ESCAPE
}

/**
 * TV kumandasında yön (D-Pad) tuşuna basılıp basılmadığını kontrol eder.
 */
fun isTvDirectionKey(event: KeyEvent): Boolean {
    val code = event.nativeKeyEvent.keyCode
    return code == android.view.KeyEvent.KEYCODE_DPAD_UP ||
        code == android.view.KeyEvent.KEYCODE_DPAD_DOWN ||
        code == android.view.KeyEvent.KEYCODE_DPAD_LEFT ||
        code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
}
