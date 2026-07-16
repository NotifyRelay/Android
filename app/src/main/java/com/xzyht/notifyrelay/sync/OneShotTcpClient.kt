package com.xzyht.notifyrelay.sync

import com.xzyht.notifyrelay.nativecore.NativeCore

/**
 * 一次性 TCP 客户端工具类
 *
 * 封装「创建连接 → 发送报文 → 读取响应 → 关闭」的模板逻辑，
 * 消除 HandshakeSender / HeartbeatSender 中的重复 Socket 代码。
 *
 * 实际网络操作委托给 Rust Core (nrc_oneshot_send_receive / nrc_oneshot_send_only)。
 */
object OneShotTcpClient {

    fun sendAndReceive(
        ip: String,
        port: Int,
        payload: String,
        connectTimeoutMs: Int = 3000,
        readTimeoutMs: Int = 3000,
    ): String? {
        return NativeCore.oneshotSendReceive(ip, port.toShort(), payload, connectTimeoutMs, readTimeoutMs)
    }

    fun sendOnly(
        ip: String,
        port: Int,
        payload: String,
        connectTimeoutMs: Int = 3000,
    ): Boolean {
        return NativeCore.oneshotSendOnly(ip, port.toShort(), payload, connectTimeoutMs)
    }
}
