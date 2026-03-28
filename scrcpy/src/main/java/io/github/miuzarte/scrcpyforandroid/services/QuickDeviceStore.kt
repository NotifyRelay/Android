package io.github.miuzarte.scrcpyforandroid.services

import android.content.Context
import androidx.core.content.edit
import io.github.miuzarte.scrcpyforandroid.constants.AppDefaults
import io.github.miuzarte.scrcpyforandroid.constants.AppPreferenceKeys
import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcut

internal fun loadQuickDevices(context: Context): List<DeviceShortcut> {
    val raw = context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
        .getString(AppPreferenceKeys.QUICK_DEVICES, "")
        .orEmpty()

    if (raw.isBlank()) return emptyList()

    val result = mutableListOf<DeviceShortcut>()
    raw.lineSequence().forEach { line ->
        val parts = line.split("|", limit = 3)
        when (parts.size) {
            3 -> {
                val name = parts[0].trim()
                val host = parts[1].trim()
                val port = parts[2].trim().toIntOrNull() ?: AppDefaults.ADB_PORT
                if (host.isNotBlank()) {
                    result.add(
                        DeviceShortcut(
                            id = "$host:$port",
                            name = name,
                            host = host,
                            port = port,
                            online = false,
                        ),
                    )
                }
            }

            2 -> {
                // Backward compatibility with old format: name|host:port
                val name = parts[0].trim()
                val host = parts[1].substringBefore(":").trim()
                val port = parts[1].substringAfter(":", AppDefaults.ADB_PORT.toString()).trim()
                    .toIntOrNull() ?: AppDefaults.ADB_PORT
                if (host.isNotBlank()) {
                    result.add(
                        DeviceShortcut(
                            id = "$host:$port",
                            name = name,
                            host = host,
                            port = port,
                            online = false,
                        ),
                    )
                }
            }
        }
    }
    return result
}

internal fun saveQuickDevices(context: Context, quickDevices: List<DeviceShortcut>) {
    val raw = quickDevices.joinToString("\n") { "${it.name}|${it.host}|${it.port}" }
    context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
        .edit {
            putString(AppPreferenceKeys.QUICK_DEVICES, raw)
        }
}

internal fun parseQuickTarget(raw: String): ConnectionTarget? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    val host = value.substringBefore(':').trim()
    if (host.isEmpty()) return null
    val port = value.substringAfter(':', AppDefaults.ADB_PORT.toString()).trim().toIntOrNull()
        ?: AppDefaults.ADB_PORT
    return ConnectionTarget(host, port)
}

internal fun upsertQuickDevice(
    context: Context,
    quickDevices: MutableList<DeviceShortcut>,
    host: String,
    port: Int,
    online: Boolean,
) {
    val id = "$host:$port"
    val idx = quickDevices.indexOfFirst { it.id == id }
    val existingName = if (idx >= 0) quickDevices[idx].name else ""
    val item = DeviceShortcut(
        id = id,
        name = existingName,
        host = host,
        port = port,
        online = online,
    )
    if (idx >= 0) quickDevices[idx] = item else quickDevices.add(0, item)
    saveQuickDevices(context, quickDevices)
}

internal fun updateQuickDeviceNameIfEmpty(
    context: Context,
    quickDevices: MutableList<DeviceShortcut>,
    host: String,
    port: Int,
    fallbackName: String,
) {
    val idx = quickDevices.indexOfFirst { it.host == host && it.port == port }
    if (idx >= 0 && quickDevices[idx].name.isBlank()) {
        quickDevices[idx] = quickDevices[idx].copy(name = fallbackName)
        saveQuickDevices(context, quickDevices)
    }
}

internal fun replaceQuickDevicePort(
    context: Context,
    quickDevices: MutableList<DeviceShortcut>,
    host: String,
    oldPort: Int,
    newPort: Int,
    online: Boolean,
) {
    val idx = quickDevices.indexOfFirst { it.host == host && it.port == oldPort }
    if (idx < 0) return

    val old = quickDevices[idx]
    val updated = old.copy(
        id = "$host:$newPort",
        port = newPort,
        online = online,
    )

    quickDevices[idx] = updated
    val dedup = quickDevices.distinctBy { it.id }
    quickDevices.clear()
    quickDevices.addAll(dedup)
    saveQuickDevices(context, quickDevices)
}
