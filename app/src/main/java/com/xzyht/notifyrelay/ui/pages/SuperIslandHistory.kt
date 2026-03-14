package com.xzyht.notifyrelay.ui.pages

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.xzyht.notifyrelay.feature.notification.superisland.FloatingReplicaManager
import com.xzyht.notifyrelay.feature.notification.superisland.floating.common.SuperIslandImageUtil
import com.xzyht.notifyrelay.feature.notification.superisland.history.SuperIslandHistoryEntry
import com.xzyht.notifyrelay.feature.notification.superisland.image.SuperIslandImageStore
import com.xzyht.notifyrelay.servers.appslist.AppRepository
import com.xzyht.notifyrelay.ui.ViewModels.GroupedSuperIslandHistory
import com.xzyht.notifyrelay.ui.ViewModels.SuperIslandHistoryViewModel
import com.xzyht.notifyrelay.ui.common.DoubleClickConfirmButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notifyrelay.base.util.Logger
import notifyrelay.core.util.DataUrlUtils
import notifyrelay.data.StorageManager
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private const val SUPER_ISLAND_IMAGE_MAX_DIMENSION = 320
private const val SUPER_ISLAND_DOWNLOAD_MAX_BYTES = 4 * 1024 * 1024

enum class SuperIslandDragValue { Center, End }

