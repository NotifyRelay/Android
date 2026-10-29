package com.xzyht.notifyrelay.nativecore.device.appsync

import com.xzyht.notifyrelay.nativecore.NativeCore
import com.xzyht.notifyrelay.nativecore.device.ProtocolSender
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import notifyrelay.base.log.Logger
import notifyrelay.data.database.entity.AppDeviceEntity
import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.domain.feature.appslist.InstalledPackagesPortRegistry
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.device.service.contract.DeviceMessagePort

/**
 * 图标同步管理器。
 *
 * 负责在已认证设备之间同步应用图标：请求过滤（已缓存/已安装/pending/设备关联）
 * 与报文构造由 Rust core 承担，本平台只负责字节编解码与本地缓存。
 */
object IconSyncManagerCore {
    private const val TAG = "IconSyncManager"
    private const val ICON_REQUEST_TIMEOUT_MS = 10000L

    private val json = Json { ignoreUnknownKeys = true }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 检查并（必要时）请求单个图标。 */
    fun checkAndSyncIcon(
        packageName: String,
        deviceManager: DeviceMessagePort,
        sourceDevice: DeviceInfo,
    ) {
        val installedPackages = InstalledPackagesPortRegistry.current?.installedPackagesSync() ?: emptySet()
        if (installedPackages.contains(packageName)) return

        ioScope.launch {
            try {
                val databaseRepository = DatabaseRepository.getInstance()
                val appDeviceUuids =
                    databaseRepository
                        .getAppDevicesByPackageName(packageName)
                        .first()
                        .map { it.sourceDevice }

                val cachedPackages =
                    if (RemoteAppIconCache.cachedBytes(packageName) != null) listOf(packageName) else emptyList()

                val requestJson =
                    NativeCore.appSyncRequestIcons(
                        listOf(packageName),
                        installedPackages.toList(),
                        cachedPackages,
                        if (appDeviceUuids.isEmpty()) emptyMap() else mapOf(packageName to appDeviceUuids),
                        sourceDevice.uuid,
                        System.currentTimeMillis(),
                    )
                if (requestJson == null || requestJson == "{}") return@launch

                requestIconsFromDevice(requestJson, deviceManager, sourceDevice)
                // 先补父行再写关联：app_devices 对 apps 有外键约束
                RemoteAppIconCache.linkToDevice(packageName, sourceDevice.uuid)
            } catch (e: IllegalStateException) {
                Logger.e(TAG, "请求图标失败：" + packageName, e)
            }
        }
    }

    /** 批量请求多个包名图标（过滤逻辑在 Rust 内部）。 */
    suspend fun requestIconsBatch(
        packageNames: List<String>,
        deviceManager: DeviceMessagePort,
        sourceDevice: DeviceInfo,
    ) {
        if (packageNames.isEmpty()) return

        val installedPackages = InstalledPackagesPortRegistry.current?.installedPackagesSync() ?: emptySet()
        val iconMap = RemoteAppIconCache.cachedBytes(packageNames)
        val cachedPackages = packageNames.filter { iconMap[it] != null }

        val databaseRepository = DatabaseRepository.getInstance()
        val appDevices = databaseRepository.getAppDevicesByPackageNames(packageNames)
        val appDeviceMap =
            appDevices
                .groupBy { it.packageName }
                .mapValues { (_, entities) -> entities.map { it.sourceDevice } }

        val requestJson =
            NativeCore.appSyncRequestIcons(
                packageNames,
                installedPackages.toList(),
                cachedPackages,
                appDeviceMap,
                sourceDevice.uuid,
                System.currentTimeMillis(),
            )
        if (requestJson == null || requestJson == "{}") return

        val sourceDeviceUuid = sourceDevice.uuid
        try {
            requestIconsFromDevice(requestJson, deviceManager, sourceDevice)
            val need = parseRequestedPackages(requestJson)
            // 先补父行再写关联：app_devices 对 apps 有外键约束
            need.forEach { RemoteAppIconCache.linkToDevice(it, sourceDeviceUuid) }
        } catch (e: IllegalStateException) {
            Logger.e(TAG, "批量请求失败：" + packageNames, e)
        }
    }

