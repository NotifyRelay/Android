package notifyrelay.data.config

import notifyrelay.base.SuperIslandStorageKeys
import notifyrelay.base.util.VersionUtils
import notifyrelay.domain.feature.notification.superisland.SpecInjectionMode
import notifyrelay.domain.platform.FormFactorPort
import notifyrelay.data.Storage

/**
 * 超级岛配置策略：键定义、持久化编码与互斥推导的唯一实现。
 *
 * Android 与桌面共用本类；平台差异（是否平板、系统版本）经 [FormFactorPort] 注入。
 * 键名与编码均为已发布持久化契约，**字符串值不可更改**。
 */
object SuperIslandConfigPolicy {
    /** 注入方式的显式编码，与枚举序数解耦（0/1 沿用历史序数，3 为 NONE）。 */
    private const val CODE_SUPER_ISLAND = 0
    private const val CODE_LIVE_UPDATES = 1
    private const val CODE_NONE = 3

    /** 旧版本默认值（两者都注入），视为「未显式修改」，跟随当前默认。 */
    private const val LEGACY_BOTH_ORDINAL = 2

    /** 浮窗默认开启所需比较的系统版本。 */
    private const val OS_VERSION_THRESHOLD = "OS3.0.300"

    /** 展示通道：三者互斥，由浮窗与列表两个开关推导。 */
    enum class DisplayChannel {
        FLOATING,
        LIST,
        NOTIFICATION,
    }

    /**
     * 浮窗兼容开关的默认值：系统版本高于阈值时默认关闭。
     *
     * 无系统版本（桌面）或非平板判定失败时均按「不高于阈值」处理，即默认开启。
     */
    fun defaultFloatingWindowEnabled(formFactor: FormFactorPort?): Boolean {
        val osVersion = formFactor?.osVersion() ?: return true
        return !VersionUtils.isVersionGreaterThan(osVersion, OS_VERSION_THRESHOLD)
    }

    /** 浮窗兼容是否开启。 */
    fun isFloatingWindowEnabled(formFactor: FormFactorPort?): Boolean =
        Storage.getBoolean(SuperIslandStorageKeys.FLOATING_WINDOW, defaultFloatingWindowEnabled(formFactor))

    /** 是否列表模式；默认值由「非浮窗 + 平板」推导。 */
    fun isNotificationListMode(formFactor: FormFactorPort?): Boolean {
        val isTablet = formFactor?.isTablet() ?: false
        val defaultListMode = isTablet && !isFloatingWindowEnabled(formFactor)
        return Storage.getBoolean(SuperIslandStorageKeys.NOTIFICATION_LIST, defaultListMode)
    }

    /** 推导当前生效的展示通道。 */
    fun resolveDisplayChannel(formFactor: FormFactorPort?): DisplayChannel =
        when {
            isFloatingWindowEnabled(formFactor) -> DisplayChannel.FLOATING
            isNotificationListMode(formFactor) -> DisplayChannel.LIST
            else -> DisplayChannel.NOTIFICATION
        }

    /**
     * 设置浮窗开关（与列表模式互斥），返回展示通道是否变化。
     *
     * 列表模式的默认值依赖浮窗开关，故必须在写入浮窗键**之前**判定。
     */
    fun setFloatingWindowEnabled(
        formFactor: FormFactorPort?,
        enabled: Boolean,
    ): Boolean {
        val previousChannel = resolveDisplayChannel(formFactor)
        val listModeWillClose = enabled && isNotificationListMode(formFactor)
        Storage.putBoolean(SuperIslandStorageKeys.FLOATING_WINDOW, enabled)
        if (listModeWillClose) {
            Storage.putBoolean(SuperIslandStorageKeys.NOTIFICATION_LIST, false)
        }
        return resolveDisplayChannel(formFactor) != previousChannel
    }

    /** 设置列表模式（与浮窗互斥），返回展示通道是否变化。 */
    fun setNotificationListMode(
        formFactor: FormFactorPort?,
        enabled: Boolean,
    ): Boolean {
        val previousChannel = resolveDisplayChannel(formFactor)
        if (enabled && isFloatingWindowEnabled(formFactor)) {
            Storage.putBoolean(SuperIslandStorageKeys.FLOATING_WINDOW, false)
        }
        Storage.putBoolean(SuperIslandStorageKeys.NOTIFICATION_LIST, enabled)
        return resolveDisplayChannel(formFactor) != previousChannel
    }

    /** 是否需要为通知设置点击/删除意图。 */
    fun needClickIntent(formFactor: FormFactorPort?): Boolean = isFloatingWindowEnabled(formFactor) || isNotificationListMode(formFactor)

    /** 是否显示来自远端的超级岛（默认开启）。 */
    fun isRemoteDisplayEnabled(): Boolean = Storage.getBoolean(SuperIslandStorageKeys.REMOTE_SHOW, true)

    /** 设置是否显示来自远端的超级岛。 */
    fun setRemoteDisplayEnabled(enabled: Boolean) {
        Storage.putBoolean(SuperIslandStorageKeys.REMOTE_SHOW, enabled)
    }

    /** 当前注入方式。 */
    fun specInjectionMode(): SpecInjectionMode {
        val code = Storage.getInt(SuperIslandStorageKeys.SPEC_INJECTION_MODE, -1)
        // 先解码 NONE：它是独立编码，不得落入历史值判定
        if (code == CODE_NONE) return SpecInjectionMode.NONE
        if (code == -1 || code == LEGACY_BOTH_ORDINAL) return SpecInjectionMode.LIVE_UPDATES
        return when (code) {
            CODE_SUPER_ISLAND -> SpecInjectionMode.SUPER_ISLAND
            CODE_LIVE_UPDATES -> SpecInjectionMode.LIVE_UPDATES
            else -> SpecInjectionMode.LIVE_UPDATES
        }
    }

    /** 设置注入方式（写显式编码，非枚举序数）。 */
    fun setSpecInjectionMode(mode: SpecInjectionMode) {
        val code =
            when (mode) {
                SpecInjectionMode.SUPER_ISLAND -> CODE_SUPER_ISLAND
                SpecInjectionMode.LIVE_UPDATES -> CODE_LIVE_UPDATES
                SpecInjectionMode.NONE -> CODE_NONE
            }
        Storage.putInt(SuperIslandStorageKeys.SPEC_INJECTION_MODE, code)
    }

    /** 镜像应用过滤是否开启（默认开启）。 */
    fun isMirrorFilterEnabled(): Boolean = Storage.getBoolean(SuperIslandStorageKeys.MIRROR_FILTER_ENABLED, true)

    /** 设置镜像应用过滤。 */
    fun setMirrorFilterEnabled(enabled: Boolean) {
        Storage.putBoolean(SuperIslandStorageKeys.MIRROR_FILTER_ENABLED, enabled)
    }
}
