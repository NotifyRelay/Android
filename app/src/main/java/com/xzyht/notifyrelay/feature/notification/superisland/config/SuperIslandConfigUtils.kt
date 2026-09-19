package com.xzyht.notifyrelay.feature.notification.superisland.config

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.xzyht.notifyrelay.feature.notification.superisland.contract.SuperIslandActions
import com.xzyht.notifyrelay.feature.notification.superisland.receiver.NotificationBroadcastReceiver
import com.xzyht.notifyrelay.feature.notification.superisland.replica.FloatingReplicaManager
import notifyrelay.base.util.DeviceUtils
import notifyrelay.base.util.Logger
import notifyrelay.data.StorageManager

/**
 * 超级岛配置工具类，提供浮窗和规范信息注入相关的公共方法
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
     * 检查浮窗功能是否开启
     */
    fun isFloatingWindowEnabled(context: Context): Boolean = StorageManager.getBoolean(context, SUPER_ISLAND_FLOATING_WINDOW_KEY, FloatingReplicaManager.getDefaultFloatingWindowEnabled())

    /**
     * 设置浮窗开关（与通知列表模式互斥）。
     * 开启浮窗时自动关闭通知列表模式。
     */
    fun setFloatingWindowEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        if (enabled && isNotificationListMode(context)) {
            setNotificationListMode(context, false)
            Logger.i(TAG, "浮窗开启，自动关闭通知列表模式（互斥）")
        }
        StorageManager.putBoolean(context, SUPER_ISLAND_FLOATING_WINDOW_KEY, enabled)
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
     */
    fun setNotificationListMode(
        context: Context,
        enabled: Boolean,
    ) {
        if (enabled && isFloatingWindowEnabled(context)) {
            setFloatingWindowEnabled(context, false)
            Logger.i(TAG, "通知列表模式开启，自动关闭浮窗（互斥）")
        }
        StorageManager.putBoolean(context, SUPER_ISLAND_NOTIFICATION_LIST_KEY, enabled)
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
     * 创建通知移除时的删除 PendingIntent
     */
    fun createDeletePendingIntent(
        context: Context,
        notificationId: Int,
    ): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, NotificationBroadcastReceiver::class.java)
                .putExtra("notificationId", notificationId)
                .setAction(SuperIslandActions.CLOSE_NOTIFICATION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

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
