package notifyrelay.data.database.entity

/**
 * 超级岛历史记录的摘要投影（不含 rawPayload 大字段）。
 *
 * 只是查询列的子集，不是表：字段取自 [SuperIslandHistoryEntity] 的同名列，
 * 图片引用从关联表单独装配。
 */
data class SuperIslandHistorySummary(
    val id: Long,
    val sourceDeviceUuid: String? = null,
    val originalPackage: String? = null,
    val mappedPackage: String? = null,
    val appName: String? = null,
    val title: String? = null,
    val text: String? = null,
    val paramV2Raw: String? = null,
    val featureId: String? = null,
)
