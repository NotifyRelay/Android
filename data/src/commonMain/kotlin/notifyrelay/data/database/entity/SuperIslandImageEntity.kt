package notifyrelay.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "super_island_images",
    indices = [
        Index(name = "index_super_island_images_contentHash", value = ["contentHash"], unique = true),
    ],
)
data class SuperIslandImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentHash: String,
    val data: String,
    val lastUpdated: Long,
)
