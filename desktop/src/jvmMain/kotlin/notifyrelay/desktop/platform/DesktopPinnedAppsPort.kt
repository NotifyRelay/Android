package notifyrelay.desktop.platform

import notifyrelay.shared.feature.appslist.PinnedAppsPort
import notifyrelay.shared.feature.appslist.RepositoryPinnedAppsPort

/**
 * 桌面端的置顶端口。
 *
 * 与 Android 同源：置顶状态在公共数据层，本类只作为平台侧注册名，行为全部来自
 * [RepositoryPinnedAppsPort]。
 */
object DesktopPinnedAppsPort : PinnedAppsPort by RepositoryPinnedAppsPort
