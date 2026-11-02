package notifyrelay.desktop.platform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Rust 回包解码。
 *
 * 覆盖 `serde_json` 的实际输出形态：内容按 JSON 规则转义，路径中的反斜杠与文本中的换行
 * 必须以原样还原。解码结果同时是 Rust 防循环的内容比较输入，故必须幂等：反复回灌不叠加转义。
 */
class ClipboardResultDecodeTest {
    /** 覆盖回灌敏感与转义敏感的输入。 */
    private val samples =
        listOf(
            "C:\\Users\\name\\file.txt",
            "a\nb\nc",
            "a\r\nb",
            "he said \"hi\"",
            "C:\\tmp\\\"quoted\"",
            "tab\there",
            "多行\n路径 C:\\a\\b\n结束",
            "emoji 😀 代理对 𝄞",
            "",
        )

    /** 与 Rust `serde_json` 同形的回包：`{"type":"text","content":<转义后的内容>}`。 */
    private fun wire(content: String): String =
        buildJsonObject {
            put("type", "text")
            put("content", content)
        }.toString()

    @Test
    fun decodesLiteralPathAndNewlineWireForm() {
        assertEquals("C:\\Users\\a", decodeResultField("""{"type":"text","content":"C:\\Users\\a"}""", "content"))
        assertEquals("a\nb", decodeResultField("""{"type":"text","content":"a\nb"}""", "content"))
        assertEquals("he said \"hi\"", decodeResultField("""{"type":"text","content":"he said \"hi\""}""", "content"))
    }

    @Test
    fun decodesTypeAndActionFields() {
        assertEquals("text", decodeResultField("""{"type":"text","content":""}""", "type"))
        assertEquals("skipped", decodeResultField("""{"action":"skipped","reason":"内容来自远程，跳过发送"}""", "action"))
        assertEquals("内容来自远程，跳过发送", decodeResultField("""{"action":"skipped","reason":"内容来自远程，跳过发送"}""", "reason"))
    }

    @Test
    fun rejectsMissingInvalidAndNonStringFields() {
        assertNull(decodeResultField("""{"action":"sent"}""", "content"))
        assertNull(decodeResultField("not json", "content"))
        assertNull(decodeResultField("""{"content":1}""", "content"))
        assertNull(decodeResultField("""{"content":null}""", "content"))
        assertNull(decodeResultField("""{"content":{"a":1}}""", "content"))
    }

    @Test
    fun decodeIsStableAcrossRepeatedEchoes() {
        for (sample in samples) {
            var current = sample
            repeat(20) {
                val decoded = decodeResultField(wire(current), "content")
                assertEquals(current, decoded, "第 " + it + " 轮解码改变了内容：" + current)
                current = decoded!!
            }
            assertEquals(sample, current)
        }
    }
}

/**
 * 发送目标列表的 JSON。
 *
 * 平台把它交给 `clipboardOnChanged`；非法 JSON 会被 Rust 当成"没有目标"而静默不发送。
 */
class ClipboardTargetsJsonTest {
    @Test
    fun buildsJsonStringArrayOfUuids() {
        assertEquals("""["a","b"]""", buildTargetsJson(listOf("a", "b")))
    }

    @Test
    fun roundTripsThroughJsonParser() {
        val uuids = listOf("3d97367f-5d81-47c2-837d-10ec68e4bc4a", "0f0f", "a-\"b")
        val parsed =
            Json
                .parseToJsonElement(buildTargetsJson(uuids))
                .jsonArray
                .map { it.jsonPrimitive.content }
        assertEquals(uuids, parsed)
    }

    @Test
    fun emptyListIsStillValidJson() {
        assertEquals("[]", buildTargetsJson(emptyList()))
    }
}

/**
 * 剪贴板换行归一。
 *
 * 平台把内容交给 Rust 前、以及写回系统剪贴板前统一为 `LF`；Rust 的防循环以内容相等为前提，
 * 该归一必须幂等，才能让回读值与接收登记值逐字相等。
 */
class ClipboardNewlineNormalizationTest {
    @Test
    fun crlfCollapsesToLf() {
        assertEquals("a\nb", Win32Clipboard.normalizeNewlines("a\r\nb"))
    }

    @Test
    fun loneCrTabAndAstralCharactersSurvive() {
        assertEquals("a\rb\tc 𝄞", Win32Clipboard.normalizeNewlines("a\rb\tc 𝄞"))
    }

    @Test
    fun normalizationIsIdempotent() {
        val samples = listOf("a\r\nb", "a\nb", "a\rb", "C:\\Users\\a\r\nb\r\n")
        for (sample in samples) {
            val once = Win32Clipboard.normalizeNewlines(sample)
            assertEquals(once, Win32Clipboard.normalizeNewlines(once))
        }
    }
}
