package io.github.miuzarte.scrcpyforandroid.pages

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import notifyrelay.base.util.ThemeSettingsManager
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHostState

@Composable
fun ScrcpyDevicePage() {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: ScrcpyUiViewModel = viewModel(factory = ScrcpyUiViewModel.Factory(app))
    val snackHostState = remember { SnackbarHostState() }
    val scrollBehavior = MiuixScrollBehavior(canScroll = { true })
    var themeBaseIndex by remember { mutableIntStateOf(ThemeSettingsManager.getThemeBaseIndex(context)) }

    DisposableEffect(context) {
        val listener = ThemeSettingsManager.ThemeChangeListener { newBaseIndex ->
            themeBaseIndex = newBaseIndex
        }
        ThemeSettingsManager.addThemeChangeListener(context, listener)
        onDispose {
            ThemeSettingsManager.removeThemeChangeListener(context, listener)
        }
    }

    val navigationActions = remember {
        ScrcpyNavigationActions(
            openAdvancedPage = {},
            openVirtualButtonOrder = {},
            openFullscreenPage = { session ->
                viewModel.fullscreenLaunch = FullscreenControlLaunch(
                    deviceName = session.deviceName,
                    width = session.width,
                    height = session.height,
                    codec = session.codec,
                )
            },
            openReorderDevices = { viewModel.openReorderDevicesAction?.invoke() },
            pickServer = {},
        )
    }
    val fullscreenActions = remember {
        ScrcpyFullscreenActions(
            onDismiss = { viewModel.fullscreenLaunch = null },
            onVideoSizeChanged = { _, _ -> },
        )
    }

    ProvideScrcpyUiEnvironment(
        viewModel = viewModel,
        contentPadding = PaddingValues(0.dp),
        scrollBehavior = scrollBehavior,
        snackHostState = snackHostState,
        themeBaseIndex = themeBaseIndex,
        navigationActions = navigationActions,
        fullscreenActions = fullscreenActions,
    ) {
        if (viewModel.fullscreenLaunch != null) {
            FullscreenControlPage()
        } else {
            DeviceTabScreen()
        }
    }
}
