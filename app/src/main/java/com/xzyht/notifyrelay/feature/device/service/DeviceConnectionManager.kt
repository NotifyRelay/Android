package com.xzyht.notifyrelay.feature.device.service

import android.os.Build
import android.os.Environment
import com.sun.jna.Pointer
import com.xzyht.notifyrelay.nativecore.NativeCore
import com.xzyht.notifyrelay.nativecore.NotifyRelayCore
import com.xzyht.notifyrelay.feature.notification.superisland.RemoteMediaSessionManager
import com.xzyht.notifyrelay.servers.MediaControlUtil
import com.xzyht.notifyrelay.servers.clipboard.ClipboardProcessor
import com.xzyht.notifyrelay.sync.AppLaunchManager
import com.xzyht.notifyrelay.sync.AppListSyncManager
import com.xzyht.notifyrelay.sync.ConnectionDiscoveryManager
import com.xzyht.notifyrelay.sync.ConnectionKeepAlive
import com.xzyht.notifyrelay.sync.IconSyncManager
import com.xzyht.notifyrelay.sync.ProtocolSender
import com.xzyht.notifyrelay.sync.HeartbeatProcessor
import com.xzyht.notifyrelay.sync.ServerLineRouter
import com.xzyht.notifyrelay.sync.ftpServer
import com.xzyht.notifyrelay.sync.ftpServer.StartResult
import com.xzyht.notifyrelay.sync.notification.NotificationProcessor
import com.xzyht.notifyrelay.sync.notification.StatusProcessor
import com.xzyht.notifyrelay.sync.notification.SuperIslandProcessor
import com.xzyht.notifyrelay.ui.activity.GuideActivity
import github.xzynine.superislandui.common.SuperIslandProtocol
import io.github.miuzarte.scrcpyforandroid.services.AudioForwardingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import notifyrelay.base.util.IntentUtils
import notifyrelay.base.util.Logger
import notifyrelay.base.util.DeviceUtils
import notifyrelay.core.util.BatteryUtils
import notifyrelay.core.util.PairingCodeManager
import notifyrelay.data.StorageManager
import notifyrelay.data.config.AppConfig
import notifyrelay.data.config.ScrcpyDefaults
import notifyrelay.data.database.entity.DeviceEntity
import notifyrelay.data.database.repository.DatabaseRepository
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

data class DeviceInfo(
    val uuid: String,
    val displayName: String, // 前端显示名，优先蓝牙名，其次型号
    val ip: String,
    val port: Int,
    var batteryLevel: Int = -1, // 设备电量，默认-1表示未知
    // 充电状态：使用 '*' 表示未知（与 batteryLevel 使用 -1 表示未知一致），'1' 表示充电，'0' 表示未充电
    var chargingStatus: Char = '*' // 充电状态，默认'*'表示未知
)

object DeviceConnectionManagerUtil {
    // 工具：构造 json 格式的通知数据
    fun buildNotificationJson(packageName: String, appName: String?, title: String?, text: String?, time: Long): String {
        val json = org.json.JSONObject()
        json.put("packageName", packageName)
        json.put("appName", appName ?: packageName)
        json.put("title", title ?: "")
        json.put("text", text ?: "")
        json.put("time", time)
        return json.toString()
    }

    // 静态缓存，便于 UI 查询 uuid->displayName
    private val globalDeviceNameCache = mutableMapOf<String, String>()
    fun updateGlobalDeviceName(uuid: String, displayName: String) {
        synchronized(globalDeviceNameCache) {
            globalDeviceNameCache[uuid] = displayName
        }
    }
    fun getDisplayNameByUuid(uuid: String?): String {
        if (uuid == null) return "未知设备"
        if (uuid == "本机") return "本机"
        synchronized(globalDeviceNameCache) {
            return globalDeviceNameCache[uuid] ?: uuid
        }
    }
}


data class AuthInfo(
    val publicKey: String,
    val sharedSecret: String,
    val isAccepted: Boolean,
    val displayName: String? = null,
    val lastIp: String? = null,
    val lastPort: Int? = null,
    val deviceType: String? = null,
    val battery: String? = null
)

// =================== 设备连接管理器主类 ===================
class DeviceConnectionManager(private val context: android.content.Context) {
    // 单例实例
    companion object {
        /**
         * 获取单例实例
         * 内部使用DeviceConnectionManagerSingleton确保全局唯一实例
         */
        fun getInstance(context: android.content.Context): DeviceConnectionManager {
            return DeviceConnectionManagerSingleton.getDeviceManager(context)
        }

        // 静态引用，供 native 回调线程从 Rust 回调中调度到实例方法
        private var _callbackInstance: DeviceConnectionManager? = null
        internal fun getCallbackInstance(): DeviceConnectionManager? = _callbackInstance
    }
    
    // 用于比较在线设备缓存是否变化的变量
    private var lastOnlineDevicesCacheJson: String? = null

    // ==================== 握手请求处理接口 ====================
    /**
     * 配对请求处理接口。
     * 当接收到 PAIRING_INIT 时通过此接口通知 UI 层显示配对码输入弹窗。
     */
    interface HandshakeRequestHandler {
        fun onPairingInitRequest(deviceInfo: DeviceInfo, tmpPublicKey: String)
    }

    /**
     * 配对请求处理器
     */
    var handshakeRequestHandler: HandshakeRequestHandler? = null

    internal fun getLocalDisplayName(): String {
        return DeviceUtils.getLocalDeviceName(context)
    }

    // 将显示名称清洗为不可见字符替换、并裁剪（口径较宽）
    private fun sanitizeDisplayName(raw: String): String {
        try {
            var s = raw.replace(Regex("[\\r\\n]"), " ")
            s = s.trim()
            if (s.isEmpty()) return s
            val bytes = s.toByteArray(Charsets.UTF_8)
            if (bytes.size <= 64) return s
            var cut = 64
            while (cut > 0 && (bytes[cut - 1].toInt() and 0xC0) == 0x80) cut--
            return String(bytes.copyOfRange(0, cut), Charsets.UTF_8)
        } catch (_: Exception) {
            return raw
        }
    }
// 设备信息缓存，解决未认证设备无法显示详细信息问题
    private val deviceInfoCache = mutableMapOf<String, DeviceInfo>()
    private val PREFS_AUTHED_DEVICES = "authed_devices_json"
    // 保持 JNA 回调对象强引用，防止被 GC
    private val rustCallbackRefs = mutableListOf<Any>()

    // 加载已认证设备
    private fun loadAuthedDevices() {
        val devices = kotlinx.coroutines.runBlocking {
            DatabaseRepository.getInstance(context).getDevices()
        }
        
        val ctx = rustContext
        var hasOldKey = false
        for (device in devices) {
            if (device.uuid == "本机") continue
            
            if (device.sharedSecret.isNotEmpty()) {
                // 旧版明文密钥（C#/Kotlin ECDH），与 Rust HKDF 不兼容，清除配对
                hasOldKey = true
                try {
                    ctx?.let { NativeCore.removeDevice(it, device.uuid) }
                } catch (_: Exception) {}
                // 更新数据库：清除密钥，标记未配对，但保留设备记录
                kotlinx.coroutines.runBlocking {
                    DatabaseRepository.getInstance(context).saveDevice(
                        device.copy(sharedSecret = "", isAccepted = false)
                    )
                }
            } else if (device.isAccepted) {
                // 已通过 Rust 重新配对过的设备，恢复认证状态
                synchronized(authenticatedDevices) {
                    authenticatedDevices[device.uuid] = AuthInfo(
                        publicKey = device.publicKey,
                        sharedSecret = "",
                        isAccepted = true,
                        displayName = device.displayName,
                        lastIp = device.lastIp,
                        lastPort = device.lastPort
                    )
                }
            }
            
            // 恢复设备名和 IP 到缓存
            if (!device.displayName.isNullOrEmpty()) {
                DeviceConnectionManagerUtil.updateGlobalDeviceName(device.uuid, device.displayName)
            }
            synchronized(deviceInfoCache) {
                deviceInfoCache[device.uuid] = DeviceInfo(
                    uuid = device.uuid,
                    displayName = device.displayName,
                    ip = device.lastIp,
                    port = device.lastPort
                )
            }
        }
        
        // 更新设备列表和 Flow
        try {
            coroutineScope.launch {
                updateDeviceList()
                _authenticatedDevicesFlow.value = authenticatedDevices.toMap()
                _rejectedDevicesFlow.value = rejectedDevices.toSet()
            }
        } catch (_: Exception) {}
        if (hasOldKey) {
            Logger.i("死神-NotifyRelay", "检测到旧版密钥，已清除配对，请重新配对设备")
        }
    }

