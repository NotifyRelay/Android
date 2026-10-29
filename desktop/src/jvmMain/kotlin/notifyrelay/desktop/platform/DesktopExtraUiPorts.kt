package notifyrelay.desktop.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import notifyrelay.base.log.Logger
import notifyrelay.base.util.DataUrlUtils
import notifyrelay.data.Storage
import notifyrelay.shared.ui.pages.remoteapps.AppLaunchPort
import notifyrelay.shared.ui.pages.remoteapps.DisplayInfo
import notifyrelay.shared.ui.pages.remoteapps.DisplayPort
import notifyrelay.shared.ui.pages.remoteapps.LocalAppLaunchPort
import notifyrelay.shared.ui.pages.remoteapps.LocalDisplayPort
import notifyrelay.shared.ui.pages.superisland.SUPER_ISLAND_IMAGE_MAX_DIMENSION
import notifyrelay.shared.ui.port.ImageDecodePort
import notifyrelay.shared.ui.port.LocalImageDecodePort
import notifyrelay.shared.ui.port.LocalPreferencePort
import notifyrelay.shared.ui.port.PreferencePort
import notifyrelay.shared.ui.screen.LocalMainScreenPort
import notifyrelay.shared.ui.screen.LocalSettingsPort
import notifyrelay.shared.ui.screen.MainScreenPort
import notifyrelay.shared.ui.screen.SettingsPort
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import kotlin.math.roundToInt

// 桌面端补充端口：偏好、图片解码、显示器、应用启动、主界面与设置。
// 偏好与开发者模式开关落到公共端 Storage（已由 data 层初始化）；
// 图片解码经 Skia 解码 data URL 与 http(s) 图片；
// 横屏口径统一由 isWindowLandscape() 提供，设备列表栏与主界面共用。
// 显示器与应用启动在桌面端无对应概念，给出中性实现。

/** 偏好端口：落到公共端 Storage 的默认分组。 */
internal object DesktopPreferencePort : PreferencePort {
    override fun getBoolean(
        key: String,
        default: Boolean,
    ): Boolean = Storage.getBoolean(key, default)

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) = Storage.putBoolean(key, value)

    override fun getInt(
        key: String,
        default: Int,
    ): Int = Storage.getInt(key, default)

    override fun putInt(
        key: String,
        value: Int,
    ) = Storage.putInt(key, value)

    override fun getString(
        key: String,
        default: String,
    ): String = Storage.getString(key, default)

    override fun putString(
        key: String,
        value: String,
    ) = Storage.putString(key, value)
}

/**
 * 图片解码端口：data URL 与 http(s) 图片经 Skia 解码为位图，按原文缓存最近 32 条。
 *
 * 数字图片 id 的数据库反查属超级岛历史数据源，接入时在此补分支。
 */
internal object DesktopImageDecodePort : ImageDecodePort {
    private const val TAG = "DesktopImage"

    /** 缓存条目上限，与 Android 侧超级岛图片缓存一致。 */
    private const val MAX_CACHE_SIZE = 32

    /** "data:" 前缀长度。 */
    private const val DATA_PREFIX_LENGTH = 5

    private const val HTTP_PREFIX = "http"
    private const val HTTP_CONNECT_TIMEOUT_MS = 3000
    private const val HTTP_READ_TIMEOUT_MS = 5000
    private const val HTTP_BUFFER_SIZE = 8 * 1024

    /** 远程图片字节上限，超出即视为失败。 */
    private const val MAX_HTTP_BYTES = 8 * 1024 * 1024

    private val cache =
        object : LinkedHashMap<String, ImageBitmap>(MAX_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean = size > MAX_CACHE_SIZE
        }

    override fun cached(data: String): ImageBitmap? = synchronized(cache) { cache[data] }

    override fun putCache(
        data: String,
        bitmap: ImageBitmap,
    ) {
        synchronized(cache) { cache[data] = bitmap }
    }

