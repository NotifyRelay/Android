package notifyrelay.desktop.device

import com.xzyht.notifyrelay.nativecore.device.appsync.InboundNotificationProcessor
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
 * 与 Android 侧同语义：解析并落库通知历史，同时把原始报文归档到聊天记忆。
 * 落库后推送历史变更信号，使通知历史页刷新。
 */
internal object DesktopInboundNotificationHandler {
    private const val TAG = "DesktopNotification"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 处理一条入站通知报文；[remoteUuid] 为来源设备 UUID。 */
    fun dispatch(
        payload: String,
        remoteUuid: String,
    ) {
        scope.launch {
            try {
                InboundNotificationProcessor.process(payload, remoteUuid)
            } catch (e: IllegalStateException) {
                Logger.e(TAG, "解析入站通知失败: device=" + remoteUuid, e)
            }
            // 聊天记忆归档与落库结果无关：入站报文一律可见
            ChatMemory.append("收到: " + payload)
            (NotificationHistoryPortRegistry.current as? notifyrelay.desktop.platform.DesktopHistoryPort)?.notifyHistoryChanged()
        }
    }
}
