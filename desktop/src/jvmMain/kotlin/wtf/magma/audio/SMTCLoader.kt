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
    /** 库是否加载成功；失败时各方法返回空值，不抛异常。 */
    val available: Boolean =
        runCatching {
            System.loadLibrary("smtc_jni")
            true
        }.getOrDefault(false)

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
}
