package com.xzyht.notifyrelay.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.feature.notification.backend.RemoteFilterConfig
import com.xzyht.notifyrelay.ui.navigation.Navigator
import com.xzyht.notifyrelay.ui.pages.ClipboardSyncPage
import com.xzyht.notifyrelay.ui.pages.MusicControlPage
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设备转发页面屏幕
 * 纯 Compose 实现
 */
@Composable
fun DeviceForwardScreen(
    navigator: Navigator
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        if (!RemoteFilterConfig.isLoaded) {
            RemoteFilterConfig.load(context)
            RemoteFilterConfig.isLoaded = true
        }
    }
    
    val tabTitles = listOf("剪贴板同步", "音乐控制")
    val pagerState = rememberPagerState(initialPage = 0) { tabTitles.size }
    val selectedTabIndex = pagerState.currentPage
    val colorScheme = MiuixTheme.colorScheme
    
    val selectedDeviceState = GlobalSelectedDeviceHolder.current()
    selectedDeviceState.value

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
                    0 -> ClipboardSyncPage()
                    1 -> MusicControlPage()
                }
            }
        }
    }
}
