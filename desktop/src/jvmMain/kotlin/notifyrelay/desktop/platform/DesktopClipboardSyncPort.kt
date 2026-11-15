package notifyrelay.desktop.platform

import com.xzyht.notifyrelay.nativecore.NativeCore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import notifyrelay.base.log.Logger
import notifyrelay.data.Storage
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.shared.ui.port.ClipboardSyncPort
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
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
 * - 文本经 [Win32Clipboard] 直写，绕开 AWT 写 `CF_UNICODETEXT` 时把换行换成 `CRLF` 的行为；
 * - 采集文本时归一换行为 `LF`（`CRLF` → `LF`，其余字符不变），图片走 AWT（PNG → Base64）；
 * - 去重、防循环、频率限制与 2MB 阈值全部交给 Rust core（`clipboardOnChanged`）；
 * - 收到远端报文经 `clipboardOnReceived` 解析后写回系统剪贴板。
 *
 * 平台侧不持有任何同步状态：本机复制与远端回灌的区分、重复内容是否跳过，全部由 Rust core 判定。
 * 平台对 Rust 的义务是口径一致——写回前把换行归一到 `LF`，交给 `clipboardOnChanged` 的内容必须是
 * 回读时的那一个字符串（含按 JSON 语法解码，见 [decodeResultField]）；口径不一致时 Rust 的
 * 内容相等前提失效，回灌会被当成新的本机复制重新发出。
 *
 * 与 Android 的 Fcitx 方案不同：桌面直接读写系统剪贴板，不需要输入法配对。
 */
class DesktopClipboardSyncPort(
    private val onlineDevices: () -> List<DeviceInfo>,
) : ClipboardSyncPort {
    private companion object {
        const val TAG = "DesktopClipboard"
        const val KEY_ENABLED = "clipboard_sync_enabled"

        /** 文本 MIME。 */
        const val MIME_TEXT = "text/plain"

        /** 图片 MIME。 */
        const val MIME_PNG = "image/png"

        /** Rust 归一后的类型：图片。 */
        const val CLIPBOARD_TYPE_IMAGE = "image"
    }

    private val enabledState = MutableStateFlow(Storage.getBoolean(KEY_ENABLED, true))
    override val enabled: StateFlow<Boolean> = enabledState.asStateFlow()

    private val watchingState = MutableStateFlow(false)
    override val watching: StateFlow<Boolean> = watchingState.asStateFlow()

    private val lastResultState = MutableStateFlow<String?>(null)
    override val lastResult: StateFlow<String?> = lastResultState.asStateFlow()

    @Volatile
    private var running = false

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

    /** 处理一条入站剪贴板报文，写回系统剪贴板。回灌由 Rust 在判定时按内容排除。 */
    fun onReceived(payload: String) {
        if (!enabledState.value) return
        val resultJson =
            runCatching { NativeCore.clipboardOnReceived(payload, System.currentTimeMillis()) }
                .getOrNull()
        if (resultJson.isNullOrBlank()) return

        val content = decodeResultField(resultJson, "content")
        if (content.isNullOrEmpty()) {
            Logger.w(TAG, "剪贴板回包无可用内容，放弃写入剪贴板")
            return
        }
        val type = decodeResultField(resultJson, "type") ?: "text"

        try {
            if (type == CLIPBOARD_TYPE_IMAGE) {
                val bytes = Base64.getDecoder().decode(content)
                val image = ImageIO.read(bytes.inputStream()) ?: return
                Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageSelection(image), null)
            } else {
                // 归一换行后再写：与本机采集口径一致，Rust 的内容相等判定才能成立
                val text = Win32Clipboard.normalizeNewlines(content)
                if (Win32Clipboard.isAvailable()) {
                    // 直写原生格式：AWT 写路径会把换行改写为 CRLF，本机剪贴板将与本机内容不一致
                    if (!Win32Clipboard.writeText(text)) {
                        Logger.w(TAG, "Win32 剪贴板写入失败")
                        return
                    }
                } else {
                    Toolkit
                        .getDefaultToolkit()
                        .systemClipboard
                        .setContents(StringSelection(text), null)
                }
            }
            Logger.d(TAG, "写回系统剪贴板：类型=" + type + " " + describe(content))
        } catch (e: Exception) {
            Logger.w(TAG, "写入系统剪贴板失败", e)
        }
    }

    /** 处理一次剪贴板变化：采集内容并交给 Rust 判定与发送。 */
    private fun handleClipboardChange(
        clipboard: Clipboard,
        force: Boolean = false,
        notify: Boolean = false,
    ) {
        if (!enabledState.value) return

        val captured = capture(clipboard) ?: return
        Logger.d(TAG, "剪贴板事件：" + describe(captured.first))

        val targets = onlineDevices().filter { it.uuid.isNotBlank() }
        if (targets.isEmpty()) return
        if (NativeCore.isReady().not()) return

        val targetsJson = buildTargetsJson(targets.map { it.uuid })
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

        val action = result?.let { decodeResultField(it, "action") }
        val reason = result?.let { decodeResultField(it, "reason") }
        // reason 是区分"本机复制"与"远端回灌"的唯一依据：回灌会得到"内容来自远程，跳过发送"
        Logger.d(TAG, "Rust 判定：action=" + action + " reason=" + reason)
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
                // 换行归一为 LF（与 Windows 端 ClipboardService 同口径）：
                // AWT 读原生 CF_UNICODETEXT 时已把 CRLF 压成 LF，
                // 但读本 JVM 自己写入的内容是逐字返回，此处再归一一遍以覆盖该情形
                return Win32Clipboard.normalizeNewlines(raw) to MIME_TEXT
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

    /** 把 AWT 图像包装成可放入剪贴板的 [Transferable]。 */
    private class ImageSelection(
        private val image: Image,
    ) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = DataFlavor.imageFlavor == flavor

        override fun getTransferData(flavor: DataFlavor): Any = image
    }
}

/**
 * 目标设备 uuid 数组的 JSON 文本。
 *
 * 手写引号与转义出过一次事故：字面量写坏会得到非法 JSON，Rust 侧解析失败后被当成"没有目标"，
 * 发送静默不发生。此处统一由序列化库生成。
 */
internal fun buildTargetsJson(uuids: List<String>): String =
    buildJsonArray {
        uuids.forEach { add(it) }
    }.toString()

/**
 * 取 Rust 回包的顶层字符串字段。
 *
 * Rust 侧以 `serde_json` 输出，内容按 JSON 规则转义；此处按 JSON 语法解码，路径中的反斜杠与
 * 文本中的换行原样还原。JSON 非法、字段缺失或字段不是字符串时返回 null。
 */
internal fun decodeResultField(
    json: String,
    field: String,
): String? =
    runCatching {
        val element = (Json.parseToJsonElement(json) as? JsonObject)?.get(field) as? JsonPrimitive
        if (element == null || !element.isString) null else element.content
    }.getOrNull()

/**
 * 剪贴板内容的日志摘要：长度、反斜杠数、换行数与关联标记。
 *
 * 不输出内容本身（剪贴板可能承载凭据）；配合 Rust 回包里的 reason，可在日志里区分
 * 本机复制与远端回灌，并暴露转义叠加与换行丢失。
 */
private fun describe(text: String): String =
    "长度=" + text.length +
        " 反斜杠=" + text.count { it == '\\' } +
        " 换行=" + text.count { it == '\n' } +
        " 标记=" + text.hashCode().toUInt().toString(16)