    override suspend fun decode(data: String): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val bytes =
                when {
                    DataUrlUtils.isDataUrl(data) -> DataUrlUtils.cleanDataUrl(data)?.let { dataUrlBytes(it) }
                    data.startsWith(HTTP_PREFIX, ignoreCase = true) -> fetchBytes(data)
                    else -> null
                } ?: return@withContext null
            runCatching { bytes.decodeToImageBitmap() }
                .onFailure { Logger.w(TAG, "解码图片失败: " + data.take(64), it) }
                .getOrNull()
                ?.let { scaledDown(it) }
        }

    /** data URL 负载字节：base64 用宽容解码器，其余按 URL 解码后取 UTF-8 字节。 */
    private fun dataUrlBytes(cleaned: String): ByteArray? {
        val comma = cleaned.indexOf(',')
        if (comma <= DATA_PREFIX_LENGTH) return null
        val meta = cleaned.substring(DATA_PREFIX_LENGTH, comma)
        val raw = cleaned.substring(comma + 1)
        return runCatching {
            if (meta.contains("base64", ignoreCase = true)) {
                Base64.getMimeDecoder().decode(raw)
            } else {
                URLDecoder.decode(raw, "UTF-8").toByteArray(Charsets.UTF_8)
            }
        }.getOrNull()
    }

    /** 取远程图片字节；超时、超限或失败返回 null。 */
    private fun fetchBytes(url: String): ByteArray? =
        runCatching {
            val connection = URI(url).toURL().openConnection()
            connection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
            connection.readTimeout = HTTP_READ_TIMEOUT_MS
            val output = ByteArrayOutputStream()
            connection.getInputStream().use { input ->
                val buffer = ByteArray(HTTP_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    if (output.size() > MAX_HTTP_BYTES) break
                }
            }
            if (output.size() > MAX_HTTP_BYTES) return@runCatching null
            output.toByteArray().takeIf { it.isNotEmpty() }
        }.getOrNull()

    /** 最长边超过展示上限时按比例缩小，避免缓存占用过大。 */
    private fun scaledDown(source: ImageBitmap): ImageBitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= SUPER_ISLAND_IMAGE_MAX_DIMENSION) return source
        val scale = SUPER_ISLAND_IMAGE_MAX_DIMENSION.toFloat() / largest
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        val target = ImageBitmap(width, height)
        Canvas(target).drawImageRect(
            image = source,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(source.width, source.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(width, height),
            paint = Paint(),
        )
        return target
    }
}

/** 显示器端口：桌面端只有单一显示区，不做插拔监听。 */
internal class DesktopDisplayPort : DisplayPort {
    private val single = MutableStateFlow(listOf(DisplayInfo(id = 0, name = "桌面", isBuiltIn = true)))
    override val displays = single

    override fun start() = Unit

    override fun stop() = Unit
}

/** 应用启动端口：桌面端无 Android 应用启动概念。 */
internal object DesktopAppLaunchPort : AppLaunchPort {
    override fun launchLocalApp(
        packageName: String,
        displayId: Int,
    ) {
        println("[启动应用] " + packageName)
    }
}

/** 主界面端口：横屏按窗口宽高比判定，无自启动横幅，结束动作隐藏到托盘。 */
internal class DesktopMainScreenPort(
    private val onFinish: () -> Unit,
) : MainScreenPort {
    private val noBanner = mutableStateOf(false)
    private val noMessage = mutableStateOf<String?>(null)

    @Composable
    override fun isLandscape(): Boolean = isWindowLandscape()

    override fun showAutoStartBanner(): State<Boolean> = noBanner

    override fun bannerMessage(): State<String?> = noMessage

    override fun openAppSettings() = Unit

    override fun finish() = onFinish()

    override fun showExitHint() = Unit
}

/** 设置端口：开发者模式开关落到公共端 Storage，入口页面未接线。 */
internal object DesktopSettingsPort : SettingsPort {
    private const val KEY_DEVELOPER_MODE = "developer_mode_enabled"

    override fun isDeveloperModeEnabled(): Boolean = Storage.getBoolean(KEY_DEVELOPER_MODE, false)
}

/** 窗口是否横向：取主窗口容器尺寸的宽高比，未附着窗口（尺寸为 0）时为 false。 */
@Composable
internal fun isWindowLandscape(): Boolean {
    val containerSize = LocalWindowInfo.current.containerSize
    return containerSize.width > containerSize.height
}

/** 为 [content] 提供桌面端补充端口。 */
@Composable
fun ProvideDesktopExtraPorts(
    onFinish: () -> Unit,
    content: @Composable () -> Unit,
) {
    val mainScreenPort = DesktopMainScreenPort(onFinish)
    CompositionLocalProvider(
        LocalPreferencePort provides DesktopPreferencePort,
        LocalImageDecodePort provides DesktopImageDecodePort,
        LocalDisplayPort provides DesktopDisplayPort(),
        LocalAppLaunchPort provides DesktopAppLaunchPort,
        LocalMainScreenPort provides mainScreenPort,
        LocalSettingsPort provides DesktopSettingsPort,
        content = content,
    )
}
