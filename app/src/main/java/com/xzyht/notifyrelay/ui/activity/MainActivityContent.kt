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
import com.xzyht.notifyrelay.ui.screen.HistoryScreen
import com.xzyht.notifyrelay.ui.screen.MainScreen
import com.xzyht.notifyrelay.ui.screen.ScrcpyAdvancedScreen
import com.xzyht.notifyrelay.ui.screen.ScrcpyVirtualButtonOrderScreen
import com.xzyht.notifyrelay.ui.screen.SettingsAboutScreen
import com.xzyht.notifyrelay.ui.screen.SettingsAppearanceScreen
import com.xzyht.notifyrelay.ui.screen.SettingsLocalFilterScreen
import com.xzyht.notifyrelay.ui.screen.SettingsRemoteFilterScreen
import com.xzyht.notifyrelay.ui.screen.SettingsScrcpyScreen
import com.xzyht.notifyrelay.ui.screen.SettingsScreen
import com.xzyht.notifyrelay.ui.screen.SettingsSuperIslandScreen
import notifyrelay.base.util.ThemeSettingsManager
import notifyrelay.shared.ui.navigation.LocalNavigator
import notifyrelay.shared.ui.navigation.Navigator
import notifyrelay.shared.ui.navigation.Route
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
                    entry<Route.History> { HistoryScreen(navigator) }
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
                        SettingsSuperIslandScreen()
                    }
                    entry<Route.SettingsScrcpy> {
                        SettingsScrcpyScreen()
                    }
                    entry<Route.SettingsAbout> {
                        SettingsAboutScreen()
                    }
                    entry<Route.SettingsAppearance> {
                        SettingsAppearanceScreen()
                    }
                }
            }
        }
    }
}
