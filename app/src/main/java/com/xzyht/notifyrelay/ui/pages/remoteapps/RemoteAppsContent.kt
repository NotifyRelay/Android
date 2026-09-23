package com.xzyht.notifyrelay.ui.pages.remoteapps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.feature.appslist.model.RemoteAppInfo
import com.xzyht.notifyrelay.feature.appslist.model.RemoteAppsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun RemoteAppsContent(
    state: RemoteAppsState,
    searchQuery: String,
    onAppClick: (RemoteAppInfo) -> Unit,
    onAppLongClick: (RemoteAppInfo) -> Unit,
    onRefresh: () -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles

    val iconCache = remember { mutableStateMapOf<String, Bitmap?>() }

    LaunchedEffect(state.apps) {
        state.apps.forEach { app ->
            if (app.iconBytes != null && !iconCache.containsKey(app.packageName)) {
                iconCache[app.packageName] =
                    withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(app.iconBytes, 0, app.iconBytes.size)
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
                        text = state.error ?: "加载失败",
                        color = colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    top.yukonga.miuix.kmp.basic.Button(
                        onClick = onRefresh,
                    ) {
                        Text("重试")
                    }
                }
            }
        }
        state.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "加载中",
                        color = colorScheme.onSurfaceSecondary,
                    )
                }
            }
        }
        state.isEmpty -> {
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
                        text = "暂无应用数据",
                        color = colorScheme.onSurfaceSecondary,
                    )
                }
            }
        }
        else -> {
            val filteredApps =
                if (searchQuery.isBlank()) {
                    state.apps
                } else {
                    state.apps.filter {
                        it.appName.contains(searchQuery, ignoreCase = true) ||
                            it.packageName.contains(searchQuery, ignoreCase = true)
                    }
                }

            if (filteredApps.isEmpty()) {
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
                            text = "无匹配结果",
                            color = colorScheme.onSurfaceSecondary,
                        )
                    }
                }
            } else {
                val pinnedApps = filteredApps.filter { it.isPinned }
                val regularApps = filteredApps.filter { !it.isPinned }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 80.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (pinnedApps.isNotEmpty()) {
                        item(span = {
                            androidx.compose.foundation.lazy.grid
                                .GridItemSpan(maxLineSpan)
                        }) {
                            Text(
                                text = "置顶应用",
                                style = textStyles.main,
                                color = colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        items(pinnedApps, key = { "pinned_${it.packageName}" }) { app ->
                            AppItem(
                                app = app,
                                iconBitmap = iconCache[app.packageName],
                                onClick = { onAppClick(app) },
                                onLongClick = { onAppLongClick(app) },
                            )
                        }
                        item(span = {
                            androidx.compose.foundation.lazy.grid
                                .GridItemSpan(maxLineSpan)
                        }) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = colorScheme.dividerLine,
                            )
                        }
                    }
                    if (regularApps.isNotEmpty() && pinnedApps.isNotEmpty()) {
                        item(span = {
                            androidx.compose.foundation.lazy.grid
                                .GridItemSpan(maxLineSpan)
                        }) {
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
    }
}
