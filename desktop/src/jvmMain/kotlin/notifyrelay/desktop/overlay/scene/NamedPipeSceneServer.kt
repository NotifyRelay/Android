package notifyrelay.desktop.overlay.scene

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIFunctionMapper
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

    /** 待推送的场景；由渲染侧写入，会话线程取走后清空。 */
    private val pending = AtomicReference<SceneSnapshot?>(null)

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

    /** 提交一个待推送场景；后续场景覆盖前面的，只保证送达最新一帧。 */
    fun publish(snapshot: SceneSnapshot) {
        pending.set(snapshot)
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
        // 先关句柄：无客户端时线程阻塞在 ConnectNamedPipe 上，
        // 仅置标志位无法让它返回，必须由关闭句柄打断
        closePipe()
        worker?.join(1_000)
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
        var lastInboundAt = System.currentTimeMillis()
        while (running.get()) {
            // 优先推送待发场景：场景与心跳共用一条管道，心跳的时效由下面的空轮询保证
            val snapshot = pending.getAndSet(null)
            if (snapshot != null) {
                val scene =
                    SceneMessage(
                        type = SceneMessage.TYPE_SCENE,
                        revision = snapshot.revision,
                        width = snapshot.widthDp,
                        height = snapshot.heightDp,
                        document = snapshot.document,
                        regions = snapshot.regions,
                    ).encodeLine()
                if (!sendLine(handle, scene)) return
            }

            val message = reader.readMessage(POLL_INTERVAL_MS)
            if (message != null) {
                lastInboundAt = System.currentTimeMillis()
                when (message.type) {
                    SceneMessage.TYPE_PING ->
                        if (!sendLine(
                                handle,
                                SceneMessage(
                                    type = SceneMessage.TYPE_PONG,
                                    revision = lastRevision,
                                ).encodeLine(),
                            )
                        ) {
                            return
                        }

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
                return
            }

            // 心跳：超过判定阈值未收到任何对端报文即视为僵尸会话
            val silentMs = System.currentTimeMillis() - lastInboundAt
            if (silentMs > SCENE_HEARTBEAT_TIMEOUT_MS) {
                Logger.w(TAG, "心跳超时 " + silentMs + "ms，结束会话")
                return
            }
        }
    }

    /** 点击回调：由渲染侧注入；参数为区域 key、阶段与场景坐标（dp）。 */
    var clickListener: ((String, String, Float?, Float?) -> Unit)? = null

    private var lastRevision: Long = 0L

    private fun teardown(handle: WinNT.HANDLE) {
        subscribed.set(false)
        clientSize.set(null)
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

    private fun closePipe(handle: WinNT.HANDLE? = pipe) {
        if (handle == null) return
        runCatching {
            kernel32.DisconnectNamedPipe(handle)
            kernel32.CloseHandle(handle)
        }
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

    /** 写入一行；返回 false 表示管道已断。 */
    private fun sendLine(
        handle: WinNT.HANDLE,
        line: String,
    ): Boolean {
        val bytes = line.toByteArray(Charsets.UTF_8)
        val written = IntByReference()
        val ok = kernel32.WriteFile(handle, bytes, bytes.size, written, null)
        if (!ok) {
            val error = kernel32.GetLastError()
            // ERROR_BROKEN_PIPE / ERROR_NO_DATA 表示客户端已走，属正常断开
            if (error != ERROR_BROKEN_PIPE && error != ERROR_NO_DATA) {
                Logger.w(TAG, "写入管道失败 错误码=" + error)
            }
            return false
        }
        kernel32.FlushFileBuffers(handle)
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
        private const val BUFFER_SIZE = 64 * 1024

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

        private const val HANDSHAKE_TIMEOUT_MS = 10_000L
        private const val POLL_INTERVAL_MS = 250L
        private const val RETRY_INTERVAL_MS = 500L

        /** 命名管道完整路径前缀。 */
        private val PIPE_PREFIX = "\\\\.\\pipe\\"

        /** 命名管道与文件调用；JNA 自带实现的创建与连接均已验证可用。 */
        private val kernel32: Kernel32 = Kernel32.INSTANCE

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

/** 一次待推送的完整场景。 */
internal data class SceneSnapshot(
    val revision: Long,
    val widthDp: Float,
    val heightDp: Float,
    val document: String,
    val regions: List<SceneRegion>,
)
/** advapi32 的 SDDL 转换入口；返回的安全描述符需用 LocalFree 释放。 */

/** advapi32 的 SDDL 转换入口；返回的安全描述符需用 LocalFree 释放。 */
internal interface SddlApi : StdCallLibrary {
    fun ConvertStringSecurityDescriptorToSecurityDescriptorA(
        stringSecurityDescriptor: String,
        stringSDRevision: Int,
        securityDescriptor: PointerByReference,
    ): Boolean
}