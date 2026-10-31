package notifyrelay.desktop.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import github.xzynine.superislandui.common.AutoScrollText
import github.xzynine.superislandui.floating.bigisland.components.MediaIslandCompose
import github.xzynine.superislandui.floating.common.rememberSuperIslandImagePainter
import github.xzynine.superislandui.model.components.MediaSessionData
import kotlinx.coroutines.delay
import notifyrelay.base.log.Logger
import notifyrelay.desktop.DataLayerDesktop
import notifyrelay.desktop.platform.DesktopOverlaySettings
import notifyrelay.domain.feature.media.MediaSessionSnapshot
import top.yukonga.miuix.kmp.basic.Text
import java.awt.Dimension
import java.awt.Toolkit
import kotlin.math.roundToInt

/**
 * 叠加层窗口：无边框、透明、置顶、点击穿透、不抢焦点。
 *
 * 媒体卡、超级岛与弹幕均在本机用 Compose 直接绘制，
 * 不经位图编码与跨进程传输。
 */
@Composable
internal fun DesktopOverlayWindow() {
    val screen: Dimension = remember { Toolkit.getDefaultToolkit().screenSize }
    // 窗口按物理像素铺满主屏：dp 与像素之间需除以 density，
    // 否则系统缩放 150% 时窗口会被建成 1.5 倍屏幕尺寸
    val density = LocalDensity.current
    val widthDp = with(density) { screen.width.toFloat().toDp() }
    val heightDp = with(density) { screen.height.toFloat().toDp() }
    val windowState =
        rememberWindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition(0.dp, 0.dp),
            width = widthDp,
            height = heightDp,
        )

    Window(
        onCloseRequest = {},
        state = windowState,
        title = "NotifyRelayOverlay",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
    ) {
        // 窗口就绪后定位到主屏左上角并应用点击穿透样式
        LaunchedEffect(Unit) {
            window.setBounds(0, 0, screen.width, screen.height)
            window.isAlwaysOnTop = true
            val applied = DesktopOverlayWindowStyle.apply(window)
            // 置顶与几何监控：系统或其他应用改写 z-order、显示器尺寸变化后自动恢复
            DesktopOverlayTopmostMonitor.start(window)
            Logger.i("DesktopOverlayWin", "叠加层窗口已就绪 点击穿透=" + applied)
        }

        DisposableEffect(Unit) {
            onDispose { DesktopOverlayTopmostMonitor.stop() }
        }

        OverlayContent()
    }
}

/** 叠加层内容：媒体卡 + 超级岛 + 弹幕。 */
@Composable
private fun OverlayContent() {
    // 帧时钟：驱动弹幕位移与到期清理
    var frameNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis {
                frameNow = System.currentTimeMillis()
                // 帧心跳：置顶监控据此判定渲染是否卡死
                DesktopOverlayTopmostMonitor.onFrame()
            }
        }
    }

    // 顶部卡片高度（像素）：作为弹幕轨道起点（对齐 C# 叠加层的 TopOffset）
    var topOffsetPx by remember { mutableFloatStateOf(0f) }
    val layoutDensity = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        // 顶部：媒体卡与超级岛纵向排布
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .onGloballyPositioned { coords ->
                        topOffsetPx = coords.size.height + TOP_CARD_BOTTOM_GAP_PX
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DesktopOverlayStore.mediaSessions.forEach { (deviceUuid, snapshot) ->
                key(deviceUuid) {
                    OverlayMediaItem(
                        snapshot = snapshot,
                        deviceName = mediaDeviceLabel(deviceUuid),
                    )
                }
            }
            DesktopOverlayStore.superIslands.values.forEach { entry ->
                SuperIslandCard(entry)
            }
        }

        // 超级岛超时回收：超过存活时长没有更新的条目自动移除
        LaunchedEffect(Unit) {
            while (true) {
                delay(SUPER_ISLAND_TIMEOUT_MS)
                DesktopOverlayStore.pruneSuperIslands(
                    nonMediaTimeoutMs = SUPER_ISLAND_TIMEOUT_MS,
                    mediaTimeoutMs = SUPER_ISLAND_MEDIA_TIMEOUT_MS,
                )
            }
        }

        // 弹幕层：轨道从顶部卡片下方开始
        DanmakuLayer(now = frameNow, topOffsetPx = topOffsetPx)

        // 扩展点浮窗：余额 / 罗技电池 / 键盘 / 时间
        OverlayFeatureElements(now = frameNow)
    }
}

