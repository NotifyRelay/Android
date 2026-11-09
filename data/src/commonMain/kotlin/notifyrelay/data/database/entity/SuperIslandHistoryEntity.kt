package notifyrelay.data.database.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 超级岛历史记录。
 *
 * 图片引用不在本表：`super_island_history_images` 按（条目, 图片键）存指向 [SuperIslandImageEntity]
 * 的外键。读取时由仓储装配回 [picMap]，写入时由仓储拆成关联行。
 */
@Entity(
    tableName = "super_island_history",
    indices = [
        Index(name = "index_super_island_feature_id", value = ["featureId"]),
        Index(name = "index_super_island_device_feature", value = ["sourceDeviceUuid", "featureId"]),
    ],
)
data class SuperIslandHistoryEntity(
    @PrimaryKey(autoGenerate = false) val id: Long,
    val sourceDeviceUuid: String? = null,
    val originalPackage: String? = null,
    val mappedPackage: String? = null,
    val appName: String? = null,
    val title: String? = null,
    val text: String? = null,
    val paramV2Raw: String? = null,
    val rawPayload: String? = null,
    val featureId: String? = null,
) {
    /**
     * 图片键到图片原文（或历史遗留的图片 id）的映射。
     *
     * 不落库：由仓储在读取时从关联表装配，写入时拆回关联行。
     */
    @Ignore
    var picMap: Map<String, String> = emptyMap()
}
