package com.xzyht.notifyrelay.ui.fragment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.xzyht.notifyrelay.BuildConfig
import com.xzyht.notifyrelay.feature.device.model.NotificationRepository
import com.xzyht.notifyrelay.feature.notification.backend.RemoteFilterConfig
import com.xzyht.notifyrelay.servers.appslist.AppRepository
import com.xzyht.notifyrelay.sync.MessageSender
import com.xzyht.notifyrelay.sync.notification.data.NotificationRecord
import com.xzyht.notifyrelay.ui.GuideActivity
import com.xzyht.notifyrelay.ui.common.DoubleClickConfirmButton
import com.xzyht.notifyrelay.ui.common.ProvideNavigationEventDispatcherOwner
import com.xzyht.notifyrelay.ui.filter.UISuperIslandSettings
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
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
fun DeleteButton(onClick: () -> Unit, modifier: Modifier = Modifier.Companion) {
    IconButton(
        onClick = {
            onClick()
        },
        modifier = modifier.fillMaxHeight().width(80.dp),
        backgroundColor = Color.Companion.Red,
        cornerRadius = 8.dp,
        minHeight = 40.dp,
        minWidth = 80.dp
    ) {
        Icon(
            imageVector = MiuixIcons.Delete,
            contentDescription = "Settings",
            modifier = Modifier.Companion.size(24.dp)
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
    installedPackages: Set<String>
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
        modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
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
        colors = CardDefaults.defaultColors(
            color = cardColor,
            contentColor = contentColor
        ),
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt
    ) {
        Row(verticalAlignment = Alignment.Companion.CenterVertically) {
            if (displayAppIcon != null) {
                Image(
                    bitmap = displayAppIcon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.Companion.size(24.dp)
                )
                Spacer(modifier = Modifier.Companion.width(8.dp))
            }
            // 标题显示为原始通知标题
            Text(
                text = displayTitle,
                style = notificationTextStyles.body2.copy(color = cardColorScheme.onSurface)
            )
            Spacer(modifier = Modifier.Companion.width(8.dp))
        }
        Spacer(modifier = Modifier.Companion.height(4.dp))
        Text(
            text = record.text ?: "(无内容)",
            style = notificationTextStyles.body1.copy(color = cardColorScheme.onSurface)
        )
        Spacer(modifier = Modifier.Companion.height(4.dp))
        Text(
            text = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(record.time),
                ZoneId.systemDefault()
            ).format(dateTimeFormatter),
            style = notificationTextStyles.body2.copy(color = cardColorScheme.onSurfaceSecondary)
        )
    }
}

// 工具函数：获取应用名和图标（文件级顶层）
suspend fun getAppNameAndIcon(context: Context, packageName: String?): Pair<String, Bitmap?> {
    var name = packageName ?: ""
    var icon: Bitmap? = null
    if (packageName != null) {
        try {
            // 先获取应用名
            name = try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                packageName // 外部应用或获取失败时使用包名
            }

            // 使用统一的图标获取方法，自动处理本地和外部应用
            icon = AppRepository.getAppIconWithAutoRequest(context, packageName)
        } catch (_: Exception) {
            // 如果所有尝试都失败，使用包名和默认图标
            name = packageName
            icon = null
        }
    }
    return name to icon
}

// 工具函数：Drawable转Bitmap（文件级顶层）
fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) {
        val bmp = drawable.bitmap
        if (bmp != null) return bmp
    }
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bmp
}


class NotificationHistoryFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ProvideNavigationEventDispatcherOwner {
                    MiuixTheme {
                        NotificationHistoryScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationHistoryScreen() {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val context = LocalContext.current

    // 创建协程作用域用于Tab点击事件
    val coroutineScope = rememberCoroutineScope()

    // TabRow相关状态
    val tabTitles = listOf("通知历史", "超级岛历史")

    // Pager相关状态 - 使用Pager状态作为唯一数据源
    val pagerState = rememberPagerState(initialPage = 0) {
        tabTitles.size
    }

    // 从Pager状态直接获取当前选中的Tab索引，不使用独立状态
    val selectedTabIndex = pagerState.currentPage

    // 响应全局设备选中状态
    val selectedDeviceObj by GlobalSelectedDeviceHolder.current()
    val selectedDevice = selectedDeviceObj?.uuid ?: "本机"
    // 切换设备时，先切换 currentDevice，再刷新 flow
    LaunchedEffect(selectedDevice) {
        NotificationRepository.currentDevice = selectedDevice
        NotificationRepository.notifyHistoryChanged(selectedDevice, context)
    }
    // 订阅当前分组的通知历史
    val notifications by NotificationRepository.notificationHistoryFlow.collectAsState()

    // 使用流处理分组，避免内存缓存
    // 跟踪每个分组的展开状态
    val isDarkTheme = isSystemInDarkTheme()
    // 包名到应用名和图标的缓存
    val appInfoCache = remember { mutableStateMapOf<String, Pair<String, Bitmap?>>() }

    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    // 跟踪每个分组的排序时间，只有在分组收起时才更新
    val groupSortTimes = remember { mutableStateMapOf<String, Long>() }

    // 添加一个刷新触发器，用于通知UI重新渲染
    val refreshTrigger = remember { mutableStateOf(0L) }
    val installedPackagesState = remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) {
        val cachedPkgs = AppRepository.getInstalledPackageNames(context)
        installedPackagesState.value = cachedPkgs
        if (cachedPkgs.isEmpty()) {
            installedPackagesState.value = AppRepository.getInstalledPackageNamesAsync(context)
        }
    }
    val installedPackages = installedPackagesState.value

    // 监听AppRepository的iconUpdates流，当图标更新时刷新UI
    LaunchedEffect(Unit) {
        AppRepository.iconUpdates.collect {
            if (it != null) {
                // 图标更新，刷新UI
                refreshTrigger.value = System.currentTimeMillis()
                // 清除缓存，确保下次获取时能拿到最新图标
                appInfoCache.remove(it)
            }
        }
    }

    val mixedList by remember(refreshTrigger.value, installedPackages) {
        derivedStateOf {
            // 使用缓存的包名集合，避免同步加载
            val grouped = notifications.groupBy { record ->
                // 使用映射后的包名进行分组
                RemoteFilterConfig.mapToLocalPackage(record.packageName, installedPackages)
            }
            val unifiedList = mutableListOf<List<NotificationRecord>>()
            for (entry in grouped.entries) {
                val list = entry.value.sortedByDescending { it.time }
                unifiedList.add(list)
            }
            // 排序分组，使用固定的排序时间
            unifiedList.sortedByDescending { list ->
                val groupKey = list.firstOrNull()?.packageName?.let {
                    RemoteFilterConfig.mapToLocalPackage(it, installedPackages)
                } ?: "unknown"
                val isExpanded = expandedGroups.getOrPut(groupKey) { false }
                if (isExpanded) {
                    // 如果分组展开，使用之前保存的排序时间或当前时间
                    groupSortTimes.getOrPut(groupKey) { list.firstOrNull()?.time ?: 0L }
                } else {
                    // 如果分组收起，更新排序时间并使用新时间
                    val newTime = list.firstOrNull()?.time ?: 0L
                    groupSortTimes[groupKey] = newTime
                    newTime
                }
            }
        }
    }
    // 设置系统状态栏字体颜色和背景色
    LaunchedEffect(isDarkTheme) {
        val window = (context as? Activity)?.window
        window?.let {
            val decorView = it.decorView
            // 统一使用 WindowInsetsControllerCompat 设置状态栏字体颜色
            WindowInsetsControllerCompat(it, decorView).isAppearanceLightStatusBars = !isDarkTheme
            // 移除状态栏背景色设置，避免编译错误
        }
    }
    LaunchedEffect(Unit) {
        NotificationRepository.init(context)
    }

    // 工具函数：获取并缓存应用名和图标
    fun getCachedAppInfo(packageName: String?): Pair<String, Bitmap?> {
        if (packageName == null) return "" to null

        // 从缓存获取，如果缓存中没有或图标为null，重新获取
        var appInfo = appInfoCache[packageName]
        if (appInfo == null || appInfo.second == null) {
            appInfo = runBlocking {
                getAppNameAndIcon(context, packageName)
            }
            appInfoCache[packageName] = appInfo
        }

        return appInfo
    }

    val clearHistory: () -> Unit = {
        try {
            // 只清除当前设备的历史，不删除其他设备的历史文件
            NotificationRepository.clearDeviceHistory(selectedDevice, context)
            appInfoCache.clear() // 清空应用信息缓存
            // 主动刷新 StateFlow
            NotificationRepository.notifyHistoryChanged(selectedDevice, context)
        } catch (e: Exception) {
                Logger.e("NotifyRelay", "清除历史异常", e)
                ToastUtils.showShortToast(
                    context,
                    "清除失败: ${e.message}"
                )
            }
    }
    val density = LocalDensity.current
    val deleteWidthPx = with(density) { 80.dp.toPx() }
    val deleteWidth = 80.dp

    // 通用通知列表块
    @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun NotificationListBlock(
        notifications: List<NotificationRecord>,
        mixedList: List<List<NotificationRecord>>,
        getCachedAppInfo: (String?) -> Pair<String, Bitmap?>,
        expandedGroups: MutableMap<String, Boolean>,
        installedPackages: Set<String>
    ) {
        val coroutineScope = rememberCoroutineScope()
        if (notifications.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    items = mixedList,
                    // 为每个分组提供稳定的 key，基于分组的包名
                    key = { list ->
                        list.firstOrNull()?.packageName?.let {
                            RemoteFilterConfig.mapToLocalPackage(it, installedPackages)
                        } ?: "unknown"
                    }
                ) { list ->
                    // 为每个分组的拖动状态提供稳定的键，确保状态与分组关联
                    // 使用更稳定的键：分组包名 + 列表大小，确保列表变化时重置状态
                    val groupKey = list.firstOrNull()?.packageName?.let {
                        RemoteFilterConfig.mapToLocalPackage(it, installedPackages)
                    } ?: "unknown"
                    val sortedList = list
                    val anchoredDraggableState = remember(groupKey, list.size) {
                        AnchoredDraggableState(
                            initialValue = DragValue.Center
                        )
                    }

                    // 为每个分组重新计算锚点，确保始终有效
                    val anchors = remember(groupKey, deleteWidthPx) {
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
                    val offset = remember(
                        anchoredDraggableState.currentValue,
                        anchoredDraggableState.offset
                    ) {
                        when {
                            anchoredDraggableState.currentValue == DragValue.End -> -deleteWidthPx
                            anchoredDraggableState.offset.isNaN() -> 0f
                            else -> anchoredDraggableState.offset
                        }
                    }
                    Box(modifier = Modifier.Companion.fillMaxWidth()) {
                        // 卡片
                        Box(
                            modifier = Modifier.Companion
                                .fillMaxWidth()
                                .anchoredDraggable(
                                    state = anchoredDraggableState,
                                    orientation = Orientation.Horizontal
                                )
                                .offset { IntOffset(offset.roundToInt(), 0) }
                        ) {
                            if (list.size == 1) {
                                val record = list[0]
                                val (_, appIcon) = getCachedAppInfo(record.packageName)
                                NotificationCard(
                                    record = record,
                                    appIcon = appIcon,
                                    context = context,
                                    getCachedAppInfo = getCachedAppInfo,
                                    cardColor = colorScheme.surface,
                                    contentColor = colorScheme.onSurface,
                                    installedPackages = installedPackages
                                )
                            } else {
                                val latest = sortedList.firstOrNull()
                                // 使用映射后的包名获取应用信息，使用缓存的包名集合，避免同步加载
                                val mappedPkg = RemoteFilterConfig.mapToLocalPackage(
                                    latest?.packageName ?: "",
                                    installedPackages
                                )
                                val appInfo: Pair<String, Bitmap?> = getCachedAppInfo(mappedPkg)
                                val (appName, appIcon) = appInfo
                                // 修正：分组折叠标题优先显示json中的appName字段，其次本地应用名，再次包名，最后(未知应用)
                                val groupTitle = when {
                                    !latest?.appName.isNullOrBlank() -> latest.appName
                                    appName.isNotBlank() -> appName
                                    mappedPkg.isNotBlank() -> mappedPkg
                                    else -> "(未知应用)"
                                }
                                // 使用全局展开状态映射
                                val groupKey = mappedPkg
                                val expanded = expandedGroups.getOrPut(groupKey) { false }
                                Card(
                                    modifier = Modifier.Companion
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    onClick = { expandedGroups[groupKey] = !expanded },
                                    cornerRadius = 12.dp,
                                    insideMargin = PaddingValues(12.dp),
                                    colors = CardDefaults.defaultColors(
                                        color = colorScheme.surface,
                                        contentColor = colorScheme.onSurface
                                    ),
                                    // 展开时不显示按压效果
                                    showIndication = !expanded,
                                    // 展开时不显示按压反馈
                                    pressFeedbackType = if (expanded) PressFeedbackType.None else PressFeedbackType.Sink
                                ) {
                                    Row(
                                        modifier = Modifier.Companion.fillMaxWidth(),
                                        verticalAlignment = Alignment.Companion.CenterVertically
                                    ) {
                                        if (appIcon != null) {
                                            Image(
                                                bitmap = appIcon.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.Companion.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.Companion.width(8.dp))
                                        }
                                        Text(
                                            text = groupTitle,
                                            style = textStyles.title3.copy(color = colorScheme.onSurface)
                                        )
                                        Spacer(modifier = Modifier.Companion.width(12.dp))
                                        Text(
                                            text = (latest?.time?.let {
                                                LocalDateTime.ofInstant(
                                                    Instant.ofEpochMilli(it),
                                                    ZoneId.systemDefault()
                                                ).format(dateTimeFormatter)
                                            } ?: ""),
                                            style = textStyles.body2.copy(color = colorScheme.onSurfaceSecondary)
                                        )
                                        Spacer(modifier = Modifier.Companion.weight(1f))
                                        Text(
                                            text = if (expanded) "收起" else "展开",
                                            style = textStyles.body2.copy(color = colorScheme.primary)
                                        )
                                    }
                                    Spacer(modifier = Modifier.Companion.height(8.dp))
                                    val showList = if (expanded) sortedList else sortedList.take(3)
                                    if (!expanded) {
                                        showList.forEachIndexed { idx, record ->
                                            Row(
                                                modifier = Modifier.Companion.fillMaxWidth(),
                                                verticalAlignment = Alignment.Companion.CenterVertically
                                            ) {
                                                // 修正：标题应为原始通知标题而非应用名
                                                Text(
                                                    text = record.title ?: "(无标题)",
                                                    style = textStyles.body2.copy(
                                                        color = colorScheme.onSurface,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    modifier = Modifier.Companion.weight(0.4f)
                                                )
                                                Spacer(modifier = Modifier.Companion.width(4.dp))
                                                Text(
                                                    text = record.text ?: "(无内容)",
                                                    style = textStyles.body2.copy(color = colorScheme.onSurfaceSecondary),
                                                    modifier = Modifier.Companion.weight(0.6f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (idx < showList.lastIndex) {
                                                HorizontalDivider(
                                                    modifier = Modifier.Companion.fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    color = colorScheme.outline,
                                                    thickness = 1.dp
                                                )
                                            }
                                        }
                                        if (list.size > 3) {
                                            Text(
                                                text = "... 共${list.size}条，点击展开",
                                                style = textStyles.body2.copy(color = colorScheme.outline)
                                            )
                                        }
                                    } else {
                                        // 优化：限制展开时显示的通知数量，避免性能问题
                                        val expandedList = sortedList
                                        val maxExpandedItems = 50 // 限制最多显示50条通知
                                        val displayList =
                                            if (expandedList.size > maxExpandedItems) {
                                                expandedList.take(maxExpandedItems)
                                            } else {
                                                expandedList
                                            }

                                        // 使用IndexedValue获取索引，便于后续处理
                                        displayList.forEachIndexed { index, record ->
                                            // 为每个单个通知提供独立的拖动状态，使用record.key作为稳定键
                                            val anchoredDraggableState = remember(record.key) {
                                                AnchoredDraggableState(
                                                    initialValue = DragValue.Center
                                                )
                                            }

                                            // 为每个通知重新计算锚点，确保始终有效
                                            val anchors = remember(record.key, deleteWidthPx) {
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
                                            val offset = remember(
                                                anchoredDraggableState.currentValue,
                                                anchoredDraggableState.offset
                                            ) {
                                                when {
                                                    anchoredDraggableState.currentValue == DragValue.End -> -deleteWidthPx
                                                    anchoredDraggableState.offset.isNaN() -> 0f
                                                    else -> anchoredDraggableState.offset
                                                }
                                            }
                                            Box(modifier = Modifier.Companion.fillMaxWidth()) {
                                                // 卡片
                                                Box(
                                                    modifier = Modifier.Companion
                                                        .fillMaxWidth()
                                                        .anchoredDraggable(
                                                            state = anchoredDraggableState,
                                                            orientation = Orientation.Horizontal
                                                        )
                                                        .offset {
                                                            IntOffset(
                                                                offset.roundToInt(),
                                                                0
                                                            )
                                                        }
                                                ) {
                                                    val (_, appIcon1) = getCachedAppInfo(record.packageName)
                                                    NotificationCard(
                                                        record = record,
                                                        appIcon = appIcon1,
                                                        context = context,
                                                        getCachedAppInfo = getCachedAppInfo,
                                                        cardColor = colorScheme.surfaceContainer,
                                                        contentColor = colorScheme.onSurface,
                                                        installedPackages = installedPackages
                                                    )
                                                }
                                                // 删除按钮
                                                if (anchoredDraggableState.currentValue == DragValue.End) {
                                                    DeleteButton(
                                                        onClick = {
                                                            NotificationRepository.currentDevice =
                                                                selectedDevice
                                                            //Logger.d("NotifyRelay", "轮胎: 删除按钮点击 - 展开列表单个通知, key=${record.key}")
                                                            // 在删除前，先将拖动状态重置到中心位置，避免后续操作中出现状态不一致
                                                            // 使用 snapTo 方法同步重置状态
                                                            coroutineScope.launch {
                                                                anchoredDraggableState.snapTo(
                                                                    DragValue.Center
                                                                )
                                                            }
                                                            NotificationRepository.removeNotification(
                                                                record.key,
                                                                context
                                                            )
                                                            // 不需要手动调用notifyHistoryChanged，removeNotification方法内部会处理
                                                        },
                                                        modifier = Modifier.Companion.align(
                                                            Alignment.Companion.CenterEnd
                                                        ).width(deleteWidth).fillMaxHeight()
                                                    )
                                                }
                                            }
                                        }

                                        // 如果通知数量超过限制，显示提示信息
                                        if (expandedList.size > maxExpandedItems) {
                                            Spacer(modifier = Modifier.Companion.height(8.dp))
                                            Text(
                                                text = "... 仅显示前${maxExpandedItems}条，共${expandedList.size}条通知",
                                                style = textStyles.body2.copy(color = colorScheme.outline)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // 删除按钮
                        if (anchoredDraggableState.currentValue == DragValue.End) {
                            DeleteButton(
                                onClick = {
                                    NotificationRepository.currentDevice = selectedDevice
                                    //Logger.d("NotifyRelay", "轮胎: 删除按钮点击, key=${list[0].key}, size=${list.size}")
                                    try {
                                        // 在删除前，先将拖动状态重置到中心位置，避免后续操作中出现状态不一致
                                        // 使用 snapTo 方法同步重置状态
                                        coroutineScope.launch {
                                            anchoredDraggableState.snapTo(DragValue.Center)
                                        }

                                        if (list.size == 1) {
                                            // 保存要删除的包名，用于后续重置状态
                                            val pkgName = list[0].packageName
                                            val mappedPkg = RemoteFilterConfig.mapToLocalPackage(
                                                pkgName,
                                                installedPackages
                                            )
                                            NotificationRepository.removeNotification(
                                                list[0].key,
                                                context
                                            )
                                            // 重置该分组的展开状态和排序时间
                                            expandedGroups.remove(mappedPkg)
                                            groupSortTimes.remove(mappedPkg)
                                        } else {
                                            // 使用更高效的分组删除方法，避免循环调用
                                            val firstRecord = list[0]
                                            // 使用映射后的包名进行删除，确保删除整个分组
                                            val mappedPkg = RemoteFilterConfig.mapToLocalPackage(
                                                firstRecord.packageName,
                                                installedPackages
                                            )
                                            NotificationRepository.removeNotificationsByPackage(
                                                mappedPkg,
                                                context
                                            )
                                            // 重置分组的展开状态和排序时间
                                            expandedGroups.remove(mappedPkg)
                                            groupSortTimes.remove(mappedPkg)
                                        }
                                        // 重新获取通知历史，更新UI
                                        NotificationRepository.notifyHistoryChanged(
                                            selectedDevice,
                                            context
                                        )
                                    } catch (e: Exception) {
                                        Logger.e("NotifyRelay", "删除失败", e)
                                    }
                                },
                                modifier = Modifier.Companion.align(Alignment.Companion.CenterEnd)
                                    .width(deleteWidth).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }

    // 超级岛历史块
    @Composable
    fun SuperIslandHistoryBlock() {
        UISuperIslandSettings()
    }

    // 使用 Miuix Scaffold 重构布局
    Scaffold(
        containerColor = colorScheme.background,
        popupHost = { },
        floatingToolbar = {
            if (notifications.isNotEmpty() && selectedTabIndex == 0) {
                FloatingToolbar(
                    color = colorScheme.primary,
                    cornerRadius = 20.dp,
                    showDivider = false
                ) {
                    // 使用Row水平排列按钮
                    Row(
                        modifier = Modifier.Companion.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Companion.CenterVertically
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
                            confirmColors = ButtonDefaults.buttonColors(color = Color.Companion.Red),
                            textColor = colorScheme.onPrimary,
                            confirmTextColor = Color.Companion.White
                        )

                        // 垂直分割线
                        VerticalDivider(
                            thickness = 1.dp,
                            modifier = Modifier.Companion.height(40.dp)
                        )

                        // 引导按钮 - 仅在DEBUG模式下显示
                        if (BuildConfig.DEBUG) {
                            Button(
                                onClick = {
                                    try {
                                        // 跳转引导页面
                                        val intent = IntentUtils.createIntent(
                                            context,
                                            GuideActivity::class.java
                                        )
                                        intent.putExtra("fromInternal", true)
                                        IntentUtils.startActivity(context, intent, true)
                                    } catch (e: Exception) {
                                        Logger.e("NotifyRelay", "引导跳转失败", e)
                                        ToastUtils.showShortToast(
                                            context,
                                            "跳转失败: ${e.message}"
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColorsPrimary(),
                                cornerRadius = 16.dp,
                                minWidth = 0.dp,
                                minHeight = 0.dp,
                                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "引导",
                                    style = textStyles.body2.copy(color = colorScheme.onPrimary)
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingToolbarPosition = ToolbarPosition.Companion.BottomEnd,
        content = { paddingValues ->
            Column(
                modifier = Modifier.Companion
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                TabRowWithContour(
                    tabs = tabTitles,
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { index ->
                        coroutineScope.launch {
                            pagerState.scrollToPage(index)
                        }
                    },
                    modifier = Modifier.Companion.fillMaxWidth(),
                    colors = TabRowDefaults.tabRowColors(
                        backgroundColor = colorScheme.surface,
                        contentColor = colorScheme.onSurface,
                        selectedBackgroundColor = colorScheme.primary,
                        selectedContentColor = colorScheme.onPrimary
                    ),
                    minWidth = 100.dp,
                    height = 48.dp,
                    cornerRadius = 16.dp
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.Companion
                        .fillMaxSize()
                        .padding(16.dp)
                ) { page ->
                    when (page) {
                        0 -> {
                            // 通知历史 Tab
                            Column(
                                modifier = Modifier.Companion.fillMaxSize()
                            ) {
                                if (notifications.isEmpty()) {
                                    Spacer(modifier = Modifier.Companion.height(16.dp))
                                    Text(
                                        text = "暂无通知",
                                        style = textStyles.body1.copy(color = colorScheme.onSurfaceSecondary)
                                    )
                                } else {
                                    NotificationListBlock(
                                        notifications = notifications,
                                        mixedList = mixedList,
                                        getCachedAppInfo = { pkg -> getCachedAppInfo(pkg) },
                                        expandedGroups = expandedGroups,
                                        installedPackages = installedPackages
                                    )
                                }
                            }
                        }

                        1 -> {
                            // 超级岛历史 Tab
                            SuperIslandHistoryBlock()
                        }
                    }
                }
            }
        }
    )
}