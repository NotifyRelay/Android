package io.github.miuzarte.scrcpyforandroid.services

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.pages.ShortcutLaunchActivity
import notifyrelay.data.config.ScrcpyDefaults

object DynamicShortcutManager {
    private const val MAX_SHORTCUTS = 4

    fun updateShortcuts(context: Context) {
        val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
            ?: return

        val onlineDevices = loadOnlineDevicesFromApp(context)
            .filter { it.deviceType?.lowercase() != "pc" }
            .take(MAX_SHORTCUTS)

        val shortcuts = onlineDevices.map { device ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setClass(context, ShortcutLaunchActivity::class.java)
                putExtra("shortcut_device_ip", device.ip)
                putExtra("shortcut_device_port", ScrcpyDefaults.ADB_PORT)
                putExtra("shortcut_device_name", device.displayName)
                putExtra("shortcut_device_uuid", device.uuid)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            ShortcutInfo.Builder(context, "device_${device.uuid}")
                .setShortLabel(device.displayName)
                .setIcon(Icon.createWithResource(context, R.drawable.ic_screen_mirroring))
                .setIntent(intent)
                .build()
        }

        try {
            shortcutManager.dynamicShortcuts = shortcuts
        } catch (_: Exception) {}
    }
}
