package com.xzyht.notifyrelay.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xzyht.notifyrelay.ui.common.SetupSystemBars
import com.xzyht.notifyrelay.ui.pages.ClipboardSyncPage
import com.xzyht.notifyrelay.ui.pages.UIAbout
import com.xzyht.notifyrelay.ui.pages.UIChatTest
import com.xzyht.notifyrelay.ui.pages.UISuperIslandHistory
import com.xzyht.notifyrelay.ui.pages.UISuperIslandSettings
import com.xzyht.notifyrelay.ui.screen.DeviceListScreenContent
import com.xzyht.notifyrelay.ui.screen.ScrcpyAdvancedScreen
import com.xzyht.notifyrelay.ui.screen.ScrcpyVirtualButtonOrderScreen
import com.xzyht.notifyrelay.ui.screen.SettingsScrcpyScreen
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyRootScreen
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyScreenHost
import notifyrelay.base.util.ThemeSettingsManager
import notifyrelay.shared.ui.common.NotifyRelayTheme
import notifyrelay.shared.ui.navigation.LocalNavigator
import notifyrelay.shared.ui.navigation.Navigator
import notifyrelay.shared.ui.navigation.Route
import notifyrelay.shared.ui.pages.MusicControlPage
import notifyrelay.shared.ui.pages.history.NotificationHistoryScreen
import notifyrelay.shared.ui.screen.DeviceForwardScreen
import notifyrelay.shared.ui.screen.HistoryScreen
import notifyrelay.shared.ui.screen.MainScreen
import notifyrelay.shared.ui.screen.SettingsAboutScreen
import notifyrelay.shared.ui.screen.SettingsAppearanceScreen
import notifyrelay.shared.ui.screen.SettingsLocalFilterScreen
import notifyrelay.shared.ui.screen.SettingsRemoteFilterScreen
import notifyrelay.shared.ui.screen.SettingsScreen
import notifyrelay.shared.ui.screen.SettingsSuperIslandScreen
import notifyrelay.shared.ui.viewmodel.RemoteFilterViewModel
import notifyrelay.shared.ui.viewmodel.devicePortsViewModel
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `MainActivity` 的 Compose 内容根：主题（含跟随系统/手动切换）与 Miuix 导航图。
 *
 * 仅做声明式搬移，行为与原 `setContent { ... }` 内联实现一致。
 */
@Composable
internal fun MainActivityContent(navigator: Navigator) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val themeBaseIndex = remember { mutableIntStateOf(ThemeSettingsManager.getThemeBaseIndex(context)) }

    val isDarkTheme =
        when (themeBaseIndex.intValue) {
            ThemeSettingsManager.THEME_LIGHT -> false
            ThemeSettingsManager.THEME_DARK -> true
            else -> systemDarkTheme
        }

    DisposableEffect(context) {
        val listener =
            ThemeSettingsManager.ThemeChangeListener { newBaseIndex ->
                themeBaseIndex.intValue = newBaseIndex
            }
        ThemeSettingsManager.addThemeChangeListener(context, listener)
        onDispose {
            ThemeSettingsManager.removeThemeChangeListener(context, listener)
        }
    }

    NotifyRelayTheme(darkTheme = isDarkTheme) {
        val colorScheme = MiuixTheme.colorScheme
        SetupSystemBars(isDarkTheme)

        val filterViewModel =
            viewModel<RemoteFilterViewModel>(
                factory =
                    object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                            RemoteFilterViewModel(
                                notifyrelay.domain.feature.notification.filter.RemoteFilterPortRegistry.current
                                    ?: error("RemoteFilterPort 未注册"),
                                notifyrelay.shared.feature.appslist.LocalAppsPortRegistry.current
                                    ?: error("LocalAppsPort 未注册"),
                                notifyrelay.shared.feature.appslist.AppIconPortRegistry.current
                                    ?: error("AppIconPort 未注册"),
                            ) as T
                    },
            )
        CompositionLocalProvider(
            LocalNavigator provides navigator,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(colorScheme.background),
            ) {
                NavDisplay(
                    backStack = navigator.backStack,
                    onBack = {
                        if (navigator.backStackSize() > 1) {
                            navigator.pop()
                        }
                    },
                ) {
                    entry<Route.Main> {
                        MainScreen(
                            navigator = navigator,
                            deviceListContent = { args ->
                                DeviceListScreenContent(
                                    isLandscape = args.isLandscape,
                                    discoveryEnabled = args.discoveryEnabled,
                                    onDiscoveryChange = args.onDiscoveryChange,
                                    allDevices = args.allDevices,
                                    authedDeviceUuids = args.authedDeviceUuids,
                                    deviceStates = args.deviceStates,
                                    unauthedDevices = args.unauthedDevices,
                                    selectedDevice = args.selectedDevice,
                                    onSelectDevice = args.onSelectDevice,
                                    onRequestDelete = args.onRequestDelete,
                                    onShowRejectedDialog = args.onShowRejectedDialog,
                                    localBatteryLevel = args.localBatteryLevel,
                                    colorScheme = MiuixTheme.colorScheme,
                                    textStyles = MiuixTheme.textStyles,
                                )
                            },
                            historyContent = {
                                HistoryScreen(
                                    navigator,
                                    notificationHistory = { NotificationHistoryScreen() },
                                    superIslandHistory = { UISuperIslandHistory() },
                                    chatTest = { UIChatTest(deviceManager = devicePortsViewModel().chatPort()) },
                                )
                            },
                            forwardContent = { DeviceForwardScreen(filterViewModel = filterViewModel, clipboardSync = { ClipboardSyncPage() }, musicControl = { MusicControlPage() }, scrcpyScreen = { device, onAdvanced -> ScrcpyScreenHost(startScreen = ScrcpyRootScreen.Device, selectedDevice = device?.let { notifyrelay.data.model.SelectedDeviceInfo(it.displayName, it.ip, it.deviceType) }, onOpenAdvanced = onAdvanced) }) },
                            settingsContent = { SettingsScreen() },
                        )
                    }
                    entry<Route.History> {
                        HistoryScreen(
                            navigator,
                            notificationHistory = { NotificationHistoryScreen() },
                            superIslandHistory = { UISuperIslandHistory() },
                            chatTest = { UIChatTest(deviceManager = devicePortsViewModel().chatPort()) },
                        )
                    }
                    entry<Route.Settings> { SettingsScreen() }
                    entry<Route.ScrcpyAdvanced> { ScrcpyAdvancedScreen(navigator) }
                    entry<Route.ScrcpyVirtualButtonOrder> { ScrcpyVirtualButtonOrderScreen(navigator) }
                    entry<Route.SettingsRemoteFilter> {
                        SettingsRemoteFilterScreen()
                    }
                    entry<Route.SettingsLocalFilter> {
                        SettingsLocalFilterScreen()
                    }
                    entry<Route.SettingsSuperIsland> {
                        SettingsSuperIslandScreen { UISuperIslandSettings() }
                    }
                    entry<Route.SettingsScrcpy> {
                        SettingsScrcpyScreen()
                    }
                    entry<Route.SettingsAbout> {
                        SettingsAboutScreen { UIAbout() }
                    }
                    entry<Route.SettingsAppearance> {
                        SettingsAppearanceScreen()
                    }
                }
            }
        }
    }
}
