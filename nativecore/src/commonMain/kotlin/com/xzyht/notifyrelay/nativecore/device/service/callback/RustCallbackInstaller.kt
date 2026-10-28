package com.xzyht.notifyrelay.nativecore.device.service.callback

import com.xzyht.notifyrelay.nativecore.NativeCore

/**
 * 核心回调装配器。
 *
 * 把配对、数据、设备事件、状态查询、音频与日志回调统一注册为单个 [CoreCallbacks] 实现，
 * 并**持有该对象强引用**，防止被 GC 回收导致 core 侧悬空。
 */
object RustCallbackInstaller {
    /** 保持回调对象强引用，防止被 GC */
    private var adapterRef: CoreCallbacksAdapter? = null

    /**
     * 注册全部回调。
     *
     * @param host 回调宿主（提供状态与操作能力）
     * @param dataPort 入站数据报文的业务处理端口
     * @param heartbeatPort 心跳报文的业务处理端口
     */
    fun install(
        host: DeviceCallbackHost,
        dataPort: DataMessagePort = NoopDataMessagePort,
        heartbeatPort: HeartbeatMessagePort = NoopHeartbeatMessagePort,
    ) {
        val adapter =
            adapterRef ?: CoreCallbacksAdapter(host, dataPort, heartbeatPort).also { adapterRef = it }
        NativeCore.setCallbacks(adapter)
    }
}
