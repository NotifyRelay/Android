package com.xzyht.notifyrelay.feature.device.model

import android.content.Context
import notifyrelay.base.util.Logger
import notifyrelay.data.database.repository.DatabaseRepository

/**
 * 缓存清理 + 历史老化清理单元。
 * 原位于 NotificationRepository（NotificationData.kt），抽离为独立 object 以降低并发/数据层耦合。
 *
 * 契约保留：
 * - cacheCleaner 由监听服务注册，其「空集合 = 清除全部缓存」协议必须保留（见 [clearProcessedCacheAll]）。
 * - cleanupOldNotifications 依赖 deviceList 已由 scanDeviceList 填充（init 中清理前已完成扫描），调用顺序不可变。
 */
internal object NotificationCacheCleaner {
    // 缓存清理回调（由监听服务注册）
    private var cacheCleaner: ((Set<String>) -> Unit)? = null

    /**
     * 注册缓存清理器（由监听服务调用）
     */
    fun registerCacheCleaner(cleaner: (Set<String>) -> Unit) {
        cacheCleaner = cleaner
    }

    /**
     * 清理指定通知的缓存
     */
    fun clearProcessedCache(notificationKeys: Set<String>) {
        cacheCleaner?.invoke(notificationKeys)
    }

    /**
     * 清理全部缓存（仅用于本机设备）
     */
    fun clearProcessedCacheAll() {
        // 传递空集合表示清除全部缓存
        cacheCleaner?.invoke(emptySet())
    }

    /**
     * 清理历史通知，确保每个包名的通知数量不超过80条
     */
    suspend fun cleanupOldNotifications(
        context: Context,
        deviceList: List<String>,
    ) {
        try {
            Logger.i("NotifyRelay", "开始清理历史通知")
            val repository = DatabaseRepository.getInstance(context)

            // 获取所有设备的列表
            val devices = deviceList

            // 对每个设备，清理其通知
            for (device in devices) {
                Logger.i("NotifyRelay", "清理设备 $device 的通知")

                // 获取该设备的所有通知
                val allNotifications = repository.getNotificationsByDevice(device)

                // 按包名分组通知
                val packageNames = allNotifications.map { it.packageName }.distinct()

                // 对每个包名使用批量删除方法
                for (packageName in packageNames) {
                    Logger.i("NotifyRelay", "清理包名 $packageName 的通知")
                    // 使用数据库仓库的批量删除方法，保留最新的80条
                    repository.deleteOldestNotificationsByPackageAndDevice(packageName, device, 80)
                }
            }

            Logger.i("NotifyRelay", "历史通知清理完成")
        } catch (e: Exception) {
            Logger.e("NotifyRelay", "清理历史通知失败: ${e.message}")
        }
    }
}
