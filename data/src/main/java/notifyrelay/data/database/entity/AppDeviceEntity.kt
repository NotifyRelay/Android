package notifyrelay.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 应用设备关联实体类
 * 存储应用与设备的关联关系
 */
@Entity(
    tableName = "app_devices",
    primaryKeys = ["packageName", "sourceDevice"],
    foreignKeys = [
        ForeignKey(
            entity = AppEntity::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sourceDevice"])
    ]
)
data class AppDeviceEntity(
    val packageName: String,
    val sourceDevice: String,
    val lastUpdated: Long
)