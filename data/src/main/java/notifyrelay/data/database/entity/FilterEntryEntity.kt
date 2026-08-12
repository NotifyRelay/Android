package notifyrelay.data.database.entity

import androidx.room.Entity

/**
 * 本地过滤条目（关键词 + 包名，至少一个非空）
 */
@Entity(tableName = "filter_entries", primaryKeys = ["keyword", "packageName"])
data class FilterEntryEntity(
    val keyword: String = "",
    val packageName: String = "",
    val enabled: Boolean = true,
)