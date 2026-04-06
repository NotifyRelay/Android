package com.xzyht.notifyrelay.ui.pages

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xzyht.notifyrelay.servers.appslist.RemoteAppInfo
import com.xzyht.notifyrelay.ui.ViewModels.RemoteAppsViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RemoteAppsPage(
    deviceUuid: String,
    deviceIp: String,
    modifier: Modifier = Modifier,
    viewModel: RemoteAppsViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showMenuForApp by remember { mutableStateOf<RemoteAppInfo?>(null) }
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles

    LaunchedEffect(deviceUuid) {
        viewModel.loadApps(context, deviceUuid)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { newValue ->
                    searchQuery = newValue
                    viewModel.searchApps(newValue)
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                label = "搜索应用",
                singleLine = true
            )
            IconButton(
                onClick = { viewModel.refreshApps(context) },
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = MiuixIcons.Settings,
                        contentDescription = "刷新"
                    )
                }
            }
        }

        when {
            state.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.error ?: "加载失败",
                            color = colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        top.yukonga.miuix.kmp.basic.Button(
                            onClick = { viewModel.refreshApps(context) }
                        ) {
                            Text("重试")
                        }
                    }
                }
            }
            state.isEmpty -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = MiuixIcons.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = colorScheme.onSurfaceSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "暂无应用数据",
                            color = colorScheme.onSurfaceSecondary
                        )
                    }
                }
            }
            else -> {
                val filteredApps = if (searchQuery.isBlank()) {
                    state.apps
                } else {
                    state.apps.filter {
                        it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
                    }
                }
                val pinnedApps = filteredApps.filter { it.isPinned }
                val regularApps = filteredApps.filter { !it.isPinned }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 80.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (pinnedApps.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "置顶应用",
                                style = textStyles.main,
                                color = colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(pinnedApps, key = { "pinned_${it.packageName}" }) { app ->
                            AppItem(
                                app = app,
                                onClick = { viewModel.openApp(context, app, deviceIp) },
                                onLongClick = { showMenuForApp = app }
                            )
                        }
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = colorScheme.dividerLine
                            )
                        }
                    }
                    if (regularApps.isNotEmpty() && pinnedApps.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "全部应用",
                                style = textStyles.main,
                                color = colorScheme.onSurfaceSecondary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                    items(regularApps, key = { it.packageName }) { app ->
                        AppItem(
                            app = app,
                            onClick = { viewModel.openApp(context, app, deviceIp) },
                            onLongClick = { showMenuForApp = app }
                        )
                    }
                }
            }
        }
    }

    showMenuForApp?.let { app ->
        AppContextMenu(
            app = app,
            onDismiss = { showMenuForApp = null },
            onPin = { viewModel.pinApp(context, app.packageName) },
            onUnpin = { viewModel.unpinApp(context, app.packageName) }
        )
    }
}

@Composable
private fun AppItem(
    app: RemoteAppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles

    val bitmap = remember(app.iconBytes) {
        app.iconBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(56.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = app.appName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        imageVector = MiuixIcons.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = colorScheme.onSurfaceSecondary
                    )
                }
            }
            if (app.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
            if (app.isPinned) {
                Icon(
                    imageVector = MiuixIcons.Settings,
                    contentDescription = "已置顶",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .background(
                            colorScheme.primary,
                            CircleShape
                        )
                        .padding(2.dp),
                    tint = colorScheme.onPrimary
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.appName,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            color = colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AppContextMenu(
    app: RemoteAppInfo,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.defaultColors(
            color = colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = app.appName,
                style = textStyles.title3
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = app.packageName,
                style = textStyles.body2,
                color = colorScheme.onSurfaceSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = "取消",
                    onClick = onDismiss
                )
                Spacer(modifier = Modifier.width(8.dp))
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = if (app.isPinned) "取消置顶" else "置顶",
                    onClick = {
                        if (app.isPinned) onUnpin() else onPin()
                        onDismiss()
                    }
                )
            }
        }
    }
}
