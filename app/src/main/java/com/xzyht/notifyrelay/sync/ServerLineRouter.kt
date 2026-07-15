package com.xzyht.notifyrelay.sync

import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.nativecore.NativeCore
import notifyrelay.base.util.Logger
import java.io.BufferedReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.Socket

/**
 * 服务端首行协议路由器
 *
 * 所有协议消息统一走 Rust [NativeCore.processLine] 分发：
 * - DATA 消息：Rust 解密后通过注册的 DATA 回调分发
 * - 非 DATA 消息：Rust 解码后通过注册的非 DATA 回调分发（携带结构化参数字段）
 *
 * 回调执行期间通过 [sessionLocal] 传递 TCP 会话上下文。
 */
object ServerLineRouter {

    private const val TAG = "配对"

    // ==================== 回调线程上下文传递 ====================

    /** 供 Rust 回调线程获取当前 TCP 会话上下文 */
    data class SessionContext(
        val client: Socket,
        val reader: BufferedReader,
        val deviceManager: DeviceConnectionManager
    )

    private val sessionLocal = object : ThreadLocal<SessionContext>() {}

    /** Rust 回调从中获取当前会话上下文 */
    fun getSessionContext(): SessionContext? = sessionLocal.get()

    // ==================== 统一路由入口 ====================

    /**
     * 统一路由：所有消息类型均走 Rust [NativeCore.processLine]。
     * Rust 上下文不可用时记录警告（不再单独回落处理，统一走 Rust）。
     *
     * 回调执行期间通过 [sessionLocal] 传递 TCP 会话上下文。
     */
    fun routeLine(
        line: String,
        client: Socket,
        reader: BufferedReader,
        deviceManager: DeviceConnectionManager
    ) {
        val ctx = deviceManager.rustContextInternal
        if (ctx == null) {
            Logger.w(TAG, "Rust 上下文不可用，无法处理消息: $line")
            return
        }

        sessionLocal.set(SessionContext(client, reader, deviceManager))
        try {
            val result = NativeCore.processLine(ctx, line)
            if (result == -1) {
                Logger.w(TAG, "processLine 处理失败: $line")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "processLine 异常", e)
        } finally {
            sessionLocal.remove()
            try { reader.close() } catch (_: Exception) {}
            try { client.close() } catch (_: Exception) {}
        }
    }

    internal fun getLocalIpAddress(deviceManager: DeviceConnectionManager): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress ?: "0.0.0.0"
                    }
                }
            }
            "0.0.0.0"
        } catch (_: Exception) {
            "0.0.0.0"
        }
    }
}
