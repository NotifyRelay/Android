package io.github.miuzarte.scrcpyforandroid.services

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import notifyrelay.data.StorageManager
import notifyrelay.data.config.ScrcpyDefaults
import notifyrelay.data.config.ScrcpyPreferenceKeys
import notifyrelay.data.model.ConnectionTarget
import notifyrelay.data.model.OnlineDeviceInfo

internal fun parseQuickTarget(raw: String): ConnectionTarget? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    val host = value.substringBefore(':').trim()
    if (host.isEmpty()) return null
    val port = value.substringAfter(':', ScrcpyDefaults.ADB_PORT.toString()).trim().toIntOrNull()
        ?: ScrcpyDefaults.ADB_PORT
    return ConnectionTarget(host, port)
}

internal fun loadOnlineDevicesFromApp(context: Context): List<OnlineDeviceInfo> {
    return try {
        val json = StorageManager.getString(
            context,
            ScrcpyPreferenceKeys.ONLINE_DEVICES_CACHE,
            "[]",
            StorageManager.PrefsType.SCRCPY,
        )
        val gson = Gson()
        val type = TypeToken.getParameterized(List::class.java, OnlineDeviceInfo::class.java).type
        gson.fromJson(json, type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}
