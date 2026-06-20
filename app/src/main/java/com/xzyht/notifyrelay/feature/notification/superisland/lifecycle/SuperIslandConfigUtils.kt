package com.xzyht.notifyrelay.feature.notification.superisland.lifecycle

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.xzyht.notifyrelay.feature.notification.superisland.FloatingReplicaManager
import com.xzyht.notifyrelay.feature.notification.superisland.NotificationBroadcastReceiver
import notifyrelay.base.util.Logger
import notifyrelay.data.StorageManager

/**
 * 超级岛配置工具类，提供浮窗和规范信息注入相关的公共方法
 */
object SuperIslandConfigUtils {
    private const val TAG = "SuperIslandConfigUtils"
    private const val SUPER_ISLAND_FLOATING_WINDOW_KEY = "super_island_floating_window"
    private const val SPEC_INJECTION_MODE_KEY = "spec_injection_mode"

    // 注入方式枚举
    enum class SpecInjectionMode {
        SUPER_ISLAND,      // 仅超级岛规范信息注入
        LIVE_UPDATES,      // 仅Live Updates规范信息注入
        BOTH,              // 两者都注入
        NONE               // 都不注入（不应该使用，但为了完整性保留）
    }

    /**
     * 检查浮窗功能是否开启
     */
    fun isFloatingWindowEnabled(context: Context): Boolean {
        return StorageManager.getBoolean(context, SUPER_ISLAND_FLOATING_WINDOW_KEY, FloatingReplicaManager.getDefaultFloatingWindowEnabled())
    }

    /**
     * 获取规范信息注入模式
     */
    fun getSpecInjectionMode(context: Context): SpecInjectionMode {
        val modeOrdinal = StorageManager.getInt(context, SPEC_INJECTION_MODE_KEY, SpecInjectionMode.BOTH.ordinal)
        return SpecInjectionMode.entries.toTypedArray().getOrElse(modeOrdinal) { SpecInjectionMode.BOTH }
    }

    /**
     * 检查超级岛规范信息注入是否开启
     */
    fun isSuperIslandSpecInjectionEnabled(context: Context): Boolean {
        val mode = getSpecInjectionMode(context)
        return mode == SpecInjectionMode.SUPER_ISLAND || mode == SpecInjectionMode.BOTH
    }

    /**
     * 检查Live Updates规范信息注入是否开启
     */
    fun isLiveUpdatesSpecInjectionEnabled(context: Context): Boolean {
        val mode = getSpecInjectionMode(context)
        return mode == SpecInjectionMode.LIVE_UPDATES || mode == SpecInjectionMode.BOTH
    }

    /**
     * 检查是否至少有一种规范信息注入开启
     * @return true 如果至少有一种注入开启，false 如果都关闭
     */
    fun isAnySpecInjectionEnabled(context: Context): Boolean {
        return isSuperIslandSpecInjectionEnabled(context) || isLiveUpdatesSpecInjectionEnabled(context)
    }

    /**
     * 创建通知移除时的删除 PendingIntent
     */
    fun createDeletePendingIntent(context: Context, notificationId: Int): PendingIntent? {
        return PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, NotificationBroadcastReceiver::class.java)
                .putExtra("notificationId", notificationId)
                .setAction("com.xzyht.notifyrelay.ACTION_CLOSE_NOTIFICATION"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 验证规范信息注入开关状态，确保至少有一种开启
     * 如果都关闭，则默认开启两者都注入
     */
    fun validateSpecInjectionSwitches(context: Context) {
        if (!isAnySpecInjectionEnabled(context)) {
            StorageManager.putInt(context, SPEC_INJECTION_MODE_KEY, SpecInjectionMode.BOTH.ordinal)
            Logger.w(TAG, "规范信息注入模式无效，已默认设置为两者都注入")
        }
    }
}
