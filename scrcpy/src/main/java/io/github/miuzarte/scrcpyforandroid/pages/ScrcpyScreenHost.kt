package io.github.miuzarte.scrcpyforandroid.pages

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import notifyrelay.data.model.SelectedDeviceInfo

sealed interface ScrcpyRootScreen : NavKey {
    data object Device : ScrcpyRootScreen
    data object Settings : ScrcpyRootScreen
    data object Advanced : ScrcpyRootScreen
    data object VirtualButtonOrder : ScrcpyRootScreen
}

@Composable
fun ScrcpyScreenHost(
    startScreen: ScrcpyRootScreen,
    selectedDevice: SelectedDeviceInfo? = null,
    onPickServer: (() -> Unit)? = null,
    onOpenAdvanced: (() -> Unit)? = null,
    onExit: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel = remember { ScrcpyUiViewModel.getInstance(app) }
    val backStack = remember { mutableStateListOf<NavKey>(startScreen) }

    LaunchedEffect(startScreen) {
        if (backStack.size != 1 || backStack.firstOrNull() != startScreen) {
            backStack.clear()
            backStack.add(startScreen)
        }
    }

    fun popOrExit() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        } else {
            onExit?.invoke()
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { popOrExit() },
        entryProvider = entryProvider {
            entry<ScrcpyRootScreen.Device> {
                ScrcpyDevicePage(
                    selectedDevice = selectedDevice,
                    onOpenAdvanced = { onOpenAdvanced?.invoke() ?: backStack.add(ScrcpyRootScreen.Advanced) },
                    viewModel = viewModel,
                )
            }
            entry<ScrcpyRootScreen.Settings> {
                ScrcpySettingsPage(
                    onOpenVirtualButtonOrder = { backStack.add(ScrcpyRootScreen.VirtualButtonOrder) },
                    onPickServer = { onPickServer?.invoke() },
                )
            }
            entry<ScrcpyRootScreen.Advanced> {
                ScrcpyAdvancedPage(
                    onBack = { popOrExit() },
                    viewModel = viewModel,
                )
            }
            entry<ScrcpyRootScreen.VirtualButtonOrder> {
                ScrcpyVirtualButtonOrderPage(
                    onBack = { popOrExit() },
                )
            }
        },
    )
}
