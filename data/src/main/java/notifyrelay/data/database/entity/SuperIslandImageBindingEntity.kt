package notifyrelay.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "super_island_image_bindings",
    primaryKeys = ["packageName", "imageKey"],
    foreignKeys = [
        ForeignKey(
            entity = SuperIslandImageEntity::class,
            parentColumns = ["id"],
            childColumns = ["imageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(name = "index_super_island_image_bindings_imageId", value = ["imageId"]),
        Index(name = "index_super_island_image_bindings_packageName", value = ["packageName"]),
        Index(name = "index_super_island_image_bindings_imageKey", value = ["imageKey"]),
    ],
)
data class SuperIslandImageBindingEntity(
    val packageName: String,
    val imageKey: String,
    val imageId: Long,
    val lastUpdated: Long,
)
