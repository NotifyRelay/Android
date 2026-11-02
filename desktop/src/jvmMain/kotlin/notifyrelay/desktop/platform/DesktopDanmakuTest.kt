package notifyrelay.desktop.platform

import androidx.compose.runtime.Composable
import notifyrelay.desktop.overlay.DanmakuEntry
import notifyrelay.desktop.overlay.DesktopOverlayStore
import notifyrelay.shared.feature.appslist.SharedAppIcon
import top.yukonga.miuix.kmp.preference.ArrowPreference

/**
 * 弹幕测试。
 *
 * 与 C# `DanmakuViewModel.SendTestDanmaku` 一致：
 * 应用名「NotifyRelay」、标题「测试」、内容「这是一条测试弹幕通知」，
 * 图标取应用自身图标（C# 用 `Assets/NotifyRelayAppIcon.png`，此处用同一份矢量源）。
 */
internal object DesktopDanmakuTest {
    /**
     * 追加一条测试弹幕。
     *
     * 图标在首次调用时异步取好并缓存，避免每次点击都读一次资源。
     */
    fun post() {
        val icon = testIcon()
        DesktopOverlayStore.addDanmaku(
            DanmakuEntry(
                id = System.nanoTime(),
                appName = APP_NAME,
                title = TITLE,
                text = TEXT,
                deviceName = DEVICE_NAME,
                iconPng = icon,
            ),
        )
    }

    /** 应用图标的矢量字节（与应用图标同一来源）。 */
    @Volatile
    private var cachedIcon: ByteArray? = null

    /**
     * 取应用图标的 **PNG 字节**。
     *
     * 与 C# 的 `Assets/NotifyRelayAppIcon.png` 对应：应用图标源是矢量，
     * 而弹幕层与列表一样走位图解码，因此这里先栅格化再编码为 PNG。
     */
    private fun testIcon(): ByteArray? {
        cachedIcon?.let { return it }
        cachedIcon =
            runCatching {
                val svg =
                    SharedAppIcon.bytesOrNull()
                        ?: kotlinx.coroutines.runBlocking { SharedAppIcon.preload() }
                        ?: return null
                val image = AppIcon.render(svg, ICON_SIZE)
                java.io.ByteArrayOutputStream().use { stream ->
                    javax.imageio.ImageIO.write(image, "png", stream)
                    stream.toByteArray()
                }
            }.getOrNull()
        return cachedIcon
    }

    /** 测试图标栅格边长（像素）。 */
    private const val ICON_SIZE = 96

    private const val APP_NAME = "NotifyRelay"
    private const val TITLE = "测试"
    private const val TEXT = "这是一条测试弹幕通知"
    private const val DEVICE_NAME = "本机"
}

/** 叠加层设置页的「测试弹幕」入口。 */
@Composable
internal fun DesktopDanmakuTestEntry() {
    ArrowPreference(
        title = "测试弹幕",
        summary = "每次点击追加一条",
        onClick = { DesktopDanmakuTest.post() },
    )
}
