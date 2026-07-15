package com.xzyht.notifyrelay.nativecore

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

interface NotifyRelayCore : Library {

    fun nrc_init(): Pointer
    fun nrc_destroy(ctx: Pointer)

    fun nrc_ecdh_generate_keypair(ctx: Pointer): Int
    fun nrc_ecdh_get_public_key(ctx: Pointer): Pointer
    fun nrc_ecdh_has_keypair(ctx: Pointer): Int
    fun nrc_ecdh_derive_shared_secret(ctx: Pointer, peerUuid: String, peerPubKeyB64: String): Int

    fun nrc_migrate_shared_secret(ctx: Pointer, deviceUuid: String, secret: ByteArray, len: Int): Int
    fun nrc_remove_device(ctx: Pointer, deviceUuid: String): Int

    fun nrc_encrypt_message(
        ctx: Pointer, header: String, localUuid: String,
        localPubKey: String, remoteUuid: String, plaintext: String
    ): Pointer

    fun nrc_decrypt_message(ctx: Pointer, encryptedLine: String): Pointer
    fun nrc_decode_line(ctx: Pointer, line: String): Pointer

    // ======== New: Ephemeral ECDH ========
    fun nrc_ecdh_generate_ephemeral_keypair(ctx: Pointer): Int
    fun nrc_ecdh_get_ephemeral_public_key(ctx: Pointer): Pointer
    fun nrc_ecdh_has_ephemeral_keypair(ctx: Pointer): Int
    fun nrc_ecdh_clear_ephemeral_keypair(ctx: Pointer)

    // ======== New: Pairing code ========
    fun nrc_ecdh_derive_pairing_key(ctx: Pointer, peerEphPubB64: String): Int
    fun nrc_ecdh_encrypt_pairing_code(ctx: Pointer, code: String): Pointer
    fun nrc_ecdh_decrypt_pairing_code(ctx: Pointer, encryptedB64: String): Pointer

    // ======== New: Long-term key alias ========
    fun nrc_ecdh_derive_long_term_key(ctx: Pointer, peerUuid: String, peerLtPubB64: String): Int

    // ======== New: Key export ========
    fun nrc_export_device_key(ctx: Pointer, deviceUuid: String): Pointer
    fun nrc_export_local_keypair(ctx: Pointer): Pointer

    // ======== New: Unified process ========
    fun nrc_process_line(ctx: Pointer, line: String): Int

    // ======== New: User data ========
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
        fun invoke(uuid: Pointer?, nameB64: Pointer?, port: Short, battery: Int, deviceType: Pointer?, ip: Pointer?, userData: Pointer?)
    }
    interface OnDiscoverManualCb : Callback {
        fun invoke(uuid: Pointer?, nameB64: Pointer?, port: Short, battery: Int, deviceType: Pointer?, userData: Pointer?)
    }
    interface OnDataCb : Callback {
        fun invoke(localUuid: Pointer?, plaintext: Pointer?, userData: Pointer?)
    }

    interface OnLogCb : Callback {
        fun invoke(level: Int, message: Pointer?)
    }

    // ======== Callback setters ========
    fun nrc_set_log_callback(cb: OnLogCb?)
    fun nrc_set_on_handshake_cb(ctx: Pointer, cb: OnHandshakeCb?)
    fun nrc_set_on_pairing_init_cb(ctx: Pointer, cb: OnPairingInitCb?)
    fun nrc_set_on_pairing_resp_cb(ctx: Pointer, cb: OnPairingRespCb?)
    fun nrc_set_on_accept_cb(ctx: Pointer, cb: OnAcceptCb?)
    fun nrc_set_on_reject_cb(ctx: Pointer, cb: OnRejectCb?)
    fun nrc_set_on_heartbeat_tcp_cb(ctx: Pointer, cb: OnHeartbeatTcpCb?)
    fun nrc_set_on_discover_manual_cb(ctx: Pointer, cb: OnDiscoverManualCb?)
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

    fun nrc_format_heartbeat(
        uuid: String, name: String, port: Short,
        battery: Int, deviceType: String
    ): Pointer

    fun nrc_parse_heartbeat(line: String): Pointer

    fun nrc_format_discovery(
        uuid: String, name: String, port: Short,
        battery: Int, deviceType: String
    ): Pointer

    fun nrc_format_tcp_heartbeat(
        uuid: String, name: String, port: Short,
        battery: Int, deviceType: String
    ): Pointer

    fun nrc_parse_heartbeat_json(line: String): Pointer
    fun nrc_parse_heartbeat_tcp_json(line: String): Pointer

    fun nrc_format_pairing_init(
        uuid: String, tmpPubKey: String, ip: String,
        battery: Int, deviceType: String
    ): Pointer

    fun nrc_format_pairing_resp(
        uuid: String, tmpPub: String, ltPub: String,
        encryptedCode: String, ip: String,
        battery: Int, deviceType: String
    ): Pointer

    fun nrc_format_accept(
        uuid: String, ltPubKey: String, ip: String,
        battery: Int, deviceType: String
    ): Pointer

    fun nrc_format_handshake(
        uuid: String, pubKey: String, ip: String,
        battery: Int, deviceType: String
    ): Pointer

    fun nrc_export_state(ctx: Pointer): Pointer
    fun nrc_import_state(ctx: Pointer, json: String): Int
    fun nrc_encrypt_local_state(ctx: Pointer, plaintext: String, deviceUuid: String): Pointer
    fun nrc_decrypt_local_state(ctx: Pointer, encryptedB64: String, deviceUuid: String): Pointer

    fun nrc_free_string(s: Pointer)

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
