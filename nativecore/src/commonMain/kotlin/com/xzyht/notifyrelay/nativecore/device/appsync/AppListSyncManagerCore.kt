package com.xzyht.notifyrelay.nativecore.device.appsync

import com.xzyht.notifyrelay.nativecore.NativeCore
import com.xzyht.notifyrelay.nativecore.device.ProtocolSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import notifyrelay.domain.feature.appslist.AppLabelPortRegistry
import notifyrelay.domain.feature.appslist.InstalledPackagesPortRegistry
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.device.service.contract.DeviceMessagePort

/**
 * 应用列表同步管理器。
 *
 * 按需在设备之间传输「用户应用」的包名与应用名列表：
 * - 请求：`DATA_APP_LIST_REQUEST`
 * - 响应：`DATA_APP_LIST_RESPONSE`
 *
 * 报文解析与会话登记由 Rust core 承担，本类只负责收发编排与本地缓存。
 */
object AppListSyncManagerCore {
    private const val TAG = "AppListSyncManager"
    private const val REQ_TIMEOUT = 15000L
    private const val DEFAULT_PORT = 23333
    private const val SCOPE_USER = "user"

    private val json = Json { ignoreUnknownKeys = true }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 按设备 uuid 跟踪的在途同步任务；设备被移除时按 uuid 取消。 */
    private val deviceJobs = mutableMapOf<String, MutableList<Job>>()
    private val jobsLock = Mutex()

    /** 取消指定设备的全部在途同步任务。 */
    suspend fun cancelForDevice(deviceUuid: String) {
        val jobs = jobsLock.withLock { deviceJobs.remove(deviceUuid) }
        jobs?.forEach { it.cancel() }
    }

    /** 在协程内部登记自身 Job，完成时移除。 */
    private suspend fun trackCurrentJob(deviceUuid: String) {
        val job = coroutineContext[Job] ?: return
        jobsLock.withLock {
            deviceJobs.getOrPut(deviceUuid) { mutableListOf() }.add(job)
        }
        job.invokeOnCompletion {
            ioScope.launch {
                jobsLock.withLock {
                    val set = deviceJobs[deviceUuid] ?: return@withLock
                    set.remove(job)
                    if (set.isEmpty()) deviceJobs.remove(deviceUuid)
                }
            }
        }
    }

    /** 主动向目标设备请求其「用户应用」列表。 */
    fun requestAppList(
        deviceManager: DeviceMessagePort,
        targetDevice: DeviceInfo,
        scope: String = SCOPE_USER,
    ) {
        val raw = NativeCore.appSyncBuildApplistRequest(scope, System.currentTimeMillis()) ?: return
        ProtocolSender.sendEncrypted(deviceManager, targetDevice, "DATA_APP_LIST_REQUEST", raw, REQ_TIMEOUT)
    }

