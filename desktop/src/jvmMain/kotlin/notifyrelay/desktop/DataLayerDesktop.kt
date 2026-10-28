package notifyrelay.desktop

import notifyrelay.data.Storage
import notifyrelay.data.database.AppDatabaseJvmFactory
import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.desktop.platform.DesktopAppIconPort
import notifyrelay.desktop.platform.DesktopHistoryPort
import notifyrelay.desktop.platform.DesktopLocalAppsPort
import notifyrelay.desktop.platform.DesktopRemoteFilterPort
import notifyrelay.domain.feature.notification.filter.RemoteFilterPortRegistry
import notifyrelay.domain.feature.notification.history.NotificationHistoryPortRegistry
import notifyrelay.shared.feature.appslist.AppIconPortRegistry
import notifyrelay.shared.feature.appslist.LocalAppsPortRegistry
import java.io.File

/**
 * 桌面端数据层初始化。
 *
 * 数据库文件放在用户目录下的 .notifyrelay 文件夹；
 * 初始化时把数据层能力注册到公共端端口。
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
            NotificationHistoryPortRegistry.install(DesktopHistoryPort(repository))
            // 卡片渲染所需的其余端口：桌面端无对应数据源，给出中性实现
            RemoteFilterPortRegistry.install(DesktopRemoteFilterPort())
            LocalAppsPortRegistry.install(DesktopLocalAppsPort())
            AppIconPortRegistry.install(DesktopAppIconPort())
            SampleData.seedIfEmpty(repository)
            initialized = true
        }
    }
}
