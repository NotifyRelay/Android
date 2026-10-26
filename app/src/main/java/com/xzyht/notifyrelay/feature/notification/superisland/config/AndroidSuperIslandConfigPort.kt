package com.xzyht.notifyrelay.feature.notification.superisland.config

import android.content.Context
import com.xzyht.notifyrelay.feature.notification.superisland.replica.FloatingReplicaManager
import notifyrelay.domain.feature.notification.superisland.SpecInjectionMode
import notifyrelay.domain.feature.notification.superisland.SuperIslandConfigPort

/** 以 [SuperIslandConfigUtils] 与 [FloatingReplicaManager] 实现 [SuperIslandConfigPort]。 */
class AndroidSuperIslandConfigPort(
    private val context: Context,
) : SuperIslandConfigPort {
    override fun isRemoteDisplayEnabled(): Boolean = SuperIslandConfigUtils.isRemoteSuperIslandDisplayEnabled(context)

    override fun setRemoteDisplayEnabled(enabled: Boolean) {
        SuperIslandConfigUtils.setRemoteSuperIslandDisplayEnabled(context, enabled)
    }

    override fun isFloatingWindowEnabled(): Boolean = SuperIslandConfigUtils.isFloatingWindowEnabled(context)

    override fun setFloatingWindowEnabled(enabled: Boolean): Boolean = SuperIslandConfigUtils.setFloatingWindowEnabled(context, enabled)

    override fun isNotificationListMode(): Boolean = SuperIslandConfigUtils.isNotificationListMode(context)

    override fun setNotificationListMode(enabled: Boolean): Boolean = SuperIslandConfigUtils.setNotificationListMode(context, enabled)

    override fun specInjectionMode(): SpecInjectionMode =
        when (SuperIslandConfigUtils.getSpecInjectionMode(context)) {
            SuperIslandConfigUtils.SpecInjectionMode.SUPER_ISLAND -> SpecInjectionMode.SUPER_ISLAND
            SuperIslandConfigUtils.SpecInjectionMode.LIVE_UPDATES -> SpecInjectionMode.LIVE_UPDATES
            SuperIslandConfigUtils.SpecInjectionMode.NONE -> SpecInjectionMode.NONE
        }

    override fun setSpecInjectionMode(mode: SpecInjectionMode) {
        val legacy =
            when (mode) {
                SpecInjectionMode.SUPER_ISLAND -> SuperIslandConfigUtils.SpecInjectionMode.SUPER_ISLAND
                SpecInjectionMode.LIVE_UPDATES -> SuperIslandConfigUtils.SpecInjectionMode.LIVE_UPDATES
                SpecInjectionMode.NONE -> SuperIslandConfigUtils.SpecInjectionMode.NONE
            }
        SuperIslandConfigUtils.setSpecInjectionMode(context, legacy)
    }

    override fun dismissAllRemote() {
        FloatingReplicaManager.dismissAllRemoteSuperIsland(context)
    }

    override fun switchChannel() {
        FloatingReplicaManager.switchSuperIslandChannel(context)
    }
}
