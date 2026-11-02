package notifyrelay.shared.feature.notification.superisland.tracker

/** 本机超级岛活跃应用追踪。 */
object LocalSuperIslandTracker {
    private val activePackages = mutableSetOf<String>()

    fun markActive(packageName: String) {
        activePackages.add(packageName)
    }

    fun markInactive(packageName: String) {
        activePackages.remove(packageName)
    }

    fun isActive(packageName: String): Boolean = activePackages.contains(packageName)
}
