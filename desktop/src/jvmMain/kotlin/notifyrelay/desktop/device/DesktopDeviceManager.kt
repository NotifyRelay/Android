package notifyrelay.desktop.device

import com.xzyht.notifyrelay.nativecore.NativeCore
import com.xzyht.notifyrelay.nativecore.device.DeviceTargetRegistry
import com.xzyht.notifyrelay.nativecore.device.ProtocolSender
import com.xzyht.notifyrelay.nativecore.device.service.callback.DeviceCallbackHost
import com.xzyht.notifyrelay.nativecore.device.service.callback.StateQueryPort
import com.xzyht.notifyrelay.nativecore.device.service.pairing.PairingCoordinator
import com.xzyht.notifyrelay.nativecore.device.service.state.DeviceSnapshotStore
import com.xzyht.notifyrelay.nativecore.device.service.state.OnlineDevicesCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import notifyrelay.base.log.Logger
import notifyrelay.desktop.platform.BatteryLevel
import notifyrelay.desktop.platform.PowerStatusMonitor
import notifyrelay.domain.feature.device.model.AuthInfo
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.device.model.DeviceSnapshot
import notifyrelay.domain.feature.device.model.PendingPairing
import notifyrelay.domain.feature.device.service.callback.HandshakeRequestHandler
import notifyrelay.domain.feature.device.service.contract.DeviceAdminPort
import notifyrelay.domain.feature.device.service.contract.DeviceAuthTablePort
import notifyrelay.domain.feature.device.service.pairing.HandshakeWaiterRegistry

/**
 * 桌面端设备管理。
 *
 * 只承载设备列表与配对：设备状态来自 Rust core 的快照，配对经 [PairingCoordinator] 完成。
 * 通知转发、音频中继等能力不属于设备列表范畴，桌面端不接入。
 */
