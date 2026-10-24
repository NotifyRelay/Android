package com.xzyht.notifyrelay.nativecore

import android.media.projection.MediaProjection
import android.util.Log
import com.sun.jna.Pointer
import com.xzyht.notifyrelay.core.ffi.Core
import com.xzyht.notifyrelay.core.ffi.CoreCallbacks
import com.xzyht.notifyrelay.core.ffi.abiProbe
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
import notifyrelay.base.util.Logger as AppLogger

/**
 * Rust 核心门面。
 *
 * 全部能力经 UniFFI 生成的绑定调用（[Core]），不再使用手写 JNA 声明。
 * 保留 `Pointer` 形参仅为兼容既有调用点：上下文由 [Core] 单一持有，
 * 形参值不参与定位（传 null 亦可）。
 */
object NativeCore {
    private const val TAG = "NativeCore"

    private val coreRef = AtomicReference<Core?>(null)

    /** 当前核心实例；未创建时为 null。 */
    val core: Core?
        get() = coreRef.get()

    var mediaProjection: MediaProjection? = null

    /**
     * 发送队列句柄。
     *
     * 队列由 core 内部持有，平台侧不再需要句柄值；保留本属性仅为兼容既有调用点，
     * 非 0 表示队列已就绪。
     */
    val senderQueuePtr: Long
        get() = if (coreRef.get()?.isSenderQueueReady() == true) 1L else 0L

    /** 发送队列是否已就绪（由 [startCore] 建立）。 */
    val senderQueueReady: Boolean
        get() = coreRef.get()?.isSenderQueueReady() ?: false

    /** 创建核心上下文；重复调用返回既有实例。 */
    fun createContext(): Pointer? {
        if (coreRef.get() == null) {
            synchronized(this) {
                if (coreRef.get() == null) {
                    coreRef.set(Core())
                }
            }
        }
        return rawContextPointer()
    }

    /** 上下文地址（仅用于日志/兼容），无实例时为 null。 */
    private fun rawContextPointer(): Pointer? {
        val c = coreRef.get() ?: return null
        return Pointer(c.rawContextPtr().toLong())
    }

    fun getContext(): Pointer? = rawContextPointer()

    fun setContext(ctx: Pointer?) {
        // 上下文由 Core 持有，此处仅为兼容旧调用点保留
    }

    /** 绑定是否已初始化。 */
    fun isReady(): Boolean = coreRef.get() != null

    /** UniFFI ABI 版本（绑定链路自检用）。 */
    fun getAbiProbe(): String = abiProbe()

    // ======== ECDH ========

    fun generateKeypair(ctx: Pointer?): Boolean = coreRef.get()?.ecdhGenerateKeypair() ?: false

    fun getPublicKey(ctx: Pointer?): String? = coreRef.get()?.ecdhGetPublicKey()

    fun hasKeypair(ctx: Pointer?): Boolean = coreRef.get()?.ecdhHasKeypair() ?: false

    fun deriveSharedSecret(
        ctx: Pointer?,
        peerUuid: String,
        peerPubKey: String,
    ): Boolean = coreRef.get()?.ecdhDeriveSharedSecret(peerUuid, peerPubKey) ?: false

    fun migrateSharedSecret(
        ctx: Pointer?,
        deviceUuid: String,
        secret: ByteArray,
    ): Boolean = coreRef.get()?.migrateSharedSecret(deviceUuid, secret) ?: false

    fun removeDevice(
        ctx: Pointer?,
        deviceUuid: String,
    ): Boolean = coreRef.get()?.removeDevice(deviceUuid) ?: false

    // ======== State ========

    fun exportState(ctx: Pointer?): String? = coreRef.get()?.exportState()

    fun importState(
        ctx: Pointer?,
        json: String,
    ): Boolean = coreRef.get()?.importState(json) ?: false

    fun encryptLocalState(
        ctx: Pointer?,
        plaintext: String,
        deviceUuid: String,
    ): String? = coreRef.get()?.encryptLocalState(plaintext, deviceUuid)

    fun decryptLocalState(
        ctx: Pointer?,
        encryptedB64: String,
        deviceUuid: String,
    ): String? = coreRef.get()?.decryptLocalState(encryptedB64, deviceUuid)

