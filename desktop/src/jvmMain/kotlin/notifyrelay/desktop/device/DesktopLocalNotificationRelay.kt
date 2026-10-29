package notifyrelay.desktop.device

import com.xzyht.notifyrelay.nativecore.device.notification.LocalNotificationForwardGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import notifyrelay.base.log.Logger
import notifyrelay.desktop.platform.DesktopNotificationCollector
import notifyrelay.desktop.platform.DesktopSessionLock
import notifyrelay.domain.feature.device.service.contract.DeviceAuthTablePort

/**
 * 桌面端本机通知转发装配。
 *
 * 把 [DesktopNotificationCollector] 采集到的通知交给公共网关
 * [LocalNotificationForwardGateway]：过滤、包名匹配、落库与广播均在该网关内完成。
 *
 * Windows 侧的应用标识为 AUMID（非打包 Win32 应用的 AUMID 往往就是应用名，
 * 如 QQ），因此传 `resolvePackage = true`，由网关按应用显示名反查安卓包名。
 */
class DesktopLocalNotificationRelay(
    /** 设备端口访问器（设备管理器在数据层之后创建）。 */
    private val deviceManager: () -> DeviceAuthTablePort?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var started = false

    /** 启动采集与转发。重复调用无副作用。 */
    fun start() {
        if (started) return
        started = true
        DesktopNotificationCollector.start { item ->
            val manager = deviceManager()
            if (manager == null) {
                Logger.w(TAG, "设备端口未就绪，丢弃本机通知: " + item.appName)
                return@start
            }
            // 叠加层弹幕：本机通知即时上屏
            notifyrelay.desktop.overlay.DesktopOverlayStore.addDanmaku(
                notifyrelay.desktop.overlay.DanmakuEntry(
                    id = item.id.hashCode().toLong(),
                    appName = item.appName,
                    title = item.title,
                    text = item.text,
                    deviceName = "本机",
                ),
            )
            scope.launch {
                runCatching {
                    LocalNotificationForwardGateway.forward(
                        notification =
                            LocalNotificationForwardGateway.LocalNotification(
                                id = item.id,
                                appName = item.appName,
                                // Windows 的 AUMID 常等于应用名，仅作无映射时的回落
                                rawPackage = item.appPackage,
                                title = item.title,
                                text = item.text,
                                // 按显示名反查安卓包名：AUMID 与安卓包名不同域
                                resolvePackage = true,
                                isLocked = DesktopSessionLock.isLocked(),
                            ),
                        deviceManager = manager,
                        persistLocally = true,
                    )
                }.onFailure { Logger.w(TAG, "本机通知转发失败", it) }
            }
        }
    }

    /** 停止采集。 */
    fun stop() {
        started = false
        DesktopNotificationCollector.stop()
    }

    private companion object {
        const val TAG = "DesktopNotifRelay"
    }
}
