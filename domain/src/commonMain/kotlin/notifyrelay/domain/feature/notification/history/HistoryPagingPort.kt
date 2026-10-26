package notifyrelay.domain.feature.notification.history

import androidx.compose.ui.graphics.ImageBitmap
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.StateFlow

/** 应用图标信息（包名 → 显示名 + 图标）。 */
data class AppIconInfo(
    val appName: String,
    val icon: ImageBitmap?,
)

/**
 * 通知历史端口。
 */
interface NotificationHistoryPort {
    /** 构造指定设备的分页源。 */
    fun pagingSource(
        deviceUuid: String,
        installedPackages: Set<String>,
    ): PagingSource<Int, GroupedNotifications>

    /** 设备列表。 */
    fun deviceList(): List<String>

    /** 切换到指定设备（刷新该设备的历史）。 */
    fun selectDevice(device: String)

    /** 通知变化信号。 */
    val historyChanged: StateFlow<Long>

    /** 已安装包名集合。 */
    fun installedPackages(): Set<String>

    /** 图标更新信号。 */
    val iconUpdates: StateFlow<Pair<String, Long>?>

    /** 批量取图标信息。 */
    suspend fun appIcons(packageNames: List<String>): Map<String, AppIconInfo>

    /** 删除单条通知。 */
    suspend fun deleteNotification(key: String)

    /** 删除指定包名的分组。 */
    suspend fun deleteGroup(packageName: String)

    /** 清空全部历史。 */
    suspend fun clearHistory()
}

/**
 * 超级岛历史端口。
 */
interface SuperIslandHistoryPort {
    /** 构造分页源。 */
    fun pagingSource(): PagingSource<Int, GroupedSuperIslandHistory>

    /** 变化信号。 */
    val historyChanged: StateFlow<Long>

    /** 图标更新信号。 */
    val iconUpdates: StateFlow<Pair<String, Long>?>

    /** 批量取图标信息。 */
    suspend fun appIcons(packageNames: List<String>): Map<String, AppIconInfo>

    /** 读取条目详情。 */
    suspend fun loadEntryDetail(id: Long): SuperIslandHistoryEntry?

    /** 删除单条历史。 */
    suspend fun deleteEntry(id: Long)

    /** 删除指定包名的分组。 */
    suspend fun deleteGroup(packageName: String)

    /** 清空全部历史。 */
    suspend fun clearHistory()
}

/** [NotificationHistoryPort] 的注册点。 */
object NotificationHistoryPortRegistry {
    @Volatile
    var current: NotificationHistoryPort? = null
        private set

    fun install(port: NotificationHistoryPort) {
        current = port
    }
}

/** [SuperIslandHistoryPort] 的注册点。 */
object SuperIslandHistoryPortRegistry {
    @Volatile
    var current: SuperIslandHistoryPort? = null
        private set

    fun install(port: SuperIslandHistoryPort) {
        current = port
    }
}
