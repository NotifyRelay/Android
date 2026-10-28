package notifyrelay.data.database.migration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import notifyrelay.base.log.Logger
import notifyrelay.data.PersistenceManager
import notifyrelay.data.Storage
import notifyrelay.data.database.dao.AppDao
import notifyrelay.data.database.dao.AppDeviceDao
import notifyrelay.data.database.dao.NotificationRecordDao
import notifyrelay.data.database.dao.SuperIslandHistoryDao
import notifyrelay.data.database.entity.AppDeviceEntity
import notifyrelay.data.database.entity.AppEntity
import notifyrelay.data.database.entity.NotificationRecordEntity
import notifyrelay.data.database.entity.SuperIslandHistoryEntity
import notifyrelay.data.platform.LegacyStoragePortRegistry

/**
 * 旧存储 → Room 的迁移逻辑。
 *
 * 平台差异（旧文件位置、已安装应用枚举）经 [LegacyStoragePortRegistry] 注入，
 * 本对象只保留与平台无关的解析与落库流程。
 */
object MigrationHelper {
    private const val TAG = "MigrationHelper"

    /** 迁移通知记录。 */
    suspend fun migrateNotifications(notificationRecordDao: NotificationRecordDao) {
        val files = PersistenceManager.getAllNotificationFiles()
        val entities = mutableListOf<NotificationRecordEntity>()

        for (fileName in files) {
            try {
                val deviceId = fileName.removePrefix("notification_records_").removeSuffix(".json")
                val raw = LegacyStoragePortRegistry.current?.readFile(fileName) ?: continue
                val records = Json.parseToJsonElement(raw).asArrayOfObjects()

                for (record in records) {
                    entities.add(
                        NotificationRecordEntity(
                            key = record.stringOr("key") ?: "",
                            deviceUuid = if (deviceId == "local") "本机" else deviceId,
                            packageName = record.stringOr("packageName") ?: "",
                            appName = record.stringOr("appName"),
                            title = record.stringOr("title"),
                            text = record.stringOr("text"),
                            time = record.longOr("time"),
                        ),
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, "迁移文件 " + fileName + " 失败: " + e.message, e)
            }
        }

        if (entities.isNotEmpty()) {
            // 分批插入，避免单次语句过大
            entities.chunked(100).forEach { notificationRecordDao.insertAll(it) }
        }
    }

    /** 迁移超级岛历史记录。 */
    suspend fun migrateSuperIslandHistory(superIslandHistoryDao: SuperIslandHistoryDao) {
        try {
            val raw =
                LegacyStoragePortRegistry.current
                    ?.readNotificationRecords("super_island_history")
                    ?: return
            val oldHistory = Json.parseToJsonElement(raw).asArrayOfObjects()

            val entities =
                oldHistory.mapNotNull { entry ->
                    try {
                        SuperIslandHistoryEntity(
                            id = entry.longOr("id", System.currentTimeMillis()),
                            sourceDeviceUuid = entry.stringOr("sourceDeviceUuid") ?: "",
                            originalPackage = entry.stringOr("originalPackage") ?: "",
                            mappedPackage = entry.stringOr("mappedPackage") ?: "",
                            appName = entry.stringOr("appName"),
                            title = entry.stringOr("title"),
                            text = entry.stringOr("text"),
                            paramV2Raw = entry.stringOr("paramV2Raw"),
                            picMap = entry.stringOr("picMap") ?: "{}",
                            rawPayload = entry.stringOr("rawPayload"),
                        )
                    } catch (_: Exception) {
                        null
                    }
                }

            if (entities.isNotEmpty()) {
                superIslandHistoryDao.insertAll(entities)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "迁移超级岛历史记录失败: " + e.message, e)
        }
    }

    /** 迁移应用信息与图标。 */
    suspend fun migrateApps(
        appDao: AppDao,
        appDeviceDao: AppDeviceDao,
    ) {
        try {
            val installedApps = LegacyStoragePortRegistry.current?.installedApps() ?: return
            val appEntities = mutableListOf<AppEntity>()
            val appDeviceEntities = mutableListOf<AppDeviceEntity>()

            for (app in installedApps) {
                try {
                    val iconBytes = app.iconPngBytes
                    appEntities.add(
                        AppEntity(
                            packageName = app.packageName,
                            appName = app.appName,
                            isSystemApp = app.isSystemApp,
                            iconBytes = iconBytes,
                            isIconMissing = iconBytes == null,
                            lastUpdated = System.currentTimeMillis(),
                        ),
                    )
                    appDeviceEntities.add(
                        AppDeviceEntity(
                            packageName = app.packageName,
                            sourceDevice = "local",
                            lastUpdated = System.currentTimeMillis(),
                        ),
                    )
                } catch (e: Exception) {
                    Logger.w(TAG, "迁移应用信息失败: " + app.packageName, e)
                }
            }

            if (appEntities.isNotEmpty()) {
                appDao.insertAll(appEntities)
                Logger.d(TAG, "迁移应用信息完成，共" + appEntities.size + "条")
            }
            if (appDeviceEntities.isNotEmpty()) {
                appDeviceDao.insertAll(appDeviceEntities)
                Logger.d(TAG, "迁移应用设备关联完成，共" + appDeviceEntities.size + "条")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "迁移应用信息和图标失败: " + e.message, e)
        }
    }

    /** 清理旧的存储文件并标记迁移完成。 */
    fun cleanupLegacyStorage() {
        val port = LegacyStoragePortRegistry.current ?: return
        PersistenceManager.getAllNotificationFiles().forEach { port.deleteFile(it) }
        Storage.putBoolean("migration_completed", true)
    }

    /** 检查是否需要迁移。 */
    fun shouldMigrate(): Boolean = !Storage.getBoolean("migration_completed", false)
}

// ---------- JSON 解析辅助 ----------

/** 把 JSON 元素当作对象数组；非数组时返回空列表。 */
private fun kotlinx.serialization.json.JsonElement.asArrayOfObjects(): List<JsonObject> =
    (this as? kotlinx.serialization.json.JsonArray)
        ?.mapNotNull { it as? JsonObject }
        ?: emptyList()

/** 读取字符串字段；缺失或非字符串返回 null。 */
private fun JsonObject.stringOr(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** 读取长整数字段；缺失时用 [default]。 */
private fun JsonObject.longOr(
    key: String,
    default: Long = 0L,
): Long {
    val p = this[key] as? JsonPrimitive ?: return default
    return p.longOrNull ?: p.intOrNull?.toLong() ?: p.content.toLongOrNull() ?: default
}
