package notifyrelay.data

import android.content.Context
import notifyrelay.shared.log.Logger

/**
 * 兼容外壳：保留带 [Context] 形参的旧存储入口（供 :scrcpy 等既有调用方使用）。
 *
 * 业务实现位于 commonMain 的 [Storage]；本对象只负责「用 Context 确保数据层已初始化」并转发调用。
 * 新代码请直接使用 [Storage]（无 Context）。
 */
object StorageManager {
    /** 偏好集合类型，与 [Storage.PrefsType] 为同一类型。 */
    typealias PrefsType = Storage.PrefsType

    private fun ensureInitialized(context: Context) {
        DataLayerAndroid.ensureInitialized(context)
    }

    /** 读取字符串值，键不存在时返回 [default]。 */
    fun getString(
        context: Context,
        key: String,
        default: String = "",
        prefsType: PrefsType = PrefsType.GENERAL,
    ): String =
        try {
            ensureInitialized(context)
            Storage.getString(key, default, prefsType)
        } catch (e: InterruptedException) {
            Logger.w("StorageManager", "获取字符串时线程被中断，键: $key", e)
            Thread.currentThread().interrupt()
            default
        } catch (e: Exception) {
            Logger.w("StorageManager", "获取字符串失败，键: $key", e)
            default
        }

    /** 写入字符串值。 */
    fun putString(
        context: Context,
        key: String,
        value: String,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) {
        try {
            ensureInitialized(context)
            Storage.putString(key, value, prefsType)
        } catch (e: InterruptedException) {
            Logger.w("StorageManager", "写入字符串时线程被中断，键: $key", e)
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            Logger.w("StorageManager", "写入字符串失败，键: $key", e)
        }
    }

    /** 读取布尔值，键不存在时返回 [default]。 */
    fun getBoolean(
        context: Context,
        key: String,
        default: Boolean = false,
        prefsType: PrefsType = PrefsType.GENERAL,
    ): Boolean =
        try {
            ensureInitialized(context)
            Storage.getBoolean(key, default, prefsType)
        } catch (e: InterruptedException) {
            Logger.w("StorageManager", "获取布尔值时线程被中断，键: $key", e)
            Thread.currentThread().interrupt()
            default
        } catch (e: Exception) {
            Logger.w("StorageManager", "获取布尔值失败，键: $key", e)
            default
        }

    /** 写入布尔值。 */
    fun putBoolean(
        context: Context,
        key: String,
        value: Boolean,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) {
        try {
            ensureInitialized(context)
            Storage.putBoolean(key, value, prefsType)
        } catch (e: InterruptedException) {
            Logger.w("StorageManager", "写入布尔值时线程被中断，键: $key", e)
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            Logger.w("StorageManager", "写入布尔值失败，键: $key", e)
        }
    }

    /** 批量写入布尔值。 */
    fun putBooleans(
        context: Context,
        values: Map<String, Boolean>,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) {
        try {
            ensureInitialized(context)
            Storage.putBooleans(values, prefsType)
        } catch (e: InterruptedException) {
            Logger.w("StorageManager", "批量写入布尔值时线程被中断", e)
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            Logger.w("StorageManager", "批量写入布尔值失败", e)
        }
    }

    /** 读取整数，键不存在时返回 [default]。 */
    fun getInt(
        context: Context,
        key: String,
        default: Int = 0,
        prefsType: PrefsType = PrefsType.GENERAL,
    ): Int =
        try {
            ensureInitialized(context)
            Storage.getInt(key, default, prefsType)
        } catch (e: InterruptedException) {
            Logger.w("StorageManager", "获取整数时线程被中断，键: $key", e)
            Thread.currentThread().interrupt()
            default
        } catch (e: Exception) {
            Logger.w("StorageManager", "获取整数失败，键: $key", e)
            default
        }

    /** 写入整数。 */
    fun putInt(
        context: Context,
        key: String,
        value: Int,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) {
        try {
            ensureInitialized(context)
            Storage.putInt(key, value, prefsType)
        } catch (e: InterruptedException) {
            Logger.w("StorageManager", "写入整数时线程被中断，键: $key", e)
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            Logger.w("StorageManager", "写入整数失败，键: $key", e)
        }
    }

    /** 读取浮点数，键不存在时返回 [default]。 */
    fun getFloat(
        context: Context,
        key: String,
        default: Float = 0f,
        prefsType: PrefsType = PrefsType.GENERAL,
    ): Float =
        try {
            ensureInitialized(context)
            Storage.getFloat(key, default, prefsType)
        } catch (e: InterruptedException) {
            Logger.w("StorageManager", "获取浮点数时线程被中断，键: $key", e)
            Thread.currentThread().interrupt()
            default
        } catch (e: Exception) {
            Logger.w("StorageManager", "获取浮点数失败，键: $key", e)
            default
        }

