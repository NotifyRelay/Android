package com.xzyht.notifyrelay.ui.pages.remoteapps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.ui.viewmodel.LocalAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import notifyrelay.base.util.Logger
import notifyrelay.base.util.image.toBitmapOrDefault
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LocalAppsContent(
    apps: List<LocalAppInfo>,
    isLoading: Boolean,
    error: String?,
    onAppClick: (LocalAppInfo) -> Unit,
    onAppLongClick: (LocalAppInfo) -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val context = LocalContext.current
    val packageManager = context.packageManager

    val iconCache = remember { mutableStateMapOf<String, ImageBitmap?>() }

    LaunchedEffect(apps) {
        apps.forEach { app ->
            if (!iconCache.containsKey(app.packageName)) {
                iconCache[app.packageName] =
                    withContext(Dispatchers.IO) {
                        try {
                            val appInfo = packageManager.getApplicationInfo(app.packageName, 0)
                            val drawable = appInfo.loadIcon(packageManager)
                            drawable.toBitmapOrDefault(1).asImageBitmap()
                        } catch (e: Exception) {
                            Logger.e("LocalAppsContent", "Failed to load icon for ${app.packageName}", e)
                            null
                        }
                    }
            }
        }
    }

    when {
        error != null -> {
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
                        text = error,
                        color = colorScheme.error,
                    )
                }
            }
        }
        isLoading -> {
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
