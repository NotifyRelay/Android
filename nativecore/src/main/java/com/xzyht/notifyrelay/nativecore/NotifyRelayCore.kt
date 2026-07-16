package com.xzyht.notifyrelay.nativecore

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

interface NotifyRelayCore : Library {

    // ======== Lifecycle ========
    fun nrc_init(): Pointer
    fun nrc_destroy(ctx: Pointer)

    // ======== ECDH key management ========
    fun nrc_ecdh_generate_keypair(ctx: Pointer): Int
    fun nrc_ecdh_get_public_key(ctx: Pointer): Pointer
    fun nrc_ecdh_has_keypair(ctx: Pointer): Int
    fun nrc_ecdh_derive_shared_secret(ctx: Pointer, peerUuid: String, peerPubKeyB64: String): Int

    fun nrc_migrate_shared_secret(ctx: Pointer, deviceUuid: String, secret: ByteArray, len: Int): Int
    fun nrc_remove_device(ctx: Pointer, deviceUuid: String): Int

    // ======== Encrypt (for sending data) ========
    fun nrc_encrypt_message(
        ctx: Pointer, header: String, localUuid: String,
        localPubKey: String, remoteUuid: String, plaintext: String
    ): Pointer

    // ======== Unified process ========
    fun nrc_process_line(ctx: Pointer, line: String): Int

    // ======== User data ========
    fun nrc_set_user_data(ctx: Pointer, userData: Pointer)

    // ======== Callback interfaces ========
    interface OnHandshakeCb : Callback {
        fun invoke(uuid: Pointer?, pubKey: Pointer?, ip: Pointer?, battery: Int, deviceType: Pointer?, userData: Pointer?)
    }
    interface OnPairingInitCb : Callback {
        fun invoke(uuid: Pointer?, tmpPubKey: Pointer?, ip: Pointer?, battery: Int, deviceType: Pointer?, userData: Pointer?)
    }
    interface OnPairingRespCb : Callback {
        fun invoke(uuid: Pointer?, tmpPub: Pointer?, ltPub: Pointer?, encryptedCode: Pointer?, ip: Pointer?, battery: Int, deviceType: Pointer?, userData: Pointer?)
    }
    interface OnAcceptCb : Callback {
        fun invoke(uuid: Pointer?, ltPubKey: Pointer?, ip: Pointer?, battery: Int, deviceType: Pointer?, userData: Pointer?)
    }
    interface OnRejectCb : Callback {
        fun invoke(uuid: Pointer?, userData: Pointer?)
    }
    interface OnHeartbeatTcpCb : Callback {
        fun invoke(uuid: Pointer?, name: Pointer?, port: Short, battery: Int, deviceType: Pointer?, ip: Pointer?, userData: Pointer?)
    }
    interface OnDataCb : Callback {
        fun invoke(localUuid: Pointer?, plaintext: Pointer?, userData: Pointer?)
    }
    interface OnLogCb : Callback {
        fun invoke(level: Int, message: Pointer?)
    }
    interface OnHeartbeatUdpCb : Callback {
        fun invoke(uuid: Pointer?, name: Pointer?, port: Short, battery: Int, deviceType: Pointer?, userData: Pointer?)
    }
    interface OnDeviceTimeoutCb : Callback {
        fun invoke(uuid: Pointer?, userData: Pointer?)
    }
    interface OnPairingResultCb : Callback {
        fun invoke(uuid: Pointer?, success: Int, errorMsg: Pointer?, userData: Pointer?)
    }

    // ======== Device timeout ========
    fun nrc_set_on_device_timeout_cb(ctx: Pointer, cb: OnDeviceTimeoutCb?)

    // ======== Dedup engine ========
    fun nrc_dedup_check_and_pend(ctx: Pointer, dedupKey: String, ttlMs: Long): Int
    fun nrc_dedup_mark_sent(ctx: Pointer, dedupKey: String)
    fun nrc_dedup_clear_pending(ctx: Pointer, dedupKey: String)
    fun nrc_dedup_cleanup(ctx: Pointer, nowMs: Long, ttlMs: Long)

    // ======== Utility functions ========
    fun nrc_compute_dedup_key(deviceUuid: String, data: String): Pointer
    fun nrc_compute_feature_id(superPkg: String, paramV2Raw: String, title: String, text: String, instanceId: String): Pointer
    fun nrc_compute_feature_id_simple(packageName: String, title: String, text: String): Pointer

    // ======== Text similarity & dedup ========
    fun nrc_text_similarity(a: String, b: String): Double
    fun nrc_should_deduplicate(newTitle: String, newText: String, oldTitle: String, oldText: String): Int

    // ======== Filter ========
    fun nrc_set_filter_config(
        ctx: Pointer, filterMode: String, filterListJson: String,
        packageGroupsJson: String, groupEnabledJson: String, installedPkgsJson: String
    ): Int
    fun nrc_map_local_package(ctx: Pointer, pkg: String): Pointer
    fun nrc_check_filter_mode(ctx: Pointer, mappedPkg: String, originalPkg: String, title: String, text: String): Int
    fun nrc_filter_notification(ctx: Pointer, pkg: String, title: String, text: String): Pointer

