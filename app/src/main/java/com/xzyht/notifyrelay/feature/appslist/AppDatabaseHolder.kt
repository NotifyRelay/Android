package com.xzyht.notifyrelay.feature.appslist

import android.content.Context
import notifyrelay.data.database.repository.DatabaseRepository

/**
 * 应用列表相关仓库共享的 [DatabaseRepository] 懒加载单例持有者。
 *
 * 原先 `databaseRepository` + `databaseRepositoryLock` 由 `AppRepository` 独自持有；
 * 拆分出 `AppIconRepository` / `InstalledAppsRepository` / `RemoteAppsCache` 后，
 * 若各 object 各持一份实例会破坏单例与外键语义，故统一收敛到此处，保持原有的
 * `synchronized` 懒加载语义不变。
 */
internal object AppDatabaseHolder {
    private var databaseRepository: DatabaseRepository? = null
    private val databaseRepositoryLock = Any()

    /**
     * 初始化数据库仓库（幂等，线程安全）。
     *
     * @param context Android 上下文，用于获取 DatabaseRepository 单例。
     */
    fun init(context: Context) {
        synchronized(databaseRepositoryLock) {
            if (databaseRepository == null) {
                val instance: DatabaseRepository = DatabaseRepository.getInstance(context)
                databaseRepository = instance
            }
        }
    }

    /**
     * 获取数据库仓库实例。
     *
     * @return 已初始化则返回实例，否则返回 null（调用方需先调用 [init]）。
     */
    fun get(): DatabaseRepository? = databaseRepository
}
