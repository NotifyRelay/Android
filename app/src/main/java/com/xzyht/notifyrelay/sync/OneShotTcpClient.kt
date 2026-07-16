package com.xzyht.notifyrelay.sync

import com.sun.jna.Pointer
import com.xzyht.notifyrelay.nativecore.NativeCore

object OneShotTcpClient {

    fun sendAndReceive(
        ctx: Pointer,
        ip: String,
        port: Int,
        payload: String,
        connectTimeoutMs: Int = 3000,
        readTimeoutMs: Int = 3000,
    ): Boolean {
        return NativeCore.oneshotSendReceive(ctx, ip, port.toShort(), payload, readTimeoutMs)
    }

    fun sendOnly(
        ctx: Pointer,
        ip: String,
        port: Int,
        payload: String,
        connectTimeoutMs: Int = 3000,
    ): Boolean {
        return NativeCore.oneshotSendOnly(ctx, ip, port.toShort(), payload, connectTimeoutMs)
    }
}
