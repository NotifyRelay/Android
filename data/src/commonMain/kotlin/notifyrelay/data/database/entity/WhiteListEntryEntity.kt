package notifyrelay.data.database.entity

import androidx.room.Entity

/**
 * 远程白名单条目
 * keyword 为空字符串表示无关键词
 */
@Entity(tableName = "white_list_entries", primaryKeys = ["packageName", "keyword"])
data class WhiteListEntryEntity(
    val packageName: String,
    val keyword: String = "",
    val enabled: Boolean = true,
)
