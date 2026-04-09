package io.github.miuzarte.scrcpyforandroid.nativecore

import android.util.Log
import android.view.KeyEvent
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStreamReader
import java.nio.file.Path
import java.security.SecureRandom
import java.util.ArrayDeque
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class ScrcpySessionManager(private val adbService: NativeAdbService) {
    @Volatile
    private var activeSession: ActiveSession? = null

    @Volatile
    private var videoConsumer: ((VideoPacket) -> Unit)? = null

    @Volatile
    private var videoReaderThread: Thread? = null

    @Volatile
    private var audioConsumer: ((AudioPacket) -> Unit)? = null

    @Volatile
    private var audioReaderThread: Thread? = null

    @Volatile
    private var lastServerCommand: String? = null
    private val serverLogBuffer = ArrayDeque<String>()

    /**
     * 启动 scrcpy 会话。
     *
     * 职责：
     * - 将服务器工件推送到设备，构建服务器命令，并打开服务器 shell 流。
     * - 使用重试机制打开所需的抽象套接字（视频/音频/控制），并读取初始会话元数据（设备名称、编解码器、分辨率）。
     * - 初始化一个 [ActiveSession]，用于保存套接字流和读取线程。
     *
     * 线程说明：
     * - 此方法是同步的，以避免并发启动/停止。
     * - 与 adb 交互时可能会阻塞；调用方应在适当时在 UI 线程之外执行。外观模式使用执行器来序列化此类调用。
     */
    @Synchronized
    fun start(serverJarPath: Path, options: ScrcpyStartOptions): SessionInfo {
        stop()
        synchronized(this) {
            serverLogBuffer.clear()
        }
        val targetPath = options.serverRemotePath.ifBlank { DEFAULT_SERVER_REMOTE_PATH }
        val scid = random.nextInt(Int.MAX_VALUE)
        val socketName = socketNameFor(scid)

        var serverStream: AdbSocketStream? = null
        var serverLogThread: Thread? = null
        var firstStream: AdbSocketStream? = null
        var videoStream: AdbSocketStream? = null
        var videoInput: DataInputStream? = null
        var audioStream: AdbSocketStream? = null
        var audioInput: DataInputStream? = null
        var controlStream: AdbSocketStream? = null

        try {
            adbService.push(serverJarPath, targetPath)
            val cmd = buildServerCommand(targetPath, scid, options)
            lastServerCommand = cmd
            Log.i(
                TAG,
                "start(): socket=$socketName codec=${options.videoCodec} audio=${options.audio} audioCodec=${options.audioCodec}"
            )
            serverStream = adbService.openShellStream(cmd)
            serverLogThread = startServerLogThread(serverStream!!, socketName)
            Thread.sleep(SERVER_BOOT_DELAY_MS)

            // The first socket always carries device meta (and dummy byte).
            firstStream = openAbstractSocketWithRetry(socketName, expectDummyByte = true)
            val firstInput = DataInputStream(BufferedInputStream(firstStream!!.inputStream))

            when {
                options.video -> {
                    videoStream = firstStream
                    videoInput = firstInput
                }

                options.audio -> {
                    audioStream = firstStream
                    audioInput = firstInput
                }

                options.control -> {
                    controlStream = firstStream
                }

                else -> {
                    throw IllegalArgumentException("At least one of video/audio/control must be enabled")
                }
            }

            if (options.video && videoStream == null) {
                val vStream = openAbstractSocketWithRetry(socketName, expectDummyByte = false)
                videoStream = vStream
                videoInput = DataInputStream(BufferedInputStream(vStream.inputStream))
            }

            if (options.audio && audioStream == null) {
                val aStream = openAbstractSocketWithRetry(socketName, expectDummyByte = false)
                audioStream = aStream
                audioInput = DataInputStream(BufferedInputStream(aStream.inputStream))
            }

            if (options.control && controlStream == null) {
                controlStream = openAbstractSocketWithRetry(socketName, expectDummyByte = false)
            }

            val deviceName = readDeviceName(firstInput)
            val audioCodecId = if (options.audio) audioCodecIdFromName(options.audioCodec) else 0
            val codecId: Int
            val width: Int
            val height: Int
            if (options.video) {
                val vInput = checkNotNull(videoInput)
                codecId = vInput.readInt()
                width = vInput.readInt()
                height = vInput.readInt()
            } else {
                codecId = 0
                width = 0
                height = 0
            }

            val sessionInfo = SessionInfo(
                deviceName = deviceName,
                codecId = codecId,
                codecName = codecName(codecId),
                width = width,
                height = height,
                audioCodecId = audioCodecId,
                controlEnabled = controlStream != null,
            )

            activeSession = ActiveSession(
                info = sessionInfo,
                socketName = socketName,
                serverStream = serverStream!!,
                serverLogThread = serverLogThread!!,
                videoStream = videoStream,
                videoInput = videoInput,
                audioStream = audioStream,
                audioInput = audioInput,
                controlStream = controlStream,
                controlWriter = controlStream?.outputStream?.let {
                    ScrcpyControlWriter(
                        DataOutputStream(it)
                    )
                },
            )
            return sessionInfo
        } catch (t: Throwable) {
            cleanupOnStartFailure(
                serverStream = serverStream,
                serverLogThread = serverLogThread,
                firstStream = firstStream,
                videoStream = videoStream,
                audioStream = audioStream,
                controlStream = controlStream
            )
            val tail = snapshotServerLogs()
            val detail = if (tail.isBlank()) "" else " | server_log_tail=\n$tail"
            throw IllegalStateException("scrcpy start failed: ${t.message}$detail", t)
        }
    }

    private fun cleanupOnStartFailure(
        serverStream: AdbSocketStream?,
        serverLogThread: Thread?,
        firstStream: AdbSocketStream?,
        videoStream: AdbSocketStream?,
        audioStream: AdbSocketStream?,
        controlStream: AdbSocketStream?
    ) {
        runCatching { controlStream?.close() }
        runCatching { audioStream?.close() }
        runCatching { videoStream?.close() }
        if (firstStream !== videoStream && firstStream !== audioStream && firstStream !== controlStream) {
            runCatching { firstStream?.close() }
        }
        runCatching { serverStream?.close() }
        if (serverLogThread != null && Thread.currentThread() !== serverLogThread) {
            runCatching { serverLogThread.interrupt() }
            runCatching { serverLogThread.join(300) }
        }
    }

    /**
     * 附加视频消费者回调。
     *
     * - 启动一个专用的 `scrcpy-video-reader` 线程，从视频套接字读取分帧的 Annex B
     *   数据包，并将 `VideoPacket` 实例传递给 [consumer]。
     * - 当会话结束或套接字关闭时，读取线程会停止。
     * - 消费者应能够应对偶尔的数据包丢失或读取器错误。
     */
    @Synchronized
    fun attachVideoConsumer(consumer: (VideoPacket) -> Unit) {
        val session = activeSession ?: throw IllegalStateException("scrcpy session not started")
        val vInput = session.videoInput ?: return
        val vStream = session.videoStream ?: return
        videoConsumer = consumer
        if (videoReaderThread?.isAlive == true) {
            return
        }

        videoReaderThread = thread(start = true, name = "scrcpy-video-reader") {
            while (activeSession === session && !vStream.closed) {
                try {
                    val ptsAndFlags = vInput.readLong()
                    val packetSize = vInput.readInt()
                    if (packetSize <= 0) {
                        continue
                    }

                    val payload = ByteArray(packetSize)
                    vInput.readFully(payload)

                    val config = (ptsAndFlags and PACKET_FLAG_CONFIG) != 0L
                    val keyFrame = (ptsAndFlags and PACKET_FLAG_KEY_FRAME) != 0L
                    val ptsUs = ptsAndFlags and PACKET_PTS_MASK
                    videoConsumer?.invoke(
                        VideoPacket(
                            data = payload,
                            ptsUs = ptsUs,
                            isConfig = config,
                            isKeyFrame = keyFrame,
                        ),
                    )
                } catch (_: EOFException) {
                    break
                } catch (_: InterruptedException) {
                    // Ignore transient interrupts while session remains active.
                    if (activeSession !== session || vStream.closed) {
                        break
                    }
                    Thread.interrupted()
                } catch (e: Exception) {
                    Log.w(TAG, "video reader failed", e)
                    break
                }
            }
        }
    }

    @Synchronized
    fun clearVideoConsumer() {
        videoConsumer = null
    }

    /**
     * 附加音频消费者回调。
     *
     * - 与视频消费者类似，这会启动一个 `scrcpy-audio-reader` 线程，
     *   该线程读取音频数据包并将 `AudioPacket` 分派给提供的回调。
     * - 该函数读取音频流头部以确定音频是否可用，如果禁用则提前退出。
     */
    @Synchronized
    fun attachAudioConsumer(consumer: (AudioPacket) -> Unit) {
        val session = activeSession ?: throw IllegalStateException("scrcpy session not started")
        val aInput = session.audioInput ?: return // audio disabled or unavailable
        val aStream = session.audioStream ?: return
        audioConsumer = consumer
        if (audioReaderThread?.isAlive == true) return

        audioReaderThread = thread(start = true, name = "scrcpy-audio-reader") {
            val streamCodecId = try {
                aInput.readInt()
            } catch (e: Exception) {
                Log.w(TAG, "audio codec header read failed", e)
                return@thread
            }
            when (streamCodecId) {
                AUDIO_DISABLED -> {
                    Log.w(TAG, "audio disabled by server")
                    return@thread
                }

                AUDIO_ERROR -> {
                    Log.e(TAG, "audio stream configuration error from server")
                    return@thread
                }

                else -> {
                    Log.i(TAG, "audio stream codec=0x${streamCodecId.toUInt().toString(16)}")
                }
            }
            if (session.info.audioCodecId != 0 && streamCodecId != session.info.audioCodecId) {
                Log.w(
                    TAG,
                    "audio codec mismatch: requested=0x${
                        session.info.audioCodecId.toUInt().toString(16)
                    } stream=0x${streamCodecId.toUInt().toString(16)}",
                )
            }

            while (activeSession === session && !aStream.closed) {
                try {
                    val ptsAndFlags = aInput.readLong()
                    val packetSize = aInput.readInt()
                    if (packetSize <= 0) continue

                    val payload = ByteArray(packetSize)
                    aInput.readFully(payload)

                    val isConfig = (ptsAndFlags and PACKET_FLAG_CONFIG) != 0L
                    val ptsUs = ptsAndFlags and PACKET_PTS_MASK
                    audioConsumer?.invoke(
                        AudioPacket(
                            data = payload,
                            ptsUs = ptsUs,
                            isConfig = isConfig
                        )
                    )
                } catch (_: EOFException) {
                    break
                } catch (_: InterruptedException) {
                    if (activeSession !== session || aStream.closed) break
                    Thread.interrupted()
                } catch (e: Exception) {
                    Log.w(TAG, "audio reader failed", e)
                    break
                }
            }
        }
    }

    @Synchronized
    fun clearAudioConsumer() {
        audioConsumer = null
    }

    /**
     * 注入按键事件到控制通道。
     *
     * - 需要活动的控制通道；如果缺少则抛出异常。
     * - 同步以序列化控制写入。
     */
    @Synchronized
    fun injectKeycode(action: Int, keycode: Int, repeat: Int = 0, metaState: Int = 0) {
        requireControlWriter().injectKeycode(action, keycode, repeat, metaState)
    }

    @Synchronized
    fun injectText(text: String) {
        requireControlWriter().injectText(text)
    }

    /**
     * 注入触摸事件到控制通道。
     *
     * - 坐标以设备像素为单位，并与屏幕尺寸一起写入，以确保服务器正确解释它们。
     * - 同步以序列化控制写入。
     */
    @Synchronized
    fun injectTouch(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float,
        actionButton: Int,
        buttons: Int,
    ) {
        requireControlWriter().injectTouch(
            action,
            pointerId,
            x,
            y,
            screenWidth,
            screenHeight,
            pressure,
            actionButton,
            buttons
        )
    }

    @Synchronized
    fun injectScroll(
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        hScroll: Float,
        vScroll: Float,
        buttons: Int
    ) {
        requireControlWriter().injectScroll(
            x,
            y,
            screenWidth,
            screenHeight,
            hScroll,
            vScroll,
            buttons
        )
    }

    @Synchronized
    fun pressBackOrScreenOn(action: Int = KeyEvent.ACTION_DOWN) {
        requireControlWriter().pressBackOrScreenOn(action)
    }

    @Synchronized
    fun setDisplayPower(on: Boolean) {
        requireControlWriter().setDisplayPower(on)
    }

    /**
     * 停止活动会话并清理读取线程和流。
     *
     * - 中断并等待读取线程，关闭套接字并清除状态。 安全调用。
     */
    @Synchronized
    fun stop() {
        val session = activeSession ?: return
        activeSession = null
        videoConsumer = null
        audioConsumer = null

        if (Thread.currentThread() !== videoReaderThread) {
            runCatching { videoReaderThread?.interrupt() }
            runCatching { videoReaderThread?.join(300) }
        }
        videoReaderThread = null

        if (Thread.currentThread() !== audioReaderThread) {
            runCatching { audioReaderThread?.interrupt() }
            runCatching { audioReaderThread?.join(300) }
        }
        audioReaderThread = null

        runCatching { session.controlStream?.close() }
        runCatching { session.audioStream?.close() }
        runCatching { session.videoStream?.close() }
        runCatching { session.serverStream.close() }
        if (Thread.currentThread() !== session.serverLogThread) {
            runCatching { session.serverLogThread.interrupt() }
            runCatching { session.serverLogThread.join(300) }
        }
    }

    fun isStarted(): Boolean = activeSession != null

    fun getLastServerCommand(): String? = lastServerCommand

    @Synchronized
    fun listEncoders(serverJarPath: Path, options: ScrcpyStartOptions): EncoderLists {
        val targetPath = options.serverRemotePath.ifBlank { DEFAULT_SERVER_REMOTE_PATH }
        adbService.push(serverJarPath, targetPath)
        val cmd = buildServerCommand(
            targetPath = targetPath,
            scid = 0x1234abcd,
            options = options.copy(
                video = false,
                audio = false,
                control = false,
                listEncoders = true,
                cleanup = false,
            ),
        )
        Log.i(TAG, "listEncoders(): cmd=$cmd")
        // scrcpy 打印在日志中，所以合并 stderr 到 stdout。
        val output = adbService.shell("$cmd 2>&1")
        val parsed = parseEncoderLists(output)
        val preview = output.lineSequence().take(40).joinToString("\n")
        Log.i(
            TAG,
            "listEncoders(): parsed video=${parsed.videoEncoders.size} audio=${parsed.audioEncoders.size}, outputPreview=\n$preview",
        )
        return parsed.copy(rawOutput = output)
    }

    @Synchronized
    fun listCameraSizes(serverJarPath: Path, options: ScrcpyStartOptions): CameraSizeLists {
        val targetPath = options.serverRemotePath.ifBlank { DEFAULT_SERVER_REMOTE_PATH }
        adbService.push(serverJarPath, targetPath)
        val cmd = buildServerCommand(
            targetPath = targetPath,
            scid = 0x2234abcd,
            options = options.copy(
                video = false,
                audio = false,
                control = false,
                listCameraSizes = true,
                cleanup = false,
            ),
        )
        Log.i(TAG, "listCameraSizes(): cmd=$cmd")
        val output = adbService.shell("$cmd 2>&1")
        val parsed = parseCameraSizeLists(output)
        val preview = output.lineSequence().take(40).joinToString("\n")
        Log.i(
            TAG,
            "listCameraSizes(): parsed sizes=${parsed.sizes.size}, outputPreview=\n$preview",
        )
        return parsed.copy(rawOutput = output)
    }

    private fun requireControlWriter(): ScrcpyControlWriter {
        return activeSession?.controlWriter
            ?: throw IllegalStateException("scrcpy control channel not available")
    }

    private fun buildServerCommand(
        targetPath: String,
        scid: Int,
        options: ScrcpyStartOptions
    ): String {
        val serverArgs = buildServerArgs(scid, options)
        return "CLASSPATH=$targetPath app_process / com.genymobile.scrcpy.Server ${options.serverVersion} $serverArgs"
    }

    private fun buildServerArgs(scid: Int, options: ScrcpyStartOptions): String {
        val videoSource = options.videoSource.trim().ifBlank { "display" }
        val isCameraSource = videoSource.equals("camera", ignoreCase = true)
        val cameraId = options.cameraId.trim()
        val hasCameraId = cameraId.isNotBlank()
        val hasExplicitCameraSize = options.cameraSize.trim().isNotBlank()
        val hasValidCameraFps = options.cameraFps > 0

        val args = listOf(
            ServerArg(
                type = ServerArgType.STRING,
                key = "scid",
                value = String.format("%08x", scid),
            ),
            ServerArg(
                type = ServerArgType.STRING,
                key = "log_level",
                value = "debug",
            ),
            ServerArg(
                type = ServerArgType.BOOLEAN,
                key = "tunnel_forward",
                value = true,
            ),
            ServerArg(
                type = ServerArgType.BOOLEAN,
                key = "cleanup",
                value = options.cleanup,
            ),
            ServerArg(
                type = ServerArgType.BOOLEAN,
                key = "send_device_meta",
                value = true,
            ),
            ServerArg(
                type = ServerArgType.BOOLEAN,
                key = "send_frame_meta",
                value = true,
            ),
            ServerArg(
                type = ServerArgType.BOOLEAN,
                key = "send_dummy_byte",
                value = true,
            ),
            ServerArg(
                type = ServerArgType.BOOLEAN,
                key = "send_codec_meta",
                value = true,
            ),
            ServerArg(
                type = ServerArgType.BOOLEAN,
                key = "video",
                value = options.video,
            ),
            ServerArg(
                type = ServerArgType.BOOLEAN,
                key = "audio",
                value = options.audio,
            ),
            ServerArg(
                type = ServerArgType.BOOLEAN,
                key = "control",
                value = options.control,
            ),
            ServerArg(
                type = ServerArgType.STRING,
                key = "video_source",
                value = videoSource,
            ),
            ServerArg(
                type = ServerArgType.NUMBER,
                key = "max_size",
                value = options.maxSize,
            ),
            ServerArg(
                type = ServerArgType.STRING,
                key = "video_codec",
                value = options.videoCodec,
            ),
            ServerArg(
                type = ServerArgType.NUMBER,
                key = "video_bit_rate",
                value = options.videoBitRate,
            ),
            ServerArg(
                type = ServerArgType.NUMBER,
                key = "max_fps",
                value = options.maxFps,
            ),
            ServerArg(
                type = ServerArgType.STRING,
                key = "camera_id",
                value = cameraId,
                includeWhen = isCameraSource,
            ),
            ServerArg(
                type = ServerArgType.STRING,
                key = "camera_facing",
                value = options.cameraFacing.trim(),
                includeWhen = isCameraSource && !hasCameraId,
            ),
            ServerArg(
                type = ServerArgType.STRING,
                key = "camera_size",
                value = options.cameraSize.trim(),
                includeWhen = isCameraSource,
            ),
            ServerArg(
                type = ServerArgType.STRING,
                key = "camera_ar",
                value = options.cameraAr.trim(),
                includeWhen = isCameraSource && !hasExplicitCameraSize,
            ),
            ServerArg(
                type = ServerArgType.NUMBER,
                key = "camera_fps",
                value = options.cameraFps,
                includeWhen = isCameraSource,
            ),
            ServerArg(
                type = ServerArgType.BOOLEAN,
                key = "camera_high_speed",
                value = options.cameraHighSpeed,
                includeWhen = isCameraSource && options.cameraHighSpeed && hasValidCameraFps,
            ),
            ServerArg(
                type = ServerArgType.STRING,
                key = "audio_codec",
                value = options.audioCodec,
                includeWhen = options.audio,
            ),
            ServerArg(
                type = ServerArgType.NUMBER,
                key = "audio_bit_rate",
                value = options.audioBitRate,
                includeWhen = options.audio,
            ),
            ServerArg(
                type = ServerArgType.STRING,
                key = "audio_source",
                value = options.audioSource.trim(),
                includeWhen = options.audio,
            ),
            ServerArg(
                type = ServerArgType.BOOLEAN,
                key = "audio_dup",
                value = options.audioDup,
                includeWhen = options.audioDup,
            ),
            ServerArg(
                type = ServerArgType.STRING,
                key = "video_encoder",
                value = options.videoEncoder.trim(),
            ),
            ServerArg(
                type = ServerArgType.STRING,
                key = "video_codec_options",
                value = options.videoCodecOptions.trim(),
            ),
            ServerArg(
                type = ServerArgType.STRING,
                key = "audio_encoder",
                value = options.audioEncoder.trim(),
            ),
            ServerArg(
                type = ServerArgType.STRING,
                key = "audio_codec_options",
                value = options.audioCodecOptions.trim(),
            ),
            ServerArg(
                type = ServerArgType.STRING,
                key = "new_display",
                value = options.newDisplay.trim(),
            ),
            ServerArg(
                type = ServerArgType.NUMBER,
                key = "display_id",
                value = options.displayId,
            ),
            ServerArg(
                type = ServerArgType.STRING,
                key = "crop",
                value = options.crop.trim(),
            ),
            ServerArg(
                type = ServerArgType.BOOLEAN,
                key = "list_encoders",
                value = options.listEncoders,
                includeWhen = options.listEncoders,
            ),
            ServerArg(
                type = ServerArgType.BOOLEAN,
                key = "list_camera_sizes",
                value = options.listCameraSizes,
                includeWhen = options.listCameraSizes,
            ),
            ServerArg(
                type = ServerArgType.STRING,
                key = "start-app",
                value = options.startApp.trim(),
            ),
            ServerArg(
                type = ServerArgType.STRING,
                key = "window_title",
                value = options.windowTitle.trim(),
            ),
            // Reserved for future args that require repeated/list values.
            // ServerArg(
            //     type = ServerArgType.LIST,
            //     key = "_unused_list",
            //     value = emptyList<String>(),
            //     includeWhen = false,
            // ),
        )

        val rendered = args.mapNotNull { it.render() }
        val header = rendered.firstOrNull()
        val kv = rendered.drop(1)
        return if (header == null) {
            kv.joinToString(" ")
        } else {
            "$header ${kv.joinToString(" ")}".trim()
        }
    }

    private enum class ServerArgType {
        NUMBER,
        STRING,
        BOOLEAN,
        LIST,
    }

    private enum class ZeroValueBehavior {
        OMIT,
        KEEP,
    }

    private data class ServerArg<T>(
        val type: ServerArgType,
        val key: String,
        val value: T?,
        val includeWhen: Boolean = true,
        val zeroValueBehavior: ZeroValueBehavior = ZeroValueBehavior.OMIT,
    ) {
        fun render(): String? {
            if (!includeWhen) return null
            return when (type) {
                ServerArgType.STRING -> renderString(value as? String)
                ServerArgType.BOOLEAN -> renderBoolean(value as? Boolean)
                ServerArgType.NUMBER -> renderNumber(value as? Number)
                ServerArgType.LIST -> renderList(value as? Iterable<*>)
            }
        }

        private fun renderString(raw: String?): String? {
            val normalized = raw?.trim().orEmpty()
            if (normalized.isBlank()) return null
            return "$key=$normalized"
        }

        private fun renderBoolean(raw: Boolean?): String? {
            val normalized = raw ?: return null
            return "$key=$normalized"
        }

        private fun renderNumber(raw: Number?): String? {
            val normalized = raw ?: return null
            val asDouble = normalized.toDouble()
            if (asDouble == 0.0 && zeroValueBehavior == ZeroValueBehavior.OMIT) return null
            val valueString = when (normalized) {
                is Float -> normalized.toString()
                is Double -> normalized.toString()
                else -> normalized.toLong().toString()
            }
            return "$key=$valueString"
        }

        private fun renderList(raw: Iterable<*>?): String? {
            val items = raw
                ?.mapNotNull { it?.toString()?.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            if (items.isEmpty()) return null
            return "$key=${items.joinToString(",")}"
        }
    }

    private fun parseEncoderLists(output: String): EncoderLists {
        val video = LinkedHashSet<String>()
        val audio = LinkedHashSet<String>()
        val videoTypes = linkedMapOf<String, String>()
        val audioTypes = linkedMapOf<String, String>()
        VIDEO_ENCODER_REGEX.findAll(output).forEach { match ->
            video.add(match.groupValues[1])
        }
        AUDIO_ENCODER_REGEX.findAll(output).forEach { match ->
            audio.add(match.groupValues[1])
        }
        // 回退日志格式，包含编码器名称。
        VIDEO_ENCODER_FALLBACK_REGEX.findAll(output).forEach { match ->
            video.add(match.groupValues[1])
        }
        AUDIO_ENCODER_FALLBACK_REGEX.findAll(output).forEach { match ->
            audio.add(match.groupValues[1])
        }
        VIDEO_ENCODER_TYPE_REGEX.findAll(output).forEach { match ->
            val name = match.groupValues[1]
            val type = match.groupValues[2]
            if (name.isNotBlank() && type.isNotBlank() && !videoTypes.containsKey(name)) {
                videoTypes[name] = type
            }
        }
        AUDIO_ENCODER_TYPE_REGEX.findAll(output).forEach { match ->
            val name = match.groupValues[1]
            val type = match.groupValues[2]
            if (name.isNotBlank() && type.isNotBlank() && !audioTypes.containsKey(name)) {
                audioTypes[name] = type
            }
        }
        return EncoderLists(
            videoEncoders = video.toList(),
            audioEncoders = audio.toList(),
            videoEncoderTypes = videoTypes,
            audioEncoderTypes = audioTypes,
        )
    }

    private fun parseCameraSizeLists(output: String): CameraSizeLists {
        val sizes = LinkedHashSet<String>()
        CAMERA_SIZE_REGEX.findAll(output).forEach { match ->
            sizes.add(match.groupValues[1])
        }
        CAMERA_SIZE_FALLBACK_REGEX.findAll(output).forEach { match ->
            sizes.add(match.groupValues[1])
        }
        return CameraSizeLists(sizes = sizes.toList())
    }

    private fun startServerLogThread(serverStream: AdbSocketStream, socketName: String): Thread {
        return thread(start = true, name = "scrcpy-server-log") {
            try {
                BufferedReader(
                    InputStreamReader(
                        serverStream.inputStream,
                        Charsets.UTF_8
                    )
                ).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        appendServerLog(line)
                        Log.i(TAG, "[server:$socketName] $line")
                    }
                }
            } catch (e: Exception) {
                if (activeSession != null) {
                    Log.w(TAG, "server log thread failed", e)
                }
            }
        }
    }

    @Synchronized
    private fun appendServerLog(line: String) {
        if (serverLogBuffer.size >= SERVER_LOG_BUFFER_MAX_LINES) {
            serverLogBuffer.removeFirst()
        }
        serverLogBuffer.addLast(line)
    }

    @Synchronized
    private fun snapshotServerLogs(maxLines: Int = 120): String {
        if (serverLogBuffer.isEmpty()) {
            return ""
        }
        val take = maxLines.coerceIn(1, SERVER_LOG_BUFFER_MAX_LINES)
        return serverLogBuffer.toList().takeLast(take).joinToString("\n")
    }

    /**
     * 尝试打开抽象 adb 套接字。
     *
     * - 尝试打开套接字，如果失败则等待短时间后重试。
     * - 可选地期望流中的虚拟字节，以验证服务器握手。
     */
    private fun openAbstractSocketWithRetry(
        socketName: String,
        expectDummyByte: Boolean
    ): AdbSocketStream {
        var lastEx: Exception? = null
        repeat(CONNECT_RETRY_COUNT) { attempt ->
            try {
                val stream = adbService.openAbstractSocket(socketName)
                if (expectDummyByte) {
                    val value = stream.inputStream.read()
                    if (value < 0) {
                        stream.close()
                        throw EOFException("scrcpy dummy byte missing")
                    }
                }
                return stream
            } catch (e: Exception) {
                lastEx = e
                if (attempt < CONNECT_RETRY_COUNT - 1) Thread.sleep(CONNECT_RETRY_DELAY_MS)
            }
        }
        throw IllegalStateException("Unable to open scrcpy socket '$socketName'", lastEx)
    }

    private fun readDeviceName(input: DataInputStream): String {
        val buffer = ByteArray(DEVICE_NAME_FIELD_LENGTH)
        input.readFully(buffer)
        val firstZero = buffer.indexOf(0)
        val length = if (firstZero >= 0) firstZero else buffer.size
        return buffer.copyOf(length).toString(Charsets.UTF_8)
    }

    private fun codecName(codecId: Int): String {
        return when (codecId) {
            VIDEO_CODEC_H264 -> "h264"
            VIDEO_CODEC_H265 -> "h265"
            VIDEO_CODEC_AV1 -> "av1"
            else -> "unknown"
        }
    }

    private fun audioCodecIdFromName(name: String): Int {
        return when (name.lowercase()) {
            "opus" -> AUDIO_CODEC_OPUS
            "aac" -> AUDIO_CODEC_AAC
            "raw" -> AUDIO_CODEC_RAW
            "flac" -> AUDIO_CODEC_FLAC
            else -> 0
        }
    }

    data class SessionInfo(
        val deviceName: String,
        val codecId: Int,
        val codecName: String,
        val width: Int,
        val height: Int,
        val audioCodecId: Int = 0,
        val controlEnabled: Boolean,
    )

    data class VideoPacket(
        val data: ByteArray,
        val ptsUs: Long,
        val isConfig: Boolean,
        val isKeyFrame: Boolean,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as VideoPacket

            if (ptsUs != other.ptsUs) return false
            if (isConfig != other.isConfig) return false
            if (isKeyFrame != other.isKeyFrame) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = ptsUs.hashCode()
            result = 31 * result + isConfig.hashCode()
            result = 31 * result + isKeyFrame.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    data class AudioPacket(
        val data: ByteArray,
        val ptsUs: Long,
        val isConfig: Boolean,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as AudioPacket

            if (ptsUs != other.ptsUs) return false
            if (isConfig != other.isConfig) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = ptsUs.hashCode()
            result = 31 * result + isConfig.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    data class ScrcpyStartOptions(
        val serverVersion: String = "3.3.4",
        val serverRemotePath: String = DEFAULT_SERVER_REMOTE_PATH,
        val video: Boolean = true,
        val audio: Boolean = true,
        val control: Boolean = true,
        val cleanup: Boolean = true,
        val maxSize: Int = 0,
        val maxFps: Float = 0f,
        val videoBitRate: Int = 8_000_000,
        val videoCodec: String = "h264",
        val audioBitRate: Int = 128_000,
        val audioCodec: String = "opus",
        val videoEncoder: String = "",
        val videoCodecOptions: String = "",
        val audioEncoder: String = "",
        val audioCodecOptions: String = "",
        val audioDup: Boolean = false,
        val audioSource: String = "",
        val videoSource: String = "display",
        val cameraId: String = "",
        val cameraFacing: String = "",
        val cameraSize: String = "",
        val cameraAr: String = "",
        val cameraFps: Int = 0,
        val cameraHighSpeed: Boolean = false,
        val newDisplay: String = "",
        val displayId: Int? = null,
        val crop: String = "",
        val listEncoders: Boolean = false,
        val listCameraSizes: Boolean = false,
        val startApp: String = "",
        val windowTitle: String = "",
    )

    data class EncoderLists(
        val videoEncoders: List<String>,
        val audioEncoders: List<String>,
        val videoEncoderTypes: Map<String, String> = emptyMap(),
        val audioEncoderTypes: Map<String, String> = emptyMap(),
        val rawOutput: String = "",
    )

    data class CameraSizeLists(
        val sizes: List<String>,
        val rawOutput: String = "",
    )

    private data class ActiveSession(
        val info: SessionInfo,
        val socketName: String,
        val serverStream: AdbSocketStream,
        val serverLogThread: Thread,
        val videoStream: AdbSocketStream?,
        val videoInput: DataInputStream?,
        val audioStream: AdbSocketStream?,
        val audioInput: DataInputStream?,
        val controlStream: AdbSocketStream?,
        val controlWriter: ScrcpyControlWriter?,
    )

    private class ScrcpyControlWriter(private val output: DataOutputStream) {
        @Synchronized
        fun injectKeycode(action: Int, keycode: Int, repeat: Int, metaState: Int) {
            output.writeByte(TYPE_INJECT_KEYCODE)
            output.writeByte(action)
            output.writeInt(keycode)
            output.writeInt(repeat)
            output.writeInt(metaState)
            output.flush()
        }

        @Synchronized
        fun injectText(text: String) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            output.writeByte(TYPE_INJECT_TEXT)
            output.writeInt(bytes.size)
            output.write(bytes)
            output.flush()
        }

        @Synchronized
        fun injectTouch(
            action: Int,
            pointerId: Long,
            x: Int,
            y: Int,
            screenWidth: Int,
            screenHeight: Int,
            pressure: Float,
            actionButton: Int,
            buttons: Int,
        ) {
            output.writeByte(TYPE_INJECT_TOUCH_EVENT)
            output.writeByte(action)
            output.writeLong(pointerId)
            writePosition(x, y, screenWidth, screenHeight)
            output.writeShort(encodeUnsignedFixedPoint16(pressure))
            output.writeInt(actionButton)
            output.writeInt(buttons)
            output.flush()
        }

        @Synchronized
        fun injectScroll(
            x: Int,
            y: Int,
            screenWidth: Int,
            screenHeight: Int,
            hScroll: Float,
            vScroll: Float,
            buttons: Int
        ) {
            output.writeByte(TYPE_INJECT_SCROLL_EVENT)
            writePosition(x, y, screenWidth, screenHeight)
            output.writeShort(encodeSignedFixedPoint16(hScroll / 16f))
            output.writeShort(encodeSignedFixedPoint16(vScroll / 16f))
            output.writeInt(buttons)
            output.flush()
        }

        @Synchronized
        fun pressBackOrScreenOn(action: Int) {
            output.writeByte(TYPE_BACK_OR_SCREEN_ON)
            output.writeByte(action)
            output.flush()
        }

        @Synchronized
        fun setDisplayPower(on: Boolean) {
            output.writeByte(TYPE_SET_DISPLAY_POWER)
            output.writeBoolean(on)
            output.flush()
        }

        private fun writePosition(x: Int, y: Int, screenWidth: Int, screenHeight: Int) {
            output.writeInt(x)
            output.writeInt(y)
            output.writeShort(screenWidth)
            output.writeShort(screenHeight)
        }

        private fun encodeUnsignedFixedPoint16(value: Float): Int {
            val clamped = value.coerceIn(0f, 1f)
            return if (clamped >= 1f) {
                0xffff
            } else {
                (clamped * 65536f).roundToInt().coerceIn(0, 0xfffe)
            }
        }

        private fun encodeSignedFixedPoint16(value: Float): Int {
            val clamped = value.coerceIn(-1f, 1f)
            if (clamped >= 1f) {
                return 0x7fff
            }
            if (clamped <= -1f) {
                return -0x8000
            }
            return (clamped * 32768f).roundToInt().coerceIn(-0x8000, 0x7ffe)
        }
    }

    companion object {
        const val DEFAULT_SERVER_REMOTE_PATH = "/data/local/tmp/scrcpy-server.jar"
        private const val TAG = "ScrcpySessionManager"
        private const val SERVER_BOOT_DELAY_MS = 200L
        private const val SERVER_LOG_BUFFER_MAX_LINES = 400

        private const val CONNECT_RETRY_COUNT = 100
        private const val CONNECT_RETRY_DELAY_MS = 100L
        private const val DEVICE_NAME_FIELD_LENGTH = 64
        private const val PACKET_FLAG_CONFIG = 1L shl 63
        private const val PACKET_FLAG_KEY_FRAME = 1L shl 62
        private const val PACKET_PTS_MASK = (1L shl 62) - 1
        private const val VIDEO_CODEC_H264 = 0x68323634
        private const val VIDEO_CODEC_H265 = 0x68323635
        private const val VIDEO_CODEC_AV1 = 0x00617631
        private const val AUDIO_CODEC_OPUS = 0x6f707573
        private const val AUDIO_CODEC_AAC = 0x00616163
        private const val AUDIO_CODEC_FLAC = 0x666c6163
        private const val AUDIO_CODEC_RAW = 0x00726177

        // Audio stream disable codes from server (writeDisableStream)
        private const val AUDIO_DISABLED = 0
        private const val AUDIO_ERROR = 1
        private const val TYPE_INJECT_KEYCODE = 0
        private const val TYPE_INJECT_TEXT = 1
        private const val TYPE_INJECT_TOUCH_EVENT = 2
        private const val TYPE_INJECT_SCROLL_EVENT = 3
        private const val TYPE_BACK_OR_SCREEN_ON = 4
        private const val TYPE_SET_DISPLAY_POWER = 10
        private val VIDEO_ENCODER_REGEX = Regex("--video-encoder=([\\w.\\-]+)")
        private val AUDIO_ENCODER_REGEX = Regex("--audio-encoder=([\\w.\\-]+)")
        private val VIDEO_ENCODER_FALLBACK_REGEX = Regex("""--video-encoder=['"]?([^'"\s]+)""")
        private val AUDIO_ENCODER_FALLBACK_REGEX = Regex("""--audio-encoder=['"]?([^'"\s]+)""")
        private val VIDEO_ENCODER_TYPE_REGEX =
            Regex("""--video-codec=\S+\s+--video-encoder=(\S+).*?\((hw|sw)\)""")
        private val AUDIO_ENCODER_TYPE_REGEX =
            Regex("""--audio-codec=\S+\s+--audio-encoder=(\S+).*?\((hw|sw)\)""")
        private val CAMERA_SIZE_REGEX = Regex("--camera-size=([0-9]+x[0-9]+)")
        private val CAMERA_SIZE_FALLBACK_REGEX = Regex("\\b([1-9][0-9]{1,4}x[1-9][0-9]{1,4})\\b")

        private val random = SecureRandom()

        private fun socketNameFor(scid: Int): String {
            return "scrcpy_%08x".format(scid)
        }
    }
}
