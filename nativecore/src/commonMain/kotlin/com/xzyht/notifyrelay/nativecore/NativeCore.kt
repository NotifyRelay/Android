package com.xzyht.notifyrelay.nativecore

import com.xzyht.notifyrelay.core.ffi.Core
import com.xzyht.notifyrelay.core.ffi.CoreCallbacks
import com.xzyht.notifyrelay.core.ffi.abiProbe
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import notifyrelay.base.log.Logger as AppLogger

/**
 * Rust 核心门面。
 *
 * 全部能力经 UniFFI 生成的绑定调用（[Core]），上下文由 [Core] 单一持有，
 * 平台侧不再传递上下文句柄与发送队列句柄。
 */
object NativeCore {
    private const val TAG = "NativeCore"

    private val coreRef = atomic<Core?>(null)

    private val coreLock = SynchronizedObject()

    /** 当前核心实例；未创建时为 null。 */
    val core: Core?
        get() = coreRef.value

    var mediaProjection: PlatformMediaProjection? = null

    /** 发送队列是否已就绪（由 [startCore] 建立）。 */
    val senderQueueReady: Boolean
        get() = coreRef.value?.isSenderQueueReady() ?: false

    /** 创建核心实例；重复调用返回既有实例。返回是否已就绪。 */
    fun createContext(): Boolean {
        if (coreRef.value == null) {
            synchronized(coreLock) {
                if (coreRef.value == null) {
                    coreRef.value = Core()
                }
            }
        }
        return coreRef.value != null
    }

    /** 绑定是否已初始化。 */
    fun isReady(): Boolean = coreRef.value != null

    /** UniFFI ABI 版本（绑定链路自检用）。 */
    fun getAbiProbe(): String = abiProbe()

    // ======== ECDH ========

    fun generateKeypair(): Boolean = coreRef.value?.ecdhGenerateKeypair() ?: false

    fun getPublicKey(): String? = coreRef.value?.ecdhGetPublicKey()

    fun hasKeypair(): Boolean = coreRef.value?.ecdhHasKeypair() ?: false

    fun deriveSharedSecret(
        peerUuid: String,
        peerPubKey: String,
    ): Boolean = coreRef.value?.ecdhDeriveSharedSecret(peerUuid, peerPubKey) ?: false

    fun migrateSharedSecret(
        deviceUuid: String,
        secret: ByteArray,
    ): Boolean = coreRef.value?.migrateSharedSecret(deviceUuid, secret) ?: false

    fun removeDevice(deviceUuid: String): Boolean = coreRef.value?.removeDevice(deviceUuid) ?: false

    // ======== State ========

    fun exportState(): String? = coreRef.value?.exportState()

    fun importState(json: String): Boolean = coreRef.value?.importState(json) ?: false

    fun encryptLocalState(
        plaintext: String,
        deviceUuid: String,
    ): String? = coreRef.value?.encryptLocalState(plaintext, deviceUuid)

    fun decryptLocalState(
        encryptedB64: String,
        deviceUuid: String,
    ): String? = coreRef.value?.decryptLocalState(encryptedB64, deviceUuid)

    /**
     * 本地状态反序列化聚合入口：解密与导入由 core 内部配对完成。
     *
     * @param encrypted 0=payload 为明文状态 JSON，其它=payload 为密文
     * @return true 表示状态已导入
     */
    fun deserializeLocalState(
        payload: String,
        deviceUuid: String,
        encrypted: Int,
    ): Boolean {
        val c = coreRef.value ?: return false
        val result = c.deserializeLocalState(payload, deviceUuid, encrypted != 0)
        if (result.isEmpty()) return false
        return try {
            nativeCoreJson.parseToJsonElement(result).jsonObject["ok"].asBooleanOrFalse()
        } catch (e: Exception) {
            false
        }
    }

    fun exportDeviceKey(deviceUuid: String): String? = coreRef.value?.exportDeviceKey(deviceUuid)

    // ======== Persistence（Rust 私有库：uuid/密钥由 Rust 持有）=======

    fun getLocalUuid(): String? = coreRef.value?.getLocalUuid()

    fun renameDevice(
        deviceUuid: String,
        name: String,
    ): Boolean = coreRef.value?.renameDevice(deviceUuid, name) ?: false

    // ======== Process ========

    fun periodicBroadcast(
        action: Int,
        uuid: String? = null,
        name: String? = null,
        battery: Int = -1,
        deviceType: String? = null,
    ): Int =
        coreRef.value?.periodicBroadcast(action, uuid ?: "", name ?: "", battery, deviceType ?: "")
            ?: -1

    // ======== Send functions ========

    fun sendHandshake(
        uuid: String,
        pubKey: String,
        localIp: String,
        targetIp: String,
        battery: Int,
        deviceType: String,
    ): Int = coreRef.value?.sendHandshake(uuid, localIp, targetIp, battery, deviceType) ?: -1

