package notifyrelay.desktop.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import github.xzynine.superislandui.common.AutoScrollText
import github.xzynine.superislandui.floating.bigisland.components.MediaIslandCompose
import github.xzynine.superislandui.floating.common.SuperIslandExpandableCard
import github.xzynine.superislandui.floating.common.rememberSuperIslandImagePainter
import github.xzynine.superislandui.model.components.MediaSessionData
import kotlinx.coroutines.delay
import notifyrelay.base.log.Logger
import notifyrelay.desktop.DataLayerDesktop
import notifyrelay.desktop.platform.DesktopOverlaySettings
import notifyrelay.domain.feature.media.MediaControlAction
import notifyrelay.domain.feature.media.MediaSessionSnapshot
import notifyrelay.shared.ui.common.DebugFlags
import top.yukonga.miuix.kmp.basic.Text
import java.awt.Rectangle
import java.awt.Toolkit
import kotlin.math.roundToInt

/** 叠加层内容宽度：媒体卡、超级岛卡与通知卡共用同一列宽。 */
internal val OVERLAY_COLUMN_WIDTH = 420.dp

/** 叠加层距屏幕顶部的距离。 */
private val OVERLAY_TOP_MARGIN = 10.dp

/** 窗口初始高度：首帧测量出真实内容高度后立即被替换。 */
private val INITIAL_WINDOW_HEIGHT = 120.dp

/**
 * 叠加层窗口。
 *
 * 无边框、透明、置顶、点击穿透、不抢焦点；窗口尺寸随内容实测结果收缩，
 * 固定停留在屏幕顶部居中，不铺满屏幕。媒体卡、通知卡、超级岛与状态元素均由本机 Compose 绘制，
 * 不经位图编码与跨进程传输。
 */
@Composable
internal fun DesktopOverlayWindow() {
    val density = LocalDensity.current
    // 显示器分辨率变化后需要重新居中：轮询屏幕尺寸，变化时触发一次重组
    var screen by remember { mutableStateOf(Toolkit.getDefaultToolkit().screenSize) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(SCREEN_SIZE_POLL_MS)
            val current = Toolkit.getDefaultToolkit().screenSize
            if (current != screen) screen = current
        }
    }
    val windowState =
        rememberWindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition(0.dp, 0.dp),
            width = OVERLAY_COLUMN_WIDTH,
            height = INITIAL_WINDOW_HEIGHT,
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
        val overlay =
            remember {
                DesktopWindowsOverlay(report = { Logger.w("DesktopOverlayWin", "叠加层原生操作失败", it) })
            }

        // 非外观设置经 revision 观察：切换开关立即生效，无需重开叠加层窗口
        val settingsRevision = DesktopOverlaySettings.revision.value
        val interactiveEnabled = remember(settingsRevision) { DesktopOverlaySettings.interactiveEnabled }
        val spectrumEnabled = remember(settingsRevision) { DesktopOverlaySettings.spectrumEnabled }

        LaunchedEffect(Unit) {
            window.isAlwaysOnTop = true
            val applied = overlay.applyOverlayStyle(window)
            overlay.clickThrough(window, true)
            Logger.i("DesktopOverlayWin", "叠加层窗口已就绪 点击穿透=" + applied)
        }

        LaunchedEffect(interactiveEnabled) {
            overlay.start(window, interactiveMode = interactiveEnabled)
        }

        DisposableEffect(Unit) {
            onDispose { overlay.close() }
        }

        // 前台全屏检测：开启时隐藏叠加层内容，避免遮挡全屏游戏/视频
        val hideFullscreen = remember { DesktopOverlaySettings.hideFullscreen }
        var fullscreen by remember { mutableStateOf(false) }
        if (hideFullscreen) {
            LaunchedEffect(Unit) {
                while (true) {
                    fullscreen = overlay.foregroundIsFullscreen(window)
                    delay(FULLSCREEN_CHECK_INTERVAL_MS)
                }
            }
        }

        // 可交互区域：媒体卡、超级岛卡与通知图标方块，供原生层切换点击穿透
        val interactiveRegions = remember { androidx.compose.runtime.mutableStateMapOf<String, Rectangle>() }
        if (interactiveEnabled) {
            LaunchedEffect(Unit) {
                androidx.compose.runtime.snapshotFlow { interactiveRegions.toMap() }.collect { map ->
                    overlay.setInteractiveRegions(map.values.toList())
                }
            }
        } else {
            LaunchedEffect(Unit) { overlay.setInteractiveRegions(emptyList()) }
        }

        // 内容实测尺寸决定窗口尺寸与位置：屏幕顶部居中，宽度固定为一列内容宽度
        var contentSize by remember { mutableStateOf(IntSize.Zero) }
        val topMarginPx = with(density) { OVERLAY_TOP_MARGIN.roundToPx() }
        LaunchedEffect(contentSize, topMarginPx) {
            if (contentSize.width <= 0 || contentSize.height <= 0) return@LaunchedEffect
            val x = ((screen.width - contentSize.width) / 2f).roundToInt()
            overlay.moveTo(window, Rectangle(x, topMarginPx, contentSize.width, contentSize.height))
        }

        if (!fullscreen) {
            OverlayContent(
                overlay = overlay,
                interactiveEnabled = interactiveEnabled,
                spectrumEnabled = spectrumEnabled,
                interactiveRegions = interactiveRegions,
                contentSize = contentSize,
                onContentSizeChanged = { contentSize = it },
            )
        }
    }
}

