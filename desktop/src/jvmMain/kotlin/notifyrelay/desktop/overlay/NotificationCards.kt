package notifyrelay.desktop.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import notifyrelay.desktop.platform.DesktopIconCodecPort
import top.yukonga.miuix.kmp.basic.Text

/** 卡片展开后自动收起的时长。 */
internal const val NOTIFICATION_COLLAPSE_MS = 3_000L

/** 收起为图标后图标的存活时长，从收起那一刻起算。 */
internal const val NOTIFICATION_ICON_LIFETIME_MS = 12_000L

/** 同时处于展开态的卡片数量上限；超出的排队，等前面的收起后再展开。 */
internal const val NOTIFICATION_MAX_EXPANDED = 2

/** 通知卡片宽度，与媒体卡同宽。 */
internal val NOTIFICATION_CARD_WIDTH = 420.dp

/** 卡片圆角。 */
private val CARD_CORNER = 16.dp

/** 卡片与图标方块底色。 */
private val CARD_BACKGROUND = Color(0xE6000000)

/** 图标方块边长与圆角。 */
private val ICON_SLOT = 24.dp
private val ICON_CORNER = 8.dp

/** 卡片内图标与文本之间的间距。 */
private val ITEM_GAP = 8.dp

/**
 * 通知卡片的显示状态。
 *
 * 展开集合与两套时刻表由本类持有，渲染拆成两处：展开态卡片独占一行，
 * 收起态图标方块并入叠加层的状态行。计时只由 [rememberNotificationCardsState]
 * 的巡检循环推进，两处渲染都只读状态。
 */
internal class NotificationCardsState {
    /** 各条目的展开起始时刻。 */
    internal val expandedAt = mutableStateMapOf<Long, Long>()

    /** 各条目的收起时刻；自该时刻起图标开始计算存活时长。 */
    internal val collapsedAt = mutableStateMapOf<Long, Long>()

    /** 当前处于展开态的条目 id，按到达顺序取前 [NOTIFICATION_MAX_EXPANDED] 条。 */
    val expandedIds: List<Long>
        get() =
            DesktopOverlayStore.notifications
                .filter { collapsedAt[it.id] == null }
                .map { it.id }
                .take(NOTIFICATION_MAX_EXPANDED)

    /**
     * 点击图标方块：把该条目提到展开态。
     *
     * 已满额时先让最早展开的条目收起，点击的这张才能进入展开集合。
     */
    fun expand(id: Long) {
        val now = System.currentTimeMillis()
        val current = expandedIds
        if (current.size >= NOTIFICATION_MAX_EXPANDED) {
            current
                .take(current.size - NOTIFICATION_MAX_EXPANDED + 1)
                .forEach { old ->
                    collapsedAt[old] = now
                    expandedAt.remove(old)
                }
        }
        collapsedAt.remove(id)
        expandedAt[id] = now
    }

    /** 登记某条目开始展开；已登记则保持原起点。 */
    internal fun markExpanded(id: Long) {
        if (expandedAt[id] == null && collapsedAt[id] == null) {
            expandedAt[id] = System.currentTimeMillis()
        }
    }

    /** 巡检一轮：登记新展开项、推进收起、移除到期图标。 */
    internal fun tick(now: Long) {
        val alive = DesktopOverlayStore.notifications.toList()

        val current = expandedIds
        current.forEach { id ->
            if (expandedAt[id] == null) expandedAt[id] = now
        }
        expandedAt.keys.retainAll(current.toSet())

        current
            .filter { now - (expandedAt[it] ?: now) >= NOTIFICATION_COLLAPSE_MS }
            .forEach { id ->
                collapsedAt[id] = now
                expandedAt.remove(id)
            }

        alive
            .filter { entry ->
                val since = collapsedAt[entry.id] ?: return@filter false
                now - since >= NOTIFICATION_ICON_LIFETIME_MS
            }.forEach { DesktopOverlayStore.removeNotification(it) }

        val remaining = DesktopOverlayStore.notifications.map { it.id }.toSet()
        collapsedAt.keys.retainAll(remaining)
        expandedAt.keys.retainAll(remaining)
    }
}

/**
 * 通知卡片状态的进程级持有者。
 *
 * 状态不能挂在组合树的 remember 上：录制尺寸变化时 OverlaySceneRecorder 会被整体重建，
 * 组合状态随之归零，展开起始时刻反复被刷新，卡片就永远攒不满收起阈值、
 * 图标也永远到不了存活上限。计时与渲染因此分家：本对象长期存活，组合树只读它。
 */
internal object NotificationCardsHolder {
    /** 唯一的状态实例。 */
    val state: NotificationCardsState by lazy { NotificationCardsState() }

    init {
        // 计时统一由 OverlayClock 推进，本对象不再自建循环
        OverlayClock.register { now -> state.tick(now) }
    }

    /**
     * 登记新到达的条目。
     *
     * 展开起点在此写入而非等下一次 tick：巡检有粒度，等到下一轮才登记
     * 会让起点晚于真实到达时刻，收起随之整体推迟。
     */
    fun onArrived(id: Long) {
        state.markExpanded(id)
    }
}

/**
 * 取得通知卡片状态。
 *
 * 状态与计时都进程级持有（见 NotificationCardsHolder 与 OverlayClock），
 * 因此录制器重建不会重置收起与移除的进度。
 */
@Composable internal fun rememberNotificationCardsState(): NotificationCardsState = NotificationCardsHolder.state

/**
 * 展开态通知卡片列：每条独占一行并居中。
 *
 * 条目为空时不占位，整列不参与布局。
 */
@Composable
internal fun NotificationExpandedCards(state: NotificationCardsState) {
    val expanded = state.expandedIds
    if (expanded.isEmpty()) return
    val entries = DesktopOverlayStore.notifications.associateBy { it.id }

    Column(
        modifier = Modifier.width(NOTIFICATION_CARD_WIDTH),
        verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        expanded.forEach { id ->
            val entry = entries[id] ?: return@forEach
            NotificationCard(entry)
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
internal fun NotificationIconItem(
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
                .trackInteractiveRegion(interactiveRegions, regionKey, interactiveEnabled)
                .let { if (interactiveEnabled) it.clickable { onClick() } else it },
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
