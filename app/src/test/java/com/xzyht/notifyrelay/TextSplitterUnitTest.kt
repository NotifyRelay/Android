package com.xzyht.notifyrelay

import com.xzyht.notifyrelay.feature.notification.superisland.common.TextSplitter
import org.junit.Test
import java.io.File

class TextSplitterUnitTest {
    
    @Test
    fun testTextSplitter() {
        val testCases = listOf(
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
            "過去形フィルムに縋った僕らは舵取り粘土に飲まれていつしか固まっていくようで"
        )
        
        val outputDir = File("build/test-output/textsplitter")
        outputDir.mkdirs()
        
        val resultFile = File(outputDir, "test_results.txt")
        val resultBuilder = StringBuilder()
        
        testCases.forEachIndexed { index, text ->
            // 使用修复后的TextSplitter逻辑
            val (iconText, capsuleText) = TextSplitter.splitLyricWithCharacterType(text, 6)
            
            val result = formatTestResult(index, text, iconText, capsuleText)
            resultBuilder.append(result)
            print(result)
        }
        
        resultFile.writeText(resultBuilder.toString(), Charsets.UTF_8)
        println("测试结果已保存到: ${resultFile.absolutePath}")
    }
    
    /**
     * 格式化测试结果
     */
    private fun formatTestResult(index: Int, text: String, iconText: String, capsuleText: String): String {
        val builder = StringBuilder()
        
        builder.appendLine("=== 测试用例 ${index + 1} ===")
        builder.appendLine("原始文本: $text")
        builder.appendLine("总长度: ${text.length}")
        builder.appendLine("等价长度: ${TextSplitter.calculateTextLength(text)}")
        builder.appendLine()
        builder.appendLine("分割结果:")
        
        // 只有当图标文本不为空时才显示图标部分信息
        if (iconText.isNotEmpty()) {
            builder.appendLine("图标文本: $iconText")
            builder.appendLine("图标文本长度: ${iconText.length}")
            builder.appendLine("图标文本等价长度: ${TextSplitter.calculateTextLength(iconText)}")
            builder.appendLine()
        }
        
        builder.appendLine("胶囊文本: $capsuleText")
        builder.appendLine("胶囊文本长度: ${capsuleText.length}")
        builder.appendLine("胶囊文本等价长度: ${TextSplitter.calculateTextLength(capsuleText)}")
        builder.appendLine()
        
        // 仅在追加到resultBuilder时添加分隔线
        builder.appendLine("-".repeat(50))
        builder.appendLine()
        
        return builder.toString()
    }
}
