package notifyrelay.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "super_island_mirror_filters")
data class SuperIslandMirrorFilterEntity(
    @PrimaryKey val packageName: String,
    val enabled: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis(),
)
