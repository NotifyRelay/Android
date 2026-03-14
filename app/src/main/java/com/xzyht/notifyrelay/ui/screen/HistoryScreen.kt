package com.xzyht.notifyrelay.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
import com.xzyht.notifyrelay.ui.navigation.Navigator
import com.xzyht.notifyrelay.ui.pages.NotificationHistoryScreen
import com.xzyht.notifyrelay.ui.pages.UISuperIslandHistory
import com.xzyht.notifyrelay.ui.pages.UIChatTest
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 历史页面屏幕
 * 纯 Compose 实现
 */
@Composable
fun HistoryScreen(
    navigator: Navigator
) {
    val coroutineScope = rememberCoroutineScope()
    val tabTitles = listOf("通知历史", "超级岛历史", "聊天测试")
    val pagerState = rememberPagerState(initialPage = 0) { tabTitles.size }
    val selectedTabIndex = pagerState.currentPage
    val colorScheme = MiuixTheme.colorScheme
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(12.dp)
    ) {
        TabRowWithContour(
            tabs = tabTitles,
            selectedTabIndex = selectedTabIndex,
            onTabSelected = { index ->
                coroutineScope.launch {
                    pagerState.scrollToPage(index)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = TabRowDefaults.tabRowColors(
                backgroundColor = colorScheme.surface,
                contentColor = colorScheme.onSurface,
                selectedBackgroundColor = colorScheme.primary,
                selectedContentColor = colorScheme.onPrimary
            ),
            minWidth = 100.dp,
            height = 48.dp,
            cornerRadius = 16.dp
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) {
            when (it) {
                0 -> NotificationHistoryScreen()
                1 -> UISuperIslandHistory()
                2 -> UIChatTest(
                    deviceManager = DeviceConnectionManagerSingleton.getDeviceManager(context)
                )
            }
        }
    }
}