/**
 * 扩展点浮窗渲染。
 *
 * 只读 [DesktopOverlayStore] 的快照与 [DesktopOverlaySettings] 的锚点配置。
 * 锚点各字段以 Compose mutableState 为背景态，拖动滑块时直接触发重组，浮窗位置实时跟手；
 * 非锚点设置（开关、显示格式等）经 `revision` 观察，避免逐帧阻塞读存储。
 */
@Composable
private fun OverlayFeatureElements(now: Long) {
    // 非锚点设置经 revision 观察
    val revision = DesktopOverlaySettings.revision.value
    val balanceEnabled = remember(revision) { DesktopOverlaySettings.balanceEnabled }
    val logiEnabled = remember(revision) { DesktopOverlaySettings.logiEnabled }
    val keyboardEnabled = remember(revision) { DesktopOverlaySettings.keyboardEnabled }
    val clockEnabled = remember(revision) { DesktopOverlaySettings.clockEnabled }
    val hideDisconnected = remember(revision) { DesktopOverlaySettings.logiHideWhenDisconnected }

    // 映射提示超时清理：超过展示时长后清空
    val hint = DesktopOverlayStore.keyboardHint.value
    val hintAt = DesktopOverlayStore.keyboardHintAt.value
    LaunchedEffect(hint, hintAt) {
        if (hint.isNotBlank()) {
            delay(KEYBOARD_HINT_DURATION_MS)
            DesktopOverlayStore.clearKeyboardHint()
        }
    }

    // 锚点直接读 Anchor 活态：拖动期 mutableState 变化即触发重组，位置实时跟手
    BalanceElement(
        anchor = DesktopOverlaySettings.balanceAnchor.snapshot(balanceEnabled),
        snapshot = DesktopOverlayStore.balance.value,
    )
    LogiBatteryElement(
        anchor = DesktopOverlaySettings.logiAnchor.snapshot(logiEnabled),
        devices = DesktopOverlayStore.logiDevices.value,
        hideWhenDisconnected = hideDisconnected,
    )
    KeyboardElement(
        anchor = DesktopOverlaySettings.keyboardAnchor.snapshot(keyboardEnabled),
        state = DesktopOverlayStore.keyboardState.value,
        hint = if (now - hintAt <= KEYBOARD_HINT_DURATION_MS) hint else "",
    )
    ClockElement(
        anchor = DesktopOverlaySettings.clockAnchor.snapshot(clockEnabled),
        text = DesktopOverlayStore.clockText.value,
    )
}

/** 按键映射提示的展示时长（毫秒）。 */
private const val KEYBOARD_HINT_DURATION_MS = 1_200L

/** 媒体会话所属设备的显示名；本机会话固定为「本机」。 */
private fun mediaDeviceLabel(deviceUuid: String): String =
    if (deviceUuid == DesktopOverlayStore.LOCAL_KEY) {
        "本机"
    } else {
        DataLayerDesktop.findOnlineDevice(deviceUuid)?.displayName ?: deviceUuid
    }

/**
 * 媒体条目：展开态复用媒体大岛，超时后收起为摘要胶囊。
 *
 * 标题或艺术家变化视为换曲，重新展开。
 */
@Composable
private fun OverlayMediaItem(
    snapshot: MediaSessionSnapshot,
    deviceName: String,
) {
    val trackKey = snapshot.title.orEmpty() + "\u0001" + snapshot.artist.orEmpty()
    var expanded by remember { mutableStateOf(true) }
    val collapseMs = remember { DesktopOverlaySettings.mediaAutoCollapseMs }
    LaunchedEffect(trackKey, collapseMs) {
        expanded = true
        delay(collapseMs)
        expanded = false
    }

    val mediaSession =
        MediaSessionData(
            packageName = snapshot.packageName.orEmpty(),
            appName = deviceName,
            title = snapshot.title.orEmpty(),
            text = snapshot.artist.orEmpty(),
            coverUrl = snapshot.coverUrl,
            deviceName = deviceName,
        )

    if (expanded) {
        Box(
            Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MEDIA_CARD_BACKGROUND),
        ) {
            MediaIslandCompose(mediaSession = mediaSession, showControls = false)
        }
    } else {
        CollapsedMediaCapsule(title = snapshot.title, coverUrl = snapshot.coverUrl)
    }
}