    // ======== OneShot TCP client (new signature: ctx param, unified timeout, returns status) ========
    fun nrc_oneshot_send_receive(ctx: Pointer, ip: String, port: Short, payload: String, timeoutMs: Int): Int
    fun nrc_oneshot_send_only(ctx: Pointer, ip: String, port: Short, payload: String, timeoutMs: Int): Int

    // ======== FTP credential derivation ========
    fun nrc_derive_ftp_credentials(sharedSecretB64: String): Pointer
    fun nrc_derive_password_hash(password: String): Pointer
    fun nrc_generate_random_password(): Pointer

    // ======== Callback setters ========
    fun nrc_set_log_callback(cb: OnLogCb?)
    fun nrc_set_on_handshake_cb(ctx: Pointer, cb: OnHandshakeCb?)
    fun nrc_set_on_pairing_init_cb(ctx: Pointer, cb: OnPairingInitCb?)
    fun nrc_set_on_pairing_resp_cb(ctx: Pointer, cb: OnPairingRespCb?)
    fun nrc_set_on_accept_cb(ctx: Pointer, cb: OnAcceptCb?)
    fun nrc_set_on_reject_cb(ctx: Pointer, cb: OnRejectCb?)
    fun nrc_set_on_heartbeat_tcp_cb(ctx: Pointer, cb: OnHeartbeatTcpCb?)
    fun nrc_set_on_notification_cb(ctx: Pointer, cb: OnDataCb?)
    fun nrc_set_on_media_play_cb(ctx: Pointer, cb: OnDataCb?)
    fun nrc_set_on_icon_request_cb(ctx: Pointer, cb: OnDataCb?)
    fun nrc_set_on_icon_response_cb(ctx: Pointer, cb: OnDataCb?)
    fun nrc_set_on_app_list_request_cb(ctx: Pointer, cb: OnDataCb?)
    fun nrc_set_on_app_list_response_cb(ctx: Pointer, cb: OnDataCb?)
    fun nrc_set_on_media_control_cb(ctx: Pointer, cb: OnDataCb?)
    fun nrc_set_on_ftp_cb(ctx: Pointer, cb: OnDataCb?)
    fun nrc_set_on_clipboard_cb(ctx: Pointer, cb: OnDataCb?)
    fun nrc_set_on_status_cb(ctx: Pointer, cb: OnDataCb?)
    fun nrc_set_on_app_launch_cb(ctx: Pointer, cb: OnDataCb?)
    fun nrc_set_on_superisland_cb(ctx: Pointer, cb: OnDataCb?)
    fun nrc_set_on_unknown_data_cb(ctx: Pointer, cb: OnDataCb?)
    fun nrc_set_on_heartbeat_udp_cb(ctx: Pointer, cb: OnHeartbeatUdpCb?)
    fun nrc_set_on_pairing_result_cb(ctx: Pointer, cb: OnPairingResultCb?)

    // ======== Send functions ========
    fun nrc_send_handshake(ctx: Pointer, uuid: String, pubKey: String, localIp: String, targetIp: String, battery: Int, deviceType: String): Int
    fun nrc_send_pairing_init(ctx: Pointer, uuid: String, expectedCode: String, ip: String, battery: Int, deviceType: String): Int
    fun nrc_send_pairing_resp(ctx: Pointer, uuid: String, ltPub: String, pairingCode: String, ip: String, battery: Int, deviceType: String): Int
    fun nrc_send_accept(ctx: Pointer, uuid: String, ltPubKey: String, ip: String, battery: Int, deviceType: String)
    fun nrc_send_reject(ctx: Pointer, uuid: String)
    fun nrc_send_heartbeat_tcp(ctx: Pointer, uuid: String, name: String, port: Short, battery: Int, deviceType: String)
    fun nrc_send_heartbeat_udp(ctx: Pointer, uuid: String, name: String, port: Short, battery: Int, deviceType: String)
    fun nrc_send_discovery(ctx: Pointer, uuid: String, name: String, port: Short, battery: Int, deviceType: String)
    fun nrc_send_data_message(ctx: Pointer, header: String, localUuid: String, localPubKey: String, remoteUuid: String, plaintext: String)

    // ======== Periodic broadcast ========
    fun nrc_periodic_broadcast(
        ctx: Pointer, action: Int,
        uuid: String?, name: String?, battery: Int, deviceType: String?
    ): Int

    // ======== State persistence ========
    fun nrc_export_state(ctx: Pointer): Pointer
    fun nrc_import_state(ctx: Pointer, json: String): Int
    fun nrc_encrypt_local_state(ctx: Pointer, plaintext: String, deviceUuid: String): Pointer
    fun nrc_decrypt_local_state(ctx: Pointer, encryptedB64: String, deviceUuid: String): Pointer

