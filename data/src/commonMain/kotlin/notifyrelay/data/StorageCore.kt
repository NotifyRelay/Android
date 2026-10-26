package notifyrelay.data

import notifyrelay.base.log.Logger
import notifyrelay.data.database.repository.DatabaseRepository

/**
 * 存储业务核心（commonMain）。
 *
 * 仅依赖 [DatabaseRepository] 与 shared 模块的 Logger；
 * Context 获取、阻塞调用与线程中断适配由平台侧的 Storage 入口负责。
 */
class StorageCore(
    private val repository: DatabaseRepository,
) {
    /**
     * 根据 [Storage.PrefsType] 为键名添加前缀，避免不同类型的偏好键名冲突。
     */
    private fun getPrefixedKey(
        key: String,
        prefsType: Storage.PrefsType,
    ): String =
        when (prefsType) {
            Storage.PrefsType.GENERAL -> "general_$key"
            Storage.PrefsType.DEVICE -> "device_$key"
            Storage.PrefsType.FILTER -> "filter_$key"
            Storage.PrefsType.SCRCPY -> "scrcpy_$key"
        }

    /** 读取字符串值，键不存在时返回 [default]。 */
    suspend fun getString(
        key: String,
        default: String = "",
        prefsType: Storage.PrefsType = Storage.PrefsType.GENERAL,
    ): String = repository.getConfig(getPrefixedKey(key, prefsType), default)

    /** 写入字符串值。 */
    suspend fun putString(
        key: String,
        value: String,
        prefsType: Storage.PrefsType = Storage.PrefsType.GENERAL,
    ) {
        repository.setConfig(getPrefixedKey(key, prefsType), value)
    }

    /** 读取布尔值，键不存在时返回 [default]。 */
    suspend fun getBoolean(
        key: String,
        default: Boolean = false,
        prefsType: Storage.PrefsType = Storage.PrefsType.GENERAL,
    ): Boolean = repository.getConfig(getPrefixedKey(key, prefsType), default.toString()).toBoolean()

    /** 写入布尔值。 */
    suspend fun putBoolean(
        key: String,
        value: Boolean,
        prefsType: Storage.PrefsType = Storage.PrefsType.GENERAL,
    ) {
        repository.setConfig(getPrefixedKey(key, prefsType), value.toString())
    }

    /** 批量写入布尔值。 */
    suspend fun putBooleans(
        values: Map<String, Boolean>,
        prefsType: Storage.PrefsType = Storage.PrefsType.GENERAL,
    ) {
        for ((key, value) in values) {
            repository.setConfig(getPrefixedKey(key, prefsType), value.toString())
        }
    }

    /** 读取整数，键不存在或无法解析时返回 [default]。 */
    suspend fun getInt(
        key: String,
        default: Int = 0,
        prefsType: Storage.PrefsType = Storage.PrefsType.GENERAL,
    ): Int = repository.getConfig(getPrefixedKey(key, prefsType), default.toString()).toInt()

    /** 写入整数。 */
    suspend fun putInt(
        key: String,
        value: Int,
        prefsType: Storage.PrefsType = Storage.PrefsType.GENERAL,
    ) {
        repository.setConfig(getPrefixedKey(key, prefsType), value.toString())
    }

    /** 读取浮点数，键不存在时返回 [default]。 */
    suspend fun getFloat(
        key: String,
        default: Float = 0f,
        prefsType: Storage.PrefsType = Storage.PrefsType.GENERAL,
    ): Float = repository.getConfig(getPrefixedKey(key, prefsType), default.toString()).toFloat()

    /** 写入浮点数。 */
    suspend fun putFloat(
        key: String,
        value: Float,
        prefsType: Storage.PrefsType = Storage.PrefsType.GENERAL,
    ) {
        repository.setConfig(getPrefixedKey(key, prefsType), value.toString())
    }

    /** 读取长整数，键不存在时返回 [default]。 */
    suspend fun getLong(
        key: String,
        default: Long = 0L,
        prefsType: Storage.PrefsType = Storage.PrefsType.GENERAL,
    ): Long = repository.getConfig(getPrefixedKey(key, prefsType), default.toString()).toLong()

    /** 写入长整数。 */
    suspend fun putLong(
        key: String,
        value: Long,
        prefsType: Storage.PrefsType = Storage.PrefsType.GENERAL,
    ) {
        repository.setConfig(getPrefixedKey(key, prefsType), value.toString())
    }

    /** 批量写入字符串键值对。 */
    suspend fun putStrings(
        values: Map<String, String>,
        prefsType: Storage.PrefsType = Storage.PrefsType.GENERAL,
    ) {
        for ((key, value) in values) {
            repository.setConfig(getPrefixedKey(key, prefsType), value)
        }
    }

    /** 批量写入整数键值对。 */
    suspend fun putInts(
        values: Map<String, Int>,
        prefsType: Storage.PrefsType = Storage.PrefsType.GENERAL,
    ) {
        for ((key, value) in values) {
            repository.setConfig(getPrefixedKey(key, prefsType), value.toString())
        }
    }

    /**
     * 读取字符串集合，键不存在或内容无法解析时返回 [default]。
     * 存储格式为 JSON 数组字符串，与历史 Gson 输出逐字节一致。
     */
    suspend fun getStringSet(
        key: String,
        default: Set<String> = emptySet(),
        prefsType: Storage.PrefsType = Storage.PrefsType.GENERAL,
    ): Set<String> {
        val json = repository.getConfig(getPrefixedKey(key, prefsType), "[]")
        // 解析失败时由 androidMain 门面的异常分支记录日志并返回 default，与历史 Gson 解析异常行为一致
        return StringSetJsonCodec.decode(json) ?: default
    }

    /** 将字符串集合编码为 JSON 数组字符串后写入。 */
    suspend fun putStringSet(
        key: String,
        value: Set<String>,
        prefsType: Storage.PrefsType = Storage.PrefsType.GENERAL,
    ) {
        repository.setConfig(getPrefixedKey(key, prefsType), StringSetJsonCodec.encode(value))
    }

    /** 移除指定键（写入空字符串表示删除）。 */
    suspend fun remove(
        key: String,
        prefsType: Storage.PrefsType = Storage.PrefsType.GENERAL,
    ) {
        repository.setConfig(getPrefixedKey(key, prefsType), "")
    }

    /**
     * 执行偏好数据的迁移操作：读取当前版本号，低于 [toVersion] 时执行迁移并更新版本号。
     * 迁移步骤内部失败时记录警告并继续，线程中断异常向调用方抛出以恢复中断位。
     */
    suspend fun migrateData(toVersion: Int) {
        val currentVersion = repository.getConfig("general_data_version", "0").toInt()
        if (currentVersion >= toVersion) {
            return
        }
        Logger.i(TAG, "正在迁移数据: 从 v$currentVersion 到 v$toVersion")
        if (currentVersion < 1) {
            val oldValue = getStringOrDefault("old_key")
            if (oldValue.isNotEmpty()) {
                putStringSafely("new_key", oldValue)
                removeSafely("old_key")
            }
        }
        repository.setConfig("general_data_version", toVersion.toString())
        Logger.i(TAG, "数据迁移完成")
    }

    private suspend fun getStringOrDefault(key: String): String =
        try {
            getString(key)
        } catch (e: Exception) {
            rethrowIfInterrupted(e)
            Logger.w(TAG, "获取字符串失败，键: $key", e)
            ""
        }

    private suspend fun putStringSafely(
        key: String,
        value: String,
    ) {
        try {
            putString(key, value)
        } catch (e: Exception) {
            rethrowIfInterrupted(e)
            Logger.w(TAG, "写入字符串失败，键: $key", e)
        }
    }

    private suspend fun removeSafely(key: String) {
        try {
            remove(key)
        } catch (e: Exception) {
            rethrowIfInterrupted(e)
            Logger.w(TAG, "移除键失败: $key", e)
        }
    }

    /** commonMain 无法引用 java.lang.InterruptedException，按类型名判定后交由 androidMain 门面恢复中断位。 */
    private fun rethrowIfInterrupted(e: Exception) {
        if (e::class.simpleName == "InterruptedException") {
            throw e
        }
    }

    companion object {
        private const val TAG = "Storage"
    }
}
