package com.xzyht.notifyrelay.feature.notification.superisland.config

import android.content.Context
import notifyrelay.base.util.DeviceUtils
import notifyrelay.base.util.Logger
import notifyrelay.base.util.PermissionHelper
import notifyrelay.base.util.ToastUtils
import notifyrelay.data.StorageManager

/**
 * 超级岛配置工具类：**只做纯配置读写与推导**。
 *
 * 越权职责已剥离（P2-5 / P2-6）：
 * - 意图 / 渠道构造 → `intent.NotificationIntentFactory`（本类不再 import `PendingIntent` / `Intent`
 *   / `NotificationBroadcastReceiver` / `SuperIslandActions`）；
 * - 通道切换副作用 → 由设置页调用方在写入后触发 `FloatingReplicaManager.switchSuperIslandChannel`，
 *   本类不再依赖 `replica` 包（原为 config ↔ replica 双向依赖）。
 */
object SuperIslandConfigUtils {
    private const val TAG = "SuperIslandConfigUtils"

    /** 浮窗开关的存储 key。持久化契约，值不可改。 */
    internal const val SUPER_ISLAND_FLOATING_WINDOW_KEY = "super_island_floating_window"

    /** 通知列表模式的存储 key。持久化契约，值不可改。 */
    internal const val SUPER_ISLAND_NOTIFICATION_LIST_KEY = "super_island_notification_list"

    /** 规范信息注入方式的存储 key。持久化契约，值不可改。 */
    internal const val SPEC_INJECTION_MODE_KEY = "spec_injection_mode"

    /** 镜像应用过滤开关的存储 key。持久化契约，值不可改。 */
    internal const val MIRROR_FILTER_ENABLED_KEY = "super_island_mirror_filter_enabled"

    /** 远端超级岛显示开关的存储 key。持久化契约，值不可改（原为设置页私有常量）。 */
    internal const val SUPER_ISLAND_SHOW_KEY = "superisland_show"

    // 注入方式枚举
    enum class SpecInjectionMode {
        SUPER_ISLAND, // 仅超级岛规范信息注入（需要 XMSF 鉴权，未注册 scope 的构建不生效）
        LIVE_UPDATES, // 仅Live Updates规范信息注入
        NONE, // 都不注入（不应该使用，但为了完整性保留）
    }

    // 旧版本默认值（两者都注入），用于迁移判断；旧默认视为"未显式修改"，跟随新默认
    private const val LEGACY_BOTH_ORDINAL = 2

    // 规范信息注入模式的持久化编码：显式定义，与枚举序数解耦，
    // 避免枚举顺序调整破坏已存储的值。
    // 0 / 1 沿用历史枚举序数（SUPER_ISLAND / LIVE_UPDATES）以保持向后兼容；
    // 2 已被 LEGACY_BOTH_ORDINAL 占用；NONE 单独使用 3，确保可往返且不与任何历史值冲突。
    private const val CODE_SUPER_ISLAND = 0
    private const val CODE_LIVE_UPDATES = 1
    private const val CODE_NONE = 3

    /**
     * 浮窗兼容开关的**默认值**：OS 版本高于 OS3.0.300 时默认关闭。
     *
     * 原为 `FloatingReplicaManager.getDefaultFloatingWindowEnabled()`，由 config 调用 replica
     * 形成 config→replica 依赖；按 P2-6 移入 config 自身（判定逻辑逐字不变）。
     */
    fun defaultFloatingWindowEnabled(): Boolean {
        val detailedOsVersion = PermissionHelper.getDetailedOsVersion()
        val isGreater = PermissionHelper.isVersionGreaterThan(detailedOsVersion, "OS3.0.300")
        return !isGreater
    }

    /**
     * 检查浮窗功能是否开启
     */
    fun isFloatingWindowEnabled(context: Context): Boolean = StorageManager.getBoolean(context, SUPER_ISLAND_FLOATING_WINDOW_KEY, defaultFloatingWindowEnabled())

    /** 远端超级岛的展示通道：三者互斥，由浮窗 / 列表两个开关推导。 */
    enum class DisplayChannel {
        FLOATING,
        LIST,
        NOTIFICATION,
    }

