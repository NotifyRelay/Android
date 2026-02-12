package notifyrelay.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import notifyrelay.data.database.entity.AppDeviceEntity

/**
 * 应用设备关联DAO接口
 * 定义应用与设备关联关系的增删改查操作
 */
@Dao
interface AppDeviceDao {
    /**
     * 获取所有应用设备关联
     */
    @Query("SELECT * FROM app_devices")
    fun getAll(): Flow<List<AppDeviceEntity>>
    
    /**
     * 根据包名获取应用设备关联
     */
    @Query("SELECT * FROM app_devices WHERE packageName = :packageName")
    fun getByPackageName(packageName: String): Flow<List<AppDeviceEntity>>
    
    /**
     * 批量根据包名获取应用设备关联
     */
    @Query("SELECT * FROM app_devices WHERE packageName IN (:packageNames)")
    suspend fun getByPackageNames(packageNames: List<String>): List<AppDeviceEntity>
    
    /**
     * 根据设备UUID获取应用设备关联
     */
    @Query("SELECT * FROM app_devices WHERE sourceDevice = :deviceUuid")
    fun getByDeviceUuid(deviceUuid: String): Flow<List<AppDeviceEntity>>
    
    /**
     * 检查应用与设备是否存在关联
     */
    @Query("SELECT * FROM app_devices WHERE packageName = :packageName AND sourceDevice = :deviceUuid")
    suspend fun getByPackageNameAndDeviceUuid(packageName: String, deviceUuid: String): AppDeviceEntity?
    
    /**
     * 插入或更新应用设备关联
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(appDevice: AppDeviceEntity)
    
    /**
     * 批量插入或更新应用设备关联
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(appDevices: List<AppDeviceEntity>)
    
    /**
     * 删除应用设备关联
     */
    @Delete
    suspend fun delete(appDevice: AppDeviceEntity)
    
    /**
     * 根据包名删除应用设备关联
     */
    @Query("DELETE FROM app_devices WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)
    
    /**
     * 根据设备UUID删除应用设备关联
     */
    @Query("DELETE FROM app_devices WHERE sourceDevice = :deviceUuid")
    suspend fun deleteByDeviceUuid(deviceUuid: String)
    
    /**
     * 根据包名和设备UUID删除应用设备关联
     */
    @Query("DELETE FROM app_devices WHERE packageName = :packageName AND sourceDevice = :deviceUuid")
    suspend fun deleteByPackageNameAndDeviceUuid(packageName: String, deviceUuid: String)
}