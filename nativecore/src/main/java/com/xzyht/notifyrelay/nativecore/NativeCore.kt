package com.xzyht.notifyrelay.nativecore

import android.media.projection.MediaProjection
import android.util.Log
import com.sun.jna.Pointer
import notifyrelay.base.util.Logger as AppLogger
import org.json.JSONArray
import org.json.JSONObject

object NativeCore {
    private const val TAG = "NativeCore"
    val lib = NotifyRelayCore.instance()

    private var _rustContext: Pointer? = null
    var mediaProjection: MediaProjection? = null

    // 网络层新特性的内部状态
    var senderQueuePtr: Long = 0L
        private set
    var offlineDetectorHandle: Long = 0L
        private set
    var reconnectStatePtr: Long = 0L
        private set

    fun createContext(): Pointer {
        val ctx = lib.nrc_init()
        _rustContext = ctx
        return ctx
    }

    fun generateKeypair(ctx: Pointer): Boolean =
        lib.nrc_ecdh_generate_keypair(ctx) == 0

    fun getPublicKey(ctx: Pointer): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_ecdh_get_public_key(ctx))

    fun hasKeypair(ctx: Pointer): Boolean =
        lib.nrc_ecdh_has_keypair(ctx) != 0

    fun deriveSharedSecret(ctx: Pointer, peerUuid: String, peerPubKey: String): Boolean =
        lib.nrc_ecdh_derive_shared_secret(ctx, peerUuid, peerPubKey) == 0

    fun migrateSharedSecret(ctx: Pointer, deviceUuid: String, secret: ByteArray): Boolean =
        lib.nrc_migrate_shared_secret(ctx, deviceUuid, secret, secret.size) == 0

    fun removeDevice(ctx: Pointer, deviceUuid: String): Boolean =
        lib.nrc_remove_device(ctx, deviceUuid) == 0

    // ======== State ========
    fun exportState(ctx: Pointer): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_export_state(ctx))

    fun importState(ctx: Pointer, json: String): Boolean =
        lib.nrc_import_state(ctx, json) == 0

    fun encryptLocalState(ctx: Pointer, plaintext: String, deviceUuid: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_encrypt_local_state(ctx, plaintext, deviceUuid))

    fun decryptLocalState(ctx: Pointer, encryptedB64: String, deviceUuid: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_decrypt_local_state(ctx, encryptedB64, deviceUuid))

    fun exportDeviceKey(ctx: Pointer, deviceUuid: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_export_device_key(ctx, deviceUuid))

    // ======== Process ========
    fun periodicBroadcast(ctx: Pointer, action: Int, uuid: String? = null, name: String? = null, battery: Int = -1, deviceType: String? = null): Int =
        lib.nrc_periodic_broadcast(ctx, action, uuid, name, battery, deviceType)

    // ======== Send functions ========
    fun sendHandshake(ctx: Pointer, uuid: String, pubKey: String, localIp: String, targetIp: String, battery: Int, deviceType: String): Int =
        lib.nrc_send_handshake(ctx, uuid, pubKey, localIp, targetIp, battery, deviceType)

    fun sendPairingInit(ctx: Pointer, localUuid: String, targetUuid: String, expectedCode: String, battery: Int, deviceType: String): Int =
        lib.nrc_send_pairing_init(ctx, localUuid, targetUuid, expectedCode, battery, deviceType)

    fun sendPairingResp(ctx: Pointer, uuid: String, ltPub: String, pairingCode: String, ip: String, battery: Int, deviceType: String): Int =
        lib.nrc_send_pairing_resp(ctx, uuid, ltPub, pairingCode, ip, battery, deviceType)

    fun sendAccept(ctx: Pointer, uuid: String, ltPubKey: String, ip: String, battery: Int, deviceType: String) =
        lib.nrc_send_accept(ctx, uuid, ltPubKey, ip, battery, deviceType)

    fun sendReject(ctx: Pointer, uuid: String) =
        lib.nrc_send_reject(ctx, uuid)

    // ======== Pairing code management (Rust-generated) ========
    fun generatePairingCode(ctx: Pointer, ttlSecs: Int = 300): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_generate_pairing_code(ctx, ttlSecs))

    fun clearPairingCode(ctx: Pointer) =
        lib.nrc_clear_pairing_code(ctx)

    // ======== Utility functions ========
    fun computeDedupKey(deviceUuid: String, data: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_compute_dedup_key(deviceUuid, data))

    fun computeFeatureId(superPkg: String, paramV2Raw: String, title: String, text: String, instanceId: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_compute_feature_id(superPkg, paramV2Raw, title, text, instanceId))

    // ======== Dedup engine ========
    fun dedup(ctx: Pointer, action: Int, dedupKey: String, arg1Ms: Long = 0L, arg2Ms: Long = 0L): Int =
        lib.nrc_dedup(ctx, action, dedupKey, arg1Ms, arg2Ms)

    // ======== Text similarity & dedup ========
    fun shouldDeduplicate(newTitle: String, newText: String, oldTitle: String, oldText: String): Boolean =
        lib.nrc_should_deduplicate(newTitle, newText, oldTitle, oldText) != 0

    // ======== Filter ========
    fun setFilterConfig(ctx: Pointer, configJson: String): Int =
        lib.nrc_set_filter_config(ctx, configJson)

    fun mapLocalPackage(ctx: Pointer, pkg: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_map_local_package(ctx, pkg))

    fun checkFilterMode(ctx: Pointer, mappedPkg: String, originalPkg: String, title: String, text: String): Boolean =
        lib.nrc_check_filter_mode(ctx, mappedPkg, originalPkg, title, text) != 0

    fun filterNotification(ctx: Pointer, pkg: String, title: String, text: String): Boolean =
        lib.nrc_filter_notification(ctx, pkg, title, text) != 0

    // ======== Network layer ========
    fun startTcpServer(ctx: Pointer, port: Short): Int =
        lib.nrc_start_tcp_server(ctx, port)

    fun removeDeviceSession(ctx: Pointer, uuid: String): Int =
        lib.nrc_remove_device_session(ctx, uuid)

    // ======== OneShot TCP (new signature) ========
    fun oneshotSendReceive(ctx: Pointer, ip: String, port: Short, payload: String, timeoutMs: Int = 5000): Boolean =
        lib.nrc_oneshot_send_receive(ctx, ip, port, payload, timeoutMs) == 0

    fun oneshotSendOnly(ctx: Pointer, ip: String, port: Short, payload: String, timeoutMs: Int = 5000): Boolean =
        lib.nrc_oneshot_send_only(ctx, ip, port, payload, timeoutMs) != 0

    // ======== FTP credential derivation ========
    fun deriveFtpCredentials(sharedSecretB64: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_derive_ftp_credentials(sharedSecretB64))

    fun derivePasswordHash(password: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_derive_password_hash(password))

    fun generateRandomPassword(): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_generate_random_password())

    // ======== Heartbeat scheduler (统一心跳调度) ========
    fun startHeartbeatScheduler(ctx: Pointer, uuid: String, name: String, battery: Int, deviceType: String, intervalMs: Long): Long =
        lib.nrc_start_heartbeat_scheduler(ctx, uuid, name, battery, deviceType, intervalMs)

    fun updateHeartbeatSchedulerParams(ctx: Pointer, name: String, battery: Int, deviceType: String) =
        lib.nrc_update_heartbeat_scheduler_params(ctx, name, battery, deviceType)

    // ======== Device state snapshot ========
    fun getDeviceList(ctx: Pointer, authedTimeoutMs: Long, unauthedTimeoutMs: Long): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_get_device_list(ctx, authedTimeoutMs, unauthedTimeoutMs))

    // ======== Offline detector ========
    fun startOfflineDetector(ctx: Pointer, timeoutSec: Long = 30, checkIntervalMs: Long = 3000): Long =
        lib.nrc_start_offline_detector(ctx, timeoutSec, checkIntervalMs)

    // ======== Sender queue ========
    fun createSenderQueue(ctx: Pointer): Long =
        lib.nrc_create_sender_queue(ctx)

    fun startSenderQueue(ctx: Pointer, queuePtr: Long) =
        lib.nrc_start_sender_queue(ctx, queuePtr)

    fun enqueueMessage(ctx: Pointer, queuePtr: Long, deviceUuid: String, header: String, plaintext: String, dedupKey: String? = null) =
        lib.nrc_enqueue_message(ctx, queuePtr, deviceUuid, header, plaintext, dedupKey)

    // ======== Clipboard ========
    /**
     * 剪贴板内容变化入口。Rust 内部完成去重/防循环/频率限制/2MB 阈值判定并直接入队发送。
     * 返回 JSON：{"action": "sent"|"skipped"|"file_transfer", "reason": "..."}
     */
    fun clipboardOnChanged(ctx: Pointer?, queuePtr: Long, targetsJson: String, mime: String, content: String, nowMs: Long, force: Boolean): String? {
        val c = ctx ?: return null
        return NotifyRelayCore.ptrToStringAndFree(lib.nrc_clipboard_on_changed(c, queuePtr, targetsJson, mime, content, nowMs, if (force) 1 else 0))
    }

    /**
     * 收到远程剪贴板报文入口。Rust 解析/归一化并登记防循环时间窗。
     * 返回 JSON：{"type": "text"|"image", "content": "..."}
     */
    fun clipboardOnReceived(ctx: Pointer?, payloadJson: String, nowMs: Long): String? {
        val c = ctx ?: return null
        return NotifyRelayCore.ptrToStringAndFree(lib.nrc_clipboard_on_received(c, payloadJson, nowMs))
    }

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
        nowMs: Long
    ): String? {
        val c = ctx ?: return null
        val appDeviceJson = JSONObject().apply {
            appDeviceMap.forEach { (k, v) -> put(k, JSONArray(v)) }
        }.toString()
        return NotifyRelayCore.ptrToStringAndFree(
            lib.nrc_app_sync_prepare_icon_request(c, JSONArray(packages).toString(), JSONArray(installed).toString(), JSONArray(cached).toString(), appDeviceJson, sourceDeviceUuid, nowMs)
        )
    }

    fun appSyncClearIconPending(ctx: Pointer?, packages: List<String>) {
        val c = ctx ?: return
        lib.nrc_app_sync_clear_icon_pending(c, JSONArray(packages).toString())
    }

    /** 解析图标响应报文，返回 {"icons":[...],"missing":[...]} */
    fun appSyncParseIconResponse(payload: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_app_sync_parse_icon_response(payload))

    /** 构造应用列表请求报文 */
    fun appSyncBuildApplistRequest(scope: String, nowMs: Long): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_app_sync_build_applist_request(scope, nowMs))

    /** 解析应用列表响应报文，返回 {"apps":[...],"scope":"..","total":N} */
    fun appSyncParseApplistResponse(payload: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_app_sync_parse_applist_response(payload))

    // 推送「全量」超级岛/媒体状态；Rust 内部计算差异、合并、ACK 与心跳，接收端经 on_data 回传全量。
    // isQuery：true=查询回调响应推送（心跳查询发现变更后由平台推送），false=正常主动推送。
    fun pushSuperislandState(ctx: Pointer?, queuePtr: Long, deviceUuid: String, fullJson: String, isEnd: Boolean, isQuery: Boolean = false) {
        val c = ctx ?: return
        lib.nrc_push_superisland_state(c, queuePtr, deviceUuid, fullJson, if (isEnd) 1 else 0, if (isQuery) 1 else 0)
    }

    fun pushMediaState(ctx: Pointer?, queuePtr: Long, deviceUuid: String, fullJson: String, isEnd: Boolean, isQuery: Boolean = false) {
        val c = ctx ?: return
        lib.nrc_push_media_state(c, queuePtr, deviceUuid, fullJson, if (isEnd) 1 else 0, if (isQuery) 1 else 0)
    }

    // 注册状态查询回调（Rust 心跳线程锁外调用，返回 0=不存在 / 1=存在无变更 / 2=存在有变更）
    private var stateQueryCallbackRef: Any? = null
    fun setOnStateQueryCallback(ctx: Pointer, cb: NotifyRelayCore.OnStateQueryCb?) {
        stateQueryCallbackRef = cb
        lib.nrc_set_on_state_query_cb(ctx, cb)
    }

    // ======== Network change ========
    fun onNetworkChanged(ctx: Pointer, localIp: String?) =
        lib.nrc_on_network_changed(ctx, localIp)

    // ======== Local IP ========
    fun getLocalIp(): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_get_local_ip())

    // ======== mDNS ========
    fun startMdnsAdvertiser(ctx: Pointer, uuid: String, name: String, port: Short, pubkey: String, deviceType: String, battery: Int): Int =
        lib.nrc_start_mdns_advertiser(ctx, uuid, name, port, pubkey, deviceType, battery)

    fun stopMdnsAdvertiser(ctx: Pointer): Int =
        lib.nrc_stop_mdns_advertiser(ctx)

    fun startMdnsDiscovery(ctx: Pointer): Int =
        lib.nrc_start_mdns_discovery(ctx)

    fun stopMdnsDiscovery(ctx: Pointer): Int =
        lib.nrc_stop_mdns_discovery(ctx)

    // ======== Discovery ========
    fun addKnownDevice(ctx: Pointer, uuid: String, ip: String) =
        lib.nrc_add_known_device(ctx, uuid, ip)

    fun removeKnownDevice(ctx: Pointer, uuid: String) =
        lib.nrc_remove_known_device(ctx, uuid)

    fun startKnownDeviceScanner(ctx: Pointer) =
        lib.nrc_start_known_device_scanner(ctx)

    // ======== Reconnect ========
    fun createReconnectState(ctx: Pointer): Long =
        lib.nrc_create_reconnect_state(ctx)

    fun reconnectAddTarget(ctx: Pointer, statePtr: Long, uuid: String, ip: String) =
        lib.nrc_reconnect_add_target(ctx, statePtr, uuid, ip)

    fun reconnectRemoveTarget(ctx: Pointer, statePtr: Long, uuid: String) =
        lib.nrc_reconnect_remove_target(ctx, statePtr, uuid)

    fun reconnectStart(ctx: Pointer, statePtr: Long, intervalSecs: Long = 10, maxRetries: Int = 5) =
        lib.nrc_reconnect_start(ctx, statePtr, intervalSecs, maxRetries)

    // ======== Audio stream ========
    private var audioDataCallback: ((ByteArray, Int, Int) -> Unit)? = null
    private var audioDataCallbackRef: Any? = null
    private var audioEventCallbackRef: Any? = null

    fun registerAudioDataCallback(cb: (ByteArray, Int, Int) -> Unit) {
        audioDataCallback = cb
    }

    fun audioStart(direction: String, port: Int, sampleRate: Int, channels: Int, remoteUuid: String = ""): Int {
        val ctx = getContext() ?: return -1
        setupAudioCallbacks()
        return lib.nrc_audio_start(ctx, direction, port, sampleRate, channels, remoteUuid)
    }

    fun audioWriteFrame(pcmData: ByteArray): Int {
        val ctx = getContext() ?: return -1
        return lib.nrc_audio_write_frame(ctx, pcmData, pcmData.size)
    }

    fun audioStop(): Int {
        val ctx = getContext() ?: return -1
        return lib.nrc_audio_stop(ctx)
    }

    fun audioIsActive(): Boolean {
        val ctx = getContext() ?: return false
        return lib.nrc_audio_is_active(ctx) != 0
    }

    private fun setupAudioCallbacks() {
        if (audioDataCallbackRef != null) return
        val dataCb = object : NotifyRelayCore.OnAudioDataCb {
            override fun invoke(deviceUuid: Pointer?, pcmData: Pointer?, pcmLen: Int, sampleRate: Int, channels: Int, userData: Pointer?) {
                if (pcmData == null || pcmLen <= 0) return
                val arr = pcmData.getByteArray(0, pcmLen)
                audioDataCallback?.invoke(arr, sampleRate, channels)
            }
        }
        val eventCb = object : NotifyRelayCore.OnAudioEventCb {
            override fun invoke(deviceUuid: Pointer?, event: Pointer?, errorMsg: Pointer?, userData: Pointer?) {
                val evt = event?.getString(0, "UTF-8") ?: "null"
                val err = errorMsg?.getString(0, "UTF-8") ?: ""
                Log.d(TAG, "音频事件: $evt, 错误: $err")
            }
        }
        val ctx = getContext() ?: return
        lib.nrc_register_audio_data_cb(ctx, dataCb)
        lib.nrc_register_audio_event_cb(ctx, eventCb)
        audioDataCallbackRef = dataCb
        audioEventCallbackRef = eventCb
    }

    fun setContext(ctx: Pointer?) { _rustContext = ctx }
    fun getContext(): Pointer? = _rustContext

    // ======== Log callback ========
    private var logCallbackRef: Any? = null

    fun setLogCallback(ctx: Pointer) {
        val cb = object : NotifyRelayCore.OnLogCb {
            override fun invoke(level: Int, message: Pointer?) {
                val msg = NotifyRelayCore.ptrToString(message) ?: return
                    when (level) {
                        1 -> AppLogger.e("Rust", msg)
                        2 -> AppLogger.w("Rust", msg)
                        3 -> AppLogger.i("Rust", msg)
                        4 -> AppLogger.d("Rust", msg)
                        5 -> AppLogger.v("Rust", msg)
                        else -> AppLogger.d("Rust", msg)
                    }
            }
        }
        lib.nrc_set_log_callback(cb)
        logCallbackRef = cb
    }

    // ======== Version ========
    fun getGitHash(): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_get_git_hash())

    // ======== Initialize new network features ========
    fun initializeNewFeatures(ctx: Pointer) {
        Log.i(TAG, "NotifyRelay Core loaded (git: ${getGitHash()})")
        if (senderQueuePtr == 0L) {
            senderQueuePtr = createSenderQueue(ctx)
            startSenderQueue(ctx, senderQueuePtr)
        }
        if (offlineDetectorHandle == 0L) {
            offlineDetectorHandle = startOfflineDetector(ctx)
        }
        if (reconnectStatePtr == 0L) {
            reconnectStatePtr = createReconnectState(ctx)
            reconnectStart(ctx, reconnectStatePtr)
        }
    }
}
