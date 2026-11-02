@file:Suppress("SpellCheckingInspection")

package notifyrelay.data.database

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import notifyrelay.base.log.Logger
import notifyrelay.data.database.migration.MigrationHelper

/**
 * AppDatabase 的 Android 侧构建入口。
 *
 * 仅负责「用 Context 创建 Builder」与「旧存储迁移」，
 * 驱动、迁移脚本与构建流程由 [AppDatabaseBuilder] 统一处理。
 */
object AppDatabaseFactory {
    @Volatile
    private var instance: AppDatabase? = null

    /** 获取数据库实例（单例）。 */
    fun getDatabase(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: AppDatabaseBuilder
                .build(
                    builder =
                        Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            AppDatabaseBuilder.DATABASE_NAME,
                        ),
                    onCreate = {
                        // 数据库创建后执行旧存储迁移（与实例创建解耦，避免回调内引用未就绪的单例）
                        instance?.let { db ->
                            CoroutineScope(Dispatchers.IO).launch {
                                migrateFromLegacyStorage(db)
                            }
                        }
                    },
                ).also { instance = it }
        }

    private suspend fun migrateFromLegacyStorage(database: AppDatabase) {
        if (!MigrationHelper.shouldMigrate()) return
        try {
            MigrationHelper.migrateNotifications(database.notificationRecordDao())
            MigrationHelper.migrateApps(database.appDao(), database.appDeviceDao())
            MigrationHelper.migrateSuperIslandHistory(database.superIslandHistoryDao())
            MigrationHelper.cleanupLegacyStorage()
        } catch (e: Exception) {
            Logger.e("AppDatabase", "迁移失败: ${e.message}", e)
        }
    }
}
