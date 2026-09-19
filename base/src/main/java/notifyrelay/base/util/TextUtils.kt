package notifyrelay.base.util

/**
 * 文本处理工具类
 */
object TextUtils {
    /**
     * 标题前缀正则：形如 `(微博)` 的括注前缀。
     *
     * 提为 `private val` 避免每次调用重复构造 [Regex]。
     */
    private val TITLE_PREFIX_PATTERN = Regex("^\\([^)]+\\)")

    /**
     * 去掉形如 `(xxx)` 的前缀并 trim。
     *
     * @param title 原始标题，可为 null。
     * @return 去除前缀并去除首尾空白后的标题；[title] 为 null 时返回 `""`。
     */
    fun normalizeTitle(title: String?): String {
        if (title == null) return ""
        return title.replace(TITLE_PREFIX_PATTERN, "").trim()
    }
}
