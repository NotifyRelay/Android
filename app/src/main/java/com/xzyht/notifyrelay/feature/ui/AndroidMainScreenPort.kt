package com.xzyht.notifyrelay.feature.ui

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalConfiguration
import com.xzyht.notifyrelay.ui.activity.MainActivity
import notifyrelay.base.util.IntentUtils
import notifyrelay.base.util.ToastUtils
import notifyrelay.shared.ui.screen.MainScreenPort

/** 以 [MainActivity] 实现 [MainScreenPort]。 */
class AndroidMainScreenPort(
    private val context: Context,
    private val activity: MainActivity?,
) : MainScreenPort {
    @Composable
    override fun isLandscape(): Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    override fun showAutoStartBanner(): State<Boolean> = activity?.showAutoStartBanner ?: NoOpState(false)

    override fun bannerMessage(): State<String?> = activity?.bannerMessage ?: NoOpState(null)

    override fun openAppSettings() {
        IntentUtils.startActivity(
            context,
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
            true,
        )
    }

    override fun finish() {
        activity?.finish()
    }

    override fun showExitHint() {
        ToastUtils.showShortToast(context, "再次返回以退出应用")
    }

    /** 固定值状态，宿主未提供 Activity 时使用。 */
    private class NoOpState<T>(
        override val value: T,
    ) : State<T>
}
