package com.stalkerapp.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.nativeKeyEvent

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
fun isTvSelectKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyUp) return false
    val code = event.nativeKeyEvent.keyCode
    return code == KeyEvent.KEYCODE_DPAD_CENTER ||
        code == KeyEvent.KEYCODE_ENTER ||
        code == KeyEvent.KEYCODE_NUMPAD_ENTER ||
        code == KeyEvent.KEYCODE_BUTTON_A
}

/**
 * TV kumandasında "Geri" tuşuna basılıp basılmadığını kontrol eder.
 */
fun isTvBackKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyUp) return false
    val code = event.nativeKeyEvent.keyCode
    return code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE
}

/**
 * TV kumandasında yön (D-Pad) tuşuna basılıp basılmadığını kontrol eder.
 */
fun isTvDirectionKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
    val code = event.nativeKeyEvent.keyCode
    return code == KeyEvent.KEYCODE_DPAD_UP ||
        code == KeyEvent.KEYCODE_DPAD_DOWN ||
        code == KeyEvent.KEYCODE_DPAD_LEFT ||
        code == KeyEvent.KEYCODE_DPAD_RIGHT
}
