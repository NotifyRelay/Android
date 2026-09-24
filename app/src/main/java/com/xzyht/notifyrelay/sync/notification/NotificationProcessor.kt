package com.xzyht.notifyrelay.sync.notification

import android.content.Context
import com.xzyht.notifyrelay.feature.appslist.AppRepository
import com.xzyht.notifyrelay.feature.appslist.sync.IconSyncManager
import com.xzyht.notifyrelay.feature.device.model.NotificationRepository
import com.xzyht.notifyrelay.feature.device.repository.remoteNotificationFilter
import com.xzyht.notifyrelay.feature.device.repository.replicateNotification
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.notification.data.ChatMemory
import com.xzyht.notifyrelay.feature.notification.filter.BackendRemoteFilter
import com.xzyht.notifyrelay.feature.notification.filter.RemoteFilterConfig
import com.xzyht.notifyrelay.nativecore.NativeCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import notifyrelay.base.util.Logger
import notifyrelay.base.util.PermissionHelper
import notifyrelay.base.util.TextUtils
import org.json.JSONObject

/**
 * 远程通知处理管线（单条通知级别，不负责网络收发）。
 */
object NotificationProcessor {
    private const val TAG = "NotificationProcessor"

    data class NotificationInput(
        val header: String?,
        val rawData: String,
        val remoteUuid: String?,
    )

    fun process(
        context: Context,
        manager: DeviceConnectionManager,
        scope: CoroutineScope,
        input: NotificationInput,
        notificationCallbacks: Collection<(String) -> Unit>,
    ) {
        val (header, data, remoteUuid) = input

        // data 已由 ProtocolRouter 解密为明文，直接处理
        handleJsonLevel(context, manager, header, data, remoteUuid)

        handleFilterAndReplicate(context, manager, scope, data, remoteUuid)

        notificationCallbacks.forEach { callback ->
            try {
                callback.invoke(data)
            } catch (e: Exception) {
                Logger.e(TAG, "调用UI层回调失败: ${e.message}")
            }
        }
    }

    private fun handleJsonLevel(
        context: Context,
        manager: DeviceConnectionManager,
        header: String?,
        decrypted: String,
        remoteUuid: String?,
    ) {
        try {
            if (remoteUuid != null) {
                val parsedJson =
                    try {
                        NativeCore.parseNotificationInbound(decrypted)
                    } catch (_: Exception) {
                        null
                    }
                if (parsedJson == null) return
                val parsed = JSONObject(parsedJson)
                val pkg = parsed.optString("packageName")
                val appName = parsed.optString("appName")
                val title = parsed.optString("title")
                val text = parsed.optString("text")
                val timeRaw = parsed.optLong("time", 0L)
                val time = if (timeRaw == 0L) System.currentTimeMillis() else timeRaw

                val installedPkgs = AppRepository.getInstalledPackageNamesSync(context)
                val mappedPkg = RemoteFilterConfig.mapToLocalPackage(pkg.orEmpty(), installedPkgs)

                try {
                    NotificationRepository.addRemoteNotification(mappedPkg, appName, title, text, time, remoteUuid, context)
                    NotificationRepository.scanDeviceList(context)
                } catch (e: Exception) {
                    Logger.e(TAG, "存储远程通知失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "handleJsonLevel异常: ${e.message}")
        }
    }

    private fun handleFilterAndReplicate(
        context: Context,
        manager: DeviceConnectionManager,
        scope: CoroutineScope,
        decrypted: String,
        remoteUuid: String?,
    ) {
        val result = remoteNotificationFilter(decrypted, context)

        if (result.shouldShow) {
            val localIsLocked = PermissionHelper.isDeviceLocked(context)

            if (result.needsDelay && localIsLocked) {
                handleLockedScreenDelayed(context, scope, result)
                ChatMemory.append(context, "收到: ${result.rawData}")
            } else {
                scope.launch {
                    replicateNotification(context, result, null, startMonitoring = true)
                }

                if (remoteUuid != null) {
                    try {
                        val sourceDevice = manager.lookupDevice(remoteUuid)
                        if (sourceDevice != null) {
                            IconSyncManager.checkAndSyncIcon(
                                context,
                                result.mappedPkg,
                                manager,
                                sourceDevice,
                            )
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, "图标同步检查失败", e)
                    }
                }
            }
        } else {
            ChatMemory.append(context, "收到: ${result.rawData}")
        }
    }

    private fun handleLockedScreenDelayed(
        context: Context,
        scope: CoroutineScope,
        result: BackendRemoteFilter.FilterResult,
    ) {
        try {
            if (RemoteFilterConfig.enableDeduplication) {
                BackendRemoteFilter.addPlaceholder(result.title, result.text, result.mappedPkg, 15_000L)
            }
        } catch (_: Exception) {
        }

        scope.launch {
            try {
                val waitMs = 15_000L
                delay(waitMs)

                val localList = NotificationRepository.getNotificationsByDevice("本机")

                val normalizedPendingTitle = TextUtils.normalizeTitle(result.title)
                val pendingText = result.text
                val duplicateFound =
                    localList.any { nr ->
                        try {
                            nr.device == "本机" && TextUtils.normalizeTitle(nr.title) == normalizedPendingTitle && (nr.text ?: "") == pendingText
                        } catch (_: Exception) {
                            false
                        }
                    }

                if (!duplicateFound) {
                    val placeholderStillExists =
                        try {
                            BackendRemoteFilter.isPlaceholderPresent(result.title, result.text, result.mappedPkg)
                        } catch (e: Exception) {
                            true
                        }

                    if (!placeholderStillExists) {
                        // 占位被移除，跳过复刻
                    } else {
                        try {
                            replicateNotification(context, result, null, startMonitoring = false)
                        } catch (e: Exception) {
                            Logger.e("智能去重", "锁屏延迟复刻执行复刻时发生错误", e)
                        } finally {
                            try {
                                BackendRemoteFilter.removePlaceholderMatching(result.title, result.text, result.mappedPkg)
                            } catch (_: Exception) {
                            }
                        }
                    }
                } else {
                    try {
                        BackendRemoteFilter.removePlaceholderMatching(result.title, result.text, result.mappedPkg)
                    } catch (_: Exception) {
                    }
                }
            } catch (e: Exception) {
                Logger.e("智能去重", "锁屏延迟复刻异常", e)
                try {
                    BackendRemoteFilter.removePlaceholderMatching(result.title, result.text, result.mappedPkg)
                } catch (_: Exception) {
                }
            }
        }
    }
}