    /**
     * 推导当前生效的展示通道。
     *
     * 注意 [isNotificationListMode] 的默认值依赖浮窗开关，故必须在**配置写入前后**各算一次，
     * 才能得到真实的通道变化。
     */
    fun resolveDisplayChannel(context: Context): DisplayChannel =
        when {
            isFloatingWindowEnabled(context) -> DisplayChannel.FLOATING
            isNotificationListMode(context) -> DisplayChannel.LIST
            else -> DisplayChannel.NOTIFICATION
        }

    /**
     * 设置浮窗开关（与通知列表模式互斥）。
     * 开启浮窗时自动关闭通知列表模式。
     *
     * **只写配置，不触发通道切换**（P2-6：副作用上移到设置页调用方）：
     * 展示通道变化时由调用方按返回值决定是否调用
     * `FloatingReplicaManager.switchSuperIslandChannel`（撤下旧通道并按新通道重建）。
     *
     * @return 展示通道是否发生变化；true 表示调用方需要执行通道切换。
     */
    fun setFloatingWindowEnabled(
        context: Context,
        enabled: Boolean,
    ): Boolean {
        val previousChannel = resolveDisplayChannel(context)
        // 列表模式是否开启必须在写入浮窗开关**之前**判定：平板默认值由「非浮窗」推导
        val listModeWillClose = enabled && isNotificationListMode(context)
        StorageManager.putBoolean(context, SUPER_ISLAND_FLOATING_WINDOW_KEY, enabled)
        if (listModeWillClose) {
            StorageManager.putBoolean(context, SUPER_ISLAND_NOTIFICATION_LIST_KEY, false)
        }
        return resolveDisplayChannel(context) != previousChannel
    }

    /**
     * 检查通知列表模式是否开启。
     * 该模式仅在浮窗关闭时有效，与浮窗互斥。
     * 默认值：平板开启，手机关闭。
     */
    fun isNotificationListMode(context: Context): Boolean {
        val defaultListMode = DeviceUtils.isTablet(context) && !isFloatingWindowEnabled(context)
        return StorageManager.getBoolean(
            context,
            SUPER_ISLAND_NOTIFICATION_LIST_KEY,
            defaultListMode,
        )
    }

    /**
     * 设置通知列表模式（与浮窗互斥）。
     * 开启通知列表模式时自动关闭浮窗。
     *
     * **只写配置，不触发通道切换**（P2-6）：各通道的通知 ID 与渲染方式均不同，通道变化时
     * 由调用方调用 `FloatingReplicaManager.switchSuperIslandChannel`：撤下旧通道展示并按新通道
     * 重建当前展示内容，否则会出现旧通道通知残留、两条通知并存，或内容空到远端下一个包到来。
     *
     * @return 展示通道是否发生变化；true 表示调用方需要执行通道切换。
     */
    fun setNotificationListMode(
        context: Context,
        enabled: Boolean,
    ): Boolean {
        val previousChannel = resolveDisplayChannel(context)
        if (enabled && isFloatingWindowEnabled(context)) {
            StorageManager.putBoolean(context, SUPER_ISLAND_FLOATING_WINDOW_KEY, false)
        }
        StorageManager.putBoolean(context, SUPER_ISLAND_NOTIFICATION_LIST_KEY, enabled)
        return resolveDisplayChannel(context) != previousChannel
    }

    /**
     * 检查是否显示来自远端的超级岛（默认开启）。
     *
     * 关闭时只关闭「展示」：入站解析、远端状态缓存与历史记录照常进行，
     * 由 SuperIslandProcessor 跳过浮窗 / 列表 / 通知的创建。
     */
    fun isRemoteSuperIslandDisplayEnabled(context: Context): Boolean = StorageManager.getBoolean(context, SUPER_ISLAND_SHOW_KEY, true)

