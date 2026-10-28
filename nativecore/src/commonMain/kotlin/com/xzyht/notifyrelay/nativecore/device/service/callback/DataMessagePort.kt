package com.xzyht.notifyrelay.nativecore.device.service.callback

import notifyrelay.domain.feature.device.model.DeviceInfo

/**
 * 入站数据报文的业务处理端口。
 *
 * core 通过 `onData` 上抛的报文按类型分派到各业务能力；
 * 平台侧实现后注入，桌面端等无对应能力的平台给出空实现。
 */
interface DataMessagePort {
    /** 通知报文。 */
    fun onNotification(
        deviceUuid: String,
        payload: String,
    )

    /** 媒体播放状态报文。 */
    fun onMediaPlay(
        device: DeviceInfo,
        payload: String,
    )

    /** 图标请求。 */
    fun onIconRequest(
        device: DeviceInfo,
        payload: String,
    )

    /** 图标响应。 */
    fun onIconResponse(payload: String)

    /** 应用列表请求。 */
    fun onAppListRequest(
        device: DeviceInfo,
        payload: String,
    )

    /** 应用列表响应。 */
    fun onAppListResponse(
        deviceUuid: String,
        payload: String,
    )

    /** 媒体控制指令（播放/暂停/切歌/音频中继）。 */
    fun onMediaControl(
        deviceUuid: String,
        payload: String,
    )

    /** FTP 控制指令。 */
    fun onFtp(
        deviceUuid: String,
        payload: String,
    )

    /** 剪贴板报文。 */
    fun onClipboard(payload: String)

    /** 状态报文。 */
    fun onStatus(
        deviceUuid: String,
        payload: String,
    )

    /** 请求启动对端应用。 */
    fun onAppLaunch(
        device: DeviceInfo,
        payload: String,
    )

    /** 超级岛报文。 */
    fun onSuperIsland(
        deviceUuid: String,
        payload: String,
    )
}

/** [DataMessagePort] 的空实现：所有报文直接忽略。 */
object NoopDataMessagePort : DataMessagePort {
    override fun onNotification(
        deviceUuid: String,
        payload: String,
    ) = Unit

    override fun onMediaPlay(
        device: DeviceInfo,
        payload: String,
    ) = Unit

    override fun onIconRequest(
        device: DeviceInfo,
        payload: String,
    ) = Unit

    override fun onIconResponse(payload: String) = Unit

    override fun onAppListRequest(
        device: DeviceInfo,
        payload: String,
    ) = Unit

    override fun onAppListResponse(
        deviceUuid: String,
        payload: String,
    ) = Unit

    override fun onMediaControl(
        deviceUuid: String,
        payload: String,
    ) = Unit

    override fun onFtp(
        deviceUuid: String,
        payload: String,
    ) = Unit

    override fun onClipboard(payload: String) = Unit

    override fun onStatus(
        deviceUuid: String,
        payload: String,
    ) = Unit

    override fun onAppLaunch(
        device: DeviceInfo,
        payload: String,
    ) = Unit

    override fun onSuperIsland(
        deviceUuid: String,
        payload: String,
    ) = Unit
}
