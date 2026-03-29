package io.github.miuzarte.scrcpyforandroid.pages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.ScrcpySessionInfo
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
    val openFullscreenPage: (ScrcpySessionInfo) -> Unit,
    val openReorderDevices: () -> Unit,
    val pickServer: () -> Unit,
)

val LocalScrcpyNavigation = staticCompositionLocalOf {
    ScrcpyNavigationActions(
        openAdvancedPage = {},
        openVirtualButtonOrder = {},
        openFullscreenPage = {},
        openReorderDevices = {},
        pickServer = {},
    )
}

data class ScrcpyFullscreenActions(
    val onDismiss: () -> Unit,
    val onVideoSizeChanged: (width: Int, height: Int) -> Unit,
)

val LocalScrcpyFullscreenActions = staticCompositionLocalOf {
    ScrcpyFullscreenActions(
        onDismiss = {},
        onVideoSizeChanged = { _, _ -> },
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
    fullscreenActions: ScrcpyFullscreenActions = LocalScrcpyFullscreenActions.current,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalScrcpyUiViewModel provides viewModel,
        LocalScrcpyPagePadding provides contentPadding,
        LocalScrcpyScrollBehavior provides scrollBehavior,
        LocalScrcpySnackbarHostState provides snackHostState,
        LocalScrcpyThemeBaseIndex provides themeBaseIndex,
        LocalScrcpyNavigation provides navigationActions,
        LocalScrcpyFullscreenActions provides fullscreenActions,
        content = content,
    )
}
