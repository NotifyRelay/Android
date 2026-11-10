package notifyrelay.desktop.overlay.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 命名管道服务端与客户端的会话级验证。
 *
 * 这里用同一套管道 API 扮演客户端，覆盖握手、订阅、推场景、点击与心跳：
 * 若这些环节在真实 Gamebar 上不通，问题必然出在 DACL 或跨容器边界，
 * 而不是协议本身。
 */
class NamedPipeSceneServerTest {
    @Test
    fun handshakeSubscribeSceneAndClick() {
        val receivedClicks = mutableListOf<Pair<String, String>>()
        val pipeName = "NotifyRelayTestScene-" + System.nanoTime()
        val server = NamedPipeSceneServer(pipeName = pipeName)
        server.clickListener = { key, phase, _, _ -> receivedClicks.add(key to phase) }
        server.start()

        val pipe = connectWithRetry(pipeName)
        assertTrue(pipe != 0L, "客户端应能连上管道，实际错误码见日志")

        try {
            // 握手
            writeLine(pipe, SceneMessage(type = SceneMessage.TYPE_HELLO, protocolVersion = SCENE_PROTOCOL_VERSION))
            val welcome = readLine(pipe)
            assertNotNull(welcome, "应收到 WELCOME")
            assertEquals(SceneMessage.TYPE_WELCOME, welcome.type)
            assertEquals(SCENE_PROTOCOL_VERSION, welcome.protocolVersion)

            // 订阅
            writeLine(pipe, SceneMessage(type = SceneMessage.TYPE_SUBSCRIBE, width = 320f, height = 625f, dpi = 1f))
            Thread.sleep(200)
            assertTrue(server.isSubscribed(), "订阅后服务端应转入 Live")
            val viewport = server.viewport()
            assertNotNull(viewport)
            assertEquals(320f, viewport.widthDp)

            // 推场景
            server.publish(
                SceneSnapshot(
                    revision = 1,
                    widthDp = 320f,
                    heightDp = 625f,
                    document = "<svg width=\"320\" height=\"625\"/>",
                    regions = listOf(SceneRegion("notification:1", 4f, 6f, 24f, 24f)),
                ),
            )
            val scene = readLine(pipe, timeoutMs = 5_000)
            assertNotNull(scene, "应收到 SCENE")
            assertEquals(SceneMessage.TYPE_SCENE, scene.type)
            assertEquals(1L, scene.revision)
            assertEquals(1, scene.regions?.size)
            assertEquals("notification:1", scene.regions?.first()?.key)
            assertTrue(scene.document!!.contains("<svg"))

            // 心跳
            writeLine(pipe, SceneMessage(type = SceneMessage.TYPE_PING))
            val pong = readLine(pipe, timeoutMs = 3_000)
            assertNotNull(pong, "应收到 PONG")
            assertEquals(SceneMessage.TYPE_PONG, pong.type)

            // 点击
            writeLine(pipe, SceneMessage(type = SceneMessage.TYPE_CLICK, key = "notification:1", phase = "up"))
            Thread.sleep(400)
            assertEquals(1, receivedClicks.size, "点击应派发到监听器")
            assertEquals("notification:1" to "up", receivedClicks.first())
        } finally {
            closeHandle(pipe)
            server.close()
        }
    }

    @Test
    fun rejectsVersionMismatch() {
        val pipeName = "NotifyRelayTestScene2-" + System.nanoTime()
        val server = NamedPipeSceneServer(pipeName = pipeName)
        server.start()
        val pipe = connectWithRetry(pipeName)
        assertTrue(pipe != 0L, "客户端应能连上管道")
        try {
            writeLine(pipe, SceneMessage(type = SceneMessage.TYPE_HELLO, protocolVersion = 999))
            val reply = readLine(pipe)
            assertNotNull(reply, "版本不匹配应收到回应")
            assertEquals(SceneMessage.TYPE_REJECT, reply.type)
        } finally {
            closeHandle(pipe)
            server.close()
        }
    }

    // ======== 最小客户端：直接复用同一套 Win32 管道 API ========

    /** 测试用客户端接口：必须显式用 W 版本，否则宽字符串会被当作 ANSI 解析。 */
    private interface ClientApi : com.sun.jna.win32.StdCallLibrary {
        fun CreateFileW(
            fileName: String,
            desiredAccess: Int,
            shareMode: Int,
            securityAttributes: com.sun.jna.Pointer?,
            creationDisposition: Int,
            flagsAndAttributes: Int,
            templateFile: com.sun.jna.Pointer?,
        ): com.sun.jna.platform.win32.WinNT.HANDLE

