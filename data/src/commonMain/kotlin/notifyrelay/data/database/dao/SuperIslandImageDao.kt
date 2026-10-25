package notifyrelay.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import notifyrelay.data.database.entity.SuperIslandImageBindingEntity
import notifyrelay.data.database.entity.SuperIslandImageEntity

@Dao
interface SuperIslandImageDao {
    @Query("SELECT id FROM super_island_images WHERE contentHash = :contentHash LIMIT 1")
    suspend fun getImageIdByHash(contentHash: String): Long?

    @Query("SELECT data FROM super_island_images WHERE id = :imageId LIMIT 1")
    suspend fun getImageDataById(imageId: Long): String?

    @Query(
        """
        SELECT i.data
        FROM super_island_images AS i
        INNER JOIN super_island_image_bindings AS b ON b.imageId = i.id
        WHERE b.packageName = :packageName AND b.imageKey = :imageKey
        LIMIT 1
        """,
    )
    suspend fun getImageDataByBinding(
        packageName: String,
        imageKey: String,
    ): String?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImage(image: SuperIslandImageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBinding(binding: SuperIslandImageBindingEntity)

    @Query("UPDATE super_island_images SET lastUpdated = :lastUpdated WHERE id = :imageId")
    suspend fun touchImage(
        imageId: Long,
        lastUpdated: Long,
    )

    @Query("DELETE FROM super_island_images WHERE lastUpdated < :cutoff")
    suspend fun deleteImagesOlderThan(cutoff: Long)

    @Query(
        """
        DELETE FROM super_island_images
        WHERE id NOT IN (
            SELECT id FROM super_island_images
            ORDER BY lastUpdated DESC
            LIMIT :keepCount
        )
        """,
    )
    suspend fun deleteImagesKeepingLatest(keepCount: Int)

    @Query("DELETE FROM super_island_image_bindings")
    suspend fun clearAllBindings()

    @Query("DELETE FROM super_island_images")
    suspend fun clearAllImages()
}
