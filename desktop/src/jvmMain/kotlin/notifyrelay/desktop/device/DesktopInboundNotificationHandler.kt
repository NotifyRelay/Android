package notifyrelay.desktop.device

import com.xzyht.notifyrelay.nativecore.device.appsync.IconSyncManagerCore
import com.xzyht.notifyrelay.nativecore.device.appsync.InboundNotificationProcessor
import com.xzyht.notifyrelay.nativecore.device.appsync.RemoteAppIconCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notifyrelay.base.log.Logger
import notifyrelay.data.database.entity.NotificationRecordEntity
import notifyrelay.desktop.overlay.DanmakuEntry
import notifyrelay.desktop.overlay.DesktopOverlayStore
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
            val inbound =
                try {
                    InboundNotificationProcessor.process(payload, remoteUuid)
                } catch (e: IllegalStateException) {
                    Logger.e(TAG, "解析入站通知失败: device=" + remoteUuid, e)
                    null
                }

            // 聊天记忆归档与落库结果无关：入站报文一律可见
            ChatMemory.append("收到: " + payload)

            // 远端通知上屏：与 Android 侧收到即展示同口径；已入库检定命中时只跳过上屏
            if (inbound != null) {
                val record = inbound.record
                // 登记应用（应用名 + 包名 + 图标）：清空历史后仍可用于列表与图标展示
                registerRemoteApp(record, remoteUuid)
                if (inbound.duplicate) {
                    Logger.d(TAG, "远端通知已入库，跳过弹幕: pkg=" + record.packageName)
                } else {
                    InboundNotificationProcessor.markContentSeen(
                        record.appName.orEmpty(),
                        record.title.orEmpty(),
                        record.text.orEmpty(),
                    )
                    // 图标取本地缓存（入站时同链路的图标请求可能尚未回包，无图标时不占位）
                    val icon = runCatching { RemoteAppIconCache.cachedBytes(record.packageName) }.getOrNull()
                    withContext(Dispatchers.Main) {
                        DesktopOverlayStore.addDanmaku(record.toDanmaku(remoteUuid, icon))
                    }
                }
                // 图标缺失时向来源设备请求（与 Android 入站后 checkAndSyncIcon 同口径）
                requestIconIfMissing(record.packageName, remoteUuid)
            }

            (NotificationHistoryPortRegistry.current as? notifyrelay.desktop.platform.DesktopHistoryPort)?.notifyHistoryChanged()
        }
    }

    /** 登记远端应用：应用名与包名写入应用表，并建立来源设备关联。 */
    private suspend fun registerRemoteApp(
        record: NotificationRecordEntity,
        remoteUuid: String,
    ) {
        if (record.packageName.isBlank()) return
        runCatching {
            RemoteAppIconCache.cache(
                packageName = record.packageName,
                pngBytes = null,
                deviceUuid = remoteUuid,
                appName = record.appName,
            )
        }.onFailure { Logger.w(TAG, "登记远端应用失败: " + record.packageName, it) }
    }

    /** 远端通知转弹幕：应用名为记录中的应用名，缺省回落到包名。 */
    private fun NotificationRecordEntity.toDanmaku(
        remoteUuid: String,
        icon: ByteArray?,
    ): DanmakuEntry =
        DanmakuEntry(
            id = key.hashCode().toLong(),
            appName = appName?.takeIf { it.isNotBlank() } ?: packageName,
            title = title.orEmpty(),
            text = text.orEmpty(),
            deviceName =
                notifyrelay.desktop.DataLayerDesktop
                    .findOnlineDevice(remoteUuid)
                    ?.displayName ?: remoteUuid,
            iconPng = icon,
        )

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
