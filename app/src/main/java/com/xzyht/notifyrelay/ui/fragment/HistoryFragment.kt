package com.xzyht.notifyrelay.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.xzyht.notifyrelay.ui.common.ProvideNavigationEventDispatcherOwner
import com.xzyht.notifyrelay.ui.pages.NotificationHistoryScreen
import com.xzyht.notifyrelay.ui.pages.UISuperIslandHistory
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme

class HistoryFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ProvideNavigationEventDispatcherOwner {
                    MiuixTheme {
                        HistoryScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen() {
    // 创建协程作用域用于Tab点击事件
    val coroutineScope = rememberCoroutineScope()

    // TabRow相关状态
    val tabTitles = listOf("通知历史", "超级岛历史")

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
                    0 -> {
                        // 通知历史 Tab
                        NotificationHistoryScreen()
                    }
                    1 -> {
                        // 超级岛历史 Tab
                        UISuperIslandHistory()
                    }
                }
        }
    }
}
