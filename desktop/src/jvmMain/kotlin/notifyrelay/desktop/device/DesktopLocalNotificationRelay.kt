package notifyrelay.desktop.device

import com.xzyht.notifyrelay.nativecore.device.appsync.InboundNotificationProcessor
import com.xzyht.notifyrelay.nativecore.device.appsync.RemoteAppIconCache
import com.xzyht.notifyrelay.nativecore.device.notification.LocalNotificationForwardGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                }.onSuccess { forwarded ->
                    // 弹幕只在确实转发出去时上屏：被过滤或已在库中的通知不再重复提示
                    if (!forwarded) return@onSuccess
                    // 图标统一走缓存：先落库，再与列表/历史页读同一来源
                    registerLocalApp(item)
                    val icon = cachedIcon(item)
                    withContext(Dispatchers.Main) {
                        notifyrelay.desktop.overlay.DesktopOverlayStore.addDanmaku(
                            notifyrelay.desktop.overlay.DanmakuEntry(
                                id = item.id.hashCode().toLong(),
                                appName = item.appName,
                                title = item.title,
                                text = item.text,
                                deviceName = "本机",
                                iconPng = icon,
                            ),
                        )
                    }
                }.onFailure { Logger.w(TAG, "本机通知转发失败", it) }
            }
        }
    }

    /**
     * 登记本机应用：应用名、包名与应用图标一并写入应用表。
     *
     * 包名优先取「应用名反查」结果，其次用采集到的原始标识；
     * 图标来自采集层的 `AppInfo.DisplayInfo.GetLogo`。
     */
    private suspend fun registerLocalApp(item: DesktopNotificationCollector.Item) {
        val packageName = LocalNotificationForwardGateway.resolvePackageName(item.appName) ?: item.appPackage
        if (packageName.isBlank()) return
        runCatching {
            RemoteAppIconCache.cache(
                packageName = packageName,
                pngBytes = item.iconPng,
                deviceUuid = InboundNotificationProcessor.localDevice(),
                appName = item.appName,
            )
        }.onFailure { Logger.w(TAG, "登记本机应用失败: " + packageName, it) }
    }

    /**
     * 取本机通知的应用图标字节。
     *
     * 与列表、通知历史、超级岛历史读同一来源：应用表里按包名缓存的图标字节。
     */
    private suspend fun cachedIcon(item: DesktopNotificationCollector.Item): ByteArray? {
        val byName = LocalNotificationForwardGateway.resolvePackageName(item.appName)
        return byName?.let { RemoteAppIconCache.cachedBytes(it) }
            ?: RemoteAppIconCache.cachedBytes(item.appPackage)
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
