package com.xzyht.notifyrelay.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.ui.activity.DeveloperModeActivity
import com.xzyht.notifyrelay.ui.common.ScrollableTopAppBarPage
import com.xzyht.notifyrelay.ui.navigation.LocalNavigator
import com.xzyht.notifyrelay.ui.navigation.Route
import com.xzyht.notifyrelay.ui.pages.UIAbout
import com.xzyht.notifyrelay.ui.pages.UIAppearance
import com.xzyht.notifyrelay.ui.pages.UILocalFilter
import com.xzyht.notifyrelay.ui.pages.UIRemoteFilter
import com.xzyht.notifyrelay.ui.pages.UISuperIslandSettings
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyRootScreen
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyScreenHost
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyUiViewModel
import notifyrelay.base.util.Logger
import notifyrelay.data.StorageManager
import notifyrelay.data.config.ScrcpyPreferenceKeys
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置页面屏幕
 * 传统 Preference 多子页类型：主页为设置项列表，点击进入对应子页
 */
@Composable
fun SettingsScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme
    var isDeveloperModeEnabled by remember {
        mutableStateOf(StorageManager.getBoolean(context, "developer_mode_enabled", false))
    }

    // 问题 7 修复：开发者模式入口在"关于"子页激活后返回时不会刷新。
    // ON_RESUME 方案在本导航架构（Navigator 为 rememberSaveable + 栈内重组，路由 push/pop
    // 不改变 Activity lifecycle）下无效。改用 LaunchedEffect 监听 backStack.size：
    // 从"关于"返回时栈大小变小，触发重新读取 developer_mode_enabled。
    LaunchedEffect(navigator.backStack.size) {
        isDeveloperModeEnabled = StorageManager.getBoolean(context, "developer_mode_enabled", false)
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
                onClick = {
                    context.startActivity(Intent(context, DeveloperModeActivity::class.java))
                },
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
private fun SettingsSubPage(
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
fun SettingsSuperIslandScreen() = SettingsSubPage("超级岛") { UISuperIslandSettings() }

/**
 * 屏幕镜像设置子页（含 TopAppBar）
 */
@Composable
fun SettingsScrcpyScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val serverPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.onFailure { e ->
                Logger.e("SettingsScrcpyScreen", "takePersistableUriPermission 失败: uri=$uri", e)
            }
            runCatching {
                val uriString = uri.toString()
                StorageManager.putString(
                    context,
                    ScrcpyPreferenceKeys.CUSTOM_SERVER_URI,
                    uriString,
                    StorageManager.PrefsType.SCRCPY,
                )
                val app = context.applicationContext as android.app.Application
                ScrcpyUiViewModel.getInstance(app).customServerUri = uriString
            }.onFailure { e ->
                Logger.e("SettingsScrcpyScreen", "scrcpy server URI 保存失败: uri=$uri", e)
            }
        }
    SettingsSubPage("屏幕镜像") {
        ScrcpyScreenHost(
            startScreen = ScrcpyRootScreen.Settings,
            onPickServer = { serverPicker.launch(arrayOf("application/java-archive", "application/octet-stream", "*/*")) },
            onExit = { navigator.pop() },
        )
    }
}

@Composable
fun SettingsAboutScreen() = SettingsSubPage("关于") { UIAbout() }

@Composable
fun SettingsAppearanceScreen() = SettingsSubPage("外观") { UIAppearance() }