/**
 * 叠加层内容：媒体卡 → 超级岛（含罗技汇总）→ 通知卡 → 状态元素。
 *
 * 该列是叠加层唯一的内容容器，宽度固定为 [OVERLAY_COLUMN_WIDTH]，
 * 高度随各行内容变化，上层内容把下层内容整体下压。
 */
@Composable
private fun OverlayContent(
    overlay: DesktopWindowsOverlay,
    interactiveEnabled: Boolean,
    spectrumEnabled: Boolean,
    interactiveRegions: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Rectangle>,
    contentSize: IntSize,
    onContentSizeChanged: (IntSize) -> Unit,
) {
    // 帧时钟：驱动频谱与映射提示淡出
    var frameNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis {
                frameNow = System.currentTimeMillis()
                // 帧心跳：置顶监控据此判定渲染是否卡死
                overlay.onFrame()
            }
        }
    }

    val regionDebug = DebugFlags.OVERLAY_REGION_DEBUG.value
    // 通知卡片的展开/收起状态：展开态卡片与状态行里的图标方块共用同一份
    val cardsState = rememberNotificationCardsState()

    Box(Modifier.wrapContentSize()) {
        // 独立行（卡片与罗技胶囊）居中，状态条目与通知图标填进它们左右剩下的空档
        OverlayColumn(
            modifier = Modifier.width(OVERLAY_COLUMN_WIDTH).onSizeChanged(onContentSizeChanged),
            anchors = overlayAnchors(interactiveEnabled, spectrumEnabled, frameNow, cardsState, interactiveRegions),
            fillers = overlayFillers(frameNow, cardsState, interactiveEnabled, interactiveRegions),
        )

        if (regionDebug) {
            RegionDebugOverlay(
                regions = interactiveRegions,
                interactiveEnabled = interactiveEnabled,
            )
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
    }
}

/**
 * 独立行内容：媒体卡、超级岛卡片、通知卡片、罗技汇总岛。
 *
 * 每个条目独占一行并水平居中；罗技汇总岛排在最后，作为状态条目的主要落点。
 */
@Composable
private fun overlayAnchors(
    interactiveEnabled: Boolean,
    spectrumEnabled: Boolean,
    frameNow: Long,
    cardsState: NotificationCardsState,
    interactiveRegions: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Rectangle>,
): List<OverlayColumnItem> {
    val anchors = mutableListOf<OverlayColumnItem>()

    DesktopOverlayStore.mediaSessions.forEach { (deviceUuid, snapshot) ->
        anchors.add(
            OverlayColumnItem(key = "media-" + deviceUuid) {
                OverlayMediaItem(
                    snapshot = snapshot,
                    deviceName = mediaDeviceLabel(deviceUuid),
                    deviceUuid = deviceUuid,
                    interactiveEnabled = interactiveEnabled,
                    spectrumEnabled = spectrumEnabled,
                    frameNow = frameNow,
                    regions = interactiveRegions,
                )
            },
        )
    }

    DesktopOverlayStore.superIslands.values.forEach { entry ->
        anchors.add(
            OverlayColumnItem(key = "super-" + entry.sourceKey) {
                SuperIslandCard(
                    entry = entry,
                    interactiveEnabled = interactiveEnabled,
                    regions = interactiveRegions,
                )
            },
        )
    }

    if (cardsState.expandedIds.isNotEmpty()) {
        anchors.add(OverlayColumnItem(key = "notifications-expanded") { NotificationExpandedCards(cardsState) })
    }

    val revision = DesktopOverlaySettings.revision.value
    val logiEnabled = remember(revision) { DesktopOverlaySettings.logiEnabled }
    val logiHideDisconnected = remember(revision) { DesktopOverlaySettings.logiHideWhenDisconnected }
    if (logiEnabled && visibleLogiDevices(logiHideDisconnected).isNotEmpty()) {
        anchors.add(
            OverlayColumnItem(key = "logi") {
                LogiIslandCard(
                    interactiveEnabled = interactiveEnabled,
                    interactiveRegions = interactiveRegions,
                )
            },
        )
    }

    return anchors
}

