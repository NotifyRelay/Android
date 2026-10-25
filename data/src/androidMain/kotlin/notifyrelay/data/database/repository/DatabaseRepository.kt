package notifyrelay.data.database.repository

import android.content.Context
import notifyrelay.data.database.AppDatabase
import notifyrelay.data.database.AppDatabaseFactory

/**
 * 数据库仓库 Android 侧入口
 * 单例缓存与 Context → AppDatabase 的获取由本类承担，业务方法继承自 commonMain 的 DatabaseRepositoryBase
 */
class DatabaseRepository(
    database: AppDatabase,
) : DatabaseRepositoryBase(database) {
    companion object {
        @Volatile
        private var instance: DatabaseRepository? = null

        /**
         * 获取数据库仓库实例（单例模式）
         */
        fun getInstance(context: Context): DatabaseRepository =
            instance ?: synchronized(this) {
                instance ?: DatabaseRepository(AppDatabaseFactory.getDatabase(context)).also { instance = it }
            }
    }
}
