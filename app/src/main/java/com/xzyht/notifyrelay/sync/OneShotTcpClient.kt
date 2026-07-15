package com.xzyht.notifyrelay.sync

import notifyrelay.base.util.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 一次性 TCP 客户端工具类
 *
 * 封装「创建连接 → 发送报文 → 读取响应 → 关闭」的模板逻辑，
 * 消除 HandshakeSender / HeartbeatSender 中的重复 Socket 代码。
 */
object OneShotTcpClient {

    private const val TAG = "OneShotTcp"

    fun sendAndReceive(
        ip: String,
        port: Int,
        payload: String,
        connectTimeoutMs: Int = 3000,
        readTimeoutMs: Int = 3000,
    ): String? {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), connectTimeoutMs)
                socket.soTimeout = readTimeoutMs
                val writer = OutputStreamWriter(socket.getOutputStream())
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                writer.write(payload + "\n")
                writer.flush()
                reader.readLine()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "sendAndReceive 失败: $ip:$port", e)
            null
        }
    }

    fun sendOnly(
        ip: String,
        port: Int,
        payload: String,
        connectTimeoutMs: Int = 3000,
    ): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), connectTimeoutMs)
                val writer = OutputStreamWriter(socket.getOutputStream())
                writer.write(payload + "\n")
                writer.flush()
            }
            true
        } catch (e: Exception) {
            Logger.e(TAG, "sendOnly 失败: $ip:$port", e)
            false
        }
    }
}