/**
 * 收起态胶囊。
 *
 * 定宽胶囊：封面在左，标题在右侧剩余宽度内滚动；滚动结束复位后停留片刻再开始下一轮。
 */
@Composable
private fun CollapsedMediaCapsule(
    title: String?,
    coverUrl: String?,
) {
    val shape = RoundedCornerShape(percent = 50)
    val painter = rememberSuperIslandImagePainter(url = coverUrl)
    Row(
        modifier =
            Modifier
                .width(COLLAPSED_WIDTH)
                .height(COLLAPSED_HEIGHT)
                .shadow(elevation = 6.dp, shape = shape)
                .background(color = CAPSULE_BACKGROUND, shape = shape)
                .border(width = 1.dp, color = CAPSULE_BORDER, shape = shape)
                .clip(shape)
                .padding(horizontal = COLLAPSED_HORIZONTAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = "歌曲封面",
                modifier = Modifier.size(COLLAPSED_COVER_SIZE).clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(text = "\u266A", color = Color(0xFF888888), fontSize = 14.sp)
        }

        Spacer(Modifier.width(6.dp))

        Box(Modifier.weight(1f)) {
            AutoScrollText(
                text = title.orEmpty().ifBlank { "未在播放" },
                style = TextStyle(fontSize = 12.sp),
                color = Color.White,
                baseSpeedPxPerSec = 100f,
                pauseMillis = 0,
                endPauseMillis = CAPSULE_END_PAUSE_MS,
            )
        }
    }
}

/**
 * 弹幕层。
 *
 * 与 C# 叠加层同口径：轨道起点取顶部卡片实测高度（`TopOffset`），
 * 几何与字号一律按**物理像素**计（C# 侧窗口 DPI 固定 96，36px 字号即 60px 轨道）。
 */
@Composable
private fun DanmakuLayer(
    now: Long,
    topOffsetPx: Float,
) {
    val screen =
        remember {
            Toolkit.getDefaultToolkit().screenSize
        }
    val widthPx = screen.width.toFloat()
    val heightPx = screen.height.toFloat()
    val density = LocalDensity.current

    // 到期弹幕：完全离屏即移除，并清理轨道占用
    val expired =
        DesktopOverlayStore.danmaku.filter { entry ->
            val track = DesktopOverlayStore.danmakuTracks[entry.id] ?: return@filter false
            DanmakuTracks.isExpired(
                spawnX = track.spawnX,
                spawnAt = track.spawnAt,
                width = track.width,
                now = now,
            )
        }
    expired.forEach { DesktopOverlayStore.removeDanmakuWithTrack(it) }

    // 字号按物理像素换算为 sp：Compose 的 sp 会再乘 fontScale 与 density
    val fontScale = density.fontScale * density.density
    val fontSizeSp = (DanmakuTracks.fontSizePx / fontScale.coerceAtLeast(0.01f)).sp
    // 图标边长在 C# 中等于字号像素值
    val iconSizeDp = with(density) { (DanmakuTracks.fontSizePx / density.density).dp }

    // 每帧批量分配：只要还有待分配条目且轨道可分配就持续分配（对应 C# 的 SpawnPending）
    val pendingIds =
        DesktopOverlayStore.danmaku
            .filter { DesktopOverlayStore.danmakuTracks[it.id] == null }
            .map { it.id }
    if (pendingIds.isNotEmpty()) {
        val occupied = DesktopOverlayStore.danmakuTracks.values.toMutableList()
        val widthCache = HashMap<Long, Float>()
        DesktopOverlayStore.danmaku.forEach { widthCache[it.id] = estimateWidth(it, density.density) }
        val assigned =
            DanmakuTracks.spawnPending(
                width = widthPx,
                topOffset = topOffsetPx,
                areaHeight = heightPx,
                now = now,
                pending = pendingIds,
                occupied = occupied,
                widthOf = { id -> widthCache[id] ?: 0f },
            )
        assigned.forEach { (id, occupancy) -> DesktopOverlayStore.danmakuTracks[id] = occupancy }
    }

    DesktopOverlayStore.danmaku.forEach { entry ->
        val track = DesktopOverlayStore.danmakuTracks[entry.id] ?: return@forEach
        val x = track.spawnX - (now - track.spawnAt) / 1000f * DanmakuTracks.pixelsPerSecond
        // 纯文字绘制：无卡片背景，阴影与描边只作用于文字（与 C# DrawDanmaku 一致）
        Row(
            modifier =
                Modifier
                    // 直接以像素定位，避免 dp 换算改变轨道位置
                    .offset { IntOffset(x.roundToInt(), track.trackY.roundToInt()) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = rememberDanmakuIcon(entry.iconPng)
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = entry.appName,
                    modifier = Modifier.size(iconSizeDp),
                    contentScale = ContentScale.FillBounds,
                )
                Spacer(Modifier.width(8.dp))
            }
            DanmakuText(text = entry.displayText, fontSize = fontSizeSp)
        }
    }
}