    /** 写入浮点数。 */
    fun putFloat(
        context: Context,
        key: String,
        value: Float,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) {
        try {
            ensureInitialized(context)
            Storage.putFloat(key, value, prefsType)
        } catch (e: InterruptedException) {
            Logger.w("StorageManager", "写入浮点数时线程被中断，键: $key", e)
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            Logger.w("StorageManager", "写入浮点数失败，键: $key", e)
        }
    }

    /** 读取长整数，键不存在时返回 [default]。 */
    fun getLong(
        context: Context,
        key: String,
        default: Long = 0L,
        prefsType: PrefsType = PrefsType.GENERAL,
    ): Long =
        try {
            ensureInitialized(context)
            Storage.getLong(key, default, prefsType)
        } catch (e: InterruptedException) {
            Logger.w("StorageManager", "获取长整数时线程被中断，键: $key", e)
            Thread.currentThread().interrupt()
            default
        } catch (e: Exception) {
            Logger.w("StorageManager", "获取长整数失败，键: $key", e)
            default
        }

    /** 写入长整数。 */
    fun putLong(
        context: Context,
        key: String,
        value: Long,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) {
        try {
            ensureInitialized(context)
            Storage.putLong(key, value, prefsType)
        } catch (e: InterruptedException) {
            Logger.w("StorageManager", "写入长整数时线程被中断，键: $key", e)
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            Logger.w("StorageManager", "写入长整数失败，键: $key", e)
        }
    }

    /** 批量写入字符串键值对。 */
    fun putStrings(
        context: Context,
        values: Map<String, String>,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) {
        try {
            ensureInitialized(context)
            Storage.putStrings(values, prefsType)
        } catch (e: InterruptedException) {
            Logger.w("StorageManager", "批量写入字符串时线程被中断", e)
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            Logger.w("StorageManager", "批量写入字符串失败", e)
        }
    }

    /** 批量写入整数键值对。 */
    fun putInts(
        context: Context,
        values: Map<String, Int>,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) {
        try {
            ensureInitialized(context)
            Storage.putInts(values, prefsType)
        } catch (e: InterruptedException) {
            Logger.w("StorageManager", "批量写入整数时线程被中断", e)
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            Logger.w("StorageManager", "批量写入整数失败", e)
        }
    }

    /** 读取字符串集合，键不存在或内容无法解析时返回 [default]。 */
    fun getStringSet(
        context: Context,
        key: String,
        default: Set<String> = emptySet(),
        prefsType: PrefsType = PrefsType.GENERAL,
    ): Set<String> =
        try {
            ensureInitialized(context)
            Storage.getStringSet(key, default, prefsType)
        } catch (e: InterruptedException) {
            Logger.w("StorageManager", "获取字符串集合时线程被中断，键: $key", e)
            Thread.currentThread().interrupt()
            default
        } catch (e: Exception) {
            Logger.w("StorageManager", "获取字符串集合失败，键: $key", e)
            default
        }

    /** 写入字符串集合（JSON 数组字符串，与历史 Gson 输出一致）。 */
    fun putStringSet(
        context: Context,
        key: String,
        value: Set<String>,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) {
        try {
            ensureInitialized(context)
            Storage.putStringSet(key, value, prefsType)
        } catch (e: InterruptedException) {
            Logger.w("StorageManager", "写入字符串集合时线程被中断，键: $key", e)
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            Logger.w("StorageManager", "写入字符串集合失败，键: $key", e)
        }
    }

    /** 移除指定键。 */
    fun remove(
        context: Context,
        key: String,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) {
        try {
            ensureInitialized(context)
            Storage.remove(key, prefsType)
        } catch (e: InterruptedException) {
            Logger.w("StorageManager", "移除键时线程被中断，键: $key", e)
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            Logger.w("StorageManager", "移除键失败: $key", e)
        }
    }

    /** 清空指定偏好集合（当前后端不支持，记录告警）。 */
    fun clear(
        context: Context,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) {
        try {
            Storage.clear(prefsType)
        } catch (e: Exception) {
            Logger.w("StorageManager", "清空偏好失败", e)
        }
    }

    /** 执行偏好数据迁移。 */
    @Suppress("UNUSED_PARAMETER")
    fun migrateData(
        context: Context,
        fromVersion: Int,
        toVersion: Int,
    ) {
        try {
            fromVersion // 显式读取参数，保持既有告警口径
            ensureInitialized(context)
            Storage.migrateData(fromVersion, toVersion)
        } catch (e: InterruptedException) {
            Logger.e("StorageManager", "数据迁移时线程被中断", e)
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            Logger.e("StorageManager", "数据迁移失败", e)
        }
    }
}
