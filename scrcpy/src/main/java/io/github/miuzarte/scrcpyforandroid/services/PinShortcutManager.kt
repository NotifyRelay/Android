package io.github.miuzarte.scrcpyforandroid.services

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.pages.ShortcutLaunchActivity
import notifyrelay.data.config.ScrcpyDefaults

object PinShortcutManager {

    fun createPinnedShortcut(
        context: Context,
        deviceName: String,
        deviceIp: String,
        devicePort: Int = ScrcpyDefaults.ADB_PORT
    ): Boolean {
        val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
            ?: return false

        if (!shortcutManager.isRequestPinShortcutSupported) {
            return false
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setClass(context, ShortcutLaunchActivity::class.java)
                putExtra("shortcut_device_ip", deviceIp)
                putExtra("shortcut_device_port", devicePort)
                putExtra("shortcut_device_name", deviceName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            val shortcut = ShortcutInfo.Builder(context, "pinned_${deviceIp}_$devicePort")
                .setShortLabel(deviceName)
                .setLongLabel(deviceName)
                .setIcon(Icon.createWithResource(context, R.drawable.ic_screen_mirroring))
                .setIntent(intent)
                .build()

            shortcutManager.requestPinShortcut(shortcut, null)
        } catch (_: Exception) {
            false
        }
    }
}
