package com.xzyht.notifyrelay.feature.notification.superisland.config

import android.content.Context
import com.xzyht.notifyrelay.feature.notification.superisland.replica.FloatingReplicaManager
import notifyrelay.domain.feature.notification.superisland.SpecInjectionMode
import notifyrelay.domain.feature.notification.superisland.SuperIslandConfigPort

/**
 * 超级岛配置端口（Android）。
 *
 * 配置读写委托公共策略；本类只补平台专属的展示副作用（撤下/切换通道）。
 */
class AndroidSuperIslandConfigPort(
    private val context: Context,
) : SuperIslandConfigPort {
    override fun isRemoteDisplayEnabled(): Boolean = SuperIslandConfigUtils.isRemoteSuperIslandDisplayEnabled(context)

    override fun setRemoteDisplayEnabled(enabled: Boolean) = SuperIslandConfigUtils.setRemoteSuperIslandDisplayEnabled(context, enabled)

    override fun isFloatingWindowEnabled(): Boolean = SuperIslandConfigUtils.isFloatingWindowEnabled(context)

    override fun setFloatingWindowEnabled(enabled: Boolean): Boolean = SuperIslandConfigUtils.setFloatingWindowEnabled(context, enabled)

    override fun isNotificationListMode(): Boolean = SuperIslandConfigUtils.isNotificationListMode(context)

    override fun setNotificationListMode(enabled: Boolean): Boolean = SuperIslandConfigUtils.setNotificationListMode(context, enabled)

    override fun specInjectionMode(): SpecInjectionMode = SuperIslandConfigUtils.getSpecInjectionMode(context)

    override fun setSpecInjectionMode(mode: SpecInjectionMode) = SuperIslandConfigUtils.setSpecInjectionMode(context, mode)

    override fun dismissAllRemote() = FloatingReplicaManager.dismissAllRemoteSuperIsland(context)

    override fun switchChannel() = FloatingReplicaManager.switchSuperIslandChannel(context)
}
