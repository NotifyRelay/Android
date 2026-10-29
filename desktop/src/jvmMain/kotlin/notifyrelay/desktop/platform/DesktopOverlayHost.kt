package notifyrelay.desktop.platform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import notifyrelay.base.log.Logger
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * 叠加层伴生进程的装配（JVM 侧）。
 *
 * 职责：
 * 1. 启动时拉起 `OverlayHost.exe`，退出时终止；
 * 2. 经命名管道推送渲染数据（弹幕、媒体卡）并接收其回传日志；
 * 3. 日志转由 [Logger] 输出（与 Rust core 的日志桥同构）。
 *
 * 管道以 Win32 设备路径 `\\.\pipe\<name>` 打开，按字节流读写逐行 UTF-8 JSON；
 * 不占用端口，也不经网络栈。进程为 WinExe，无控制台窗口。
 */
internal object DesktopOverlayHost {
    private const val TAG = "DesktopOverlay"

    /** 默认管道名，与 OverlayHost 一致。 */
    private const val DEFAULT_PIPE_NAME = "notifyrelay-overlay"

    /** 宿主创建管道前的等待重试次数与间隔。 */
    private const val CONNECT_MAX_ATTEMPTS = 40
    private const val CONNECT_RETRY_MS = 150L

    private val json = Json { encodeDefaults = false }

    @Volatile
    private var process: Process? = null

    @Volatile
    private var pipe: RandomAccessFile? = null

    @Volatile
    private var running = false

    @Volatile
    private var readerThread: Thread? = null

    /** 管道未就绪期间的待发消息。 */
    private val pending = mutableListOf<String>()

    /** 待发消息上限：超出丢弃最旧的。 */
    private const val MAX_PENDING = 256

    /** 叠加层是否可用（宿主进程与管道均就绪）。 */
    val available: Boolean get() = running

    /**
     * 启动宿主进程并连接管道。
     *
     * @param exePath OverlayHost.exe 路径；为空或不存在时不启动，仅记录日志
     * @param pipeName 管道名
     * @return 是否成功连接
     */
    fun start(
        exePath: String,
        pipeName: String = DEFAULT_PIPE_NAME,
    ): Boolean {
        if (running) return true
        if (exePath.isBlank() || !File(exePath).exists()) {
            Logger.w(TAG, "未找到 OverlayHost.exe，叠加层不可用: " + exePath)
            return false
        }
        return try {
            val proc =
                ProcessBuilder(exePath, pipeName)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
            process = proc
            Logger.i(TAG, "叠加层宿主已启动: pid=" + proc.pid())

            val opened = connectWithRetry(pipeName)
            if (!opened) {
                Logger.w(TAG, "叠加层管道连接失败，终止宿主进程")
                stop()
                return false
            }
            running = true
            flushPending()
            startLogReader()
            true
        } catch (e: java.io.IOException) {
            Logger.e(TAG, "启动叠加层宿主失败", e)
            stop()
            false
        }
    }

    /**
     * 连接命名管道。
     *
     * 宿主刚启动时管道可能尚未创建，按固定间隔重试。
     */
    private fun connectWithRetry(pipeName: String): Boolean {
        val path = "\\\\.\\pipe\\" + pipeName
        repeat(CONNECT_MAX_ATTEMPTS) { attempt ->
            val proc = process ?: return false
            if (!proc.isAlive) {
                Logger.w(TAG, "叠加层宿主进程已退出，放弃连接")
                return false
            }
            try {
                pipe = RandomAccessFile(path, "rw")
                Logger.i(TAG, "叠加层管道已连接: " + path)
                return true
            } catch (e: java.io.IOException) {
                // 宿主尚未创建管道实例：稍后重试；末次失败时记录原因
                if (attempt == CONNECT_MAX_ATTEMPTS - 1) {
                    Logger.w(TAG, "叠加层管道连接失败: " + path + " " + e.message)
                }
                Thread.sleep(CONNECT_RETRY_MS)
            }
        }
        return false
    }

    /**
     * 读取宿主回传的日志并转由 [Logger] 输出。
     *
     * 命名管道为字节流：读侧独立持有输入流并按行切分；
     * 与写侧（RandomAccessFile.write）互不干扰各自的缓冲。
     */

    /** 连上管道后按序补发启动期缓冲的消息。 */
    private fun flushPending() {
        val batch: List<String>
        synchronized(pending) {
            if (pending.isEmpty()) return
            batch = pending.toList()
            pending.clear()
        }
        batch.forEach { send(it) }
        Logger.i(TAG, "补发叠加层消息 " + batch.size + " 条")
    }

