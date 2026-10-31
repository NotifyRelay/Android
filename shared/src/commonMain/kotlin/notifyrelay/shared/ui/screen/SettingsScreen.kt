package notifyrelay.shared.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import notifyrelay.shared.ui.common.ScrollableTopAppBarPage
import notifyrelay.shared.ui.navigation.LocalNavigator
import notifyrelay.shared.ui.navigation.Route
import notifyrelay.shared.ui.pages.UIAppearance
import notifyrelay.shared.ui.pages.UILocalFilter
import notifyrelay.shared.ui.pages.UIRemoteFilter
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置页面屏幕
 * 传统 Preference 多子页类型：主页为设置项列表，点击进入对应子页
 *
 * @param showOverlayEntry 是否显示「叠加层」入口；仅桌面端为 true
 */
@Composable
fun SettingsScreen(showOverlayEntry: Boolean = false) {
    val navigator = LocalNavigator.current
    val settingsPort = LocalSettingsPort.current
    val colorScheme = MiuixTheme.colorScheme
    var isDeveloperModeEnabled by remember {
        mutableStateOf(settingsPort?.isDeveloperModeEnabled() ?: false)
    }

    // 问题 7 修复：开发者模式入口在"关于"子页激活后返回时不会刷新。
    // ON_RESUME 方案在本导航架构（Navigator 为 rememberSaveable + 栈内重组，路由 push/pop
    // 不改变 Activity lifecycle）下无效。改用 LaunchedEffect 监听 backStack.size：
    // 从"关于"返回时栈大小变小，触发重新读取 developer_mode_enabled。
    LaunchedEffect(navigator.backStack.size) {
        isDeveloperModeEnabled = settingsPort?.isDeveloperModeEnabled() ?: false
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp),
    ) {
        ArrowPreference(
            title = "远程过滤",
            summary = "远程通知过滤与黑白名单",
            onClick = { navigator.push(Route.SettingsRemoteFilter) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
        ArrowPreference(
            title = "本地过滤",
            summary = "本机通知过滤设置",
            onClick = { navigator.push(Route.SettingsLocalFilter) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
        ArrowPreference(
            title = "超级岛",
            summary = "超级岛读取、显示与镜像过滤",
            onClick = { navigator.push(Route.SettingsSuperIsland) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
        ArrowPreference(
            title = "屏幕镜像",
            summary = "Scrcpy 屏幕镜像设置",
            onClick = { navigator.push(Route.SettingsScrcpy) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
        ArrowPreference(
            title = "外观",
            summary = "外观模式设置",
            onClick = { navigator.push(Route.SettingsAppearance) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
        // 叠加层为桌面端专有能力：Android 端不显示该入口
        if (showOverlayEntry) {
            ArrowPreference(
                title = "叠加层",
                summary = "媒体卡、超级岛与弹幕的显示设置",
                onClick = { navigator.push(Route.SettingsOverlay) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
        }
        ArrowPreference(
            title = "关于",
            summary = "版本、更新与下载设置",
            onClick = { navigator.push(Route.SettingsAbout) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (isDeveloperModeEnabled) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
            ArrowPreference(
                title = "开发者选项",
                summary = "开发者调试设置",
                onClick = { navigator.push(Route.SettingsDeveloperMode) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/**
 * 设置子页公共容器
 * 统一 TopAppBar 与返回导航，子页只需提供标题与内容
 */
@Composable
fun SettingsSubPage(
    title: String,
    content: @Composable BoxScope.() -> Unit,
) {
    val navigator = LocalNavigator.current
    ScrollableTopAppBarPage(
        title = title,
        onBack = { navigator.pop() },
    ) {
        content()
    }
}

@Composable
fun SettingsRemoteFilterScreen() = SettingsSubPage("远程过滤") { UIRemoteFilter() }

@Composable
fun SettingsLocalFilterScreen() = SettingsSubPage("本地过滤") { UILocalFilter() }

@Composable
fun SettingsSuperIslandScreen(content: @Composable () -> Unit) = SettingsSubPage("超级岛") { content() }

@Composable
fun SettingsDeveloperModeScreen(content: @Composable () -> Unit) = SettingsSubPage("开发者选项") { content() }

/**
 * 屏幕镜像设置子页（含 TopAppBar）
 */

@Composable
fun SettingsAboutScreen(content: @Composable () -> Unit) = SettingsSubPage("关于") { content() }

@Composable
fun SettingsAppearanceScreen() = SettingsSubPage("外观") { UIAppearance() }

/** 叠加层设置子页（仅桌面端注册内容）。 */
@Composable
fun SettingsOverlayScreen(content: @Composable () -> Unit) = SettingsSubPage("叠加层") { content() }

/** 叠加层-弹幕与媒体卡子页（仅桌面端注册内容）。 */
@Composable
fun SettingsOverlayDanmakuScreen(content: @Composable () -> Unit) = SettingsSubPage("弹幕与媒体卡") { content() }

/** 叠加层-余额子页（仅桌面端注册内容）。 */
@Composable
fun SettingsOverlayBalanceScreen(content: @Composable () -> Unit) = SettingsSubPage("DeepSeek 余额") { content() }

/** 叠加层-罗技电池子页（仅桌面端注册内容）。 */
@Composable
fun SettingsOverlayLogiScreen(content: @Composable () -> Unit) = SettingsSubPage("罗技电池") { content() }

/** 叠加层-键盘子页（仅桌面端注册内容）。 */
@Composable
fun SettingsOverlayKeyboardScreen(content: @Composable () -> Unit) = SettingsSubPage("键盘监控") { content() }

/** 叠加层-时间子页（仅桌面端注册内容）。 */
@Composable
fun SettingsOverlayClockScreen(content: @Composable () -> Unit) = SettingsSubPage("时间浮窗") { content() }
