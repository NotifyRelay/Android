package notifyrelay.base.util

/**
 * 版本号比较工具（平台无关）。
 *
 * 系统版本号取自厂商指纹（Android），但比较逻辑本身是纯字符串运算，故置于 commonMain。
 */
object VersionUtils {
    /**
     * 比较两个版本号，判断当前版本是否大于目标版本。
     *
     * @param version 当前版本号，例如 "OS3.0.300"
     * @param target 目标版本号，例如 "OS3.0.200"
     * @return 如果当前版本大于目标版本返回 true，否则返回 false
     */
    fun isVersionGreaterThan(
        version: String?,
        target: String?,
    ): Boolean {
        if (version == null || target == null) return false

        try {
            // 去除OS前缀
            val versionNum = version.replace("OS", "")
            val targetNum = target.replace("OS", "")

            // 分割版本号
            val versionParts = versionNum.split(".").mapNotNull { it.toIntOrNull() }
            val targetParts = targetNum.split(".").mapNotNull { it.toIntOrNull() }

            // 比较版本号
            for (i in 0 until maxOf(versionParts.size, targetParts.size)) {
                val versionPart = versionParts.getOrElse(i) { 0 }
                val targetPart = targetParts.getOrElse(i) { 0 }

                if (versionPart > targetPart) return true
                if (versionPart < targetPart) return false
            }

            return false // 版本相同
        } catch (e: Exception) {
            return false
        }
    }
}