    /**
     * 处理收到的应用列表请求，并把本机应用列表作为响应发回。
     *
     * 本机应用来源经 [InstalledPackagesPortRegistry] 注入；桌面端为空集（不枚举本机应用），
     * 因此应答为一条空列表而不是不回包。
     */
    fun handleAppListRequest(
        requestData: String,
        deviceManager: DeviceMessagePort,
        sourceDevice: DeviceInfo,
    ) {
        try {
            val type = parseField(requestData, "type")
            if (type != "APP_LIST_REQUEST" && type != "DATA_APP_LIST_REQUEST") return
            val scope = parseField(requestData, "scope") ?: SCOPE_USER

            val localPackages = InstalledPackagesPortRegistry.current?.installedPackagesSync() ?: emptySet()
            val labelPort = AppLabelPortRegistry.current
            val appArray =
                localPackages.map { packageName ->
                    buildJsonObject {
                        put("packageName", packageName)
                        put("appName", labelPort?.labelOf(packageName) ?: packageName)
                    }
                }

            val raw =
                json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        put("type", "APP_LIST_RESPONSE")
                        put("scope", scope)
                        put("total", JsonPrimitive(appArray.size))
                        put("apps", JsonArray(appArray))
                        put("time", JsonPrimitive(System.currentTimeMillis()))
                    },
                )
            ProtocolSender.sendEncrypted(deviceManager, sourceDevice, "DATA_APP_LIST_RESPONSE", raw, REQ_TIMEOUT)
            Logger.d(TAG, "已响应应用列表：" + sourceDevice.displayName + "，共" + appArray.size + "项")
        } catch (e: IllegalArgumentException) {
            Logger.e(TAG, "处理应用列表请求失败", e)
        }
    }

    /**
     * 处理收到的应用列表响应：解析 → 缓存 → 关联设备 → 请求缺失图标。
     */
    fun handleAppListResponse(
        responseData: String,
        deviceUuid: String,
        deviceManager: DeviceMessagePort,
    ) {
        try {
            val parsed = NativeCore.appSyncParseApplistResponse(responseData) ?: return
            val root = json.parseToJsonElement(parsed).jsonObject
            val total = root["total"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1
            Logger.d(TAG, "收到应用列表响应，共 " + total + " 项，来源设备：" + deviceUuid)

            val appsArray = root["apps"]?.jsonArray ?: return
            val appsMap = mutableMapOf<String, String>()
            val packageNames = mutableListOf<String>()
            for (element in appsArray) {
                val item = element as? JsonObject ?: continue
                val packageName = item["packageName"]?.jsonPrimitive?.content.orEmpty()
                val appName = item["appName"]?.jsonPrimitive?.content.orEmpty()
                if (packageName.isNotEmpty() && appName.isNotEmpty()) {
                    appsMap[packageName] = appName
                    packageNames.add(packageName)
                }
            }

            ioScope.launch {
                    trackCurrentJob(deviceUuid)
                    RemoteAppsCacheCore.cache(appsMap, deviceUuid)
                    val now = System.currentTimeMillis()
                    DatabaseRepository.getInstance().saveAppDeviceAssociations(
                        packageNames.map {
                            AppDeviceEntity(
                                packageName = it,
                                sourceDevice = deviceUuid,
                                lastUpdated = now,
                            )
                        },
                    )
                    val sourceDevice = deviceManager.resolveDeviceInfo(deviceUuid, null, DEFAULT_PORT)
                    sourceDevice?.let { checkAndRequestMissingIcons(packageNames, deviceManager, it) }
                }
        } catch (e: IllegalArgumentException) {
            Logger.e(TAG, "处理应用列表响应失败", e)
        }
    }

    /** 检查并批量请求缺失的图标。 */
    fun checkAndRequestMissingIcons(
        packageNames: List<String>,
        deviceManager: DeviceMessagePort,
        sourceDevice: DeviceInfo,
    ) {
        ioScope.launch {
                trackCurrentJob(sourceDevice.uuid)
                val missingIcons = RemoteAppIconCache.missingOf(packageNames)
                if (missingIcons.isEmpty()) return@launch
                val needRequestIcons = filterOutLocalInstalled(missingIcons)
                if (needRequestIcons.isEmpty()) return@launch
                IconSyncManagerCore.requestIconsBatch(needRequestIcons, deviceManager, sourceDevice)
            }
    }

    /** 入站入口：处理应用列表请求（非挂起，供 Rust 回调线程直接调用）。 */
    fun onInboundAppListRequest(
        payload: String,
        deviceManager: DeviceMessagePort,
        sourceDevice: DeviceInfo,
    ) {
        ioScope.launch { handleAppListRequest(payload, deviceManager, sourceDevice) }
    }

    /** 入站入口：处理应用列表响应（非挂起）。 */
    fun onInboundAppListResponse(
        payload: String,
        deviceUuid: String,
        deviceManager: DeviceMessagePort,
    ) {
        handleAppListResponse(payload, deviceUuid, deviceManager)
    }

    /** 过滤掉本机已安装的应用（本机图标可直接获取，无需请求）。 */
    fun filterOutLocalInstalled(packageNames: List<String>): List<String> {
        val installedPackages = InstalledPackagesPortRegistry.current?.installedPackagesSync() ?: emptySet()
        return packageNames.filterNot { installedPackages.contains(it) }
    }

    private fun parseField(
        raw: String,
        field: String,
    ): String? =
        try {
            json.parseToJsonElement(raw).jsonObject[field]?.jsonPrimitive?.content
        } catch (e: IllegalArgumentException) {
            null
        }
}