/**
 * 流式条目：时间、键盘、余额、收起态通知图标。
 *
 * 按此顺序依次尝试填进各独立行左右剩下的空档，填不下的留到后续独立行，
 * 所有独立行都试过后仍未安置的排在最下方并整体居中。
 */
@Composable
private fun overlayFillers(
    now: Long,
    cardsState: NotificationCardsState,
    interactiveEnabled: Boolean,
    interactiveRegions: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Rectangle>,
): List<OverlayColumnItem> {
    val revision = DesktopOverlaySettings.revision.value
    val balanceEnabled = remember(revision) { DesktopOverlaySettings.balanceEnabled }
    val keyboardEnabled = remember(revision) { DesktopOverlaySettings.keyboardEnabled }
    val clockEnabled = remember(revision) { DesktopOverlaySettings.clockEnabled }

    // 映射提示超时清理：超过展示时长后清空
    val hint = DesktopOverlayStore.keyboardHint.value
    val hintAt = DesktopOverlayStore.keyboardHintAt.value
    LaunchedEffect(hint, hintAt) {
        if (hint.isNotBlank()) {
            delay(KEYBOARD_HINT_DURATION_MS)
            DesktopOverlayStore.clearKeyboardHint()
        }
    }

    // 元素本身在关闭或无内容时不发出节点，列布局按零尺寸跳过
    val fillers = mutableListOf<OverlayColumnItem>()
    fillers.add(
        OverlayColumnItem(key = "clock") {
            ClockElement(
                style = DesktopOverlaySettings.clockStyle.snapshot(clockEnabled),
                text = DesktopOverlayStore.clockText.value,
            )
        },
    )
    fillers.add(
        OverlayColumnItem(key = "keyboard") {
            KeyboardElement(
                style = DesktopOverlaySettings.keyboardStyle.snapshot(keyboardEnabled),
                state = DesktopOverlayStore.keyboardState.value,
                hint = hint,
                hintElapsedMs = now - hintAt,
            )
        },
    )
    fillers.add(
        OverlayColumnItem(key = "balance") {
            BalanceElement(
                style = DesktopOverlaySettings.balanceStyle.snapshot(balanceEnabled),
                snapshot = DesktopOverlayStore.balance.value,
            )
        },
    )

    // 收起态通知图标：每条一个流式条目，按到达顺序参与填空隙
    val expanded = cardsState.expandedIds.toSet()
    DesktopOverlayStore.notifications
        .filter { it.id !in expanded }
        .filter { cardsState.collapsedAt[it.id] != null }
        .forEach { entry ->
            fillers.add(
                OverlayColumnItem(key = "notification-" + entry.id) {
                    NotificationIconItem(
                        entry = entry,
                        interactiveEnabled = interactiveEnabled,
                        interactiveRegions = interactiveRegions,
                        onClick = { cardsState.expand(entry.id) },
                    )
                },
            )
        }

    return fillers
}

/**
 * 可点击区域调试：红色绘出可交互矩形，橙色虚线绘出叠加层窗口边界。
 *
 * 只做绘制，不参与点击穿透判定：穿透仍由交互开关与区域集合决定。
 */
@Composable
private fun BoxScope.RegionDebugOverlay(
    regions: Map<String, Rectangle>,
    interactiveEnabled: Boolean,
) {
    Canvas(Modifier.matchParentSize()) {
        // 窗口边界：橙色虚线，标出叠加层实际占据的屏幕范围
        drawRect(
            color = REGION_DEBUG_WINDOW_BORDER,
            topLeft = Offset.Zero,
            size = size,
            style =
                Stroke(
                    width = REGION_DEBUG_STROKE,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                ),
        )
        if (!interactiveEnabled) return@Canvas
        regions.values.forEach { rect ->
            val rectSize = Size(rect.width.toFloat(), rect.height.toFloat())
            val topLeft = Offset(rect.x.toFloat(), rect.y.toFloat())
            drawRect(color = REGION_DEBUG_FILL, topLeft = topLeft, size = rectSize)
            drawRect(
                color = REGION_DEBUG_STROKE_COLOR,
                topLeft = topLeft,
                size = rectSize,
                style = Stroke(REGION_DEBUG_STROKE),
            )
        }
    }
}

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
 * 标题或艺术家变化视为换曲，重新展开且属于自动展开：该次展开不显示操作按钮。
 * [interactiveEnabled] 开启时收起态可点击展开，仅用户点开的展开显示操作按钮。
 * [spectrumEnabled] 开启时右侧显示频谱条。
 */
