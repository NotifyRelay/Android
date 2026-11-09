package com.xzyht.notifyrelay.feature.appslist.sync

import android.content.Context
import com.xzyht.notifyrelay.nativecore.device.appsync.IconSyncManagerCore
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.device.service.contract.DeviceMessagePort

/**
 * 图标同步的平台入口。
 *
 * 同步流程本身（Rust 报文构造、请求/响应处理、字节编解码）在 [IconSyncManagerCore]；
 * 本 object 只提供 Android 侧的本机已安装集合与图标来源，并把入站回调转给 core。
 */
object IconSyncManager {
    private const val TAG = "IconSyncManager"

    /** 检查并（必要时）请求单个图标。 */
    fun checkAndSyncIcon(
        context: Context,
        packageName: String,
        deviceManager: DeviceMessagePort,
        sourceDevice: DeviceInfo,
    ) {
        IconSyncManagerCore.checkAndSyncIcon(packageName, deviceManager, sourceDevice)
    }

    /** 批量请求多个包名图标。 */
    suspend fun requestIconsBatch(
        context: Context,
        packageNames: List<String>,
        deviceManager: DeviceMessagePort,
        sourceDevice: DeviceInfo,
    ) {
        IconSyncManagerCore.requestIconsBatch(packageNames, deviceManager, sourceDevice)
    }

    /** 入站图标请求。 */
    fun handleIconRequest(
        requestData: String,
        deviceManager: DeviceMessagePort,
        sourceDevice: DeviceInfo,
        context: Context,
    ) {
        IconSyncManagerCore.onInboundIconRequest(requestData, deviceManager, sourceDevice)
    }

    /** 入站图标响应。 */
    fun handleIconResponse(
        responseData: String,
        context: Context,
    ) {
        IconSyncManagerCore.onInboundIconResponse(responseData)
    }
}
