package notifyrelay.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 包名等价组（默认组 isDefault = true）
 */
@Entity(tableName = "package_groups")
data class PackageGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupName: String,
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
)

/**
 * 包名等价组内包名
 */
@Entity(
    tableName = "package_group_items",
    primaryKeys = ["groupId", "packageName"],
    foreignKeys = [
        ForeignKey(
            entity = PackageGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId")]
)
data class PackageGroupItemEntity(
    val groupId: Long,
    val packageName: String,
)