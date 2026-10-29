package notifyrelay.desktop

import com.xzyht.notifyrelay.nativecore.device.appsync.IconCodecPortRegistry
import notifyrelay.data.Storage
import notifyrelay.data.database.AppDatabaseJvmFactory
import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.data.filter.RemoteFilterRepository
import notifyrelay.desktop.platform.DesktopAppIconPort
import notifyrelay.desktop.platform.DesktopAudioRelayPort
import notifyrelay.desktop.platform.DesktopFormFactorPort
import notifyrelay.desktop.platform.DesktopHistoryPort
import notifyrelay.desktop.platform.DesktopIconCodecPort
import notifyrelay.desktop.platform.DesktopInstalledPackagesPort
import notifyrelay.desktop.platform.DesktopLocalAppsPort
import notifyrelay.desktop.platform.DesktopLocalFilterPort
import notifyrelay.desktop.platform.DesktopMediaControlPort
import notifyrelay.desktop.platform.DesktopNativeFilterPort
import notifyrelay.desktop.platform.DesktopPinnedAppsPort
import notifyrelay.desktop.platform.DesktopRemoteAppsPort
import notifyrelay.desktop.platform.DesktopRemoteFilterPort
import notifyrelay.desktop.platform.DesktopSuperIslandConfigPort
import notifyrelay.desktop.platform.DesktopSuperIslandHistoryPort
import notifyrelay.domain.feature.appslist.InstalledPackagesPortRegistry
import notifyrelay.domain.feature.appslist.RemoteAppsPortRegistry
import notifyrelay.domain.feature.media.AudioRelayPortRegistry
import notifyrelay.domain.feature.media.MediaControlPortRegistry
import notifyrelay.domain.feature.notification.filter.LocalFilterPortRegistry
import notifyrelay.domain.feature.notification.filter.NativeFilterPortRegistry
import notifyrelay.domain.feature.notification.filter.RemoteFilterPortRegistry
import notifyrelay.domain.feature.notification.history.NotificationHistoryPortRegistry
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryPortRegistry
import notifyrelay.domain.feature.notification.superisland.SuperIslandConfigPortRegistry
import notifyrelay.domain.platform.FormFactorPortRegistry
import notifyrelay.shared.feature.appslist.AppIconPortRegistry
import notifyrelay.shared.feature.appslist.LocalAppsPortRegistry
import notifyrelay.shared.feature.appslist.PinnedAppsPortRegistry
import notifyrelay.shared.ui.common.DebugFlags
import notifyrelay.shared.ui.screen.loadLogLevel
import java.io.File

/**
 * 桌面端数据层初始化。
 *
 * 数据库文件放在用户目录下的 .notifyrelay 文件夹；
 * 初始化时把数据层能力注册到公共端端口，无数据源的端口给出中性实现。
 */
object DataLayerDesktop {
    @Volatile
    private var initialized = false