    private fun startLogReader() {
        val f = pipe ?: return
        readerThread =
            Thread({
                try {
                    val input = java.io.FileInputStream(f.fd)
                    val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
                    while (running) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) continue
                        dispatchInbound(line)
                    }
                } catch (_: java.io.IOException) {
                    // 进程退出或管道关闭，属正常结束
                }
            }, "overlay-log-reader").apply {
                isDaemon = true
                start()
            }
    }

    /** 处理宿主回传的一行消息；当前仅有日志。 */
    private fun dispatchInbound(line: String) {
        val obj =
            runCatching {
                json.parseToJsonElement(line) as? JsonObject
            }.getOrNull() ?: return
        val type = (obj["type"] as? JsonPrimitive)?.content
        if (type != "log") return
        val level = (obj["level"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 2
        val message = (obj["message"] as? JsonPrimitive)?.content ?: return
        // .NET LogLevel：0=Trace 1=Debug 2=Information 3=Warning 4=Error 5=Critical
        when (level) {
            0, 1 -> Logger.d(TAG, message)
            2 -> Logger.i(TAG, message)
            3 -> Logger.w(TAG, message)
            else -> Logger.e(TAG, message)
        }
    }

    /**
     * 推送一条渲染消息。
     *
     * @return 是否已写出
     */
    fun send(payload: String): Boolean {
        val f = pipe
        if (f == null) {
            // 管道尚未就绪：缓冲待发，连上后按序补发，避免启动期数据丢失
            synchronized(pending) {
                if (pending.size >= MAX_PENDING) pending.removeAt(0)
                pending.add(payload)
            }
            return false
        }
        return try {
            f.write((payload + "\\n").toByteArray(StandardCharsets.UTF_8))
            true
        } catch (e: java.io.IOException) {
            Logger.w(TAG, "叠加层管道写入失败", e)
            false
        }
    }

    /** 推送一条弹幕。 */
    fun showDanmaku(
        appName: String,
        title: String,
        body: String,
        deviceName: String,
        sourceKey: String? = null,
    ): Boolean =
        send(
            json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("type", "danmaku")
                    put("appName", appName)
                    put("title", title)
                    put("body", body)
                    put("deviceName", deviceName)
                    if (!sourceKey.isNullOrBlank()) put("sourceKey", sourceKey)
                },
            ),
        )

    /**
     * 贴一张由本机渲染好的位图。
     *
     * 媒体卡、超级岛等界面在 JVM 侧绘制，叠加层只按坐标合成，
     * 避免叠加层重复维护一套 UI。
     *
     * @param id 同一 id 表示更新同一张图
     * @param png 已编码的 PNG 字节
     */
    fun showImageOverlay(
        id: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        png: ByteArray,
        opacity: Float = 1f,
    ): Boolean =
        send(
            json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("type", "image_overlay_update")
                    put("id", id)
                    put("x", x)
                    put("y", y)
                    put("width", width)
                    put("height", height)
                    put(
                        "png",
                        java.util.Base64
                            .getEncoder()
                            .encodeToString(png),
                    )
                    if (opacity != 1f) put("opacity", opacity)
                },
            ),
        )

    /** 移除位图叠加。 */
    fun removeImageOverlay(id: String): Boolean =
        send(
            json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("type", "image_overlay_remove")
                    put("id", id)
                },
            ),
        )

    /** 推送超级岛更新。 */
    fun showSuperIsland(
        sourceId: String,
        deviceName: String,
        title: String?,
        text: String?,
        paramV2Raw: String?,
        pics: Map<String, String>?,
        progress: Int = 0,
        timerType: Int = 0,
        timerValue: Long = 0,
        timerStartTime: Long = 0,
    ): Boolean =
        send(
            json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("type", "superisland_update")
                    put("sourceId", sourceId)
                    put("deviceName", deviceName)
                    if (!title.isNullOrBlank()) put("title", title)
                    if (!text.isNullOrBlank()) put("text", text)
                    if (!paramV2Raw.isNullOrBlank()) put("paramV2Raw", paramV2Raw)
                    if (progress > 0) put("progress", progress)
                    if (timerType != 0) put("timerType", timerType)
                    if (timerValue > 0) put("timerValue", timerValue)
                    if (timerStartTime > 0) put("timerStartTime", timerStartTime)
                    if (!pics.isNullOrEmpty()) {
                        putJsonObject("pics") {
                            pics.forEach { (k, v) -> put(k, v) }
                        }
                    }
                },
            ),
        )

    /** 移除超级岛。 */
    fun removeSuperIsland(sourceId: String): Boolean =
        send(
            json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("type", "superisland_remove")
                    put("sourceId", sourceId)
                },
            ),
        )

    /** 推送媒体卡。 */
    fun showMedia(
        deviceId: String,
        deviceName: String,
        title: String,
        artist: String,
        isPlaying: Boolean,
    ): Boolean =
        send(
            json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("type", "media_update")
                    put("deviceId", deviceId)
                    put("deviceName", deviceName)
                    put("title", title)
                    put("artist", artist)
                    put("isPlaying", isPlaying)
                },
            ),
        )

    /** 移除媒体卡。 */
    fun removeMedia(deviceId: String): Boolean =
        send(
            json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("type", "media_remove")
                    put("deviceId", deviceId)
                },
            ),
        )

    /**
     * 停止管道并终止宿主进程。
     *
     * 本方法在 AWT 事件线程（托盘退出菜单）上被调用，必须快速返回：
     * 先杀进程让管道读端收到 EOF，再在后台线程关闭管道并回收进程，
     * 避免 close/waitFor 阻塞 UI 线程造成退出卡死。
     */
    fun stop() {
        running = false
        val proc = process
        process = null
        val f = pipe
        pipe = null
        val reader = readerThread
        readerThread = null

        // 先终止进程：管道对端消失后读线程会立即返回，不依赖 close
        runCatching { proc?.destroy() }
        runCatching { reader?.interrupt() }

        Thread({
            runCatching { f?.close() }
            if (proc != null) {
                runCatching {
                    if (!proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) proc.destroyForcibly()
                }
            }
            Logger.i(TAG, "叠加层宿主已终止")
        }, "overlay-stop").apply {
            isDaemon = true
            start()
        }
    }
}
