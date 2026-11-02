package notifyrelay.base.util

import notifyrelay.base.log.Logger

/**
 * data URL 文本工具（平台无关）。
 *
 * 只负责前缀判断与文本规范化；位图解码依赖平台能力，由调用侧完成。
 */
object DataUrlUtils {
    private const val TAG = "DataUrlUtils"

    /** data URL 前缀。 */
    private const val DATA_PREFIX = "data:"

    /**
     * 判断文本是否为 data URL（忽略首尾空白与前缀大小写）。
     *
     * @param text 待判断文本。
     * @return 以 data: 开头时为 true。
     */
    fun isDataUrl(text: String): Boolean = text.trim().startsWith(DATA_PREFIX, ignoreCase = true)

    /**
     * 规范化 data URL 文本：去首尾空白、去包裹双引号、还原转义斜杠，并校验前缀与逗号分隔符。
     *
     * @param dataUrl 待规范化的文本。
     * @return 规范化后的 data URL；校验失败返回 null，并以 [Logger.w] 记录原因。
     */
    fun cleanDataUrl(dataUrl: String): String? {
        var candidate = dataUrl.trim()
        if (candidate.length >= 2 && candidate.first() == '"' && candidate.last() == '"') {
            candidate = candidate.substring(1, candidate.length - 1)
        }
        candidate = candidate.replace("\\/", "/")
        candidate = candidate.replace("\\\\", "")
        if (!candidate.startsWith(DATA_PREFIX, ignoreCase = true)) {
            Logger.w(TAG, "cleanDataUrl: 不以 data: 开头，原始前64字符: ${dataUrl.take(64)}")
            return null
        }
        val comma = candidate.indexOf(',')
        if (comma <= 0) {
            Logger.w(TAG, "cleanDataUrl: 未找到逗号分隔符，清理后前64字符: ${candidate.take(64)}")
            return null
        }
        return candidate
    }
}