@Composable
fun SuperIslandDeleteButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier.fillMaxHeight().width(80.dp),
        backgroundColor = Color.Red,
        cornerRadius = 8.dp,
        minHeight = 40.dp,
        minWidth = 80.dp
    ) {
        Icon(
            imageVector = MiuixIcons.Delete,
            contentDescription = "删除",
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun UISuperIslandHistory() {
    val context = LocalContext.current
    var includeImageDataOnCopy by remember { mutableStateOf(StorageManager.getBoolean(context, "superisland_copy_image_data", false)) }

    val viewModel: SuperIslandHistoryViewModel = viewModel(
        factory = SuperIslandHistoryViewModel.Factory(context.applicationContext as android.app.Application)
    )

    val isDarkTheme = isSystemInDarkTheme()
    LaunchedEffect(isDarkTheme) {
        val window = (context as? Activity)?.window
        window?.let {
            val decorView = it.decorView
            WindowInsetsControllerCompat(it, decorView).isAppearanceLightStatusBars = !isDarkTheme
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val appIconCache by viewModel.appIconCache.collectAsState()
    val pagingItems = viewModel.groupedPagingFlow.collectAsLazyPagingItems()

    val groupPackages = pagingItems.itemSnapshotList.items.map { it.packageName }.distinct()
    LaunchedEffect(groupPackages) {
        viewModel.preloadAppIcons(groupPackages)
    }

    val getCachedAppInfo: (String?) -> Pair<String, Bitmap?> = { packageName ->
        if (packageName.isNullOrBlank() || packageName == "(未知应用)") {
            "" to null
        } else {
            appIconCache[packageName] ?: (packageName to null)
        }
    }

    val clearHistory: () -> Unit = {
        try {
            viewModel.clearHistory()
        } catch (e: Exception) {
            Logger.e("NotifyRelay", "清除超级岛历史异常", e)
            Toast.makeText(context, "清除失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    MiuixTheme {
        val colorScheme = MiuixTheme.colorScheme
        val textStyles = MiuixTheme.textStyles
        val density = LocalDensity.current
        val deleteWidthPx = with(density) { 80.dp.toPx() }
        val deleteWidth = 80.dp

        Scaffold(
            containerColor = colorScheme.background,
            popupHost = { },
            floatingToolbar = {
                if (pagingItems.itemCount > 0) {
                    FloatingToolbar(
                        color = colorScheme.primary,
                        cornerRadius = 20.dp,
                        showDivider = false
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DoubleClickConfirmButton(
                                text = "清空超级岛历史",
                                confirmText = "确认?",
                                onClick = {},
                                onConfirm = clearHistory,
                                colors = ButtonDefaults.buttonColors(color = colorScheme.onSurface),
                                confirmColors = ButtonDefaults.buttonColors(color = Color.Red)
                            )
                        }
                    }
                }
            },
            floatingToolbarPosition = ToolbarPosition.BottomEnd,
            content = { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "复制图片详细信息",
                            style = textStyles.body2,
                            color = colorScheme.onSurface
                        )
                        Switch(
                            checked = includeImageDataOnCopy,
                            onCheckedChange = {
                                includeImageDataOnCopy = it
                                StorageManager.putBoolean(context, "superisland_copy_image_data", it)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (pagingItems.itemCount == 0) {
                        Text("暂无超级岛历史记录", style = textStyles.body2, color = colorScheme.onSurfaceVariantSummary)
                    } else {
                        SuperIslandHistoryListBlock(
                            pagingItems = pagingItems,
                            getCachedAppInfo = getCachedAppInfo,
                            expandedGroups = uiState.expandedGroups,
                            includeImageDataOnCopy = includeImageDataOnCopy,
                            onToggleGroup = { packageName -> viewModel.toggleGroupExpansion(packageName) },
                            onDeleteGroup = { packageName -> viewModel.deleteGroup(packageName) },
                            onDeleteEntry = { id -> viewModel.deleteEntry(id) },
                            loadEntryDetail = { id -> viewModel.loadEntryDetail(id) },
                            deleteWidthPx = deleteWidthPx,
                            deleteWidth = deleteWidth
                        )
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuperIslandHistoryListBlock(
    pagingItems: androidx.paging.compose.LazyPagingItems<GroupedSuperIslandHistory>,
    getCachedAppInfo: (String?) -> Pair<String, Bitmap?>,
    expandedGroups: Set<String>,
    includeImageDataOnCopy: Boolean,
    onToggleGroup: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    loadEntryDetail: suspend (Long) -> SuperIslandHistoryEntry?,
    deleteWidthPx: Float,
    deleteWidth: Dp
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            count = pagingItems.itemCount,
            key = { index -> pagingItems[index]?.packageName ?: "group-$index" }
        ) { index ->
            val group = pagingItems[index] ?: return@items
            val groupKey = group.packageName
            val anchoredDraggableState = remember(groupKey, group.entries.size) {
                AnchoredDraggableState(
                    initialValue = SuperIslandDragValue.Center
                )
            }

            val anchors = remember(groupKey, deleteWidthPx) {
                DraggableAnchors {
                    SuperIslandDragValue.Center at 0f
                    SuperIslandDragValue.End at -deleteWidthPx
                }
            }

            LaunchedEffect(anchoredDraggableState, anchors) {
                anchoredDraggableState.updateAnchors(anchors)
            }

            val offset = remember(
                anchoredDraggableState.currentValue,
                anchoredDraggableState.offset
            ) {
                when {
                    anchoredDraggableState.currentValue == SuperIslandDragValue.End -> -deleteWidthPx
                    anchoredDraggableState.offset.isNaN() -> 0f
                    else -> anchoredDraggableState.offset
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .anchoredDraggable(
                            state = anchoredDraggableState,
                            orientation = Orientation.Horizontal
                        )
                        .offset { IntOffset(offset.roundToInt(), 0) }
                ) {
                    SuperIslandHistoryGroupCard(
                        group = group,
                        getCachedAppInfo = getCachedAppInfo,
                        includeImageDataOnCopy = includeImageDataOnCopy,
                        isExpanded = expandedGroups.contains(groupKey),
                        onToggleExpand = { onToggleGroup(groupKey) },
                        loadEntryDetail = loadEntryDetail,
                        deleteWidthPx = deleteWidthPx,
                        deleteWidth = deleteWidth,
                        onDeleteEntry = onDeleteEntry
                    )
                }

                if (anchoredDraggableState.currentValue == SuperIslandDragValue.End) {
                    SuperIslandDeleteButton(
                        onClick = {
                            coroutineScope.launch {
                                anchoredDraggableState.snapTo(SuperIslandDragValue.Center)
                            }
                            onDeleteGroup(groupKey)
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(deleteWidth)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SuperIslandHistoryGroupCard(
    group: GroupedSuperIslandHistory,
    getCachedAppInfo: (String?) -> Pair<String, Bitmap?>,
    includeImageDataOnCopy: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    loadEntryDetail: suspend (Long) -> SuperIslandHistoryEntry?,
    deleteWidthPx: Float,
    deleteWidth: Dp,
    onDeleteEntry: (Long) -> Unit
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val headerEntry = group.entries.firstOrNull()
    val groupTitle = headerEntry?.appName?.takeIf { !it.isNullOrBlank() }
        ?: headerEntry?.title?.takeIf { !it.isNullOrBlank() }
        ?: group.packageName
    val iconPackage = remember(headerEntry, group.packageName) {
        headerEntry?.mappedPackage?.takeIf { !it.isNullOrBlank() }
            ?: headerEntry?.originalPackage?.takeIf { !it.isNullOrBlank() }
            ?: group.packageName.takeIf { group.packageName != "(未知应用)" }
    }
    val appIconBitmap = rememberAppIconBitmap(iconPackage)
    val latestTimestamp = remember(headerEntry?.id) {
        headerEntry?.let { formatTimestamp(it.id) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 8.dp,
        insideMargin = PaddingValues(12.dp),
        colors = CardDefaults.defaultColors(
            color = colorScheme.surface,
            contentColor = colorScheme.onSurface
        ),
        showIndication = !isExpanded,
        pressFeedbackType = if (isExpanded) PressFeedbackType.None else PressFeedbackType.Sink
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuperIslandAppIcon(appIconBitmap, iconPackage, 48.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(groupTitle, style = textStyles.body1, color = colorScheme.onSurface)
                    Text(
                        text = "${group.packageName} · ${group.entries.size} 条记录",
                        style = textStyles.body2,
                        color = colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    latestTimestamp?.let {
                        Text(
                            text = "最新时间: $it",
                            style = textStyles.body2,
                            color = colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = if (isExpanded) "收起" else "展开",
                    style = textStyles.body2,
                    color = colorScheme.primary
                )
            }

            if (isExpanded) {
                group.entries.forEachIndexed { index, entry ->
                    val entryAnchoredDraggableState = remember(entry.id) {
                        AnchoredDraggableState(
                            initialValue = SuperIslandDragValue.Center
                        )
                    }

                    val entryAnchors = remember(entry.id, deleteWidthPx) {
                        DraggableAnchors {
                            SuperIslandDragValue.Center at 0f
                            SuperIslandDragValue.End at -deleteWidthPx
                        }
                    }

                    LaunchedEffect(entryAnchoredDraggableState, entryAnchors) {
                        entryAnchoredDraggableState.updateAnchors(entryAnchors)
                    }

                    val entryOffset = remember(
                        entryAnchoredDraggableState.currentValue,
                        entryAnchoredDraggableState.offset
                    ) {
                        when {
                            entryAnchoredDraggableState.currentValue == SuperIslandDragValue.End -> -deleteWidthPx
                            entryAnchoredDraggableState.offset.isNaN() -> 0f
                            else -> entryAnchoredDraggableState.offset
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .anchoredDraggable(
                                    state = entryAnchoredDraggableState,
                                    orientation = Orientation.Horizontal
                                )
                                .offset { IntOffset(entryOffset.roundToInt(), 0) }
                        ) {
                            SuperIslandHistoryEntryCard(
                                entry = entry,
                                includeImageDataOnCopy = includeImageDataOnCopy,
                                appIconBitmap = appIconBitmap,
                                iconPackage = iconPackage,
                                loadEntryDetail = loadEntryDetail
                            )
                        }

                        if (entryAnchoredDraggableState.currentValue == SuperIslandDragValue.End) {
                            SuperIslandDeleteButton(
                                onClick = {
                                    coroutineScope.launch {
                                        entryAnchoredDraggableState.snapTo(SuperIslandDragValue.Center)
                                    }
                                    onDeleteEntry(entry.id)
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(deleteWidth)
                                    .fillMaxHeight()
                            )
                        }
                    }

                    if (index < group.entries.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = colorScheme.outline)
                    }
                }
            } else {
                val previewList = group.entries.take(3)
                previewList.forEachIndexed { index, entry ->
                    SuperIslandHistorySummaryRow(
                        entry = entry,
                        includeImageDataOnCopy = includeImageDataOnCopy,
                        appIconBitmap = appIconBitmap,
                        iconPackage = iconPackage,
                        loadEntryDetail = loadEntryDetail
                    )
                    if (index < previewList.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                if (group.entries.size > previewList.size) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "... 共${group.entries.size}条，点击展开",
                        style = textStyles.body2,
                        color = colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }
}

@Composable
private fun SuperIslandHistorySummaryRow(
    entry: SuperIslandHistoryEntry,
    includeImageDataOnCopy: Boolean,
    appIconBitmap: ImageBitmap?,
    iconPackage: String?,
    loadEntryDetail: suspend (Long) -> SuperIslandHistoryEntry?
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val titleText = entry.title?.takeIf { it.isNotBlank() }
        ?: entry.appName?.takeIf { it.isNotBlank() }
        ?: entry.mappedPackage?.takeIf { it.isNotBlank() }
        ?: entry.originalPackage?.takeIf { it.isNotBlank() }
        ?: "超级岛事件"

    val displayTitle = titleText.let { SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it) }
    val formattedTimestamp = remember(entry.id) { formatTimestamp(entry.id) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    triggerFloatingReplica(context, entry)
                },
                onLongClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val full = try { loadEntryDetail(entry.id) } catch (_: Exception) { null }
                        val final = full ?: entry
                        val text = buildEntryCopyText(final, includeImageDataOnCopy)
                        withContext(Dispatchers.Main) {
                            copyEntryToClipboard(context, text)
                        }
                    }
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SuperIslandAppIcon(appIconBitmap, iconPackage, 44.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(displayTitle, style = textStyles.body2, color = colorScheme.onSurface)
            val summaryText = entry.text
            if (!summaryText.isNullOrBlank()) {
                val summaryDisplay = if (includeImageDataOnCopy) summaryText else sanitizeImageContent(summaryText, false)
                Text(
                    text = summaryDisplay,
                    style = textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = formattedTimestamp,
                style = textStyles.body2,
                color = colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.picMap.isNotEmpty()) {
                Text(
                    text = "包含图片 ${entry.picMap.size} 张",
                    style = textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuperIslandHistoryEntryCard(
    entry: SuperIslandHistoryEntry,
    includeImageDataOnCopy: Boolean,
    appIconBitmap: ImageBitmap?,
    iconPackage: String?,
    loadEntryDetail: suspend (Long) -> SuperIslandHistoryEntry?
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val formattedTimestamp = remember(entry.id) { formatTimestamp(entry.id) }
    val sanitizedDetail = remember(entry.text, includeImageDataOnCopy) {
        val detail = entry.text
        if (detail.isNullOrBlank()) null else if (includeImageDataOnCopy) detail else sanitizeImageContent(detail, false)
    }
    val sanitizedParamV2 = remember(entry.paramV2Raw, includeImageDataOnCopy) {
        entry.paramV2Raw?.takeIf { it.isNotBlank() }?.let {
            if (includeImageDataOnCopy) it else sanitizeImageContent(it, false)
        }
    }
    val sanitizedPayload = remember(entry.rawPayload, includeImageDataOnCopy) {
        entry.rawPayload?.takeIf { it.isNotBlank() }?.let {
            if (includeImageDataOnCopy) it else sanitizeImageContent(it, false)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    triggerFloatingReplica(context, entry)
                },
                onLongClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val full = try { loadEntryDetail(entry.id) } catch (_: Exception) { null }
                        val final = full ?: entry
                        val text = buildEntryCopyText(final, includeImageDataOnCopy)
                        withContext(Dispatchers.Main) {
                            copyEntryToClipboard(context, text)
                        }
                    }
                }
            )
    ) {
        val titleText = entry.appName?.takeIf { it.isNotBlank() }
            ?: entry.title?.takeIf { it.isNotBlank() }
            ?: entry.mappedPackage?.takeIf { it.isNotBlank() }
            ?: entry.originalPackage?.takeIf { it.isNotBlank() }
            ?: "超级岛事件"
        val displayTitle = titleText.let { SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SuperIslandAppIcon(appIconBitmap, iconPackage, 48.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(displayTitle, style = textStyles.body1, color = colorScheme.onSurface)
                sanitizedDetail?.let {
                    Text(it, style = textStyles.body2, color = colorScheme.onSurfaceVariantSummary)
                }
            }
        }

        if (entry.picMap.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                entry.picMap.forEach { (key, data) ->
                    val displayKey = key.ifBlank { "(未命名图片)" }
                    SuperIslandHistoryImage(displayKey, data)
                }
            }
        }

        val mappedPackage = entry.mappedPackage
        if (!mappedPackage.isNullOrBlank()) {
            Text("映射包名: $mappedPackage", style = textStyles.body2, color = colorScheme.outline)
        }
        val originalPackage = entry.originalPackage
        if (!originalPackage.isNullOrBlank()) {
            Text("原始包名: $originalPackage", style = textStyles.body2, color = colorScheme.outline)
        }
        val sourceDevice = entry.sourceDeviceUuid
        if (!sourceDevice.isNullOrBlank()) {
            Text("来源设备: $sourceDevice", style = textStyles.body2, color = colorScheme.outline)
        }

        Text(
            text = formattedTimestamp,
            style = textStyles.body2,
            color = colorScheme.outline
        )

        sanitizedParamV2?.let {
            Text(it, style = textStyles.body2, color = colorScheme.onSurfaceVariantSummary)
        }

        var loadedDetail by remember { mutableStateOf<SuperIslandHistoryEntry?>(null) }
        val displayPayload = remember(loadedDetail, sanitizedPayload, includeImageDataOnCopy) {
            loadedDetail?.rawPayload?.takeIf { !it.isNullOrBlank() }?.let {
                if (includeImageDataOnCopy) it else sanitizeImageContent(it, false)
            } ?: sanitizedPayload
        }
        if (!displayPayload.isNullOrBlank()) {
            Text(
                text = displayPayload,
                style = textStyles.body2,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            TextButton(onClick = {
                coroutineScope.launch {
                    val full = try { loadEntryDetail(entry.id) } catch (_: Exception) { null }
                    if (full != null) {
                        loadedDetail = full
                    }
                }
            }) {
                Text("加载详情")
            }
        }
    }
}

@Composable
private fun SuperIslandHistoryImage(imageKey: String, data: String, modifier: Modifier = Modifier) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val context = LocalContext.current

    val bitmap by produceState<Bitmap?>(initialValue = SuperIslandImageCache.get(data), key1 = data) {
        val cached = SuperIslandImageCache.get(data)
        if (cached != null) {
            value = cached
            return@produceState
        }

        val loaded = withContext(Dispatchers.IO) {
            try {
                val resolved = try {
                    SuperIslandImageStore.resolve(context, data) ?: data
                } catch (_: Exception) { data }

                val decoded = when {
                    DataUrlUtils.isDataUrl(resolved) -> DataUrlUtils.decodeDataUrlToBitmap(resolved)
                    resolved.startsWith("http", ignoreCase = true) -> downloadBitmap(context, resolved)
                    else -> null
                }
                decoded?.let { SuperIslandImageCache.put(data, it) }
            } catch (_: Exception) {
                null
            }
        }

        value = loaded
    }

    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = imageKey,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = data.take(120),
                style = textStyles.body2,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.surfaceVariant)
                    .padding(8.dp)
            )
        }
        if (imageKey.isNotBlank()) {
            Text(
                text = imageKey,
                style = textStyles.body2,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun rememberAppIconBitmap(packageName: String?): ImageBitmap? {
    val target = remember(packageName) { packageName?.takeIf { it.isNotBlank() } }
    val context = LocalContext.current
    val iconUpdateKey by AppRepository.iconUpdates.collectAsState()
    val bitmapState = produceState<ImageBitmap?>(initialValue = null, key1 = target, key2 = iconUpdateKey) {
        if (target == null) {
            value = null
            return@produceState
        }
        val cached = AppRepository.getExternalAppIcon(context, target)
        if (cached != null) {
            value = cached.asImageBitmap()
            return@produceState
        }
        val fetched = withContext(Dispatchers.IO) {
            AppRepository.getAppIconWithAutoRequest(context, target)
        }
        value = fetched?.asImageBitmap()
    }
    return bitmapState.value
}

@Composable
private fun SuperIslandAppIcon(
    iconBitmap: ImageBitmap?,
    iconPackage: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = iconPackage,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        val fallback = remember(iconPackage) {
            iconPackage?.substringAfterLast('.')
                ?.takeLast(2)
                ?.uppercase(Locale.getDefault())
                ?: "APP"
        }
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fallback,
                style = textStyles.footnote1,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}

private suspend fun downloadBitmap(context: Context, urlString: String, timeoutMs: Int = 5_000): Bitmap? {
    return try {
        SuperIslandImageUtil.loadBitmapSuspend(context, urlString, timeoutMs)
    } catch (_: Exception) { null }
}

private fun decodeSampledBitmap(bytes: ByteArray, maxDimension: Int): Bitmap? {
    if (bytes.isEmpty()) return null
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
    val sampleSize = computeInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, maxDimension)
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
}

private fun computeInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sampleSize = 1
    var largestSide = max(width, height)
    while (largestSide / sampleSize > maxDimension) {
        sampleSize *= 2
    }
    return sampleSize
}

private object SuperIslandImageCache {
    private const val MAX_CACHE_SIZE = 32
    private val cache = object : LinkedHashMap<String, Bitmap>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    fun get(key: String): Bitmap? = synchronized(this) {
        val cached = cache[key]
        if (cached != null && cached.isRecycled) {
            cache.remove(key)
            return@synchronized null
        }
        cached
    }

    fun put(key: String, bitmap: Bitmap): Bitmap {
        if (bitmap.isRecycled) return bitmap
        val normalized = normalizeBitmap(bitmap)
        synchronized(this) {
            cache[key] = normalized
        }
        return normalized
    }

    private fun normalizeBitmap(source: Bitmap): Bitmap {
        if (source.isRecycled) return source
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return source

        var working = source
        val largestSide = max(width, height)
        if (largestSide > SUPER_ISLAND_IMAGE_MAX_DIMENSION) {
            val scale = SUPER_ISLAND_IMAGE_MAX_DIMENSION.toFloat() / largestSide.toFloat()
            val targetWidth = max(1, (width * scale).roundToInt())
            val targetHeight = max(1, (height * scale).roundToInt())
            working = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        }

        if (working.config == Bitmap.Config.HARDWARE) {
            working.copy(Bitmap.Config.ARGB_8888, false)?.let { working = it }
        }
        if (working !== source && !source.isRecycled) {
            try { source.recycle() } catch (_: Exception) {}
        }
        return working
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return try {
        DateFormat.format("yyyy-MM-dd HH:mm:ss", Date(timestamp)).toString()
    } catch (_: Exception) {
        timestamp.toString()
    }
}

private fun buildEntryCopyText(
    entry: SuperIslandHistoryEntry,
    includeImageDataOnCopy: Boolean
): String {
    return buildString {
        appendLine("id: ${entry.id}")
        appendLine("timestamp: ${formatTimestamp(entry.id)}")
        entry.sourceDeviceUuid?.takeIf { it.isNotBlank() }?.let {
            appendLine("sourceDeviceUuid: $it")
        }
        entry.originalPackage?.takeIf { it.isNotBlank() }?.let {
            appendLine("originalPackage: $it")
        }
        entry.mappedPackage?.takeIf { it.isNotBlank() }?.let {
            appendLine("mappedPackage: $it")
        }
        entry.appName?.takeIf { it.isNotBlank() }?.let {
            appendLine("appName: $it")
        }
        entry.title?.takeIf { it.isNotBlank() }?.let {
            appendLine("title: $it")
        }
        entry.text?.takeIf { it.isNotBlank() }?.let {
            appendLine("text: ${sanitizeImageContent(it, includeImageDataOnCopy)}")
        }
        if (entry.picMap.isNotEmpty()) {
            appendLine("picMap:")
            entry.picMap.forEach { (label, data) ->
                val finalLabel = label.ifBlank { "(未命名图片)" }
                val finalData = if (includeImageDataOnCopy) data else "图片"
                appendLine("  $finalLabel: $finalData")
            }
        }
        entry.paramV2Raw?.takeIf { it.isNotBlank() }?.let {
            appendMultilineField("paramV2Raw", it, includeImageDataOnCopy)
        }
        entry.rawPayload?.takeIf { it.isNotBlank() }?.let {
            appendMultilineField("rawPayload", it, includeImageDataOnCopy)
        }
    }.trim()
}

private fun copyEntryToClipboard(context: Context, content: String) {
    if (content.isBlank()) {
        Toast.makeText(context, "当前条目无可复制内容", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("super_island_entry", content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "已复制原始消息到剪贴板", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Logger.e("NotifyRelay", "复制超级岛原始消息失败", e)
        Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show()
    }
}

private fun triggerFloatingReplica(context: Context, entry: SuperIslandHistoryEntry) {
    val sourceId = entry.mappedPackage?.takeIf { it.isNotBlank() }
        ?: entry.originalPackage?.takeIf { it.isNotBlank() }
        ?: entry.appName?.takeIf { it.isNotBlank() }
        ?: entry.id.toString()
    val title = entry.title?.takeIf { it.isNotBlank() }
        ?: entry.appName?.takeIf { it.isNotBlank() }
        ?: entry.mappedPackage?.takeIf { it.isNotBlank() }
        ?: entry.originalPackage?.takeIf { it.isNotBlank() }
    FloatingReplicaManager.showFloating(
        context = context,
        sourceId = sourceId,
        title = title,
        text = entry.text,
        paramV2Raw = entry.paramV2Raw,
        picMap = entry.picMap.takeIf { it.isNotEmpty() },
        isLocked = false
    )
}

private fun sanitizeImageContent(source: String, includeImageDataOnCopy: Boolean): String {
    if (includeImageDataOnCopy) return source
    var sanitized = DATA_URL_REGEX.replace(source) { "图片" }
    sanitized = IMAGE_URL_REGEX.replace(sanitized) { "图片" }
    return sanitized
}

private val DATA_URL_REGEX = Regex(
    pattern = "data:[^,]+;base64,[^\\s\"]+",
    options = setOf(RegexOption.IGNORE_CASE)
)

private val IMAGE_URL_REGEX = Regex(
    pattern = "https?:[^\\s\"]+\\.(?:png|jpe?g|gif|webp|bmp|svg)",
    options = setOf(RegexOption.IGNORE_CASE)
)

private fun formatMultilineContent(content: String): List<String> {
    if (content.isBlank()) return emptyList()
    prettyPrintJson(content)?.let { return it }
    return wrapPlainText(content)
}

private fun prettyPrintJson(text: String): List<String>? {
    val firstNonWhitespace = text.firstOrNull { !it.isWhitespace() } ?: return emptyList()
    if (firstNonWhitespace != '{' && firstNonWhitespace != '[') return null
    return try {
        val jsonElement = JsonParser.parseString(text)
        val pretty = prettyGson.toJson(jsonElement)
        pretty.lineSequence()
            .flatMap { wrapPlainText(it).asSequence() }
            .toList()
    } catch (_: Exception) {
        null
    }
}

private fun wrapPlainText(text: String): List<String> {
    val firstNonWhitespaceIndex = text.indexOfFirst { !it.isWhitespace() }
    val indent = if (firstNonWhitespaceIndex > 0) text.substring(0, firstNonWhitespaceIndex) else ""
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()
    if (trimmed.length <= SANITIZED_LINE_WRAP) return listOf(indent + trimmed)
    val result = mutableListOf<String>()
    var remaining = trimmed
    while (remaining.length > SANITIZED_LINE_WRAP) {
        val window = remaining.substring(0, SANITIZED_LINE_WRAP)
        val breakIndex = window.lastIndexOfAny(WRAP_BREAK_CHARS)
        val cut = if (breakIndex <= 0) SANITIZED_LINE_WRAP else breakIndex + 1
        val segment = remaining.substring(0, cut).trimEnd()
        result += indent + segment
        remaining = remaining.substring(cut).trimStart()
    }
    if (remaining.isNotEmpty()) {
        result += indent + remaining
    }
    return result
}

private val prettyGson by lazy { GsonBuilder().setPrettyPrinting().create() }

private const val SANITIZED_LINE_WRAP = 80
private val WRAP_BREAK_CHARS = charArrayOf(',', ' ', ';', ')', ']', '}', '"')

private fun StringBuilder.appendMultilineField(
    label: String,
    content: String,
    includeImageDataOnCopy: Boolean
) {
    val sanitized = sanitizeImageContent(content, includeImageDataOnCopy).trim()
    if (sanitized.isBlank()) return
    appendLine("$label:")
    formatMultilineContent(sanitized).forEach {
        appendLine("  $it")
    }
}