    /**
     * 设置是否显示来自远端的超级岛。
     *
     * 仅写入配置；已展示的条目由调用方在关闭时调用
     * `FloatingReplicaManager.dismissAllRemoteSuperIsland` 立即撤下。
     */
    fun setRemoteSuperIslandDisplayEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        StorageManager.putBoolean(context, SUPER_ISLAND_SHOW_KEY, enabled)
    }

    /**
     * 用户主动展示（历史重放 / 测试分支）前的开关校验。
     *
     * 与远端自动接收路径不同，主动展示属用户显式操作，被开关拦下时必须给出提示，
     * 否则表现为「点了没反应」。
     *
     * @return true 表示允许展示；false 表示已提示用户，调用方应中止展示。
     */
    fun confirmManualRemoteSuperIslandDisplay(context: Context): Boolean {
        if (isRemoteSuperIslandDisplayEnabled(context)) return true
        ToastUtils.showShortToast(context, "超级岛显示开关已关闭")
        return false
    }

    /**
     * 获取规范信息注入模式。
     *
     * 存储值为 [CODE_SUPER_ISLAND] / [CODE_LIVE_UPDATES] / [CODE_NONE] 三个显式编码，
     * 与枚举序数解耦；[CODE_NONE] 必须在旧值处理之前单独解码，
     * 否则会与历史值 [LEGACY_BOTH_ORDINAL] 混淆而导致无法往返。
     *
     * 迁移逻辑：
     * - 无存储值 / 旧默认 BOTH（视为"仍为默认"，用户未显式修改过）→ 固定默认 LIVE_UPDATES
     * - 存储为显式 SUPER_ISLAND / LIVE_UPDATES / NONE → 保持用户修改
     */
    fun getSpecInjectionMode(context: Context): SpecInjectionMode {
        val storedCode = StorageManager.getInt(context, SPEC_INJECTION_MODE_KEY, -1)
        // 先解码 NONE：它是独立编码，不得落入下方的历史值判定
        if (storedCode == CODE_NONE) return SpecInjectionMode.NONE
        if (storedCode == -1 || storedCode == LEGACY_BOTH_ORDINAL) {
            // 无存储或旧默认 BOTH：固定默认 Live Updates
            return SpecInjectionMode.LIVE_UPDATES
        }
        return when (storedCode) {
            CODE_SUPER_ISLAND -> SpecInjectionMode.SUPER_ISLAND
            CODE_LIVE_UPDATES -> SpecInjectionMode.LIVE_UPDATES
            else -> SpecInjectionMode.LIVE_UPDATES
        }
    }

    /**
     * 设置规范信息注入模式。
     * 写入 [SpecInjectionMode] 对应的显式持久化编码（非枚举序数），与 [getSpecInjectionMode] 同口径。
     *
     * @param context 用于写入存储的上下文。
     * @param mode 目标注入模式。
     */
    fun setSpecInjectionMode(
        context: Context,
        mode: SpecInjectionMode,
    ) {
        val code =
            when (mode) {
                SpecInjectionMode.SUPER_ISLAND -> CODE_SUPER_ISLAND
                SpecInjectionMode.LIVE_UPDATES -> CODE_LIVE_UPDATES
                SpecInjectionMode.NONE -> CODE_NONE
            }
        StorageManager.putInt(context, SPEC_INJECTION_MODE_KEY, code)
    }

    /**
     * 检查超级岛规范信息注入是否开启
     */
    fun isSuperIslandSpecInjectionEnabled(context: Context): Boolean = getSpecInjectionMode(context) == SpecInjectionMode.SUPER_ISLAND

    /**
     * 检查Live Updates规范信息注入是否开启
     */
    fun isLiveUpdatesSpecInjectionEnabled(context: Context): Boolean = getSpecInjectionMode(context) == SpecInjectionMode.LIVE_UPDATES

    /**
     * 检查是否至少有一种规范信息注入开启
     * @return true 如果至少有一种注入开启，false 如果都关闭
     */
    fun isAnySpecInjectionEnabled(context: Context): Boolean = getSpecInjectionMode(context) != SpecInjectionMode.NONE

    /**
     * 验证规范信息注入开关状态，确保至少有一种开启
     * 如果都关闭，则重置为固定默认 Live Updates
     */
    fun validateSpecInjectionSwitches(context: Context) {
        if (!isAnySpecInjectionEnabled(context)) {
            setSpecInjectionMode(context, SpecInjectionMode.LIVE_UPDATES)
            Logger.w(TAG, "规范信息注入模式无效，已重置为 Live Updates")
        }
    }
}