    fun connectDevice(
        uuid: String,
        targetIp: String,
        battery: Int,
        deviceType: String,
    ): Int = coreRef.value?.connectDevice(uuid, targetIp, battery, deviceType) ?: -1

    fun sendPairingInit(
        localUuid: String,
        targetUuid: String,
        expectedCode: String,
        battery: Int,
        deviceType: String,
    ): Int =
        coreRef.value?.sendPairingInit(localUuid, targetUuid, expectedCode, battery, deviceType)
            ?: -1

    fun sendPairingResp(
        uuid: String,
        ltPub: String,
        pairingCode: String,
        ip: String,
        battery: Int,
        deviceType: String,
    ): Int = coreRef.value?.sendPairingResp(uuid, ltPub, pairingCode, ip, battery, deviceType) ?: -1

    fun sendAccept(
        uuid: String,
        ltPubKey: String,
        ip: String,
        battery: Int,
        deviceType: String,
    ) {
        coreRef.value?.sendAccept(uuid, ltPubKey, ip, battery, deviceType)
    }

    fun sendReject(uuid: String) {
        coreRef.value?.sendReject(uuid)
    }

    // ======== Pairing code management (Rust-generated) ========

    fun generatePairingCode(ttlSecs: Int = 300): String? = coreRef.value?.generatePairingCode(ttlSecs.toUInt())

    fun clearPairingCode() {
        coreRef.value?.clearPairingCode()
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
        action: Int,
        dedupKey: String,
        arg1Ms: Long = 0L,
        arg2Ms: Long = 0L,
    ): Int = coreRef.value?.dedup(action, dedupKey, arg1Ms, arg2Ms) ?: -1

    // ======== 入站内容去重（10s 窗口；键为「应用名 + 标题 + 正文」，TTL 由 core 持有）========

    fun contentDedup(
        action: Int,
        appName: String,
        title: String,
        text: String,
    ): Int = coreRef.value?.contentDedup(action, appName, title, text) ?: -1

    // ======== Text similarity & dedup ========

    fun shouldDeduplicate(
        newAppName: String,
        newTitle: String,
        newText: String,
        oldAppName: String,
        oldTitle: String,
        oldText: String,
    ): Boolean =
        com.xzyht.notifyrelay.core.ffi
            .shouldDeduplicate(newAppName, newTitle, newText, oldAppName, oldTitle, oldText)

    // ======== Filter ========

    fun setFilterConfig(configJson: String): Int = coreRef.value?.setFilterConfig(configJson) ?: -1

    fun mapLocalPackage(pkg: String): String? = coreRef.value?.mapLocalPackage(pkg)

    fun checkFilterMode(
        mappedPkg: String,
        originalPkg: String,
        title: String,
        text: String,
    ): Boolean = (coreRef.value?.checkFilterMode(mappedPkg, originalPkg, title, text) ?: 1) != 0

    // ======== Network layer ========

