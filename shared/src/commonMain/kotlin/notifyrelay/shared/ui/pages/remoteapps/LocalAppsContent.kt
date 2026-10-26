package notifyrelay.shared.ui.pages.remoteapps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import notifyrelay.base.log.Logger
import notifyrelay.domain.feature.appslist.model.LocalAppInfo
import notifyrelay.domain.feature.appslist.model.appMatchesQuery
import notifyrelay.shared.feature.appslist.LocalAppsPortRegistry
import notifyrelay.shared.ui.viewmodel.AppsState
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LocalAppsContent(
    state: AppsState<LocalAppInfo>,
    onAppClick: (LocalAppInfo) -> Unit,
    onAppLongClick: (LocalAppInfo) -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
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
        state.error != null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.error,
                        color = colorScheme.error,
                    )
                }
            }
        }
        state.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        apps.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = MiuixIcons.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = colorScheme.onSurfaceSecondary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "暂无应用",
                        color = colorScheme.onSurfaceSecondary,
                    )
                }
            }
        }
        else -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 80.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
}
