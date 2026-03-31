package io.github.miuzarte.scrcpyforandroid.pages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHostState

val LocalScrcpyUiViewModel = staticCompositionLocalOf<ScrcpyUiViewModel> {
    error("ScrcpyUiViewModel is not provided")
}

val LocalScrcpyPagePadding = staticCompositionLocalOf { PaddingValues(0.dp) }
val LocalScrcpyScrollBehavior = staticCompositionLocalOf<ScrollBehavior?> { null }
val LocalScrcpySnackbarHostState = staticCompositionLocalOf<SnackbarHostState?> { null }
val LocalScrcpyThemeBaseIndex = staticCompositionLocalOf { 0 }

data class ScrcpyNavigationActions(
    val openAdvancedPage: () -> Unit,
    val openVirtualButtonOrder: () -> Unit,
    val openFullscreenPage: (ip: String, port: Int, deviceName: String) -> Unit,
    val openReorderDevices: () -> Unit,
    val pickServer: () -> Unit,
)

val LocalScrcpyNavigation = staticCompositionLocalOf {
    ScrcpyNavigationActions(
        openAdvancedPage = {},
        openVirtualButtonOrder = {},
        openFullscreenPage = { _, _, _ -> },
        openReorderDevices = {},
        pickServer = {},
    )
}

@Composable
fun ProvideScrcpyUiEnvironment(
    viewModel: ScrcpyUiViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollBehavior: ScrollBehavior? = null,
    snackHostState: SnackbarHostState? = null,
    themeBaseIndex: Int = 0,
    navigationActions: ScrcpyNavigationActions = LocalScrcpyNavigation.current,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalScrcpyUiViewModel provides viewModel,
        LocalScrcpyPagePadding provides contentPadding,
        LocalScrcpyScrollBehavior provides scrollBehavior,
        LocalScrcpySnackbarHostState provides snackHostState,
        LocalScrcpyThemeBaseIndex provides themeBaseIndex,
        LocalScrcpyNavigation provides navigationActions,
        content = content,
    )
}