    // 保存已认证设备
    private fun saveAuthedDevices() {
        try {
            // 保存到Room数据库
            val deviceEntities = mutableListOf<DeviceEntity>()
            for ((uuid, auth) in authenticatedDevices) {
                // 过滤掉uuid为"本机"的记录
                if (uuid == "本机") continue
                
                if (auth.isAccepted) {
                    val name = auth.displayName ?: deviceInfoCache[uuid]?.displayName ?: DeviceConnectionManagerUtil.getDisplayNameByUuid(uuid)
                    val info = deviceInfoCache[uuid]
                    val deviceEntity = DeviceEntity(
                        uuid = uuid,
                        publicKey = auth.publicKey,
                        sharedSecret = auth.sharedSecret,
                        isAccepted = true,
                        displayName = name,
                        lastIp = info?.ip ?: auth.lastIp ?: "",
                        lastPort = info?.port ?: auth.lastPort ?: 23333
                    )
                    deviceEntities.add(deviceEntity)
                }
            }
            
            // 异步保存到数据库
            coroutineScope.launch {
                val repository = DatabaseRepository.getInstance(context)
                
                // 获取当前数据库中的所有设备
                val currentDevices = repository.getDevices()
                currentDevices.map { it.uuid }.toSet()
                
                // 要保存的设备UUID列表
                val deviceUuidsToSave = deviceEntities.map { it.uuid }.toSet()
                
                // 删除数据库中存在但内存中不存在的设备
                currentDevices.forEach {
                    // 保留uuid为"本机"的记录，避免影响旧数据
                    if (!deviceUuidsToSave.contains(it.uuid) && it.uuid != "本机") {
                        repository.deleteDevice(it)
                    }
                }
                
                // 保存或更新设备
                deviceEntities.forEach {
                    repository.saveDevice(it)
                }
                
                // 更新Flow值
                _authenticatedDevicesFlow.value = authenticatedDevices.toMap()
                _rejectedDevicesFlow.value = rejectedDevices.toSet()
            }
        } catch (_: Exception) {}
    }
    /**
     * 设备发现/连接/数据发送/接收，全部本地实现。
     */
    private val notificationDataReceivedCallbacks = mutableSetOf<(String) -> Unit>()

    /**
     * 注册通知数据接收回调
     */
    fun registerOnNotificationDataReceived(callback: (String) -> Unit) {
        notificationDataReceivedCallbacks.add(callback)
    }

    /**
     * 注销通知数据接收回调
     */
    fun unregisterOnNotificationDataReceived(callback: (String) -> Unit) {
        notificationDataReceivedCallbacks.remove(callback)
    }
    internal val notificationDataReceivedCallbacksInternal: Collection<(String) -> Unit>
        get() = notificationDataReceivedCallbacks
    private val _devices = MutableStateFlow<Map<String, Pair<DeviceInfo, Boolean>>>(emptyMap())
    /**
     * 设备状态流：key为uuid，value为(DeviceInfo, isOnline)
     * 只要认证过的设备会一直保留，未认证设备3秒未发现则消失
     */
    val devices: StateFlow<Map<String, Pair<DeviceInfo, Boolean>>> = _devices
    
    private val _authenticatedDevicesFlow = MutableStateFlow<Map<String, AuthInfo>>(emptyMap())
    /**
     * 已认证设备状态流
     */
    val authenticatedDevicesFlow: StateFlow<Map<String, AuthInfo>> = _authenticatedDevicesFlow
    
    private val _rejectedDevicesFlow = MutableStateFlow<Set<String>>(emptySet())
    /**
     * 已拒绝设备状态流
     */
    val rejectedDevicesFlow: StateFlow<Set<String>> = _rejectedDevicesFlow
    internal val uuid: String

    // 认证设备表，key为uuid
    internal val authenticatedDevices = mutableMapOf<String, AuthInfo>()
    // 被拒绝设备表
    private val rejectedDevices = mutableSetOf<String>()
    // 本地 ECDH 公钥（Base64 编码的 65 字节未压缩点）
    internal val localPublicKey: String
    // localPrivateKey 不再使用，ECDH 私钥在 Android Keystore 中，不可直接获取
    @Deprecated("ECDH 私钥在 Keystore 中，不再使用")
    private val localPrivateKey: String = ""
    internal val listenPort: Int = 23333
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val keepAlive = ConnectionKeepAlive(this, coroutineScope)
    private val discoveryManager = ConnectionDiscoveryManager(this, coroutineScope)

    // === 以下为提供给 ServerLineRouter 等内部组件使用的访问器（保持字段本身 private） ===
    internal val deviceInfoCacheInternal: MutableMap<String, DeviceInfo>
        get() = deviceInfoCache

    internal val deviceLastSeenInternal: MutableMap<String, Long>
        get() = deviceLastSeen

    internal val rejectedDevicesInternal: MutableSet<String>
        get() = rejectedDevices

    /** 检查指定设备是否已认证 */
    fun isAuthenticatedInternal(uuid: String): Boolean {
        return synchronized(authenticatedDevices) { authenticatedDevices.containsKey(uuid) }
    }

    internal val incompatibleDevicesInternal: MutableSet<String> =
        java.util.Collections.synchronizedSet(mutableSetOf())

    internal val coroutineScopeInternal: CoroutineScope
        get() = coroutineScope

    private val heartbeatedDevices = mutableSetOf<String>()

    internal val heartbeatedDevicesInternal: MutableSet<String>
        get() = heartbeatedDevices

    internal val heartbeatJobsInternal: MutableMap<String, kotlinx.coroutines.Job>
        get() = heartbeatJobs

    internal val contextInternal: android.content.Context
        get() = context

    internal fun localDisplayNameInternal(): String = getLocalDisplayName()

