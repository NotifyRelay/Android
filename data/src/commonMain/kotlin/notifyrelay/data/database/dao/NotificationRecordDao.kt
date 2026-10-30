package notifyrelay.data.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import notifyrelay.data.database.entity.NotificationRecordEntity

/**
 * 通知记录DAO接口
 * 定义通知记录的增删改查操作
 */
@Dao
interface NotificationRecordDao {
    /**
     * 根据设备UUID获取通知记录
     */
    @Query("SELECT * FROM notification_records WHERE deviceUuid = :deviceUuid ORDER BY time DESC")
    suspend fun getByDevice(deviceUuid: String): List<NotificationRecordEntity>

    /**
     * 获取所有通知记录
     */
    @Query("SELECT * FROM notification_records ORDER BY time DESC")
    suspend fun getAll(): List<NotificationRecordEntity>

    /**
     * 根据key获取通知记录
     */
    @Query("SELECT * FROM notification_records WHERE key = :key")
    suspend fun getByKey(key: String): NotificationRecordEntity?

    /**
     * 插入或更新通知记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: NotificationRecordEntity)

    /**
     * 批量插入或更新通知记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<NotificationRecordEntity>)

    /**
     * 删除通知记录
     */
    @Delete
    suspend fun delete(record: NotificationRecordEntity)

    /**
     * 根据key删除通知记录
     */
    @Query("DELETE FROM notification_records WHERE key = :key")
    suspend fun deleteByKey(key: String)

    /**
     * 根据设备UUID删除所有通知记录
     */
    @Query("DELETE FROM notification_records WHERE deviceUuid = :deviceUuid")
    suspend fun deleteByDevice(deviceUuid: String)

    /**
     * 删除指定时间之前的通知记录
     */
    @Query("DELETE FROM notification_records WHERE time < :timeThreshold")
    suspend fun deleteOldRecords(timeThreshold: Long)

    /**
     * 获取设备的通知记录数量
     */
    @Query("SELECT COUNT(*) FROM notification_records WHERE deviceUuid = :deviceUuid")
    suspend fun countByDevice(deviceUuid: String): Int

    /**
     * 根据包名和设备UUID获取通知记录数量
     */
    @Query("SELECT COUNT(*) FROM notification_records WHERE packageName = :packageName AND deviceUuid = :deviceUuid")
    suspend fun countByPackageAndDevice(
        packageName: String,
        deviceUuid: String,
    ): Int

    /**
     * 根据包名和设备UUID删除通知记录
     */
    @Query("DELETE FROM notification_records WHERE packageName = :packageName AND deviceUuid = :deviceUuid")
    suspend fun deleteByPackageAndDevice(
        packageName: String,
        deviceUuid: String,
    )

    /**
     * 判断是否已存在同内容通知记录。
     *
     * 用于跨重启的内容级判重：本机通知每次采集的时间戳不同，
     * 不能以「时间+包名+设备」作键，需按「设备 + 包名 + 标题 + 内容」比对
     * （与 Windows `NotificationRepository` 的聚合键同思路）。
     */
    @Query("SELECT COUNT(*) FROM notification_records WHERE deviceUuid = :deviceUuid AND packageName = :packageName AND title = :title AND text = :text")
    suspend fun countByContent(
        deviceUuid: String,
        packageName: String,
        title: String,
        text: String,
    ): Int

    /**
     * 跨全部设备统计「同包名 + 同应用名 + 同标题 + 同正文」的记录数。
     *
     * 用于相似度窗口之外的旧记录判定：不限时间，仅比对完全一致。
     */
    @Query(
        "SELECT COUNT(*) FROM notification_records WHERE packageName = :packageName " +
            "AND IFNULL(appName, '') = :appName AND IFNULL(title, '') = :title AND IFNULL(text, '') = :text",
    )
    suspend fun countByExactContent(
        packageName: String,
        appName: String,
        title: String,
        text: String,
    ): Int

    /**
     * 取指定时刻之后、同一包名的记录，按时间倒序限制条数。
     *
     * 作为相似度比对的候选集，避免全表扫描。
     */
    @Query("SELECT * FROM notification_records WHERE packageName = :packageName AND time >= :sinceTime ORDER BY time DESC LIMIT :limit")
    suspend fun getRecentByPackage(
        packageName: String,
        sinceTime: Long,
        limit: Int,
    ): List<NotificationRecordEntity>

    /**
     * 根据包名和设备UUID获取通知记录，按时间降序排序
     */
    @Query("SELECT * FROM notification_records WHERE packageName = :packageName AND deviceUuid = :deviceUuid ORDER BY time DESC")
    suspend fun getByPackageAndDevice(
        packageName: String,
        deviceUuid: String,
    ): List<NotificationRecordEntity>

    /**
     * 根据包名和设备UUID删除最旧的通知记录
     * @param limit 要删除的记录数量
     */
    @Query("DELETE FROM notification_records WHERE key IN (SELECT key FROM notification_records WHERE packageName = :packageName AND deviceUuid = :deviceUuid ORDER BY time ASC LIMIT :limit)")
    suspend fun deleteOldestByPackageAndDevice(
        packageName: String,
        deviceUuid: String,
        limit: Int,
    )

    /**
     * 获取分页数据源（按设备分组使用）
     */
    @Query("SELECT * FROM notification_records WHERE deviceUuid = :deviceUuid ORDER BY time DESC")
    fun getPagingSourceByDevice(deviceUuid: String): PagingSource<Int, NotificationRecordEntity>

    /**
     * 获取分组后的通知数量统计（按最新时间排序）
     */
    @Query(
        """
        SELECT packageName, COUNT(*) as count, MAX(time) as latestTime
        FROM notification_records
        WHERE deviceUuid = :deviceUuid
        GROUP BY packageName
        ORDER BY latestTime DESC
    """,
    )
    suspend fun getPackageCountByDevice(deviceUuid: String): List<PackageCount>
}

/**
 * 包名统计
 */
data class PackageCount(
    val packageName: String,
    val count: Int,
    val latestTime: Long,
)
