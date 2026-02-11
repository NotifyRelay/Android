package com.xzyht.notifyrelay.feature

import android.os.Bundle
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.xzyht.notifyrelay.feature.notification.ui.filter.UILocalFilter
import com.xzyht.notifyrelay.feature.notification.ui.filter.UIRemoteFilter
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme

class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        return ComposeView(requireContext()).apply {
            setContent {
                MiuixTheme {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // 创建协程作用域用于Tab点击事件
    val coroutineScope = rememberCoroutineScope()
    
    // 简化实现，移除RemoteFilterConfig引用
    // TabRow相关状态
    val tabTitles = listOf("远程过滤", "本地过滤")
    
    // Pager相关状态 - 使用Pager状态作为唯一数据源
    val pagerState = rememberPagerState(initialPage = 0) {
        tabTitles.size
    }
    
    // 从Pager状态直接获取当前选中的Tab索引，不使用独立状态
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
        ) {
            page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentAlignment = Alignment.TopStart
            ) {
                when (page) {
                    0 -> {
                        // 远程通知过滤 Tab
                        UIRemoteFilter()
                    }
                    1 -> {
                        // 本地通知过滤 Tab
                        UILocalFilter()
                    }
                }
            }
        }
    }
}
