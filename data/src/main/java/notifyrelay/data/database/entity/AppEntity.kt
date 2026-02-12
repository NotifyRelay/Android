package notifyrelay.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 应用实体类
 * 存储应用基本信息和图标
 */
@Entity(tableName = "apps")
data class AppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val iconBytes: ByteArray?,
    val isIconMissing: Boolean,
    val lastUpdated: Long
)