@Composable
private fun OverlayMediaItem(
    snapshot: MediaSessionSnapshot,
    deviceName: String,
    deviceUuid: String,
    interactiveEnabled: Boolean,
    spectrumEnabled: Boolean,
    frameNow: Long,
    regions: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Rectangle>,
) {
    val trackKey = snapshot.title.orEmpty() + "\u0001" + snapshot.artist.orEmpty()
    var expanded by remember { mutableStateOf(true) }
    // 展开是否由用户点开：换曲与首次出现均为自动展开，操作按钮只对用户点开的展开显示
    var userExpanded by remember { mutableStateOf(false) }
    var expandGeneration by remember { mutableLongStateOf(0L) }
    val collapseMs = remember { DesktopOverlaySettings.mediaAutoCollapseMs }
    val controlsEnabled = remember { DesktopOverlaySettings.mediaControlsEnabled }
    LaunchedEffect(trackKey) {
        userExpanded = false
    }
    LaunchedEffect(trackKey, expandGeneration, collapseMs) {
        expanded = true
        delay(collapseMs)
        expanded = false
        userExpanded = false
    }

    val mediaSession =
        MediaSessionData(
            packageName = snapshot.packageName.orEmpty(),
            appName = deviceName,
            title = snapshot.title.orEmpty(),
            text = snapshot.artist.orEmpty(),
            coverUrl = snapshot.coverUrl,
            deviceName = deviceName,
            positionMs = snapshot.positionMs,
            durationMs = snapshot.durationMs,
            isPlaying = snapshot.isPlaying,
        )

    val mediaControl = DataLayerDesktop.mediaControl
    val onPlayPause: () -> Unit = {
        if (deviceUuid == DesktopOverlayStore.LOCAL_KEY) {
            mediaControl?.localControl(MediaControlAction.PLAY_PAUSE)
        } else {
            DataLayerDesktop.findOnlineDevice(deviceUuid)?.let {
                mediaControl?.sendToRemote(it, MediaControlAction.PLAY_PAUSE)
            }
        }
    }
    val onPrevious: () -> Unit = {
        if (deviceUuid == DesktopOverlayStore.LOCAL_KEY) {
            mediaControl?.localControl(MediaControlAction.PREVIOUS)
        } else {
            DataLayerDesktop.findOnlineDevice(deviceUuid)?.let {
                mediaControl?.sendToRemote(it, MediaControlAction.PREVIOUS)
            }
        }
    }
    val onNext: () -> Unit = {
        if (deviceUuid == DesktopOverlayStore.LOCAL_KEY) {
            mediaControl?.localControl(MediaControlAction.NEXT)
        } else {
            DataLayerDesktop.findOnlineDevice(deviceUuid)?.let {
                mediaControl?.sendToRemote(it, MediaControlAction.NEXT)
            }
        }
    }

    val regionKey = "media-" + deviceUuid
    DisposableEffect(regionKey) {
        onDispose { regions.remove(regionKey) }
    }
    val regionModifier =
        if (interactiveEnabled) {
            Modifier.onGloballyPositioned { coords ->
                val pos = coords.localToRoot(Offset.Zero)
                val sz = coords.size
                regions[regionKey] =
                    Rectangle(
                        pos.x.roundToInt(),
                        pos.y.roundToInt(),
                        sz.width,
                        sz.height,
                    )
            }
        } else {
            Modifier
        }

    // 背景由容器单层绘制并在两态颜色之间渐变，两态内容不再各自画表面
    SuperIslandExpandableCard(
        expanded = expanded,
        expandedBackgroundColor = MEDIA_CARD_BACKGROUND,
        collapsedBackgroundColor = CAPSULE_BACKGROUND,
        modifier = Modifier.then(regionModifier),
        expandedCornerRadius = MEDIA_CARD_CORNER_RADIUS,
        expandedContent = {
            Box(Modifier.width(MEDIA_CARD_WIDTH)) {
                MediaIslandCompose(
                    mediaSession = mediaSession,
                    showControls = interactiveEnabled && controlsEnabled && userExpanded,
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                )
            }
        },
        collapsedContent = {
            Box(
                Modifier.then(
                    if (interactiveEnabled) {
                        Modifier.clickable {
                            userExpanded = true
                            expanded = true
                            expandGeneration++
                        }
                    } else {
                        Modifier
                    },
                ),
            ) {
                CollapsedMediaCapsule(
                    title = snapshot.title,
                    coverUrl = snapshot.coverUrl,
                    spectrumEnabled = spectrumEnabled,
                    playing = snapshot.isPlaying,
                    frameNow = frameNow,
                )
            }
        },
    )
}

