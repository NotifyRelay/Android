package github.xzynine.superislandui.builder

/**
 * 超级岛图片资源约束（依据《小米澎湃OS 超级岛通知 API》）。
 *
 * - 单张图片大小 ≤ 100KB，超过则不下载
 * - 图片链接必须为 https，否则按下载失败处理
 * - 图片宽高比在 1:1 ~ 16:9 之间
 * - 单个通知图片数量 ≤ 10 张
 *
 * 说明：[validatePicMap] 仅做**无需下载**即可判断的校验（数量、链接 scheme）；
 * [validateImage] 用于已拿到位图/字节后的校验（大小、宽高比）。
 */
object SuperIslandImageSpec {
    /** 单张图片大小上限（字节，100KB） */
    const val MAX_IMAGE_BYTES = 100 * 1024

    /** 单个通知图片数量上限 */
    const val MAX_IMAGE_COUNT = 10

    /** 最小宽高比（1:1） */
    const val MIN_ASPECT_RATIO = 1.0f

    /** 最大宽高比（16:9） */
    const val MAX_ASPECT_RATIO = 16.0f / 9.0f

    /** 图片资源 key 前缀（规范要求） */
    const val PIC_KEY_PREFIX = "miui.focus.pic_"

    /** 允许出现在图片链接中的 scheme 白名单（含结尾冒号） */
    private val ALLOWED_PIC_SCHEMES = setOf("https:", "data:", "content:", "file:")

    /** scheme 正则：ALPHA *( ALPHA / DIGIT / "+" / "-" / "." ) ":" */
    private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    /**
     * 提取 URL 的 scheme（含结尾冒号），无 scheme 返回 null。
     * 例："https://x" -> "https:"，"content://x" -> "content:"，"data:base64" -> "data:"，本地路径 -> null。
     */
    private fun extractScheme(url: String): String? = SCHEME_REGEX.find(url)?.value

    /**
     * 图片条目是否合法（供 [SuperIslandExtras.writePicMap] 集中过滤使用）：
     * - 非空；
     * - 无 scheme 的本地路径视为合法；
     * - 有 scheme 但不在白名单（如 `ftp:`、`http:`）视为非法。
     */
    fun isPicEntryValid(url: String): Boolean {
        if (url.isBlank()) return false
        val scheme = extractScheme(url) ?: return true
        return scheme in ALLOWED_PIC_SCHEMES
    }

    /**
     * 校验 picMap 的数量与链接约束（无需下载图片）。
     * data URL / 本地资源不受 https 约束。
     * @return 问题列表（空表示通过）
     */
    fun validatePicMap(picMap: Map<String, String>?): List<String> {
        if (picMap.isNullOrEmpty()) return emptyList()
        val issues = mutableListOf<String>()
        val picEntries = picMap.filterKeys { it.startsWith(PIC_KEY_PREFIX) }
        if (picEntries.size > MAX_IMAGE_COUNT) {
            issues += "图片数量 ${picEntries.size} 超过上限 $MAX_IMAGE_COUNT 张"
        }
        picEntries.forEach { (key, url) ->
            if (!isPicEntryValid(url)) {
                issues += "图片 $key 的链接 scheme 不在允许范围（仅支持 https/data/content/file 或本地路径），将被忽略"
            }
        }
        return issues
    }

    /**
     * 校验单张图片的字节数与宽高比。
     * @return 问题列表（空表示通过）
     */
    fun validateImage(
        bytes: Int,
        width: Int,
        height: Int,
    ): List<String> {
        val issues = mutableListOf<String>()
        if (bytes > MAX_IMAGE_BYTES) {
            issues += "图片大小 ${bytes}B 超过上限 ${MAX_IMAGE_BYTES}B"
        }
        if (width > 0 && height > 0) {
            val ratio = aspectRatio(width, height)
            if (ratio > MAX_ASPECT_RATIO + 0.01f) {
                issues += "图片宽高比 ${"%.2f".format(ratio)} 超出 1:1 ~ 16:9"
            }
        }
        return issues
    }

    /** 宽高比（长边 / 短边） */
    fun aspectRatio(
        width: Int,
        height: Int,
    ): Float = if (width <= 0 || height <= 0) 0f else maxOf(width, height).toFloat() / minOf(width, height)

    /** 是否满足宽高比（1:1 ~ 16:9） */
    fun isAspectRatioValid(
        width: Int,
        height: Int,
    ): Boolean {
        val ratio = aspectRatio(width, height)
        return ratio >= MIN_ASPECT_RATIO - 0.01f && ratio <= MAX_ASPECT_RATIO + 0.01f
    }
}
