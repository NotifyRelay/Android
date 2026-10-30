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
import androidx.compose.ui.text.TextStyle
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
import notifyrelay.domain.feature.media.MediaSessionSnapshot
import top.yukonga.miuix.kmp.basic.Text
import java.awt.Dimension
import java.awt.Toolkit

/**
 * 叠加层窗口：无边框、透明、置顶、点击穿透、不抢焦点。
 *
 * 媒体卡、超级岛与弹幕均在本机用 Compose 直接绘制，
 * 不经位图编码与跨进程传输。
 */
@Composable
internal fun DesktopOverlayWindow() {
    val screen: Dimension = remember { Toolkit.getDefaultToolkit().screenSize }
    val windowState =
        rememberWindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition(0.dp, 0.dp),
            width = screen.width.dp,
            height = screen.height.dp,
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

    Box(Modifier.fillMaxSize()) {
        // 顶部：媒体卡与超级岛纵向排布
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
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

        // 弹幕层
        DanmakuLayer(now = frameNow)
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
 * 标题或艺术家变化视为换曲，重新展开。
 */
@Composable
private fun OverlayMediaItem(
    snapshot: MediaSessionSnapshot,
    deviceName: String,
) {
    val trackKey = snapshot.title.orEmpty() + "\u0001" + snapshot.artist.orEmpty()
    var expanded by remember { mutableStateOf(true) }
    LaunchedEffect(trackKey) {
        expanded = true
        delay(MEDIA_AUTO_COLLAPSE_MS)
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

/** 弹幕层：自右向左匀速滚动，出屏即移除。 */
@Composable
private fun DanmakuLayer(now: Long) {
    val screenWidth =
        remember {
            Toolkit
                .getDefaultToolkit()
                .screenSize.width
                .toFloat()
        }
    val laneHeight = 44f
    // 超过存活时间的弹幕移除，避免状态无限增长
    val expired =
        DesktopOverlayStore.danmaku.filter { entry ->
            (now - entry.spawnAt) / 1000f * DANMAKU_SPEED_PX_PER_SEC > screenWidth + 600f
        }
    expired.forEach { DesktopOverlayStore.removeDanmaku(it) }

    DesktopOverlayStore.danmaku.forEachIndexed { index, entry ->
        val elapsed = (now - entry.spawnAt) / 1000f
        val x = screenWidth - elapsed * DANMAKU_SPEED_PX_PER_SEC
        Row(
            modifier =
                Modifier
                    .offset(x = x.dp, y = (520 + (index % 6) * laneHeight).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 来源应用图标：文本行内与字号等高，缺失时不占位
            val icon = rememberDanmakuIcon(entry.iconPng)
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = entry.appName,
                    modifier = Modifier.size(DANMAKU_ICON_SIZE).clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = entry.appName + ": " + entry.title + " - " + entry.text,
                color = Color.White,
                style = notifyrelay.desktop.overlay.OverlayTypography.BODY,
            )
        }
    }
}

private const val DANMAKU_SPEED_PX_PER_SEC = 180f

/** 弹幕应用图标边长，与 13sp 正文视觉对齐。 */
private val DANMAKU_ICON_SIZE = 24.dp

/** 解码弹幕应用图标；无图标或解码失败时返回 null。 */
@Composable
private fun rememberDanmakuIcon(png: ByteArray?): ImageBitmap? =
    remember(png) {
        png?.let { bytes -> notifyrelay.base.util.decodeImageBitmap(bytes) }
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
