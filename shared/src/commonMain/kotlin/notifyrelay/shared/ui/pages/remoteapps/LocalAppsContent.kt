package notifyrelay.shared.ui.pages.remoteapps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import notifyrelay.base.log.Logger
import notifyrelay.domain.feature.appslist.model.LocalAppInfo
import notifyrelay.domain.feature.appslist.model.appMatchesQuery
import notifyrelay.shared.feature.appslist.LocalAppsPortRegistry
import notifyrelay.shared.ui.viewmodel.AppsState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings

/**
 * 本机应用网格。
 *
 * 三类空态与网格参数与远端列表共用，见 [AppsErrorState]、[AppsLoadingState]、[AppsEmptyState]。
 *
 * @param state 应用列表状态。
 * @param onAppClick 单击应用。
 * @param onAppLongClick 长按应用。
 */
@Composable
fun LocalAppsContent(
    state: AppsState<LocalAppInfo>,
    onAppClick: (LocalAppInfo) -> Unit,
    onAppLongClick: (LocalAppInfo) -> Unit,
) {
    val appsPort = LocalAppsPortRegistry.current ?: error("LocalAppsPort 未注册")
    val apps = state.apps.filter { appMatchesQuery(it.appName, it.packageName, state.searchQuery) }
    val iconCache = remember { mutableStateMapOf<String, ImageBitmap?>() }

    LaunchedEffect(apps) {
        apps.forEach { app ->
            if (!iconCache.containsKey(app.packageName)) {
                iconCache[app.packageName] =
                    try {
                        appsPort.iconFor(app.packageName)
                    } catch (e: Exception) {
                        Logger.e("LocalAppsContent", "Failed to load icon for " + app.packageName, e)
                        null
                    }
            }
        }
    }

    when {
        state.error != null -> AppsErrorState(state.error)
        state.isLoading -> AppsLoadingState()
        apps.isEmpty() -> AppsEmptyState("暂无应用", MiuixIcons.Settings)
        else ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 80.dp),
                contentPadding = AppsGridContentPadding,
                horizontalArrangement = Arrangement.spacedBy(AppsGridHorizontalSpacing),
                verticalArrangement = Arrangement.spacedBy(AppsGridVerticalSpacing),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    LocalAppItem(
                        app = app,
                        iconBitmap = iconCache[app.packageName],
                        onClick = { onAppClick(app) },
                        onLongClick = { onAppLongClick(app) },
                    )
                }
            }
    }
}
