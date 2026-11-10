package notifyrelay.desktop.overlay.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 完整会话验证：握手 → 订阅 → 收场景 → 心跳 → 点击回传。
 *
 * 这里刻意覆盖 Gamebar 客户端会走的每一步，若任一步不通，
 * 真实链路上必然表现为「小组件一直显示离线」或「点击无反应」。
 */
class FullSessionTest {
    @Test
    fun fullSessionFlow() {
        val clicks = mutableListOf<Pair<String, String>>()
        val pipeName = "NrFullSession-" + System.nanoTime()
        val server = NamedPipeSceneServer(pipeName = pipeName)
        server.clickListener = { key, phase, _, _ -> clicks.add(key to phase) }
        server.start()

        val handle = connect(pipeName)
        assertTrue(handle != 0L, "应能连接管道")

        try {
            // 1) HELLO/WELCOME
            write(handle, SceneMessage(type = SceneMessage.TYPE_HELLO, protocolVersion = SCENE_PROTOCOL_VERSION))
            val welcome = read(handle)
            assertNotNull(welcome, "应收到 WELCOME")
            assertEquals(SceneMessage.TYPE_WELCOME, welcome.type)
            assertEquals(SCENE_PROTOCOL_VERSION, welcome.protocolVersion)

            // 2) SUBSCRIBE
            write(handle, SceneMessage(type = SceneMessage.TYPE_SUBSCRIBE, width = 320f, height = 625f, dpi = 1f))
            Thread.sleep(300)
            assertTrue(server.isSubscribed(), "订阅后应进入可推送状态")
            assertEquals(320f, server.viewport()?.widthDp)

            // 3) SCENE
            server.publish(
                SceneSnapshot(
                    revision = 42,
                    widthDp = 320f,
                    heightDp = 625f,
                    document = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"320\" height=\"625\"><rect width=\"320\" height=\"625\" fill=\"#1C1C1E\"/></svg>",
                    regions = listOf(
                        SceneRegion("notification:7", 10f, 20f, 24f, 24f),
                        SceneRegion("media:local", 0f, 100f, 320f, 46f),
                    ),
                ),
            )
            val scene = read(handle, timeoutMs = 5000)
            assertNotNull(scene, "应收到 SCENE")
            assertEquals(SceneMessage.TYPE_SCENE, scene.type)
            assertEquals(42L, scene.revision)
            assertTrue(scene.document!!.contains("<svg"))
            assertEquals(2, scene.regions?.size)
            // 区域坐标必须是 dp 原值，不能被缩放或取整
            val mediaRegion = scene.regions!!.first { it.key == "media:local" }
            assertEquals(100f, mediaRegion.y)
            assertEquals(320f, mediaRegion.width)
            assertEquals(46f, mediaRegion.height)

            // 4) PING/PONG
            write(handle, SceneMessage(type = SceneMessage.TYPE_PING))
            val pong = read(handle, timeoutMs = 3000)
            assertNotNull(pong, "应收到 PONG")
            assertEquals(SceneMessage.TYPE_PONG, pong.type)

            // 5) CLICK
            write(handle, SceneMessage(type = SceneMessage.TYPE_CLICK, key = "media:local", phase = "up"))
            Thread.sleep(400)
            assertEquals(1, clicks.size, "点击应被派发")
            assertEquals("media:local" to "up", clicks.first())
        } finally {
            com.sun.jna.platform.win32.Kernel32.INSTANCE
                .CloseHandle(com.sun.jna.platform.win32.WinNT.HANDLE(com.sun.jna.Pointer(handle)))
            server.close()
        }
    }

    // ======== 最小客户端 ========

    private fun connect(name: String): Long {
        val k32 = com.sun.jna.platform.win32.Kernel32.INSTANCE
        repeat(40) {
            val h = k32.CreateFile(PREFIX + name, GENERIC_RW, 0, null, OPEN_EXISTING, 0, null)
            if (h != null && h.pointer != null) {
                val raw = com.sun.jna.Pointer.nativeValue(h.pointer)
                if (raw != -1L) return raw
            }
            Thread.sleep(100)
        }
        return 0L
    }

    private fun write(value: Long, message: SceneMessage) {
        val k32 = com.sun.jna.platform.win32.Kernel32.INSTANCE
        val h = com.sun.jna.platform.win32.WinNT.HANDLE(com.sun.jna.Pointer(value))
        val bytes = message.encodeLine().toByteArray(Charsets.UTF_8)
        k32.WriteFile(h, bytes, bytes.size, com.sun.jna.ptr.IntByReference(), null)
        k32.FlushFileBuffers(h)
    }

    private fun read(value: Long, timeoutMs: Long = 3000): SceneMessage? {
        val k32 = com.sun.jna.platform.win32.Kernel32.INSTANCE
        val h = com.sun.jna.platform.win32.WinNT.HANDLE(com.sun.jna.Pointer(value))
        val buffer = java.io.ByteArrayOutputStream()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val avail = com.sun.jna.ptr.IntByReference()
            if (!k32.PeekNamedPipe(h, null, 0, null, avail, null)) return null
            if (avail.value <= 0) { Thread.sleep(10); continue }
            val chunk = ByteArray(avail.value)
            val read = com.sun.jna.ptr.IntByReference()
            if (!k32.ReadFile(h, chunk, chunk.size, read, null) || read.value <= 0) return null
            buffer.write(chunk, 0, read.value)
            val text = buffer.toString("UTF-8")
            val nl = text.indexOf('\n')
            if (nl >= 0) return runCatching { SceneMessage.parse(text.substring(0, nl).trim()) }.getOrNull()
        }
        return null
    }

    private companion object {
        private const val PREFIX = "\u005C\u005C.\u005Cpipe\u005C"
        private const val GENERIC_RW = -1073741824 or 0x40000000
        private const val OPEN_EXISTING = 3
    }
}