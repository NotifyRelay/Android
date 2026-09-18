package com.xzyht.notifyrelay.feature.device.model

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.xzyht.notifyrelay.sync.notification.data.NotificationRecord
import notifyrelay.base.util.Logger

/**
 * 通知历史内存态（plan.md「步骤 4」抽取）。
 *
 * 持有 UI 直接观察的内存状态（[notifications] / [currentDevice] / [deviceList]）与无锁的扫描/查询逻辑。
 *
 * **不引入任何同步**：拆分前这些状态由 [NotificationRepository] 的监视器（`@Synchronized` 方法）串行保护，
 * 拆分后保持一致 —— 门面仍持有全部 `@Synchronized` 方法，本对象只做状态持有与纯逻辑，
 * 避免 `@Synchronized` + `runBlocking` 组合下因新增第二把锁而产生死锁路径。
 *
 * 契约保留：
 * - [notifications] 必须是 Compose `SnapshotStateList`（UI 直接观察）。
 * - [currentDevice] / [deviceList] 是跨 UI 与 [scanDeviceList] 的全局可变状态，写入口径不变。
 */
internal object NotificationMemoryStore {
    val notifications: SnapshotStateList<NotificationRecord> = mutableStateListOf()

    // 当前选中设备
    var currentDevice: String = "本机"

    // 设备列表，自动维护
    val deviceList: MutableList<String> = mutableListOf("本机")

    // 扫描设备列表：设备信息由 Rust 私有库持有（uuid 仅平台端兜底），
    // 列表数据源改为 DeviceConnectionManager 的已认证设备集合
    fun scanDeviceList(context: Context) {
        // 添加本机设备
        val found = mutableSetOf<String>()
        found.add("本机")

        // 添加已认证设备 UUID（来自 DeviceConnectionManager 内存态，Rust 库为准）
        try {
            com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
                .getInstance(context)
                .getAuthenticatedDevices()
                .keys
                .forEach { uuid ->
                    if (!uuid.isNullOrEmpty() && uuid != "本机") {
                        found.add(uuid)
                    }
                }
        } catch (e: Exception) {
            Logger.w("NotifyRelay", "[scanDeviceList] 获取已认证设备失败", e)
        }

        // 保证本机在首位
        val sorted = found.sortedWith(compareBy({ if (it == "本机") 0 else 1 }, { it }))
        Logger.i("NotifyRelay", "[scanDeviceList] found devices: $sorted")
        deviceList.clear()
        deviceList.addAll(sorted)
    }

    /**
     * 获取指定设备的通知列表。
     * 注：锁由门面 [NotificationRepository.getNotificationsByDevice] 的 `@Synchronized` 提供。
     */
    fun getNotificationsByDevice(device: String): List<NotificationRecord> {
        val filtered = notifications.filter { it.device == device }
        Logger.i("NotifyRelay", "[getNotificationsByDevice] device=$device, found=${filtered.size}")
        return filtered
    }
}