/**
 * 弹幕文字：黑色描边 + 白色填充 + 右下 2px 阴影。
 *
 * 对应 C# `DrawDanmaku`：`BorderEnabled = true`、`BorderThickness = 2`、
 * `ShadowEnabled = true`、`ShadowDepth = 2`、`Color = #FFFFFF`、`Bold = true`。
 */
@Composable
private fun DanmakuText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    val style =
        androidx.compose.ui.text.TextStyle(
            fontSize = fontSize,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
    // 两层文字必须叠在同一位置：用 Box 承载，否则 Row 会把它们并排摆放
    Box {
        // 描边层：Stroke 样式的同文案
        Text(
            text = text,
            color = Color.Black,
            style =
                style.copy(
                    drawStyle =
                        androidx.compose.ui.graphics.drawscope
                            .Stroke(width = 2f),
                ),
        )
        // 填充层：白色 + 文字阴影
        Text(
            text = text,
            color = Color.White,
            style =
                style.copy(
                    shadow =
                        androidx.compose.ui.graphics.Shadow(
                            color = Color.Black,
                            offset =
                                androidx.compose.ui.geometry
                                    .Offset(2f, 2f),
                            blurRadius = 0f,
                        ),
                ),
        )
    }
}

/**
 * 估算弹幕宽度（像素）。
 *
 * 按保守上界计算，宁可偏大：CJK 字符按满宽（1.0×字号）、ASCII 按 0.6×字号，
 * 再加上图标（字号值 + 8）与左右内边距。偏大会让轨道让位更保守，避免重叠。
 */
private fun estimateWidth(
    entry: DanmakuEntry,
    density: Float,
): Float {
    val font = DanmakuTracks.fontSizePx
    var textWidth = 0f
    entry.displayText.forEach { ch ->
        textWidth += if (ch.code > 0x2E80) font else font * 0.6f
    }
    val iconWidth = if (entry.iconPng != null) font + 8f else 0f
    return textWidth + iconWidth + 20f * density
}

/** 顶部卡片与首条弹幕轨道之间的间距（像素）。 */
private const val TOP_CARD_BOTTOM_GAP_PX = 16f

/** 解码弹幕应用图标（与列表/历史同一条解码路径，含透明留白裁剪）；无图标或解码失败时返回 null。 */
@Composable
private fun rememberDanmakuIcon(png: ByteArray?): ImageBitmap? =
    remember(png) {
        png?.let { bytes ->
            notifyrelay.desktop.platform.DesktopIconCodecPort
                .decodeIconBitmap(bytes)
        }
    }

/** 媒体卡展开后自动收起时长。 */
private const val MEDIA_AUTO_COLLAPSE_MS = 5_000L

/** 收起态胶囊宽度。 */
private val COLLAPSED_WIDTH = 260.dp

/** 收起态胶囊高度。 */
private val COLLAPSED_HEIGHT = 36.dp

/** 收起态文本滚动结束复位后的停留时长。 */
private const val CAPSULE_END_PAUSE_MS = 1_500

/** 收起态封面尺寸。 */
private val COLLAPSED_COVER_SIZE = 24.dp

/** 收起态左右内边距。 */
private val COLLAPSED_HORIZONTAL_PADDING = 10.dp

/** 收起态胶囊背景。 */
private val CAPSULE_BACKGROUND = Color(0xCC000000)

/** 收起态胶囊边框。 */
private val CAPSULE_BORDER = Color(0x80FFFFFF)

/** 媒体卡背景。 */
private val MEDIA_CARD_BACKGROUND = Color(0xFF1C1C1E)

/** 叠加层文本样式。 */
private object OverlayTypography {
    val TITLE = TextStyle(fontSize = 16.sp)
    val BODY = TextStyle(fontSize = 13.sp)
}