    fun nrc_export_device_key(ctx: Pointer, deviceUuid: String): Pointer
    fun nrc_free_string(s: Pointer)

    // ======== Network layer ========
    fun nrc_start_tcp_server(ctx: Pointer, port: Short): Int
    fun nrc_stop_tcp_server(ctx: Pointer): Int
    fun nrc_restart_udp_listener(ctx: Pointer): Int
    fun nrc_send_to_device(ctx: Pointer, uuid: String, message: String): Int
    fun nrc_broadcast_message(ctx: Pointer, message: String): Int
    fun nrc_get_connected_device_count(ctx: Pointer): Int
    fun nrc_is_device_connected(ctx: Pointer, uuid: String): Int
    fun nrc_remove_device_session(ctx: Pointer, uuid: String): Int

    // ======== Heartbeat sender ========
    fun nrc_start_heartbeat_sender(ctx: Pointer, uuid: String, name: String, battery: Int, deviceType: String, ip: String, intervalMs: Long, mode: Int): Long
    fun nrc_update_heartbeat_params(ctx: Pointer, handlePtr: Long, uuid: String, name: String, battery: Int, deviceType: String)
    fun nrc_stop_heartbeat_sender(ctx: Pointer, handlePtr: Long)

    // ======== Offline detector ========
    fun nrc_start_offline_detector(ctx: Pointer, timeoutSec: Long, checkIntervalMs: Long): Long
    fun nrc_stop_offline_detector(ctx: Pointer)

    // ======== Sender queue ========
    fun nrc_create_sender_queue(ctx: Pointer): Long
    fun nrc_start_sender_queue(ctx: Pointer, queuePtr: Long)
    fun nrc_enqueue_message(ctx: Pointer, queuePtr: Long, deviceUuid: String, deviceIp: String, header: String, plaintext: String, dedupKey: String?)
    fun nrc_stop_sender_queue(ctx: Pointer, queuePtr: Long)

    // ======== Diff ========
    fun nrc_compute_superisland_diff(oldState: String, newState: String): Pointer

    // ======== Network change ========
    fun nrc_on_network_changed(ctx: Pointer, localIp: String?)

    // ======== Local IP ========
    fun nrc_get_local_ip(): Pointer

    // ======== Discovery ========
    fun nrc_add_known_device(ctx: Pointer, uuid: String, ip: String)
    fun nrc_remove_known_device(ctx: Pointer, uuid: String)
    fun nrc_record_discovered_device(ctx: Pointer, uuid: String, name: String?, ip: String, port: Short, battery: Int, deviceType: String)
    fun nrc_get_discovered_devices(ctx: Pointer): Pointer
    fun nrc_start_known_device_scanner(ctx: Pointer)
    fun nrc_stop_known_device_scanner(ctx: Pointer)

    // ======== Reconnect ========
    fun nrc_create_reconnect_state(ctx: Pointer): Long
    fun nrc_reconnect_add_target(ctx: Pointer, statePtr: Long, uuid: String, ip: String)
    fun nrc_reconnect_remove_target(ctx: Pointer, statePtr: Long, uuid: String)
    fun nrc_reconnect_start(ctx: Pointer, statePtr: Long, intervalSecs: Long, maxRetries: Int)
    fun nrc_reconnect_stop(ctx: Pointer, statePtr: Long)

    // ======== Network callbacks ========
    interface OnDeviceConnectedCb : Callback {
        fun invoke(uuid: Pointer?, ip: Pointer?, userData: Pointer?)
    }
    interface OnDeviceDisconnectedCb : Callback {
        fun invoke(uuid: Pointer?, userData: Pointer?)
    }
    interface OnTcpErrorCb : Callback {
        fun invoke(error: Pointer?, userData: Pointer?)
    }

    fun nrc_set_on_device_connected_cb(ctx: Pointer, cb: OnDeviceConnectedCb?)
    fun nrc_set_on_device_disconnected_cb(ctx: Pointer, cb: OnDeviceDisconnectedCb?)
    fun nrc_set_on_tcp_error_cb(ctx: Pointer, cb: OnTcpErrorCb?)

    companion object {
        private var _instance: NotifyRelayCore? = null

        fun instance(): NotifyRelayCore {
            if (_instance == null) {
                _instance = Native.load("notify_relay_core", NotifyRelayCore::class.java)
            }
            return _instance!!
        }

        fun ptrToStringAndFree(ptr: Pointer?): String? {
            if (ptr == null || Pointer.nativeValue(ptr) == 0L) return null
            val result = ptr.getString(0, "UTF-8")
            instance().nrc_free_string(ptr)
            return result
        }

        fun ptrToString(ptr: Pointer?): String? {
            if (ptr == null || Pointer.nativeValue(ptr) == 0L) return null
            return ptr.getString(0, "UTF-8")
        }
    }
}
