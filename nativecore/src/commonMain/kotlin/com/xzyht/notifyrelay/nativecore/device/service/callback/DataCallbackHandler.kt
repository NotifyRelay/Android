package com.xzyht.notifyrelay.nativecore.device.service.callback

import notifyrelay.base.log.Logger

/**
 * 入站数据报文分发器。
 *
 * core 的 `onData` 回调运行在 JNA 附加线程，此处只做「按报文类型分派」，
 * 具体业务由 [DataMessagePort] 的实现方处理；桌面端等平台注入空实现即可。
 */
class DataCallbackHandler(
    private val host: DeviceCallbackHost,
    private val dataPort: DataMessagePort,
) {
    companion object {
        private const val TAG = "CoreCb"
    }

    /** 入站数据报文入口。 */
    fun handle(
        uuidStr: String,
        messageTypeStr: String,
        text: String,
    ) {
        val authed =
            synchronized(host.authenticatedDeviceTable) {
                host.authenticatedDeviceTable[uuidStr]?.isAccepted == true
            }
        Logger.d(TAG, "on_data: type=" + messageTypeStr + ", authed=" + authed + ", text_len=" + text.length)
        if (!authed && messageTypeStr != "DATA_UNKNOWN") return

        try {
            when (messageTypeStr) {
                "NOTIFICATION" -> dataPort.onNotification(uuidStr, text)
                "MEDIAPLAY" ->
                    host.resolveDevice(uuidStr)?.let { device ->
                        dataPort.onMediaPlay(device, text)
                    }
                "ICON_REQUEST" ->
                    host.resolveDevice(uuidStr)?.let { device ->
                        dataPort.onIconRequest(device, text)
                    }
                "ICON_RESPONSE" -> dataPort.onIconResponse(text)
                "APP_LIST_REQUEST" ->
                    host.resolveDevice(uuidStr)?.let { device ->
                        dataPort.onAppListRequest(device, text)
                    }
                "APP_LIST_RESPONSE" -> dataPort.onAppListResponse(uuidStr, text)
                "MEDIA_CONTROL" -> dataPort.onMediaControl(uuidStr, text)
                "FTP" -> dataPort.onFtp(uuidStr, text)
                "CLIPBOARD" -> dataPort.onClipboard(text)
                "STATUS" -> dataPort.onStatus(uuidStr, text)
                "APP_LAUNCH" ->
                    host.resolveDevice(uuidStr)?.let { device ->
                        dataPort.onAppLaunch(device, text)
                    }
                "SUPERISLAND" -> dataPort.onSuperIsland(uuidStr, text)
                else -> Logger.d(TAG, "未知DATA通道: type=" + messageTypeStr + ", uuid=" + uuidStr + ", size=" + text.length)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "on_data error: " + e.message)
        }
    }
}
