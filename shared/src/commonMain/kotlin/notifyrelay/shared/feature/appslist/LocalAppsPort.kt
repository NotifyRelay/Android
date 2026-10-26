package notifyrelay.shared.feature.appslist

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.StateFlow
import notifyrelay.domain.feature.appslist.model.LocalAppInfo

/**
 * 本机已安装应用端口。
 *
 * 公共端 UI 依赖本接口；平台侧以 PackageManager 实现并注入。
 */
interface LocalAppsPort {
    /** 是否正在加载。 */
    val isLoading: StateFlow<Boolean>

    /** 全部已安装应用。 */
    val apps: StateFlow<List<LocalAppInfo>>

    /** 加载应用列表。 */
    suspend fun load()

    /** 按条件过滤应用；[showSystemApps] 为 false 时仅返回用户应用。 */
    fun filtered(
        query: String,
        showSystemApps: Boolean,
    ): List<LocalAppInfo>

    /** 取指定包名的应用图标；失败返回 null。 */
    suspend fun iconFor(packageName: String): ImageBitmap?

    /** 数据是否已加载。 */
    fun isDataLoaded(): Boolean

    /** 已安装包名集合。 */
    fun installedPackageNames(): Set<String>

    /** 同步获取已安装包名集合（必要时触发刷新）。 */
    fun installedPackageNamesSync(): Set<String>
}
