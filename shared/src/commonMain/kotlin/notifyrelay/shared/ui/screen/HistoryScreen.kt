package notifyrelay.shared.ui.screen

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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import notifyrelay.shared.ui.navigation.Navigator
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 历史页面屏幕
 * 纯 Compose 实现
 */
@Composable
fun HistoryScreen(
    navigator: Navigator,
    notificationHistory: @Composable () -> Unit,
    superIslandHistory: @Composable () -> Unit,
    chatTest: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val tabTitles = listOf("通知历史", "超级岛历史", "聊天测试")
    val pagerState = rememberPagerState(initialPage = 0) { tabTitles.size }
    val selectedTabIndex = pagerState.currentPage
    val colorScheme = MiuixTheme.colorScheme

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .padding(12.dp),
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
            colors =
                TabRowDefaults.tabRowColors(
                    backgroundColor = colorScheme.surface,
                    contentColor = colorScheme.onSurface,
                    selectedBackgroundColor = colorScheme.primary,
                    selectedContentColor = colorScheme.onPrimary,
                ),
            minWidth = 100.dp,
            height = 48.dp,
            cornerRadius = 16.dp,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (it) {
                0 -> notificationHistory()
                1 -> superIslandHistory()
                2 -> chatTest()
            }
        }
    }
}
