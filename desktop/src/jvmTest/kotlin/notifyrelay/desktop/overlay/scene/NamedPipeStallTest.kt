package notifyrelay.desktop.overlay.scene

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 验证对端停止读取时，写出与关闭都不会无限阻塞。
 *
 * 二者是同一故障的两面：同步写会永久停在 WriteFile 上，
 * 随后关闭句柄又会停在内核里等这条未完成的请求，
 * 结果连界面线程也被拖住。
 */
class NamedPipeStallTest {
    @Test
    fun writeTimesOutWhenPeerStopsReading() {
        val server = NamedPipeSceneServer(pipeName = TEST_PIPE)
        val client = connectClient(TEST_PIPE, server)
        assertNotNull(client, "客户端应能连上测试管道")
        try {
            println("STALL 开始握手")
            handshake(client)
            println("STALL 握手写入完成")
            assertTrue(
                awaitCondition(SUBSCRIBE_BUDGET_MS) {
                    val ok = server.isSubscribed()
                    println("STALL 轮询 subscribed=" + ok)
                    ok
                },
                "服务端应已接受订阅，否则后续写压力无从施加",
            )

            // 先确认帧确实在流动：否则「写失败」可能只是没连上
            val flowStart = System.currentTimeMillis()
            assertTrue(
                awaitCondition(FLOW_BUDGET_MS) { driveFrames(server) },
                "订阅成立后应至少能推出一帧",
            )
            val flowCost = System.currentTimeMillis() - flowStart

            // 客户端此后不再读取：缓冲写满后服务端必须自行放弃
            val stallStart = System.currentTimeMillis()
            var stalled = false
            while (System.currentTimeMillis() - stallStart < STALL_BUDGET_MS) {
                if (!driveFrames(server)) {
                    stalled = true
                    break
                }
            }
            val stallCost = System.currentTimeMillis() - stallStart
            assertTrue(stalled, "对端停止读取后，推送应在超时后失败而不是永久阻塞")
            assertTrue(
                stallCost >= WRITE_TIMEOUT_MS / 2,
                "放弃应发生在写超时之后，实际仅 " + stallCost + "ms（首帧 " + flowCost + "ms）",
            )

            // 关闭必须立即返回：它不能等内核把在途写收敛
            val closeStart = System.currentTimeMillis()
            server.close()
            val closeCost = System.currentTimeMillis() - closeStart
            assertTrue(closeCost < CLOSE_BUDGET_MS, "关闭耗时 " + closeCost + "ms，界面线程会被拖住")
        } finally {
            server.close()
            Kernel32.INSTANCE.CloseHandle(client)
        }
    }

    @Test
    fun closeReturnsWhileNoClientConnected() {
        val server = NamedPipeSceneServer(pipeName = TEST_PIPE_IDLE)
        server.start()
        Thread.sleep(300)
        val start = System.currentTimeMillis()
        server.close()
        val cost = System.currentTimeMillis() - start
        assertTrue(cost < CLOSE_BUDGET_MS, "无客户端时关闭耗时 " + cost + "ms")
    }

    /** 推送一帧；在独立线程内计时，避免测试线程被阻塞的写拖住。 */
    private fun driveFrames(server: NamedPipeSceneServer): Boolean {
        val latch = CountDownLatch(1)
        var ok = false
        val worker =
            Thread {
                ok =
                    server.sendFrame(
                        revision = 1,
                        width = FRAME_WIDTH,
                        height = FRAME_HEIGHT,
                        contentHeight = FRAME_HEIGHT,
                        regions = emptyList(),
                        frame =
                            RasterFrame(
                                width = FRAME_WIDTH,
                                height = FRAME_HEIGHT,
                                stride = FRAME_WIDTH * 4,
                                address = payloadAddress,
                            ),
                    )
                latch.countDown()
            }
        worker.isDaemon = true
        worker.start()
        // 留出超过一次写超时的余量：正常路径不解锁就说明写仍挂在内核里
        latch.await(STALL_BUDGET_MS, TimeUnit.MILLISECONDS)
        return ok
    }

    private fun awaitCondition(
        budgetMs: Long,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + budgetMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return false
    }

