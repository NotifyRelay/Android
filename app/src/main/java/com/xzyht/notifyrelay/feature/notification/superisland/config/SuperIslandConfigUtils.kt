package com.xzyht.notifyrelay.feature.notification.superisland.config

import android.content.Context
import notifyrelay.base.log.Logger
import notifyrelay.base.util.ToastUtils
import notifyrelay.data.config.SuperIslandConfigPolicy
import notifyrelay.domain.feature.notification.superisland.SpecInjectionMode
import notifyrelay.domain.platform.FormFactorPortRegistry

/**
 * 超级岛配置工具（Android 侧门面）。
 *
 * 键定义、持久化编码与互斥推导的唯一实现位于公共端 [SuperIslandConfigPolicy]；
 * 本类只保留 Android 依赖的形参与提示动作，供既有调用点原样使用。
 */
object SuperIslandConfigUtils {
    private const val TAG = "SuperIslandConfigUtils"

    /** 浮窗兼容开关的默认值。 */
    fun defaultFloatingWindowEnabled(): Boolean = SuperIslandConfigPolicy.defaultFloatingWindowEnabled(FormFactorPortRegistry.current)

    /** 浮窗功能是否开启。 */
    fun isFloatingWindowEnabled(context: Context): Boolean = SuperIslandConfigPolicy.isFloatingWindowEnabled(FormFactorPortRegistry.current)

    /** 设置浮窗开关（与通知列表模式互斥）；返回展示通道是否发生变化。 */
    fun setFloatingWindowEnabled(
        context: Context,
        enabled: Boolean,
    ): Boolean = SuperIslandConfigPolicy.setFloatingWindowEnabled(FormFactorPortRegistry.current, enabled)

    /** 通知列表模式是否开启。 */
    fun isNotificationListMode(context: Context): Boolean = SuperIslandConfigPolicy.isNotificationListMode(FormFactorPortRegistry.current)

    /** 设置通知列表模式（与浮窗互斥）；返回展示通道是否发生变化。 */
    fun setNotificationListMode(
        context: Context,
        enabled: Boolean,
    ): Boolean = SuperIslandConfigPolicy.setNotificationListMode(FormFactorPortRegistry.current, enabled)

    /** 是否需要为通知设置点击/删除意图。 */
    fun needClickIntent(context: Context): Boolean = SuperIslandConfigPolicy.needClickIntent(FormFactorPortRegistry.current)

    /** 是否显示来自远端的超级岛。 */
    fun isRemoteSuperIslandDisplayEnabled(context: Context): Boolean = SuperIslandConfigPolicy.isRemoteDisplayEnabled()

    /** 设置是否显示来自远端的超级岛。 */
    fun setRemoteSuperIslandDisplayEnabled(
        context: Context,
        enabled: Boolean,
    ) = SuperIslandConfigPolicy.setRemoteDisplayEnabled(enabled)

    /** 用户主动展示前的开关校验；被开关拦下时给出提示并返回 false。 */
    fun confirmManualRemoteSuperIslandDisplay(context: Context): Boolean {
        if (SuperIslandConfigPolicy.isRemoteDisplayEnabled()) return true
        ToastUtils.showShortToast(context, "超级岛显示开关已关闭")
        return false
    }

    /** 当前注入方式。 */
    fun getSpecInjectionMode(context: Context): SpecInjectionMode = SuperIslandConfigPolicy.specInjectionMode()

    /** 设置注入方式。 */
    fun setSpecInjectionMode(
        context: Context,
        mode: SpecInjectionMode,
    ) = SuperIslandConfigPolicy.setSpecInjectionMode(mode)

    /** 规范信息注入是否开启（超级岛分支）。 */
    fun isSuperIslandSpecInjectionEnabled(context: Context): Boolean = getSpecInjectionMode(context) == SpecInjectionMode.SUPER_ISLAND

    /** 规范信息注入是否开启（Live Updates 分支）。 */
    fun isLiveUpdatesSpecInjectionEnabled(context: Context): Boolean = getSpecInjectionMode(context) == SpecInjectionMode.LIVE_UPDATES

    /** 是否至少有一种规范信息注入开启。 */
    fun isAnySpecInjectionEnabled(context: Context): Boolean = getSpecInjectionMode(context) != SpecInjectionMode.NONE

    /** 校验注入开关；两者都关闭时重置为 Live Updates。 */
    fun validateSpecInjectionSwitches(context: Context) {
        if (!isAnySpecInjectionEnabled(context)) {
            setSpecInjectionMode(context, SpecInjectionMode.LIVE_UPDATES)
            Logger.w(TAG, "规范信息注入模式无效，已重置为 Live Updates")
        }
    }

    /** 镜像应用过滤是否开启。 */
    fun isMirrorFilterEnabled(): Boolean = SuperIslandConfigPolicy.isMirrorFilterEnabled()

    /** 设置镜像应用过滤。 */
    fun setMirrorFilterEnabled(enabled: Boolean) = SuperIslandConfigPolicy.setMirrorFilterEnabled(enabled)
}
