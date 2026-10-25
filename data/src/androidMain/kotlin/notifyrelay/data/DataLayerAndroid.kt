package notifyrelay.data

import android.content.Context
import notifyrelay.data.database.AppDatabaseFactory
import notifyrelay.data.database.repository.DatabaseRepository

/**
 * Android 侧数据层初始化入口：用应用 Context 创建 Room 数据库并注入公共端的存储与仓储单例。
 *
 * 应用启动时调用 [initialize]；旧调用点（仅持有 Context）可调用 [ensureInitialized] 按需初始化。
 */
object DataLayerAndroid {
    @Volatile
    private var initialized = false

    /** 创建/获取数据库仓库实例（不改变全局初始化状态）。 */
    fun createRepository(context: Context): DatabaseRepository {
        val database = AppDatabaseFactory.getDatabase(context.applicationContext)
        return DatabaseRepository(database)
    }

    /** 初始化公共端存储与仓储单例；重复调用无副作用。 */
    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            Storage.initialize(createRepository(context))
            initialized = true
        }
    }

    /** 供仅持有 Context 的旧调用点按需初始化。 */
    fun ensureInitialized(context: Context) {
        if (!Storage.isInitialized()) initialize(context)
    }
}
