package com.xzyht.notifyrelay.feature.device.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.util.Base64
import com.sun.jna.Pointer
import com.xzyht.notifyrelay.nativecore.NativeCore
import com.xzyht.notifyrelay.nativecore.NotifyRelayCore
import com.xzyht.notifyrelay.feature.audio.AudioRelayPlayer
import com.xzyht.notifyrelay.feature.notification.backend.BackendRemoteFilter
import com.xzyht.notifyrelay.feature.notification.superisland.RemoteMediaSessionManager
import com.xzyht.notifyrelay.servers.MediaControlUtil
import com.xzyht.notifyrelay.servers.MediaSessionMonitorService
import com.xzyht.notifyrelay.servers.NotifyRelayNotificationListenerService
import com.xzyht.notifyrelay.servers.clipboard.ClipboardProcessor
import com.xzyht.notifyrelay.sync.AppLaunchManager
import com.xzyht.notifyrelay.sync.AppListSyncManager
import com.xzyht.notifyrelay.sync.ConnectionDiscoveryManager
import com.xzyht.notifyrelay.sync.ConnectionKeepAlive
import com.xzyht.notifyrelay.sync.IconSyncManager
import com.xzyht.notifyrelay.sync.MessageSender
import com.xzyht.notifyrelay.sync.ProtocolSender
import com.xzyht.notifyrelay.sync.HeartbeatProcessor
import com.xzyht.notifyrelay.sync.ftpServer
import com.xzyht.notifyrelay.sync.ftpServer.StartResult
import com.xzyht.notifyrelay.sync.notification.NotificationProcessor
import com.xzyht.notifyrelay.sync.notification.StatusProcessor
import com.xzyht.notifyrelay.sync.notification.SuperIslandProcessor
import com.xzyht.notifyrelay.ui.activity.GuideActivity
import github.xzynine.superislandui.common.SuperIslandManager
import io.github.miuzarte.scrcpyforandroid.services.AudioForwardingService
import java.io.ByteArrayOutputStream
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
    companion object {
        private var stopReceiverRegistered = false

        // 静态引用，供 native 回调线程从 Rust 回调中调度到实例方法
        private var _callbackInstance: DeviceConnectionManager? = null
        internal fun getCallbackInstance(): DeviceConnectionManager? = _callbackInstance

        /**
         * 获取单例实例
         */
        fun getInstance(context: android.content.Context): DeviceConnectionManager {
            return DeviceConnectionManagerSingleton.getDeviceManager(context)
        }
    }

    private var audioRelayNotificationReceiver: BroadcastReceiver? = null
    var onRequestMediaProjection: (() -> Unit)? = null

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
            if (device.uuid == "本机" || device.uuid == this.uuid) continue
            
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
                registerReconnectTarget(device.uuid, device.lastIp ?: "")
                registerKnownDevice(device.uuid, device.lastIp ?: "")
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


    internal fun getDeviceInfoInternal(uuid: String): DeviceInfo? = getDeviceInfo(uuid)

    // Rust 原生上下文
    private var rustContext: com.sun.jna.Pointer? = null
    internal val rustContextInternal: com.sun.jna.Pointer?
        get() = rustContext

    private var serverStarted = false
    // 电池变化监听器（常驻，随 DeviceConnectionManager 生命周期）
    private var batteryReceiver: BroadcastReceiver? = null
    // 上次同步给 Rust 心跳调度器的带符号电量（正=充电，负=放电），用于防抖
    private var lastSentSignedBattery: Int = Int.MIN_VALUE
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
        // 启动统一心跳调度器：已配对设备的每设备心跳（Auto 模式）由 Rust 自动启停
        try {
            rustContext?.let { ctx ->
                val batteryLevel = BatteryUtils.getBatteryLevel(context)
                val battery = if (BatteryUtils.isCharging(context)) batteryLevel else -batteryLevel
                lastSentSignedBattery = battery
                NativeCore.startHeartbeatScheduler(ctx, uuid, getLocalDisplayName(), battery, "android", 2000L)
            }
        } catch (e: Exception) {
            Logger.e("死神-NotifyRelay", "启动心跳调度器失败", e)
        }
        // 电池变化监听：电量/充电状态变化时同步到 Rust 心跳调度器，对端实时显示更新
        registerBatteryChangeReceiver()
        // 新增：初始补全本机 deviceInfoCache，便于反向 connectToDevice
        val displayName = getLocalDisplayName()
        val localIp = discoveryManager.getLocalIpAddressInternal()
        synchronized(deviceInfoCache) {
            deviceInfoCache[uuid] = DeviceInfo(uuid, displayName, localIp, listenPort)
        }
        startOfflineDeviceCleaner()
        discoveryManager.registerNetworkCallback()
        // 自动重连已移交 Rust 重连状态机（loadAuthedDevices 中已登记认证设备）
        // 启动 mDNS 广告和发现
        startMdnsServices()
        // 启动 Rust 已知设备扫描器（持续对认证设备做握手重连/发现）
        try {
            rustContext?.let { NativeCore.startKnownDeviceScanner(it) }
        } catch (_: Exception) {}
    }

    // 电池变化监听：ACTION_BATTERY_CHANGED 为粘性广播，注册后立即回调一次当前状态；
    // 电量或充电状态变化时调用 Rust 调度器参数更新，使心跳报文携带实时电量（正=充电，负=放电）
    private fun registerBatteryChangeReceiver() {
        if (batteryReceiver != null) return
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
                try {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    if (level < 0 || scale <= 0) return
                    val batteryLevel = (level * 100 / scale).coerceIn(0, 100)
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    val signed = if (charging) batteryLevel else -batteryLevel
                    if (signed == lastSentSignedBattery) return
                    lastSentSignedBattery = signed
                    rustContext?.let {
                        NativeCore.updateHeartbeatSchedulerParams(it, getLocalDisplayName(), signed, "android")
                    }
                } catch (_: Exception) {}
            }
        }
        try {
            context.registerReceiver(
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                Context.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {}
    }

    private fun startMdnsServices() {
        try {
            val ctx = rustContext ?: return
            val displayName = getLocalDisplayName()
            NativeCore.startMdnsAdvertiser(ctx, uuid, displayName, listenPort.toShort(), localPublicKey, "android", lastSentSignedBattery)
            NativeCore.startMdnsDiscovery(ctx)
            Logger.d("死神-NotifyRelay", "mDNS 服务已启动")
        } catch (e: Exception) {
            Logger.e("死神-NotifyRelay", "启动 mDNS 服务失败", e)
        }
    }

    // 统一设备状态管理：1s 定时拉取 Rust 状态快照（在线判定/超时移除已迁移至 Rust DeviceRegistry）
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

    // 统一设备状态管理：在线判定/超时移除已迁移至 Rust DeviceRegistry，此处消费状态快照
    private fun updateDeviceList() {
        refreshDevicesFromRust()
    }

    /**
     * 从 Rust 拉取设备状态快照（uuid/ip/电量/在线/配对等），回填 deviceInfoCache，
     * 生成 _devices（未认证且不在线的设备过滤 = 显示策略），并刷新在线设备缓存。
     * 1s 定时器（startOfflineDeviceCleaner）与心跳回调共同触发。
     */
    private fun refreshDevicesFromRust() {
        val ctx = rustContext ?: return
        val json = try {
            NativeCore.getDeviceList(ctx, 12_000L, 5_000L)
        } catch (_: Exception) { null } ?: return
        try {
            val arr = org.json.JSONArray(json)
            val authSnapshot = synchronized(authenticatedDevices) { authenticatedDevices.toMap() }
            val newMap = mutableMapOf<String, Pair<DeviceInfo, Boolean>>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val uuid = obj.optString("uuid")
                if (uuid.isEmpty() || uuid == this.uuid) continue
                val online = obj.optBoolean("online")
                val ip = obj.optString("ip")
                val port = obj.optInt("port", listenPort).takeIf { it > 0 } ?: listenPort
                val battery = obj.optInt("battery", -101)
                val oldInfo = synchronized(deviceInfoCache) { deviceInfoCache[uuid] }
                // 未知电量（超出 [-100,100]）沿用缓存旧值，不覆盖已显示的电量/充电状态
                val batteryUnknown = kotlin.math.abs(battery) > 100
                val chargingStatus = if (batteryUnknown) (oldInfo?.chargingStatus ?: '0')
                else if (battery >= 0) '1' else '0'
                val deviceType = obj.optString("deviceType", "unknown")
                val isAuthed = authSnapshot[uuid]?.isAccepted == true
                val auth = authSnapshot[uuid]
                val displayName = obj.optString("name")
                    .ifEmpty { oldInfo?.displayName ?: auth?.displayName ?: DeviceConnectionManagerUtil.getDisplayNameByUuid(uuid) }
                // 回填缓存（名称/类型以平台持久化与历史缓存为准，IP/电量来自快照）
                synchronized(deviceInfoCache) {
                    deviceInfoCache[uuid] = DeviceInfo(
                        uuid = uuid,
                        displayName = displayName,
                        ip = ip,
                        port = port,
                        batteryLevel = if (batteryUnknown) (oldInfo?.batteryLevel ?: -1) else kotlin.math.abs(battery),
                        chargingStatus = chargingStatus
                    )
                }
                // 显示策略：未认证且不在线的设备过滤
                if (!isAuthed && !online) continue
                synchronized(deviceInfoCache) {
                    deviceInfoCache[uuid]?.let { newMap[uuid] = it to online }
                }
                // 同步已认证设备的 lastIp/deviceType 元数据
                if (isAuthed && auth != null) {
                    val effectiveIp = ip.takeUnless { it == "0.0.0.0" || it.isBlank() }
                    val validType = deviceType.takeUnless { it.isBlank() || it == "unknown" }
                    val ipChanged = effectiveIp != null && auth.lastIp != effectiveIp
                    val typeChanged = validType != null && auth.deviceType != validType
                    if (ipChanged || typeChanged) {
                        synchronized(authenticatedDevices) {
                            authenticatedDevices[uuid] = auth.copy(lastIp = effectiveIp ?: auth.lastIp, deviceType = validType ?: auth.deviceType)
                        }
                        saveAuthedDevices()
                    }
                }
            }
            _devices.value = newMap
            updateOnlineDevicesCache(newMap, authSnapshot)
        } catch (_: Exception) {}
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
        rustContext?.let { NativeCore.clearPairingCode(it) }
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
            registerReconnectTarget(uuid, lastIp ?: "")
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
                            dm.registerReconnectTarget(uuid, ip)
            dm.registerKnownDevice(uuid, ip)
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
                                // 登记已知设备（uuid+ip），心跳由 Rust 调度器自动启动
                                dm.registerKnownDevice(uuid, ip)
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
                                        // 登记已知设备（uuid+ip），心跳由 Rust 调度器自动启动
                                        val cachedIp = synchronized(dm.deviceInfoCacheInternal) {
                                            dm.deviceInfoCacheInternal[uuid]?.ip
                                        }
                                        dm.registerKnownDevice(uuid, cachedIp ?: "")
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
                                            pendingAudioRelaySend = PendingAudioSend(it.ip, it.displayName, uuid)
                                            onRequestMediaProjection?.invoke()
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
                                        audioRelayPlayer.start("recv", sr, ch, remoteUuid = uuid)
                                        showAudioRelayNotification(device?.displayName ?: ip, uuid)
                                    }
                                }
                                "audioStop" -> {
                                    coroutineScope.launch {
                                        audioRelayPlayer.stop()
                                        cancelAudioRelayNotification()
                                        if (json.optString("result", "").isNotEmpty()) return@launch
                                        val device = resolveDeviceInfo(uuid, "", 23333)
                                        if (device != null) {
                                            val raw = "{\"type\":\"MEDIA_CONTROL\",\"action\":\"audioStop\",\"result\":\"ok\"}"
                                            try {
                                                ProtocolSender.sendEncrypted(this@DeviceConnectionManager, device, "DATA_MEDIA_CONTROL", raw)
                                            } catch (_: Exception) {}
                                        }
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
                        port = port.toInt(), batteryLevel = battery,
                        isCharging = battery > 0, deviceType = remoteDeviceType, ip = resolvedIp
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
            override fun invoke(uuid: Pointer?, name: Pointer?, ip: Pointer?, port: Short, battery: Int, deviceType: Pointer?, userData: Pointer?) {
                val dm = _callbackInstance ?: return
                val remoteUuid = ptr2str(uuid) ?: return
                val remoteName = ptr2str(name) ?: return
                val remoteIp = ptr2str(ip) ?: "0.0.0.0"
                val remoteDeviceType = ptr2str(deviceType) ?: "unknown"
                try {
                    // 广告 TXT 携带 signed 电量（正=充电，负=放电，-101=未知），充电状态由符号推断
                    val info = HeartbeatProcessor.HeartbeatInfo(
                        uuid = remoteUuid, displayName = remoteName,
                        port = port.toInt(), batteryLevel = battery,
                        isCharging = battery > 0, deviceType = remoteDeviceType, ip = remoteIp
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
                // 超时离线状态由 Rust DeviceRegistry 维护，此处仅重新登记重连目标
                val auth = synchronized(authenticatedDevices) { authenticatedDevices[uuid] }
                if (auth != null) registerReconnectTarget(uuid, auth.lastIp ?: "")
            }
        }
        lib.nrc_set_on_device_timeout_cb(ctx, deviceTimeoutCb); rustCallbackRefs.add(deviceTimeoutCb)

        // ---- on_state_query (超级岛/媒体心跳查询回调：0=不存在 / 1=存在无变更 / 2=存在有变更) ----
        // 运行在 Rust 心跳线程且锁已释放，回调内可直接调用 nrc_push_*（isQuery=1）响应变更
        val stateQueryCb = object : NotifyRelayCore.OnStateQueryCb {
            override fun invoke(uuidPtr: Pointer?, featureIdPtr: Pointer?, isMedia: Int, userData: Pointer?): Int {
                val remoteUuid = ptr2str(uuidPtr) ?: return 0
                val featureId = ptr2str(featureIdPtr) ?: return 0
                val dm = _callbackInstance ?: return 0
                return try {
                    if (isMedia != 0) dm.handleMediaStateQuery(remoteUuid, featureId)
                    else dm.handleSuperIslandStateQuery(remoteUuid, featureId)
                } catch (e: Exception) {
                    Logger.e("CoreCb", "on_state_query error: ${e.message}")
                    1 // 异常保守保活，等待下一次查询
                }
            }
        }
        lib.nrc_set_on_state_query_cb(ctx, stateQueryCb); rustCallbackRefs.add(stateQueryCb)

        // ---- 日志回调（接入 Logger.CURRENT_LEVEL 等级控制） ----
        NativeCore.setLogCallback(ctx)
    }

    // ---- 状态查询回调处理（运行在 Rust 心跳线程，锁已释放；回调内可直接调 push(isQuery=1)） ----
    private val mediaFeatureId = "media_global" // 与 Rust MEDIA_KEY 一致

    /** 超级岛查询：扫描活跃通知，重算 featureId（与 Rust 算法一致，iid 传空）匹配后对比推送 */
    private fun handleSuperIslandStateQuery(remoteUuid: String, featureId: String): Int {
        val listener = NotifyRelayNotificationListenerService.instance ?: return 0
        val actives = listener.activeNotifications ?: return 0
        for (sbn in actives) {
            if (sbn.packageName == context.packageName) continue
            val superData = try { SuperIslandManager.extractSuperIslandData(sbn, context) } catch (_: Exception) { null } ?: continue
            val superPkg = superData.sourcePackage ?: continue
            // 重算 featureId：与 Rust 会话 key 及发送端严格一致（sbn.key 稳定值）
            val computedId = listener.getNotificationKey(sbn, "")
            if (computedId != featureId) continue
            // 匹配：组装 full（与 sendSuperIslandData 同格式）并对比推送。
            // 该回调运行在 Rust 心跳线程且必须同步返回 0/1/2，无法异步处理；
            // 查询路径的 picMap 来自已提取的活跃通知（多为 data URI/http，无 IO），
            // 仅本地 file/content URI 时才会发生实际文件读取，故在此包装 runBlocking 是必要且可接受的。
            val content = kotlinx.coroutines.runBlocking {
                MessageSender.buildSuperIslandFullContent(
                    context, superPkg,
                    superData.appName ?: "超级岛",
                    superData.title, superData.text,
                    sbn.postTime, superData.paramV2Raw,
                    superData.picMap ?: emptyMap(),
                    featureId
                )
            }
            return compareAndPushState(remoteUuid, featureId, content, isMedia = false)
        }
        // 无匹配：平台上不存在该会话
        Logger.w("CoreCb", "状态查询: 超级岛会话不存在, fid=$featureId")
        return 0
    }

    private var cachedMediaBitmap: Bitmap? = null
    private var cachedMediaCoverUrl: String? = null
    private val mediaCoverCacheLock = Any()

    /** 媒体查询：读取当前媒体会话组装 full 并对比推送；无媒体会话返回 0 */
    private fun handleMediaStateQuery(remoteUuid: String, featureId: String): Int {
        if (featureId != mediaFeatureId) return 0
        val monitor = MediaSessionMonitorService.instance ?: return 0
        val primary = monitor.getPrimaryController() ?: return 0
        val pkg = primary.packageName
        val mediaData = NotifyRelayNotificationListenerService.getMediaSessionData(pkg)
            ?: return 1 // 有媒体会话但数据未就绪，保守保活等待下次查询
        val coverUrl = synchronized(mediaCoverCacheLock) {
            val bmp = mediaData.artBitmap
            if (bmp !== cachedMediaBitmap) {
                cachedMediaBitmap = bmp
                cachedMediaCoverUrl = if (bmp == null) null else try {
                    val stream = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                    "data:image/jpeg;base64," + Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                } catch (_: Exception) { null }
            }
            cachedMediaCoverUrl
        }
        val content = MessageSender.buildMediaFullContent(
            context, pkg, pkg, mediaData.title, mediaData.artist, coverUrl, System.currentTimeMillis()
        )
        return compareAndPushState(remoteUuid, featureId, content, isMedia = true)
    }

    /**
     * 与上次推送缓存对比（仅 Rust diff 字段：title/text/param_v2_raw/pics）：
     * 缓存缺失 → 保守"有变更"推一次建立缓存；无变更 → 也推一次(isQuery=1)发空差量保活 → 1；变更 → 更新缓存并推(isQuery=1) → 2
     */
    private fun compareAndPushState(remoteUuid: String, featureId: String, fullJson: String, isMedia: Boolean): Int {
        val cached = MessageSender.getLastPushedState(featureId)
        if (cached == null) {
            MessageSender.cacheLastPushedState(featureId, fullJson)
            pushQueryResponse(remoteUuid, featureId, fullJson, isMedia)
            return 2
        }
        if (sameDiffFields(cached, fullJson)) {
            // 无变更：仍推一次，Rust 端发空差量保活包，刷新远端卡片时间戳防止超时消失
            pushQueryResponse(remoteUuid, featureId, fullJson, isMedia)
            return 1
        }
        MessageSender.cacheLastPushedState(featureId, fullJson)
        pushQueryResponse(remoteUuid, featureId, fullJson, isMedia)
        return 2
    }

    /** 查询响应推送：仅推给查询对应的远端设备（isQuery=1） */
    private fun pushQueryResponse(remoteUuid: String, featureId: String, fullJson: String, isMedia: Boolean) {
        try {
            val ctx = rustContextInternal ?: return
            val queuePtr = NativeCore.senderQueuePtr
            if (queuePtr == 0L) return
            if (isMedia) NativeCore.pushMediaState(ctx, queuePtr, remoteUuid, fullJson, false, true)
            else NativeCore.pushSuperislandState(ctx, queuePtr, remoteUuid, fullJson, false, true)
        } catch (e: Exception) {
            Logger.w("CoreCb", "查询响应推送失败: $remoteUuid fid=$featureId", e)
        }
    }

    /** 与 Rust diff 字段严格对齐（title/text/param_v2_raw/pics），避免错位导致误判 */
    private fun sameDiffFields(a: String, b: String): Boolean {
        return try {
            val ja = JSONObject(a)
            val jb = JSONObject(b)
            ja.optString("title") == jb.optString("title") &&
                ja.optString("text") == jb.optString("text") &&
                ja.optString("param_v2_raw") == jb.optString("param_v2_raw") &&
                ja.opt("pics").toString() == jb.opt("pics").toString()
        } catch (_: Exception) {
            false
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

    // 将认证设备登记到 Rust 重连状态机（先移除再添加，重置重试周期）
    private fun registerReconnectTarget(uuid: String, ip: String) {
        try {
            if (uuid == this.uuid) return
            val ctx = rustContext ?: return
            if (NativeCore.reconnectStatePtr == 0L) return
            if (ip.isNullOrEmpty() || ip == "0.0.0.0") return
            NativeCore.reconnectRemoveTarget(ctx, NativeCore.reconnectStatePtr, uuid)
            NativeCore.reconnectAddTarget(ctx, NativeCore.reconnectStatePtr, uuid, ip)
        } catch (_: Exception) {}
    }

    // 重新登记所有认证设备到 Rust 重连状态机（网络恢复后调用）
    internal fun refreshAllReconnectTargetsInternal() {
        val snapshot = synchronized(authenticatedDevices) { authenticatedDevices.toMap() }
        for ((uuid, auth) in snapshot) {
            if (uuid == "本机") continue
            registerReconnectTarget(uuid, auth.lastIp ?: "")
        }
    }

    // 从 Rust 重连状态机移除目标
    private fun removeReconnectTarget(uuid: String) {
        try {
            val ctx = rustContext ?: return
            if (NativeCore.reconnectStatePtr == 0L) return
            NativeCore.reconnectRemoveTarget(ctx, NativeCore.reconnectStatePtr, uuid)
        } catch (_: Exception) {}
    }

    // 将认证设备登记到 Rust 已知设备扫描器（known_device_scanner）
    private fun registerKnownDevice(uuid: String, ip: String) {
        try {
            if (uuid == this.uuid) return
            val ctx = rustContext ?: return
            if (ip.isNullOrEmpty() || ip == "0.0.0.0") return
            NativeCore.removeKnownDevice(ctx, uuid)
            NativeCore.addKnownDevice(ctx, uuid, ip)
        } catch (_: Exception) {}
    }

    // 从 Rust 已知设备扫描器移除
    private fun removeKnownDevice(uuid: String) {
        try {
            val ctx = rustContext ?: return
            NativeCore.removeKnownDevice(ctx, uuid)
        } catch (_: Exception) {}
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
     * - 从 Rust 上下文移除设备密钥（registry 状态随之清理）
     * - 从 Rust 重连状态机与已知设备扫描器移除（心跳调度随之停止）
     * - 直接从数据库中删除设备
     * - 从内存中移除设备信息
     * - 触发 updateDeviceList() 以通知观察者
     * 返回 true 表示存在并已移除，false 表示没有该uuid
     */
    fun removeAuthenticatedDevice(uuid: String, deleteHistory: Boolean = false): Boolean {
        try {
            var existed = false
            
            // 从协议不兼容设备集合移除
            try { incompatibleDevicesInternal.remove(uuid) } catch (_: Exception) {}

            // 从 Rust 上下文移除设备密钥（同时清理 registry 状态）
            try {
                rustContext?.let { NativeCore.removeDevice(it, uuid) }
                saveRustCoreState()
            } catch (_: Exception) {}

            // 从 Rust 重连状态机移除目标
            removeReconnectTarget(uuid)

            // 从 Rust 已知设备扫描器移除（调度器随之停止该设备心跳）
            removeKnownDevice(uuid)

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

    val audioRelayPlayer = AudioRelayPlayer(context)
    private var currentAudioRelayUuid: String = ""

    private data class PendingAudioSend(
        val deviceIp: String,
        val deviceName: String,
        val remoteUuid: String
    )
    private var pendingAudioRelaySend: PendingAudioSend? = null

    fun startSendTo(deviceIp: String, deviceName: String, remoteUuid: String) {
        pendingAudioRelaySend = PendingAudioSend(deviceIp, deviceName, remoteUuid)
        onRequestMediaProjection?.invoke()
    }

    fun startPendingAudioRelaySend() {
        val pending = pendingAudioRelaySend ?: return
        pendingAudioRelaySend = null
        if (NativeCore.mediaProjection != null) {
            startAudioRelaySend(pending.deviceIp, pending.deviceName, pending.remoteUuid)
        }
    }

    private fun startAudioRelaySend(deviceIp: String, deviceName: String, remoteUuid: String) {
        val projection = NativeCore.mediaProjection ?: return
        currentAudioRelayUuid = remoteUuid
        audioRelayPlayer.start("send", remoteUuid = remoteUuid)
        audioRelayPlayer.startSendCapture(projection)
        showAudioRelayNotification(deviceName, direction = "send")
    }

    private fun showAudioRelayNotification(deviceName: String, remoteUuid: String = "", direction: String = "recv") {
        if (remoteUuid.isNotEmpty()) {
            currentAudioRelayUuid = remoteUuid
        }
        registerAudioRelayStopReceiver()
        com.xzyht.notifyrelay.servers.AudioRelayForegroundService.start(context, deviceName, direction)
    }

    private fun registerAudioRelayStopReceiver() {
        if (audioRelayNotificationReceiver != null) return
        audioRelayNotificationReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == com.xzyht.notifyrelay.servers.AudioRelayForegroundService.STOP_ACTION) {
                    stopAudioRelay()
                }
            }
        }
        try {
            context.registerReceiver(
                audioRelayNotificationReceiver,
                IntentFilter(com.xzyht.notifyrelay.servers.AudioRelayForegroundService.STOP_ACTION),
                Context.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {}
    }

    fun stopAudioRelay() {
        val uuid = currentAudioRelayUuid
        if (uuid.isNotEmpty()) {
            val device = resolveDeviceInfo(uuid, "", 23333)
            if (device != null) {
                val raw = "{\"type\":\"MEDIA_CONTROL\",\"action\":\"audioStop\"}"
                try {
                    ProtocolSender.sendEncrypted(this@DeviceConnectionManager, device, "DATA_MEDIA_CONTROL", raw)
                } catch (_: Exception) {}
            }
        }
        audioRelayPlayer.stop()
        cancelAudioRelayNotification()
    }

    private fun cancelAudioRelayNotification() {
        try {
            audioRelayNotificationReceiver?.let { context.unregisterReceiver(it) }
        } catch (_: Exception) {}
        audioRelayNotificationReceiver = null
        com.xzyht.notifyrelay.servers.AudioRelayForegroundService.stop(context)
        com.xzyht.notifyrelay.servers.MediaProjectionForegroundService.stop(context)
    }
}
