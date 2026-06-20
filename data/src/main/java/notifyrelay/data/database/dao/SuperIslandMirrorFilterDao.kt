package notifyrelay.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import notifyrelay.data.database.entity.SuperIslandMirrorFilterEntity

@Dao
interface SuperIslandMirrorFilterDao {
    @Query("SELECT * FROM super_island_mirror_filters WHERE enabled = 1")
    suspend fun getEnabledPackages(): List<SuperIslandMirrorFilterEntity>

    @Query("SELECT * FROM super_island_mirror_filters ORDER BY packageName ASC")
    suspend fun getAllPackages(): List<SuperIslandMirrorFilterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pkg: SuperIslandMirrorFilterEntity)

    @Query("UPDATE super_island_mirror_filters SET enabled = :enabled WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)

    @Query("DELETE FROM super_island_mirror_filters WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("SELECT COUNT(*) FROM super_island_mirror_filters")
    suspend fun count(): Int
}