    // 解码并清洗从网络接收到的名称
    private fun decodeDisplayNameFromTransport(encoded: String): String {
        try {
            if (encoded.isEmpty()) {
                // 处理空字符串情况，返回默认设备名称"错误空"以便排除故障点
                return "错误空"
            }
            val decoded = try { android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP) } catch (_: Exception) { null }
            if (decoded != null) {
                val s = String(decoded, Charsets.UTF_8)
                val sanitized = sanitizeDisplayName(s)
                // 确保解码后的名称不为空，使用默认值"错误空"兜底以便排除故障点
                return sanitized.ifEmpty { "错误空" }
            }
        } catch (_: Exception) {}
        // 如果解码失败，尝试直接使用原字符串，确保不为空
        val sanitized = sanitizeDisplayName(encoded)
        return sanitized.ifEmpty { "错误空" }
    }

    internal fun decodeDisplayNameFromTransportInternal(encoded: String): String = decodeDisplayNameFromTransport(encoded)

    internal fun startServerInternal() = startServer()

    internal fun updateDeviceListInternal() = updateDeviceList()

    internal fun saveAuthedDevicesInternal() = saveAuthedDevices()

    internal fun decryptDataInternal(input: String, uuid: String): String = decryptData(input, uuid)

    internal fun getDeviceInfoInternal(uuid: String): DeviceInfo? = getDeviceInfo(uuid)

    // Rust 原生上下文
    private var rustContext: com.sun.jna.Pointer? = null
    internal val rustContextInternal: com.sun.jna.Pointer?
        get() = rustContext

    private var serverSocket: ServerSocket? = null
    private val deviceLastSeen = mutableMapOf<String, Long>()
    // 心跳定时任务
    private val heartbeatJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    // UI全局开关：是否启用UDP发现，使用内存缓存避免频繁数据库访问
    // 使用AppConfig管理UDP发现配置
    var udpDiscoveryEnabled: Boolean
        get() {
            return AppConfig.getUdpDiscoveryEnabled(context)
        }
        set(value) {
            AppConfig.setUdpDiscoveryEnabled(context, value)
        }

    init {
        // 设置静态引用供 native 回调使用
        _callbackInstance = this

        val savedUuid = StorageManager.getString(context, "device_uuid")
        if (savedUuid.isNotEmpty()) {
            uuid = savedUuid
        } else {
            val newUuid = UUID.randomUUID().toString()
            StorageManager.putString(context, "device_uuid", newUuid)
            uuid = newUuid
        }
        // 兼容旧用户：首次运行时如无保存则默认true
        if (!AppConfig.getUdpDiscoveryEnabled(context)) {
            AppConfig.setUdpDiscoveryEnabled(context, true)
        }
        // 初始化 Rust 上下文并获取/生成本机 ECDH 密钥对
        var initPubKey = ""
        try {
            rustContext = NativeCore.createContext()
            val ctx = rustContext!!
            val savedStateEnc = StorageManager.getString(context, "rust_core_state")
            if (savedStateEnc.isNotEmpty()) {
                val decrypted = NativeCore.decryptLocalState(ctx, savedStateEnc, uuid)
                if (decrypted != null) {
                    NativeCore.importState(ctx, decrypted)
                }
            }
            if (!NativeCore.hasKeypair(ctx)) {
                NativeCore.generateKeypair(ctx)
            }
            initPubKey = NativeCore.getPublicKey(ctx) ?: ""
            val stateJson = NativeCore.exportState(ctx)
            if (stateJson != null) {
                val encrypted = NativeCore.encryptLocalState(ctx, stateJson, uuid)
                if (encrypted != null) {
                    StorageManager.putString(context, "rust_core_state", encrypted)
                }
            }
            Logger.d("死神-NotifyRelay", "Rust core 上下文已初始化")
            // 注册 Rust 回调
            setupRustCallbacks()
        } catch (e: Exception) {
            Logger.e("死神-NotifyRelay", "Rust core 初始化失败", e)
        }
        localPublicKey = initPubKey
        loadAuthedDevices()
        // 新增：初始补全本机 deviceInfoCache，便于反向 connectToDevice
        val displayName = getLocalDisplayName()
        val localIp = discoveryManager.getLocalIpAddressInternal()
        synchronized(deviceInfoCache) {
            deviceInfoCache[uuid] = DeviceInfo(uuid, displayName, localIp, listenPort)
        }
        startOfflineDeviceCleaner()
        discoveryManager.registerNetworkCallback()
        startWifiDirectReconnectionChecker()
    }

    // 统一设备状态管理：3秒未发现未认证设备直接移除，已认证设备置灰
    private fun startOfflineDeviceCleaner() {
        coroutineScope.launch {
            while (true) {
                delay(1000)
                try {
                    updateDeviceList()
                } catch (e: Exception) {
                    Logger.e("死神-NotifyRelay", "startOfflineDeviceCleaner定时器异常: ${e.message}")
                }
            }
        }
    }

    private fun updateDeviceList() {
        val now = System.currentTimeMillis()
        //Logger.d("死神-NotifyRelay", "[updateDeviceList] invoked at $now")
        val authSnapshot = synchronized(authenticatedDevices) { authenticatedDevices.toMap() }
        val authed = authSnapshot.keys.toSet()
        val lastSeenSnapshot = synchronized(deviceLastSeen) { deviceLastSeen.toMap() }
        val allUuids = (lastSeenSnapshot.keys + authed).toSet()
        val newMap = mutableMapOf<String, Pair<DeviceInfo, Boolean>>()
        val unauthedTimeout = 5000L // 未认证设备保留两次UDP广播周期（2*2000ms）
        val authedHeartbeatTimeout = 12_000L // 已认证设备心跳超时阈值
        val oldMap = _devices.value
        // 计算旧的已认证且在线数量快照
        val oldAuthOnlineCount = try {
            oldMap.count { (uuid, pair) -> pair.second && (authSnapshot[uuid]?.isAccepted == true) }
        } catch (_: Exception) { 0 }
        for (uuid in allUuids) {
            // 过滤掉uuid为"本机"的记录
            if (uuid == "本机") continue
            
            val deviceInfo = getDeviceInfo(uuid)
            
            val lastSeen = lastSeenSnapshot[uuid]
            val auth = synchronized(authenticatedDevices) { authenticatedDevices[uuid] }
            // 不兼容的旧版协议设备，强制离线
            val isIncompatible = incompatibleDevicesInternal.contains(uuid)
            if (auth != null) {
                // 仅基于心跳包判定在线
                val diff = if (lastSeen != null) now - lastSeen else -1L
                val isOnline = !isIncompatible && lastSeen != null && diff <= authedHeartbeatTimeout
                val info = deviceInfo ?: DeviceInfo(uuid, auth.displayName ?: "已认证设备", "", listenPort)
                val oldOnline = oldMap[uuid]?.second
                if (oldOnline != null && oldOnline != isOnline) {
                    Logger.i("天使-死神-NotifyRelay", "[updateDeviceList] 已认证设备状态变化: uuid=$uuid, isOnline=$isOnline, lastSeen=$lastSeen, diff=$diff")
                }
                newMap[uuid] = info to isOnline
            } else {
                val diff = if (lastSeen != null) now - lastSeen else -1L
                val isOnline = lastSeen != null && diff <= unauthedTimeout
                val info = deviceInfo
                val oldOnline = oldMap[uuid]?.second
                if (oldOnline != null && oldOnline != isOnline) {
                    Logger.i("死神-NotifyRelay", "[updateDeviceList] 未认证设备状态变化: uuid=$uuid, isOnline=$isOnline, lastSeen=$lastSeen, diff=$diff")
                }
                if (isOnline) {
                    if (info != null) newMap[uuid] = info to true
                } else {
                    synchronized(deviceLastSeen) { deviceLastSeen.remove(uuid) }
                }
            }
        }
        // 仅在设备列表或在线状态发生实际变化时触发回调，避免频繁刷新
        // 计算新的已认证且在线数量快照
        val newAuthOnlineCount = try {
            newMap.count { (uuid, pair) -> pair.second && (authSnapshot[uuid]?.isAccepted == true) }
        } catch (_: Exception) { 0 }

        // 直接更新Flow值，UI层通过Flow订阅获取变化
        _devices.value = newMap

        // 更新在线设备缓存，供 scrcpy 模块使用
        updateOnlineDevicesCache(newMap, authSnapshot)
    }

    private fun updateOnlineDevicesCache(
        deviceMap: Map<String, Pair<DeviceInfo, Boolean>>,
        authSnapshot: Map<String, AuthInfo>
    ) {
        try {
            val onlineDevices = deviceMap.filter { (uuid, pair) ->
                pair.second && (authSnapshot[uuid]?.isAccepted == true)
            }.mapNotNull { (uuid, pair) ->
                val info = pair.first
                val auth = authSnapshot[uuid]
                if (info.ip.isNotBlank() && info.ip != "0.0.0.0") {
                    notifyrelay.data.model.OnlineDeviceInfo(
                        uuid = info.uuid,
                        displayName = info.displayName,
                        ip = info.ip,
                        port = info.port,
                        deviceType = auth?.deviceType
                    )
                } else null
            }
            val gson = com.google.gson.Gson()
            val json = gson.toJson(onlineDevices)
            
            // 只有当内容实际变化时才执行存储和快捷方式更新
            if (json != lastOnlineDevicesCacheJson) {
                StorageManager.putString(
                    context,
                    notifyrelay.data.config.ScrcpyPreferenceKeys.ONLINE_DEVICES_CACHE,
                    json,
                    StorageManager.PrefsType.SCRCPY
                )
                try {
                    io.github.miuzarte.scrcpyforandroid.services.DynamicShortcutManager
                        .updateShortcuts(context)
                } catch (_: Exception) {}
                lastOnlineDevicesCacheJson = json
            }
        } catch (_: Exception) {}
    }

    private fun getDeviceInfo(uuid: String): DeviceInfo? {
        // 优先从缓存取（含真实ip）
        synchronized(deviceInfoCache) {
            deviceInfoCache[uuid]?.let { return it }
        }
        // 其次从设备流取
        _devices.value[uuid]?.first?.let { return it }
        // 最后从认证表补全（无ip）
        val auth = authenticatedDevices[uuid]
        if (auth != null) {
            val name = auth.displayName ?: DeviceConnectionManagerUtil.getDisplayNameByUuid(uuid)
            val ip = auth.lastIp ?: ""
            val port = auth.lastPort ?: listenPort
            return DeviceInfo(uuid, name, ip, port)
        }
        // 新增：本机兜底逻辑
        if (uuid == this.uuid) {
            val displayName = getLocalDisplayName()
            val localIp = discoveryManager.getLocalIpAddressInternal()
            return DeviceInfo(uuid, displayName, localIp, listenPort)
        }
        return null
    }

    /**
     * 获取已认证设备列表
     */
    fun getAuthenticatedDevices(): Map<String, AuthInfo> {
        synchronized(authenticatedDevices) {
            return authenticatedDevices.toMap()
        }
    }

    /**
     * 获取已拒绝设备列表
     */
    fun getRejectedDevices(): Set<String> {
        synchronized(rejectedDevices) {
            return rejectedDevices.toSet()
        }
    }

    /**
     * 保存已认证设备（公开API）
     */
    fun saveAuthedDevicesPublic() {
        saveAuthedDevices()
    }

    /**
     * 更新设备列表（公开API）
     */
    fun updateDeviceListPublic() {
        updateDeviceList()
    }

    /**
     * 客户端收到 ACCEPT 后，在本机完成配对：派生密钥、导入 Keystore、标记已认证。
     */
    fun completePairing(remoteUuid: String, remotePubKey: String) {
        try {
            val ctx = rustContext
            if (ctx != null) {
                NativeCore.deriveSharedSecret(ctx, remoteUuid, remotePubKey)
            }
            synchronized(authenticatedDevices) {
                authenticatedDevices[remoteUuid] = AuthInfo(
                    remotePubKey, "", true, "未知设备"
                )
                saveAuthedDevices()
            }
            updateDeviceList()
            Logger.d("死神-NotifyRelay", "客户端配对完成: $remoteUuid")
        } catch (e: Exception) {
            Logger.e("死神-NotifyRelay", "客户端配对失败: $remoteUuid", e)
        }
    }

    // 存储待处理的配对请求信息（接收端对话框需要远端临时公钥）
    data class PendingPairing(
        val remoteUuid: String,
        val remotePubKey: String,
        val remoteIp: String,
        val tmpPubKey: String = ""  // 发起端的临时公钥，用于加密回传配对码
    )
    private val _pendingPairingLock = Any()
    private var _pendingPairing: PendingPairing? = null
    var pendingPairing: PendingPairing?
        get() = synchronized(_pendingPairingLock) { _pendingPairing }
        internal set(value) = synchronized(_pendingPairingLock) { _pendingPairing = value }

    /**
     * 取消当前待处理的配对请求。
     */
    fun cancelPendingPairing() {
        val p = pendingPairing
        if (p != null) {
            Logger.d("死神-NotifyRelay", "取消配对: ${p.remoteUuid}")
        }
        pendingPairing = null
        notifyrelay.core.util.PairingCodeManager.clear()
    }

    /**
     * 发起端存储在配对阶段的临时 ECDH 私钥（Base64 编码），
     * 供 ServerLineRouter.handlePairingResp 解密接收端回传的配对码。
     * 配对完成后应清空。
     */
    private val _pendingTempPrivKeyB64Lock = Any()
    private var _pendingTempPrivKeyB64: String? = null
    var pendingTempPrivKeyB64: String?
        get() = synchronized(_pendingTempPrivKeyB64Lock) { _pendingTempPrivKeyB64 }
        set(value) = synchronized(_pendingTempPrivKeyB64Lock) { _pendingTempPrivKeyB64 = value }

    /**
     * 使用长期 ECDH 密钥完成标准密钥交换。
     * 配对码验证通过后，双方使用长期 ECDH 密钥派生共享密钥并导入 Keystore。
     *
     * @param uuid 远端设备 UUID
     * @param remoteLtPubKey 远端长期 ECDH 公钥 Base64
     * @param displayName 设备显示名称
     * @param lastIp 设备 IP
     * @return 是否成功
     */
    fun completePairingWithLongTermKeys(
        uuid: String,
        remoteLtPubKey: String,
        displayName: String = "未知设备",
        lastIp: String? = null
    ): Boolean {
        return try {
            val ctx = rustContext
            if (ctx != null) {
                NativeCore.deriveSharedSecret(ctx, uuid, remoteLtPubKey)
            }
            synchronized(authenticatedDevices) {
                authenticatedDevices[uuid] = AuthInfo(
                    remoteLtPubKey, "", true, displayName,
                    lastIp = lastIp
                )
                saveAuthedDevices()
            }
            updateDeviceList()
            Logger.d("死神-NotifyRelay", "长期密钥配对完成: $uuid")
            true
        } catch (e: Exception) {
            Logger.e("死神-NotifyRelay", "长期密钥配对失败: $uuid", e)
            false
        }
    }

    /**
     * 公开解析设备信息：优先使用缓存/认证信息，缺失IP时使用提供的回退IP。
     */
    fun resolveDeviceInfo(uuid: String, fallbackIp: String?, fallbackPort: Int = 23333): DeviceInfo? {
        val cached = getDeviceInfo(uuid)
        if (cached != null && cached.ip.isNotEmpty() && cached.ip != "0.0.0.0") return cached
        val auth = synchronized(authenticatedDevices) { authenticatedDevices[uuid] }
        val name = auth?.displayName ?: DeviceConnectionManagerUtil.getDisplayNameByUuid(uuid)
        val port = cached?.port ?: auth?.lastPort ?: fallbackPort
        return fallbackIp?.let { DeviceInfo(uuid, name, it, port) }
    }

    internal fun isWifiDirectNetworkInternal(): Boolean {
        return discoveryManager.isWifiDirectNetworkInternal()
    }

    // 连接设备
    fun connectToDevice(device: DeviceInfo, callback: ((Boolean, String?) -> Unit)? = null) {
        coroutineScope.launch {
            try {
                if (rejectedDevices.contains(device.uuid)) {
                    //Logger.d("死神-NotifyRelay", "connectToDevice: 已被对方拒绝 uuid=${device.uuid}")
                    callback?.invoke(false, "已被对方拒绝")
                    return@launch
                }

                // 新增：WLAN直连模式下增加重试次数
                val maxRetries = if (isWifiDirectNetworkInternal()) 3 else 1
                val result = keepAlive.performDeviceConnectionWithRetry(device, maxRetries)
                callback?.invoke(result.first, result.second)
            } catch (e: Exception) {
                Logger.e("死神-NotifyRelay", "connectToDevice异常: ${e.message}")
                e.printStackTrace()
                callback?.invoke(false, e.message)
            }
        }
    }

    // 使用 Rust core 加密，失败直接抛异常
    // header 为协议头（如 DATA_NOTIFICATION），返回完整报文：$header:uuid:pubKey:encrypted
    internal fun encryptData(input: String, uuid: String, header: String = "DATA"): String {
        val ctx = rustContext ?: throw IllegalStateException("Rust context not initialized")
        return NativeCore.encryptMessage(ctx, header, this.uuid, this.localPublicKey, uuid, input)
            ?: throw IllegalStateException("Rust加密失败: encryptMessage, device=$uuid")
    }

    // 使用 Rust core 解密，失败直接抛异常
    internal fun decryptData(input: String, uuid: String): String {
        val ctx = rustContext ?: throw IllegalStateException("Rust context not initialized")
        return NativeCore.decryptPayload(ctx, uuid, input)
            ?: throw IllegalStateException("Rust解密失败: decryptPayload, device=$uuid")
    }

    // 发送通知数据（加密）
    fun sendNotificationData(device: DeviceInfo, data: String) {
        coroutineScope.launch {
            try {
                val auth = authenticatedDevices[device.uuid]
                if (auth == null || !auth.isAccepted) {
                    //Logger.d("死神-NotifyRelay", "未认证设备，禁止发送")
                    return@launch
                }
                ProtocolSender.sendEncrypted(this@DeviceConnectionManager, device, "DATA_NOTIFICATION", data, 10000L)
            } catch (e: Exception) {
                Logger.e("死神-NotifyRelay", "发送通知数据失败", e)
            }
        }
    }

    /**
     * 公开API：请求远端设备的“用户应用列表”。
     */
    fun requestRemoteAppList(device: DeviceInfo, scope: String = "user") {
        try {
            AppListSyncManager.requestAppListFromDevice(context, this, device, scope)
        } catch (_: Exception) {}
    }

    /**
     * 公开API：请求远端设备转发音频。
     * @return 是否成功发送请求
     */
    fun requestAudioForwarding(device: DeviceInfo): Boolean {
        try {
            val raw = "{\"type\":\"MEDIA_CONTROL\",\"action\":\"audioRequest\"}"
            ProtocolSender.sendEncrypted(this, device, "DATA_MEDIA_CONTROL", raw, 10000L)
            return true
        } catch (_: Exception) {
            return false
        }
    }
    
    /**
     * 公开API：发送剪贴板内容到指定设备。
     * @param device 目标设备
     * @param clipboardType 剪贴板类型（text/image）
     * @param content 剪贴板内容
     * @return 是否成功发送请求
     */
    fun sendClipboardToDevice(device: DeviceInfo, clipboardType: String, content: String): Boolean {
        try {
            val raw = org.json.JSONObject().apply {
                put("type", "clipboard")
                put("clipboardType", clipboardType)
                put("content", content)
                put("time", System.currentTimeMillis())
            }.toString()
            ProtocolSender.sendEncrypted(this, device, "DATA_CLIPBOARD", raw, 10000L)
            return true
        } catch (_: Exception) {
            return false
        }
    }
    
    /**
     * 公开API：发送剪贴板内容到所有已认证的在线设备。
     * @param clipboardType 剪贴板类型（text/image）
     * @param content 剪贴板内容
     * @return 是否成功发送请求
     */
    fun sendClipboardToAllDevices(clipboardType: String, content: String): Boolean {
        try {
            val devices = getAuthenticatedOnlineDevices()
            if (devices.isEmpty()) return false
            
            val raw = org.json.JSONObject().apply {
                put("type", "clipboard")
                put("clipboardType", clipboardType)
                put("content", content)
                put("time", System.currentTimeMillis())
            }.toString()
            for (device in devices) {
                ProtocolSender.sendEncrypted(this, device, "DATA_CLIPBOARD", raw, 10000L)
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    init {
        // 启动图标同步过期请求清理协程
        coroutineScope.launch {
            while (true) {
                delay(60000) // 每分钟清理一次
                IconSyncManager.cleanupExpiredRequests()
            }
        }
    }

    // 注册 Rust 回调，每个 DATA_* 类型直接调用对应处理器
    private fun setupRustCallbacks() {
        val ctx = rustContext ?: return
        val lib = NotifyRelayCore.instance()
        fun ptr2str(ptr: Pointer?) = NotifyRelayCore.ptrToString(ptr)

        // 注意：Android 端 Rust 日志由 android_logger crate 直接写入 logcat（tag: NotifyRelayCore）
        // 无需通过 FFI 日志回调，此处仅注册数据回调
        // rustCallbackRefs 保持所有 JNA 回调对象强引用，防止 GC 回收导致 native 指针悬空

        fun cb(setter: (NotifyRelayCore.OnDataCb?) -> Unit, tag: String, handler: (String, String) -> Unit) {
            val cb = object : NotifyRelayCore.OnDataCb {
                override fun invoke(localUuid: Pointer?, plaintext: Pointer?, userData: Pointer?) {
                    val uuid = ptr2str(localUuid) ?: return
                    val text = ptr2str(plaintext) ?: return
                    val authed = synchronized(authenticatedDevices) {
                        authenticatedDevices[uuid]?.isAccepted == true
                    }
                    android.util.Log.d("CoreCb", "$tag: uuid=$uuid, authed=$authed, text_len=${text.length}")
                    if (!authed) return
                    handler(uuid, text)
                }
            }
            setter(cb); rustCallbackRefs.add(cb)
        }

        cb({ lib.nrc_set_on_notification_cb(ctx, it) }, "DATA_NOTIFICATION") { uuid, text ->
            NotificationProcessor.process(context, this, coroutineScope,
                NotificationProcessor.NotificationInput("DATA_NOTIFICATION", text, uuid),
                notificationDataReceivedCallbacksInternal)
        }
        cb({ lib.nrc_set_on_media_play_cb(ctx, it) }, "DATA_MEDIAPLAY") { uuid, text ->
            try {
                val json = JSONObject(text)
                resolveDeviceInfo(uuid, "", 23333)?.let {
                    RemoteMediaSessionManager.onMediaMessageReceived(context, json, it)
                }
            } catch (e: Exception) { Logger.e("CoreCb", "DATA_MEDIAPLAY", e) }
        }
        cb({ lib.nrc_set_on_icon_request_cb(ctx, it) }, "DATA_ICON_REQUEST") { uuid, text ->
            resolveDeviceInfo(uuid, "", 23333)?.let {
                IconSyncManager.handleIconRequest(text, this, it, context)
            }
        }
        cb({ lib.nrc_set_on_icon_response_cb(ctx, it) }, "DATA_ICON_RESPONSE") { _, text ->
            IconSyncManager.handleIconResponse(text, context)
        }
        cb({ lib.nrc_set_on_app_list_request_cb(ctx, it) }, "DATA_APP_LIST_REQUEST") { uuid, text ->
            resolveDeviceInfo(uuid, "", 23333)?.let {
                AppListSyncManager.handleAppListRequest(text, this, it, context)
            }
        }
        cb({ lib.nrc_set_on_app_list_response_cb(ctx, it) }, "DATA_APP_LIST_RESPONSE") { uuid, text ->
            AppListSyncManager.handleAppListResponse(text, context, uuid, this)
        }
        cb({ lib.nrc_set_on_media_control_cb(ctx, it) }, "DATA_MEDIA_CONTROL") { uuid, text ->
            try {
                val json = JSONObject(text)
                val action = json.getString("action")
                when (action) {
                    "playPause" -> try { MediaControlUtil.playPause(); sendMediaControlResponse(uuid, "playPause", "success", null) }
                    catch (e: Exception) { sendMediaControlResponse(uuid, "playPause", "error", e.message) }
                    "next" -> try { MediaControlUtil.next(); sendMediaControlResponse(uuid, "next", "success", null) }
                    catch (e: Exception) { sendMediaControlResponse(uuid, "next", "error", e.message) }
                    "previous" -> try { MediaControlUtil.previous(); sendMediaControlResponse(uuid, "previous", "success", null) }
                    catch (e: Exception) { sendMediaControlResponse(uuid, "previous", "error", e.message) }
                    "audioRequest" -> {
                        val device = resolveDeviceInfo(uuid, "", 23333)
                        val ok = device?.let { AudioForwardingService.startAudioForwarding(context, it.ip, ScrcpyDefaults.ADB_PORT, it.displayName) } == true
                        val result = if (ok) "accepted" else "rejected"
                        val raw = "{\"type\":\"MEDIA_CONTROL\",\"action\":\"audioResponse\",\"result\":\"$result\"}"
                        device?.let { ProtocolSender.sendEncrypted(this, it, "DATA_MEDIA_CONTROL", raw) }
                    }
                    "audioResponse" -> {
                        if (json.optString("result", "rejected") != "accepted") {
                            coroutineScope.launch { notifyrelay.base.util.ToastUtils.showShortToast(context, "音频转发请求被拒绝") }
                        }
                    }
                }
            } catch (e: Exception) { Logger.e("CoreCb", "DATA_MEDIA_CONTROL", e) }
        }
        cb({ lib.nrc_set_on_ftp_cb(ctx, it) }, "DATA_FTP") { uuid, text ->
            val isPc = synchronized(authenticatedDevices) {
                authenticatedDevices[uuid]?.deviceType?.lowercase() == "pc"
            }
            if (!isPc) return@cb
            coroutineScope.launch {
                try {
                    val json = JSONObject(text)
                    when (json.optString("action", "")) {
                        "start" -> {
                            val pcUser = json.optString("username", null)
                            val pcPass = json.optString("password", null)
                            val result = ftpServer.start(getLocalDisplayName(), context, pcUser, pcPass)
                            when (result.status) {
                                StartResult.SUCCESS, StartResult.ALREADY_RUNNING -> {
                                    result.serverInfo?.let { info ->
                                        val raw = JSONObject().apply { put("action", "started"); put("ipAddress", info.ipAddress); put("port", info.port) }.toString()
                                        resolveDeviceInfo(uuid, "", 23333)?.let {
                                            ProtocolSender.sendEncrypted(this@DeviceConnectionManager, it, "DATA_FTP", raw)
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                                            val intent = IntentUtils.createIntent(context, GuideActivity::class.java)
                                            intent.putExtra("fromftp", true); intent.putExtra("fromInternal", true)
                                            IntentUtils.startActivity(context, intent, true)
                                        }
                                    }
                                }
                                else -> {
                                    val err = when (result.status) { StartResult.PERMISSION_DENIED -> "PERMISSION_DENIED"; StartResult.PORT_IN_USE -> "PORT_IN_USE"; StartResult.CONFIG_ERROR -> "CONFIG_ERROR"; else -> "FAILED" }
                                    val raw = JSONObject().apply { put("originalHeader", "DATA_FTP"); put("action", "start"); put("result", "error"); put("errorCode", err) }.toString()
                                    resolveDeviceInfo(uuid, "", 23333)?.let {
                                        ProtocolSender.sendEncrypted(this@DeviceConnectionManager, it, "DATA_STATUS", raw)
                                    }
                                }
                            }
                        }
                        "stop" -> { ftpServer.stop()
                            val raw = JSONObject().apply { put("action", "stopped") }.toString()
                            resolveDeviceInfo(uuid, "", 23333)?.let {
                                ProtocolSender.sendEncrypted(this@DeviceConnectionManager, it, "DATA_FTP", raw)
                            }
                        }
                    }
                } catch (e: Exception) { Logger.e("CoreCb", "DATA_FTP", e) }
            }
        }
        cb({ lib.nrc_set_on_clipboard_cb(ctx, it) }, "DATA_CLIPBOARD") { _, text ->
            ClipboardProcessor.process(context, ClipboardProcessor.ClipboardInput("DATA_CLIPBOARD", text, ""))
        }
        cb({ lib.nrc_set_on_status_cb(ctx, it) }, "DATA_STATUS") { uuid, text ->
            StatusProcessor.process(context, this, coroutineScope,
                StatusProcessor.StatusInput("DATA_STATUS", text, uuid),
                notificationDataReceivedCallbacksInternal)
        }
        cb({ lib.nrc_set_on_app_launch_cb(ctx, it) }, "DATA_APP_LAUNCH") { uuid, text ->
            resolveDeviceInfo(uuid, "", 23333)?.let {
                AppLaunchManager.handleAppLaunchRequest(text, this, it, context)
            }
        }
        cb({ lib.nrc_set_on_superisland_cb(ctx, it) }, "DATA_SUPERISLAND") { uuid, text ->
            try { SuperIslandProcessor.process(context, this, text, uuid) } catch (e: Exception) { Logger.e("CoreCb", "DATA_SUPERISLAND", e) }
        }
        cb({ lib.nrc_set_on_unknown_data_cb(ctx, it) }, "DATA_UNKNOWN") { uuid, text ->
            Logger.d("CoreCb", "未知DATA通道: uuid=$uuid, size=${text.length}")
        }

        // ==================== 非 DATA 回调注册 ====================

        // ---- on_handshake ----
        run {
            val cb = object : NotifyRelayCore.OnHandshakeCb {
                override fun invoke(uuid: Pointer?, pubKey: Pointer?, ip: Pointer?, battery: Int, deviceType: Pointer?, userData: Pointer?) {
                    val session = ServerLineRouter.getSessionContext() ?: return
                    val dm = session.deviceManager
                    val remoteUuid = ptr2str(uuid) ?: return
                    val remotePubKey = ptr2str(pubKey) ?: return
                    val remoteIp = ptr2str(ip) ?: return
                    val remoteDeviceType = ptr2str(deviceType) ?: "unknown"
                    val clientIp = session.client.inetAddress.hostAddress.orEmpty().ifEmpty { "0.0.0.0" }

                    try {
                        synchronized(dm.deviceInfoCacheInternal) {
                            val old = dm.deviceInfoCacheInternal[remoteUuid]
                            val displayName = old?.displayName ?: "未知设备"
                            dm.deviceInfoCacheInternal[remoteUuid] = DeviceInfo(remoteUuid, displayName, clientIp, old?.port ?: 23333)
                        }
                        synchronized(dm.authenticatedDevices) {
                            val auth = dm.authenticatedDevices[remoteUuid]
                            if (auth != null) {
                                dm.authenticatedDevices[remoteUuid] = auth.copy(lastIp = clientIp)
                                dm.saveAuthedDevicesInternal()
                            }
                        }
                        val alreadyAuthed = synchronized(dm.authenticatedDevices) {
                            dm.authenticatedDevices[remoteUuid]?.isAccepted == true
                        }
                        if (alreadyAuthed) {
                            synchronized(dm.authenticatedDevices) {
                                val existingAuth = dm.authenticatedDevices[remoteUuid]
                                if (existingAuth != null && existingAuth.publicKey != remotePubKey) {
                                    dm.authenticatedDevices[remoteUuid] = existingAuth.copy(publicKey = remotePubKey, sharedSecret = "")
                                    dm.saveAuthedDevicesInternal()
                                }
                            }
                            synchronized(dm.incompatibleDevicesInternal) { dm.incompatibleDevicesInternal.remove(remoteUuid) }
                            val localIp = ServerLineRouter.getLocalIpAddress(dm)
                            NativeCore.sendAccept(dm.rustContextInternal!!, dm.uuid, dm.localPublicKey, localIp, BatteryUtils.getBatteryLevel(dm.contextInternal), remoteDeviceType)
                            synchronized(dm.authenticatedDevices) {
                                val auth = dm.authenticatedDevices[remoteUuid]
                                if (auth != null) {
                                    dm.authenticatedDevices[remoteUuid] = auth.copy(deviceType = remoteDeviceType, lastIp = clientIp)
                                    dm.saveAuthedDevicesInternal()
                                }
                            }
                        } else {
                            NativeCore.sendReject(dm.rustContextInternal!!, dm.uuid)
                        }
                    } catch (e: Exception) {
                        Logger.e("CoreCb", "on_handshake error: ${e.message}")
                    }
                }
            }
            lib.nrc_set_on_handshake_cb(ctx, cb); rustCallbackRefs.add(cb)
        }

        // ---- on_pairing_init ----
        run {
            val cb = object : NotifyRelayCore.OnPairingInitCb {
                override fun invoke(uuid: Pointer?, tmpPubKey: Pointer?, ip: Pointer?, battery: Int, deviceType: Pointer?, userData: Pointer?) {
                    val session = ServerLineRouter.getSessionContext() ?: return
                    val dm = session.deviceManager
                    val remoteUuid = ptr2str(uuid) ?: return
                    val tmpPub = ptr2str(tmpPubKey) ?: return
                    val remoteIp = ptr2str(ip) ?: return
                    val clientIp = session.client.inetAddress.hostAddress.orEmpty().ifEmpty { "0.0.0.0" }

                    try {
                        synchronized(dm.rejectedDevicesInternal) {
                            if (dm.rejectedDevicesInternal.contains(remoteUuid)) {
                                val w = OutputStreamWriter(session.client.getOutputStream())
                                w.write("REJECT:${dm.uuid}\n"); w.flush(); w.close()
                                return@invoke
                            }
                        }
                        val displayName: String
                        synchronized(dm.deviceInfoCacheInternal) {
                            displayName = dm.deviceInfoCacheInternal[remoteUuid]?.displayName ?: "未知设备"
                            dm.deviceInfoCacheInternal[remoteUuid] = DeviceInfo(remoteUuid, displayName, clientIp, 23333)
                        }
                        dm.pendingPairing = PendingPairing(remoteUuid = remoteUuid, remotePubKey = tmpPub, remoteIp = clientIp, tmpPubKey = tmpPub)
                        val remoteDevice = DeviceInfo(remoteUuid, displayName, clientIp, 23333)
                        dm.handshakeRequestHandler?.onPairingInitRequest(remoteDevice, tmpPub)
                        Logger.d("CoreCb", "PAIRING_INIT 已处理: $remoteUuid")
                    } catch (e: Exception) {
                        Logger.e("CoreCb", "on_pairing_init error: ${e.message}")
                    }
                }
            }
            lib.nrc_set_on_pairing_init_cb(ctx, cb); rustCallbackRefs.add(cb)
        }

        // ---- on_pairing_resp ----
        run {
            val cb = object : NotifyRelayCore.OnPairingRespCb {
                override fun invoke(uuid: Pointer?, tmpPub: Pointer?, ltPub: Pointer?, encryptedCode: Pointer?, ip: Pointer?, battery: Int, deviceType: Pointer?, userData: Pointer?) {
                    val session = ServerLineRouter.getSessionContext() ?: return
                    val dm = session.deviceManager
                    val remoteUuid = ptr2str(uuid) ?: return
                    val tmpPubKeyR = ptr2str(tmpPub) ?: return
                    val ltPubKeyR = ptr2str(ltPub) ?: return
                    val encCode = ptr2str(encryptedCode) ?: return

                    try {
                        val ctx = dm.rustContextInternal
                        if (ctx == null) {
                            val w = OutputStreamWriter(session.client.getOutputStream())
                            w.write("REJECT:${dm.uuid}\n"); w.flush(); w.close()
                            return@invoke
                        }
                        val code = NativeCore.decryptPairingCode(ctx, encCode) ?: throw Exception("解密配对码失败")
                        if (!PairingCodeManager.verify(code)) {
                            Logger.w("CoreCb", "配对码验证失败: $remoteUuid")
                            NativeCore.sendReject(dm.rustContextInternal!!, dm.uuid)
                            return@invoke
                        }
                        val success = dm.completePairingWithLongTermKeys(remoteUuid, ltPubKeyR, displayName = "未知设备", lastIp = session.client.inetAddress.hostAddress.orEmpty().ifEmpty { "0.0.0.0" })
                        if (!success) {
                            NativeCore.sendReject(dm.rustContextInternal!!, dm.uuid)
                            return@invoke
                        }
                        val localIp = ServerLineRouter.getLocalIpAddress(dm)
                        NativeCore.sendAccept(dm.rustContextInternal!!, dm.uuid, dm.localPublicKey, localIp, BatteryUtils.getBatteryLevel(dm.contextInternal), "android")
                        Logger.d("CoreCb", "PAIRING_RESP 配对成功: $remoteUuid")
                    } catch (e: Exception) {
                        Logger.e("CoreCb", "on_pairing_resp error", e)
                    }
                }
            }
            lib.nrc_set_on_pairing_resp_cb(ctx, cb); rustCallbackRefs.add(cb)
        }

        // ---- on_accept ----
        run {
            val cb = object : NotifyRelayCore.OnAcceptCb {
                override fun invoke(uuid: Pointer?, ltPubKey: Pointer?, ip: Pointer?, battery: Int, deviceType: Pointer?, userData: Pointer?) {
                    // ACCEPT 在服务端 routeLine 中无操作（由客户端侧处理）
                }
            }
            lib.nrc_set_on_accept_cb(ctx, cb); rustCallbackRefs.add(cb)
        }

        // ---- on_reject ----
        run {
            val cb = object : NotifyRelayCore.OnRejectCb {
                override fun invoke(uuid: Pointer?, userData: Pointer?) {
                    // REJECT 在服务端 routeLine 中无操作（由客户端侧处理）
                }
            }
            lib.nrc_set_on_reject_cb(ctx, cb); rustCallbackRefs.add(cb)
        }

        // ---- on_heartbeat_tcp ----
        run {
            val cb = object : NotifyRelayCore.OnHeartbeatTcpCb {
                override fun invoke(uuid: Pointer?, nameB64: Pointer?, port: Short, battery: Int, deviceType: Pointer?, ip: Pointer?, userData: Pointer?) {
                    val session = ServerLineRouter.getSessionContext() ?: return
                    val dm = session.deviceManager
                    val remoteUuid = ptr2str(uuid) ?: return
                    val remoteNameB64 = ptr2str(nameB64) ?: return
                    val remoteDeviceType = ptr2str(deviceType) ?: "unknown"
                    val clientIp = session.client.inetAddress.hostAddress.orEmpty()

                    try {
                        val displayName = try {
                            dm.decodeDisplayNameFromTransportInternal(remoteNameB64)
                        } catch (_: Exception) { remoteNameB64 }
                        val info = HeartbeatProcessor.HeartbeatInfo(
                            uuid = remoteUuid,
                            displayName = displayName,
                            port = port.toInt(),
                            batteryLevel = kotlin.math.abs(battery),
                            isCharging = battery >= 0,
                            deviceType = remoteDeviceType,
                            ip = clientIp
                        )
                        if (info.uuid != dm.uuid) {
                            HeartbeatProcessor.processHeartbeat(info, dm)
                        }
                    } catch (e: Exception) {
                        Logger.e("CoreCb", "on_heartbeat_tcp error", e)
                    }
                }
            }
            lib.nrc_set_on_heartbeat_tcp_cb(ctx, cb); rustCallbackRefs.add(cb)
        }

        // ---- on_send (统一发送回调：写入当前 TCP 会话) ----
        run {
            val cb = object : NotifyRelayCore.OnSendCb {
                override fun invoke(line: Pointer?, userData: Pointer?) {
                    val session = ServerLineRouter.getSessionContext() ?: return
                    val lineStr = NotifyRelayCore.ptrToString(line) ?: return
                    try {
                        val w = java.io.OutputStreamWriter(session.client.getOutputStream())
                        w.write("$lineStr\n"); w.flush(); w.close()
                    } catch (_: Exception) {}
                }
            }
            lib.nrc_set_on_send_cb(ctx, cb); rustCallbackRefs.add(cb)
        }

        // ---- on_send_udp (统一 UDP 发送回调) ----
        run {
            val cb = object : NotifyRelayCore.OnSendCb {
                override fun invoke(line: Pointer?, userData: Pointer?) {
                    val lineStr = NotifyRelayCore.ptrToString(line) ?: return
                    try {
                        val socket = java.net.DatagramSocket()
                        socket.broadcast = true
                        val buf = lineStr.toByteArray()
                        val addr = java.net.InetAddress.getByName("255.255.255.255")
                        socket.send(java.net.DatagramPacket(buf, buf.size, addr, 23334))
                        socket.close()
                    } catch (_: Exception) {}
                }
            }
            lib.nrc_set_on_send_udp_cb(ctx, cb); rustCallbackRefs.add(cb)
        }
    }

    // 辅助方法：发送媒体控制响应（由回调使用）
    private fun sendMediaControlResponse(remoteUuid: String, action: String, result: String, errorMessage: String?) {
        try {
            val raw = JSONObject().apply { put("originalHeader", "DATA_MEDIA_CONTROL"); put("action", action); put("result", result); if (errorMessage != null) put("errorMessage", errorMessage) }.toString()
            resolveDeviceInfo(remoteUuid, "", 23333)?.let {
                ProtocolSender.sendEncrypted(this, it, "DATA_STATUS", raw)
            }
        } catch (e: Exception) { Logger.e("CoreCb", "sendMediaControlResponse", e) }
    }

    // 启动TCP服务监听，接收其他设备的通知
    private fun startServer() {
        coroutineScope.launch {
            try {
                serverSocket = ServerSocket(listenPort)
                while (true) {
                    val client = serverSocket?.accept() ?: break
                    coroutineScope.launch {
                        try {
                            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                            val line = reader.readLine()
                            if (line != null) {
                                ServerLineRouter.routeLine(line, client, reader, this@DeviceConnectionManager)
                            } else {
                                try { reader.close() } catch (_: Exception) {}
                                try { client.close() } catch (_: Exception) {}
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 封装设备信息缓存更新操作
    private fun updateDeviceInfoCache(uuid: String, deviceInfo: DeviceInfo) {
        synchronized(deviceInfoCache) {
            deviceInfoCache[uuid] = deviceInfo
        }
    }

    // 封装设备信息缓存IP更新操作（保持其他信息不变）
    private fun updateDeviceInfoCacheIp(uuid: String, newIp: String) {
        synchronized(deviceInfoCache) {
            val old = deviceInfoCache[uuid]
            val displayName = old?.displayName ?: "未知设备"
            val port = old?.port ?: 23333
            deviceInfoCache[uuid] = DeviceInfo(uuid, displayName, newIp, port)
        }
    }

    // 封装设备信息缓存获取操作
    private fun getDeviceInfoFromCache(uuid: String): DeviceInfo? {
        synchronized(deviceInfoCache) {
            return deviceInfoCache[uuid]
        }
    }

    /**
     * 获取在线且已认证的设备数量（线程安全）。
     * 该方法读取当前设备列表快照和认证表快照，只把同时在线并且认证通过（isAccepted==true）的设备计入。
     */
    fun getAuthenticatedOnlineCount(): Int {
        try {
            val devsSnapshot = devices.value
            val authSnapshot = synchronized(authenticatedDevices) { authenticatedDevices.toMap() }
            return devsSnapshot.count { (uuid, pair) ->
                val isOnline = pair.second
                isOnline && (authSnapshot[uuid]?.isAccepted == true)
            }
        } catch (_: Exception) {
            return 0
        }
    }

    /**
     * 获取在线且已认证的设备列表（线程安全）。
     * 该方法读取当前设备列表快照和认证表快照，返回同时在线并且认证通过（isAccepted==true）的设备列表。
     */
    fun getAuthenticatedOnlineDevices(): List<DeviceInfo> {
        try {
            val devsSnapshot = devices.value
            val authSnapshot = synchronized(authenticatedDevices) { authenticatedDevices.toMap() }
            val deviceInfoSnapshot = synchronized(deviceInfoCache) { deviceInfoCache.toMap() }
            
            // 调试日志
            Logger.d("死神-NotifyRelay", "[getAuthenticatedOnlineDevices] 认证设备: ${authSnapshot.size} 个设备")
            Logger.d("死神-NotifyRelay", "[getAuthenticatedOnlineDevices] 设备信息缓存: ${deviceInfoSnapshot.size} 个设备")
            Logger.d("死神-NotifyRelay", "[getAuthenticatedOnlineDevices] 设备列表: ${devsSnapshot.size} 个设备")
            
            // 获取同时在线且已认证的设备列表
            val result = devsSnapshot.filter { (uuid, pair) ->
                val isOnline = pair.second
                isOnline && (authSnapshot[uuid]?.isAccepted == true)
            }.mapNotNull { (uuid, pair) ->
                // 从设备信息缓存中获取设备信息
                var deviceInfo = deviceInfoSnapshot[uuid]
                
                // 如果缓存中没有，使用设备列表中的设备信息
                if (deviceInfo == null) {
                    deviceInfo = pair.first
                }

                Logger.d("死神-NotifyRelay", "[getAuthenticatedOnlineDevices] 在线且已认证设备: $uuid, name=${deviceInfo?.displayName}, ip=${deviceInfo?.ip}")
                deviceInfo
            }
            
            Logger.d("死神-NotifyRelay", "[getAuthenticatedOnlineDevices] 返回结果: ${result.size} 个设备")
            return result
        } catch (e: Exception) {
            Logger.e("死神-NotifyRelay", "[getAuthenticatedOnlineDevices] 出错: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * 公开API：移除已认证设备（线程安全）。
     * - 取消与该设备相关的心跳任务
     * - 直接从数据库中删除设备
     * - 从内存中移除设备信息
     * - 触发 updateDeviceList() 以通知观察者
     * 返回 true 表示存在并已移除，false 表示没有该uuid
     */
    fun removeAuthenticatedDevice(uuid: String, deleteHistory: Boolean = false): Boolean {
        try {
            var existed = false
            
            // 取消心跳任务
            try {
                heartbeatJobs[uuid]?.cancel()
                heartbeatJobs.remove(uuid)
            } catch (_: Exception) {}
            
            // 从已建立心跳集合移除
            try { heartbeatedDevices.remove(uuid) } catch (_: Exception) {}

            // 从协议不兼容设备集合移除
            try { incompatibleDevicesInternal.remove(uuid) } catch (_: Exception) {}

            // 从 Rust 上下文移除设备密钥
            try { rustContext?.let { NativeCore.removeDevice(it, uuid) } } catch (_: Exception) {}

            synchronized(authenticatedDevices) {
                if (authenticatedDevices.containsKey(uuid)) {
                    // 直接从数据库中删除设备及关联数据
                    coroutineScope.launch {
                        val repository = DatabaseRepository.getInstance(context)
                        repository.deleteDeviceByUuid(uuid)
                        if (deleteHistory) {
                            repository.deleteNotificationsByDevice(uuid)
                            repository.deleteAppDeviceAssociationsByDeviceUuid(uuid)
                        }
                    }
                    
                    // 从内存中移除
                    authenticatedDevices.remove(uuid)
                    existed = true
                }
            }

            // 清理 deviceLastSeen
            try { synchronized(deviceLastSeen) { deviceLastSeen.remove(uuid) } } catch (_: Exception) {}
            
            // 清理 deviceInfoCache
            try {
                synchronized(deviceInfoCache) {
                    deviceInfoCache.remove(uuid)
                }
            } catch (_: Exception) {}

            // 触发更新，确保 StateFlow 与回调被通知
            try { 
                coroutineScope.launch { 
                    updateDeviceList() 
                    // 更新Flow值（在同步块内读取，确保一致性）
                    val authMap = synchronized(authenticatedDevices) { authenticatedDevices.toMap() }
                    val rejectedSet = synchronized(rejectedDevices) { rejectedDevices.toSet() }
                    _authenticatedDevicesFlow.value = authMap
                    _rejectedDevicesFlow.value = rejectedSet
                } 
            } catch (_: Exception) {}
            return existed
        } catch (e: Exception) {
            Logger.w("死神-NotifyRelay", "removeAuthenticatedDevice failed: ${e.message}")
            return false
        }
    }

    // 发送超级岛ACK（包含接收的hash），用于发送方确认
    private fun sendSuperIslandAck(remoteUuid: String?, hash: String, featureKeyValue: String?, mappedPkg: String?) {
        try {
            if (remoteUuid.isNullOrEmpty()) return
            val device = getDeviceInfo(remoteUuid)
            val auth = synchronized(authenticatedDevices) { authenticatedDevices[remoteUuid] }
            val ip = device?.ip ?: auth?.lastIp
            val port = device?.port ?: (auth?.lastPort ?: 23333)
            if (ip.isNullOrEmpty() || ip == "0.0.0.0") return

            // 使用DATA_STATUS发送超级岛ack
            val raw = org.json.JSONObject().apply {
                put("originalHeader", "DATA_SUPERISLAND")
                put("result", "success")
                put("action", "SI_ACK")
                put("packageName", mappedPkg ?: "superisland:ack")
                put("hash", hash)
                if (!featureKeyValue.isNullOrEmpty()) {
                    put("featureKeyName", SuperIslandProtocol.FEATURE_KEY_NAME)
                    put("featureKeyValue", featureKeyValue)
                }
                put("time", System.currentTimeMillis())
            }.toString()
            // 通过统一加密发送器发回对端
            val deviceInfo = DeviceInfo(remoteUuid, DeviceConnectionManagerUtil.getDisplayNameByUuid(remoteUuid), ip, port)
            ProtocolSender.sendEncrypted(this, deviceInfo, "DATA_STATUS", raw, 3000L)
        } catch (_: Exception) {
        }
    }

    // 提供给 NotificationProcessor 的内部包装，简化 ACK 调用
    internal fun sendSuperIslandAckInternal(
        remoteUuid: String,
        recvHash: String,
        featureId: String,
        mappedPkg: String?
    ) {
        try {
            sendSuperIslandAck(remoteUuid, recvHash, featureId, mappedPkg)
        } catch (_: Exception) {
        }
    }

    // 新增：WLAN直连定期重连检查器
    private fun startWifiDirectReconnectionChecker() {
        keepAlive.startWifiDirectReconnectionChecker()
    }
}