class DesktopDeviceManager(
    private val localDisplayName: String = MachineName.resolve(),
) : DeviceAdminPort,
    DeviceAuthTablePort,
    DeviceCallbackHost,
    notifyrelay.domain.feature.device.service.contract.DeviceMessagePort {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val authenticatedDevices = mutableMapOf<String, AuthInfo>()
    private val rejectedDevices = mutableSetOf<String>()

    /** 在线设备缓存：随 core 快照落 Storage（键 online_devices_cache，SCRCPY 组）。 */
    private val onlineCache = OnlineDevicesCache()

    private val snapshotStore =
        DeviceSnapshotStore(
            scope = scope,
            localUuidProvider = { localUuid },
            defaultPort = DEFAULT_PORT,
            onlineDevicesCache = onlineCache,
            onSnapshotRefreshed = { projection -> syncAuthenticated(projection) },
        )

    private val targetRegistry = DeviceTargetRegistry { localUuid }

    private val pairingCoordinator =
        PairingCoordinator(
            registry = targetRegistry,
            snapshotStore = snapshotStore,
            authenticatedDeviceTable = authenticatedDevices,
            onAuthChanged = { snapshotStore.requestRefresh() },
        )

    override var handshakeRequestHandler: HandshakeRequestHandler? = null

    override val devices: StateFlow<Map<String, Pair<DeviceInfo, Boolean>>> get() = snapshotStore.devices

    override val localUuid: String
        get() = NativeCore.getLocalUuid() ?: ""

    override val localPublicKey: String
        get() = NativeCore.getPublicKey() ?: ""

    override var pendingPairing: PendingPairing?
        get() = pairingCoordinator.pendingPairing
        set(value) {
            pairingCoordinator.pendingPairing = value
        }

    override fun cancelPendingPairing() = pairingCoordinator.cancelPendingPairing()

    override fun getAuthenticatedDevices(): Map<String, AuthInfo> = synchronized(authenticatedDevices) { authenticatedDevices.toMap() }

    override fun authenticatedSnapshot(): Map<String, AuthInfo> = getAuthenticatedDevices()

    override fun getPairedDeviceUuids(): Set<String> = synchronized(authenticatedDevices) { authenticatedDevices.keys.toSet() }

    /** 当前已知的远端设备 UUID（供通知历史的设备维度使用）。 */
    fun knownDeviceUuids(): List<String> = getPairedDeviceUuids().sorted()

    override fun getAuthenticatedOnlineCount(): Int = snapshotStore.onlinePairedUuids().size

    override fun getAuthenticatedOnlineDevices(): List<DeviceInfo> = snapshotStore.onlinePairedAddressableSnapshots().mapNotNull { snap -> snapshotStore.currentInfo(snap.uuid) }

    override fun resolveDeviceInfo(
        uuid: String,
        fallbackIp: String?,
        fallbackPort: Int,
    ): DeviceInfo? = snapshotStore.currentInfo(uuid)

    override fun getRejectedDevices(): Set<String> = synchronized(rejectedDevices) { rejectedDevices.toSet() }

    override fun restoreRejectedDevices(uuids: Collection<String>): Set<String> =
        synchronized(rejectedDevices) {
            rejectedDevices.removeAll(uuids.toSet())
            rejectedDevices.toSet()
        }

    override suspend fun removeAuthenticatedDevice(
        uuid: String,
        deleteHistory: Boolean,
    ): Boolean {
        val removed = NativeCore.removeDevice(uuid)
        if (removed) {
            synchronized(authenticatedDevices) { authenticatedDevices.remove(uuid) }
            targetRegistry.removeAllTargets(uuid)
            snapshotStore.requestRefresh()
        }
        return removed
    }

    override fun triggerDeviceListRefresh() = snapshotStore.requestRefresh()

    // ==================== DeviceCallbackHost 实现（供 Rust 回调处理器使用） ====================

    override val callbackScope: CoroutineScope
        get() = scope

    override fun localDisplayName(): String = localDisplayName

    override fun resolveDevice(uuid: String): DeviceInfo? = snapshotStore.currentInfo(uuid)

    override fun deviceInfoOf(uuid: String): DeviceInfo? = snapshotStore.currentInfo(uuid)

    override fun saveAuthedDevices() {
        // 认证表以 core 快照为真源：请求刷新后由 onSnapshotRefreshed 重建
        snapshotStore.requestRefresh()
    }

    override fun registerReconnectTarget(
        uuid: String,
        ip: String,
    ) = targetRegistry.registerReconnectTarget(uuid, ip)

    override fun registerKnownDevice(
        uuid: String,
        ip: String,
    ) = targetRegistry.registerKnownDevice(uuid, ip)

    override fun completePairingWithLongTermKeys(
        uuid: String,
        remoteLtPubKey: String,
        displayName: String,
        lastIp: String?,
    ): Boolean = pairingCoordinator.completeWithLongTermKeys(uuid, remoteLtPubKey, displayName, lastIp)

    override fun sendAccept(deviceType: String) {
        if (!NativeCore.isReady()) return
        NativeCore.sendAccept(localUuid, localPublicKey, NativeCore.getLocalIp() ?: "0.0.0.0", BatteryLevel.signedPercent(), deviceType)
    }

    override fun sendReject() {
        if (!NativeCore.isReady()) return
        NativeCore.sendReject(localUuid)
    }

    override fun sendDataMessage(
        target: DeviceInfo,
        header: String,
        plaintext: String,
    ) {
        val result = ProtocolSender.sendEncrypted(this, target, header, plaintext)
        if (result != ProtocolSender.EnqueueResult.SUCCESS && result != ProtocolSender.EnqueueResult.DEDUPED) {
            Logger.w(TAG, "入队失败: header=" + header + ", device=" + target.displayName + ", result=" + result)
        }
    }

    /** 状态查询端口：桌面端无超级岛/媒体会话，统一回 1（存在无变更）保活。 */
    private val stateQueryPort =
        object : StateQueryPort {
            override fun handle(
                remoteUuid: String,
                featureId: String,
                isMedia: Boolean,
            ): Int = 1

            override fun removeKey(
                remoteUuid: String,
                featureId: String,
            ) = Unit

            override fun removeKeysForDevice(remoteUuid: String) = Unit
        }

    override val stateQueryResponder: StateQueryPort get() = stateQueryPort

    override val rejectedDeviceIds: MutableSet<String> get() = rejectedDevices

    override val incompatibleDeviceIds: MutableSet<String> = mutableSetOf()

    override val authenticatedDeviceTable: MutableMap<String, AuthInfo> get() = authenticatedDevices

    override val handshakeWaiters = HandshakeWaiterRegistry()

    private val notificationCallbacks = mutableSetOf<(String) -> Unit>()

    override val notificationDataReceivedCallbacks: Collection<(String) -> Unit>
        get() = synchronized(notificationCallbacks) { notificationCallbacks.toList() }

    /** 登记入站通知回调（聊天端口与本表共用同一集合）。 */
    internal fun registerNotificationDataCallback(callback: (String) -> Unit) {
        synchronized(notificationCallbacks) { notificationCallbacks.add(callback) }
    }

    /** 注销入站通知回调。 */
    internal fun unregisterNotificationDataCallback(callback: (String) -> Unit) {
        synchronized(notificationCallbacks) { notificationCallbacks.remove(callback) }
    }

    /**
     * 广播一条入站通知报文；单个回调异常不影响其余回调。
     *
     * 回调订阅方是 Compose 界面（聊天测试页），写入的是快照状态，
     * 而本方法运行在 Rust/JNA 回调线程，故统一投递到事件派发线程执行。
     */
    internal fun dispatchNotification(payload: String) {
        val callbacks = synchronized(notificationCallbacks) { notificationCallbacks.toList() }
        if (callbacks.isEmpty()) return
        javax.swing.SwingUtilities.invokeLater {
            for (callback in callbacks) {
                runCatching { callback(payload) }
                    .onFailure { Logger.w(TAG, "通知回调失败", it) }
            }
        }
    }

    /** 启动 core 并开始设备发现。 */
    fun start() {
        // 注册核心回调：设备发现/连接事件需要它们才能刷新列表，core 日志也经此输出
        com.xzyht.notifyrelay.nativecore.device.service.callback.RustCallbackInstaller
            .install(this, dataPort = DesktopDataMessagePort(this, this))

        if (!NativeCore.isReady() && !NativeCore.createContext()) {
            Logger.e(TAG, "Rust core 上下文创建失败，设备功能不可用")
            return
        }
        if (!NativeCore.hasKeypair()) {
            NativeCore.generateKeypair()
        }
        val uuid = NativeCore.getLocalUuid()
        val pubkey = NativeCore.getPublicKey()
        if (uuid.isNullOrBlank() || pubkey.isNullOrBlank()) {
            Logger.e(TAG, "本机身份不可用，跳过 core 启动")
            return
        }
        // core 以符号区分充电/放电：正=充电、负=放电
        val battery = BatteryLevel.signedPercent()
        val started =
            NativeCore.startCore(
                uuid = uuid,
                name = localDisplayName,
                battery = battery,
                deviceType = "pc",
                tcpPort = DEFAULT_PORT.toShort(),
                pubkey = pubkey,
            )
        Logger.i(TAG, "core 启动: " + started + ", uuid=" + uuid)

        // 启动周期性广播，设备发现依赖它；action=1 开始、0 停止
        val broadcast = NativeCore.periodicBroadcast(1, uuid, localDisplayName, battery, "pc")
        Logger.i(TAG, "广播已启动: " + broadcast)
        startPowerMonitor()
        snapshotStore.requestRefresh()
    }

    /**
     * 注册电源状态变化监听：电量或充电状态变化时更新 core 心跳调度器参数，
     * 使心跳报文携带实时带符号电量（正=充电、负=放电）。
     *
     * 与 Windows 端 `SystemInfoService.BatteryChanged` → `UpdateHeartbeatSchedulerParams` 同一条路径。
     */
    private fun startPowerMonitor() {
        PowerStatusMonitor.start { snapshot ->
            if (!NativeCore.isReady()) return@start
            val signed = if (snapshot.charging) snapshot.percent else -snapshot.percent
            NativeCore.updateHeartbeatSchedulerParams(localDisplayName, signed, "pc")
        }
    }

    /** 注销电源状态监听。 */
    fun stopPowerMonitor() = PowerStatusMonitor.stop()

    /** 启动设备发现（周期性广播）。 */
    fun startDiscovery() {
        NativeCore.periodicBroadcast(1, localUuid, localDisplayName, BatteryLevel.signedPercent(), "pc")
    }

    /** 停止设备发现。 */
    fun stopDiscovery() {
        NativeCore.periodicBroadcast(0)
    }

    /** 从快照投影同步已认证设备表。 */
    private fun syncAuthenticated(projection: Map<String, DeviceSnapshot>) {
        synchronized(authenticatedDevices) {
            authenticatedDevices.clear()
            projection.values
                .filter { it.paired }
                .forEach { snap ->
                    authenticatedDevices[snap.uuid] =
                        AuthInfo(
                            publicKey = "",
                            sharedSecret = "",
                            isAccepted = true,
                            displayName = snap.name,
                            lastIp = snap.ip,
                            lastPort = snap.port,
                            deviceType = snap.deviceType,
                        )
                }
        }
    }

    private companion object {
        const val TAG = "DesktopDevice"
        const val DEFAULT_PORT = 23333
    }
}
