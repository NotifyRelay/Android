package com.xzyht.notifyrelay.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import com.xzyht.notifyrelay.servers.appslist.AppRepository
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import notifyrelay.base.util.Logger
import notifyrelay.data.database.entity.AppDeviceEntity
import notifyrelay.data.database.repository.DatabaseRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * 图标同步管理器
 *
 * 负责在已认证的设备之间同步应用图标，以避免重复传输并降低网络与性能开销。
 * 功能包括：
 *  - 检查本地是否已有图标，若无则向发送通知的设备请求图标。
 *  - 接收并处理图标请求（ICON_REQUEST）和图标响应（ICON_RESPONSE）。
 *  - 将接收到的图标解码并缓存到本地仓库。
 *
 * 所有日志仅在 BuildConfig.DEBUG 为 true 时打印，以避免在生产环境泄露信息。
 */
object IconSyncManager {

    private const val TAG = "IconSyncManager"
    private const val ICON_REQUEST_TIMEOUT = 10000L

    // 正在请求的图标缓存，避免重复请求（packageName -> requestTime）
    private val pendingRequests = mutableMapOf<String, Long>()

    /**
     * 检查并（必要时）请求单个图标。
     */
    fun checkAndSyncIcon(
        context: Context,
        packageName: String,
        deviceManager: DeviceConnectionManager,
        sourceDevice: DeviceInfo
    ) {
        // 检查 AppRepository 缓存
        val exist = runBlocking {
            AppRepository.getExternalAppIcon(context, packageName)
        }
        if (exist != null) {
            //Logger.d(TAG, "图标已存在，跳过：$packageName")
            return
        }
        
        // 检查本机已安装应用
        val installedPackages = AppRepository.getInstalledPackageNames(context)
        if (installedPackages.contains(packageName)) {
            //Logger.d(TAG, "应用已安装，跳过请求：$packageName")
            return
        }
        
        // 检查正在请求的图标
        val now = System.currentTimeMillis()
        val last = pendingRequests[packageName]
        if (last != null && (now - last) < ICON_REQUEST_TIMEOUT) {
            //Logger.d(TAG, "单图标请求进行中，跳过：$packageName")
            return
        }
        
        // 获取应用的来源设备UUID列表（替代原 getAppDeviceUuids 方法）
        val appDeviceUuids = runBlocking {
            val databaseRepository = DatabaseRepository.getInstance(context)
            val appDevices = databaseRepository.getAppDevicesByPackageName(packageName).first()
            appDevices.map { appDevice -> appDevice.sourceDevice }
        }
        
        // 检查是否应该从当前设备请求图标
        val shouldRequestFromThisDevice = appDeviceUuids.size == 0 || appDeviceUuids.contains(sourceDevice.uuid)
        
        if (shouldRequestFromThisDevice) {
            pendingRequests[packageName] = now
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    requestIconsFromDevice(context, listOf(packageName), deviceManager, sourceDevice)
                    // 请求成功，关联应用包名与当前设备（替代原 associateAppWithDevice 方法）
                    val databaseRepository = DatabaseRepository.getInstance(context)
                    val appDeviceEntities = mutableListOf<AppDeviceEntity>()
                    val appDeviceEntity = AppDeviceEntity(
                        packageName = packageName,
                        sourceDevice = sourceDevice.uuid,
                        lastUpdated = System.currentTimeMillis()
                    )
                    appDeviceEntities.add(appDeviceEntity)
                    databaseRepository.saveAppDeviceAssociations(appDeviceEntities)
                } catch (e: Exception) {
                    Logger.e(TAG, "请求图标失败：$packageName", e)
                } finally {
                    pendingRequests.remove(packageName)
                }
            }
        } else {
            //Logger.d(TAG, "应用 $packageName 不属于当前设备 $sourceDevice.uuid，跳过请求")
        }
    }

    /**
     * 批量请求多个包名图标（自动过滤已存在或正在请求的）。
     */
    suspend fun requestIconsBatch(
        context: Context,
        packageNames: List<String>,
        deviceManager: DeviceConnectionManager,
        sourceDevice: DeviceInfo
    ) {
        //Logger.d(TAG, "批量请求图标：$packageNames")
        if (packageNames.size == 0) return
        
        val now = System.currentTimeMillis()
        val installedPackages = AppRepository.getInstalledPackageNames(context)
        
        // 预获取所有需要的数据
        val (iconMap, appDeviceMap) = coroutineScope {
            // 并行预获取应用图标
            val iconDeferred = async {
                AppRepository.getExternalAppIcons(context, packageNames)
            }
            
            // 并行预获取应用设备关联关系
            val appDeviceDeferred = async {
                val databaseRepository = DatabaseRepository.getInstance(context)
                val appDevices = databaseRepository.getAppDevicesByPackageNames(packageNames)
                appDevices.groupBy { it.packageName }
                    .mapValues { (_, appDeviceEntities) ->
                        appDeviceEntities.map { it.sourceDevice }
                    }
            }
            
            Pair(iconDeferred.await(), appDeviceDeferred.await())
        }
        
        val need = packageNames.filter { pkg ->
            // 1. 检查 AppRepository 缓存
            val exist = iconMap[pkg] != null
            // 2. 检查本机已安装应用
            val isInstalled = installedPackages.contains(pkg)
            // 3. 检查正在请求的图标
            val last = pendingRequests[pkg]
            val inFlight = last != null && (now - last) < ICON_REQUEST_TIMEOUT
            // 4. 检查应用与设备的关联关系
            val appDeviceUuids = appDeviceMap[pkg] ?: emptyList()
            val isAssociatedWithThisDevice = appDeviceUuids.size == 0 || appDeviceUuids.contains(sourceDevice.uuid)
            
            !exist && !isInstalled && !inFlight && isAssociatedWithThisDevice
        }
        
        if (need.size == 0) {
            //Logger.d(TAG, "所有图标已缓存或已安装，无需批量请求")
            return
        }
        
        need.forEach { pendingRequests[it] = now }
        val sourceDeviceUuid = sourceDevice.uuid
        try {
            requestIconsFromDevice(context, need, deviceManager, sourceDevice)
            // 请求成功，批量关联应用包名与当前设备（替代原 associateAppsWithDevice 方法）
            val databaseRepository = DatabaseRepository.getInstance(context)
            val appDeviceEntities = need.map {
                AppDeviceEntity(
                    packageName = it,
                    sourceDevice = sourceDeviceUuid,
                    lastUpdated = System.currentTimeMillis()
                )
            }
            databaseRepository.saveAppDeviceAssociations(appDeviceEntities)
        } catch (e: Exception) {
            Logger.e(TAG, "批量请求失败：$need", e)
        } finally {
            need.forEach { pendingRequests.remove(it) }
        }
    }

    /**
     * 构建并发送（单包或多包） ICON_REQUEST 请求。
     */
    private suspend fun requestIconsFromDevice(
        context: Context,
        packages: List<String>,
        deviceManager: DeviceConnectionManager,
        sourceDevice: DeviceInfo
    ) {
        if (packages.size == 0) return
        val json = JSONObject().apply {
            put("type", "ICON_REQUEST")
            if (packages.size == 1) {
                put("packageName", packages.first())
            } else {
                put("packageNames", JSONArray(packages))
            }
            put("time", System.currentTimeMillis())
        }.toString()
        ProtocolSender.sendEncrypted(deviceManager, sourceDevice, "DATA_ICON_REQUEST", json, ICON_REQUEST_TIMEOUT)
        //Logger.d(TAG, "发送ICON_REQUEST(${packages.size}) -> ${sourceDevice.displayName}")
    }

    /**
     * 处理 ICON_REQUEST 请求（支持单个或批量）。
     */
    fun handleIconRequest(
        requestData: String,
        deviceManager: DeviceConnectionManager,
        sourceDevice: DeviceInfo,
        context: Context
    ) {
        try {
            val json = JSONObject(requestData)
            val type = json.optString("type")
            Logger.d(TAG, "解析到的 type 字段值：$type")
            if (type != "ICON_REQUEST" && type != "DATA_ICON_REQUEST") return

            val single = json.optString("packageName")
            val multiArray = json.optJSONArray("packageNames")

            if (multiArray != null && multiArray.length() > 0) {
                // 批量
                val resultArr = JSONArray()
                val missingArr = JSONArray()
                runBlocking {
                    for (i in 0 until multiArray.length()) {
                        val pkg = multiArray.optString(i)
                        if (pkg.isNullOrEmpty()) continue
                        val icon = getLocalAppIcon(context, pkg)
                        if (icon != null) {
                            val base64 = bitmapToBase64(icon)
                            val item = JSONObject().apply { 
                                put("packageName", pkg)
                                put("iconData", base64)
                            }
                            resultArr.put(item)
                        } else {
                            // 记录缺失的图标
                            missingArr.put(pkg)
                        }
                    }
                }
                
                // 构建响应，包含可用图标和缺失图标信息
                val resp = JSONObject().apply { 
                    put("type", "ICON_RESPONSE")
                    if (resultArr.length() > 0) {
                        put("icons", resultArr)
                    }
                    if (missingArr.length() > 0) {
                        put("missing", missingArr)
                    }
                    put("time", System.currentTimeMillis())
                }.toString()
                
                Logger.d(TAG, "批量图标响应准备发送，包含 ${resultArr.length()} 个图标，${missingArr.length()} 个缺失图标")
                // 发送响应，即使没有可用图标，也要通知请求方哪些图标缺失
                ProtocolSender.sendEncrypted(deviceManager, sourceDevice, "DATA_ICON_RESPONSE", resp, ICON_REQUEST_TIMEOUT)
                Logger.d(TAG, "批量图标响应已发送(${resultArr.length()}) -> ${sourceDevice.displayName}")
            } else if (single.isNotEmpty()) {
                val icon = runBlocking {
                    getLocalAppIcon(context, single)
                }
                val resp = JSONObject().apply { 
                    put("type", "ICON_RESPONSE")
                    put("packageName", single)
                    if (icon != null) {
                        put("iconData", bitmapToBase64(icon))
                    } else {
                        put("missing", true)
                    }
                    put("time", System.currentTimeMillis())
                }.toString()
                
                Logger.d(TAG, "单图标响应准备发送，包名：$single，${if (icon != null) "有图标" else "无图标"}")
                // 发送响应，即使没有图标，也要通知请求方
                ProtocolSender.sendEncrypted(deviceManager, sourceDevice, "DATA_ICON_RESPONSE", resp, ICON_REQUEST_TIMEOUT)
                Logger.d(TAG, "单图标响应已发送：$single -> ${sourceDevice.displayName}")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "处理ICON_REQUEST异常", e)
        }
    }

    /**
     * 处理 ICON_RESPONSE（单个或批量）。
     */
    fun handleIconResponse(responseData: String, context: Context) {
        try {
            val json = JSONObject(responseData)
            if (json.optString("type") != "ICON_RESPONSE") return

            val iconsArray = json.optJSONArray("icons")
            if (iconsArray != null && iconsArray.length() > 0) {
                for (i in 0 until iconsArray.length()) {
                    val item = iconsArray.optJSONObject(i) ?: continue
                    val pkg = item.optString("packageName")
                    val base64 = item.optString("iconData")
                    cacheDecodedIcon(context, pkg, base64)
                }
                //Logger.d(TAG, "批量图标接收完成：${iconsArray.length()}")
            }

            val pkg = json.optString("packageName")
            val base64 = json.optString("iconData")
            val isMissing = json.optBoolean("missing", false)
            
            if (pkg.isNotEmpty()) {
                if (base64.isNotEmpty()) {
                    // 处理单个图标响应
                    cacheDecodedIcon(context, pkg, base64)
                    //Logger.d(TAG, "单图标接收：$pkg")
                } else if (isMissing) {
                    // 处理单个缺失图标响应
                    //Logger.d(TAG, "单图标缺失：$pkg")
                    // 标记图标为缺失，避免重复请求（替代原 markIconAsMissing 方法）
                    runBlocking {
                        val databaseRepository = DatabaseRepository.getInstance(context)
                        databaseRepository.markAppIconAsMissing(pkg)
                    }
                }
            }
            
            // 处理批量缺失图标
            val missingArray = json.optJSONArray("missing")
            if (missingArray != null && missingArray.length() > 0) {
                for (i in 0 until missingArray.length()) {
                    val missingPkg = missingArray.optString(i)
                    if (missingPkg.isNotEmpty()) {
                        //Logger.d(TAG, "批量图标缺失：$missingPkg")
                    // 标记图标为缺失（替代原 markIconAsMissing 方法）
                        runBlocking {
                            val databaseRepository = DatabaseRepository.getInstance(context)
                            databaseRepository.markAppIconAsMissing(missingPkg)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "处理ICON_RESPONSE异常", e)
        }
    }

    private fun cacheDecodedIcon(context: Context, packageName: String, base64: String) {
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            runBlocking {
                AppRepository.cacheExternalAppIcon(context, packageName, bmp, "remote")
            }
        } catch (e: Exception) {
            Logger.w(TAG, "图标解码失败：$packageName", e)
        }
    }

    private fun bitmapToBase64(icon: Bitmap): String {
        val bos = ByteArrayOutputStream()
        icon.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    private suspend fun getLocalAppIcon(context: Context, packageName: String): Bitmap? {
        // packageName 应为实际应用包名
        val actualPackageName = packageName

        return try {
            AppRepository.getAppIconAsync(context, actualPackageName) ?: run {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(actualPackageName, 0)
                val drawable = pm.getApplicationIcon(appInfo)
                if (drawable is BitmapDrawable) {
                    drawable.bitmap
                } else {
                    val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
                    val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, w, h)
                    drawable.draw(canvas)
                    bmp
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "获取本地图标失败：$packageName", e)
            null
        }
    }

    fun cleanupExpiredRequests() {
        val now = System.currentTimeMillis()
        pendingRequests.entries.removeIf { (_, t) ->
            val expired = (now - t) > ICON_REQUEST_TIMEOUT * 2
            //Logger.d(TAG, "清理过期请求：$pkg")
            expired
        }
    }
}
