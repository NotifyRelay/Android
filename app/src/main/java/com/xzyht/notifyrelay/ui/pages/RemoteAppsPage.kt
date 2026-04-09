package com.xzyht.notifyrelay.ui.pages

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xzyht.notifyrelay.servers.appslist.RemoteAppInfo
import com.xzyht.notifyrelay.ui.ViewModels.LocalAppInfo
import com.xzyht.notifyrelay.ui.ViewModels.LocalAppsViewModel
import com.xzyht.notifyrelay.ui.ViewModels.RemoteAppsViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.extra.WindowDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Replace
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalDismissState

data class DisplayInfo(
    val id: Int,
    val name: String,
    val isBuiltIn: Boolean = false
)

@Composable
fun RemoteAppsPage(
    deviceUuid: String?,
    deviceIp: String?,
    modifier: Modifier = Modifier,
    remoteViewModel: RemoteAppsViewModel = viewModel(),
    localViewModel: LocalAppsViewModel = viewModel()
) {
    val context = LocalContext.current
    val isLocalMode = deviceUuid == null || deviceIp == null
    
    val remoteState by remoteViewModel.state.collectAsState()
    val localState by localViewModel.state.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var showMenuForApp by remember { mutableStateOf<Any?>(null) }
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles

    val displays = remember { mutableStateListOf<DisplayInfo>() }
    var selectedDisplayId by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(isLocalMode, deviceIp, deviceUuid) {
        if (isLocalMode) {
            localViewModel.loadApps(context)
        } else {
            remoteViewModel.loadApps(context, deviceUuid!!)
        }
    }

    DisposableEffect(isLocalMode) {
        if (isLocalMode) {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            fun updateDisplays() {
                val allDisplays = displayManager.displays
                android.util.Log.d("RemoteAppsPage", "所有显示器: ${allDisplays.map { "id=${it.displayId}, name=${it.name}, flags=${it.flags}" }}")
                
                val displayList = allDisplays.map { display ->
                    DisplayInfo(
                        id = display.displayId,
                        name = display.name.ifEmpty { 
                            if (display.displayId == 0) "内置显示器" else "显示器 ${display.displayId}" 
                        },
                        isBuiltIn = display.displayId == 0
                    )
                }.sortedBy { it.id }
                
                android.util.Log.d("RemoteAppsPage", "显示器列表: $displayList")
                displays.clear()
                displays.addAll(displayList)
                
                val validDisplayIds = displayList.map { it.id }
                if (selectedDisplayId !in validDisplayIds) {
                    selectedDisplayId = displayList.find { it.id == 0 }?.id 
                        ?: displayList.firstOrNull()?.id 
                        ?: 0
                    android.util.Log.d("RemoteAppsPage", "selectedDisplayId 不在有效列表中，重置为: $selectedDisplayId")
                }
            }
            updateDisplays()
            val displayListener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = updateDisplays()
                override fun onDisplayRemoved(displayId: Int) = updateDisplays()
                override fun onDisplayChanged(displayId: Int) = updateDisplays()
            }
            displayManager.registerDisplayListener(displayListener, null)
            onDispose {
                displayManager.unregisterDisplayListener(displayListener)
            }
        } else {
            onDispose {}
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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
                        if (isLocalMode) {
                            localViewModel.searchApps(newValue)
                        } else {
                            remoteViewModel.searchApps(newValue)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    label = "搜索应用",
                    singleLine = true
                )
                IconButton(
                    onClick = {
                        if (isLocalMode) {
                            localViewModel.loadApps(context)
                        } else {
                            remoteViewModel.refreshApps(context)
                        }
                    },
                    enabled = if (isLocalMode) !localState.isLoading else !remoteState.isLoading
                ) {
                    if (if (isLocalMode) localState.isLoading else remoteState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = MiuixIcons.Replace,
                            contentDescription = "刷新"
                        )
                    }
                }
            }

            if (isLocalMode) {
                LocalAppsContent(
                    apps = localState.apps.filter {
                        searchQuery.isBlank() ||
                        it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
                    },
                    isLoading = localState.isLoading,
                    error = localState.error,
                    onAppClick = { app ->
                        openLocalApp(context, app, selectedDisplayId)
                    },
                    onAppLongClick = { showMenuForApp = it }
                )
            } else {
                RemoteAppsContent(
                    state = remoteState,
                    searchQuery = searchQuery,
                    onAppClick = { app ->
                        remoteViewModel.openApp(context, app, deviceIp!!)
                    },
                    onAppLongClick = { showMenuForApp = it },
                    onRefresh = { remoteViewModel.refreshApps(context) }
                )
            }
        }

        if (isLocalMode && displays.size > 1) {
            DisplayNavigationBar(
                displays = displays,
                selectedDisplayId = selectedDisplayId,
                onDisplaySelected = { selectedDisplayId = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }

    showMenuForApp?.let { app ->
        AppContextMenu(
            appName = when (app) {
                is RemoteAppInfo -> app.appName
                is LocalAppInfo -> app.appName
                else -> ""
            },
            packageName = when (app) {
                is RemoteAppInfo -> app.packageName
                is LocalAppInfo -> app.packageName
                else -> ""
            },
            isPinned = when (app) {
                is RemoteAppInfo -> app.isPinned
                else -> false
            },
            showPinButton = app is RemoteAppInfo,
            onDismiss = { showMenuForApp = null },
            onPin = if (app is RemoteAppInfo) {
                { remoteViewModel.pinApp(context, app.packageName) }
            } else null,
            onUnpin = if (app is RemoteAppInfo) {
                { remoteViewModel.unpinApp(context, app.packageName) }
            } else null
        )
    }
}

@Composable
private fun LocalAppsContent(
    apps: List<LocalAppInfo>,
    isLoading: Boolean,
    error: String?,
    onAppClick: (LocalAppInfo) -> Unit,
    onAppLongClick: (LocalAppInfo) -> Unit
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles

    when {
        error != null -> {
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
                        text = error,
                        color = colorScheme.error
                    )
                }
            }
        }
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        apps.isEmpty() -> {
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
                        text = "暂无应用",
                        color = colorScheme.onSurfaceSecondary
                    )
                }
            }
        }
        else -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 80.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    LocalAppItem(
                        app = app,
                        onClick = { onAppClick(app) },
                        onLongClick = { onAppLongClick(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteAppsContent(
    state: com.xzyht.notifyrelay.servers.appslist.RemoteAppsState,
    searchQuery: String,
    onAppClick: (RemoteAppInfo) -> Unit,
    onAppLongClick: (RemoteAppInfo) -> Unit,
    onRefresh: () -> Unit
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles

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
                        onClick = onRefresh
                    ) {
                        Text("重试")
                    }
                }
            }
        }
        state.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "加载中",
                        color = colorScheme.onSurfaceSecondary
                    )
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

            if (filteredApps.isEmpty()) {
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
                            text = "无匹配结果",
                            color = colorScheme.onSurfaceSecondary
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
                                onClick = { onAppClick(app) },
                                onLongClick = { onAppLongClick(app) }
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
                            onClick = { onAppClick(app) },
                            onLongClick = { onAppLongClick(app) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayNavigationBar(
    displays: List<DisplayInfo>,
    selectedDisplayId: Int,
    onDisplaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MiuixTheme.colorScheme
    
    Card(
        modifier = modifier
            .padding(horizontal = 16.dp),
        colors = CardDefaults.defaultColors(
            color = colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            displays.forEach { display ->
                val isSelected = display.id == selectedDisplayId
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                        .clickable { onDisplaySelected(display.id) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (display.isBuiltIn) MiuixIcons.Settings else MiuixIcons.ScreenMirroring,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = display.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        color = if (isSelected) colorScheme.primary else colorScheme.onSurface
                    )
                    Text(
                        text = "#${display.id}",
                        fontSize = 10.sp,
                        color = if (isSelected) colorScheme.primary else colorScheme.onSurfaceSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalAppItem(
    app: LocalAppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colorScheme = MiuixTheme.colorScheme
    val context = LocalContext.current
    val packageManager = context.packageManager

    val bitmap = remember(app.packageName) {
        try {
            val appInfo = packageManager.getApplicationInfo(app.packageName, 0)
            val drawable = appInfo.loadIcon(packageManager)
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    Column(
        modifier = Modifier
            .width(72.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
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
                        bitmap = bitmap,
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
private fun AppItem(
    app: RemoteAppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colorScheme = MiuixTheme.colorScheme

    val bitmap = remember(app.iconBytes) {
        app.iconBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    Column(
        modifier = Modifier
            .width(72.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
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
                    imageVector = MiuixIcons.Pin,
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
    appName: String,
    packageName: String,
    isPinned: Boolean,
    showPinButton: Boolean,
    onDismiss: () -> Unit,
    onPin: (() -> Unit)?,
    onUnpin: (() -> Unit)?
) {
    val dismiss = LocalDismissState.current

    WindowDialog(
        title = appName,
        summary = packageName,
        show = true,
        onDismissRequest = onDismiss
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            top.yukonga.miuix.kmp.basic.TextButton(
                text = "关闭",
                onClick = { dismiss?.invoke() }
            )
            if (showPinButton && onPin != null && onUnpin != null) {
                Spacer(modifier = Modifier.width(8.dp))
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = if (isPinned) "取消置顶" else "置顶",
                    onClick = {
                        if (isPinned) onUnpin() else onPin()
                        dismiss?.invoke()
                    }
                )
            }
        }
    }
}

private fun openLocalApp(context: Context, app: LocalAppInfo, displayId: Int) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
        intent?.let {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            if (displayId > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val options = ActivityOptions.makeBasic()
                options.launchDisplayId = displayId
                context.startActivity(intent, options.toBundle())
            } else {
                context.startActivity(intent)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
