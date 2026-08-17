package com.xzyht.notifyrelay.ui.pages

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.xzyht.notifyrelay.feature.notification.backend.RemoteFilterConfig
import com.xzyht.notifyrelay.sync.MessageSender
import com.xzyht.notifyrelay.sync.notification.data.NotificationRecord
import com.xzyht.notifyrelay.ui.activity.DeveloperModeActivity
import com.xzyht.notifyrelay.ui.activity.GuideActivity
import com.xzyht.notifyrelay.ui.common.DoubleClickConfirmButton
import com.xzyht.notifyrelay.ui.screen.GlobalSelectedDeviceHolder
import com.xzyht.notifyrelay.ui.viewmodel.GroupedNotifications
import com.xzyht.notifyrelay.ui.viewmodel.NotificationHistoryViewModel
import kotlinx.coroutines.launch
import notifyrelay.base.util.IntentUtils
import notifyrelay.base.util.Logger
import notifyrelay.base.util.ToastUtils
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// 日期格式化工具（线程安全）
private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

enum class DragValue { Center, End }

// 防抖 Toast（文件级顶层对象）
object ToastDebounce {
    var lastToastTime: Long = 0L
    const val DEBOUNCE_MILLIS: Long = 1500L
}

@Composable
fun DeleteButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = {
            onClick()
        },
        modifier = modifier
            .fillMaxHeight()
            .width(80.dp),
        backgroundColor = MiuixTheme.colorScheme.error,
        cornerRadius = 8.dp,
        minHeight = 40.dp,
        minWidth = 80.dp,
    ) {
        Icon(
            imageVector = MiuixIcons.Delete,
            contentDescription = "Settings",
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
fun NotificationCard(
    record: NotificationRecord,
    appIcon: Bitmap?,
    context: Context,
    getCachedAppInfo: (String?) -> Pair<String, Bitmap?>,
    cardColor: Color,
    contentColor: Color,
    installedPackages: Set<String>,
) {
    val notificationTextStyles = MiuixTheme.textStyles
    val cardColorScheme = MiuixTheme.colorScheme

    // 对包名进行等价映射，使用缓存的包名集合，避免同步加载
    val mappedPkg = RemoteFilterConfig.mapToLocalPackage(record.packageName, installedPackages)

    // 使用映射后的包名获取应用信息
    val appInfo: Pair<String, Bitmap?> = getCachedAppInfo(mappedPkg)
    val (_, mappedAppIcon) = appInfo
    val displayAppIcon = mappedAppIcon ?: appIcon

    // 修正：单条通知卡片标题应为原始通知标题
    val displayTitle = record.title ?: "(无标题)"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = {
            // 跳转到对应应用主界面
            val pkg = record.packageName
            if (pkg.isNotEmpty()) {
                // 应用等价映射，使用缓存的包名集合，避免同步加载
                val mappedPkg = RemoteFilterConfig.mapToLocalPackage(pkg, installedPackages)

                var canOpen = false
                var intent: Intent? = null
                try {
                    intent = context.packageManager.getLaunchIntentForPackage(mappedPkg)
                    if (intent != null) {
                        canOpen = true
                    } else {
                        val now = System.currentTimeMillis()
                        if (now - ToastDebounce.lastToastTime > ToastDebounce.DEBOUNCE_MILLIS) {
                            ToastUtils.showShortToast(context, "无法打开应用：$mappedPkg")
                            ToastDebounce.lastToastTime = now
                        }
                    }
                } catch (e: Exception) {
                    val now = System.currentTimeMillis()
                    if (now - ToastDebounce.lastToastTime > ToastDebounce.DEBOUNCE_MILLIS) {
                        ToastUtils.showShortToast(context, "启动失败：${e.message}")
                        ToastDebounce.lastToastTime = now
                    }
                }
                // 仅在即将跳转前显示通知标题和内容
                if (canOpen) {
                    // 发送高优先级悬浮通知
                    val title = record.title ?: "(无标题)"
                    val text = record.text ?: "(无内容)"
                    MessageSender.sendHighPriorityNotification(context, title, text)
                    intent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        },
        cornerRadius = 8.dp,
        insideMargin = PaddingValues(12.dp),
        colors =
            CardDefaults.defaultColors(
                color = cardColor,
                contentColor = contentColor,
            ),
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (displayAppIcon != null) {
                Image(
                    bitmap = displayAppIcon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            // 标题显示为原始通知标题
            Text(
                text = displayTitle,
                style = notificationTextStyles.body2.copy(color = cardColorScheme.onSurface),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = record.text ?: "(无内容)",
            style = notificationTextStyles.body1.copy(color = cardColorScheme.onSurface),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text =
                LocalDateTime
                    .ofInstant(
                        Instant.ofEpochMilli(record.time),
                        ZoneId.systemDefault(),
                    ).format(dateTimeFormatter),
            style = notificationTextStyles.body2.copy(color = cardColorScheme.onSurfaceSecondary),
        )
    }
}

@Composable
fun NotificationHistoryScreen() {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val context = LocalContext.current
    val viewModel: NotificationHistoryViewModel =
        viewModel(
            factory = NotificationHistoryViewModel.Factory(context.applicationContext as Application),
        )

    // 创建协程作用域用于删除操作等
    val coroutineScope = rememberCoroutineScope()

    val selectedDeviceObj by GlobalSelectedDeviceHolder.current()
    val selectedDevice = selectedDeviceObj?.uuid ?: "本机"

    LaunchedEffect(selectedDevice) {
        viewModel.loadNotifications(selectedDevice)
    }

    val isDarkTheme = isSystemInDarkTheme()
    LaunchedEffect(isDarkTheme) {
        val window = (context as? Activity)?.window
        window?.let {
            val decorView = it.decorView
            WindowInsetsControllerCompat(it, decorView).isAppearanceLightStatusBars = !isDarkTheme
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val installedPackages by viewModel.installedPackagesState.collectAsState()
    val appIconCache by viewModel.appIconCache.collectAsState()
    val pagingItems = viewModel.groupedPagingFlow.collectAsLazyPagingItems()

    val groupPackages =
        pagingItems.itemSnapshotList.items
            .map { it.packageName }
            .distinct()
    LaunchedEffect(groupPackages) {
        viewModel.preloadAppIcons(groupPackages)
    }

    val getCachedAppInfo: (String?) -> Pair<String, Bitmap?> = { packageName ->
        if (packageName.isNullOrBlank()) {
            "" to null
        } else {
            appIconCache[packageName] ?: (packageName to null)
        }
    }

    val clearHistory: () -> Unit = {
        try {
            viewModel.clearHistory()
        } catch (e: Exception) {
            Logger.e("NotifyRelay", "清除历史异常", e)
            ToastUtils.showShortToast(
                context,
                "清除失败: ${e.message}",
            )
        }
    }
    val density = LocalDensity.current
    val deleteWidthPx = with(density) { 80.dp.toPx() }
    val deleteWidth = 80.dp

    // 通用通知列表块
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun NotificationListBlock(
        pagingItems: androidx.paging.compose.LazyPagingItems<GroupedNotifications>,
        getCachedAppInfo: (String?) -> Pair<String, Bitmap?>,
        expandedGroups: Set<String>,
        installedPackages: Set<String>,
        onToggleGroup: (String) -> Unit,
        onDeleteGroup: (String) -> Unit,
        onDeleteNotification: (String) -> Unit,
    ) {
        val coroutineScope = rememberCoroutineScope()
        if (pagingItems.itemCount > 0) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    count = pagingItems.itemCount,
                    key = { index ->
                        pagingItems[index]?.packageName ?: "group-$index"
                    },
                ) { index ->
                    val group = pagingItems[index] ?: return@items
                    val groupKey = group.packageName
                    val sortedList = group.notifications
                    val anchoredDraggableState =
                        remember(groupKey, sortedList.size) {
                            AnchoredDraggableState(
                                initialValue = DragValue.Center,
                            )
                        }

                    // 为每个分组重新计算锚点，确保始终有效
                    val anchors =
                        remember(groupKey, deleteWidthPx) {
                            DraggableAnchors {
                                DragValue.Center at 0f
                                DragValue.End at -deleteWidthPx
                            }
                        }

                    // 确保锚点始终有效，在状态创建或锚点变化时立即更新
                    LaunchedEffect(anchoredDraggableState, anchors) {
                        // 直接更新锚点，不需要额外的contains检查
                        anchoredDraggableState.updateAnchors(anchors)
                    }

                    // 安全地计算偏移量，避免无效状态
                    val offset =
                        remember(
                            anchoredDraggableState.currentValue,
                            anchoredDraggableState.offset,
                        ) {
                            when {
                                anchoredDraggableState.currentValue == DragValue.End -> -deleteWidthPx
                                anchoredDraggableState.offset.isNaN() -> 0f
                                else -> anchoredDraggableState.offset
                            }
                        }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // 卡片
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .anchoredDraggable(
                                        state = anchoredDraggableState,
                                        orientation = Orientation.Horizontal,
                                    )
                                    .offset { IntOffset(offset.roundToInt(), 0) },
                        ) {
                            if (sortedList.size == 1) {
                                val record = sortedList[0]
                                val (_, appIcon) = getCachedAppInfo(record.packageName)
                                NotificationCard(
                                    record = record,
                                    appIcon = appIcon,
                                    context = context,
                                    getCachedAppInfo = getCachedAppInfo,
                                    cardColor = colorScheme.surface,
                                    contentColor = colorScheme.onSurface,
                                    installedPackages = installedPackages,
                                )
                            } else {
                                val appInfo: Pair<String, Bitmap?> = getCachedAppInfo(groupKey)
                                val (appName, appIcon) = appInfo
                                val groupTitle =
                                    when {
                                        group.appName.isNotBlank() -> group.appName
                                        appName.isNotBlank() -> appName
                                        groupKey.isNotBlank() -> groupKey
                                        else -> "(未知应用)"
                                    }
                                val expanded = expandedGroups.contains(groupKey)
                                Card(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                    onClick = { onToggleGroup(groupKey) },
                                    cornerRadius = 12.dp,
                                    insideMargin = PaddingValues(12.dp),
                                    colors =
                                        CardDefaults.defaultColors(
                                            color = colorScheme.surface,
                                            contentColor = colorScheme.onSurface,
                                        ),
                                    // 展开时不显示按压效果
                                    showIndication = !expanded,
                                    // 展开时不显示按压反馈
                                    pressFeedbackType = if (expanded) PressFeedbackType.None else PressFeedbackType.Sink,
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (appIcon != null) {
                                            Image(
                                                bitmap = appIcon.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(
                                            text = groupTitle,
                                            style = textStyles.title3.copy(color = colorScheme.onSurface),
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text =
                                                LocalDateTime
                                                    .ofInstant(
                                                        Instant.ofEpochMilli(group.latestTime),
                                                        ZoneId.systemDefault(),
                                                    ).format(dateTimeFormatter),
                                            style = textStyles.body2.copy(color = colorScheme.onSurfaceSecondary),
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            text = if (expanded) "收起" else "展开",
                                            style = textStyles.body2.copy(color = colorScheme.primary),
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val showList = if (expanded) sortedList else sortedList.take(3)
                                    if (!expanded) {
                                        showList.forEachIndexed { idx, record ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                // 修正：标题应为原始通知标题而非应用名
                                                Text(
                                                    text = record.title ?: "(无标题)",
                                                    style =
                                                        textStyles.body2.copy(
                                                            color = colorScheme.onSurface,
                                                            fontWeight = FontWeight.Bold,
                                                        ),
                                                    modifier = Modifier.weight(0.4f),
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = record.text ?: "(无内容)",
                                                    style = textStyles.body2.copy(color = colorScheme.onSurfaceSecondary),
                                                    modifier = Modifier.weight(0.6f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            if (idx < showList.lastIndex) {
                                                HorizontalDivider(
                                                    modifier =
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp),
                                                    color = colorScheme.outline,
                                                    thickness = 1.dp,
                                                )
                                            }
                                        }
                                        if (sortedList.size > 3) {
                                            Text(
                                                text = "... 共${sortedList.size}条，点击展开",
                                                style = textStyles.body2.copy(color = colorScheme.outline),
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 360.dp),
                                        ) {
                                            items(
                                                count = sortedList.size,
                                                key = { itemIndex -> sortedList[itemIndex].key },
                                            ) { itemIndex ->
                                                val record = sortedList[itemIndex]
                                                val anchoredDraggableState =
                                                    remember(record.key) {
                                                        AnchoredDraggableState(
                                                            initialValue = DragValue.Center,
                                                        )
                                                    }

                                                val anchors =
                                                    remember(record.key, deleteWidthPx) {
                                                        DraggableAnchors {
                                                            DragValue.Center at 0f
                                                            DragValue.End at -deleteWidthPx
                                                        }
                                                    }

                                                LaunchedEffect(anchoredDraggableState, anchors) {
                                                    anchoredDraggableState.updateAnchors(anchors)
                                                }

                                                val offset =
                                                    remember(
                                                        anchoredDraggableState.currentValue,
                                                        anchoredDraggableState.offset,
                                                    ) {
                                                        when {
                                                            anchoredDraggableState.currentValue == DragValue.End -> -deleteWidthPx
                                                            anchoredDraggableState.offset.isNaN() -> 0f
                                                            else -> anchoredDraggableState.offset
                                                        }
                                                    }
                                                Box(modifier = Modifier.fillMaxWidth()) {
                                                    Box(
                                                        modifier =
                                                            Modifier
                                                                .fillMaxWidth()
                                                                .anchoredDraggable(
                                                                    state = anchoredDraggableState,
                                                                    orientation = Orientation.Horizontal,
                                                                )
                                                                .offset {
                                                                    IntOffset(
                                                                        offset.roundToInt(),
                                                                        0,
                                                                    )
                                                                },
                                                    ) {
                                                        val (_, appIcon1) = getCachedAppInfo(record.packageName)
                                                        NotificationCard(
                                                            record = record,
                                                            appIcon = appIcon1,
                                                            context = context,
                                                            getCachedAppInfo = getCachedAppInfo,
                                                            cardColor = colorScheme.surfaceContainer,
                                                            contentColor = colorScheme.onSurface,
                                                            installedPackages = installedPackages,
                                                        )
                                                    }
                                                    if (anchoredDraggableState.currentValue == DragValue.End) {
                                                        DeleteButton(
                                                            onClick = {
                                                                coroutineScope.launch {
                                                                    anchoredDraggableState.snapTo(
                                                                        DragValue.Center,
                                                                    )
                                                                }
                                                                onDeleteNotification(record.key)
                                                            },
                                                            modifier =
                                                                Modifier
                                                                    .align(
                                                                        Alignment.CenterEnd,
                                                                    )
                                                                    .width(deleteWidth)
                                                                    .fillMaxHeight(),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // 删除按钮
                        if (anchoredDraggableState.currentValue == DragValue.End) {
                            DeleteButton(
                                onClick = {
                                    try {
                                        coroutineScope.launch {
                                            anchoredDraggableState.snapTo(DragValue.Center)
                                        }
                                        if (sortedList.size == 1) {
                                            onDeleteNotification(sortedList[0].key)
                                        } else {
                                            onDeleteGroup(groupKey)
                                        }
                                    } catch (e: Exception) {
                                        Logger.e("NotifyRelay", "删除失败", e)
                                    }
                                },
                                modifier =
                                    Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(deleteWidth)
                                        .fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }

    // 使用 Miuix Scaffold 重构布局
    Scaffold(
        containerColor = colorScheme.background,
        floatingToolbar = {
            if (pagingItems.itemCount > 0) {
                FloatingToolbar(
                    color = colorScheme.primary,
                    cornerRadius = 20.dp,
                    showDivider = false,
                ) {
                    // 使用Row水平排列按钮
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 清除按钮 - 始终显示
                        DoubleClickConfirmButton(
                            text = "清除",
                            confirmText = "确认?",
                            onClick = {
                                // 第一次点击，显示提示信息
                            },
                            onConfirm = {
                                clearHistory()
                            },
                            modifier = Modifier.Companion,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            confirmColors = ButtonDefaults.buttonColors(color = colorScheme.error),
                            textColor = colorScheme.onPrimary,
                            confirmTextColor = colorScheme.onError,
                        )

                        if (DeveloperModeActivity.DEBUG_UI_ENABLED.value) {
                            VerticalDivider(
                                thickness = 1.dp,
                                modifier = Modifier.height(40.dp),
                            )

                            Button(
                                onClick = {
                                    try {
                                        // 跳转引导页面
                                        val intent =
                                            IntentUtils.createIntent(
                                                context,
                                                GuideActivity::class.java,
                                            )
                                        intent.putExtra("fromInternal", true)
                                        IntentUtils.startActivity(context, intent, true)
                                    } catch (e: Exception) {
                                        Logger.e("NotifyRelay", "引导跳转失败", e)
                                        ToastUtils.showShortToast(
                                            context,
                                            "跳转失败: ${e.message}",
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColorsPrimary(),
                                cornerRadius = 16.dp,
                                minWidth = 0.dp,
                                minHeight = 0.dp,
                                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = "引导",
                                    style = textStyles.body2.copy(color = colorScheme.onPrimary),
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingToolbarPosition = ToolbarPosition.BottomEnd,
        content = { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
            ) {
                val isEmpty = pagingItems.itemCount == 0
                if (isEmpty) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无通知",
                        style = textStyles.body1.copy(color = colorScheme.onSurfaceSecondary),
                    )
                } else {
                    NotificationListBlock(
                        pagingItems = pagingItems,
                        getCachedAppInfo = { pkg -> getCachedAppInfo(pkg) },
                        expandedGroups = uiState.expandedGroups,
                        installedPackages = installedPackages,
                        onToggleGroup = { packageName -> viewModel.toggleGroupExpansion(packageName) },
                        onDeleteGroup = { packageName -> viewModel.deleteGroup(packageName) },
                        onDeleteNotification = { key -> viewModel.deleteNotification(key) },
                    )
                }
            }
        },
    )
}