        fun ReadFile(
            hFile: com.sun.jna.platform.win32.WinNT.HANDLE,
            buffer: ByteArray,
            numberOfBytesToRead: Int,
            numberOfBytesRead: com.sun.jna.ptr.IntByReference,
            overlapped: com.sun.jna.Pointer?,
        ): Boolean

        fun WriteFile(
            hFile: com.sun.jna.platform.win32.WinNT.HANDLE,
            buffer: ByteArray,
            numberOfBytesToWrite: Int,
            numberOfBytesWritten: com.sun.jna.ptr.IntByReference,
            overlapped: com.sun.jna.Pointer?,
        ): Boolean

        fun FlushFileBuffers(hFile: com.sun.jna.platform.win32.WinNT.HANDLE): Boolean

        fun CloseHandle(hFile: com.sun.jna.platform.win32.WinNT.HANDLE): Boolean
    }

    /** 轮询连接管道：服务端创建实例需要时间，固定等待容易抢跑。 */
    private fun connectWithRetry(name: String): Long {
        repeat(40) {
            val handle = openClient(name)
            if (handle != 0L) return handle
            Thread.sleep(100)
        }
        return 0L
    }

    private fun openClient(name: String): Long {
        val handle =
            com.sun.jna.platform.win32.Kernel32.INSTANCE
                .CreateFile(
                    "\u005C\u005C.\u005Cpipe\u005C" + name,
                    GENERIC_READ or GENERIC_WRITE,
                    0,
                    null,
                    OPEN_EXISTING,
                    0,
                    null,
                )
        // CreateFile 失败返回 INVALID_HANDLE_VALUE（-1），调用方以 0 表示失败，
        // 故这里必须归一化，否则 -1 会被当成有效句柄
        if (handle == null || handle.pointer == null) return 0L
        val raw =
            com.sun.jna.Pointer
                .nativeValue(handle.pointer)
        return if (raw == -1L) 0L else raw
    }

    private fun writeLine(
        value: Long,
        message: SceneMessage,
    ) {
        val handle =
            com.sun.jna.platform.win32.WinNT
                .HANDLE(com.sun.jna.Pointer(value))
        val bytes = message.encodeLine().toByteArray(Charsets.UTF_8)
        com.sun.jna.platform.win32.Kernel32.INSTANCE
            .WriteFile(
                handle,
                bytes,
                bytes.size,
                com.sun.jna.ptr
                    .IntByReference(),
                null,
            )
        com.sun.jna.platform.win32.Kernel32.INSTANCE
            .FlushFileBuffers(handle)
    }

    private fun readLine(
        value: Long,
        timeoutMs: Long = 3_000,
    ): SceneMessage? {
        val kernel32 = com.sun.jna.platform.win32.Kernel32.INSTANCE
        val handle =
            com.sun.jna.platform.win32.WinNT
                .HANDLE(com.sun.jna.Pointer(value))
        val buffer = java.io.ByteArrayOutputStream()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val available =
                com.sun.jna.ptr
                    .IntByReference()
            if (!kernel32.PeekNamedPipe(handle, null, 0, null, available, null)) return null
            if (available.value <= 0) {
                Thread.sleep(10)
                continue
            }

            val chunk = ByteArray(available.value)
            val read =
                com.sun.jna.ptr
                    .IntByReference()
            if (!kernel32.ReadFile(handle, chunk, chunk.size, read, null) || read.value <= 0) return null
            buffer.write(chunk, 0, read.value)

            val text = buffer.toString("UTF-8")
            val newline = text.indexOf('\n')
            if (newline >= 0) {
                return runCatching {
                    SceneMessage.parse(text.substring(0, newline).trim())
                }.getOrNull()
            }
        }
        return null
    }

    private fun closeHandle(value: Long) {
        com.sun.jna.platform.win32.Kernel32.INSTANCE
            .CloseHandle(
                com.sun.jna.platform.win32.WinNT
                    .HANDLE(com.sun.jna.Pointer(value)),
            )
    }

    private companion object {
        private const val GENERIC_READ = 0x80000000L.toInt()
        private const val GENERIC_WRITE = 0x40000000
        private const val OPEN_EXISTING = 3
    }
}