    /**
     * 处理 ICON_REQUEST（单个或批量）并以 ICON_RESPONSE 应答。
     *
     * 本机图标经 [IconCodecPort] 注入；本机无该应用时纳入 `missing`
     * —— 必须回包，否则对端 pending 状态要等到超时才释放。
     */
    suspend fun handleIconRequest(
        requestData: String,
        deviceManager: DeviceMessagePort,
        sourceDevice: DeviceInfo,
    ) {
        try {
            val root = json.parseToJsonElement(requestData).jsonObject
            val type = root["type"]?.jsonPrimitive?.content
            if (type != "ICON_REQUEST" && type != "DATA_ICON_REQUEST") return

            val single = root["packageName"]?.jsonPrimitive?.content.orEmpty()
            val multiArray = root["packageNames"] as? JsonArray

            if (multiArray != null && multiArray.isNotEmpty()) {
                val icons = mutableListOf<JsonObject>()
                val missing = mutableListOf<String>()
                for (element in multiArray) {
                    val packageName = element.jsonPrimitive.content
                    if (packageName.isEmpty()) continue
                    val base64 = localIconBase64(packageName)
                    if (base64 != null) {
                        icons.add(
                            buildJsonObject {
                                put("packageName", packageName)
                                put("iconData", base64)
                            },
                        )
                    } else {
                        missing.add(packageName)
                    }
                }

                val raw =
                    json.encodeToString(
                        JsonObject.serializer(),
                        buildJsonObject {
                            put("type", "ICON_RESPONSE")
                            if (icons.isNotEmpty()) put("icons", JsonArray(icons))
                            if (missing.isNotEmpty()) put("missing", JsonArray(missing.map { JsonPrimitive(it) }))
                            put("time", JsonPrimitive(System.currentTimeMillis()))
                        },
                    )
                ProtocolSender.sendEncrypted(deviceManager, sourceDevice, "DATA_ICON_RESPONSE", raw, ICON_REQUEST_TIMEOUT_MS)
                Logger.d(TAG, "批量图标响应已发送(" + icons.size + ") -> " + sourceDevice.displayName)
            } else if (single.isNotEmpty()) {
                val base64 = localIconBase64(single)
                val raw =
                    json.encodeToString(
                        JsonObject.serializer(),
                        buildJsonObject {
                            put("type", "ICON_RESPONSE")
                            put("packageName", single)
                            if (base64 != null) {
                                put("iconData", base64)
                            } else {
                                put("missing", JsonPrimitive(true))
                            }
                            put("time", JsonPrimitive(System.currentTimeMillis()))
                        },
                    )
                ProtocolSender.sendEncrypted(deviceManager, sourceDevice, "DATA_ICON_RESPONSE", raw, ICON_REQUEST_TIMEOUT_MS)
                Logger.d(TAG, "单图标响应已发送：" + single + " -> " + sourceDevice.displayName)
            }
        } catch (e: IllegalArgumentException) {
            Logger.e(TAG, "处理ICON_REQUEST异常", e)
        }
    }

    /**
     * 处理 ICON_RESPONSE（单个或批量）：解析由 Rust 完成，图标字节写入本地缓存。
     */
    suspend fun handleIconResponse(responseData: String) {
        try {
            val parsed = NativeCore.appSyncParseIconResponse(responseData) ?: return
            val root = json.parseToJsonElement(parsed).jsonObject

            val iconsArray = root["icons"] as? JsonArray
            if (iconsArray != null && iconsArray.isNotEmpty()) {
                for (element in iconsArray) {
                    val item = element as? JsonObject ?: continue
                    val packageName = item["packageName"]?.jsonPrimitive?.content.orEmpty()
                    val base64 = item["iconData"]?.jsonPrimitive?.content.orEmpty()
                    if (packageName.isNotEmpty() && base64.isNotEmpty()) {
                        cacheDecodedIcon(packageName, base64)
                    }
                }
            }

            val missingArray = root["missing"] as? JsonArray
            if (missingArray != null && missingArray.isNotEmpty()) {
                for (element in missingArray) {
                    val missingPackage = element.jsonPrimitive.content
                    if (missingPackage.isNotEmpty()) RemoteAppIconCache.markMissing(missingPackage)
                }
            }
        } catch (e: IllegalArgumentException) {
            Logger.e(TAG, "处理ICON_RESPONSE异常", e)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun cacheDecodedIcon(
        packageName: String,
        base64: String,
    ) {
        try {
            val bytes = Base64.decode(base64)
            RemoteAppIconCache.cache(packageName, bytes, "remote")
        } catch (e: IllegalArgumentException) {
            Logger.w(TAG, "图标解码失败：" + packageName, e)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun localIconBase64(packageName: String): String? {
        val codec = IconCodecPortRegistry.current ?: return null
        val bytes = codec.localIconBytes(packageName) ?: return null
        return Base64.encode(bytes)
    }

    /** 入站入口：处理图标请求（非挂起，供 Rust 回调线程直接调用）。 */
    fun onInboundIconRequest(
        payload: String,
        deviceManager: DeviceMessagePort,
        sourceDevice: DeviceInfo,
    ) {
        ioScope.launch { handleIconRequest(payload, deviceManager, sourceDevice) }
    }

    /** 入站入口：处理图标响应（非挂起）。 */
    fun onInboundIconResponse(payload: String) {
        ioScope.launch { handleIconResponse(payload) }
    }

    private suspend fun requestIconsFromDevice(
        requestJson: String,
        deviceManager: DeviceMessagePort,
        sourceDevice: DeviceInfo,
    ) {
        val result =
            ProtocolSender.sendEncrypted(
                deviceManager,
                sourceDevice,
                "DATA_ICON_REQUEST",
                requestJson,
                ICON_REQUEST_TIMEOUT_MS,
            )
        if (result != ProtocolSender.EnqueueResult.SUCCESS) {
            // 入队失败时抛异常，使调用方进入 catch 分支清理 pending 状态，避免后续无法重新请求
            throw IllegalStateException("图标请求入队失败: " + result)
        }
    }

    private fun parseRequestedPackages(requestJson: String): List<String> =
        try {
            val root = json.parseToJsonElement(requestJson).jsonObject
            val single = root["packageName"]?.jsonPrimitive?.content
            if (!single.isNullOrEmpty()) {
                listOf(single)
            } else {
                (root["packageNames"] as? JsonArray)?.map { it.jsonPrimitive.content }?.filter { it.isNotEmpty() } ?: emptyList()
            }
        } catch (e: IllegalArgumentException) {
            emptyList()
        }
}
