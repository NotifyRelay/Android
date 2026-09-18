package com.xzyht.notifyrelay.feature.device.model

import android.content.Context
import com.xzyht.notifyrelay.sync.notification.data.NotificationRecordDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import notifyrelay.data.database.entity.NotificationRecordEntity
import notifyrelay.data.database.repository.DatabaseRepository

class NotificationRecordStore(
    private val context: Context,
) {
    // 数据库仓库实例
    private val repository = DatabaseRepository.getInstance(context)

    // 转换方法：将旧的NotificationRecordDto转换为新的Room实体
    private fun convertToRoomEntity(
        old: NotificationRecordDto,
        deviceUuid: String,
    ): NotificationRecordEntity =
        NotificationRecordEntity(
            key = old.key,
            deviceUuid = deviceUuid,
            packageName = old.packageName,
            appName = old.appName,
            title = old.title,
            text = old.text,
            time = old.time,
        )

    // 转换方法：将新的Room实体转换为旧的NotificationRecordEntity
    private fun convertFromRoomEntity(new: NotificationRecordEntity): NotificationRecordDto =
        NotificationRecordDto(
            key = new.key,
            packageName = new.packageName,
            appName = new.appName,
            title = new.title,
            text = new.text,
            time = new.time,
            device = new.deviceUuid,
        )

    internal suspend fun readAll(device: String): MutableList<NotificationRecordDto> {
        val deviceUuid = if (device == "local") "本机" else device
        return withContext(Dispatchers.IO) {
            repository.getNotificationsByDevice(deviceUuid)
        }.map { convertFromRoomEntity(it) }.toMutableList()
    }

    internal suspend fun writeAll(
        list: List<NotificationRecordDto>,
        device: String,
    ) {
        val deviceUuid = if (device == "local") "本机" else device
        withContext(Dispatchers.IO) {
            val roomEntities = list.map { convertToRoomEntity(it, deviceUuid) }
            repository.saveNotifications(roomEntities)
        }
    }

    suspend fun insert(record: NotificationRecordDto) {
        val deviceUuid = if (record.device == "local") "本机" else record.device
        val roomEntity = convertToRoomEntity(record, deviceUuid)
        repository.saveNotification(roomEntity)
    }

    suspend fun getAll(device: String): List<NotificationRecordDto> {
        val deviceUuid = if (device == "local") "本机" else device
        return repository
            .getNotificationsByDevice(deviceUuid)
            .map { convertFromRoomEntity(it) }
            .sortedByDescending { it.time }
    }

    suspend fun deleteByKey(
        key: String,
        device: String,
    ) {
        // 直接调用数据库删除方法，key是唯一的，不需要设备参数
        repository.deleteNotificationByKey(key)
    }

    suspend fun clearByDevice(device: String) {
        val deviceUuid = if (device == "local") "本机" else device
        repository.deleteNotificationsByDevice(deviceUuid)
    }

    suspend fun deleteByPackageAndDevice(
        packageName: String,
        device: String,
    ) {
        val deviceUuid = if (device == "local") "本机" else device
        repository.deleteNotificationsByPackageAndDevice(packageName, deviceUuid)
    }
}

// 单例提供者
object NotifyRelayStoreProvider {
    @Volatile
    private var instance: NotificationRecordStore? = null

    fun getInstance(context: Context): NotificationRecordStore =
        instance ?: synchronized(this) {
            instance ?: NotificationRecordStore(context.applicationContext).also { instance = it }
        }
}
