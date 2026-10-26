package github.xzynine.superislandui.common

import notifyrelay.base.util.measureTime
import kotlin.math.min

/**
 * 文本拆分工具类，用于处理歌词等文本的拆分
 * 权重体系复用 CapsuleScrollManager（CJK=2，西文=1，空白=0）
 */
object TextSplitter {
    private const val TAG = "TextSplitter"

    private val HALF_WIDTH_PUNCTUATION =
        setOf(
            ',',
            '.',
            '!',
            '?',
            ';',
            ':',
            '-',
            '_',
            '(',
            ')',
            '[',
            ']',
            '{',
            '}',
            '<',
            '>',
            '"',
            '\'',
            '`',
            '~',
            '|',
            '\\',
            '/',
            '%',
            '^',
            '&',
            '*',
            '+',
            '=',
        )

    private val PUNCTUATION =
        setOf(
            '，',
            '。',
            '！',
            '？',
            '；',
            '：',
            ',',
            '.',
            '!',
            '?',
            ';',
            ':',
            '、',
        )

    /**
     * 判断字符是否为标点符号
     */
    private fun isPunctuation(char: Char): Boolean = char in PUNCTUATION || char in HALF_WIDTH_PUNCTUATION

    /**
     * 计算文本的视觉权重，复用 CapsuleScrollManager 的权重系统
     * CJK=2，西文=1，空白=0
     */
    fun calculateTextLength(text: String): Int = CapsuleScrollManager.calculateWeight(text)

    /**
     * 截断文本，最长26权重，允许最多超出5个字符
     * @param text 原始文本
     * @return 截断后的文本
     */
    fun truncateText(text: String): String = truncateTextInternal(text, 26, 18)

    /**
     * 截断文本（放宽限制），最长52权重，允许最多超出5个字符
     * @param text 原始文本
     * @return 截断后的文本
     */
    fun truncateTextExtended(text: String): String = truncateTextInternal(text, 52, 36)

    /**
     * 截断文本内部实现
     * @param text 原始文本
     * @param maxWeight 最大视觉权重
     * @param maxAllowedLength 最大字符数
     * @return 截断后的文本
     */
    private fun truncateTextInternal(
        text: String,
        maxWeight: Int,
        maxAllowedLength: Int,
    ): String {
        return measureTime(TAG, "truncateText") {
            if (text.isEmpty()) {
                return@measureTime text
            }

            if (text.length <= maxAllowedLength) {
                return@measureTime text
            }

            var currentWeight = 0
            var truncatePoint = 0

            for (i in 0 until text.length) {
                currentWeight += CapsuleScrollManager.charWeight(text[i])

                if (currentWeight >= maxWeight) {
                    truncatePoint = i + 1
                    break
                }
            }

            if (truncatePoint == 0) {
                truncatePoint = maxAllowedLength
            }

            if (truncatePoint > maxAllowedLength) truncatePoint = maxAllowedLength
            if (truncatePoint < 1) truncatePoint = 1

            var finalTruncatePoint = truncatePoint
            val end = minOf(text.length, truncatePoint + 5)
            for (i in truncatePoint until end) {
                if (text[i] == ' ' || isPunctuation(text[i])) {
                    finalTruncatePoint = i
                    break
                }
            }

            if (finalTruncatePoint < 1) finalTruncatePoint = 1

            return@measureTime text.substring(0, finalTruncatePoint)
        }
    }

    /**
     * 拆分歌词文本，使用 CapsuleScrollManager 的视觉权重系统
     * @param lyricText 歌词文本
     * @param threshold 拆分阈值（视觉权重）
     * @return Pair(图标文本, 胶囊文本)
     */
    fun splitLyric(
        lyricText: String,
        threshold: Int,
    ): Pair<String, String> {
        return measureTime(TAG, "splitLyric") {
            val truncatedText = truncateText(lyricText)

            if (truncatedText.isEmpty()) {
                return@measureTime Pair("", "")
            }

            // 预计算字符权重数组，避免多次遍历
            val charWeights = truncatedText.map { CapsuleScrollManager.charWeight(it) }
            val textWeight = charWeights.sum()

            if (textWeight <= threshold) {
                return@measureTime Pair("", truncatedText)
            }

            val capsuleEquivalentWeight = 14
            var capsuleSplitPoint = truncatedText.length

            var currentWeight = 0
            for (i in truncatedText.length - 1 downTo 0) {
                currentWeight += charWeights[i]

                if (currentWeight >= capsuleEquivalentWeight) {
                    capsuleSplitPoint = i
                    break
                }
            }

            val maxIconWeight = 14
            var iconSplitPoint = min(capsuleSplitPoint, truncatedText.length)

            currentWeight = 0
            for (i in 0 until capsuleSplitPoint) {
                currentWeight += charWeights[i]

                if (currentWeight >= maxIconWeight) {
                    iconSplitPoint = i + 1
                    break
                }
            }

            val minSplitPoint = 2
            iconSplitPoint = maxOf(minSplitPoint, iconSplitPoint)
            iconSplitPoint = min(iconSplitPoint, truncatedText.length)

            val safeIconSplitPoint = min(iconSplitPoint, truncatedText.lastIndex.coerceAtLeast(0))

            val searchStart = minSplitPoint
            val searchEnd = minOf(capsuleSplitPoint, iconSplitPoint + 3, truncatedText.length)

            var splitPoint = safeIconSplitPoint
            var foundSplitPoint = false
            if (truncatedText.isNotEmpty() && safeIconSplitPoint >= searchStart) {
                for (i in safeIconSplitPoint downTo searchStart) {
                    if (truncatedText[i] == ' ' || isPunctuation(truncatedText[i])) {
                        splitPoint = i
                        foundSplitPoint = true
                        break
                    }
                }
            }

            if (!foundSplitPoint && truncatedText.isNotEmpty() && safeIconSplitPoint < searchEnd) {
                for (i in safeIconSplitPoint until searchEnd) {
                    if (truncatedText[i] == ' ' || isPunctuation(truncatedText[i])) {
                        splitPoint = i
                        break
                    }
                }
            }

            var finalSplitPoint = maxOf(minSplitPoint, min(splitPoint, capsuleSplitPoint))

            if (finalSplitPoint < truncatedText.length && isPunctuation(truncatedText[finalSplitPoint])) {
                val maxIconWeight = 14
                val currentIconWeight = truncatedText.take(finalSplitPoint).sumOf { CapsuleScrollManager.charWeight(it) }
                val punctuationWeight = CapsuleScrollManager.charWeight(truncatedText[finalSplitPoint])

                if (finalSplitPoint <= 1 || currentIconWeight + punctuationWeight <= maxIconWeight) {
                    finalSplitPoint = min(finalSplitPoint + 1, truncatedText.length)
                }
            }

            val iconText = truncatedText.take(finalSplitPoint)
            val capsuleText = truncatedText.substring(finalSplitPoint)

            return@measureTime Pair(iconText, capsuleText)
        }
    }
}
