package notifyrelay.desktop.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import github.xzynine.superislandui.floating.bigisland.components.MediaIslandCompose
import github.xzynine.superislandui.floating.smallisland.compose.BigIslandCollapsedCompose
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
            Logger.i("DesktopOverlayWin", "叠加层窗口已就绪 点击穿透=" + applied)
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
            withFrameMillis { frameNow = System.currentTimeMillis() }
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
        Box(Modifier.widthIn(max = COLLAPSED_MAX_WIDTH)) {
            BigIslandCollapsedCompose(
                bigIsland = null,
                picMap = snapshot.coverUrl?.let { mapOf("miui.focus.pic_cover" to it) },
                fallbackTitle = snapshot.title,
            )
        }
    }
}

/** 超级岛卡片：先以标题/正文呈现，布局解析待接入 paramV2。 */
@Composable
private fun SuperIslandCard(entry: SuperIslandEntry) {
    Box(
        Modifier
            .width(380.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1C1C1E)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = entry.title.orEmpty(),
                color = Color.White,
                style = notifyrelay.desktop.overlay.OverlayTypography.TITLE,
            )
            Text(
                text = entry.text.orEmpty(),
                color = Color(0xFFB0B0B0),
                style = notifyrelay.desktop.overlay.OverlayTypography.BODY,
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
        Box(
            Modifier
                .offset(x = x.dp, y = (520 + (index % 6) * laneHeight).dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xCC000000)),
        ) {
            Text(
                text = entry.appName + ": " + entry.title + " - " + entry.text,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = notifyrelay.desktop.overlay.OverlayTypography.BODY,
            )
        }
    }
}

private const val DANMAKU_SPEED_PX_PER_SEC = 180f

/** 收起态胶囊宽度上限。 */
private val COLLAPSED_MAX_WIDTH = 260.dp

/** 媒体卡展开后自动收起时长。 */
private const val MEDIA_AUTO_COLLAPSE_MS = 5_000L

/** 媒体卡背景。 */
private val MEDIA_CARD_BACKGROUND = Color(0xFF1C1C1E)

/** 叠加层文本样式。 */
private object OverlayTypography {
    val TITLE = TextStyle(fontSize = 16.sp)
    val BODY = TextStyle(fontSize = 13.sp)
}
