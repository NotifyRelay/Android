package wtf.magma.audio

/**
 * SMTC Rust JNI 桥的 Java 侧声明。
 *
 * **包名、类名与方法名不可改动**：Rust 侧（`desktop/smtc-rust/src/jni.rs`）导出的符号名是
 * `Java_wtf_magma_audio_SMTCLoader_*`，改名会导致 `UnsatisfiedLinkError`。
 *
 * 方法必须编译为静态方法（`@JvmStatic`）：Rust 侧接收的是 `JClass`，
 * 对应 Java 的 `static native`；若编译成实例方法，JNI 会去找 `Java_..._getSmtcInfo__J`
 * 之类的重载签名而失败。
 *
 * 库为 `smtc_jni`（`cargo build --release` 产物），由 `smtcRustBuild` 任务构建，
 * 其目录已加入 `jna.library.path` / `java.library.path`。
 */
object SMTCLoader {
    private const val TAG = "SMTCLoader"

    /** 库文件名。 */
    private const val LIBRARY_NAME = "smtc_jni"

    /**
     * 库是否加载成功；失败时各方法返回空值，不抛异常。
     *
     * `System.loadLibrary` 的搜索路径在 JVM 首次加载原生库时即被缓存，
     * 应用镜像里运行期补的 `java.library.path` 可能已不生效，
     * 因此在按名加载失败后按绝对路径逐个候选目录尝试。
     */
    val available: Boolean = load()

    /** 按「库名 → 候选目录绝对路径」的顺序尝试加载，失败时记录失败原因。 */
    private fun load(): Boolean {
        val byName = runCatching { System.loadLibrary(LIBRARY_NAME) }
        if (byName.isSuccess) return true
        val fileName = System.mapLibraryName(LIBRARY_NAME)
        val tried = mutableListOf<String>()
        for (dir in candidateDirs()) {
            val file = java.io.File(dir, fileName)
            if (!file.isFile) continue
            if (runCatching { System.load(file.absolutePath) }.isSuccess) {
                notifyrelay.base.log.Logger
                    .i(TAG, "原生库按路径加载成功: " + file.absolutePath)
                return true
            }
            tried.add(file.absolutePath)
        }
        notifyrelay.base.log.Logger.w(
            TAG,
            "原生库加载失败: " + byName.exceptionOrNull()?.message +
                " 候选=" + tried.joinToString(","),
        )
        return false
    }

    /** 收集原生库候选目录：资源目录、两个库搜索路径与当前目录。 */
    private fun candidateDirs(): List<String> {
        val dirs = linkedSetOf<String>()
        System
            .getProperty("compose.application.resources.dir")
            ?.takeIf { it.isNotBlank() }
            ?.let { dirs.add(it) }
        for (key in listOf("java.library.path", "jna.library.path")) {
            System
                .getProperty(key)
                ?.split(java.io.File.pathSeparator)
                ?.filter { it.isNotBlank() }
                ?.let { dirs.addAll(it) }
        }
        dirs.add(System.getProperty("user.dir").orEmpty())
        return dirs.toList()
    }

    /**
     * 当前会话的媒体信息。
     *
     * @return `"title|artist|pos_ms|dur_ms"`；无会话时为 `"No media"`
     */
    @JvmStatic
    external fun getSmtcInfo(): String

    /**
     * 当前播放状态。
     *
     * @return Windows `MediaPlaybackStatus`：0 Closed / 1 Opened / 2 Changing / 3 Playing / 4 Paused / 5 Stopped
     */
    @JvmStatic
    external fun getPlaybackStatus(): Int

    /** 当前会话所属应用的 User Model ID；无会话时为空串。 */
    @JvmStatic
    external fun getSourceAppId(): String

    /**
     * 当前会话封面的 base64（原始图片字节，通常 JPEG/PNG）；无封面时为空串。
     */
    @JvmStatic
    external fun getCoverBase64(): String

    /**
     * 启动系统声音采集（WASAPI 回环）。
     *
     * 采集线程回调 [listener] 的 `onAudioPcm(ByteArray)`，参数为 16 位小端交错 PCM。
     * Rust 侧以 `onAudioPcm` 名称反射调用，不可改名。
     *
     * @param listener 接收 PCM 的对象
     * @return 0 成功；负值失败
     */
    @JvmStatic
    external fun startAudioCapture(listener: AudioCaptureListener): Int

    /** 停止系统声音采集。 */
    @JvmStatic
    external fun stopAudioCapture(): Int

    /** 采集是否运行中。 */
    @JvmStatic
    external fun isAudioCapturing(): Int

    /** 采集流格式：`[采样率, 声道数]`；未运行时为 `[0, 0]`。 */
    @JvmStatic
    external fun audioCaptureFormat(): IntArray

    /**
     * 读取当前全部本机通知。
     *
     * @return 每条 `id\u0001appName\u0001appPackage\u0001title\u0001text`，多条以 `\u0002` 分隔；
     * 失败时以 `!` 开头返回错误说明
     */
    @JvmStatic
    external fun listNotifications(): String
}

/** 系统声音采集的数据接收者。 */
interface AudioCaptureListener {
    /** 收到一帧 PCM（16 位小端交错）。 */
    fun onAudioPcm(pcm: ByteArray)
}
