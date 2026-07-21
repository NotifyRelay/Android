package com.xzyht.notifyrelay.nativecore

import android.util.Log
import com.sun.jna.Pointer

object NativeCore {
    private const val TAG = "NativeCore"
    val lib = NotifyRelayCore.instance()

    private var _rustContext: Pointer? = null

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
    fun destroyContext(ctx: Pointer) {
        lib.nrc_destroy(ctx)
        if (_rustContext == ctx) _rustContext = null
        senderQueuePtr = 0L
        offlineDetectorHandle = 0L
        reconnectStatePtr = 0L
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

    // ======== Encrypt (sending data) ========
    fun encryptMessage(ctx: Pointer, header: String, localUuid: String, localPubKey: String, remoteUuid: String, plaintext: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_encrypt_message(ctx, header, localUuid, localPubKey, remoteUuid, plaintext))

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
    fun processLine(ctx: Pointer, line: String): Int =
        lib.nrc_process_line(ctx, line)

    fun periodicBroadcast(ctx: Pointer, action: Int, uuid: String? = null, name: String? = null, battery: Int = -1, deviceType: String? = null): Int =
        lib.nrc_periodic_broadcast(ctx, action, uuid, name, battery, deviceType)

    fun setUserData(ctx: Pointer, userData: Pointer) =
        lib.nrc_set_user_data(ctx, userData)

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

    fun validatePairingCode(ctx: Pointer, code: String): Int =
        lib.nrc_validate_pairing_code(ctx, code)

    fun sendHeartbeatUdp(ctx: Pointer, uuid: String, name: String, port: Short, battery: Int, deviceType: String) =
        lib.nrc_send_heartbeat_udp(ctx, uuid, name, port, battery, deviceType)

    fun sendDiscovery(ctx: Pointer, uuid: String, name: String, port: Short, battery: Int, deviceType: String) =
        lib.nrc_send_discovery(ctx, uuid, name, port, battery, deviceType)

    fun sendDataMessage(ctx: Pointer, header: String, localUuid: String, localPubKey: String, remoteUuid: String, plaintext: String) =
        lib.nrc_send_data_message(ctx, header, localUuid, localPubKey, remoteUuid, plaintext)

    // ======== Utility functions ========
    fun computeDedupKey(deviceUuid: String, data: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_compute_dedup_key(deviceUuid, data))

    fun computeFeatureId(superPkg: String, paramV2Raw: String, title: String, text: String, instanceId: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_compute_feature_id(superPkg, paramV2Raw, title, text, instanceId))

    fun computeFeatureIdSimple(packageName: String, title: String, text: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_compute_feature_id_simple(packageName, title, text))

    // ======== Dedup engine ========
    fun dedup(ctx: Pointer, action: Int, dedupKey: String, arg1Ms: Long = 0L, arg2Ms: Long = 0L): Int =
        lib.nrc_dedup(ctx, action, dedupKey, arg1Ms, arg2Ms)

    // ======== Text similarity & dedup ========
    fun textSimilarity(a: String, b: String): Double =
        lib.nrc_text_similarity(a, b)

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

    fun stopTcpServer(ctx: Pointer): Int =
        lib.nrc_stop_tcp_server(ctx)

    fun restartUdpListener(ctx: Pointer): Int =
        lib.nrc_restart_udp_listener(ctx)

    fun broadcastMessage(ctx: Pointer, message: String): Int =
        lib.nrc_broadcast_message(ctx, message)

    fun getConnectedDeviceCount(ctx: Pointer): Int =
        lib.nrc_get_connected_device_count(ctx)

    fun isDeviceConnected(ctx: Pointer, uuid: String): Boolean =
        lib.nrc_is_device_connected(ctx, uuid) != 0

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

    // ======== Heartbeat sender ========
    fun startHeartbeatSender(ctx: Pointer, uuid: String, name: String, battery: Int, deviceType: String, ip: String, intervalMs: Long, mode: Int): Long =
        lib.nrc_start_heartbeat_sender(ctx, uuid, name, battery, deviceType, ip, intervalMs, mode)

    fun updateHeartbeatParams(ctx: Pointer, handlePtr: Long, uuid: String, name: String, battery: Int, deviceType: String) =
        lib.nrc_update_heartbeat_params(ctx, handlePtr, uuid, name, battery, deviceType)

    fun stopHeartbeatSender(ctx: Pointer, handlePtr: Long) =
        lib.nrc_stop_heartbeat_sender(ctx, handlePtr)

    // ======== Offline detector ========
    fun startOfflineDetector(ctx: Pointer, timeoutSec: Long = 30, checkIntervalMs: Long = 3000): Long =
        lib.nrc_start_offline_detector(ctx, timeoutSec, checkIntervalMs)

    fun stopOfflineDetector(ctx: Pointer) =
        lib.nrc_stop_offline_detector(ctx)

    // ======== Sender queue ========
    fun createSenderQueue(ctx: Pointer): Long =
        lib.nrc_create_sender_queue(ctx)

    fun startSenderQueue(ctx: Pointer, queuePtr: Long) =
        lib.nrc_start_sender_queue(ctx, queuePtr)

    fun enqueueMessage(ctx: Pointer, queuePtr: Long, deviceUuid: String, header: String, plaintext: String, dedupKey: String? = null) =
        lib.nrc_enqueue_message(ctx, queuePtr, deviceUuid, header, plaintext, dedupKey)

    fun stopSenderQueue(ctx: Pointer, queuePtr: Long) =
        lib.nrc_stop_sender_queue(ctx, queuePtr)

    // ======== Diff ========
    fun computeSuperislandDiff(oldState: String, newState: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_compute_superisland_diff(oldState, newState))

    // ======== Network change ========
    fun onNetworkChanged(ctx: Pointer, localIp: String?) =
        lib.nrc_on_network_changed(ctx, localIp)

    // ======== Local IP ========
    fun getLocalIp(): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_get_local_ip())

