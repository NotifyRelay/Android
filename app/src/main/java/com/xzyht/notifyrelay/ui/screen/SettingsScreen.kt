package com.xzyht.notifyrelay.ui.screen

import android.content.Intent
import androidx.compose.foundation.background
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
import com.xzyht.notifyrelay.ui.navigation.LocalNavigator
import com.xzyht.notifyrelay.ui.navigation.Route
import notifyrelay.data.StorageManager
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