    /**
     * 本地状态反序列化聚合入口：解密与导入由 core 内部配对完成。
     *
     * @param encrypted 0=payload 为明文状态 JSON，其它=payload 为密文
     * @return true 表示状态已导入
     */
    fun deserializeLocalState(
        ctx: Pointer?,
        payload: String,
        deviceUuid: String,
        encrypted: Int,
    ): Boolean {
        val c = coreRef.get() ?: return false
        val result = c.deserializeLocalState(payload, deviceUuid, encrypted != 0)
        if (result.isEmpty()) return false
        return try {
            JSONObject(result).optBoolean("ok", false)
        } catch (e: Exception) {
            false
        }
    }

    fun exportDeviceKey(
        ctx: Pointer?,
        deviceUuid: String,
    ): String? = coreRef.get()?.exportDeviceKey(deviceUuid)

    // ======== Persistence（Rust 私有库：uuid/密钥由 Rust 持有）=======

    fun getLocalUuid(ctx: Pointer?): String? = coreRef.get()?.getLocalUuid()

    fun renameDevice(
        ctx: Pointer?,
        deviceUuid: String,
        name: String,
    ): Boolean = coreRef.get()?.renameDevice(deviceUuid, name) ?: false

    // ======== Process ========

    fun periodicBroadcast(
        ctx: Pointer?,
        action: Int,
        uuid: String? = null,
        name: String? = null,
        battery: Int = -1,
        deviceType: String? = null,
    ): Int =
        coreRef.get()?.periodicBroadcast(action, uuid ?: "", name ?: "", battery, deviceType ?: "")
            ?: -1

    // ======== Send functions ========

    fun sendHandshake(
        ctx: Pointer?,
        uuid: String,
        pubKey: String,
        localIp: String,
        targetIp: String,
        battery: Int,
        deviceType: String,
    ): Int = coreRef.get()?.sendHandshake(uuid, localIp, targetIp, battery, deviceType) ?: -1

    fun connectDevice(
        ctx: Pointer?,
        uuid: String,
        targetIp: String,
        battery: Int,
        deviceType: String,
    ): Int = coreRef.get()?.connectDevice(uuid, targetIp, battery, deviceType) ?: -1

    fun sendPairingInit(
        ctx: Pointer?,
        localUuid: String,
        targetUuid: String,
        expectedCode: String,
        battery: Int,
        deviceType: String,
    ): Int =
        coreRef.get()?.sendPairingInit(localUuid, targetUuid, expectedCode, battery, deviceType)
            ?: -1

    fun sendPairingResp(
        ctx: Pointer?,
        uuid: String,
        ltPub: String,
        pairingCode: String,
        ip: String,
        battery: Int,
        deviceType: String,
    ): Int = coreRef.get()?.sendPairingResp(uuid, ltPub, pairingCode, ip, battery, deviceType) ?: -1

    fun sendAccept(
        ctx: Pointer?,
        uuid: String,
        ltPubKey: String,
        ip: String,
        battery: Int,
        deviceType: String,
    ) {
        coreRef.get()?.sendAccept(uuid, ltPubKey, ip, battery, deviceType)
    }

    fun sendReject(
        ctx: Pointer?,
        uuid: String,
    ) {
        coreRef.get()?.sendReject(uuid)
    }

    // ======== Pairing code management (Rust-generated) ========

    fun generatePairingCode(
        ctx: Pointer?,
        ttlSecs: Int = 300,
    ): String? = coreRef.get()?.generatePairingCode(ttlSecs.toUInt())

    fun clearPairingCode(ctx: Pointer?) {
        coreRef.get()?.clearPairingCode()
    }

    // ======== Utility functions ========

    fun computeFeatureId(
        superPkg: String,
        paramV2Raw: String,
        title: String,
        text: String,
        instanceId: String,
    ): String? =
        com.xzyht.notifyrelay.core.ffi.computeFeatureId(
            superPkg,
            paramV2Raw,
            title,
            text,
            instanceId,
        )

    fun parseSuperIslandInbound(
        deviceUuid: String,
        pkg: String,
        fullJson: String,
    ): String? =
        com.xzyht.notifyrelay.core.ffi
            .parseSuperislandInbound(deviceUuid, pkg, fullJson)

