package notifyrelay.data.database.entity

import androidx.room.Entity

/**
 * 远程黑名单条目
 * keyword 为空字符串表示无关键词
 */
@Entity(tableName = "black_list_entries", primaryKeys = ["packageName", "keyword"])
data class BlackListEntryEntity(
    val packageName: String,
    val keyword: String = "",
    val enabled: Boolean = true,
)