package notifyrelay.desktop.device

import com.xzyht.notifyrelay.nativecore.NativeCore
import com.xzyht.notifyrelay.nativecore.device.DeviceTargetRegistry
import com.xzyht.notifyrelay.nativecore.device.service.callback.DeviceCallbackHost
import com.xzyht.notifyrelay.nativecore.device.service.callback.StateQueryPort
import com.xzyht.notifyrelay.nativecore.device.service.pairing.PairingCoordinator
import com.xzyht.notifyrelay.nativecore.device.service.state.DeviceSnapshotStore
import com.xzyht.notifyrelay.nativecore.device.service.state.OnlineDevicesCachePort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import notifyrelay.base.log.Logger
import notifyrelay.domain.feature.device.model.AuthInfo
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.device.model.DeviceSnapshot
import notifyrelay.domain.feature.device.model.PendingPairing
import notifyrelay.domain.feature.device.service.callback.HandshakeRequestHandler
import notifyrelay.domain.feature.device.service.contract.DeviceAdminPort
import notifyrelay.domain.feature.device.service.pairing.HandshakeWaiterRegistry

/**
 * 桌面端设备管理。
 *
 * 只承载设备列表与配对：设备状态来自 Rust core 的快照，配对经 [PairingCoordinator] 完成。
 * 通知转发、音频中继等能力不属于设备列表范畴，桌面端不接入。
 */
class DesktopDeviceManager(
    private val localDisplayName: String = "桌面端",
) : DeviceAdminPort,
    DeviceCallbackHost {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val authenticatedDevices = mutableMapOf<String, AuthInfo>()
    private val rejectedDevices = mutableSetOf<String>()

    /** 桌面端不落盘在线缓存，只保留内存态。 */
    private val onlineCache =
        object : OnlineDevicesCachePort {
            override fun update(
                devices: Map<String, Pair<DeviceInfo, Boolean>>,
                addressable: List<DeviceSnapshot>,
            ) = Unit

            override fun onCacheUpdated() = Unit
        }

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

    override fun getPairedDeviceUuids(): Set<String> = synchronized(authenticatedDevices) { authenticatedDevices.keys.toSet() }

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
        // 桌面端不落盘认证表：core 私有库已保存密钥，快照即真源
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
        NativeCore.sendAccept(localUuid, localPublicKey, localDisplayName, 0, deviceType)
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
        // 桌面端不承载数据通道（通知/媒体/剪贴板等由 Android 端处理）
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

    override val notificationDataReceivedCallbacks: Collection<(String) -> Unit> get() = notificationCallbacks

    /** 启动 core 并开始设备发现。 */
    fun start() {
        // 注册核心回调：设备发现/连接事件需要它们才能刷新列表，core 日志也经此输出
        com.xzyht.notifyrelay.nativecore.device.service.callback.RustCallbackInstaller
            .install(this)

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
        val started =
            NativeCore.startCore(
                uuid = uuid,
                name = localDisplayName,
                battery = 0,
                deviceType = "desktop",
                tcpPort = DEFAULT_PORT.toShort(),
                pubkey = pubkey,
            )
        Logger.i(TAG, "core 启动: " + started + ", uuid=" + uuid)

        // 启动周期性广播，设备发现依赖它；action=1 开始、0 停止
        val broadcast = NativeCore.periodicBroadcast(1, uuid, localDisplayName, 0, "desktop")
        Logger.i(TAG, "广播已启动: " + broadcast)
        snapshotStore.requestRefresh()
    }

    /** 启动设备发现（周期性广播）。 */
    fun startDiscovery() {
        NativeCore.periodicBroadcast(1, localUuid, localDisplayName, 0, "desktop")
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
