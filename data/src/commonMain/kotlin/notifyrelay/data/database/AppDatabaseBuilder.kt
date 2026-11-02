package notifyrelay.data.database

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * 数据库构建的公共配置。
 *
 * 平台差异只在于「如何拿到 [RoomDatabase.Builder]」：
 * - Android：`Room.databaseBuilder(context, AppDatabase::class.java, name)`
 * - JVM：`Room.databaseBuilder<AppDatabase>(name)`
 *
 * 拿到 Builder 之后，驱动、迁移、回调与构建过程全部与平台无关，
 * 因此收敛到本文件，两个平台共用同一份配置。
 */
object AppDatabaseBuilder {
    /** 数据库文件名。 */
    const val DATABASE_NAME = "notify_relay.db"

    /**
     * 对平台提供的 [builder] 施加全部公共配置并构建实例。
     *
     * @param builder 平台侧按各自 API 创建的 Builder
     * @param onCreate 数据库首次创建后的回调（平台侧据此执行旧存储迁移）
     */
    fun build(
        builder: RoomDatabase.Builder<AppDatabase>,
        onCreate: (() -> Unit)? = null,
    ): AppDatabase {
        val configured =
            builder
                .setDriver(BundledSQLiteDriver())
                .addMigrations(*AppDatabaseMigrations.ALL)
        if (onCreate != null) {
            configured.addCallback(
                object : RoomDatabase.Callback() {
                    override fun onCreate(connection: androidx.sqlite.SQLiteConnection) {
                        super.onCreate(connection)
                        onCreate()
                    }
                },
            )
        }
        return configured.build()
    }
}
