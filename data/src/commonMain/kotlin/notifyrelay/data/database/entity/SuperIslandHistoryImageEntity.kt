package notifyrelay.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 超级岛历史条目引用的图片。
 *
 * 每个（历史条目，图片键）指向一张已去重的图片；图片原文存 [SuperIslandImageEntity]，
 * 同内容图片在多条历史间共用一行，删除图片行时关联随之级联删除。
 *
 * 取代了原先 `super_island_history.picMap` 里的 JSON 映射：那种写法让「图片键 → 图片 id」
 * 无法被查询、无法建索引，也无法让外键生效。
 */
@Entity(
    tableName = "super_island_history_images",
    primaryKeys = ["historyId", "imageKey"],
    foreignKeys = [
        ForeignKey(
            entity = SuperIslandHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["historyId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SuperIslandImageEntity::class,
            parentColumns = ["id"],
            childColumns = ["imageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(name = "index_super_island_history_images_historyId", value = ["historyId"]),
        Index(name = "index_super_island_history_images_imageId", value = ["imageId"]),
    ],
)
data class SuperIslandHistoryImageEntity(
    val historyId: Long,
    val imageKey: String,
    val imageId: Long,
)
