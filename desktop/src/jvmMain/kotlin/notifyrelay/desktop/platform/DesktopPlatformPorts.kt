package notifyrelay.desktop.platform

import notifyrelay.domain.feature.appslist.InstalledPackagesPort
import notifyrelay.domain.platform.FormFactorPort

/** 设备形态端口：桌面端非平板、无系统版本判定。 */
object DesktopFormFactorPort : FormFactorPort {
    override fun isTablet(): Boolean = false

    override fun osVersion(): String? = null
}

/** 已安装应用包名端口：桌面端不枚举本机应用，恒为空集。 */
object DesktopInstalledPackagesPort : InstalledPackagesPort {
    override suspend fun installedPackages(): Set<String> = emptySet()

    override fun installedPackagesSync(): Set<String> = emptySet()
}
