package com.xzyht.notifyrelay

import github.xzynine.superislandui.common.TextSplitter
import org.junit.Test

class TextSplitterUnitTest {
    @Test
    fun testTextSplitter() {
        val testCases =
            listOf(
                // 等价短于胶囊的英语歌词
                "Hello World",
                "Love you",
                "Yes",
                "No",
                // 等价短于胶囊的汉语歌词
                "你好",
                "再见你好",
                "谢谢再见你好",
                "加谢谢再见你好",
                // 等价短于胶囊的日语歌词
                "こんにちはら",
                "さようなら",
                "ありがとう",
                "がんばれ",
                "はい",
                // 等价短于胶囊的英汉混合歌词
                "Hi 你好",
                "Bye 再见",
                "OK 好的",
                "Yes 是的",
                "No 不是",
                // 短于总限制但是长于胶囊的英语歌词
                "I love you more than words can say",
                "She sells seashells by the seashore",
                "How much wood would a woodchuck chuck",
                "Mary had a little lamb little lamb little lamb",
                "Twinkle twinkle little star how I wonder what you are",
                // 短于总限制但是长于胶囊的汉语歌词
                "我爱你胜过千言万语",
                "她在海边卖贝壳海边卖贝壳",
                "土拨鼠会扔多少木头土拨鼠会扔多少木头",
                "玛丽有只小羊羔小羊羔小羊羔",
                "一闪一闪小星星我想知道你是什么",
                // 短于总限制但是长于胶囊的日语歌词
                "愛してるよ言葉では言い表せないほど",
                "彼女は海辺で貝殻を売っています海辺で貝殻を売っています",
                "モルモットはどれだけの木を投げることができますか",
                "メアリーは小さな子羊を飼っていました小さな子羊を飼っていました",
                "きらきら星どうしてあなたはそうなのかな",
                // 短于总限制但是长于胶囊的英汉混合歌词
                "I love you 我爱你 more than words",
                "She sells 贝壳 by the seashore",
                "How much 木头 would a woodchuck chuck",
                "Mary had a 小羊羔 little lamb",
                "Twinkle twinkle 小星星 how I wonder",
                // 原始测试用例
                "【察话会Au】260310 甲乙新护卫舰：\"不正常\"国家的正常军舰",
                "真假战果？伊朗宣称击中美国林肯号。打航母究竟有多难？",
                "Minecraft: Lava Chicken (Original Game Soundtrack)",
                "It's just another rainy Sunday afternoon 又是一个周日的午后又是阴雨连绵",
                "過去形フィルムに縋った僕らは舵取り粘土に飲まれていつしか固まっていくようで",
            )

        var testFailed = false

        testCases.forEachIndexed { index, text ->
            // 使用修复后的TextSplitter逻辑
            val (iconText, capsuleText) = TextSplitter.splitLyric(text, 6)

            try {
                // 添加断言验证
                validateSplitResult(text, iconText, capsuleText)

                // 测试通过，只打印基本信息
                val result = formatTestResult(index, text, iconText, capsuleText)
                print(result)
            } catch (e: AssertionError) {
                // 测试失败，打印详细数据值
                val result = formatFailedTestResult(index, text, iconText, capsuleText)
                print(result)
                // 记录测试失败，但继续执行后续测试
                testFailed = true
            }
        }

        // 如果有测试失败，最后抛出异常，确保测试整体失败
        if (testFailed) {
            throw AssertionError("部分测试用例失败，请查看详细输出")
        }
    }

    /**
     * 验证拆分结果是否符合预期
     */
    private fun validateSplitResult(
        text: String,
        iconText: String,
        capsuleText: String,
    ) {
        val textLength = TextSplitter.calculateTextLength(text)
        val iconLength = TextSplitter.calculateTextLength(iconText)
        val capsuleLength = TextSplitter.calculateTextLength(capsuleText)
        val totalSplitLength = iconLength + capsuleLength

        // 验证短文本不会被拆分（iconText 为空）
        if (textLength <= 12) {
            assert(iconText.isEmpty()) { "短文本应该不会被拆分，iconText 应该为空" }
            assert(capsuleText == text) { "短文本应该完全显示在胶囊中" }
        }

        // 验证拆分后的权重在预期范围内
        if (iconText.isNotEmpty()) {
            // 图标长度不超过14权重
            assert(iconLength <= 14) { "图标文本长度不应超过14权重" }
            // 胶囊长度至少为2权重
            assert(capsuleLength >= 2) { "胶囊文本长度不应小于2权重" }
        }

        // 在原文本长于26权重后，图标加胶囊至少20权重
        if (textLength > 26) {
            assert(totalSplitLength >= 20) { "原文本长于26权重时，图标加胶囊至少20权重" }
        }

        // 在原始文本很短的时候胶囊加图标等于原始
        if (textLength <= 26) {
            assert(totalSplitLength == textLength) { "原始文本很短时，胶囊加图标长度应等于原始长度" }
        }

        // 验证胶囊文本长度不超过18个字符
        assert(capsuleText.length <= 18) { "胶囊文本长度不应超过18个字符" }

        // 验证 iconText + capsuleText 等于原始文本的截断形式
        val combinedText = iconText + capsuleText
        val truncatedText = TextSplitter.truncateText(text)
        assert(combinedText == truncatedText) { "拆分后的文本组合应等于截断后的原始文本" }
    }

    /**
     * 格式化测试结果
     */
    private fun formatTestResult(
        index: Int,
        text: String,
        iconText: String,
        capsuleText: String,
    ): String {
        val builder = StringBuilder()

        builder.appendLine("=== 测试用例 ${index + 1} ===")
        builder.appendLine("原始文本: $text")

        // 只有当图标文本不为空时才显示图标部分信息
        if (iconText.isNotEmpty()) {
            builder.appendLine("图标文本: $iconText")
        }

        builder.appendLine("胶囊文本: $capsuleText")
        builder.appendLine()

        // 仅在追加到resultBuilder时添加分隔线
        builder.appendLine("-".repeat(50))
        builder.appendLine()

        return builder.toString()
    }

    /**
     * 格式化故障测试结果，打印详细数据值
     */
    private fun formatFailedTestResult(
        index: Int,
        text: String,
        iconText: String,
        capsuleText: String,
    ): String {
        val builder = StringBuilder()
        val textLength = TextSplitter.calculateTextLength(text)
        val iconLength = TextSplitter.calculateTextLength(iconText)
        val capsuleLength = TextSplitter.calculateTextLength(capsuleText)

        builder.appendLine("=== 测试用例 ${index + 1} (故障) ===")
        builder.appendLine("原始文本: $text")
        builder.appendLine("总长度: ${text.length}")
        builder.appendLine("等价长度: $textLength")
        builder.appendLine()
        builder.appendLine("分割结果:")

        // 只有当图标文本不为空时才显示图标部分信息
        if (iconText.isNotEmpty()) {
            builder.appendLine("图标文本: $iconText")
            builder.appendLine("图标文本长度: ${iconText.length}")
            builder.appendLine("图标文本等价长度: $iconLength")
            builder.appendLine()
        }

        builder.appendLine("胶囊文本: $capsuleText")
        builder.appendLine("胶囊文本长度: ${capsuleText.length}")
        builder.appendLine("胶囊文本等价长度: $capsuleLength")
        builder.appendLine()

        // 仅在追加到resultBuilder时添加分隔线
        builder.appendLine("-".repeat(50))
        builder.appendLine()

        return builder.toString()
    }
}
