package io.github.miuzarte.scrcpyforandroid.pages

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import io.github.miuzarte.scrcpyforandroid.constants.UiMotion
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import notifyrelay.data.config.ScrcpyDefaults
import io.github.miuzarte.scrcpyforandroid.services.MainSettings
import io.github.miuzarte.scrcpyforandroid.services.saveMainSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notifyrelay.base.util.ThemeSettingsManager
import androidx.lifecycle.viewmodel.compose.viewModel
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

private enum class MainTabDestination(
    val title: String,
    val label: String,
    val icon: ImageVector,
) {
    Device(title = "设备", label = "设备", icon = Icons.Rounded.Devices),
    Settings(title = "设置", label = "设置", icon = Icons.Rounded.Settings),
}

private sealed interface RootScreen : NavKey {
    data object Home : RootScreen
    data object Advanced : RootScreen
    data object VirtualButtonOrder : RootScreen
    data class Fullscreen(val launch: FullscreenControlLaunch) : RootScreen
}

@Composable
fun MainPage() {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }
    val initialOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val app = context.applicationContext as Application
    val viewModel: ScrcpyUiViewModel = viewModel(factory = ScrcpyUiViewModel.Factory(app))
    val nativeCore = viewModel.nativeCore
    val snackHostState = remember { SnackbarHostState() }
    val tabs = remember { MainTabDestination.entries }
    val pagerState = rememberPagerState(
        initialPage = MainTabDestination.Device.ordinal,
        pageCount = { tabs.size })
    val currentTab = tabs[pagerState.currentPage]
    val saveableStateHolder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val rootBackStack = remember { mutableStateListOf<NavKey>(RootScreen.Home) }
    val currentRootScreen = rootBackStack.lastOrNull() as? RootScreen ?: RootScreen.Home
    val deviceScrollBehavior =
        MiuixScrollBehavior(canScroll = { currentTab == MainTabDestination.Device })
    val settingsScrollBehavior =
        MiuixScrollBehavior(canScroll = { currentTab == MainTabDestination.Settings })
    val advancedScrollBehavior = MiuixScrollBehavior(
        canScroll = {
            currentRootScreen is RootScreen.Advanced || currentRootScreen is RootScreen.VirtualButtonOrder
        },
    )
    var themeBaseIndex by remember { mutableIntStateOf(ThemeSettingsManager.getThemeBaseIndex(context)) }
    var showDeviceMenu by rememberSaveable { mutableStateOf(false) }
    var lastExitBackPressAtMs by rememberSaveable { mutableLongStateOf(0L) }
    var fullscreenOrientation by rememberSaveable {
        mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    }
    val themeMode = resolveThemeMode(themeBaseIndex)
    val themeController = remember(themeMode) { ThemeController(colorSchemeMode = themeMode) }

    LaunchedEffect(Unit) {
        themeBaseIndex = ThemeSettingsManager.getThemeBaseIndex(context)
    }

    DisposableEffect(context) {
        val listener = ThemeSettingsManager.ThemeChangeListener { newBaseIndex ->
            themeBaseIndex = newBaseIndex
        }
        ThemeSettingsManager.addThemeChangeListener(context, listener)
        onDispose {
            ThemeSettingsManager.removeThemeChangeListener(context, listener)
        }
    }

    // Restore system orientation when MainPage leaves composition.
    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = initialOrientation
        }
    }

    // Keep-screen-on is controlled globally, so fullscreen and preview share the same behavior.
    DisposableEffect(activity, viewModel.keepScreenOnWhenStreamingEnabled, viewModel.sessionStarted) {
        val window = activity?.window
        val shouldKeepScreenOn = viewModel.keepScreenOnWhenStreamingEnabled && viewModel.sessionStarted
        if (window != null && shouldKeepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (window != null && shouldKeepScreenOn) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Fullscreen route can force orientation based on stream ratio; all other routes follow system.
    LaunchedEffect(activity, currentRootScreen, fullscreenOrientation) {
        val targetOrientation = when (currentRootScreen) {
            is RootScreen.Fullscreen -> fullscreenOrientation
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        activity?.requestedOrientation = targetOrientation
    }

    LaunchedEffect(
        viewModel.audioEnabled,
        viewModel.audioCodec,
        viewModel.videoCodec,
        viewModel.fullscreenDebugInfoEnabled,
        viewModel.showFullscreenVirtualButtons,
        viewModel.showPreviewVirtualButtonText,
        viewModel.keepScreenOnWhenStreamingEnabled,
        viewModel.devicePreviewCardHeightDp,
        viewModel.virtualButtonsLayout,
        viewModel.customServerUri,
        viewModel.serverRemotePath,
        viewModel.adbKeyName,
        viewModel.adbPairingAutoDiscoverOnDialogOpen,
        viewModel.adbAutoReconnectPairedDevice,
        viewModel.adbMdnsLanDiscoveryEnabled,
    ) {
        saveMainSettings(
            context,
            MainSettings(
                audioEnabled = viewModel.audioEnabled,
                audioCodec = viewModel.audioCodec,
                videoCodec = viewModel.videoCodec,
                fullscreenDebugInfoEnabled = viewModel.fullscreenDebugInfoEnabled,
                showFullscreenVirtualButtons = viewModel.showFullscreenVirtualButtons,
                showPreviewVirtualButtonText = viewModel.showPreviewVirtualButtonText,
                keepScreenOnWhenStreamingEnabled = viewModel.keepScreenOnWhenStreamingEnabled,
                devicePreviewCardHeightDp = viewModel.devicePreviewCardHeightDp,
                virtualButtonsLayout = viewModel.virtualButtonsLayout,
                customServerUri = viewModel.customServerUri,
                serverRemotePath = viewModel.serverRemotePath,
                adbKeyName = viewModel.adbKeyName,
                adbPairingAutoDiscoverOnDialogOpen = viewModel.adbPairingAutoDiscoverOnDialogOpen,
                adbAutoReconnectPairedDevice = viewModel.adbAutoReconnectPairedDevice,
                adbMdnsLanDiscoveryEnabled = viewModel.adbMdnsLanDiscoveryEnabled,
            ),
        )
    }

    LaunchedEffect(viewModel.adbKeyName) {
        nativeCore.setAdbKeyName(viewModel.adbKeyName.ifBlank { ScrcpyDefaults.ADB_KEY_NAME })
    }

    fun popRoot() {
        if (rootBackStack.size > 1) {
            rootBackStack.removeAt(rootBackStack.lastIndex)
        }
    }

    // Unified back behavior:
    // 1) pop inner route
    // 2) switch tab back to Device
    // 3) double-back to exit and disconnect adb/scrcpy
    fun handleBackNavigation() {
        if (rootBackStack.size > 1) {
            popRoot()
        } else if (pagerState.currentPage != MainTabDestination.Device.ordinal) {
            scope.launch {
                pagerState.animateScrollToPage(
                    page = MainTabDestination.Device.ordinal,
                    animationSpec = spring(
                        dampingRatio = UiMotion.PAGE_SWITCH_DAMPING_RATIO,
                        stiffness = UiMotion.PAGE_SWITCH_STIFFNESS,
                    ),
                )
            }
        } else {
            val now = SystemClock.elapsedRealtime()
            if (now - lastExitBackPressAtMs > 2_000L) {
                lastExitBackPressAtMs = now
                Toast.makeText(context, "再按一次返回键退出", Toast.LENGTH_SHORT).show()
                return
            }
            lastExitBackPressAtMs = 0L
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching { nativeCore.scrcpyStop() }
                    runCatching { nativeCore.adbDisconnect() }
                }
                activity?.finish()
            }
        }
    }

    val canNavigateBack = rootBackStack.size > 1 ||
            pagerState.currentPage != MainTabDestination.Device.ordinal

    BackHandler(enabled = currentRootScreen !is RootScreen.Fullscreen) {
        handleBackNavigation()
    }

    PredictiveBackHandler(
        enabled = canNavigateBack && currentRootScreen !is RootScreen.Fullscreen
    ) { progress ->
        try {
            progress.collect { }
            handleBackNavigation()
        } catch (_: CancellationException) {
            // Gesture was cancelled by the system/user.
        }
    }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.customServerUri = uri.toString()
        }

    val navigationActions = remember(rootBackStack, viewModel, picker) {
        ScrcpyNavigationActions(
            openAdvancedPage = { rootBackStack.add(RootScreen.Advanced) },
            openVirtualButtonOrder = { rootBackStack.add(RootScreen.VirtualButtonOrder) },
            openFullscreenPage = { session ->
                viewModel.fullscreenLaunch = FullscreenControlLaunch(
                    deviceName = session.deviceName,
                    width = session.width,
                    height = session.height,
                    codec = session.codec,
                )
                rootBackStack.add(
                    RootScreen.Fullscreen(
                        launch = FullscreenControlLaunch(
                            deviceName = session.deviceName,
                            width = session.width,
                            height = session.height,
                            codec = session.codec,
                        ),
                    ),
                )
            },
            openReorderDevices = { viewModel.openReorderDevicesAction?.invoke() },
            pickServer = {
                picker.launch(
                    arrayOf(
                        "application/java-archive",
                        "application/octet-stream",
                        "*/*",
                    )
                )
            },
        )
    }
    val fullscreenActions = remember {
        ScrcpyFullscreenActions(
            onDismiss = {
                viewModel.fullscreenLaunch = null
                popRoot()
            },
            onVideoSizeChanged = { _, _ -> },
        )
    }

    val rootEntryProvider = entryProvider<NavKey> {
        entry(RootScreen.Home) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            page = tab.ordinal,
                                            animationSpec = spring(
                                                dampingRatio = UiMotion.PAGE_SWITCH_DAMPING_RATIO,
                                                stiffness = UiMotion.PAGE_SWITCH_STIFFNESS,
                                            ),
                                        )
                                    }
                                },
                                icon = tab.icon,
                                label = tab.label,
                            )
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackHostState) },
            ) { contentPadding ->
                HorizontalPager(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = contentPadding.calculateBottomPadding()),
                    state = pagerState,
                    beyondViewportPageCount = 1,
                ) { page ->
                    val tab = tabs[page]
                    saveableStateHolder.SaveableStateProvider(tab.name) {
                        when (tab) {
                            MainTabDestination.Device -> Scaffold(
                                topBar = {
                                    TopAppBar(
                                        title = tab.title,
                                        actions = {
                                            IconButton(
                                                onClick = { showDeviceMenu = true },
                                                holdDownState = showDeviceMenu,
                                            ) {
                                                Icon(
                                                    Icons.Rounded.MoreVert,
                                                    contentDescription = "更多"
                                                )
                                            }
                                            DeviceMenuPopup(
                                                show = showDeviceMenu,
                                                canClearLogs = viewModel.canClearLogs,
                                                onDismissRequest = { showDeviceMenu = false },
                                                onReorderDevices = {
                                                    viewModel.openReorderDevicesAction?.invoke()
                                                    showDeviceMenu = false
                                                },
                                                onOpenVirtualButtonOrder = {
                                                    rootBackStack.add(RootScreen.VirtualButtonOrder)
                                                    showDeviceMenu = false
                                                },
                                                onClearLogs = {
                                                    viewModel.clearLogsAction?.invoke()
                                                    showDeviceMenu = false
                                                },
                                            )
                                        },
                                        scrollBehavior = deviceScrollBehavior,
                                    )
                                },
                            ) { pagePadding ->
                                ProvideScrcpyUiEnvironment(
                                    viewModel = viewModel,
                                    contentPadding = pagePadding,
                                    scrollBehavior = deviceScrollBehavior,
                                    snackHostState = snackHostState,
                                    themeBaseIndex = themeBaseIndex,
                                    navigationActions = navigationActions,
                                    fullscreenActions = fullscreenActions,
                                ) {
                                    DeviceTabScreen()
                                }
                            }

                            MainTabDestination.Settings -> Scaffold(
                                topBar = {
                                    TopAppBar(
                                        title = tab.title,
                                        scrollBehavior = settingsScrollBehavior,
                                    )
                                },
                            ) { pagePadding ->
                                ProvideScrcpyUiEnvironment(
                                    viewModel = viewModel,
                                    contentPadding = pagePadding,
                                    scrollBehavior = settingsScrollBehavior,
                                    themeBaseIndex = themeBaseIndex,
                                    navigationActions = navigationActions,
                                    fullscreenActions = fullscreenActions,
                                ) {
                                    SettingsScreen()
                                }
                            }
                        }
                    }
                }
            }
        }

        entry(RootScreen.Advanced) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = "高级参数",
                        navigationIcon = {
                            IconButton(onClick = { popRoot() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回"
                                )
                            }
                        },
                        scrollBehavior = advancedScrollBehavior,
                    )
                },
                snackbarHost = { SnackbarHost(snackHostState) },
            ) { pagePadding ->
                ProvideScrcpyUiEnvironment(
                    viewModel = viewModel,
                    contentPadding = pagePadding,
                    scrollBehavior = advancedScrollBehavior,
                    snackHostState = snackHostState,
                    themeBaseIndex = themeBaseIndex,
                    navigationActions = navigationActions,
                    fullscreenActions = fullscreenActions,
                ) {
                    AdvancedConfigPage()
                }
            }
        }

        entry(RootScreen.VirtualButtonOrder) {
            Scaffold(
                modifier = Modifier.nestedScroll(advancedScrollBehavior.nestedScrollConnection),
                topBar = {
                    TopAppBar(
                        title = "虚拟按钮排序",
                        navigationIcon = {
                            IconButton(onClick = { popRoot() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回"
                                )
                            }
                        },
                        scrollBehavior = advancedScrollBehavior,
                    )
                },
            ) { pagePadding ->
                ProvideScrcpyUiEnvironment(
                    viewModel = viewModel,
                    contentPadding = pagePadding,
                    scrollBehavior = advancedScrollBehavior,
                    themeBaseIndex = themeBaseIndex,
                    navigationActions = navigationActions,
                    fullscreenActions = fullscreenActions,
                ) {
                    VirtualButtonOrderPage()
                }
            }
        }

        entry<RootScreen.Fullscreen> { screen ->
            LaunchedEffect(screen.launch) {
                viewModel.fullscreenLaunch = screen.launch
            }
            ProvideScrcpyUiEnvironment(
                viewModel = viewModel,
                navigationActions = navigationActions,
                fullscreenActions = fullscreenActions,
                themeBaseIndex = themeBaseIndex,
            ) {
                FullscreenControlPage()
            }
        }
    }

    val rootEntries = rememberDecoratedNavEntries(
        backStack = rootBackStack,
        entryProvider = rootEntryProvider,
    )

    MiuixTheme(controller = themeController) {
        NavDisplay(
            entries = rootEntries,
            onBack = { popRoot() },
        )
    }
}

