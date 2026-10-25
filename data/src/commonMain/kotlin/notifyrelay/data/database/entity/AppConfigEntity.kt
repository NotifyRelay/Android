package notifyrelay.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 应用配置实体类
 * 对应键值配置表中的通用与设备偏好项
 */
@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey val key: String,
    val value: String,
)
