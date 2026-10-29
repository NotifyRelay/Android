package notifyrelay.desktop.platform

import notifyrelay.domain.feature.appslist.InstalledPackagesPort
import notifyrelay.domain.feature.notification.filter.NativeFilterPort
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

/** Rust 过滤能力端口：经 JNA 绑定调用 core 的过滤接口。 */
object DesktopNativeFilterPort : NativeFilterPort {
    override fun isReady(): Boolean =
        com.xzyht.notifyrelay.nativecore.NativeCore
            .isReady()

    override fun setFilterConfig(json: String): Boolean =
        com.xzyht.notifyrelay.nativecore.NativeCore
            .setFilterConfig(json) == 0

    override fun mapLocalPackage(packageName: String): String? =
        com.xzyht.notifyrelay.nativecore.NativeCore
            .mapLocalPackage(packageName)

    override fun checkFilterMode(
        packageName: String,
        notificationPackageName: String,
        title: String,
        text: String,
    ): Boolean =
        com.xzyht.notifyrelay.nativecore.NativeCore
            .checkFilterMode(packageName, notificationPackageName, title, text)
}
