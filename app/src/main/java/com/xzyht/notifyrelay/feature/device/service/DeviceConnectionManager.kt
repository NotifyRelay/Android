package com.xzyht.notifyrelay.feature.device.service

import android.os.Build
import android.os.Environment
import com.sun.jna.Pointer
import com.xzyht.notifyrelay.nativecore.NativeCore
import com.xzyht.notifyrelay.nativecore.NotifyRelayCore
import com.xzyht.notifyrelay.feature.audio.AudioRelayPlayer
import com.xzyht.notifyrelay.feature.notification.backend.BackendRemoteFilter
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
import com.xzyht.notifyrelay.sync.ftpServer
import com.xzyht.notifyrelay.sync.ftpServer.StartResult
import com.xzyht.notifyrelay.sync.notification.NotificationProcessor
import com.xzyht.notifyrelay.sync.notification.StatusProcessor
import com.xzyht.notifyrelay.sync.notification.SuperIslandProcessor
import com.xzyht.notifyrelay.ui.activity.GuideActivity
import github.xzynine.superislandui.common.SuperIslandProtocol
import io.github.miuzarte.scrcpyforandroid.services.AudioForwardingService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
        // 收集需要回填 sharedSecret 的设备
        val backfillUpdates = mutableListOf<DeviceEntity>()
        
        for (device in devices) {
            if (device.uuid == "本机") continue
            
            if (device.sharedSecret.isNotEmpty()) {
                // 尝试解析为新式 base64 AES 密钥
                val keyBytes = try {
                    android.util.Base64.decode(device.sharedSecret, android.util.Base64.NO_WRAP)
                } catch (_: Exception) { null }
                
                if (keyBytes != null && keyBytes.size == 32) {
                    // 新式密钥：导入 Rust 上下文
                    if (ctx != null && !NativeCore.migrateSharedSecret(ctx, device.uuid, keyBytes)) {
                        Logger.w("死神-NotifyRelay", "迁移密钥失败，跳过设备: ${device.uuid}")
                        continue
                    }
                } else {
                    // 旧版明文密钥（C#/Kotlin ECDH），与 Rust HKDF 不兼容，清除配对
                    hasOldKey = true
                    try {
                        ctx?.let { NativeCore.removeDevice(it, device.uuid) }
                    } catch (_: Exception) {}
                    kotlinx.coroutines.runBlocking {
                        DatabaseRepository.getInstance(context).saveDevice(
                            device.copy(sharedSecret = "", isAccepted = false)
                        )
                    }
                    continue  // 跳过认证状态恢复
                }
            }
            
            if (device.isAccepted) {
                synchronized(authenticatedDevices) {
                    authenticatedDevices[device.uuid] = AuthInfo(
                        publicKey = device.publicKey,
                        sharedSecret = device.sharedSecret,
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
        
        // 回填：对 sharedSecret 为空但 Rust 中有密钥的设备，补充持久化
        if (ctx != null) {
            synchronized(authenticatedDevices) {
                for ((uuid, auth) in authenticatedDevices) {
                    if (uuid == "本机") continue
                    if (auth.isAccepted && auth.sharedSecret.isEmpty()) {
                        val keyJson = NativeCore.exportDeviceKey(ctx, uuid)
                        if (keyJson != null) {
                            val json = org.json.JSONObject(keyJson)
                            val aesKey = json.optString("aes_key_b64", "")
                            authenticatedDevices[uuid] = auth.copy(sharedSecret = aesKey)
                            backfillUpdates.add(
                                DeviceEntity(
                                    uuid = uuid,
                                    publicKey = auth.publicKey,
                                    sharedSecret = aesKey,
                                    isAccepted = true,
                                    displayName = auth.displayName ?: "",
                                    lastIp = auth.lastIp ?: "",
                                    lastPort = auth.lastPort ?: 23333
                                )
                            )
                        }
                    }
                }
            }
            if (backfillUpdates.isNotEmpty()) {
                kotlinx.coroutines.runBlocking {
                    val repo = DatabaseRepository.getInstance(context)
                    backfillUpdates.forEach { repo.saveDevice(it) }
                }
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
            saveRustCoreState()
            Logger.i("死神-NotifyRelay", "检测到旧版密钥，已清除配对，请重新配对设备")
        }
    }

    // 保存已认证设备
    private fun saveAuthedDevices() {
        try {
            // 保存到Room数据库
            val deviceEntities = mutableListOf<DeviceEntity>()
            val ctx = rustContext
            for ((uuid, auth) in authenticatedDevices) {
                // 过滤掉uuid为"本机"的记录
                if (uuid == "本机") continue
                
                if (auth.isAccepted) {
                    // 从 Rust 导出实际 AES 密钥（base64）作为持久化 fallback
                    val keyB64 = ctx?.let { NativeCore.exportDeviceKey(it, uuid) }
                        ?.let { org.json.JSONObject(it).optString("aes_key_b64", "") }
                        ?: auth.sharedSecret
                    if (keyB64.isBlank()) {
                        Logger.w("死神-NotifyRelay", "跳过无可用密钥的设备持久化: $uuid")
                        continue
                    }
                    val name = auth.displayName ?: deviceInfoCache[uuid]?.displayName ?: DeviceConnectionManagerUtil.getDisplayNameByUuid(uuid)
                    val info = deviceInfoCache[uuid]
                    val deviceEntity = DeviceEntity(
                        uuid = uuid,
                        publicKey = auth.publicKey,
                        sharedSecret = keyB64,
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
    // 挂起握手结果（用于 connectToDevice 等待远端响应）
    private val pendingHandshakeResults = mutableMapOf<String, CompletableDeferred<Boolean>>()
    // 本地 ECDH 公钥（Base64 编码的 65 字节未压缩点）
    internal val localPublicKey: String
    // localPrivateKey 不再使用，ECDH 私钥在 Android Keystore 中，不可直接获取
    @Deprecated("ECDH 私钥在 Keystore 中，不再使用")
    private val localPrivateKey: String = ""
    internal val listenPort: Int = 23333
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val keepAlive = ConnectionKeepAlive(this, coroutineScope)
    private val discoveryManager = ConnectionDiscoveryManager(this, coroutineScope)

    // === 以下为提供给内部组件使用的访问器（保持字段本身 private） ===
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

    /** 注册等待握手结果 */
    fun registerHandshakeWaiter(uuid: String): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        synchronized(pendingHandshakeResults) {
            pendingHandshakeResults[uuid]?.cancel()
            pendingHandshakeResults[uuid] = deferred
        }
        return deferred
    }

    /** 解析挂起的握手结果 */
    fun resolveHandshake(uuid: String, success: Boolean) {
        synchronized(pendingHandshakeResults) {
            pendingHandshakeResults.remove(uuid)?.complete(success)
        }
    }

    /** 按 Deferred 实例清理等待器，防止迟到请求完成或移除其他等待器 */
    fun cancelHandshakeWaiter(uuid: String, deferred: CompletableDeferred<Boolean>) {
        synchronized(pendingHandshakeResults) {
            if (pendingHandshakeResults[uuid] === deferred) {
                pendingHandshakeResults.remove(uuid)
                deferred.cancel()
            }
        }
    }

    internal val incompatibleDevicesInternal: MutableSet<String> =
        java.util.Collections.synchronizedSet(mutableSetOf())

    internal val coroutineScopeInternal: CoroutineScope
        get() = coroutineScope

    private val heartbeatedDevices = mutableSetOf<String>()

    internal val heartbeatedDevicesInternal: MutableSet<String>
        get() = heartbeatedDevices

    internal val heartbeatJobsInternal: MutableMap<String, Long>
        get() = heartbeatJobs

    internal val contextInternal: android.content.Context
        get() = context

    internal val keepAliveInternal: ConnectionKeepAlive
        get() = keepAlive

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


    internal fun getDeviceInfoInternal(uuid: String): DeviceInfo? = getDeviceInfo(uuid)

    // Rust 原生上下文
    private var rustContext: com.sun.jna.Pointer? = null
    internal val rustContextInternal: com.sun.jna.Pointer?
        get() = rustContext

    private val deviceLastSeen = mutableMapOf<String, Long>()
    // 心跳定时任务
    private val heartbeatJobs = mutableMapOf<String, Long>()
    private var serverStarted = false
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
            BackendRemoteFilter.rustContext = rustContext
            NativeCore.setContext(rustContext)
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
        saveRustCoreState()
        // 尽早初始化发送队列等新特性，避免发送窗口期
        rustContext?.let { NativeCore.initializeNewFeatures(it) }
        // 新增：初始补全本机 deviceInfoCache，便于反向 connectToDevice
        val displayName = getLocalDisplayName()
        val localIp = discoveryManager.getLocalIpAddressInternal()
        synchronized(deviceInfoCache) {
            deviceInfoCache[uuid] = DeviceInfo(uuid, displayName, localIp, listenPort)
        }
        startOfflineDeviceCleaner()
        discoveryManager.registerNetworkCallback()
        startWifiDirectReconnectionChecker()
        // 启动 mDNS 广告和发现
        startMdnsServices()
    }

    private fun startMdnsServices() {
        try {
            val ctx = rustContext ?: return
            val displayName = getLocalDisplayName()
            NativeCore.startMdnsAdvertiser(ctx, uuid, displayName, listenPort.toShort(), localPublicKey, "android")
            NativeCore.startMdnsDiscovery(ctx)
            Logger.d("死神-NotifyRelay", "mDNS 服务已启动")
        } catch (e: Exception) {
            Logger.e("死神-NotifyRelay", "启动 mDNS 服务失败", e)
        }
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
                val info = deviceInfo ?: let {
                    Logger.w("死神-NotifyRelay", "updateDeviceList: 设备 $uuid 无缓存信息, auth.lastIp=${auth.lastIp}")
                    DeviceInfo(uuid, auth.displayName ?: "已认证设备", auth.lastIp.orEmpty(), listenPort)
                }
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
        synchronized(deviceInfoCache) {
            deviceInfoCache[uuid]?.takeUnless { it.ip == "0.0.0.0" || it.ip.isBlank() }
                ?.let { return it }
        }
        _devices.value[uuid]?.first?.takeUnless { it.ip == "0.0.0.0" || it.ip.isBlank() }
            ?.let { return it }
        val auth = synchronized(authenticatedDevices) { authenticatedDevices[uuid] }
        if (auth != null) {
            val name = auth.displayName ?: DeviceConnectionManagerUtil.getDisplayNameByUuid(uuid)
            val ip = auth.lastIp?.takeUnless { it == "0.0.0.0" || it.isBlank() } ?: ""
            val port = auth.lastPort ?: listenPort
            return DeviceInfo(uuid, name, ip, port)
        }
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
            if (ctx != null && !NativeCore.deriveSharedSecret(ctx, remoteUuid, remotePubKey)) {
                Logger.e("死神-NotifyRelay", "客户端配对密钥派生失败: $remoteUuid")
                return
            }
            synchronized(authenticatedDevices) {
                authenticatedDevices[remoteUuid] = AuthInfo(
                    remotePubKey, "", true, "未知设备"
                )
                saveAuthedDevices()
            }
            saveRustCoreState()
            updateDeviceList()
            Logger.d("死神-NotifyRelay", "客户端配对完成: $remoteUuid")
        } catch (e: Exception) {
            Logger.e("死神-NotifyRelay", "客户端配对失败: $remoteUuid", e)
        }
    }

    data class PendingPairing(
        val remoteUuid: String,
        val remotePubKey: String,
        val remoteIp: String
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
     * 供回调解密接收端回传的配对码。
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
            if (ctx != null && !NativeCore.deriveSharedSecret(ctx, uuid, remoteLtPubKey)) {
                Logger.e("死神-NotifyRelay", "长期密钥派生失败: $uuid")
                return false
            }
            synchronized(authenticatedDevices) {
                authenticatedDevices[uuid] = AuthInfo(
                    remoteLtPubKey, "", true, displayName,
                    lastIp = lastIp
                )
                saveAuthedDevices()
            }
            saveRustCoreState()
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
        val keyJson = NativeCore.exportDeviceKey(ctx, uuid)
        val keyB64 = keyJson?.let { org.json.JSONObject(it).optString("aes_key_b64", "") }
        if (keyB64 == null || keyB64.isEmpty()) {
            Logger.e("死神-NotifyRelay", "encryptData: 设备密钥不在Rust中 uuid=$uuid，尝试重新迁移")
            val auth = synchronized(authenticatedDevices) { authenticatedDevices[uuid] }
            if (auth != null && auth.sharedSecret.isNotEmpty()) {
                val keyBytes = try { android.util.Base64.decode(auth.sharedSecret, android.util.Base64.NO_WRAP) } catch (_: Exception) { null }
                if (keyBytes != null && keyBytes.size == 32) {
                    NativeCore.migrateSharedSecret(ctx, uuid, keyBytes)
                }
            }
        }
        return NativeCore.encryptMessage(ctx, header, this.uuid, this.localPublicKey, uuid, input)
            ?: throw IllegalStateException("Rust加密失败: encryptMessage, device=$uuid")
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

    // 注册 Rust 回调，使用统一的配对和数据回调接口
    private fun setupRustCallbacks() {
        val ctx = rustContext ?: return
        val lib = NotifyRelayCore.instance()
        fun ptr2str(ptr: Pointer?) = NotifyRelayCore.ptrToString(ptr)

        // ---- on_pairing (统一配对回调) ----
        val pairingCb = object : NotifyRelayCore.OnPairingCb {
            override fun invoke(uuidPtr: Pointer?, msgTypePtr: Pointer?, dataPtr: Pointer?, intValue: Int, extraPtr: Pointer?, userData: Pointer?) {
                val uuid = ptr2str(uuidPtr) ?: return
                val msgType = ptr2str(msgTypePtr) ?: return
                val data = ptr2str(dataPtr)
                val extra = ptr2str(extraPtr)

                try {
                    when (msgType) {
                        "HANDSHAKE" -> {
                            val dm = _callbackInstance ?: return
                            var pubKey = ""
                            var ip = ""
                            var deviceType = "unknown"
                            data?.let {
                                try {
                                    val json = JSONObject(it)
                                    pubKey = json.optString("pub_key", "")
                                    ip = json.optString("ip", "")
                                    deviceType = json.optString("device_type", "unknown")
                                } catch (_: Exception) {}
                            }
                            synchronized(dm.deviceInfoCacheInternal) {
                                val old = dm.deviceInfoCacheInternal[uuid]
                                val displayName = old?.displayName ?: "未知设备"
                                dm.deviceInfoCacheInternal[uuid] = DeviceInfo(uuid, displayName, ip, old?.port ?: 23333)
                            }
                            synchronized(dm.authenticatedDevices) {
                                val auth = dm.authenticatedDevices[uuid]
                                if (auth != null) {
                                    dm.authenticatedDevices[uuid] = auth.copy(lastIp = ip)
                                    dm.saveAuthedDevicesInternal()
                                }
                            }
                            val alreadyAuthed = synchronized(dm.authenticatedDevices) {
                                dm.authenticatedDevices[uuid]?.isAccepted == true
                            }
                            if (alreadyAuthed) {
                                synchronized(dm.authenticatedDevices) {
                                    val existingAuth = dm.authenticatedDevices[uuid]
                                    if (existingAuth != null && existingAuth.publicKey != pubKey) {
                                        val rejectCtx = dm.rustContextInternal!!
                                        val rejectUuid = dm.uuid
                                        dm.coroutineScopeInternal.launch { NativeCore.sendReject(rejectCtx, rejectUuid) }
                                        Logger.w("CoreCb", "已认证设备公钥变化，要求重新配对: $uuid")
                                        return
                                    }
                                }
                                synchronized(dm.incompatibleDevicesInternal) { dm.incompatibleDevicesInternal.remove(uuid) }
                                val localIp = getLocalIpAddress()
                                val acceptCtx = dm.rustContextInternal!!
                                val acceptUuid = dm.uuid
                                val acceptPub = dm.localPublicKey
                                val battery = BatteryUtils.getBatteryLevel(dm.contextInternal)
                                dm.coroutineScopeInternal.launch {
                                    NativeCore.sendAccept(acceptCtx, acceptUuid, acceptPub, localIp, battery, deviceType)
                                }
                                synchronized(dm.authenticatedDevices) {
                                    val auth = dm.authenticatedDevices[uuid]
                                    if (auth != null) {
                                        dm.authenticatedDevices[uuid] = auth.copy(deviceType = deviceType, lastIp = ip)
                                        dm.saveAuthedDevicesInternal()
                                    }
                                }
                            } else {
                                val rejectCtx = dm.rustContextInternal!!
                                val rejectUuid = dm.uuid
                                dm.coroutineScopeInternal.launch { NativeCore.sendReject(rejectCtx, rejectUuid) }
                            }
                        }
                        "PAIRING_INIT" -> {
                            val dm = _callbackInstance ?: return
                            var spake2Pub = ""
                            var ip = ""
                            data?.let {
                                try {
                                    val json = JSONObject(it)
                                    spake2Pub = json.optString("spake2_pub", "")
                                    ip = json.optString("ip", "")
                                } catch (_: Exception) {}
                            }
                            synchronized(dm.rejectedDevicesInternal) {
                                if (dm.rejectedDevicesInternal.contains(uuid)) {
                                    val rejectCtx = dm.rustContextInternal!!
                                    val rejectUuid = dm.uuid
                                    dm.coroutineScopeInternal.launch { NativeCore.sendReject(rejectCtx, rejectUuid) }
                                    return
                                }
                            }
                            val displayName: String
                            synchronized(dm.deviceInfoCacheInternal) {
                                displayName = dm.deviceInfoCacheInternal[uuid]?.displayName ?: "未知设备"
                                dm.deviceInfoCacheInternal[uuid] = DeviceInfo(uuid, displayName, ip, 23333)
                            }
                            dm.pendingPairing = PendingPairing(remoteUuid = uuid, remotePubKey = spake2Pub, remoteIp = ip)
                            val remoteDevice = DeviceInfo(uuid, displayName, ip, 23333)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                dm.handshakeRequestHandler?.onPairingInitRequest(remoteDevice, spake2Pub)
                            }
                            Logger.d("CoreCb", "PAIRING_INIT 已处理: $uuid")
                        }
                        "PAIRING_RESP" -> {
                            Logger.w("CoreCb", "收到意外的 PAIRING_RESP: $uuid")
                        }
                        "ACCEPT" -> {
                            val dm = _callbackInstance ?: return
                            var ltPubKey = ""
                            var ip = ""
                            data?.let {
                                try {
                                    val json = JSONObject(it)
                                    ltPubKey = json.optString("lt_pub_key", "")
                                    ip = json.optString("ip", "")
                                } catch (_: Exception) {}
                            }
                            val ok = dm.completePairingWithLongTermKeys(uuid, ltPubKey, lastIp = ip)
                            dm.resolveHandshake(uuid, ok)
                            if (ok) {
                                dm.keepAliveInternal.startHeartbeatToDevice(uuid, ip, 23333, "")
                            }
                            Logger.d("CoreCb", "ACCEPT 已处理: $uuid")
                        }
                        "REJECT" -> {
                            val dm = _callbackInstance ?: return
                            dm.resolveHandshake(uuid, false)
                            synchronized(dm.rejectedDevicesInternal) {
                                dm.rejectedDevicesInternal.add(uuid)
                            }
                            Logger.w("CoreCb", "REJECT 已处理: $uuid")
                        }
                        "RESULT" -> {
                            val err = extra ?: ""
                            if (intValue == 0) {
                                Logger.w("CoreCb", "配对失败: $uuid, error=$err")
                                _callbackInstance?.resolveHandshake(uuid, false)
                            } else {
                                Logger.d("CoreCb", "配对成功: $uuid")
                                val dm = _callbackInstance ?: return
                                val keyJson = dm.rustContext?.let { NativeCore.exportDeviceKey(it, uuid) }
                                if (keyJson != null) {
                                    val json = org.json.JSONObject(keyJson)
                                    val ltPub = json.optString("remote_pub_key", "")
                                    if (ltPub.isNotEmpty()) {
                                        dm.completePairingWithLongTermKeys(uuid, ltPub)
                                    }
                                }
                                dm.resolveHandshake(uuid, true)
                            }
                        }
                        "HEARTBEAT_TCP" -> {
                            val dm = _callbackInstance ?: return
                            val remoteName = extra ?: return
                            var deviceType = "unknown"
                            var ip = ""
                            data?.let {
                                try {
                                    val json = JSONObject(it)
                                    deviceType = json.optString("device_type", "unknown")
                                    ip = json.optString("ip", "")
                                } catch (_: Exception) {}
                            }
                            val info = HeartbeatProcessor.HeartbeatInfo(
                                uuid = uuid,
                                displayName = remoteName,
                                port = 23333,
                                batteryLevel = kotlin.math.abs(intValue),
                                isCharging = intValue >= 0,
                                deviceType = deviceType,
                                ip = ip
                            )
                            if (info.uuid != dm.uuid) {
                                HeartbeatProcessor.processHeartbeat(info, dm)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.e("CoreCb", "on_pairing error: ${e.message}")
                }
            }
        }
        lib.nrc_set_on_pairing_cb(ctx, pairingCb); rustCallbackRefs.add(pairingCb)

        // ---- on_data (统一数据回调) ----
        val dataCb = object : NotifyRelayCore.OnDataCb {
            override fun invoke(uuidPtr: Pointer?, msgTypePtr: Pointer?, plaintextPtr: Pointer?, userData: Pointer?) {
                val uuid = ptr2str(uuidPtr) ?: return
                val msgType = ptr2str(msgTypePtr) ?: return
                val text = ptr2str(plaintextPtr) ?: return
                val authed = synchronized(authenticatedDevices) {
                    authenticatedDevices[uuid]?.isAccepted == true
                }
                android.util.Log.d("CoreCb", "on_data: type=$msgType, uuid=$uuid, authed=$authed, text_len=${text.length}")
                if (!authed && msgType != "DATA_UNKNOWN") return

                try {
                    when (msgType) {
                        "NOTIFICATION" -> {
                            NotificationProcessor.process(context, this@DeviceConnectionManager, coroutineScope,
                                NotificationProcessor.NotificationInput("DATA_NOTIFICATION", text, uuid),
                                notificationDataReceivedCallbacksInternal)
                        }
                        "MEDIAPLAY" -> {
                            val json = JSONObject(text)
                            resolveDeviceInfo(uuid, "", 23333)?.let {
                                RemoteMediaSessionManager.onMediaMessageReceived(context, json, it)
                            }
                        }
                        "ICON_REQUEST" -> {
                            resolveDeviceInfo(uuid, "", 23333)?.let {
                                IconSyncManager.handleIconRequest(text, this@DeviceConnectionManager, it, context)
                            }
                        }
                        "ICON_RESPONSE" -> {
                            IconSyncManager.handleIconResponse(text, context)
                        }
                        "APP_LIST_REQUEST" -> {
                            resolveDeviceInfo(uuid, "", 23333)?.let {
                                AppListSyncManager.handleAppListRequest(text, this@DeviceConnectionManager, it, context)
                            }
                        }
                        "APP_LIST_RESPONSE" -> {
                            AppListSyncManager.handleAppListResponse(text, context, uuid, this@DeviceConnectionManager)
                        }
                        "MEDIA_CONTROL" -> {
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
                                    val relayMode = StorageManager.getInt(context, "audio_relay_mode", 0)
                                    if (relayMode == 1) {
                                        // 中继模式：Rust 内部自动发控制消息
                                        val device = resolveDeviceInfo(uuid, "", 23333)
                                        device?.let {
                                            audioRelayPlayer.start("send", deviceIp = it.ip, remoteUuid = uuid)
                                        }
                                    } else {
                                        // scrcpy 模式：现有逻辑
                                        val device = resolveDeviceInfo(uuid, "", 23333)
                                        val ok = device?.let { AudioForwardingService.startAudioForwarding(context, it.ip, ScrcpyDefaults.ADB_PORT, it.displayName) } == true
                                        val result = if (ok) "accepted" else "rejected"
                                        val raw = "{\"type\":\"MEDIA_CONTROL\",\"action\":\"audioResponse\",\"result\":\"$result\"}"
                                        device?.let { ProtocolSender.sendEncrypted(this@DeviceConnectionManager, it, "DATA_MEDIA_CONTROL", raw) }
                                    }
                                }
                                "audioResponse" -> {
                                    if (json.optString("result", "rejected") != "accepted") {
                                        coroutineScope.launch { notifyrelay.base.util.ToastUtils.showShortToast(context, "音频转发请求被拒绝") }
                                    }
                                }
                                "audioStart" -> {
                                    coroutineScope.launch {
                                        val sr = json.optInt("sampleRate", 48000)
                                        val ch = json.optInt("channels", 2)
                                        val device = resolveDeviceInfo(uuid, "", 23333)
                                        val ip = device?.ip ?: ""
                                        audioRelayPlayer.start("recv", sr, ch, deviceIp = ip, remoteUuid = uuid)
                                    }
                                }
                                "audioStop" -> {
                                    coroutineScope.launch {
                                        audioRelayPlayer.stop()
                                    }
                                }
                            }
                        }
                        "FTP" -> {
                            val isPc = synchronized(authenticatedDevices) {
                                authenticatedDevices[uuid]?.deviceType?.lowercase() == "pc"
                            }
                            if (!isPc) return
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
                        "CLIPBOARD" -> {
                            ClipboardProcessor.process(context, ClipboardProcessor.ClipboardInput("DATA_CLIPBOARD", text, ""))
                        }
                        "STATUS" -> {
                            StatusProcessor.process(context, this@DeviceConnectionManager, coroutineScope,
                                StatusProcessor.StatusInput("DATA_STATUS", text, uuid),
                                notificationDataReceivedCallbacksInternal)
                        }
                        "APP_LAUNCH" -> {
                            resolveDeviceInfo(uuid, "", 23333)?.let {
                                AppLaunchManager.handleAppLaunchRequest(text, this@DeviceConnectionManager, it, context)
                            }
                        }
                        "SUPERISLAND" -> {
                            SuperIslandProcessor.process(context, this@DeviceConnectionManager, text, uuid)
                        }
                        else -> {
                            Logger.d("CoreCb", "未知DATA通道: type=$msgType, uuid=$uuid, size=${text.length}")
                        }
                    }
                } catch (e: Exception) {
                    Logger.e("CoreCb", "on_data error: ${e.message}")
                }
            }
        }
        lib.nrc_set_on_data_cb(ctx, dataCb); rustCallbackRefs.add(dataCb)

        // ---- on_heartbeat_udp ----
        val heartbeatUdpCb = object : NotifyRelayCore.OnHeartbeatUdpCb {
            override fun invoke(uuid: Pointer?, name: Pointer?, port: Short, battery: Int, deviceType: Pointer?, ip: Pointer?, userData: Pointer?) {
                val dm = _callbackInstance ?: return
                val remoteUuid = ptr2str(uuid) ?: return
                val remoteName = ptr2str(name) ?: return
                val remoteDeviceType = ptr2str(deviceType) ?: "unknown"
                val srcIp = ptr2str(ip)
                val resolvedIp = if (srcIp.isNullOrBlank() || srcIp == "0.0.0.0") "0.0.0.0" else srcIp
                try {
                    val info = HeartbeatProcessor.HeartbeatInfo(
                        uuid = remoteUuid, displayName = remoteName,
                        port = port.toInt(), batteryLevel = kotlin.math.abs(battery),
                        isCharging = battery >= 0, deviceType = remoteDeviceType, ip = resolvedIp
                    )
                    if (info.uuid != dm.uuid) {
                        HeartbeatProcessor.processHeartbeat(info, dm)
                    }
                } catch (e: Exception) {
                    Logger.e("CoreCb", "on_heartbeat_udp error", e)
                }
            }
        }
        lib.nrc_set_on_heartbeat_udp_cb(ctx, heartbeatUdpCb); rustCallbackRefs.add(heartbeatUdpCb)

        // ---- on_mdns_discovered ----
        val mdnsDiscoveredCb = object : NotifyRelayCore.OnMdnsDiscoveredCb {
            override fun invoke(uuid: Pointer?, name: Pointer?, ip: Pointer?, port: Short, deviceType: Pointer?, userData: Pointer?) {
                val dm = _callbackInstance ?: return
                val remoteUuid = ptr2str(uuid) ?: return
                val remoteName = ptr2str(name) ?: return
                val remoteIp = ptr2str(ip) ?: "0.0.0.0"
                val remoteDeviceType = ptr2str(deviceType) ?: "unknown"
                try {
                    val info = HeartbeatProcessor.HeartbeatInfo(
                        uuid = remoteUuid, displayName = remoteName,
                        port = port.toInt(), batteryLevel = -1,
                        isCharging = false, deviceType = remoteDeviceType, ip = remoteIp
                    )
                    if (info.uuid != dm.uuid) {
                        HeartbeatProcessor.processHeartbeat(info, dm)
                    }
                } catch (e: Exception) {
                    Logger.e("CoreCb", "on_mdns_discovered error", e)
                }
            }
        }
        lib.nrc_set_on_mdns_discovered_cb(ctx, mdnsDiscoveredCb); rustCallbackRefs.add(mdnsDiscoveredCb)

        // ---- on_device_timeout (设备心跳超时回调) ----
        val deviceTimeoutCb = object : NotifyRelayCore.OnDeviceTimeoutCb {
            override fun invoke(uuidPtr: Pointer?, userData: Pointer?) {
                val uuid = NotifyRelayCore.ptrToString(uuidPtr) ?: return
                synchronized(deviceLastSeen) {
                    deviceLastSeen[uuid] = System.currentTimeMillis() - 30_000L
                }
            }
        }
        lib.nrc_set_on_device_timeout_cb(ctx, deviceTimeoutCb); rustCallbackRefs.add(deviceTimeoutCb)

        // ---- 日志回调（接入 Logger.CURRENT_LEVEL 等级控制） ----
        NativeCore.setLogCallback(ctx)
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

    // 启动TCP服务监听，使用 Rust TCP 服务器
    private fun startServer() {
        coroutineScope.launch {
            try {
                val ctx = rustContext ?: return@launch
                if (!serverStarted) {
                    val result = NativeCore.startTcpServer(ctx, listenPort.toShort())
                    if (result == 0) {
                        Logger.i("死神-NotifyRelay", "Rust TCP 服务器已启动，端口: $listenPort")
                        serverStarted = true
                    } else {
                        Logger.e("死神-NotifyRelay", "启动 Rust TCP 服务器失败")
                    }
                }
                // 无论 TCP 是否已启动，总是初始化新网络特性（发送队列、离线检测、重连状态机）
                NativeCore.initializeNewFeatures(ctx)
            } catch (e: Exception) {
                Logger.e("死神-NotifyRelay", "启动 Rust TCP 服务器异常", e)
            }
        }
    }

    // 保存 Rust Core 状态到持久化存储，确保重启后 device_keys 可恢复
    internal fun saveRustCoreState() {
        try {
            val ctx = rustContext ?: return
            val stateJson = NativeCore.exportState(ctx) ?: return
            val encrypted = NativeCore.encryptLocalState(ctx, stateJson, uuid) ?: return
            StorageManager.putString(context, "rust_core_state", encrypted)
        } catch (e: Exception) {
            Logger.e("死神-NotifyRelay", "保存 Rust Core 状态失败", e)
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

    // 获取本机 IP 地址
    private fun getLocalIpAddress(): String {
        return NativeCore.getLocalIp() ?: "0.0.0.0"
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
                val handle = heartbeatJobs[uuid]
                if (handle != null && rustContext != null) {
                    NativeCore.stopHeartbeatSender(rustContext!!, handle)
                }
                heartbeatJobs.remove(uuid)
            } catch (_: Exception) {}
            
            // 从已建立心跳集合移除
            try { heartbeatedDevices.remove(uuid) } catch (_: Exception) {}

            // 从协议不兼容设备集合移除
            try { incompatibleDevicesInternal.remove(uuid) } catch (_: Exception) {}

            // 从 Rust 上下文移除设备密钥
            try {
                rustContext?.let { NativeCore.removeDevice(it, uuid) }
                saveRustCoreState()
            } catch (_: Exception) {}

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

    val audioRelayPlayer = AudioRelayPlayer()

    // 新增：WLAN直连定期重连检查器
    private fun startWifiDirectReconnectionChecker() {
        keepAlive.startWifiDirectReconnectionChecker()
    }
}
