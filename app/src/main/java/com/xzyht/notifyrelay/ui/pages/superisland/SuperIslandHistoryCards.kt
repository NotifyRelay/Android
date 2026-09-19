package com.xzyht.notifyrelay.ui.pages.superisland

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapTo
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.feature.notification.superisland.history.SuperIslandHistoryStoreEntry
import com.xzyht.notifyrelay.ui.pages.DeleteButton
import com.xzyht.notifyrelay.ui.pages.DragValue
import com.xzyht.notifyrelay.ui.pages.formatTimestamp
import com.xzyht.notifyrelay.ui.viewmodel.GroupedSuperIslandHistory
import github.xzynine.superislandui.floating.common.SuperIslandImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SuperIslandHistoryListBlock(
    pagingItems: androidx.paging.compose.LazyPagingItems<GroupedSuperIslandHistory>,
    expandedGroups: Set<String>,
    includeImageDataOnCopy: Boolean,
    onToggleGroup: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    loadEntryDetail: suspend (Long) -> SuperIslandHistoryStoreEntry?,
    deleteWidthPx: Float,
    deleteWidth: Dp,
) {
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            count = pagingItems.itemCount,
            key = { index -> pagingItems[index]?.packageName ?: "group-$index" },
        ) { index ->
            val group = pagingItems[index] ?: return@items
            val groupKey = group.packageName
            val anchoredDraggableState =
                remember(groupKey, group.entries.size) {
                    AnchoredDraggableState(
                        initialValue = DragValue.Center,
                    )
                }

            val anchors =
                remember(groupKey, deleteWidthPx) {
                    DraggableAnchors {
                        DragValue.Center at 0f
                        DragValue.End at -deleteWidthPx
                    }
                }

            LaunchedEffect(anchoredDraggableState, anchors) {
                anchoredDraggableState.updateAnchors(anchors)
            }

            val offset by remember(anchoredDraggableState.currentValue, anchoredDraggableState.offset) {
                derivedStateOf {
                    when {
                        anchoredDraggableState.currentValue == DragValue.End -> -deleteWidthPx
                        anchoredDraggableState.offset.isNaN() -> 0f
                        else -> anchoredDraggableState.offset
                    }
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
                            ).offset { IntOffset(offset.roundToInt(), 0) },
                ) {
                    SuperIslandHistoryGroupCard(
                        group = group,
                        includeImageDataOnCopy = includeImageDataOnCopy,
                        isExpanded = expandedGroups.contains(groupKey),
                        onToggleExpand = { onToggleGroup(groupKey) },
                        loadEntryDetail = loadEntryDetail,
                        deleteWidthPx = deleteWidthPx,
                        deleteWidth = deleteWidth,
                        onDeleteEntry = onDeleteEntry,
                    )
                }

                if (anchoredDraggableState.currentValue == DragValue.End) {
                    DeleteButton(
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .width(deleteWidth)
                                .fillMaxHeight(),
                        onClick = {
                            coroutineScope.launch {
                                anchoredDraggableState.snapTo(DragValue.Center)
                            }
                            onDeleteGroup(groupKey)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun SuperIslandHistoryGroupCard(
    group: GroupedSuperIslandHistory,
    includeImageDataOnCopy: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    loadEntryDetail: suspend (Long) -> SuperIslandHistoryStoreEntry?,
    deleteWidthPx: Float,
    deleteWidth: Dp,
    onDeleteEntry: (Long) -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val coroutineScope = rememberCoroutineScope()
    val headerEntry = group.entries.firstOrNull()
    val groupTitle =
        headerEntry?.appName?.takeIf { it.isNotBlank() }
            ?: headerEntry?.title?.takeIf { it.isNotBlank() }
            ?: group.packageName
    val iconPackage =
        remember(headerEntry, group.packageName) {
            headerEntry?.mappedPackage?.takeIf { it.isNotBlank() }
                ?: headerEntry?.originalPackage?.takeIf { it.isNotBlank() }
                ?: group.packageName.takeIf { group.packageName != "(未知应用)" }
        }
    val appIconBitmap = rememberAppIconBitmap(iconPackage)
    val latestTimestamp =
        remember(headerEntry?.id) {
            headerEntry?.let { formatTimestamp(it.id) }
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 8.dp,
        insideMargin = PaddingValues(12.dp),
        colors =
            CardDefaults.defaultColors(
                color = colorScheme.surface,
                contentColor = colorScheme.onSurface,
            ),
        showIndication = !isExpanded,
        pressFeedbackType = if (isExpanded) PressFeedbackType.None else PressFeedbackType.Sink,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SuperIslandAppIcon(appIconBitmap, iconPackage, 48.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(groupTitle, style = textStyles.body1, color = colorScheme.onSurface)
                    Text(
                        text = "${group.packageName} · ${group.entries.size} 条记录",
                        style = textStyles.body2,
                        color = colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    latestTimestamp?.let {
                        Text(
                            text = "最新时间: $it",
                            style = textStyles.body2,
                            color = colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = if (isExpanded) "收起" else "展开",
                    style = textStyles.body2,
                    color = colorScheme.primary,
                )
            }

            if (isExpanded) {
                group.entries.forEachIndexed { index, entry ->
                    val entryAnchoredDraggableState =
                        remember(entry.id) {
                            AnchoredDraggableState(
                                initialValue = DragValue.Center,
                            )
                        }

                    val entryAnchors =
                        remember(entry.id, deleteWidthPx) {
                            DraggableAnchors {
                                DragValue.Center at 0f
                                DragValue.End at -deleteWidthPx
                            }
                        }

                    LaunchedEffect(entryAnchoredDraggableState, entryAnchors) {
                        entryAnchoredDraggableState.updateAnchors(entryAnchors)
                    }

                    val entryOffset by remember(entryAnchoredDraggableState.currentValue, entryAnchoredDraggableState.offset) {
                        derivedStateOf {
                            when {
                                entryAnchoredDraggableState.currentValue == DragValue.End -> -deleteWidthPx
                                entryAnchoredDraggableState.offset.isNaN() -> 0f
                                else -> entryAnchoredDraggableState.offset
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .anchoredDraggable(
                                        state = entryAnchoredDraggableState,
                                        orientation = Orientation.Horizontal,
                                    ).offset { IntOffset(entryOffset.roundToInt(), 0) },
                        ) {
                            SuperIslandHistoryStoreEntryCard(
                                entry = entry,
                                includeImageDataOnCopy = includeImageDataOnCopy,
                                appIconBitmap = appIconBitmap,
                                iconPackage = iconPackage,
                                loadEntryDetail = loadEntryDetail,
                            )
                        }

                        if (entryAnchoredDraggableState.currentValue == DragValue.End) {
                            DeleteButton(
                                modifier =
                                    Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(deleteWidth)
                                        .fillMaxHeight(),
                                onClick = {
                                    coroutineScope.launch {
                                        entryAnchoredDraggableState.snapTo(DragValue.Center)
                                    }
                                    onDeleteEntry(entry.id)
                                },
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
                        loadEntryDetail = loadEntryDetail,
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
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SuperIslandHistorySummaryRow(
    entry: SuperIslandHistoryStoreEntry,
    includeImageDataOnCopy: Boolean,
    appIconBitmap: ImageBitmap?,
    iconPackage: String?,
    loadEntryDetail: suspend (Long) -> SuperIslandHistoryStoreEntry?,
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val titleText =
        entry.title?.takeIf { it.isNotBlank() }
            ?: entry.appName?.takeIf { it.isNotBlank() }
            ?: entry.mappedPackage?.takeIf { it.isNotBlank() }
            ?: entry.originalPackage?.takeIf { it.isNotBlank() }
            ?: "超级岛事件"

    val displayTitle = titleText.let { SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it) }
    val formattedTimestamp = remember(entry.id) { formatTimestamp(entry.id) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        triggerFloatingReplica(context, entry)
                    },
                    onLongClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val full =
                                try {
                                    loadEntryDetail(entry.id)
                                } catch (_: Exception) {
                                    null
                                }
                            val final = full ?: entry
                            val text = buildEntryCopyText(final, includeImageDataOnCopy)
                            withContext(Dispatchers.Main) {
                                copyEntryToClipboard(context, text)
                            }
                        }
                    },
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SuperIslandAppIcon(appIconBitmap, iconPackage, 44.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formattedTimestamp,
                style = textStyles.body2,
                color = colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.picMap.isNotEmpty()) {
                Text(
                    text = "包含图片 ${entry.picMap.size} 张",
                    style = textStyles.body2,
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SuperIslandHistoryStoreEntryCard(
    entry: SuperIslandHistoryStoreEntry,
    includeImageDataOnCopy: Boolean,
    appIconBitmap: ImageBitmap?,
    iconPackage: String?,
    loadEntryDetail: suspend (Long) -> SuperIslandHistoryStoreEntry?,
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val formattedTimestamp = remember(entry.id) { formatTimestamp(entry.id) }
    val sanitizedDetail =
        remember(entry.text, includeImageDataOnCopy) {
            val detail = entry.text
            if (detail.isNullOrBlank()) {
                null
            } else if (includeImageDataOnCopy) {
                detail
            } else {
                sanitizeImageContent(detail, false)
            }
        }
    val sanitizedParamV2 =
        remember(entry.paramV2Raw, includeImageDataOnCopy) {
            entry.paramV2Raw?.takeIf { it.isNotBlank() }?.let {
                if (includeImageDataOnCopy) it else sanitizeImageContent(it, false)
            }
        }
    val sanitizedPayload =
        remember(entry.rawPayload, includeImageDataOnCopy) {
            entry.rawPayload?.takeIf { it.isNotBlank() }?.let {
                if (includeImageDataOnCopy) it else sanitizeImageContent(it, false)
            }
        }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        triggerFloatingReplica(context, entry)
                    },
                    onLongClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val full =
                                try {
                                    loadEntryDetail(entry.id)
                                } catch (_: Exception) {
                                    null
                                }
                            val final = full ?: entry
                            val text = buildEntryCopyText(final, includeImageDataOnCopy)
                            withContext(Dispatchers.Main) {
                                copyEntryToClipboard(context, text)
                            }
                        }
                    },
                ),
    ) {
        val titleText =
            entry.appName?.takeIf { it.isNotBlank() }
                ?: entry.title?.takeIf { it.isNotBlank() }
                ?: entry.mappedPackage?.takeIf { it.isNotBlank() }
                ?: entry.originalPackage?.takeIf { it.isNotBlank() }
                ?: "超级岛事件"
        val displayTitle = titleText.let { SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SuperIslandAppIcon(appIconBitmap, iconPackage, 48.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
            color = colorScheme.outline,
        )

        sanitizedParamV2?.let {
            Text(it, style = textStyles.body2, color = colorScheme.onSurfaceVariantSummary)
        }

        var loadedDetail by remember { mutableStateOf<SuperIslandHistoryStoreEntry?>(null) }
        val displayPayload =
            remember(loadedDetail, sanitizedPayload, includeImageDataOnCopy) {
                loadedDetail?.rawPayload?.takeIf { it.isNotBlank() }?.let {
                    if (includeImageDataOnCopy) it else sanitizeImageContent(it, false)
                } ?: sanitizedPayload
            }
        if (!displayPayload.isNullOrBlank()) {
            Text(
                text = displayPayload,
                style = textStyles.body2,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            TextButton(
                text = "加载详情",
                onClick = {
                    coroutineScope.launch {
                        val full =
                            try {
                                loadEntryDetail(entry.id)
                            } catch (_: Exception) {
                                null
                            }
                        if (full != null) {
                            loadedDetail = full
                        }
                    }
                },
            )
        }
    }
}