    fun parseNotificationInbound(
        fullJson: String,
    ): String? =
        com.xzyht.notifyrelay.core.ffi
            .parseNotificationInbound(fullJson)

    fun parseMediaInbound(
        fullJson: String,
    ): String? =
        com.xzyht.notifyrelay.core.ffi
            .parseMediaInbound(fullJson)

    // ======== Dedup engine ========

    fun dedup(
        ctx: Pointer?,
        action: Int,
        dedupKey: String,
        arg1Ms: Long = 0L,
        arg2Ms: Long = 0L,
    ): Int = coreRef.get()?.dedup(action, dedupKey, arg1Ms, arg2Ms) ?: -1

    // ======== 入站内容去重（10s 窗口；键与 TTL 由 core 持有）========

    fun contentDedup(
        ctx: Pointer?,
        action: Int,
        title: String,
        text: String,
    ): Int = coreRef.get()?.contentDedup(action, title, text) ?: -1

    // ======== Text similarity & dedup ========

    fun shouldDeduplicate(
        newTitle: String,
        newText: String,
        oldTitle: String,
        oldText: String,
    ): Boolean =
        com.xzyht.notifyrelay.core.ffi
            .shouldDeduplicate(newTitle, newText, oldTitle, oldText)

    // ======== Filter ========

    fun setFilterConfig(
        ctx: Pointer?,
        configJson: String,
    ): Int = coreRef.get()?.setFilterConfig(configJson) ?: -1

    fun mapLocalPackage(
        ctx: Pointer?,
        pkg: String,
    ): String? = coreRef.get()?.mapLocalPackage(pkg)

    fun checkFilterMode(
        ctx: Pointer?,
        mappedPkg: String,
        originalPkg: String,
        title: String,
        text: String,
    ): Boolean = (coreRef.get()?.checkFilterMode(mappedPkg, originalPkg, title, text) ?: 1) != 0

    // ======== Network layer ========

    fun removeDeviceSession(
        ctx: Pointer?,
        uuid: String,
    ): Int = coreRef.get()?.removeDeviceSession(uuid) ?: -1

    // ======== FTP credential derivation ========

    fun deriveFtpCredentials(sharedSecretB64: String): String? =
        com.xzyht.notifyrelay.core.ffi
            .deriveFtpCredentials(sharedSecretB64)

    fun derivePasswordHash(password: String): String? =
        com.xzyht.notifyrelay.core.ffi
            .derivePasswordHash(password)

    fun generateRandomPassword(): String? =
        com.xzyht.notifyrelay.core.ffi
            .generateRandomPassword()

    // ======== Heartbeat scheduler params (电量/名称变化) ========

    fun updateHeartbeatSchedulerParams(
        ctx: Pointer?,
        name: String,
        battery: Int,
        deviceType: String,
    ) {
        coreRef.get()?.updateHeartbeatSchedulerParams(name, battery, deviceType)
    }

    // ======== Device state snapshot ========

    fun getDeviceList(
        ctx: Pointer?,
        authedTimeoutMs: Long,
        unauthedTimeoutMs: Long,
    ): String? = coreRef.get()?.getDeviceList(authedTimeoutMs, unauthedTimeoutMs)

    // ======== Sender queue ========

    /** 入队：header 规范化、通知类发送去重（仅 DATA_NOTIFICATION）与 ts 注入由 core 内部完成 */
    fun enqueueMessage(
        ctx: Pointer?,
        queuePtr: Long,
        deviceUuid: String,
        header: String,
        plaintext: String,
    ): Int {
        val c = coreRef.get() ?: return -1
        if (!c.isSenderQueueReady()) return -1
        return if (c.enqueueMessage(deviceUuid, header, plaintext)) 1 else 0
    }

    // ======== Clipboard ========

    /**
     * 剪贴板内容变化入口。Rust 内部完成去重/防循环/频率限制/2MB 阈值判定并直接入队发送。
     * 返回 JSON：{"action": "sent"|"skipped"|"file_transfer", "reason": "..."}
     */
    fun clipboardOnChanged(
        ctx: Pointer?,
        queuePtr: Long,
        targetsJson: String,
        mime: String,
        content: String,
        nowMs: Long,
        force: Boolean,
    ): String? = coreRef.get()?.clipboardOnChanged(targetsJson, mime, content, nowMs, force)

