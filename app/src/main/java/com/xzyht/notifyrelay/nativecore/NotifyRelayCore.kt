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

    fun nrc_process_line(
        ctx: Pointer, line: String,
        onMessage: MessageCallback?, onPairing: PairingCallback?,
        userData: Pointer
    ): Int

    fun nrc_format_heartbeat(
        uuid: String, name: String, port: Short,
        battery: Int, deviceType: String
    ): Pointer

    fun nrc_parse_heartbeat(line: String): Pointer

    fun nrc_format_discovery(
        uuid: String, name: String, port: Short,
        battery: Int, deviceType: String
    ): Pointer

    fun nrc_export_state(ctx: Pointer): Pointer
    fun nrc_import_state(ctx: Pointer, json: String): Int

    fun nrc_free_string(s: Pointer)

    interface MessageCallback : Callback {
        fun invoke(type: Pointer?, data: Pointer?, userData: Pointer?)
    }

    interface PairingCallback : Callback {
        fun invoke(type: Pointer?, uuid: Pointer?, pubKey: Pointer?, userData: Pointer?)
    }

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
    }
}