    // ======== Discovery ========
    fun addKnownDevice(ctx: Pointer, uuid: String, ip: String) =
        lib.nrc_add_known_device(ctx, uuid, ip)

    fun removeKnownDevice(ctx: Pointer, uuid: String) =
        lib.nrc_remove_known_device(ctx, uuid)

    fun recordDiscoveredDevice(ctx: Pointer, uuid: String, name: String?, ip: String, port: Short, battery: Int, deviceType: String) =
        lib.nrc_record_discovered_device(ctx, uuid, name, ip, port, battery, deviceType)

    fun getDiscoveredDevices(ctx: Pointer): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_get_discovered_devices(ctx))

    fun startKnownDeviceScanner(ctx: Pointer) =
        lib.nrc_start_known_device_scanner(ctx)

    fun stopKnownDeviceScanner(ctx: Pointer) =
        lib.nrc_stop_known_device_scanner(ctx)

    // ======== Reconnect ========
    fun createReconnectState(ctx: Pointer): Long =
        lib.nrc_create_reconnect_state(ctx)

    fun reconnectAddTarget(ctx: Pointer, statePtr: Long, uuid: String, ip: String) =
        lib.nrc_reconnect_add_target(ctx, statePtr, uuid, ip)

    fun reconnectRemoveTarget(ctx: Pointer, statePtr: Long, uuid: String) =
        lib.nrc_reconnect_remove_target(ctx, statePtr, uuid)

    fun reconnectStart(ctx: Pointer, statePtr: Long, intervalSecs: Long = 10, maxRetries: Int = 5) =
        lib.nrc_reconnect_start(ctx, statePtr, intervalSecs, maxRetries)

    fun reconnectStop(ctx: Pointer, statePtr: Long) =
        lib.nrc_reconnect_stop(ctx, statePtr)

    // ======== Audio stream ========
    private var audioDataCallback: ((ByteArray, Int, Int) -> Unit)? = null
    private var audioDataCallbackRef: Any? = null
    private var audioEventCallbackRef: Any? = null

    fun registerAudioDataCallback(cb: (ByteArray, Int, Int) -> Unit) {
        audioDataCallback = cb
    }

    fun audioStart(direction: String, deviceIp: String, port: Int, sampleRate: Int, channels: Int, remoteUuid: String = ""): Int {
        val ctx = getContext() ?: return -1
        setupAudioCallbacks()
        return lib.nrc_audio_start(ctx, direction, deviceIp, port, sampleRate, channels, remoteUuid)
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

    fun stopNewFeatures(ctx: Pointer) {
        if (senderQueuePtr != 0L) {
            stopSenderQueue(ctx, senderQueuePtr)
            senderQueuePtr = 0L
        }
        if (offlineDetectorHandle != 0L) {
            stopOfflineDetector(ctx)
            offlineDetectorHandle = 0L
        }
        if (reconnectStatePtr != 0L) {
            reconnectStop(ctx, reconnectStatePtr)
            reconnectStatePtr = 0L
        }
    }
}
