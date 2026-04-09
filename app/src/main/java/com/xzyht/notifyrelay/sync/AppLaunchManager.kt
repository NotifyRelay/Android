package com.xzyht.notifyrelay.sync

import android.content.Context
import android.content.Intent
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import notifyrelay.base.util.Logger
import org.json.JSONObject

object AppLaunchManager {
    private const val TAG = "AppLaunchManager"

    fun handleAppLaunchRequest(
        decrypted: String,
        deviceManager: DeviceConnectionManager,
        source: DeviceInfo,
        context: Context
    ) {
        try {
            val json = JSONObject(decrypted)
            val action = json.optString("action", "")
            
            when (action) {
                "launchApp" -> {
                    val targetPackageName = json.getString("packageName")
                    Logger.i(TAG, "收到启动应用请求: $targetPackageName")
                    
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            Logger.i(TAG, "成功启动目标应用: $targetPackageName")
                        } else {
                            Logger.w(TAG, "无法获取目标应用的启动 Intent: $targetPackageName")
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, "启动目标应用失败: $targetPackageName", e)
                    }
                }
                else -> {
                    Logger.w(TAG, "未知的 action: $action")
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "处理应用启动请求失败", e)
        }
    }

    fun sendAppLaunchRequest(
        context: Context,
        deviceManager: DeviceConnectionManager,
        target: DeviceInfo,
        packageName: String
    ) {
        try {
            val json = JSONObject().apply {
                put("action", "launchApp")
                put("packageName", packageName)
            }
            Logger.d(TAG, "发送应用启动请求: $packageName")
            ProtocolSender.sendEncrypted(deviceManager, target, "DATA_APP_LAUNCH", json.toString())
        } catch (e: Exception) {
            Logger.e(TAG, "发送应用启动请求失败", e)
        }
    }
}
