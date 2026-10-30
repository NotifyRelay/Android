package notifyrelay.shared.ui.navigation

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 路由定义，类型安全的导航键
 * Route definitions, type-safe navigation keys
 */
@Serializable
sealed interface Route : NavKey {
    /**
     * 主页面（设备互联与增强）
     * Main page (Device connection and enhancement)
     */
    @Serializable
    data object Main : Route

    /**
     * 历史页面
     * History page
     */
    @Serializable
    data object History : Route

    /**
     * 设置页面
     * Settings page
     */
    @Serializable
    data object Settings : Route

    /**
     * Scrcpy高级设置页面
     * Scrcpy advanced settings page
     */
    @Serializable
    data object ScrcpyAdvanced : Route

    /**
     * Scrcpy虚拟按钮排序页面
     * Scrcpy virtual button order page
     */
    @Serializable
    data object ScrcpyVirtualButtonOrder : Route

    /**
     * 设置-远程过滤子页面
     * Settings remote filter sub page
     */
    @Serializable
    data object SettingsRemoteFilter : Route

    /**
     * 设置-本地过滤子页面
     * Settings local filter sub page
     */
    @Serializable
    data object SettingsLocalFilter : Route

    /**
     * 设置-超级岛子页面
     * Settings super island sub page
     */
    @Serializable
    data object SettingsSuperIsland : Route

    /**
     * 设置-开发者选项子页面
     * Settings developer options sub page
     */
    @Serializable
    data object SettingsDeveloperMode : Route

    /**
     * 设置-屏幕镜像子页面
     * Settings scrcpy sub page
     */
    @Serializable
    data object SettingsScrcpy : Route

    /**
     * 设置-关于子页面
     * Settings about sub page
     */
    @Serializable
    data object SettingsAbout : Route

    /**
     * 设置-外观子页面
     * Settings appearance sub page
     */
    @Serializable
    data object SettingsAppearance : Route

    /**
     * 设置-叠加层子页面（仅桌面端显示）
     * Settings overlay sub page, desktop only
     */
    @Serializable
    data object SettingsOverlay : Route
}
