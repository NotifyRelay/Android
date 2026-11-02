package com.xzyht.notifyrelay.nativecore.device.service.callback

/** 心跳报文的业务处理端口。 */
interface HeartbeatMessagePort {
    /**
     * 处理一条心跳报文。
     *
     * @param deviceUuid 对端设备 uuid
     * @param intValue 带符号电量（正=充电，负=放电）
     * @param payload 原始报文
     */
    fun onHeartbeat(
        deviceUuid: String,
        intValue: Int,
        payload: String,
    )
}

/** [HeartbeatMessagePort] 的空实现。 */
object NoopHeartbeatMessagePort : HeartbeatMessagePort {
    override fun onHeartbeat(
        deviceUuid: String,
        intValue: Int,
        payload: String,
    ) = Unit
}
