package notifyrelay.desktop.platform

import androidx.compose.runtime.Composable
import notifyrelay.desktop.overlay.DesktopOverlayStore
import notifyrelay.desktop.overlay.NotificationEntry
import notifyrelay.shared.feature.appslist.SharedAppIcon
import top.yukonga.miuix.kmp.preference.ArrowPreference

/**
 * 通知卡片测试。
 *
 * 每次点击追加一条通知卡片，用于在不等待远端报文时确认卡片、收起与图标态的时序。
 * 图标取应用自身图标，与真实通知走同一条位图解码路径。
 */
internal object DesktopNotificationTest {
    /**
     * 追加一条测试通知。
     *
     * 图标在首次调用时异步取好并缓存，避免每次点击都读一次资源。
     */
    fun post() {
        val icon = testIcon()
        DesktopOverlayStore.addNotification(
            NotificationEntry(
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
     * 取应用图标的 PNG 字节。
     *
     * 应用图标源是矢量，而卡片与图标态同样走位图解码，因此这里先栅格化再编码为 PNG。
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
    private const val TITLE = "测试通知"
    private const val TEXT = "这是一条测试通知卡片"
    private const val DEVICE_NAME = "本机"
}

/** 叠加层设置页的「测试通知卡片」入口。 */
@Composable
internal fun DesktopNotificationTestEntry() {
    ArrowPreference(
        title = "测试通知卡片",
        summary = "每次点击追加一条，3 秒后收起为图标，图标 12 秒后移除",
        onClick = { DesktopNotificationTest.post() },
    )
}
