package com.xzyht.notifyrelay.ui.port

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.xzyht.notifyrelay.feature.appslist.launch.AndroidAppLaunchPort
import com.xzyht.notifyrelay.feature.device.AndroidDeviceListPort
import com.xzyht.notifyrelay.feature.device.service.AndroidDevicePortsProvider
import com.xzyht.notifyrelay.feature.display.AndroidDisplayPort
import com.xzyht.notifyrelay.feature.preference.AndroidPreferencePort
import com.xzyht.notifyrelay.feature.settings.AndroidSettingsPort
import com.xzyht.notifyrelay.feature.ui.AndroidMainScreenPort
import com.xzyht.notifyrelay.ui.activity.MainActivity
import notifyrelay.base.util.ClipboardUtils
import notifyrelay.base.util.IntentUtils
import notifyrelay.base.util.SystemBarUtils
import notifyrelay.base.util.ThemeSettingsManager
import notifyrelay.base.util.ToastUtils
import notifyrelay.base.util.image.toBitmapOrDefault
import notifyrelay.shared.ui.pages.remoteapps.LocalAppLaunchPort
import notifyrelay.shared.ui.pages.remoteapps.LocalDisplayPort
import notifyrelay.shared.ui.port.BitmapPort
import notifyrelay.shared.ui.port.ClipboardPort
import notifyrelay.shared.ui.port.IntentLaunchPort
import notifyrelay.shared.ui.port.LocalBitmapPort
import notifyrelay.shared.ui.port.LocalClipboardPort
import notifyrelay.shared.ui.port.LocalIntentLaunchPort
import notifyrelay.shared.ui.port.LocalPreferencePort
import notifyrelay.shared.ui.port.LocalSystemBarPort
import notifyrelay.shared.ui.port.LocalThemePort
import notifyrelay.shared.ui.port.LocalToastPort
import notifyrelay.shared.ui.port.SystemBarPort
import notifyrelay.shared.ui.port.ThemePort
import notifyrelay.shared.ui.port.ToastPort
import notifyrelay.shared.ui.screen.LocalMainScreenPort
import notifyrelay.shared.ui.screen.LocalSettingsPort
import notifyrelay.shared.ui.screen.devicelist.LocalDeviceListPort
import notifyrelay.shared.ui.viewmodel.LocalDevicePortsProvider

private class AndroidToastPort(
    private val context: Context,
) : ToastPort {
    override fun short(message: String) = ToastUtils.showShortToast(context, message)

    override fun long(message: String) = ToastUtils.showLongToast(context, message)
}

private class AndroidClipboardPort(
    private val context: Context,
) : ClipboardPort {
    override fun copyText(
        label: String,
        text: String,
    ): Boolean = ClipboardUtils.copyText(context, label, text)
}

private class AndroidIntentLaunchPort(
    private val context: Context,
) : IntentLaunchPort {
    override fun startAction(
        action: String,
        data: String?,
        addNewTaskFlag: Boolean,
    ) {
        IntentUtils.startActivity(context, action, data?.let { android.net.Uri.parse(it) }, addNewTaskFlag)
    }
}

private class AndroidThemePort(
    private val context: Context,
) : ThemePort {
    override fun currentIndex(): Int = ThemeSettingsManager.getThemeBaseIndex(context)

    override fun setIndex(index: Int) = ThemeSettingsManager.setThemeBaseIndex(context, index)

    override fun addListener(listener: (Int) -> Unit) {
        ThemeSettingsManager.addThemeChangeListener(context, listener)
    }

    override fun removeListener(listener: (Int) -> Unit) {
        ThemeSettingsManager.removeThemeChangeListener(context, listener)
    }
}

private class AndroidSystemBarPort(
    private val view: android.view.View,
) : SystemBarPort {
    override fun setStatusBarColor(
        color: Int,
        animate: Boolean,
    ) {
        (view.parent as? android.view.View)?.let { }
        val window = (view.context as? android.app.Activity)?.window ?: return
        SystemBarUtils.setStatusBarColor(window, color, animate)
    }

    override fun setNavigationBarColor(
        color: Int,
        animate: Boolean,
    ) {
        val window = (view.context as? android.app.Activity)?.window ?: return
        SystemBarUtils.setNavigationBarColor(window, color, animate)
    }
}

private object AndroidBitmapPort : BitmapPort {
    override fun drawableToBitmap(
        drawable: Any,
        fallbackSize: Int,
    ): Any = (drawable as android.graphics.drawable.Drawable).toBitmapOrDefault(fallbackSize)

    override fun decodeByteArray(bytes: ByteArray): androidx.compose.ui.graphics.ImageBitmap? =
        runCatching {
            android.graphics.BitmapFactory
                .decodeByteArray(bytes, 0, bytes.size)
                ?.asImageBitmap()
        }.getOrNull()
}

/**
 * 为 [content] 提供全部界面端口。宿主 Activity 的内容根需以此包裹一层。
 */
@Composable
fun ProvideAndroidUiPorts(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    CompositionLocalProvider(
        LocalToastPort provides AndroidToastPort(context),
        LocalClipboardPort provides AndroidClipboardPort(context),
        LocalIntentLaunchPort provides AndroidIntentLaunchPort(context),
        LocalThemePort provides AndroidThemePort(context),
        LocalSystemBarPort provides AndroidSystemBarPort(view),
        LocalBitmapPort provides AndroidBitmapPort,
        LocalDisplayPort provides AndroidDisplayPort(context),
        LocalAppLaunchPort provides AndroidAppLaunchPort(context),
        LocalDeviceListPort provides AndroidDeviceListPort(context),
        LocalDevicePortsProvider provides AndroidDevicePortsProvider(context),
        LocalSettingsPort provides AndroidSettingsPort(context),
        LocalMainScreenPort provides AndroidMainScreenPort(context, LocalContext.current as? MainActivity),
        LocalPreferencePort provides AndroidPreferencePort,
        content = content,
    )
}