/**
 * 收起态胶囊。
 *
 * 定宽胶囊：封面在左，标题在右侧剩余宽度内滚动；短标题（未进入滚动）贴右对齐，
 * 滚动结束复位后停留片刻再开始下一轮。频谱开启时贴胶囊右端常显。
 */
@Composable
private fun CollapsedMediaCapsule(
    title: String?,
    coverUrl: String?,
    spectrumEnabled: Boolean,
    playing: Boolean,
    frameNow: Long,
) {
    val painter = rememberSuperIslandImagePainter(url = coverUrl)
    Row(
        modifier =
            Modifier
                .width(COLLAPSED_WIDTH)
                .padding(horizontal = COLLAPSED_HORIZONTAL_PADDING, vertical = COLLAPSED_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(COLLAPSED_ITEM_GAP),
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

        // weight 撑满剩余宽度；短标题（未进入滚动）贴右侧频谱对齐，长标题滚动行为不变
        Box(
            modifier = Modifier.weight(1f, fill = true),
            contentAlignment = Alignment.CenterEnd,
        ) {
            AutoScrollText(
                text = title.orEmpty().ifBlank { "未在播放" },
                style = TextStyle(fontSize = 12.sp),
                color = Color.White,
                baseSpeedPxPerSec = 100f,
                pauseMillis = 0,
                endPauseMillis = CAPSULE_END_PAUSE_MS,
            )
        }

        if (spectrumEnabled) {
            OverlaySpectrum(
                positionMs = frameNow,
                playing = playing,
                widthDp = COLLAPSED_SPECTRUM_WIDTH,
                heightDp = COLLAPSED_SPECTRUM_HEIGHT,
            )
        }
    }
}

/** 前台全屏检测轮询间隔（毫秒）。 */
private const val FULLSCREEN_CHECK_INTERVAL_MS = 500L

/** 屏幕尺寸轮询间隔（毫秒）：分辨率变化时重新居中。 */
private const val SCREEN_SIZE_POLL_MS = 2_000L

/** 收起态胶囊宽度。 */
private val COLLAPSED_WIDTH = 260.dp

/** 收起态封面尺寸。 */
private val COLLAPSED_COVER_SIZE = 24.dp

/** 收起态左右与上下内边距：收起态无固定高度，由内边距与内容撑开。 */
private val COLLAPSED_HORIZONTAL_PADDING = 10.dp
private val COLLAPSED_VERTICAL_PADDING = 6.dp

/** 收起态文本滚动结束复位后的停留时长。 */
private const val CAPSULE_END_PAUSE_MS = 1_500

/** 胶囊内封面、标题与频谱的横向间距。 */
private val COLLAPSED_ITEM_GAP = 6.dp

/** 收起态频谱条区域尺寸。 */
private const val COLLAPSED_SPECTRUM_WIDTH = 20.5f
private const val COLLAPSED_SPECTRUM_HEIGHT = 14f

/** 收起态胶囊底色；形变过程中由外框渐变到展开态媒体卡底色。 */
private val CAPSULE_BACKGROUND = Color(0xCC000000)

/** 展开态媒体卡底色。 */
private val MEDIA_CARD_BACKGROUND = Color(0xFF1C1C1E)

/** 媒体卡展开态宽度。 */
private val MEDIA_CARD_WIDTH = 420.dp

/** 媒体卡展开态圆角：形变起点，收起过程中逐步增大到胶囊圆角。 */
private val MEDIA_CARD_CORNER_RADIUS = 24.dp

/** 可点击区域的填充色（半透明红）。 */
private val REGION_DEBUG_FILL = Color(0x4DFF0000)

/** 可点击区域的描边色（红）。 */
private val REGION_DEBUG_STROKE_COLOR = Color(0xFFFF0000)

/** 叠加层窗口边界色（橙）。 */
private val REGION_DEBUG_WINDOW_BORDER = Color(0xFFFF9800)

/** 调试描边宽度（像素）。 */
private const val REGION_DEBUG_STROKE = 2f
