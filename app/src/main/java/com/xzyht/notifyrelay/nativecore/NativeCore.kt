package com.xzyht.notifyrelay.nativecore

import com.sun.jna.Pointer

object NativeCore {
    internal val lib = NotifyRelayCore.instance()

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

    fun decryptPayload(ctx: Pointer, localUuid: String, encryptedB64: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_decrypt_payload(ctx, localUuid, encryptedB64))

    fun decodeLine(ctx: Pointer, line: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_decode_line(ctx, line))

    fun formatHandshake(uuid: String, pubKey: String, ip: String, battery: Int, deviceType: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_format_handshake(uuid, pubKey, ip, battery, deviceType))

    fun formatPairingInit(uuid: String, tmpPubKey: String, ip: String, battery: Int, deviceType: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_format_pairing_init(uuid, tmpPubKey, ip, battery, deviceType))

    fun formatPairingResp(uuid: String, tmpPub: String, ltPub: String, encryptedCode: String, ip: String, battery: Int, deviceType: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_format_pairing_resp(uuid, tmpPub, ltPub, encryptedCode, ip, battery, deviceType))

    fun formatAccept(uuid: String, ltPubKey: String, ip: String, battery: Int, deviceType: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_format_accept(uuid, ltPubKey, ip, battery, deviceType))

    fun formatHeartbeat(uuid: String, name: String, port: Short, battery: Int, deviceType: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_format_heartbeat(uuid, name, port, battery, deviceType))

    fun formatTcpHeartbeat(uuid: String, name: String, port: Short, battery: Int, deviceType: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_format_tcp_heartbeat(uuid, name, port, battery, deviceType))

    fun formatDiscovery(uuid: String, name: String, port: Short, battery: Int, deviceType: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_format_discovery(uuid, name, port, battery, deviceType))

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

    fun processUdpBroadcast(ctx: Pointer, line: String): Int =
        lib.nrc_process_udp_broadcast(ctx, line)

    fun setUserData(ctx: Pointer, userData: Pointer) =
        lib.nrc_set_user_data(ctx, userData)

    // ======== Send functions ========
    fun sendHandshake(ctx: Pointer, uuid: String, pubKey: String, ip: String, battery: Int, deviceType: String) =
        lib.nrc_send_handshake(ctx, uuid, pubKey, ip, battery, deviceType)

    fun sendPairingInit(ctx: Pointer, uuid: String, ip: String, battery: Int, deviceType: String) =
        lib.nrc_send_pairing_init(ctx, uuid, ip, battery, deviceType)

    fun sendPairingResp(ctx: Pointer, uuid: String, ltPub: String, pairingCode: String, ip: String, battery: Int, deviceType: String) =
        lib.nrc_send_pairing_resp(ctx, uuid, ltPub, pairingCode, ip, battery, deviceType)

    fun sendAccept(ctx: Pointer, uuid: String, ltPubKey: String, ip: String, battery: Int, deviceType: String) =
        lib.nrc_send_accept(ctx, uuid, ltPubKey, ip, battery, deviceType)

    fun sendReject(ctx: Pointer, uuid: String) =
        lib.nrc_send_reject(ctx, uuid)

    fun sendHeartbeatTcp(ctx: Pointer, uuid: String, name: String, port: Short, battery: Int, deviceType: String) =
        lib.nrc_send_heartbeat_tcp(ctx, uuid, name, port, battery, deviceType)

    fun sendHeartbeatUdp(ctx: Pointer, uuid: String, name: String, port: Short, battery: Int, deviceType: String) =
        lib.nrc_send_heartbeat_udp(ctx, uuid, name, port, battery, deviceType)

    fun sendDiscovery(ctx: Pointer, uuid: String, name: String, port: Short, battery: Int, deviceType: String) =
        lib.nrc_send_discovery(ctx, uuid, name, port, battery, deviceType)

    fun sendDataMessage(ctx: Pointer, header: String, localUuid: String, localPubKey: String, remoteUuid: String, plaintext: String) =
        lib.nrc_send_data_message(ctx, header, localUuid, localPubKey, remoteUuid, plaintext)

    // ======== New utility functions ========

    fun verifyPairingCode(storedCode: String, inputCode: String): Boolean =
        lib.nrc_verify_pairing_code(storedCode, inputCode) != 0

    fun computeDedupKey(deviceUuid: String, data: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_compute_dedup_key(deviceUuid, data))

    fun computeFeatureId(packageName: String, title: String, text: String): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_compute_feature_id(packageName, title, text))

    fun heartbeatTick(ctx: Pointer, timeoutSec: Long): Int =
        lib.nrc_heartbeat_tick(ctx, timeoutSec)

    // ======== Dedup engine ========
    fun dedupCheckAndPend(ctx: Pointer, dedupKey: String, ttlMs: Long): Boolean =
        lib.nrc_dedup_check_and_pend(ctx, dedupKey, ttlMs) != 0

    fun dedupMarkSent(ctx: Pointer, dedupKey: String) =
        lib.nrc_dedup_mark_sent(ctx, dedupKey)

    fun dedupClearPending(ctx: Pointer, dedupKey: String) =
        lib.nrc_dedup_clear_pending(ctx, dedupKey)

    fun dedupCleanup(ctx: Pointer, nowMs: Long, ttlMs: Long) =
        lib.nrc_dedup_cleanup(ctx, nowMs, ttlMs)

    // ======== Network layer ========

    fun startTcpServer(ctx: Pointer, port: Short): Int =
        lib.nrc_start_tcp_server(ctx, port)

    fun stopTcpServer(ctx: Pointer): Int =
        lib.nrc_stop_tcp_server(ctx)

    fun broadcastMessage(ctx: Pointer, message: String): Int =
        lib.nrc_broadcast_message(ctx, message)

    fun getConnectedDeviceCount(ctx: Pointer): Int =
        lib.nrc_get_connected_device_count(ctx)

    fun isDeviceConnected(ctx: Pointer, uuid: String): Boolean =
        lib.nrc_is_device_connected(ctx, uuid) != 0

    fun removeDeviceSession(ctx: Pointer, uuid: String): Int =
        lib.nrc_remove_device_session(ctx, uuid)
}
