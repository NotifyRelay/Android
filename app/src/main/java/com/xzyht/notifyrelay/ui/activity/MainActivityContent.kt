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
import com.xzyht.notifyrelay.ui.common.NotifyRelayTheme
import com.xzyht.notifyrelay.ui.common.SetupSystemBars
import com.xzyht.notifyrelay.ui.pages.NotificationHistoryScreen
import com.xzyht.notifyrelay.ui.pages.UIAbout
import com.xzyht.notifyrelay.ui.pages.UIChatTest
import com.xzyht.notifyrelay.ui.pages.UISuperIslandHistory
import com.xzyht.notifyrelay.ui.pages.UISuperIslandSettings
import com.xzyht.notifyrelay.ui.screen.MainScreen
import com.xzyht.notifyrelay.ui.screen.ScrcpyAdvancedScreen
import com.xzyht.notifyrelay.ui.screen.ScrcpyVirtualButtonOrderScreen
import com.xzyht.notifyrelay.ui.screen.SettingsScrcpyScreen
import notifyrelay.base.util.ThemeSettingsManager
import notifyrelay.shared.ui.navigation.LocalNavigator
import notifyrelay.shared.ui.navigation.Navigator
import notifyrelay.shared.ui.navigation.Route
import notifyrelay.shared.ui.screen.HistoryScreen
import notifyrelay.shared.ui.screen.SettingsAboutScreen
import notifyrelay.shared.ui.screen.SettingsAppearanceScreen
import notifyrelay.shared.ui.screen.SettingsLocalFilterScreen
import notifyrelay.shared.ui.screen.SettingsRemoteFilterScreen
import notifyrelay.shared.ui.screen.SettingsScreen
import notifyrelay.shared.ui.screen.SettingsSuperIslandScreen
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
                    entry<Route.Main> { MainScreen(navigator) }
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
