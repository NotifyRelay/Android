@file:Suppress(
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
)

package notifyrelay.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import notifyrelay.data.database.dao.AppConfigDao
import notifyrelay.data.database.dao.AppDao
import notifyrelay.data.database.dao.AppDeviceDao
import notifyrelay.data.database.dao.FilterListDao
import notifyrelay.data.database.dao.NotificationRecordDao
import notifyrelay.data.database.dao.SuperIslandHistoryDao
import notifyrelay.data.database.dao.SuperIslandImageDao
import notifyrelay.data.database.dao.SuperIslandMirrorFilterDao
import notifyrelay.data.database.entity.AppConfigEntity
import notifyrelay.data.database.entity.AppDeviceEntity
import notifyrelay.data.database.entity.AppEntity
import notifyrelay.data.database.entity.BlackListEntryEntity
import notifyrelay.data.database.entity.FilterEntryEntity
import notifyrelay.data.database.entity.NotificationRecordEntity
import notifyrelay.data.database.entity.PackageGroupEntity
import notifyrelay.data.database.entity.PackageGroupItemEntity
import notifyrelay.data.database.entity.SuperIslandHistoryEntity
import notifyrelay.data.database.entity.SuperIslandImageBindingEntity
import notifyrelay.data.database.entity.SuperIslandImageEntity
import notifyrelay.data.database.entity.SuperIslandMirrorFilterEntity
import notifyrelay.data.database.entity.WhiteListEntryEntity

/**
 * Room数据库核心类
 * 定义数据库的版本、实体类和DAO接口
 *
 * 版本历史：
 * v1: 初始版本
 * v2: 删除notification_records表外键约束
 * v3: super_island_history表新增featureId字段
 * v4: 新增apps表和app_devices表
 * v5: notification_records表新增复合索引
 * v6: 新增超级岛图片去重表与绑定表
 * v7: 新增super_island_mirror_filters表
 * v8: 名单类配置从app_config移出为独立表（black_list_entries、white_list_entries、filter_entries、package_groups、package_group_items），镜像过滤默认包名入库
 */
@Suppress(
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
    "SpellCheckingInspection",
)
@Database(
    entities = [
        AppConfigEntity::class,
        AppEntity::class,
        AppDeviceEntity::class,
        NotificationRecordEntity::class,
        SuperIslandHistoryEntity::class,
        SuperIslandImageEntity::class,
        SuperIslandImageBindingEntity::class,
        SuperIslandMirrorFilterEntity::class,
        BlackListEntryEntity::class,
        WhiteListEntryEntity::class,
        FilterEntryEntity::class,
        PackageGroupEntity::class,
        PackageGroupItemEntity::class,
    ],
    version = 9,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    // DAO接口
    abstract fun appConfigDao(): AppConfigDao

    abstract fun appDao(): AppDao

    abstract fun appDeviceDao(): AppDeviceDao

    abstract fun notificationRecordDao(): NotificationRecordDao

    abstract fun superIslandHistoryDao(): SuperIslandHistoryDao

    abstract fun superIslandImageDao(): SuperIslandImageDao

    abstract fun superIslandMirrorFilterDao(): SuperIslandMirrorFilterDao

    abstract fun filterListDao(): FilterListDao
}
