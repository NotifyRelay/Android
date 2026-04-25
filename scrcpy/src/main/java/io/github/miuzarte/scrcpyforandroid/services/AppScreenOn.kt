package io.github.miuzarte.scrcpyforandroid.services

import android.view.Window
import android.view.WindowManager

object AppScreenOn {
    private val windows = linkedSetOf<Window>()
    private var keepScreenOnEnabled = false

    fun register(window: Window) = synchronized(this) {
        windows += window
        applyKeepScreenOn(window, enabled = keepScreenOnEnabled)
    }

    fun unregister(window: Window) = synchronized(this) {
        windows.remove(window)
        applyKeepScreenOn(window, enabled = false)
    }

    fun acquire() = synchronized(this) {
        if (keepScreenOnEnabled) Unit
        keepScreenOnEnabled = true
        windows.forEach { applyKeepScreenOn(window = it, enabled = keepScreenOnEnabled) }
    }

    fun release() = synchronized(this) {
        if (!keepScreenOnEnabled) Unit
        keepScreenOnEnabled = false
        windows.forEach { applyKeepScreenOn(window = it, enabled = keepScreenOnEnabled) }
    }

    private fun applyKeepScreenOn(window: Window, enabled: Boolean) =
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}
