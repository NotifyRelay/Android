package notifyrelay.data

import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.shared.log.Logger

/**
 * 平台无关的存储入口（Context-free）。
 *
 * 存储后端由平台侧在启动时注入（Android 为 `DataLayerAndroid.initialize(context)`），
 * 之后各平台的偏好读写统一经由本对象完成；所有方法为阻塞式，内部通过 [runBlockingCommon] 等待。
 */
object Storage {
    /** 偏好集合类型枚举：用于区分不同用途的存储命名空间。 */
    enum class PrefsType {
        /** 通用偏好（应用范围） */
        GENERAL,

        /** 设备相关偏好 */
        DEVICE,

        /** 过滤器相关偏好 */
        FILTER,

        /** Scrcpy 模块偏好 */
        SCRCPY,
    }

    private const val TAG = "Storage"

    @Volatile
    private var repository: DatabaseRepository? = null

    /**
     * 注入存储后端；重复调用以最后一次为准。
     *
     * @param repository 已打开的数据库仓库。
     */
    fun initialize(repository: DatabaseRepository) {
        DatabaseRepository.initialize(repository)
        this.repository = repository
    }

    /** 存储后端是否已初始化。 */
    fun isInitialized(): Boolean = repository != null

    private fun core(): StorageManagerCore = StorageManagerCore(repository ?: error("存储层未初始化：请先调用 Storage.initialize(repository)"))

    /** 读取字符串值，键不存在时返回 [default]。 */
    fun getString(
        key: String,
        default: String = "",
        prefsType: PrefsType = PrefsType.GENERAL,
    ): String = runBlockingCommon { core().getString(key, default, prefsType) }

    /** 写入字符串值。 */
    fun putString(
        key: String,
        value: String,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) = runBlockingCommon { core().putString(key, value, prefsType) }

    /** 读取布尔值，键不存在时返回 [default]。 */
    fun getBoolean(
        key: String,
        default: Boolean = false,
        prefsType: PrefsType = PrefsType.GENERAL,
    ): Boolean = runBlockingCommon { core().getBoolean(key, default, prefsType) }

    /** 写入布尔值。 */
    fun putBoolean(
        key: String,
        value: Boolean,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) = runBlockingCommon { core().putBoolean(key, value, prefsType) }

    /** 批量写入布尔值。 */
    fun putBooleans(
        values: Map<String, Boolean>,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) = runBlockingCommon { core().putBooleans(values, prefsType) }

    /** 读取整数，键不存在时返回 [default]。 */
    fun getInt(
        key: String,
        default: Int = 0,
        prefsType: PrefsType = PrefsType.GENERAL,
    ): Int = runBlockingCommon { core().getInt(key, default, prefsType) }

    /** 写入整数。 */
    fun putInt(
        key: String,
        value: Int,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) = runBlockingCommon { core().putInt(key, value, prefsType) }

    /** 读取浮点数，键不存在时返回 [default]。 */
    fun getFloat(
        key: String,
        default: Float = 0f,
        prefsType: PrefsType = PrefsType.GENERAL,
    ): Float = runBlockingCommon { core().getFloat(key, default, prefsType) }

    /** 写入浮点数。 */
    fun putFloat(
        key: String,
        value: Float,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) = runBlockingCommon { core().putFloat(key, value, prefsType) }

    /** 读取长整数，键不存在时返回 [default]。 */
    fun getLong(
        key: String,
        default: Long = 0L,
        prefsType: PrefsType = PrefsType.GENERAL,
    ): Long = runBlockingCommon { core().getLong(key, default, prefsType) }

    /** 写入长整数。 */
    fun putLong(
        key: String,
        value: Long,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) = runBlockingCommon { core().putLong(key, value, prefsType) }

    /** 批量写入字符串键值对。 */
    fun putStrings(
        values: Map<String, String>,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) = runBlockingCommon { core().putStrings(values, prefsType) }

    /** 批量写入整数键值对。 */
    fun putInts(
        values: Map<String, Int>,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) = runBlockingCommon { core().putInts(values, prefsType) }

    /** 读取字符串集合，键不存在或内容无法解析时返回 [default]。 */
    fun getStringSet(
        key: String,
        default: Set<String> = emptySet(),
        prefsType: PrefsType = PrefsType.GENERAL,
    ): Set<String> = runBlockingCommon { core().getStringSet(key, default, prefsType) }

    /** 写入字符串集合（JSON 数组字符串，与历史 Gson 输出一致）。 */
    fun putStringSet(
        key: String,
        value: Set<String>,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) = runBlockingCommon { core().putStringSet(key, value, prefsType) }

    /** 移除指定键（写入空字符串表示删除）。 */
    fun remove(
        key: String,
        prefsType: PrefsType = PrefsType.GENERAL,
    ) = runBlockingCommon { core().remove(key, prefsType) }

    /**
     * 清空指定偏好集合。
     *
     * 当前存储后端不支持按集合批量删除，调用仅记录告警。
     */
    @Suppress("UNUSED_PARAMETER")
    fun clear(prefsType: PrefsType = PrefsType.GENERAL) {
        Logger.w(TAG, "clear 暂不支持：存储后端没有按集合批量删除的入口")
    }

    /**
     * 执行偏好数据迁移：读取当前版本号，低于 [toVersion] 时执行迁移并更新版本号。
     *
     * @param fromVersion 起始版本（保留调用方语义，当前不参与迁移决策）。
     * @param toVersion 目标版本号。
     */
    @Suppress("UNUSED_PARAMETER")
    fun migrateData(
        fromVersion: Int,
        toVersion: Int,
    ) = runBlockingCommon { core().migrateData(toVersion) }
}
