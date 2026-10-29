package notifyrelay.desktop.platform

import com.xzyht.notifyrelay.nativecore.NativeCore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import notifyrelay.base.log.Logger
import notifyrelay.data.Storage
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.device.service.contract.DeviceMessagePort
import notifyrelay.shared.ui.port.ClipboardSyncPort
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

/**
 * 桌面端剪贴板同步。
 *
 * 与 Windows `ClipboardService` 同口径：
 * - 监听系统剪贴板的拥有者变化（AWT 无内容变更事件，采用拥有者轮询）；
 * - 采集文本（CRLF 归一为 LF）或图片（PNG → Base64）；
 * - 去重、防循环、频率限制与 2MB 阈值全部交给 Rust core（`clipboardOnChanged`）；
 * - 收到远端报文经 `clipboardOnReceived` 解析后写回系统剪贴板。
 *
 * 与 Android 的 Fcitx 方案不同：桌面直接读写系统剪贴板，不需要输入法配对。
 */
class DesktopClipboardSyncPort(
    private val deviceMessagePort: () -> DeviceMessagePort?,
    private val onlineDevices: () -> List<DeviceInfo>,
) : ClipboardSyncPort {
    private companion object {
        const val TAG = "DesktopClipboard"
        const val KEY_ENABLED = "clipboard_sync_enabled"

        /** 剪贴板轮询间隔（毫秒）。 */
        const val POLL_INTERVAL_MS = 800L

        /** 文本 MIME。 */
        const val MIME_TEXT = "text/plain"

        /** 图片 MIME。 */
        const val MIME_PNG = "image/png"
    }

    private val enabledState = MutableStateFlow(Storage.getBoolean(KEY_ENABLED, true))
    override val enabled: StateFlow<Boolean> = enabledState.asStateFlow()

    private val watchingState = MutableStateFlow(false)
    override val watching: StateFlow<Boolean> = watchingState.asStateFlow()

    private val lastResultState = MutableStateFlow<String?>(null)
    override val lastResult: StateFlow<String?> = lastResultState.asStateFlow()

    private var poller: Thread? = null

    @Volatile
    private var running = false

    /** 上次采集到的内容签名，用于判断是否需要交给 Rust 判定。 */
    @Volatile
    private var lastSignature: Int = 0

    /** 启动轮询；系统剪贴板不可用时保持未监听状态。 */
    fun start() {
        if (running) return
        val clipboard = runCatching { Toolkit.getDefaultToolkit().systemClipboard }.getOrNull()
        if (clipboard == null) {
            Logger.w(TAG, "系统剪贴板不可用，剪贴板同步未启动")
            return
        }
        running = true
        watchingState.value = true
        poller =
            Thread({
                while (running) {
                    try {
                        Thread.sleep(POLL_INTERVAL_MS)
                        pollOnce(clipboard)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return@Thread
                    } catch (e: IllegalStateException) {
                        Logger.w(TAG, "剪贴板轮询异常", e)
                    }
                }
            }, "desktop-clipboard-watch").apply {
                isDaemon = true
                start()
            }
        Logger.i(TAG, "剪贴板监视已启动")
    }

    /** 停止轮询。 */
    fun stop() {
        running = false
        poller?.interrupt()
        poller = null
        watchingState.value = false
    }

    override fun setEnabled(enabled: Boolean) {
        enabledState.value = enabled
        Storage.putBoolean(KEY_ENABLED, enabled)
    }

    override fun syncNow() {
        if (!enabledState.value) {
            lastResultState.value = "同步已关闭"
            return
        }
        runCatching { Toolkit.getDefaultToolkit().systemClipboard }
            .onSuccess { pollOnce(it, force = true) }
            .onFailure { lastResultState.value = "系统剪贴板不可用" }
    }

    /** 处理一条入站剪贴板报文，写回系统剪贴板。 */
    fun onReceived(payload: String) {
        if (!enabledState.value) return
        val resultJson =
            runCatching { NativeCore.clipboardOnReceived(payload, System.currentTimeMillis()) }
                .getOrNull()
        if (resultJson.isNullOrBlank()) return

        val content = parseField(resultJson, "content") ?: return
        val type = parseField(resultJson, "type") ?: "text"
        if (content.isEmpty()) return

        runCatching {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val transferable =
                if (type == "image") {
                    val bytes = Base64.getDecoder().decode(content)
                    val image = ImageIO.read(bytes.inputStream()) ?: return@runCatching
                    ImageSelection(image)
                } else {
                    java.awt.datatransfer.StringSelection(content)
                }
            clipboard.setContents(transferable, null)
            // 同步本地内容签名：回写触发的下一次轮询不应再被当作本机变更
            lastSignature = content.hashCode()
        }.onFailure { Logger.w(TAG, "写入系统剪贴板失败", it) }
    }

    /** 读取一次剪贴板并按需发送。 */
    private fun pollOnce(
        clipboard: Clipboard,
        force: Boolean = false,
    ) {
        if (!enabledState.value) return

        val captured = capture(clipboard) ?: return

        // 内容签名未变则跳过：避免每次轮询都把同一内容交给 Rust
        val signature = captured.first.hashCode()
        if (!force && signature == lastSignature) return
        lastSignature = signature

        val targets = onlineDevices().filter { it.uuid.isNotBlank() }
        if (targets.isEmpty()) return
        if (NativeCore.isReady().not()) return

        val targetsJson =
            targets.joinToString(prefix = "[", postfix = "]") { """ + it.uuid + """ }
        val result =
            runCatching {
                NativeCore.clipboardOnChanged(
                    targetsJson,
                    captured.second,
                    captured.first,
                    System.currentTimeMillis(),
                    force,
                )
            }.getOrNull()

        val action = result?.let { parseField(it, "action") }
        val reason = result?.let { parseField(it, "reason") }
        lastResultState.value =
            when (action) {
                "sent" -> "已发送到 " + targets.size + " 台设备"
                "file_transfer" -> "内容超过 2MB，转文件传输"
                "skipped" -> "已跳过：" + (reason ?: "无变化")
                else -> null
            }
        if (action == "sent") Logger.i(TAG, "剪贴板已发送，目标 " + targets.size + " 台")
    }

    /** 采集剪贴板内容：优先文本，其次图片；返回（内容, MIME）。 */
    private fun capture(clipboard: Clipboard): Pair<String, String>? {
        val transferable: Transferable =
            runCatching { clipboard.getContents(null) }.getOrNull() ?: return null

        if (runCatching { transferable.isDataFlavorSupported(DataFlavor.stringFlavor) }.getOrDefault(false)) {
            val raw =
                runCatching { transferable.getTransferData(DataFlavor.stringFlavor) as? String }
                    .getOrNull()
            if (!raw.isNullOrEmpty()) {
                // Windows 换行为 CRLF，统一为 LF（与 Windows 端一致）
                return raw.replace("\r\n", "\n") to MIME_TEXT
            }
        }

        if (runCatching { transferable.isDataFlavorSupported(DataFlavor.imageFlavor) }.getOrDefault(false)) {
            val image =
                runCatching { transferable.getTransferData(DataFlavor.imageFlavor) as? Image }
                    .getOrNull() ?: return null
            val buffered =
                if (image is BufferedImage) {
                    image
                } else {
                    BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB).also {
                        it.createGraphics().drawImage(image, 0, 0, null)
                    }
                }
            val output = ByteArrayOutputStream()
            if (!runCatching { ImageIO.write(buffered, "png", output) }.getOrDefault(false)) return null
            return Base64.getEncoder().encodeToString(output.toByteArray()) to MIME_PNG
        }

        return null
    }

    /** 从 JSON 字符串中取字段值（顶层字符串字段）。 */
    private fun parseField(
        json: String,
        field: String,
    ): String? =
        runCatching {
            val marker = "\"" + field + "\":"
            val start = json.indexOf(marker)
            if (start < 0) return null
            val from = json.indexOf('"', start + marker.length)
            if (from < 0) return null
            val to = json.indexOf('"', from + 1)
            if (to < 0) return null
            json.substring(from + 1, to)
        }.getOrNull()

    /** 把 AWT 图像包装成可放入剪贴板的 [Transferable]。 */
    private class ImageSelection(
        private val image: Image,
    ) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = DataFlavor.imageFlavor == flavor

        override fun getTransferData(flavor: DataFlavor): Any = image
    }
}
