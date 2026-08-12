package notifyrelay.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import notifyrelay.data.database.entity.BlackListEntryEntity
import notifyrelay.data.database.entity.FilterEntryEntity
import notifyrelay.data.database.entity.PackageGroupEntity
import notifyrelay.data.database.entity.PackageGroupItemEntity
import notifyrelay.data.database.entity.WhiteListEntryEntity

/**
 * 过滤名单 DAO
 * 黑/白名单、本地过滤条目、包名等价组
 */
@Dao
interface FilterListDao {

    // ---------- 远程黑名单 ----------

    @Query("SELECT * FROM black_list_entries")
    suspend fun getAllBlackList(): List<BlackListEntryEntity>

    @Query("DELETE FROM black_list_entries")
    suspend fun clearBlackList()

    // ---------- 远程白名单 ----------

    @Query("SELECT * FROM white_list_entries")
    suspend fun getAllWhiteList(): List<WhiteListEntryEntity>

    @Query("DELETE FROM white_list_entries")
    suspend fun clearWhiteList()

    // ---------- 本地过滤条目 ----------

    @Query("SELECT * FROM filter_entries")
    suspend fun getAllFilterEntries(): List<FilterEntryEntity>

    @Query("DELETE FROM filter_entries")
    suspend fun clearFilterEntries()

    // ---------- 包名等价组 ----------

    @Query("SELECT * FROM package_groups")
    suspend fun getAllPackageGroups(): List<PackageGroupEntity>

    @Query("SELECT * FROM package_group_items")
    suspend fun getAllPackageGroupItems(): List<PackageGroupItemEntity>

    @Query("DELETE FROM package_groups")
    suspend fun clearPackageGroups()

    @Query("DELETE FROM package_group_items")
    suspend fun clearPackageGroupItems()

    // ---------- 写入（全量替换策略） ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlackList(entries: List<BlackListEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWhiteList(entries: List<WhiteListEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilterEntries(entries: List<FilterEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackageGroups(groups: List<PackageGroupEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackageGroupItems(items: List<PackageGroupItemEntity>)

    /** 全量替换黑名单（clear + insert 原子执行） */
    @Transaction
    suspend fun replaceBlackList(entries: List<BlackListEntryEntity>) {
        clearBlackList()
        if (entries.isNotEmpty()) insertBlackList(entries)
    }

    /** 全量替换白名单（clear + insert 原子执行） */
    @Transaction
    suspend fun replaceWhiteList(entries: List<WhiteListEntryEntity>) {
        clearWhiteList()
        if (entries.isNotEmpty()) insertWhiteList(entries)
    }

    /** 全量替换本地过滤条目（clear + insert 原子执行） */
    @Transaction
    suspend fun replaceFilterEntries(entries: List<FilterEntryEntity>) {
        clearFilterEntries()
        if (entries.isNotEmpty()) insertFilterEntries(entries)
    }

    /** 全量替换包名等价组（组 + 组内包名，itemPackages 与 groups 一一对应） */
    @Transaction
    suspend fun replacePackageGroups(groups: List<PackageGroupEntity>, itemPackages: List<List<String>>) {
        clearPackageGroups()
        clearPackageGroupItems()
        if (groups.isEmpty()) return
        val ids = insertPackageGroups(groups)
        val items = ids.zip(itemPackages).flatMap { (groupId, pkgs) ->
            pkgs.map { PackageGroupItemEntity(groupId, it) }
        }
        if (items.isNotEmpty()) insertPackageGroupItems(items)
    }
}