    /** 数据库文件路径。 */
    fun databasePath(): String {
        val dir = File(System.getProperty("user.home"), ".notifyrelay")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "notify_relay.db").absolutePath
    }

    /** 通知历史的设备列表来源，由 [registerDeviceManager] 注入；默认为仅本机。 */
    @Volatile
    private var knownDevices: () -> List<String> = { emptyList() }

    /** 设备消息端口来源，供应用列表与图标的请求链路使用。 */
    @Volatile
    private var deviceMessagePort: () -> notifyrelay.domain.feature.device.service.contract.DeviceMessagePort? = { null }

    /** 在线已认证设备来源，供剪贴板同步取发送目标。 */
    @Volatile
    private var onlineDevices: () -> List<notifyrelay.domain.feature.device.model.DeviceInfo> = { emptyList() }

    /** 设备回调宿主来源，供需要主动发报文的端口（音频中继）使用。 */
    @Volatile
    private var deviceCallbackHost: () -> com.xzyht.notifyrelay.nativecore.device.service.callback.DeviceCallbackHost? = { null }

    /** 剪贴板同步端口实例，由入口启动与释放。 */
    @Volatile
    var clipboardSync: notifyrelay.desktop.platform.DesktopClipboardSyncPort? = null
        private set

    /** 本机通知转发实例，由入口启动与释放。 */
    @Volatile
    var localNotificationRelay: notifyrelay.desktop.device.DesktopLocalNotificationRelay? = null
        private set

    /** 媒体控制端口实例，由入口启动与释放。 */
    @Volatile
    var mediaControl: notifyrelay.desktop.platform.DesktopMediaControlPort? = null
        private set

    /**
     * 登记设备管理器。
     *
     * 设备管理器在数据层之后创建，故由入口在创建后调用；
     * 端口侧每次查询时读取该提供者，避免持有过期实例。
     */
    fun registerDeviceManager(manager: notifyrelay.desktop.device.DesktopDeviceManager) {
        knownDevices = { manager.knownDeviceUuids() }
        deviceMessagePort = { manager }
        onlineDevices = { manager.getAuthenticatedOnlineDevices() }
        deviceCallbackHost = { manager }
    }

    /** 取设备回调宿主（供报文发送方使用）；未登记时返回 null。 */
    fun callbackHost(): com.xzyht.notifyrelay.nativecore.device.service.callback.DeviceCallbackHost? = deviceCallbackHost()

    /** 按 UUID 取在线设备；不存在时返回 null。 */
    fun findOnlineDevice(uuid: String): notifyrelay.domain.feature.device.model.DeviceInfo? = onlineDevices().find { it.uuid == uuid }

    /** 初始化公共端存储与仓储单例；重复调用无副作用。 */
    fun initialize() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val repository = DatabaseRepository(AppDatabaseJvmFactory.create(databasePath()))
            Storage.initialize(repository)

            // 平台能力端口
            FormFactorPortRegistry.install(DesktopFormFactorPort)
            InstalledPackagesPortRegistry.install(DesktopInstalledPackagesPort)
            NativeFilterPortRegistry.install(DesktopNativeFilterPort)
            IconCodecPortRegistry.install(DesktopIconCodecPort)

            // 有数据源的端口：通知历史与超级岛历史直接读 Room
            NotificationHistoryPortRegistry.install(DesktopHistoryPort(repository) { knownDevices() })
            SuperIslandHistoryPortRegistry.install(DesktopSuperIslandHistoryPort(repository))

            // 与 Android 共用仓储的端口
            val remoteFilterRepository = RemoteFilterRepository()
            RemoteFilterPortRegistry.install(DesktopRemoteFilterPort(remoteFilterRepository))
            LocalFilterPortRegistry.install(DesktopLocalFilterPort())
            AppIconPortRegistry.install(DesktopAppIconPort { deviceMessagePort() })
            LocalAppsPortRegistry.install(DesktopLocalAppsPort())
            RemoteAppsPortRegistry.install(DesktopRemoteAppsPort { deviceMessagePort() })
            // 音频端口需要按 UUID 找设备并发送控制报文；两者都依赖设备消息端口
            AudioRelayPortRegistry.install(
                DesktopAudioRelayPort(
                    findDevice = { uuid -> onlineDevices().find { it.uuid == uuid } },
                    sendControl = { device, plaintext ->
                        val host = deviceCallbackHost()
                        if (host == null) {
                            false
                        } else {
                            runCatching {
                                host.sendDataMessage(device, "DATA_MEDIA_CONTROL", plaintext)
                            }.isSuccess
                        }
                    },
                ),
            )
            mediaControl = notifyrelay.desktop.platform.DesktopMediaControlPort { onlineDevices() }
            MediaControlPortRegistry.install(mediaControl!!)
            SuperIslandConfigPortRegistry.install(DesktopSuperIslandConfigPort())

            // 剪贴板同步：收发判定在 Rust core，平台只做系统剪贴板读写
            clipboardSync =
                notifyrelay.desktop.platform.DesktopClipboardSyncPort(
                    { deviceMessagePort() },
                    { onlineDevices() },
                )

            // 置顶应用由公共仓库承担，端口直接暴露其状态流
            PinnedAppsPortRegistry.install(DesktopPinnedAppsPort())

            // 本机通知采集与转发：采集经 smtc-rust 的 WinRT 桥，转发委托公共网关
            localNotificationRelay = notifyrelay.desktop.device.DesktopLocalNotificationRelay { deviceMessagePort() }

            loadLogLevel { key, default -> Storage.getInt(key, default) }
            DebugFlags.load { key, default -> Storage.getBoolean(key, default) }

            initialized = true
        }
    }
}
