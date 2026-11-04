package notifyrelay.desktop.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import notifyrelay.desktop.platform.DesktopIconCodecPort
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.roundToInt

/** 卡片展开后自动收起的时长。 */
internal const val NOTIFICATION_COLLAPSE_MS = 3_000L

/** 收起为图标后图标的存活时长，从收起那一刻起算。 */
internal const val NOTIFICATION_ICON_LIFETIME_MS = 12_000L

/** 同时处于展开态的卡片数量上限；超出的排队，等前面的收起后再展开。 */
internal const val NOTIFICATION_MAX_EXPANDED = 2

/** 通知内容宽度，与媒体卡同宽。 */
internal val NOTIFICATION_CARD_WIDTH = 420.dp

/** 状态巡检间隔：驱动收起与图标到期。 */
private const val NOTIFICATION_TICK_MS = 200L

/** 卡片圆角。 */
private val CARD_CORNER = 16.dp

/** 卡片与图标方块底色。 */
private val CARD_BACKGROUND = Color(0xE6000000)

/** 图标方块边长与圆角。 */
private val ICON_SLOT = 24.dp
private val ICON_CORNER = 8.dp

/** 卡片内图标与文本、图标方块之间的间距。 */
private val ITEM_GAP = 8.dp

/**
 * 通知卡片层。
 *
 * 展示形式与 Gamebar 通知块同口径：最多 [NOTIFICATION_MAX_EXPANDED] 张卡片同时展开，
 * 其余按到达顺序排队，等前面的收起后再展开；每张展开 [NOTIFICATION_COLLAPSE_MS] 后
 * 收起为图标方块，图标自收起时刻起存活 [NOTIFICATION_ICON_LIFETIME_MS] 后移除。
 *
 * 所有计时集中在本组件的巡检循环内：展开开始、收起与到期的判定不分散在各卡片里，
 * 排队中的卡片因此能复用同一套规则。
 *
 * @param interactiveEnabled 开启时图标方块可点击重新展开
 * @param interactiveRegions 可交互区域集合，供原生层切换点击穿透
 */
@Composable
internal fun NotificationCardsLayer(
    interactiveEnabled: Boolean,
    interactiveRegions: androidx.compose.runtime.snapshots.SnapshotStateMap<String, java.awt.Rectangle>,
) {
    val entries = DesktopOverlayStore.notifications
    if (entries.isEmpty()) return

    // 各条目的展开起始时刻与收起时刻；两者都只在本组件的巡检循环里写入
    val expandedAt = remember { mutableStateMapOf<Long, Long>() }
    val collapsedAt = remember { mutableStateMapOf<Long, Long>() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(NOTIFICATION_TICK_MS)
            val now = System.currentTimeMillis()
            val alive = DesktopOverlayStore.notifications.toList()

            // 展开集合按到达顺序取前若干条未收起的条目：前面的收起后，排队者自然顶上
            val expandedIds =
                alive
                    .asSequence()
                    .filter { collapsedAt[it.id] == null }
                    .map { it.id }
                    .take(NOTIFICATION_MAX_EXPANDED)
                    .toList()

            // 新进入展开集合的条目从未登记时才起算，重新展开由点击处登记
            expandedIds.forEach { id ->
                if (expandedAt[id] == null) expandedAt[id] = now
            }
            expandedAt.keys.retainAll(expandedIds.toSet())

            expandedIds
                .filter { now - (expandedAt[it] ?: now) >= NOTIFICATION_COLLAPSE_MS }
                .forEach { id ->
                    collapsedAt[id] = now
                    expandedAt.remove(id)
                }

            val expiredIcons =
                alive.filter { entry ->
                    val since = collapsedAt[entry.id] ?: return@filter false
                    now - since >= NOTIFICATION_ICON_LIFETIME_MS
                }
            expiredIcons.forEach { DesktopOverlayStore.removeNotification(it) }

            val remaining = DesktopOverlayStore.notifications.map { it.id }.toSet()
            collapsedAt.keys.retainAll(remaining)
            expandedAt.keys.retainAll(remaining)
        }
    }

    val expandedIds =
        entries
            .filter { collapsedAt[it.id] == null }
            .map { it.id }
            .take(NOTIFICATION_MAX_EXPANDED)
            .toSet()

    Column(
        modifier = Modifier.width(NOTIFICATION_CARD_WIDTH),
        verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        entries.forEach { entry ->
            key(entry.id) {
                if (entry.id in expandedIds) {
                    NotificationCard(entry)
                }
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ITEM_GAP),
            verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
        ) {
            entries.forEach { entry ->
                if (entry.id in expandedIds) return@forEach
                key(entry.id) {
                    NotificationIcon(
                        entry = entry,
                        interactiveEnabled = interactiveEnabled,
                        interactiveRegions = interactiveRegions,
                        onClick = {
                            // 已满额时先让最早展开的那张收起，点击的这张才能进入展开集合
                            val expanded =
                                entries
                                    .filter { collapsedAt[it.id] == null }
                                    .map { it.id }
                            if (expanded.size >= NOTIFICATION_MAX_EXPANDED) {
                                val now = System.currentTimeMillis()
                                expanded
                                    .take(expanded.size - NOTIFICATION_MAX_EXPANDED + 1)
                                    .forEach { id ->
                                        collapsedAt[id] = now
                                        expandedAt.remove(id)
                                    }
                            }
                            collapsedAt.remove(entry.id)
                            expandedAt[entry.id] = System.currentTimeMillis()
                        },
                    )
                }
            }
        }
    }
}

