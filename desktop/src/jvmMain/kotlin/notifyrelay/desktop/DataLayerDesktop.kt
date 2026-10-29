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

    /** 通知历史的设备列表来源，由 [registerDeviceProvider] 注入；默认为仅本机。 */
    @Volatile
    private var knownDevices: () -> List<String> = { emptyList() }

    /**
     * 登记通知历史的远端设备来源。
     *
     * 设备管理器在数据层之后创建，故由入口在创建后调用；端口侧每次查询时读取该提供者。
     */
    fun registerDeviceProvider(provider: () -> List<String>) {
        knownDevices = provider
    }

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
            AppIconPortRegistry.install(DesktopAppIconPort())
            LocalAppsPortRegistry.install(DesktopLocalAppsPort())
            RemoteAppsPortRegistry.install(DesktopRemoteAppsPort())
            AudioRelayPortRegistry.install(DesktopAudioRelayPort())
            MediaControlPortRegistry.install(DesktopMediaControlPort())
            SuperIslandConfigPortRegistry.install(DesktopSuperIslandConfigPort())

            // 置顶应用由公共仓库承担，端口直接暴露其状态流
            PinnedAppsPortRegistry.install(DesktopPinnedAppsPort())

            loadLogLevel { key, default -> Storage.getInt(key, default) }
            DebugFlags.load { key, default -> Storage.getBoolean(key, default) }

            initialized = true
        }
    }
}
