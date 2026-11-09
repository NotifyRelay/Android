package notifyrelay.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomWarnings
import notifyrelay.data.database.entity.SuperIslandHistoryEntity
import notifyrelay.data.database.entity.SuperIslandHistoryImageEntity

data class SuperIslandPackageCount(
    val packageName: String,
    val count: Int,
    val latestTime: Long,
)

/**
 * 超级岛历史记录DAO
 * 定义超级岛历史记录的数据库操作
 */
@Dao
interface SuperIslandHistoryDao {
    /**
     * 获取所有超级岛历史记录
     */
    @Query("SELECT * FROM super_island_history ORDER BY id DESC")
    suspend fun getAllHistory(): List<SuperIslandHistoryEntity>

    /**
     * 读取列表用的历史条目（不含 rawPayload 大字段）。
     *
     * 返回完整实体而不是单独的投影类型：`picMap` 是 `@Ignore` 的装配字段，查不到也不会报缺失。
     */
    @Query("SELECT id, sourceDeviceUuid, originalPackage, mappedPackage, appName, title, text, paramV2Raw, featureId FROM super_island_history ORDER BY id DESC")
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    suspend fun getAllHistorySummary(): List<SuperIslandHistoryEntity>

    /**
     * 根据特征ID获取最新的历史记录
     */
    @Query("SELECT * FROM super_island_history WHERE featureId = :featureId ORDER BY id DESC LIMIT 1")
    suspend fun getLatestByFeatureId(featureId: String): SuperIslandHistoryEntity?

    /**
     * 根据特征ID删除所有历史记录
     */
    @Query("DELETE FROM super_island_history WHERE featureId = :featureId")
    suspend fun deleteByFeatureId(featureId: String)

    /**
     * 根据特征ID和内容更新记录（如果存在相同特征ID和内容的记录则更新，否则插入）
     * 注意：相同特征ID但内容不同的记录会被保留
     */
    suspend fun upsertByFeatureAndContent(history: SuperIslandHistoryEntity) {
        // 这里不实现UPSERT，因为我们希望保留相同特征ID但内容不同的记录
        // 实际的去重逻辑在应用层实现
        insert(history)
    }

    /**
     * 获取每个特征ID对应的最新一条记录
     */
    @Query("SELECT * FROM super_island_history WHERE id IN (SELECT MAX(id) FROM super_island_history GROUP BY featureId) ORDER BY id DESC")
    suspend fun getLatestByDistinctFeatureId(): List<SuperIslandHistoryEntity>

    /**
     * 插入超级岛历史记录（冲突时替换）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(history: List<SuperIslandHistoryEntity>)

    /**
     * 插入单条超级岛历史记录（冲突时替换）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: SuperIslandHistoryEntity)

    /**
     * 清空所有超级岛历史记录
     */
    @Query("DELETE FROM super_island_history")
    suspend fun clearAll()

    /**
     * 获取最新的N条超级岛历史记录
     */
    @Query("SELECT * FROM super_island_history ORDER BY id DESC LIMIT :limit")
    suspend fun getLatestHistory(limit: Int): List<SuperIslandHistoryEntity>

