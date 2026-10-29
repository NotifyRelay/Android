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
 * - 监听系统剪贴板变更事件（`AddClipboardFormatListener`，见 [ClipboardChangeMonitor]）；
 * - 采集文本（原样，不做换行归一）或图片（PNG → Base64）；
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

    @Volatile
    private var running = false

    /** 上次采集到的内容签名，用于判断是否需要交给 Rust 判定。 */
    @Volatile
    private var lastSignature: Int = 0

    /**
     * 启动剪贴板监听。
     *
     * 注册系统剪贴板变更事件（与 [PowerStatusMonitor] 同为 JNA 调 Win32 的方式）。
     * 非 Windows 或窗口句柄无效时监听不可用，本方法返回后 [watching] 保持 false。
     *
     * @param hwnd 主窗口句柄（`AddClipboardFormatListener` 需要窗口接收消息）
     */
    fun start(hwnd: Long = 0L) {
        if (running) return
        val clipboard = runCatching { Toolkit.getDefaultToolkit().systemClipboard }.getOrNull()
        if (clipboard == null) {
            Logger.w(TAG, "系统剪贴板不可用，剪贴板同步未启动")
            return
        }
        running = true
        watchingState.value = true

        val eventDriven =
            ClipboardChangeMonitor.start(hwnd) {
                // 窗口消息线程：只做采集与入队，Rust 负责去重与防循环
                runCatching { handleClipboardChange(clipboard) }
                    .onFailure { Logger.w(TAG, "剪贴板事件处理失败", it) }
            }

        if (!eventDriven) {
            running = false
            watchingState.value = false
            Logger.w(TAG, "剪贴板事件监听不可用（需 Windows 且窗口句柄有效）")
            return
        }
        Logger.i(TAG, "剪贴板监视已启动（事件驱动）")
    }

    /** 停止监听。 */
    fun stop() {
        running = false
        ClipboardChangeMonitor.stop()
        watchingState.value = false
    }

    override fun setEnabled(enabled: Boolean) {
        enabledState.value = enabled
        Storage.putBoolean(KEY_ENABLED, enabled)
    }

    override fun syncNow() {
        if (!enabledState.value) {
            lastResultState.value = "同步已关闭"
            DesktopToastHost.show("剪贴板同步已关闭", DesktopToastHost.Duration.SHORT)
            return
        }
        runCatching { Toolkit.getDefaultToolkit().systemClipboard }
            .onSuccess {
                handleClipboardChange(it, force = true, notify = true)
            }.onFailure {
                lastResultState.value = "系统剪贴板不可用"
                DesktopToastHost.show("系统剪贴板不可用", DesktopToastHost.Duration.LONG)
            }
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
            // 同步本地内容签名：回写触发的下一次变更事件不应再被当作本机内容发出
            lastSignature = content.hashCode()
        }.onFailure { Logger.w(TAG, "写入系统剪贴板失败", it) }
    }

    /** 处理一次剪贴板变化：采集内容并交给 Rust 判定与发送。 */
    private fun handleClipboardChange(
        clipboard: Clipboard,
        force: Boolean = false,
        notify: Boolean = false,
    ) {
        if (!enabledState.value) return

        val captured = capture(clipboard) ?: return

        // 内容签名未变则跳过：避免同一内容被重复交给 Rust 判定
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
        // 自动同步不打扰用户，仅手动触发时给出提示
        if (notify) {
            val message = lastResultState.value
            if (!message.isNullOrBlank()) {
                DesktopToastHost.show(message, DesktopToastHost.Duration.SHORT)
            }
        }
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
                // 内容原样传递：Rust core 与 Android 均不做换行归一，
                // 在此改写会与另外两端产生内容差异（接收端看到被改写的文本）
                return raw to MIME_TEXT
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
