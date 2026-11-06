package notifyrelay.shared.ui.pages.remoteapps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import notifyrelay.domain.feature.appslist.model.RemoteAppInfo
import notifyrelay.domain.feature.appslist.model.appMatchesQuery
import notifyrelay.shared.ui.port.LocalBitmapPort
import notifyrelay.shared.ui.viewmodel.AppsState
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 远端应用网格。
 *
 * 三类空态与网格参数与本机列表共用，见 [AppsErrorState]、[AppsLoadingState]、[AppsEmptyState]。
 * 置顶应用单独成段，与其余应用之间用标题与分隔线隔开。
 *
 * @param state 应用列表状态。
 * @param onAppClick 单击应用。
 * @param onAppLongClick 长按应用。
 * @param onRefresh 失败后的重试动作。
 */
@Composable
fun RemoteAppsContent(
    state: AppsState<RemoteAppInfo>,
    onAppClick: (RemoteAppInfo) -> Unit,
    onAppLongClick: (RemoteAppInfo) -> Unit,
    onRefresh: () -> Unit,
) {
    val bitmapPort = LocalBitmapPort.current
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val iconCache = remember { mutableStateMapOf<String, ImageBitmap?>() }

    LaunchedEffect(state.apps) {
        state.apps.forEach { app ->
            val iconBytes = app.iconBytes
            if (iconBytes != null && !iconCache.containsKey(app.packageName)) {
                iconCache[app.packageName] =
                    withContext(Dispatchers.Default) {
                        bitmapPort.decodeByteArray(iconBytes)
                    }
            }
        }
    }

    val filteredApps = state.apps.filter { appMatchesQuery(it.appName, it.packageName, state.searchQuery) }
    val pinnedApps = filteredApps.filter { it.isPinned }
    val regularApps = filteredApps.filter { !it.isPinned }

    when {
        state.error != null -> AppsErrorState(state.error, onRetry = onRefresh)
        state.isLoading -> AppsLoadingState("加载中")
        state.isEmpty -> AppsEmptyState("暂无应用数据", MiuixIcons.Settings)
        filteredApps.isEmpty() -> AppsEmptyState("无匹配结果", MiuixIcons.Settings)
        else ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 80.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(AppsGridHorizontalSpacing),
                verticalArrangement = Arrangement.spacedBy(AppsGridVerticalSpacing),
            ) {
                if (pinnedApps.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "置顶应用",
                            style = textStyles.main,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(pinnedApps, key = { "pinned_${'$'}{it.packageName}" }) { app ->
                        AppItem(
                            app = app,
                            iconBitmap = iconCache[app.packageName],
                            onClick = { onAppClick(app) },
                            onLongClick = { onAppLongClick(app) },
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = colorScheme.dividerLine,
                        )
                    }
                }
                if (regularApps.isNotEmpty() && pinnedApps.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "全部应用",
                            style = textStyles.main,
                            color = colorScheme.onSurfaceSecondary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                items(regularApps, key = { it.packageName }) { app ->
                    AppItem(
                        app = app,
                        iconBitmap = iconCache[app.packageName],
                        onClick = { onAppClick(app) },
                        onLongClick = { onAppLongClick(app) },
                    )
                }
            }
    }
}
