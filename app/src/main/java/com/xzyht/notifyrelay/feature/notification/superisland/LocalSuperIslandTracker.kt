package com.xzyht.notifyrelay.feature.notification.superisland

import java.util.concurrent.ConcurrentHashMap

object LocalSuperIslandTracker {
    private val activePackages = ConcurrentHashMap<String, Boolean>()

    fun markActive(packageName: String) {
        activePackages[packageName] = true
    }

    fun markInactive(packageName: String) {
        activePackages.remove(packageName)
    }

    fun isActive(packageName: String): Boolean = activePackages.containsKey(packageName)
}
