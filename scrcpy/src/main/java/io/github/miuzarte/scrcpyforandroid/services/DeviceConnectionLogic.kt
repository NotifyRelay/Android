package io.github.miuzarte.scrcpyforandroid.services

import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ConnectedDeviceInfo(
    val model: String,
    val serial: String,
    val manufacturer: String,
    val brand: String,
    val device: String,
    val androidRelease: String,
    val sdkInt: Int,
)

internal suspend fun fetchConnectedDeviceInfo(
    host: String,
    port: Int
): ConnectedDeviceInfo = withContext(Dispatchers.IO) {
    suspend fun prop(name: String): String =
        runCatching { NativeAdbService.shell("getprop $name").trim() }.getOrDefault("")

    val model = prop("ro.product.model")
    val serial = prop("ro.serialno")
    val manufacturer = prop("ro.product.manufacturer")
    val brand = prop("ro.product.brand")
    val device = prop("ro.product.device")
    val androidRelease = prop("ro.build.version.release")
    val sdkInt = prop("ro.build.version.sdk").toIntOrNull() ?: -1

    ConnectedDeviceInfo(
        model = model.ifBlank { "$host:$port" },
        serial = serial,
        manufacturer = manufacturer,
        brand = brand,
        device = device,
        androidRelease = androidRelease,
        sdkInt = sdkInt,
    )
}
