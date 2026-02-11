package notifyrelay.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import notifyrelay.data.database.entity.AppEntity

/**
 * 应用DAO接口
 * 定义应用信息的增删改查操作
 */
@Dao
interface AppDao {
    /**
     * 获取所有应用
     */
    @Query("SELECT * FROM apps")
    fun getAll(): Flow<List<AppEntity>>
    
    /**
     * 根据包名获取应用
     */
    @Query("SELECT * FROM apps WHERE packageName = :packageName")
    suspend fun getByPackageName(packageName: String): AppEntity?
    
    /**
     * 插入或更新应用
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: AppEntity)
    
    /**
     * 批量插入或更新应用
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<AppEntity>)
    
    /**
     * 删除应用
     */
    @Delete
    suspend fun delete(app: AppEntity)
    
    /**
     * 根据包名删除应用
     */
    @Query("DELETE FROM apps WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)
    
    /**
     * 获取缺失图标的应用
     */
    @Query("SELECT * FROM apps WHERE isIconMissing = 1")
    fun getIconMissingApps(): Flow<List<AppEntity>>
    
    /**
     * 更新图标
     */
    @Query("UPDATE apps SET iconBytes = :iconBytes, isIconMissing = 0, lastUpdated = :lastUpdated WHERE packageName = :packageName")
    suspend fun updateIcon(packageName: String, iconBytes: ByteArray, lastUpdated: Long)
    
    /**
     * 标记图标为缺失
     */
    @Query("UPDATE apps SET isIconMissing = 1, lastUpdated = :lastUpdated WHERE packageName = :packageName")
    suspend fun markIconAsMissing(packageName: String, lastUpdated: Long)
    
    /**
     * 获取过期的应用数据
     */
    @Query("SELECT * FROM apps WHERE lastUpdated < :expiryTime")
    suspend fun getExpiredApps(expiryTime: Long): List<AppEntity>
}