package io.github.miuzarte.scrcpyforandroid.services

import android.content.Context
import notifyrelay.data.StorageManager
import notifyrelay.data.config.ScrcpyDefaults
import notifyrelay.data.config.ScrcpyPreferenceKeys
import notifyrelay.data.model.ConnectionTarget
import notifyrelay.data.model.DeviceShortcut

internal fun loadQuickDevices(context: Context): List<DeviceShortcut> {
    val raw = StorageManager.getString(
        context,
        ScrcpyPreferenceKeys.QUICK_DEVICES,
        "",
        StorageManager.PrefsType.SCRCPY,
    )

    if (raw.isBlank()) return emptyList()

    val result = mutableListOf<DeviceShortcut>()
    raw.lineSequence().forEach { line ->
        val parts = line.split("|", limit = 3)
        when (parts.size) {
            3 -> {
                val name = parts[0].trim()
                val host = parts[1].trim()
                val port = parts[2].trim().toIntOrNull() ?: ScrcpyDefaults.ADB_PORT
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
                val name = parts[0].trim()
                val host = parts[1].substringBefore(":").trim()
                val port = parts[1].substringAfter(":", ScrcpyDefaults.ADB_PORT.toString()).trim()
                    .toIntOrNull() ?: ScrcpyDefaults.ADB_PORT
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
    StorageManager.putString(
        context,
        ScrcpyPreferenceKeys.QUICK_DEVICES,
        raw,
        StorageManager.PrefsType.SCRCPY,
    )
}

internal fun parseQuickTarget(raw: String): ConnectionTarget? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    val host = value.substringBefore(':').trim()
    if (host.isEmpty()) return null
    val port = value.substringAfter(':', ScrcpyDefaults.ADB_PORT.toString()).trim().toIntOrNull()
        ?: ScrcpyDefaults.ADB_PORT
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