    /**
     * 收到远程剪贴板报文入口。Rust 解析/归一化并登记防循环时间窗。
     * 返回 JSON：{"type": "text"|"image", "content": "..."}
     */
    fun clipboardOnReceived(
        ctx: Pointer?,
        payloadJson: String,
        nowMs: Long,
    ): String? = coreRef.get()?.clipboardOnReceived(payloadJson, nowMs)

    // ======== App sync (app list & icons) ========

    /**
     * 批量过滤并构造图标请求报文（Rust 内部维护 pending 状态与超时清理）。
     * 返回 ICON_REQUEST 报文 JSON；无需请求时返回 {}。
     */
    fun appSyncPrepareIconRequest(
        ctx: Pointer?,
        packages: List<String>,
        installed: List<String>,
        cached: List<String>,
        appDeviceMap: Map<String, List<String>>,
        sourceDeviceUuid: String,
        nowMs: Long,
    ): String? {
        val c = coreRef.get() ?: return null
        val appDeviceJson =
            JSONObject()
                .apply { appDeviceMap.forEach { (k, v) -> put(k, JSONArray(v)) } }
                .toString()
        return c.appSyncPrepareIconRequest(
            JSONArray(packages).toString(),
            JSONArray(installed).toString(),
            JSONArray(cached).toString(),
            appDeviceJson,
            sourceDeviceUuid,
            nowMs,
        )
    }

    /**
     * 图标请求聚合入口：先按同批包名解除旧登记，再构造图标请求报文。
     * 返回 ICON_REQUEST 报文 JSON；无需请求时返回 {}。
     */
    fun appSyncRequestIcons(
        ctx: Pointer?,
        packages: List<String>,
        installed: List<String>,
        cached: List<String>,
        appDeviceMap: Map<String, List<String>>,
        sourceDeviceUuid: String,
        nowMs: Long,
    ): String? {
        val c = coreRef.get() ?: return null
        val appDeviceJson =
            JSONObject()
                .apply { appDeviceMap.forEach { (k, v) -> put(k, JSONArray(v)) } }
                .toString()
        return c.appSyncRequestIcons(
            JSONArray(packages).toString(),
            JSONArray(installed).toString(),
            JSONArray(cached).toString(),
            appDeviceJson,
            sourceDeviceUuid,
            nowMs,
        )
    }

    fun appSyncClearIconPending(
        ctx: Pointer?,
        packages: List<String>,
    ) {
        coreRef.get()?.appSyncClearIconPending(JSONArray(packages).toString())
    }

    /** 解析图标响应报文，返回 {"icons":[...],"missing":[...]} */
    fun appSyncParseIconResponse(payload: String): String? =
        com.xzyht.notifyrelay.core.ffi
            .appSyncParseIconResponse(payload)

    /** 构造应用列表请求报文 */
    fun appSyncBuildApplistRequest(
        scope: String,
        nowMs: Long,
    ): String? =
        com.xzyht.notifyrelay.core.ffi
            .appSyncBuildApplistRequest(scope, nowMs)

    /** 解析应用列表响应报文 */
    fun appSyncParseApplistResponse(payload: String): String? =
        com.xzyht.notifyrelay.core.ffi
            .appSyncParseApplistResponse(payload)

    // ======== State merge (superisland / media) ========

    /** 推送超级岛全量状态；isQuery 为真表示响应状态查询。 */
    fun pushSuperislandState(
        ctx: Pointer?,
        queuePtr: Long,
        deviceUuid: String,
        fullJson: String,
        isEnd: Boolean,
        isQuery: Boolean,
    ): Boolean = coreRef.get()?.pushSuperislandState(deviceUuid, fullJson, isEnd, isQuery) ?: false

    /** 推送媒体全量状态；isQuery 为真表示响应状态查询。 */
    fun pushMediaState(
        ctx: Pointer?,
        queuePtr: Long,
        deviceUuid: String,
        fullJson: String,
        isEnd: Boolean,
        isQuery: Boolean,
    ): Boolean = coreRef.get()?.pushMediaState(deviceUuid, fullJson, isEnd, isQuery) ?: false

    // ======== Callbacks ========

    private var callbacksRef: CoreCallbacks? = null