/**
 * 展开态通知卡片。
 *
 * 图标在左，右侧依次为「应用名 · 设备名」、标题与正文；正文最多两行，超出以省略号截断。
 */
@Composable
private fun NotificationCard(entry: NotificationEntry) {
    val icon = rememberNotificationIcon(entry.iconPng)
    Row(
        modifier =
            Modifier
                .width(NOTIFICATION_CARD_WIDTH)
                .clip(RoundedCornerShape(CARD_CORNER))
                .background(CARD_BACKGROUND)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(ITEM_GAP),
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = entry.appName,
                modifier = Modifier.size(ICON_SLOT).clip(RoundedCornerShape(ICON_CORNER)),
                contentScale = ContentScale.Crop,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.sourceLabel,
                color = Color(0xB3FFFFFF),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.title.isNotBlank()) {
                Text(
                    text = entry.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.text.isNotBlank()) {
                Text(
                    text = entry.text,
                    color = Color(0xE6FFFFFF),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 收起态图标方块：唯一可点击的通知元素，点击重新展开。
 *
 * 只有可点击元素登记可交互区域，点击穿透仅在鼠标位于图标方块上时关闭。
 */
@Composable
private fun NotificationIcon(
    entry: NotificationEntry,
    interactiveEnabled: Boolean,
    interactiveRegions: androidx.compose.runtime.snapshots.SnapshotStateMap<String, java.awt.Rectangle>,
    onClick: () -> Unit,
) {
    val icon = rememberNotificationIcon(entry.iconPng)
    val regionKey = "notification-" + entry.id
    DisposableEffect(regionKey) {
        onDispose { interactiveRegions.remove(regionKey) }
    }
    Box(
        modifier =
            Modifier
                .size(ICON_SLOT)
                .clip(RoundedCornerShape(ICON_CORNER))
                .background(CARD_BACKGROUND)
                .onGloballyPositioned { coords ->
                    if (!interactiveEnabled) return@onGloballyPositioned
                    val pos = coords.localToRoot(Offset.Zero)
                    val sz = coords.size
                    interactiveRegions[regionKey] =
                        java.awt.Rectangle(
                            pos.x.roundToInt(),
                            pos.y.roundToInt(),
                            sz.width,
                            sz.height,
                        )
                }.let { if (interactiveEnabled) it.clickable { onClick() } else it },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = entry.appName,
                modifier = Modifier.size(ICON_SLOT),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(text = entry.appName.take(1), color = Color.White, fontSize = 11.sp)
        }
    }
}

/** 解码通知来源应用图标；无图标或解码失败时返回 null。 */
@Composable
private fun rememberNotificationIcon(png: ByteArray?): ImageBitmap? =
    remember(png) {
        png?.let { bytes -> DesktopIconCodecPort.decodeIconBitmap(bytes) }
    }
