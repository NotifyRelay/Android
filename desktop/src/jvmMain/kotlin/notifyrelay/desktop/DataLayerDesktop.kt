package notifyrelay.desktop

import notifyrelay.data.Storage
import notifyrelay.data.database.AppDatabaseJvmFactory
import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.desktop.platform.DesktopAppIconPort
import notifyrelay.desktop.platform.DesktopAudioRelayPort
import notifyrelay.desktop.platform.DesktopHistoryPort
import notifyrelay.desktop.platform.DesktopLocalAppsPort
import notifyrelay.desktop.platform.DesktopLocalFilterPort
import notifyrelay.desktop.platform.DesktopMediaControlPort
import notifyrelay.desktop.platform.DesktopPinnedAppsPort
import notifyrelay.desktop.platform.DesktopRemoteAppsPort
import notifyrelay.desktop.platform.DesktopRemoteFilterPort
import notifyrelay.desktop.platform.DesktopSuperIslandConfigPort
import notifyrelay.desktop.platform.DesktopSuperIslandHistoryPort
import notifyrelay.domain.feature.appslist.RemoteAppsPortRegistry
import notifyrelay.domain.feature.media.AudioRelayPortRegistry
import notifyrelay.domain.feature.media.MediaControlPortRegistry
import notifyrelay.domain.feature.notification.filter.LocalFilterPortRegistry
import notifyrelay.domain.feature.notification.filter.RemoteFilterPortRegistry
import notifyrelay.domain.feature.notification.history.NotificationHistoryPortRegistry
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryPortRegistry
import notifyrelay.domain.feature.notification.superisland.SuperIslandConfigPortRegistry
import notifyrelay.shared.feature.appslist.AppIconPortRegistry
import notifyrelay.shared.feature.appslist.LocalAppsPortRegistry
import notifyrelay.shared.feature.appslist.PinnedAppsPortRegistry
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

    /** 初始化公共端存储与仓储单例；重复调用无副作用。 */
    fun initialize() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val repository = DatabaseRepository(AppDatabaseJvmFactory.create(databasePath()))
            Storage.initialize(repository)

            // 有数据源的端口：通知历史直接读 Room
            NotificationHistoryPortRegistry.install(DesktopHistoryPort(repository))

            // 桌面端无对应数据源的端口：给中性实现，保证公共端 UI 可渲染
            RemoteFilterPortRegistry.install(DesktopRemoteFilterPort())
            LocalAppsPortRegistry.install(DesktopLocalAppsPort())
            AppIconPortRegistry.install(DesktopAppIconPort())
            PinnedAppsPortRegistry.install(DesktopPinnedAppsPort())
            RemoteAppsPortRegistry.install(DesktopRemoteAppsPort())
            LocalFilterPortRegistry.install(DesktopLocalFilterPort())
            AudioRelayPortRegistry.install(DesktopAudioRelayPort())
            MediaControlPortRegistry.install(DesktopMediaControlPort())
            SuperIslandConfigPortRegistry.install(DesktopSuperIslandConfigPort())
            SuperIslandHistoryPortRegistry.install(DesktopSuperIslandHistoryPort())

            initialized = true
        }
    }
}
