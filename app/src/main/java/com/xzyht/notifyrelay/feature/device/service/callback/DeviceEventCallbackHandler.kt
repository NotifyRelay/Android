package com.xzyht.notifyrelay.feature.device.service.callback

import notifyrelay.base.util.Logger

/**
 * 设备生命周期与状态查询类 Rust 回调处理器：
 * `on_device_discovered` / `on_device_timeout` / `on_device_connected` /
 * `on_device_disconnected` / `on_state_query`。
 *
 * 关键约束：
 * - 这些回调运行在 Rust 扫描/心跳/连接线程上，同步调用 `nrc_get_device_list` 会与 core 重入，
 *   因此 `on_device_discovered` 通过 [DeviceCallbackHost.triggerDeviceListRefresh] 切到协程异步刷新。
 * - `on_device_timeout` / `on_device_connected` / `on_device_disconnected` 同样通过
 *   [DeviceCallbackHost.triggerDeviceListRefresh] 切到协程**异步**刷新，避免在 Rust 回调线程同步调用 core 造成重入。
 * - `on_state_query` 是唯一必须**同步**返回 0/1/2 的回调，
 *   因此直接委托给 [DeviceCallbackHost.stateQueryResponder]。
 *
 * 时序：
 * ```mermaid
 * sequenceDiagram
 *     participant Core as Rust Core 线程
 *     participant H as DeviceEventCallbackHandler
 *     participant Host as DeviceCallbackHost
 *     participant Store as 设备快照刷新
 *
 *     Core->>H: on_device_discovered / timeout / connected / disconnected
 *     H->>Host: triggerDeviceListRefresh()（异步，禁止同步调 core）
 *     Host->>Store: 协程内 nrc_get_device_list
 *     Core->>H: on_state_query（必须同步返回）
 *     H->>Host: stateQueryResponder.handle()
 *     Host-->>Core: 0=不存在 / 1=无变更 / 2=有变更
 * ```
 */
class DeviceEventCallbackHandler(
    private val host: DeviceCallbackHost,
) {
    companion object {
        private const val TAG = "CoreCb"
    }

    private val lastDiscovered =
        java.util.concurrent.ConcurrentHashMap<String, String>()

    /** TCP 扫描发现：core 已完成自身过滤/名称解码/状态登记，平台端仅触发一次快照刷新。 */
    fun handleDiscovered(
        remoteUuid: String,
        nameB64: String,
        port: Int,
        battery: Int,
        deviceType: String,
        remoteIp: String,
    ) {
        try {
            // 仅在发现内容实际变化时打印（2s 广播周期会重复上抛同一内容）
            val signature = "$remoteIp:$port:$battery"
            if (lastDiscovered.put(remoteUuid, signature) != signature) {
                Logger.d(
                    "死神-NotifyRelay",
                    "[on_device_discovered] uuid=$remoteUuid, ip=$remoteIp, port=$port, battery=$battery",
                )
            }
            // 运行在 Rust 扫描线程：不得同步调用设备快照接口（会与 core 重入），
            // 交由协程异步刷新
            host.triggerDeviceListRefresh()
        } catch (e: Exception) {
            Logger.e(TAG, "on_device_discovered error", e)
        }
    }

    /** 心跳超时：重新登记重连目标并刷新快照，让 UI 立即反映离线状态。 */
    fun handleTimeout(uuidStr: String) {
        val auth = synchronized(host.authenticatedDeviceTable) { host.authenticatedDeviceTable[uuidStr] }
        if (auth != null) host.registerReconnectTarget(uuidStr, auth.lastIp ?: "")
        host.triggerDeviceListRefresh()
    }

    /** TCP 连接建立：回填连接来源 IP 并刷新快照。 */
    fun handleConnected(
        uuidStr: String,
        ip: String,
    ) {
        if (uuidStr == host.localUuid) return
        host.triggerDeviceListRefresh()
    }

    /** TCP 断开：立即刷新快照，UI 无需等 12 秒离线超时。 */
    fun handleDisconnected(uuidStr: String) {
        if (uuidStr == host.localUuid) return
        host.triggerDeviceListRefresh()
    }

    /** TCP 层错误：记录日志（无额外平台动作）。 */
    fun handleTcpError(error: String) {
        Logger.w(TAG, "on_tcp_error: $error")
    }

    /** 超级岛/媒体心跳查询：必须同步返回 0/1/2。 */
    fun handleStateQuery(
        remoteUuid: String,
        featureIdStr: String,
        isMedia: Boolean,
    ): Int =
        try {
            host.stateQueryResponder.handle(remoteUuid, featureIdStr, isMedia)
        } catch (e: Exception) {
            Logger.e(TAG, "on_state_query error: ${e.message}")
            1 // 异常保守保活，等待下一次查询
        }
}