    private fun handshake(client: WinNT.HANDLE) {
        println(
            "STALL 写 HELLO => " +
                writeLine(client, SceneMessage(type = SceneMessage.TYPE_HELLO, protocolVersion = SCENE_PROTOCOL_VERSION).encodeLine()),
        )
        Thread.sleep(300)
        println(
            "STALL 写 SUBSCRIBE => " +
                writeLine(
                    client,
                    SceneMessage(
                        type = SceneMessage.TYPE_SUBSCRIBE,
                        width = 64f,
                        height = 64f,
                        dpi = 1f,
                    ).encodeLine(),
                ),
        )
    }

    private fun connectClient(
        name: String,
        server: NamedPipeSceneServer,
    ): WinNT.HANDLE? {
        server.start()
        // 服务端实例创建是异步的：连不上就重试到上限
        val deadline = System.currentTimeMillis() + SUBSCRIBE_BUDGET_MS
        while (System.currentTimeMillis() < deadline) {
            val handle =
                runCatching {
                    Kernel32.INSTANCE.CreateFile(
                        "\\\\.\\pipe\\" + name,
                        WinNT.GENERIC_READ or WinNT.GENERIC_WRITE,
                        0,
                        null,
                        WinNT.OPEN_EXISTING,
                        WinNT.FILE_FLAG_OVERLAPPED,
                        null,
                    )
                }.getOrNull()
            if (isValid(handle)) return handle
            Thread.sleep(50)
        }
        return null
    }

    /**
     * 判断句柄是否有效。
     *
     * JNA 对 CreateFile 失败返回 INVALID_HANDLE_VALUE（指针值 -1）而非 null，
     * 直接判空会把失败当成功，后续调用只会得到 ERROR_INVALID_HANDLE。
     */
    private fun isValid(handle: WinNT.HANDLE?): Boolean =
        handle != null &&
            handle.pointer != null &&
            Pointer.nativeValue(handle.pointer) != -1L

    private fun writeLine(
        handle: WinNT.HANDLE,
        line: String,
    ): String {
        val bytes = line.toByteArray(Charsets.UTF_8)
        val io = OverlappedIo()
        try {
            val written = IntByReference()
            val started = Kernel32.INSTANCE.WriteFile(handle, bytes, bytes.size, written, io.overlapped)
            if (started) return "直接完成 字节=" + written.value
            val error = Kernel32.INSTANCE.GetLastError()
            if (error != ERROR_IO_PENDING) return "失败 错误码=" + error
            val wait = Kernel32.INSTANCE.WaitForSingleObject(io.overlapped.hEvent, 2_000)
            if (wait != 0) return "等待失败 结果=" + wait
            val ok = overlappedApi.GetOverlappedResult(handle, io.overlapped, written, false)
            return "排队后完成 ok=" + ok + " 字节=" + written.value
        } finally {
            io.close()
        }
    }

    private interface TestWriteApi : StdCallLibrary {
        fun WriteFile(
            hFile: WinNT.HANDLE,
            buffer: ByteArray,
            bytesToWrite: Int,
            bytesWritten: IntByReference,
            overlapped: WinBase.OVERLAPPED,
        ): Boolean
    }

    private companion object {
        private const val TEST_PIPE = "NotifyRelaySceneStallTest"
        private const val TEST_PIPE_IDLE = "NotifyRelaySceneIdleTest"

        /** 单帧尺寸：远大于管道缓冲，几帧即可写满。 */
        private const val FRAME_WIDTH = 1024
        private const val FRAME_HEIGHT = 512

        /** 服务端单次写超时；与实现一致，用于判定「放弃发生在超时之后」。 */
        private const val WRITE_TIMEOUT_MS = 3_000L

        private const val SUBSCRIBE_BUDGET_MS = 10_000L
        private const val FLOW_BUDGET_MS = 10_000L
        private const val STALL_BUDGET_MS = 30_000L
        private const val CLOSE_BUDGET_MS = 2_000L
        private const val ERROR_IO_PENDING = 997

        private val kernel32Write: TestWriteApi = Native.load("kernel32", TestWriteApi::class.java)

        /**
         * 供帧读取的原生缓冲。
         *
         * 服务端直接从该地址写出像素，不复制到 Java 堆，
         * 因此这里必须先分配一块真实可写内存。
         */
        private val payloadAddress: Long =
            Memory((FRAME_WIDTH * FRAME_HEIGHT * 4).toLong())
                .also { it.clear() }
                .let { Pointer.nativeValue(it) }
    }
}