@Composable
private fun DeviceMenuPopup(
    show: Boolean,
    canClearLogs: Boolean,
    onDismissRequest: () -> Unit,
    onReorderDevices: () -> Unit,
    onOpenVirtualButtonOrder: () -> Unit,
    onClearLogs: () -> Unit,
) {
    SuperListPopup(
        show = show,
        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = onDismissRequest,
        enableWindowDim = false,
    ) {
        ListPopupColumn {
            DeviceMenuPopupItem(
                text = "快速设备排序",
                optionSize = 3,
                index = 0,
                onClick = onReorderDevices,
            )
            DeviceMenuPopupItem(
                text = "虚拟按钮排序",
                optionSize = 3,
                index = 1,
                onClick = onOpenVirtualButtonOrder,
            )
            DeviceMenuPopupItem(
                text = "清空日志",
                optionSize = 3,
                index = 2,
                enabled = canClearLogs,
                onClick = onClearLogs,
            )
        }
    }
}

@Composable
private fun DeviceMenuPopupItem(
    text: String,
    optionSize: Int,
    index: Int,
    enabled: Boolean = true,
    // TODO: (Int) -> Unit
    onClick: () -> Unit,
) {
    if (enabled) {
        DropdownImpl(
            text = text,
            optionSize = optionSize,
            isSelected = false,
            index = index,
            onSelectedIndexChange = { onClick() },
        )
        return
    }

    val additionalTopPadding = if (index == 0) UiSpacing.PopupHorizontal else UiSpacing.PageItem
    val additionalBottomPadding =
        if (index == optionSize - 1) UiSpacing.PopupHorizontal else UiSpacing.PageItem
    Text(
        text = text,
        fontSize = MiuixTheme.textStyles.body1.fontSize,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiSpacing.PopupHorizontal)
            .padding(top = additionalTopPadding, bottom = additionalBottomPadding),
    )
}
