package com.xzyht.notifyrelay.ui.pages.history

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.xzyht.notifyrelay.ui.pages.DragValue
import com.xzyht.notifyrelay.ui.pages.history.dateTimeFormatter
import com.xzyht.notifyrelay.ui.viewmodel.GroupedNotifications
import kotlinx.coroutines.launch
import notifyrelay.base.util.Logger
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationListBlock(
    pagingItems: LazyPagingItems<GroupedNotifications>,
    getCachedAppInfo: (String?) -> Pair<String, android.graphics.Bitmap?>,
    expandedGroups: Set<String>,
    installedPackages: Set<String>,
    context: Context,
    colorScheme: Colors,
    textStyles: TextStyles,
    deleteWidthPx: Float,
    deleteWidth: Dp,
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
                                ).offset { IntOffset(offset.roundToInt(), 0) },
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
                            val appInfo: Pair<String, android.graphics.Bitmap?> = getCachedAppInfo(groupKey)
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
                                                            ).offset {
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
                                                                ).width(deleteWidth)
                                                                .fillMaxHeight(),
                                                    )
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
}
