package notifyrelay.domain.feature.notification.filter

/**
 * 通知过滤条目：关键词 + 应用包名，二者可单独或组合使用。
 *
 * @param keyword 关键词（可为空）
 * @param packageName 应用包名（可为空）
 */
data class FilterEntry(
    val keyword: String,
    val packageName: String,
)
