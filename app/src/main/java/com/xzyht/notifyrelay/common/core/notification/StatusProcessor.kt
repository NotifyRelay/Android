package com.xzyht.notifyrelay.common.core.notification

import android.content.Context
import com.xzyht.notifyrelay.common.core.sync.MessageSender
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import kotlinx.coroutines.CoroutineScope
import notifyrelay.core.util.Logger
import notifyrelay.core.util.ToastUtils
import org.json.JSONObject

/**
 * 状态消息处理器
 * 处理DATA_STATUS消息的接收后逻辑
 * 响应消息用作tos是默认行为
 */
object StatusProcessor {

    private const val TAG = "StatusProcessor"

    /**
     * 处理状态消息输入
     */
    data class StatusInput(
        val header: String,
        val rawData: String,
        val sharedSecret: String,
        val remoteUuid: String
    )

    /**
     * 处理状态消息
     */
    fun process(
        context: Context,
        deviceManager: DeviceConnectionManager,
        coroutineScope: CoroutineScope,
        input: StatusInput,
        callbacks: Collection<(String) -> Unit>
    ) {
        try {
            Logger.d(TAG, "接收到DATA_STATUS消息: ${input.rawData}")

            val json = JSONObject(input.rawData)
            
            // 提取关键信息
            val originalHeader = json.optString("originalHeader", "")
            val requestId = json.optString("requestId", "")
            val result = json.optString("result", "")
            val errorCode = json.optString("errorCode", "")
            val errorMessage = json.optString("errorMessage", "")
            val action = json.optString("action", "")

            // 处理不同类型的状态响应
            when (originalHeader) {
                "DATA_FTP" -> {
                    // 处理FTP相关状态响应
                    handleFtpStatusResponse(json, deviceManager, input.remoteUuid)
                }
                "DATA_MEDIA_CONTROL" -> {
                    // 处理媒体控制相关状态响应
                    handleMediaControlStatusResponse(json, deviceManager, input.remoteUuid)
                }
                "DATA_SUPERISLAND" -> {
                    // 处理超级岛相关状态响应
                    handleSuperIslandStatusResponse(json, deviceManager, input.remoteUuid)
                }
                else -> {
                    // 处理其他类型的状态响应
                    Logger.d(TAG, "处理其他类型的状态响应: $originalHeader")
                }
            }

            // 触发回调
            callbacks.forEach {
                it(input.rawData)
            }
            
            val isSuperIslandAck = action == "SI_ACK"
            if (!isSuperIslandAck) {
                // 无论是错误还是正确，都显示响应中的消息信息
                // 确保在主线程中显示Toast
                android.os.Handler(context.mainLooper).post {
                    if (errorMessage.isNotEmpty()) {
                        ToastUtils.showShortToast(context, errorMessage)
                    } else if (result == "success") {
                        // 如果是成功响应，直接显示"成功"，这样后续只需要添加排除，不需要增加成功响应
                        ToastUtils.showShortToast(context, "成功")
                    }
                }
            }

        } catch (e: Exception) {
            Logger.e(TAG, "处理DATA_STATUS消息失败", e)
        }
    }

    /**
     * 处理FTP状态响应
     */
    private fun handleFtpStatusResponse(
        json: JSONObject,
        deviceManager: DeviceConnectionManager,
        remoteUuid: String
    ) {
        val action = json.optString("action", "")
        val result = json.optString("result", "")

        Logger.d(TAG, "处理FTP状态响应: action=$action, result=$result")

        // 根据需要处理不同的FTP状态响应
    }

    /**
     * 处理媒体控制状态响应
     */
    private fun handleMediaControlStatusResponse(
        json: JSONObject,
        deviceManager: DeviceConnectionManager,
        remoteUuid: String
    ) {
        val action = json.optString("action", "")
        val result = json.optString("result", "")

        Logger.d(TAG, "处理媒体控制状态响应: action=$action, result=$result")

        // 根据需要处理不同的媒体控制状态响应
    }

    /**
     * 处理超级岛状态响应
     */
    private fun handleSuperIslandStatusResponse(
        json: JSONObject,
        deviceManager: DeviceConnectionManager,
        remoteUuid: String
    ) {
        val action = json.optString("action", "")
        val result = json.optString("result", "")
        val hash = json.optString("hash", "")
        val featureKeyValue = json.optString("featureKeyValue", "")

        Logger.d(TAG, "处理超级岛状态响应: action=$action, result=$result")

        // 处理超级岛ACK，确保进入原有ack处理逻辑
        if (action == "SI_ACK" && hash.isNotEmpty()) {
            Logger.d(TAG, "处理超级岛ACK: device=$remoteUuid, feature=$featureKeyValue, hash=$hash")
            // 调用MessageSender.onSuperIslandAck方法，让确认包正常进入原有ack处理逻辑
            MessageSender.onSuperIslandAck(remoteUuid, featureKeyValue, hash)
        }

        // 根据需要处理其他类型的超级岛状态响应
    }

    /**
     * 发送状态响应
     */
    fun sendStatusResponse(
        deviceManager: DeviceConnectionManager,
        deviceInfo: com.xzyht.notifyrelay.feature.device.service.DeviceInfo,
        originalHeader: String,
        result: String,
        errorCode: String = "",
        errorMessage: String = "",
        requestId: String = ""
    ) {
        try {
            val responseJson = JSONObject().apply {
                put("originalHeader", originalHeader)
                put("result", result)
                if (errorCode.isNotEmpty()) {
                    put("errorCode", errorCode)
                }
                if (errorMessage.isNotEmpty()) {
                    put("errorMessage", errorMessage)
                }
                if (requestId.isNotEmpty()) {
                    put("requestId", requestId)
                }
            }

            com.xzyht.notifyrelay.common.core.sync.ProtocolSender.sendEncrypted(
                deviceManager,
                deviceInfo,
                "DATA_STATUS",
                responseJson.toString()
            )

            Logger.d(TAG, "发送DATA_STATUS响应: originalHeader=$originalHeader, result=$result")
        } catch (e: Exception) {
            Logger.e(TAG, "发送DATA_STATUS响应失败", e)
        }
    }
}
