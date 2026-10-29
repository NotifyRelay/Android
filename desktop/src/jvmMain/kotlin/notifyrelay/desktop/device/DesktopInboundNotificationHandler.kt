package notifyrelay.desktop.device

import com.xzyht.notifyrelay.nativecore.device.appsync.IconSyncManagerCore
import com.xzyht.notifyrelay.nativecore.device.appsync.InboundNotificationProcessor
import com.xzyht.notifyrelay.nativecore.device.appsync.RemoteAppIconCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import notifyrelay.base.log.Logger
import notifyrelay.domain.feature.notification.chat.ChatMemory
import notifyrelay.domain.feature.notification.history.NotificationHistoryPortRegistry

/**
 * 桌面端入站通知处理。
 *
 * 与 Android 侧同语义：
 * - 解析并落库通知历史；
 * - 原始报文归档到聊天记忆；
 * - 来源应用图标缺失时向来源设备发起图标请求（与 Android 的 `checkAndSyncIcon` 同链路）；
 * - 落库后推送历史变更信号，使通知历史页刷新。
 */
internal class DesktopInboundNotificationHandler(
    private val deviceManager: DesktopDeviceManager,
) {
    private companion object {
        const val TAG = "DesktopNotification"
        const val DEFAULT_PORT = 23333
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 处理一条入站通知报文；[remoteUuid] 为来源设备 UUID。 */
    fun dispatch(
        payload: String,
        remoteUuid: String,
    ) {
        scope.launch {
            val record =
                try {
                    InboundNotificationProcessor.process(payload, remoteUuid)
                } catch (e: IllegalStateException) {
                    Logger.e(TAG, "解析入站通知失败: device=" + remoteUuid, e)
                    null
                }

            // 聊天记忆归档与落库结果无关：入站报文一律可见
            ChatMemory.append("收到: " + payload)

            // 图标缺失时向来源设备请求（与 Android 入站后 checkAndSyncIcon 同口径）
            if (record != null) {
                requestIconIfMissing(record.packageName, remoteUuid)
            }

            (NotificationHistoryPortRegistry.current as? notifyrelay.desktop.platform.DesktopHistoryPort)?.notifyHistoryChanged()
        }
    }

    /** 本机缓存无该包名图标时，向来源设备发起图标请求。 */
    private suspend fun requestIconIfMissing(
        packageName: String,
        remoteUuid: String,
    ) {
        if (packageName.isBlank()) return
        if (RemoteAppIconCache.cachedBytes(packageName) != null) return
        val sourceDevice = deviceManager.resolveDeviceInfo(remoteUuid, null, DEFAULT_PORT) ?: return
        try {
            IconSyncManagerCore.checkAndSyncIcon(packageName, deviceManager, sourceDevice)
        } catch (e: IllegalStateException) {
            Logger.w(TAG, "图标同步检查失败: " + packageName, e)
        }
    }
}