    /** 注册全部回调（配对/数据/设备事件/状态查询/音频/日志）。 */
    fun setCallbacks(callbacks: CoreCallbacks) {
        callbacksRef = callbacks
        coreRef.get()?.setCallbacks(callbacks)
    }

    // ======== Network change ========

    fun onNetworkChanged(
        ctx: Pointer?,
        localIp: String?,
    ) {
        coreRef.get()?.onNetworkChanged(localIp ?: "")
    }

    // ======== Local IP ========

    fun getLocalIp(): String? =
        com.xzyht.notifyrelay.core.ffi
            .getLocalIp()

    // ======== Discovery ========

    fun addKnownDevice(
        ctx: Pointer?,
        uuid: String,
        ip: String,
    ) {
        coreRef.get()?.addKnownDevice(uuid, ip)
    }

    fun removeKnownDevice(
        ctx: Pointer?,
        uuid: String,
    ) {
        coreRef.get()?.removeKnownDevice(uuid)
    }

    // ======== Reconnect ========

    fun reconnectAddTarget(
        ctx: Pointer?,
        uuid: String,
        ip: String,
    ) {
        coreRef.get()?.reconnectAddTarget(uuid, ip)
    }

    fun reconnectRemoveTarget(
        ctx: Pointer?,
        uuid: String,
    ) {
        coreRef.get()?.reconnectRemoveTarget(uuid)
    }

    // ======== Audio stream ========

    private var audioDataCallback: ((ByteArray, Int, Int) -> Unit)? = null

    fun registerAudioDataCallback(cb: (ByteArray, Int, Int) -> Unit) {
        audioDataCallback = cb
    }

    /** 音频数据回调入口（由 [setCallbacks] 的实现转发）。 */
    fun dispatchAudioData(
        pcm: ByteArray,
        sampleRate: Int,
        channels: Int,
    ) {
        audioDataCallback?.invoke(pcm, sampleRate, channels)
    }

    fun audioStart(
        direction: String,
        port: Int,
        sampleRate: Int,
        channels: Int,
        remoteUuid: String = "",
    ): Int = coreRef.get()?.audioStart(direction, port, sampleRate, channels, remoteUuid) ?: -1

    fun audioWriteFrame(pcmData: ByteArray): Int = coreRef.get()?.audioWriteFrame(pcmData) ?: -1

    fun audioStop(): Int = coreRef.get()?.audioStop() ?: -1

    fun audioIsActive(): Boolean = (coreRef.get()?.audioIsActive() ?: 0) != 0

    // ======== Log callback ========

    /** 日志入口（由 [setCallbacks] 的实现转发）。 */
    fun dispatchLog(
        level: Int,
        msg: String,
    ) {
        when (level) {
            1 -> AppLogger.e("Rust", msg)
            2 -> AppLogger.w("Rust", msg)
            3 -> AppLogger.i("Rust", msg)
            4 -> AppLogger.d("Rust", msg)
            5 -> AppLogger.v("Rust", msg)
            else -> AppLogger.d("Rust", msg)
        }
    }

    // ======== Version ========

    fun getGitHash(): String? = coreRef.get()?.gitHash()

    // ======== Initialize core (统一启动 TCP、心跳、离线检测、发送队列、扫描、重连) ========

    fun startCore(
        ctx: Pointer?,
        uuid: String,
        name: String,
        battery: Int,
        deviceType: String,
        tcpPort: Short,
        pubkey: String,
        heartbeatIntervalMs: Long = 2000,
        offlineTimeoutSec: Long = 12,
        offlineCheckIntervalMs: Long = 5000,
        reconnectIntervalSecs: Long = 10,
        reconnectMaxRetries: Int = 5,
    ): Boolean {
        val c = coreRef.get() ?: return false
        val queue =
            c.startCore(
                uuid,
                name,
                battery,
                deviceType,
                tcpPort.toUShort(),
                heartbeatIntervalMs.toULong(),
                offlineTimeoutSec,
                offlineCheckIntervalMs.toULong(),
                reconnectIntervalSecs.toULong(),
                reconnectMaxRetries.toUInt(),
            )
        if (queue.toLong() == 0L) {
            Log.e(TAG, "startCore 启动失败，返回句柄: $queue")
            return false
        }
        return true
    }
}
