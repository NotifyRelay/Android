package com.xzyht.notifyrelay.nativecore

import com.sun.jna.Pointer

object NativeCore {
    private val lib = NotifyRelayCore.instance()

    fun createContext(): Pointer = lib.nrc_init()
    fun destroyContext(ctx: Pointer) = lib.nrc_destroy(ctx)

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

    fun encryptMessage(ctx: Pointer, header: String, localUuid: String, localPubKey: String, remoteUuid: String, plaintext: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_encrypt_message(ctx, header, localUuid, localPubKey, remoteUuid, plaintext))

    fun decryptMessage(ctx: Pointer, encryptedLine: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_decrypt_message(ctx, encryptedLine))

    fun decodeLine(ctx: Pointer, line: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_decode_line(ctx, line))

    fun formatTcpHeartbeat(uuid: String, nameB64: String, port: Short, battery: Int, deviceType: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_format_tcp_heartbeat(uuid, nameB64, port, battery, deviceType))

    fun formatHeartbeat(uuid: String, nameB64: String, port: Short, battery: Int, deviceType: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_format_heartbeat(uuid, nameB64, port, battery, deviceType))

    fun formatDiscovery(uuid: String, nameB64: String, port: Short, battery: Int, deviceType: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_format_discovery(uuid, nameB64, port, battery, deviceType))

    fun parseHeartbeatJson(line: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_parse_heartbeat_json(line))

    fun parseHeartbeatTcpJson(line: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_parse_heartbeat_tcp_json(line))

    fun formatPairingInit(uuid: String, tmpPubKey: String, ip: String, battery: Int, deviceType: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_format_pairing_init(uuid, tmpPubKey, ip, battery, deviceType))

    fun formatPairingResp(uuid: String, tmpPub: String, ltPub: String, encryptedCode: String, ip: String, battery: Int, deviceType: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_format_pairing_resp(uuid, tmpPub, ltPub, encryptedCode, ip, battery, deviceType))

    fun formatAccept(uuid: String, ltPubKey: String, ip: String, battery: Int, deviceType: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_format_accept(uuid, ltPubKey, ip, battery, deviceType))

    fun formatHandshake(uuid: String, pubKey: String, ip: String, battery: Int, deviceType: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_format_handshake(uuid, pubKey, ip, battery, deviceType))

    fun exportState(ctx: Pointer): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_export_state(ctx))

    fun importState(ctx: Pointer, json: String): Boolean =
        lib.nrc_import_state(ctx, json) == 0

    fun encryptLocalState(ctx: Pointer, plaintext: String, deviceUuid: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_encrypt_local_state(ctx, plaintext, deviceUuid))

    fun decryptLocalState(ctx: Pointer, encryptedB64: String, deviceUuid: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_decrypt_local_state(ctx, encryptedB64, deviceUuid))

    // ======== New methods ========

    fun generateEphemeralKeypair(ctx: Pointer): Boolean =
        lib.nrc_ecdh_generate_ephemeral_keypair(ctx) == 0

    fun getEphemeralPublicKey(ctx: Pointer): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_ecdh_get_ephemeral_public_key(ctx))

    fun hasEphemeralKeypair(ctx: Pointer): Boolean =
        lib.nrc_ecdh_has_ephemeral_keypair(ctx) != 0

    fun clearEphemeralKeypair(ctx: Pointer) =
        lib.nrc_ecdh_clear_ephemeral_keypair(ctx)

    fun derivePairingKey(ctx: Pointer, peerEphPubB64: String): Boolean =
        lib.nrc_ecdh_derive_pairing_key(ctx, peerEphPubB64) == 0

    fun encryptPairingCode(ctx: Pointer, code: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_ecdh_encrypt_pairing_code(ctx, code))

    fun decryptPairingCode(ctx: Pointer, encryptedB64: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_ecdh_decrypt_pairing_code(ctx, encryptedB64))

    fun deriveLongTermKey(ctx: Pointer, peerUuid: String, peerLtPubB64: String): Boolean =
        lib.nrc_ecdh_derive_long_term_key(ctx, peerUuid, peerLtPubB64) == 0

    fun exportDeviceKey(ctx: Pointer, deviceUuid: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_export_device_key(ctx, deviceUuid))

    fun exportLocalKeypair(ctx: Pointer): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_export_local_keypair(ctx))

    fun processLine(ctx: Pointer, line: String): Int =
        lib.nrc_process_line(ctx, line)

    fun setUserData(ctx: Pointer, userData: Pointer) =
        lib.nrc_set_user_data(ctx, userData)
}
