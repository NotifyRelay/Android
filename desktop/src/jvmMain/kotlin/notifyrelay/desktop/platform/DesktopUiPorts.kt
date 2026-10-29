package notifyrelay.desktop.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import notifyrelay.base.util.decodeImageBitmap
import notifyrelay.data.Storage
import notifyrelay.desktop.DataLayerDesktop
import notifyrelay.shared.ui.port.ClipboardPort
import notifyrelay.shared.ui.port.IntentLaunchPort
import notifyrelay.shared.ui.port.LocalBitmapPort
import notifyrelay.shared.ui.port.LocalClipboardPort
import notifyrelay.shared.ui.port.LocalClipboardSyncPort
import notifyrelay.shared.ui.port.LocalIntentLaunchPort
import notifyrelay.shared.ui.port.LocalSystemBarPort
import notifyrelay.shared.ui.port.LocalThemePort
import notifyrelay.shared.ui.port.LocalToastPort
import notifyrelay.shared.ui.port.SystemBarPort
import notifyrelay.shared.ui.port.ThemeBaseIndex
import notifyrelay.shared.ui.port.ThemePort
import notifyrelay.shared.ui.port.ToastPort
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** 桌面端提示：输出到控制台（无原生 Toast 概念）。 */
private object DesktopToastPort : ToastPort {
    override fun short(message: String) {
        println("[提示] " + message)
    }

    override fun long(message: String) {
        println("[提示] " + message)
    }
}

/** 桌面端剪贴板：使用 AWT 系统剪贴板。 */
private object DesktopClipboardPort : ClipboardPort {
    override fun copyText(
        label: String,
        text: String,
    ): Boolean =
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            true
        }.getOrDefault(false)
}

/** 桌面端跳转：暂不实现系统界面跳转。 */
private object DesktopIntentLaunchPort : IntentLaunchPort {
    override fun startAction(
        action: String,
        data: String?,
        addNewTaskFlag: Boolean,
    ) {
        println("[跳转] action=" + action + " data=" + (data ?: ""))
    }
}

/** 桌面端主题：索引持久化到公共端 Storage，变化时分发监听。 */
internal object DesktopThemePort : ThemePort {
    private const val KEY_THEME_BASE_INDEX = "theme_base_index"

    private var index = Storage.getInt(KEY_THEME_BASE_INDEX, ThemeBaseIndex.FOLLOW_SYSTEM)
    private val listeners = mutableListOf<(Int) -> Unit>()

    override fun currentIndex(): Int = index

    override fun setIndex(index: Int) {
        val next = index.coerceIn(ThemeBaseIndex.FOLLOW_SYSTEM, ThemeBaseIndex.DARK)
        if (next == this.index) return
        this.index = next
        Storage.putInt(KEY_THEME_BASE_INDEX, next)
        listeners.forEach { it(next) }
    }

    override fun addListener(listener: (Int) -> Unit) {
        listeners.add(listener)
    }

    override fun removeListener(listener: (Int) -> Unit) {
        listeners.remove(listener)
    }
}

/** 桌面端系统栏：无对应概念，空实现。 */
private object DesktopSystemBarPort : SystemBarPort {
    override fun setStatusBarColor(
        color: Int,
        animate: Boolean,
    ) = Unit

    override fun setNavigationBarColor(
        color: Int,
        animate: Boolean,
    ) = Unit
}

/**
 * 桌面端位图端口。
 *
 * 解码统一走 [decodeImageBitmap]（公共实现，平台后端为 Skia）；
 * [drawableToBitmap] 在桌面端没有 Drawable 概念：入参为字节数组时按字节解码，其余原样返回。
 */
private object DesktopBitmapPort : notifyrelay.shared.ui.port.BitmapPort {
    override fun drawableToBitmap(
        drawable: Any,
        fallbackSize: Int,
    ): Any =
        when (drawable) {
            is ByteArray -> decodeImageBitmap(drawable) ?: drawable
            else -> drawable
        }

    override fun decodeByteArray(bytes: ByteArray): ImageBitmap? = decodeImageBitmap(bytes)
}

/** 为 [content] 提供桌面端全部界面端口。 */
@Composable
fun ProvideDesktopUiPorts(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalToastPort provides DesktopToastPort,
        LocalClipboardPort provides DesktopClipboardPort,
        LocalIntentLaunchPort provides DesktopIntentLaunchPort,
        LocalThemePort provides DesktopThemePort,
        LocalSystemBarPort provides DesktopSystemBarPort,
        LocalBitmapPort provides DesktopBitmapPort,
        LocalClipboardSyncPort provides DataLayerDesktop.clipboardSync,
        content = content,
    )
}
