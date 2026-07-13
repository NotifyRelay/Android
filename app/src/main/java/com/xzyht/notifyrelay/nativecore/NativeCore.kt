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

    fun processLine(
        ctx: Pointer, line: String,
        onMessage: NotifyRelayCore.MessageCallback? = null,
        onPairing: NotifyRelayCore.PairingCallback? = null,
        userData: Pointer? = null
    ): Int = lib.nrc_process_line(ctx, line, onMessage, onPairing, userData ?: Pointer.NULL)

    fun exportState(ctx: Pointer): String? =
        NotifyRelayCore.ptrToStringAndFree(lib.nrc_export_state(ctx))

    fun importState(ctx: Pointer, json: String): Boolean =
        lib.nrc_import_state(ctx, json) == 0
}
