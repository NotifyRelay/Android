package com.xzyht.notifyrelay.ui.screen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.ui.DeveloperModeActivity
import com.xzyht.notifyrelay.ui.navigation.Navigator
import com.xzyht.notifyrelay.ui.pages.UILocalFilter
import com.xzyht.notifyrelay.ui.pages.UIRemoteFilter
import com.xzyht.notifyrelay.ui.pages.UISuperIslandSettings
import com.xzyht.notifyrelay.ui.pages.UIAbout
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置页面屏幕
 * 纯 Compose 实现
 */
@Composable
fun SettingsScreen(
    navigator: Navigator
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val handleDeveloperModeTriggered = {
        val intent = Intent(context, DeveloperModeActivity::class.java)
        context.startActivity(intent)
    }

    val tabTitles = listOf("远程过滤", "本地过滤", "超级岛", "关于")
    val pagerState = rememberPagerState(initialPage = 0) { tabTitles.size }
    val selectedTabIndex = pagerState.currentPage
    val colorScheme = MiuixTheme.colorScheme

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
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentAlignment = Alignment.TopStart
            ) {
                when (page) {
                    0 -> UIRemoteFilter()
                    1 -> UILocalFilter()
                    2 -> UISuperIslandSettings()
                    3 -> UIAbout(onDeveloperModeTriggered = handleDeveloperModeTriggered)
                }
            }
        }
    }
}
