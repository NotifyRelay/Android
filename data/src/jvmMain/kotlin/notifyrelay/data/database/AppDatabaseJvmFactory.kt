package notifyrelay.data.database

import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * AppDatabase 的 JVM 侧构建入口。
 *
 * 仅负责「用文件路径创建 Builder」，其余配置由 [AppDatabaseBuilder] 统一处理。
 */
object AppDatabaseJvmFactory {
    /**
     * 创建数据库实例。
     *
     * @param databasePath 数据库文件的绝对路径
     */
    fun create(databasePath: String): AppDatabase =
        AppDatabaseBuilder.build(
            builder =
                Room
                    .databaseBuilder<AppDatabase>(name = databasePath)
                    .setQueryCoroutineContext(Dispatchers.IO),
        )
}
