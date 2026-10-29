package notifyrelay.domain.feature.appslist

/**
 * 本机已安装应用包名端口。
 *
 * `:data` 不能依赖 `:shared` 的 `LocalAppsPort`，故在领域层提供最小契约；
 * 桌面端基于「本机应用不列出」的定位返回空集。
 */
interface InstalledPackagesPort {
    /** 已安装应用包名（挂起版本）。 */
    suspend fun installedPackages(): Set<String>

    /** 已安装应用包名（同步快照）。 */
    fun installedPackagesSync(): Set<String>
}

/** [InstalledPackagesPort] 的注册点。 */
object InstalledPackagesPortRegistry {
    @Volatile
    var current: InstalledPackagesPort? = null
        private set

    fun install(port: InstalledPackagesPort) {
        current = port
    }
}
