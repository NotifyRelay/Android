package notifyrelay.desktop.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import notifyrelay.data.Storage
import notifyrelay.shared.ui.pages.remoteapps.AppLaunchPort
import notifyrelay.shared.ui.pages.remoteapps.DisplayInfo
import notifyrelay.shared.ui.pages.remoteapps.DisplayPort
import notifyrelay.shared.ui.pages.remoteapps.LocalAppLaunchPort
import notifyrelay.shared.ui.pages.remoteapps.LocalDisplayPort
import notifyrelay.shared.ui.port.ImageDecodePort
import notifyrelay.shared.ui.port.LocalImageDecodePort
import notifyrelay.shared.ui.port.LocalPreferencePort
import notifyrelay.shared.ui.port.PreferencePort
import notifyrelay.shared.ui.screen.LocalMainScreenPort
import notifyrelay.shared.ui.screen.LocalSettingsPort
import notifyrelay.shared.ui.screen.MainScreenPort
import notifyrelay.shared.ui.screen.SettingsPort

// 桌面端补充端口：偏好、图片解码、显示器、应用启动、主界面与设置。
// 偏好读写落到公共端 Storage（已由 data 层初始化的数据库承载）；
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

/** 图片解码端口：桌面端暂不解码超级岛图片。 */
internal object DesktopImageDecodePort : ImageDecodePort {
    override fun cached(data: String): ImageBitmap? = null

    override fun putCache(
        data: String,
        bitmap: ImageBitmap,
    ) = Unit

    override suspend fun decode(data: String): ImageBitmap? = null
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

/** 主界面端口：桌面端固定非横屏、无自启动横幅、退出直接结束进程。 */
internal class DesktopMainScreenPort(
    private val onExit: () -> Unit,
) : MainScreenPort {
    private val noBanner = mutableStateOf(false)
    private val noMessage = mutableStateOf<String?>(null)

    @Composable
    override fun isLandscape(): Boolean = false

    override fun showAutoStartBanner(): State<Boolean> = noBanner

    override fun bannerMessage(): State<String?> = noMessage

    override fun openAppSettings() = Unit

    override fun finish() = onExit()

    override fun showExitHint() = Unit
}

/** 设置端口：桌面端无开发者模式入口。 */
internal object DesktopSettingsPort : SettingsPort {
    override fun isDeveloperModeEnabled(): Boolean = false

    override fun openDeveloperMode() = Unit
}

/** 为 [content] 提供桌面端补充端口。 */
@Composable
fun ProvideDesktopExtraPorts(
    onExit: () -> Unit,
    content: @Composable () -> Unit,
) {
    val mainScreenPort = DesktopMainScreenPort(onExit)
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
