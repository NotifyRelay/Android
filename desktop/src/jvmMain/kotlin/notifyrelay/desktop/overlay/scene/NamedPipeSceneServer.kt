package notifyrelay.desktop.overlay.scene

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import notifyrelay.base.log.Logger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * 场景会话状态机。
 *
 * 尺寸未知时布局约束不成立，故 [Handshaking] 期间不推场景；
 * 心跳超时进入 [Stale] 后必定走 [Teardown] 回收管道实例。
 */
internal enum class SceneSessionState {
    Idle,
    WaitingClient,
    Handshaking,
    Live,
    Stale,
    Teardown,
}

/**
 * 矢量场景的命名管道服务端。
 *
 * 单实例管道：Gamebar 小组件同一时刻只有一个。连接、握手、订阅、推场景与心跳
 * 全部在一条守护线程里推进，外部只通过 [publish] 提交「有新场景可推」这一事实，
 * 渲染与录制留在调用线程之外执行，避免与 Compose 的界面线程争用。
 *
 * 存活判定用显式心跳：管道可读只说明「暂时没有报文」，无法区分对端卡死与空闲。
 */
internal class NamedPipeSceneServer(
    private val pipeName: String = SCENE_PIPE_NAME,
    private val onClientGone: () -> Unit = {},
) : AutoCloseable {
    private val running = AtomicBoolean(false)

    /** 待发的控制报文（如心跳回应）。 */
    private val pendingControl = AtomicReference<SceneMessage?>(null)

    /**
     * 当前会话的管道句柄；无会话时为 0。
     *
     * 帧由渲染线程直接写出，不再经过队列：像素缓冲复用且只在渲染回调内有效，
     * 排队会让缓冲在写出前被下一帧覆盖。
     */
    @Volatile
    private var activePipe: WinNT.HANDLE? = null

    /** 串行化写管道：帧与心跳回应来自不同线程。 */
    private val writeLock = Any()

    /** 当前会话是否已有客户端订阅；无客户端时不录制场景。 */
    private val subscribed = AtomicBoolean(false)

    /** 最近一次被接受的订阅尺寸，供渲染侧按需录制。 */
    private val clientSize = AtomicReference<ClientViewport?>(null)

    private var worker: Thread? = null
    private var pipe: WinNT.HANDLE? = null

    // 安全描述符与属主结构必须与管道同生命周期，否则指向已释放内存
    private var securityDescriptor: Pointer? = null
    private var securityAttributes: WinBase.SECURITY_ATTRIBUTES? = null

    /** 最近一次建管道的 Win32 错误码；0 表示成功。供诊断读取。 */
    var lastCreateError: Int = 0
        private set

    /** 最近一次成功创建的完整管道名；供诊断读取。 */
    var lastCreatedName: String = ""
        private set

    /** 是否需要录制场景；为 true 表示已有客户端订阅且尺寸已知。 */
    fun isSubscribed(): Boolean = subscribed.get()

    /** 当前客户端声明的画布尺寸；未订阅时为 null。 */
    fun viewport(): ClientViewport? = clientSize.get()

    /**
     * 推送一帧位图：先写一行报文头，再原样写出像素。
     *
     * 像素从 [RasterFrame.address] 直接写出，不经 Java 堆中转。
     * 必须在渲染回调内调用：该地址在那之后即失效。
     */
    fun sendFrame(
        revision: Long,
        width: Int,
        height: Int,
        contentHeight: Int,
        regions: List<SceneRegion>,
        frame: RasterFrame,
    ): Boolean {
        val handle = activePipe
        if (handle == null || !subscribed.get()) {
            return false
        }

        val header =
            SceneMessage(
                type = SceneMessage.TYPE_FRAME,
                revision = revision,
                width = width.toFloat(),
                height = height.toFloat(),
                length = frame.byteCount,
                contentHeight = contentHeight.toFloat(),
                regions = regions,
            ).encodeLine().toByteArray(Charsets.UTF_8)

        // 头部与像素必须连续写入，中间不能插入心跳回应
        synchronized(writeLock) {
            if (!writeHeader(handle, header)) {
                return false
            }
            return writeAll(handle, Pointer(frame.address), frame.byteCount)
        }
    }

    /** 写一条控制报文；与帧写出互斥。 */
    private fun sendControl(
        handle: WinNT.HANDLE,
        message: SceneMessage,
    ): Boolean {
        val line = message.encodeLine().toByteArray(Charsets.UTF_8)
        synchronized(writeLock) { return writeHeader(handle, line) }
    }

    /** 写报文头；体积远小于管道缓冲，一次写完。 */
    private fun writeHeader(
        handle: WinNT.HANDLE,
        header: ByteArray,
    ): Boolean {
        val written = IntByReference()
        if (!kernel32.WriteFile(handle, header, header.size, written, null)) {
            val error = kernel32.GetLastError()
            if (error != ERROR_BROKEN_PIPE && error != ERROR_NO_DATA) {
                Logger.w(TAG, "写入报文头失败 错误码=" + error)
            }
            return false
        }
        return written.value == header.size
    }

    /**
     * 从原生地址写满 [count] 字节。
     *
     * 大块写在字节模式管道的缓冲写满后会只写入一部分，必须循环推进偏移。
     */
    private fun writeAll(
        handle: WinNT.HANDLE,
        data: Pointer,
        count: Int,
    ): Boolean {
        var offset = 0
        while (offset < count) {
            val written = IntByReference()
            if (!pipeWrite.WriteFile(handle, data.share(offset.toLong()), count - offset, written, null)) {
                val error = kernel32.GetLastError()
                if (error != ERROR_BROKEN_PIPE && error != ERROR_NO_DATA) {
                    Logger.w(TAG, "写入管道失败 错误码=" + error)
                }
                return false
            }
            if (written.value <= 0) {
                return false
            }
            offset += written.value
        }
        return true
    }

    /** 启动监听线程。 */
    fun start() {
        if (!running.compareAndSet(false, true)) return
        worker =
            thread(isDaemon = true, name = "overlay-scene-pipe") {
                serveLoop()
            }
    }

    override fun close() {
        running.set(false)

        // 唤醒可能空闲中的发送线程，并让会话循环尽快看到停止标志
        pendingControl.set(null)
        activePipe = null

        // 先关句柄：无客户端时线程阻塞在 ConnectNamedPipe 上，
        // 仅置标志位无法让它返回，必须由关闭句柄打断。
        closePipe()

        // 必须等监听线程真正退出：它退出时还会走一次会话收尾，
        // 若此时调用方已开始下一轮（测试里同名管道重建），两个会话会互相干扰。
        worker?.join(CLOSE_JOIN_TIMEOUT_MS)
        if (worker?.isAlive == true) {
            Logger.w(TAG, "监听线程未在 " + CLOSE_JOIN_TIMEOUT_MS + "ms 内退出")
        }
        worker = null
    }

    // ======== 会话主循环 ========

    private fun serveLoop() {
        while (running.get()) {
            val handle = createPipe()
            if (handle == null) {
                Thread.sleep(RETRY_INTERVAL_MS)
                continue
            }
            pipe = handle
            try {
                serveSession(handle)
            } catch (t: Throwable) {
                Logger.w(TAG, "场景会话异常结束", t)
            } finally {
                teardown(handle)
            }
        }
    }

    /**
     * 一次完整会话：握手 → 订阅 → 循环收发。
     *
     * 握手与订阅都设超时，避免连上却不说话的客户端长期占住唯一实例。
     */
    private fun serveSession(handle: WinNT.HANDLE) {
        if (!kernel32.ConnectNamedPipe(handle, null)) {
            val error = kernel32.GetLastError()
            // ERROR_PIPE_CONNECTED 表示客户端在 ConnectNamedPipe 之前就已连上，属正常
            if (error != ERROR_PIPE_CONNECTED) {
                Logger.w(TAG, "等待客户端连接失败 错误码=" + error)
                return
            }
        }

        val reader = LineReader(handle)

        // ---- 握手 ----
        val hello = reader.readMessage(HANDSHAKE_TIMEOUT_MS) ?: return
        if (hello.type != SceneMessage.TYPE_HELLO) {
            sendError(handle, "expected_hello", "首条报文必须是 " + SceneMessage.TYPE_HELLO)
            return
        }
        val version = hello.protocolVersion ?: -1
        if (version != SCENE_PROTOCOL_VERSION) {
            sendLine(
                handle,
                SceneMessage(
                    type = SceneMessage.TYPE_REJECT,
                    reason = "protocol_version_mismatch",
                    protocolVersion = SCENE_PROTOCOL_VERSION,
                ).encodeLine(),
            )
            Logger.w(TAG, "协议版本不匹配 客户端=" + version + " 服务端=" + SCENE_PROTOCOL_VERSION)
            return
        }
        if (!sendLine(
                handle,
                SceneMessage(
                    type = SceneMessage.TYPE_WELCOME,
                    protocolVersion = SCENE_PROTOCOL_VERSION,
                ).encodeLine(),
            )
        ) {
            return
        }

        // ---- 订阅 ----
        val subscribe = reader.readMessage(HANDSHAKE_TIMEOUT_MS) ?: return
        if (subscribe.type != SceneMessage.TYPE_SUBSCRIBE) {
            sendError(handle, "expected_subscribe", "握手后必须订阅画布尺寸")
            return
        }
        val width = subscribe.width ?: 0f
        val height = subscribe.height ?: 0f
        if (width <= 0f || height <= 0f) {
            sendError(handle, "invalid_viewport", "画布尺寸必须为正")
            return
        }
        clientSize.set(ClientViewport(width, height, subscribe.dpi ?: 1f, subscribe.theme))
        subscribed.set(true)
        Logger.i(TAG, "客户端已订阅 尺寸=" + width + "x" + height + " dpi=" + subscribe.dpi)

        // ---- 收发循环 ----
        // 心跳回应独立成线程：管道写满时 WriteFile 会阻塞到对端读取为止，
        // 若与读取、心跳判定同线程，写阻塞会让心跳停摆，双方互相判定失效。
        // 帧不走这条线程：像素缓冲复用且只在渲染回调内有效，排队会被下一帧覆盖。
        val sendRunning =
            java.util.concurrent.atomic
                .AtomicBoolean(true)
        val sender =
            thread(isDaemon = true, name = "overlay-scene-sender") {
                while (sendRunning.get() && running.get()) {
                    val control =
                        pendingControl.getAndSet(null) ?: run {
                            Thread.sleep(SEND_POLL_MS)
                            continue
                        }
                    if (!sendControl(handle, control)) {
                        sendRunning.set(false)
                        return@thread
                    }
                }
            }

        activePipe = handle
        var lastInboundAt = System.currentTimeMillis()
        try {
            while (running.get()) {
                val message = reader.readMessage(POLL_INTERVAL_MS)
                if (message != null) {
                    lastInboundAt = System.currentTimeMillis()
                    when (message.type) {
                        SceneMessage.TYPE_PING ->
                            // 回包同样交给发送线程，读线程不直接写管道
                            pendingControl.set(
                                SceneMessage(
                                    type = SceneMessage.TYPE_PONG,
                                    revision = lastRevision,
                                ),
                            )

                        SceneMessage.TYPE_CLICK -> {
                            val key = message.key ?: continue
                            val phase = message.phase ?: PHASE_UP
                            clickListener?.invoke(key, phase, message.x, message.y)
                        }

                        SceneMessage.TYPE_SUBSCRIBE -> {
                            // 尺寸变化不重建会话：按新约束重新录制即可
                            val newWidth = message.width ?: continue
                            val newHeight = message.height ?: continue
                            if (newWidth > 0f && newHeight > 0f) {
                                clientSize.set(
                                    ClientViewport(newWidth, newHeight, message.dpi ?: 1f, message.theme),
                                )
                            }
                        }

                        else -> Unit
                    }
                } else if (reader.closed) {
                    break
                }

                // 心跳：超过判定阈值未收到任何对端报文即视为僵尸会话
                val silentMs = System.currentTimeMillis() - lastInboundAt
                if (silentMs > SCENE_HEARTBEAT_TIMEOUT_MS) {
                    Logger.w(TAG, "心跳超时 " + silentMs + "ms，结束会话")
                    break
                }
            }
        } finally {
            sendRunning.set(false)
            sender.join(1_000)
        }
    }

    /** 点击回调：由渲染侧注入；参数为区域 key、阶段与场景坐标（dp）。 */
    var clickListener: ((String, String, Float?, Float?) -> Unit)? = null

    private var lastRevision: Long = 0L

    private fun teardown(handle: WinNT.HANDLE) {
        subscribed.set(false)
        clientSize.set(null)

        // 会话结束即清掉待发内容，避免旧报文串入新会话
        pendingControl.set(null)
        activePipe = null

        closePipe(handle)
        onClientGone()
    }

    // ======== 管道与发送 ========

    private fun createPipe(): WinNT.HANDLE? {
        // SECURITY_ATTRIBUTES 必须连同 nLength 一起传，故取结构体自身指针
        val security = buildSecurityAttributes()
        val fullName = PIPE_PREFIX + pipeName
        val securityPointer = security.pointer
        val handle =
            kernel32.CreateNamedPipe(
                fullName,
                PIPE_ACCESS_DUPLEX,
                PIPE_TYPE_BYTE or PIPE_READMODE_BYTE or PIPE_WAIT,
                MAX_INSTANCES,
                BUFFER_SIZE,
                BUFFER_SIZE,
                0,
                security,
            )
        val raw = if (handle?.pointer == null) -1L else Pointer.nativeValue(handle.pointer)
        if (raw == -1L) {
            lastCreateError = kernel32.GetLastError()
            Logger.w(TAG, "创建命名管道失败 错误码=" + lastCreateError + " name=" + fullName)
            return null
        }
        lastCreateError = 0
        lastCreatedName = fullName
        return handle
    }

    /**
     * 关闭管道实例。
     *
     * 只调 CloseHandle：它本身就会终止连接并唤醒阻塞中的读写。
     * 不先调 DisconnectNamedPipe——该调用在对端尚未确认断开时会阻塞，
     * 而本方法常从会话收尾与关闭路径调用，阻塞会让调用方（含 UI 线程与测试线程）卡死。
     * 每次会话都重建实例，不需要复用同一个实例。
     */
    private fun closePipe(handle: WinNT.HANDLE? = pipe) {
        if (handle == null) return
        runCatching { kernel32.CloseHandle(handle) }
        if (handle === pipe) pipe = null
    }

    private fun sendError(
        handle: WinNT.HANDLE,
        code: String,
        message: String,
    ) {
        sendLine(
            handle,
            SceneMessage(type = SceneMessage.TYPE_ERROR, reason = code).encodeLine(),
        )
        Logger.w(TAG, "已回报错误 code=" + code + " " + message)
    }

    /**
     * 写入一行。
     *
     * 直接把整行交给内核写端，失败才判定断开。
     *
     * 不再为每条报文新建线程、也不再逐条 FlushFileBuffers：
     * 前者在每秒数十帧时会产生大量线程，后者会把每次写同步到对端读完为止，
     * 两者叠加会让写入远慢于产出，管道最终被塞满。
     * 管道本身有缓冲，FlushFileBuffers 对字节流没有额外语义。
     */
    private fun sendLine(
        handle: WinNT.HANDLE,
        line: String,
    ): Boolean {
        val bytes = line.toByteArray(Charsets.UTF_8)
        val written = IntByReference()
        if (!kernel32.WriteFile(handle, bytes, bytes.size, written, null)) {
            val error = kernel32.GetLastError()
            // ERROR_BROKEN_PIPE / ERROR_NO_DATA 表示客户端已走，属正常断开
            if (error != ERROR_BROKEN_PIPE && error != ERROR_NO_DATA) {
                Logger.w(TAG, "写入管道失败 错误码=" + error)
            }
            return false
        }
        return true
    }

    /**
     * 构造放行应用容器的安全描述符。
     *
     * UWP 小组件以应用容器身份运行，其令牌不带当前用户组，
     * 必须显式放行「所有应用包」SID，否则连接被拒且只报错误码 5。
     *
     * 这里用 SDDL 一次成型而非手工拼 ACE：ACL 头、ACE 计数与 AclSize
     * 必须完全自洽，任何一处算错都会让整条 DACL 无效。
     */
    private fun buildSecurityAttributes(): WinBase.SECURITY_ATTRIBUTES {
        val descriptor =
            runCatching {
                val ref = PointerByReference()
                if (!sddlApi.ConvertStringSecurityDescriptorToSecurityDescriptorA(
                        PIPE_SDDL,
                        SDDL_REVISION_1,
                        ref,
                    )
                ) {
                    Logger.w(TAG, "SDDL 转换失败 错误码=" + kernel32.GetLastError())
                    return@runCatching null
                }
                ref.value
            }.getOrNull()

        if (descriptor == null) {
            // 退回默认安全描述符：同用户下的进程仍可连接
            return WinBase.SECURITY_ATTRIBUTES().apply {
                lpSecurityDescriptor = null
                bInheritHandle = false
                write()
            }
        }

        // 保持强引用：内存一旦回收，管道持有的指针即失效
        securityDescriptor = descriptor

        val attributes =
            WinBase.SECURITY_ATTRIBUTES().apply {
                lpSecurityDescriptor = descriptor
                bInheritHandle = false
            }
        // 触发内存分配并写入 nLength 等字段；结构体由本类字段持有
        attributes.write()
        securityAttributes = attributes
        return attributes
    }

    private companion object {
        private const val TAG = "OverlayScenePipe"

        private const val PIPE_ACCESS_DUPLEX = 0x00000003
        private const val PIPE_TYPE_BYTE = 0x00000000
        private const val PIPE_READMODE_BYTE = 0x00000000
        private const val PIPE_WAIT = 0x00000000
        private const val MAX_INSTANCES = 1

        /** 管道缓冲：按典型一帧场景的大小留出数帧余量，吸收突发推送。 */
        private const val BUFFER_SIZE = 4 * 1024 * 1024

        /** 以重叠方式打开读写端，使读写互不阻塞。 */
        private const val FILE_FLAG_OVERLAPPED = 0x40000000

        /**
         * 管道 DACL：所有应用包、Everyone、LocalSystem、所有者均可读写。
         *
         * 掩码取 FILE_GENERIC_READ（0x120089）与 FILE_GENERIC_WRITE（0x120116）的并集：
         * 客户端以 GENERIC_READ|GENERIC_WRITE 打开时，系统会展开成这组具体权利并逐位比对，
         * 少 FILE_APPEND_DATA（0x4）一位即整体判为拒绝访问。
         * AC 为 ALL APPLICATION PACKAGES（S-1-15-2-1），UWP 小组件连入全信任进程管道的必要条件。
         */
        private const val PIPE_SDDL = "D:(A;;0x12019F;;;AC)(A;;0x12019F;;;WD)(A;;0x12019F;;;SY)(A;;0x12019F;;;OW)"

        private const val SDDL_REVISION_1 = 1

        private const val PHASE_UP = "up"

        private const val ERROR_PIPE_CONNECTED = 535
        private const val ERROR_BROKEN_PIPE = 109
        private const val ERROR_NO_DATA = 232

        /** 重叠写已排入队列，需等待完成事件。 */
        private const val ERROR_IO_PENDING = 997

        /** 单次写入的等待上限；超时即认为对端停止读取。 */
        private const val WRITE_TIMEOUT_MS = 3_000L

        private const val HANDSHAKE_TIMEOUT_MS = 10_000L
        private const val POLL_INTERVAL_MS = 250L
        private const val RETRY_INTERVAL_MS = 500L

        /** 发送线程空闲时的轮询间隔。 */
        private const val SEND_POLL_MS = 2L

        /** 关闭时等待监听线程退出的上限。 */
        private const val CLOSE_JOIN_TIMEOUT_MS = 5_000L

        /** 命名管道完整路径前缀。 */
        private val PIPE_PREFIX = "\\\\.\\pipe\\"

        /** 命名管道与文件调用；JNA 自带实现的创建与连接均已验证可用。 */
        private val kernel32: Kernel32 = Kernel32.INSTANCE

        private val pipeWrite: PipeWriteApi = Native.load("kernel32", PipeWriteApi::class.java)

        /**
         * SDDL 转换接口。
         *
         * 该函数实际导出为 ...DescriptorA（ANSI），W 版本存在但调用返回失败；
         * 不带后缀的名字在本机无法解析。因此显式声明 A 版本并以 ANSI 映射加载。
         * 转换失败会让管道退回默认 DACL，外部进程写入被拒（错误码 5）。
         */
        private val sddlApi: SddlApi = Native.load("advapi32", SddlApi::class.java)
    }
}

internal data class ClientViewport(
    val widthDp: Float,
    val heightDp: Float,
    val dpi: Float,
    val theme: String?,
)

/** 从原生地址写管道；JNA 自带的 WriteFile 只接受 Java 数组。 */
internal interface PipeWriteApi : StdCallLibrary {
    fun WriteFile(
        hFile: WinNT.HANDLE,
        buffer: Pointer,
        bytesToWrite: Int,
        bytesWritten: IntByReference,
        overlapped: Pointer?,
    ): Boolean
}

/** advapi32 的 SDDL 转换入口；返回的安全描述符需用 LocalFree 释放。 */
internal interface SddlApi : StdCallLibrary {
    fun ConvertStringSecurityDescriptorToSecurityDescriptorA(
        stringSecurityDescriptor: String,
        stringSDRevision: Int,
        securityDescriptor: PointerByReference,
    ): Boolean
}
