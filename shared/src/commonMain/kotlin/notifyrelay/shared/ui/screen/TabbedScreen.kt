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
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶部页签 + 可左右滑动的分页容器。
 *
 * 外壳在所有使用处一致：背景、内边距、页签样式、滚动联动。差异只在每页的内容，
 * 因此页签标题与内容渲染由调用方给出。
 *
 * @param tabTitles 页签标题，顺序即页序。
 * @param content 按页下标渲染内容。
 */
@Composable
fun TabbedScreen(
    tabTitles: List<String>,
    content: @Composable (Int) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
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
        ) { page ->
            content(page)
        }
    }
}
