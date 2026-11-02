package notifyrelay.domain.feature.appslist.model

/**
 * 本机已安装应用条目（平台无关视图）。
 *
 * @param packageName 包名
 * @param appName 显示名
 * @param isSystemApp 是否为系统应用
 * @param isUpdatedSystemApp 是否为已更新的系统应用
 * @param isPinned 是否已置顶
 */
data class LocalAppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false,
    val isUpdatedSystemApp: Boolean = false,
    val isPinned: Boolean = false,
)
