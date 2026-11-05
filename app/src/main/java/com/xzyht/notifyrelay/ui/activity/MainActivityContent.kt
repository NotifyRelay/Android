package com.xzyht.notifyrelay.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.xzyht.notifyrelay.ui.activity.GuideActivity
import com.xzyht.notifyrelay.ui.common.SetupSystemBars
import com.xzyht.notifyrelay.ui.pages.ClipboardSyncPage
import com.xzyht.notifyrelay.ui.pages.UIAbout
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
import notifyrelay.shared.ui.pages.UISuperIslandSettings
import notifyrelay.shared.ui.pages.chat.UIChatTest
import notifyrelay.shared.ui.screen.DeveloperModeScreen
import notifyrelay.shared.ui.screen.DeviceForwardScreen
import notifyrelay.shared.ui.screen.DeviceListTabContent
import notifyrelay.shared.ui.screen.HistoryTabContent
import notifyrelay.shared.ui.screen.MainScreen
import notifyrelay.shared.ui.screen.SettingsAboutScreen
import notifyrelay.shared.ui.screen.SettingsAppearanceScreen
import notifyrelay.shared.ui.screen.SettingsDeveloperModeScreen
import notifyrelay.shared.ui.screen.SettingsLocalFilterScreen
import notifyrelay.shared.ui.screen.SettingsRemoteFilterScreen
import notifyrelay.shared.ui.screen.SettingsScreen
import notifyrelay.shared.ui.screen.SettingsSuperIslandScreen
import notifyrelay.shared.ui.viewmodel.devicePortsViewModel
import notifyrelay.shared.ui.viewmodel.remoteFilterViewModel
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

        val filterViewModel = remoteFilterViewModel()
        // chatPort 在组合期取一次，发送回调内复用（ViewModel 取用本身是 @Composable）
        val chatPort = devicePortsViewModel().chatPort()

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
                            deviceListContent = { args -> DeviceListTabContent(args) },
                            historyContent = {
                                HistoryTabContent(navigator) {
                                    UIChatTest(
                                        deviceManager = chatPort,
                                        onSendMessage = { input ->
                                            com.xzyht.notifyrelay.sync.MessageSender.sendChatMessage(
                                                context,
                                                input,
                                                chatPort,
                                            )
                                        },
                                    )
                                }
                            },
                            forwardContent = { DeviceForwardScreen(filterViewModel = filterViewModel, clipboardSync = { ClipboardSyncPage() }, musicControl = { MusicControlPage() }, scrcpyScreen = { device, onAdvanced -> ScrcpyScreenHost(startScreen = ScrcpyRootScreen.Device, selectedDevice = device?.let { notifyrelay.data.model.SelectedDeviceInfo(it.displayName, it.ip, it.deviceType) }, onOpenAdvanced = onAdvanced) }) },
                            settingsContent = { SettingsScreen() },
                        )
                    }
                    entry<Route.History> {
                        HistoryTabContent(navigator) {
                            UIChatTest(
                                deviceManager = chatPort,
                                onSendMessage = { input ->
                                    com.xzyht.notifyrelay.sync.MessageSender.sendChatMessage(
                                        context,
                                        input,
                                        chatPort,
                                    )
                                },
                            )
                        }
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
                        val showTestDialog = remember { mutableStateOf(false) }
                        SettingsSuperIslandScreen {
                            UISuperIslandSettings(onOpenTestBranch = { showTestDialog.value = true })
                        }
                        com.xzyht.notifyrelay.ui.dialog
                            .SuperIslandTestDialog(showTestDialog, context)
                    }
                    entry<Route.SettingsDeveloperMode> {
                        // 通知字段转储为 Android 专有页面：经本地状态在开发者选项内切换显示
                        var showDumpPage by remember { mutableStateOf(false) }
                        if (showDumpPage) {
                            notifyrelay.shared.ui.common.ScrollableTopAppBarPage(
                                title = "通知字段转储",
                                onBack = { showDumpPage = false },
                            ) {
                                com.xzyht.notifyrelay.ui.devtools
                                    .NotificationFieldDumpPage()
                            }
                        } else {
                            SettingsDeveloperModeScreen {
                                DeveloperModeScreen(
                                    onOpenGuideBranch = { branch ->
                                        val intent =
                                            notifyrelay.base.util.IntentUtils
                                                .createIntent(context, GuideActivity::class.java)
                                        intent.putExtra("fromInternal", true)
                                        intent.putExtra("forceBranch", branch)
                                        notifyrelay.base.util.IntentUtils
                                            .startActivity(context, intent, true)
                                    },
                                    onOpenDumpPage = { showDumpPage = true },
                                )
                            }
                        }
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