    fun removeDeviceSession(uuid: String): Int = coreRef.value?.removeDeviceSession(uuid) ?: -1

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
        name: String,
        battery: Int,
        deviceType: String,
    ) {
        coreRef.value?.updateHeartbeatSchedulerParams(name, battery, deviceType)
    }

    // ======== Device state snapshot ========

    fun getDeviceList(
        authedTimeoutMs: Long,
        unauthedTimeoutMs: Long,
    ): String? = coreRef.value?.getDeviceList(authedTimeoutMs, unauthedTimeoutMs)

    // ======== Sender queue ========

    /** 入队：header 规范化、通知类发送去重（仅 DATA_NOTIFICATION）与 ts 注入由 core 内部完成 */
    fun enqueueMessage(
        deviceUuid: String,
        header: String,
        plaintext: String,
    ): Int {
        val c = coreRef.value ?: return -1
        if (!c.isSenderQueueReady()) return -1
        return if (c.enqueueMessage(deviceUuid, header, plaintext)) 1 else 0
    }

    // ======== Clipboard ========

    /**
     * 剪贴板内容变化入口。Rust 内部完成去重/防循环/频率限制/2MB 阈值判定并直接入队发送。
     * 返回 JSON：{"action": "sent"|"skipped"|"file_transfer", "reason": "..."}
     */
    fun clipboardOnChanged(
        targetsJson: String,
        mime: String,
        content: String,
        nowMs: Long,
        force: Boolean,
    ): String? = coreRef.value?.clipboardOnChanged(targetsJson, mime, content, nowMs, force)

    /**
     * 收到远程剪贴板报文入口。Rust 解析/归一化并登记防循环时间窗。
     * 返回 JSON：{"type": "text"|"image", "content": "..."}
     */
    fun clipboardOnReceived(
        payloadJson: String,
        nowMs: Long,
    ): String? = coreRef.value?.clipboardOnReceived(payloadJson, nowMs)

    // ======== App sync (app list & icons) ========

    /**
     * 批量过滤并构造图标请求报文（Rust 内部维护 pending 状态与超时清理）。
     * 返回 ICON_REQUEST 报文 JSON；无需请求时返回 {}。
     */
    fun appSyncPrepareIconRequest(
        packages: List<String>,
        installed: List<String>,
        cached: List<String>,
        appDeviceMap: Map<String, List<String>>,
        sourceDeviceUuid: String,
        nowMs: Long,
    ): String? {
        val c = coreRef.value ?: return null
        return c.appSyncPrepareIconRequest(
            toJsonArray(packages),
            toJsonArray(installed),
            toJsonArray(cached),
            toAppDeviceJson(appDeviceMap),
            sourceDeviceUuid,
            nowMs,
        )
    }

    /**
     * 图标请求聚合入口：先按同批包名解除旧登记，再构造图标请求报文。
     * 返回 ICON_REQUEST 报文 JSON；无需请求时返回 {}。
     */
    fun appSyncRequestIcons(
        packages: List<String>,
        installed: List<String>,
        cached: List<String>,
        appDeviceMap: Map<String, List<String>>,
        sourceDeviceUuid: String,
        nowMs: Long,
    ): String? {
        val c = coreRef.value ?: return null
        return c.appSyncRequestIcons(
            toJsonArray(packages),
            toJsonArray(installed),
            toJsonArray(cached),
            toAppDeviceJson(appDeviceMap),
            sourceDeviceUuid,
            nowMs,
        )
    }

    fun appSyncClearIconPending(packages: List<String>) {
        coreRef.value?.appSyncClearIconPending(toJsonArray(packages))
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
        deviceUuid: String,
        fullJson: String,
        isEnd: Boolean,
        isQuery: Boolean,
    ): Boolean = coreRef.value?.pushSuperislandState(deviceUuid, fullJson, isEnd, isQuery) ?: false

    /** 推送媒体全量状态；isQuery 为真表示响应状态查询。 */
    fun pushMediaState(
        deviceUuid: String,
        fullJson: String,
        isEnd: Boolean,
        isQuery: Boolean,
    ): Boolean = coreRef.value?.pushMediaState(deviceUuid, fullJson, isEnd, isQuery) ?: false

    // ======== Callbacks ========

    private var callbacksRef: CoreCallbacks? = null

    /** 注册全部回调（配对/数据/设备事件/状态查询/音频/日志）。 */
    fun setCallbacks(callbacks: CoreCallbacks) {
        callbacksRef = callbacks
        coreRef.value?.setCallbacks(callbacks)
    }

    // ======== Network change ========

    fun onNetworkChanged(localIp: String?) {
        coreRef.value?.onNetworkChanged(localIp ?: "")
    }

    // ======== Local IP ========

    fun getLocalIp(): String? =
        com.xzyht.notifyrelay.core.ffi
            .getLocalIp()

    // ======== Discovery ========

    fun addKnownDevice(
        uuid: String,
        ip: String,
    ) {
        coreRef.value?.addKnownDevice(uuid, ip)
    }

    fun removeKnownDevice(uuid: String) {
        coreRef.value?.removeKnownDevice(uuid)
    }

    // ======== Reconnect ========

    fun reconnectAddTarget(
        uuid: String,
        ip: String,
    ) {
        coreRef.value?.reconnectAddTarget(uuid, ip)
    }

    fun reconnectRemoveTarget(uuid: String) {
        coreRef.value?.reconnectRemoveTarget(uuid)
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
    ): Int = coreRef.value?.audioStart(direction, port, sampleRate, channels, remoteUuid) ?: -1

    fun audioWriteFrame(pcmData: ByteArray): Int = coreRef.value?.audioWriteFrame(pcmData) ?: -1

    fun audioStop(): Int = coreRef.value?.audioStop() ?: -1

    fun audioIsActive(): Boolean = (coreRef.value?.audioIsActive() ?: 0) != 0

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

    fun getGitHash(): String? = coreRef.value?.gitHash()

    // ======== Initialize core (统一启动 TCP、心跳、离线检测、发送队列、扫描、重连) ========

    fun startCore(
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
        val c = coreRef.value ?: return false
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
            AppLogger.e(TAG, "startCore 启动失败，返回句柄: $queue")
            return false
        }
        return true
    }
}

// ======== JSON 辅助（报文编码，与 Rust 侧约定一致）========

private val nativeCoreJson: Json = Json { ignoreUnknownKeys = true }

private fun toJsonArray(values: List<String>): String =
    buildJsonArray {
        values.forEach { value -> add(value) }
    }.toString()

private fun toAppDeviceJson(appDeviceMap: Map<String, List<String>>): String =
    buildJsonObject {
        appDeviceMap.forEach { (packageName, deviceUuids) ->
            put(packageName, buildJsonArray { deviceUuids.forEach { uuid -> add(uuid) } })
        }
    }.toString()

private fun JsonElement?.asBooleanOrFalse(): Boolean = (this as? JsonPrimitive)?.booleanOrNull ?: false
