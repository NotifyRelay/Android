package notifyrelay.desktop.overlay.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 协议报文编解码：保证两端理解同一份约定。 */
class SceneProtocolTest {
    @Test
    fun roundTripSceneMessage() {
        val message =
            SceneMessage(
                type = SceneMessage.TYPE_FRAME,
                revision = 7,
                width = 320f,
                height = 625f,
                length = 800000,
                regions = listOf(SceneRegion("notification:1", 0f, 0f, 24f, 24f)),
            )
        val line = message.encodeLine()
        assertTrue(line.endsWith("\n"), "每行报文必须以换行结尾")
        assertTrue(!line.dropLast(1).contains("\n"), "报文体内部不得含换行")

        val decoded = SceneMessage.parse(line.trim())
        assertEquals(SceneMessage.TYPE_FRAME, decoded.type)
        assertEquals(7L, decoded.revision)
        assertEquals(320f, decoded.width)
        assertEquals(800000, decoded.length)
        assertEquals(1, decoded.regions?.size)
        assertEquals("notification:1", decoded.regions?.first()?.key)
        assertEquals(24f, decoded.regions?.first()?.width)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        // 单侧新增可选字段不应让旧对端解析失败
        val decoded = SceneMessage.parse("""{"type":"PING","futureField":"x"}""")
        assertEquals(SceneMessage.TYPE_PING, decoded.type)
    }

    @Test
    fun regionKeepsDpUnits() {
        val region = SceneRegion("media:local", 10.5f, 20.25f, 260f, 46f)
        val decoded =
            SceneMessage.parse(
                SceneMessage(
                    type = SceneMessage.TYPE_FRAME,
                    regions = listOf(region),
                ).encodeLine().trim(),
            )
        val back = decoded.regions!!.first()
        assertEquals(10.5f, back.x)
        assertEquals(20.25f, back.y)
        assertEquals(260f, back.width)
        assertEquals(46f, back.height)
    }

    @Test
    fun omitsAbsentFields() {
        val line = SceneMessage(type = SceneMessage.TYPE_PING).toJson()
        assertEquals("""{"type":"PING"}""", line)
    }
}