    /**
     * 获取指定 id 的完整记录（包含 rawPayload），按需加载大字段
     */
    @Query("SELECT * FROM super_island_history WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SuperIslandHistoryEntity?

    /**
     * 仅按 id 获取 rawPayload 字段，便于按需加载大字符串
     */
    @Query("SELECT rawPayload FROM super_island_history WHERE id = :id LIMIT 1")
    suspend fun getRawPayloadById(id: Long): String?

    /**
     * 删除指定数量的旧记录，保留最新的记录
     */
    @Query("DELETE FROM super_island_history WHERE id NOT IN (SELECT id FROM super_island_history ORDER BY id DESC LIMIT :keepCount)")
    suspend fun deleteOldestRecords(keepCount: Int)

    /**
     * 删除单条记录
     */
    @Delete
    suspend fun delete(history: SuperIslandHistoryEntity)

    /**
     * 获取按包名分组的统计信息
     * 优先使用 mappedPackage，如果为空则使用 originalPackage
     */
    @Query(
        """
        SELECT mappedPackage as packageName, COUNT(*) as count, MAX(id) as latestTime
        FROM super_island_history
        WHERE mappedPackage IS NOT NULL AND mappedPackage != ''
        GROUP BY mappedPackage
        UNION
        SELECT originalPackage as packageName, COUNT(*) as count, MAX(id) as latestTime
        FROM super_island_history
        WHERE (mappedPackage IS NULL OR mappedPackage = '') AND originalPackage IS NOT NULL AND originalPackage != ''
        GROUP BY originalPackage
        ORDER BY latestTime DESC
    """,
    )
    suspend fun getPackageCount(): List<SuperIslandPackageCount>

    /**
     * 获取未知包名（无包名）的记录统计
     */
    @Query(
        """
        SELECT '(未知应用)' as packageName, COUNT(*) as count, MAX(id) as latestTime
        FROM super_island_history
        WHERE (mappedPackage IS NULL OR mappedPackage = '') AND (originalPackage IS NULL OR originalPackage = '')
    """,
    )
    suspend fun getUnknownPackageCount(): SuperIslandPackageCount?

    /**
     * 按包名分页获取历史记录摘要
     * @param packageName 包名，传入 null 表示获取未知包名的记录
     * @param limit 每页数量
     * @param offset 偏移量
     */
    @Query(
        """
        SELECT id, sourceDeviceUuid, originalPackage, mappedPackage, appName, title, text, paramV2Raw, featureId
        FROM super_island_history
        WHERE 
            CASE 
                WHEN :packageName IS NULL THEN (mappedPackage IS NULL OR mappedPackage = '') AND (originalPackage IS NULL OR originalPackage = '')
                ELSE (mappedPackage = :packageName OR (mappedPackage IS NULL OR mappedPackage = '') AND originalPackage = :packageName)
            END
        ORDER BY id DESC
        LIMIT :limit OFFSET :offset
    """,
    )
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    suspend fun getByPackage(
        packageName: String?,
        limit: Int,
        offset: Int,
    ): List<SuperIslandHistoryEntity>

    /**
     * 按包名获取所有历史记录摘要（不分页，用于分组内展示）
     */
    @Query(
        """
        SELECT id, sourceDeviceUuid, originalPackage, mappedPackage, appName, title, text, paramV2Raw, featureId
        FROM super_island_history
        WHERE 
            CASE 
                WHEN :packageName IS NULL THEN (mappedPackage IS NULL OR mappedPackage = '') AND (originalPackage IS NULL OR originalPackage = '')
                ELSE (mappedPackage = :packageName OR (mappedPackage IS NULL OR mappedPackage = '') AND originalPackage = :packageName)
            END
        ORDER BY id DESC
    """,
    )
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    suspend fun getAllByPackage(packageName: String?): List<SuperIslandHistoryEntity>

    /**
     * 按包名删除历史记录
     */
    @Query(
        """
        DELETE FROM super_island_history
        WHERE 
            CASE 
                WHEN :packageName IS NULL THEN (mappedPackage IS NULL OR mappedPackage = '') AND (originalPackage IS NULL OR originalPackage = '')
                ELSE (mappedPackage = :packageName OR (mappedPackage IS NULL OR mappedPackage = '') AND originalPackage = :packageName)
            END
    """,
    )
    suspend fun deleteByPackage(packageName: String?)

    /**
     * 按ID删除单条记录
     */
    @Query("DELETE FROM super_island_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 获取总记录数
     */
    @Query("SELECT COUNT(*) FROM super_island_history")
    suspend fun getCount(): Int

    /** 取单条历史的图片引用。 */
    @Query("SELECT * FROM super_island_history_images WHERE historyId = :historyId")
    suspend fun imagesOf(historyId: Long): List<SuperIslandHistoryImageEntity>

    /** 批量取一组历史的图片引用。 */
    @Query("SELECT * FROM super_island_history_images WHERE historyId IN (:historyIds)")
    suspend fun imagesOf(historyIds: List<Long>): List<SuperIslandHistoryImageEntity>

    /** 写入图片引用（同键覆盖）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImages(images: List<SuperIslandHistoryImageEntity>)

    /** 删除某条历史的全部图片引用。 */
    @Query("DELETE FROM super_island_history_images WHERE historyId = :historyId")
    suspend fun deleteImagesOf(historyId: Long)
}
