package notifyrelay.desktop.platform

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import notifyrelay.shared.ui.common.deviceIconFontFamily
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.image.BufferedImage

/** 标题栏高度。 */
private val TITLE_BAR_HEIGHT = 36.dp

/** 标题栏控制按钮宽度。 */
private val TITLE_BAR_CONTROL_WIDTH = 44.dp

/** 窗口边缘拖拽调整大小的感应宽度。 */
private val RESIZE_BAND = 6.dp

/** SetWindowPos：不改变尺寸。 */
private const val SWP_NOSIZE = 0x0001

/** SetWindowPos：不改变 Z 次序。 */
private const val SWP_NOZORDER = 0x0004

/** SetWindowPos：不激活窗口。 */
private const val SWP_NOACTIVATE = 0x0010

/** SetWindowPos：不重绘。 */
private const val SWP_NOREDRAW = 0x0008

/** 窗口最小宽度。 */
private const val MIN_WINDOW_WIDTH = 720

/** 窗口最小高度。 */
private const val MIN_WINDOW_HEIGHT = 480

/** 移动/缩放结束后延迟落盘的时长。 */
private const val GEOMETRY_SAVE_DEBOUNCE_MS = 500L

/** 窗口圆角半径，与 Windows 11 窗口圆角一致。 */
private val WINDOW_CORNER_RADIUS = 8.dp

/** 拖动过程中的临时状态；用普通对象而非 Compose 状态，避免每次移动都触发重组。 */
private class DragState {
    /** 按下时指针相对窗口左上角的偏移，拖动期间保持不变。 */
    var cursorOffset: Point? = null
}

/** 最小化按钮字形。 */
private const val GLYPH_MINIMIZE = "\uE921"

/** 最大化按钮字形。 */
private const val GLYPH_MAXIMIZE = "\uE922"

/** 还原按钮字形。 */
private const val GLYPH_RESTORE = "\uE923"

/** 关闭按钮字形。 */
private const val GLYPH_CLOSE = "\uE8BB"

/**
 * 自绘标题栏。
 *
 * 无边框窗口不提供系统标题栏，这里绘制图标、标题与最小化/最大化/关闭按钮，
 * 并接管拖动移动。最大化状态由 [DesktopWindowFrame] 持有。
 *
 * @param title 标题文本
 * @param icon 窗口图标
 * @param window 目标窗口
 * @param maximized 当前是否为最大化
 * @param onToggleMaximize 最大化/还原回调
 * @param onMinimize 最小化回调
 * @param onClose 关闭回调
 */
@Composable
private fun DesktopTitleBar(
    title: String,
    icon: java.awt.Image?,
    window: Frame,
    maximized: Boolean,
    onToggleMaximize: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
) {
    val iconBitmap = remember(icon) { (icon as? BufferedImage)?.toComposeImageBitmap() }
    // 拖动状态不放进 Compose 状态，拖动期间不触发重组/重绘
    val drag = remember { DragState() }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TITLE_BAR_HEIGHT)
                .background(MiuixTheme.colorScheme.background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 拖动区域：图标与标题；双击切换最大化
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { onToggleMaximize() })
                    }.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                if (maximized) {
                                    onToggleMaximize()
                                }
                                // 记录按下时指针相对窗口的偏移，拖动期间用绝对指针位置反推窗口位置，
                                // 避免「窗口移动改变指针窗口坐标」的回灌误差
                                val pointer = MouseInfo.getPointerInfo()?.location
                                drag.cursorOffset =
                                    pointer?.let { Point(it.x - window.x, it.y - window.y) }
                            },
                            onDragEnd = { finishDrag(window, drag) },
                            onDragCancel = { finishDrag(window, drag) },
                            onDrag = { change, _ ->
                                change.consume()
                                val offset = drag.cursorOffset ?: return@detectDragGestures
                                val pointer = MouseInfo.getPointerInfo()?.location ?: return@detectDragGestures
                                // 拖动期间禁止重绘：窗口整体由系统平移，避免逐帧整窗重绘产生闪烁
                                moveWindow(window, pointer.x - offset.x, pointer.y - offset.y, noRedraw = true)
                            },
                        )
                    },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(12.dp))
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        TitleBarButton(glyph = GLYPH_MINIMIZE, onClick = onMinimize)
        TitleBarButton(
            glyph = if (maximized) GLYPH_RESTORE else GLYPH_MAXIMIZE,
            onClick = onToggleMaximize,
        )
        TitleBarButton(glyph = GLYPH_CLOSE, danger = true, onClick = onClose)
    }
}

/** 标题栏按钮：悬停高亮，关闭按钮悬停变红。 */
@Composable
private fun TitleBarButton(
    glyph: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background =
        when {
            danger && hovered -> Color(0xFFC42B1C)
            hovered -> MiuixTheme.colorScheme.onBackground.copy(alpha = 0.08f)
            else -> Color.Transparent
        }
    val contentColor =
        if (danger && hovered) {
            Color.White
        } else {
            MiuixTheme.colorScheme.onBackground
        }
    Box(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(TITLE_BAR_CONTROL_WIDTH)
                .background(background)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            fontFamily = deviceIconFontFamily(),
            fontSize = 10.sp,
            color = contentColor,
        )
    }
}

/**
 * 无边框窗口的内容框架：上方自绘标题栏，内容区占满剩余高度，四边与四角叠加调整大小感应区。
 *
 * 最大化状态在此持有：切换时同步 DWM 圆角与描边形状（最大化窗口为直角），
 * 并回调宿主持久化状态；移动/缩放结束后延迟回调最新矩形，避免频繁写库。
 *
 * @param window 目标窗口
 * @param title 标题栏文本
 * @param icon 标题栏图标
 * @param onMinimize 最小化回调
 * @param onClose 关闭回调
 * @param initiallyMaximized 启动时是否为最大化
 * @param initialRestoreBounds 启动时若已最大化，记录的还原尺寸
 * @param onMaximizedChanged 最大化状态变化回调，参数为新的状态与还原尺寸
 * @param onGeometrySettled 窗口矩形稳定后的回调（非最大化状态下）
 * @param content 窗口内容
 */
@Composable
internal fun DesktopWindowFrame(
    window: Frame,
    title: String,
    icon: java.awt.Image?,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    initiallyMaximized: Boolean = false,
    initialRestoreBounds: Rectangle? = null,
    onMaximizedChanged: (Boolean, Rectangle) -> Unit = { _, _ -> },
    onGeometrySettled: (Rectangle) -> Unit = {},
    content: @Composable () -> Unit,
) {
    var maximized by remember { mutableStateOf(initiallyMaximized) }
    var restoreBounds by remember { mutableStateOf(initialRestoreBounds) }
    // 延迟落盘的 Job 用普通持有者保存，避免写状态触发重组
    val saveJobHolder = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val scope = rememberCoroutineScope()

    fun toggleMaximize() {
        if (maximized) {
            val bounds = restoreBounds ?: window.bounds
            window.bounds = bounds
            restoreBounds = null
            maximized = false
            applyWindowCorners(window, rounded = true)
            onMaximizedChanged(false, bounds)
        } else {
            val bounds = window.bounds
            restoreBounds = bounds
            window.bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
            maximized = true
            applyWindowCorners(window, rounded = false)
            onMaximizedChanged(true, bounds)
        }
    }

    fun scheduleGeometrySave() {
        saveJobHolder[0]?.cancel()
        saveJobHolder[0] =
            scope.launch {
                delay(GEOMETRY_SAVE_DEBOUNCE_MS)
                if (!maximized) {
                    onGeometrySettled(Rectangle(window.x, window.y, window.width, window.height))
                }
            }
    }

    DisposableEffect(window) {
        val listener =
            object : ComponentAdapter() {
                override fun componentMoved(event: ComponentEvent) = scheduleGeometrySave()

                override fun componentResized(event: ComponentEvent) = scheduleGeometrySave()
            }
        window.addComponentListener(listener)
        onDispose {
            window.removeComponentListener(listener)
        }
    }

    // 最大化时窗口贴满屏幕，描边需为直角
    val cornerRadius = if (maximized) 0.dp else WINDOW_CORNER_RADIUS
    Box(
        Modifier
            .fillMaxSize()
            .border(1.dp, MiuixTheme.colorScheme.dividerLine, RoundedCornerShape(cornerRadius)),
    ) {
        Column(Modifier.fillMaxSize()) {
            DesktopTitleBar(
                title = title,
                icon = icon,
                window = window,
                maximized = maximized,
                onToggleMaximize = { toggleMaximize() },
                onMinimize = onMinimize,
                onClose = onClose,
            )
            Box(Modifier.weight(1f)) { content() }
        }
        DesktopWindowResizeBorders(window)
    }
}

/** 移动窗口用的 user32 入口。 */
private interface MoveUser32 : StdCallLibrary {
    fun SetWindowPos(
        hWnd: Long,
        hWndInsertAfter: Long,
        x: Int,
        y: Int,
        cx: Int,
        cy: Int,
        uFlags: Int,
    ): Boolean
}

private val moveUser32: MoveUser32? by lazy {
    runCatching { Native.load("user32", MoveUser32::class.java) }.getOrNull()
}

/** DWM 窗口圆角属性。 */
private const val DWMWA_WINDOW_CORNER_PREFERENCE = 33

/** DWM 圆角取值：圆角。 */
private const val DWMWCP_ROUND = 2

/** DWM 圆角取值：直角。 */
private const val DWMWCP_DONOTROUND = 1

/** dwmapi 入口。 */
private interface DwmApi : StdCallLibrary {
    fun DwmSetWindowAttribute(
        hwnd: Long,
        attribute: Int,
        value: IntByReference,
        size: Int,
    ): Int
}

/**
 * 应用 Windows 11 窗口圆角。
 *
 * 无边框窗口不会由系统绘制圆角，这里显式请求 DWM 圆角；
 * 最大化时窗口需为直角，调用方按窗口状态传入 [rounded]。
 *
 * @param rounded 是否使用圆角
 */
internal fun applyWindowCorners(
    window: java.awt.Window,
    rounded: Boolean,
) {
    runCatching {
        val lib = Native.load("dwmapi", DwmApi::class.java)
        lib.DwmSetWindowAttribute(
            Native.getWindowID(window),
            DWMWA_WINDOW_CORNER_PREFERENCE,
            IntByReference(if (rounded) DWMWCP_ROUND else DWMWCP_DONOTROUND),
            4,
        )
    }
}

/** 以 Win32 直接移动窗口：逐帧走 AWT 的 setLocation 会带来重绘抖动。 */
private fun moveWindow(
    window: java.awt.Window,
    x: Int,
    y: Int,
    noRedraw: Boolean = false,
) {
    val lib = moveUser32
    var flags = SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE
    if (noRedraw) flags = flags or SWP_NOREDRAW
    val moved =
        if (lib == null) {
            false
        } else {
            runCatching {
                lib.SetWindowPos(Native.getWindowID(window), 0L, x, y, 0, 0, flags)
            }.getOrDefault(false)
        }
    if (!moved) window.setLocation(x, y)
}

/** 拖动结束：按最终指针位置落一次带重绘的移动，并同步回 AWT。 */
private fun finishDrag(
    window: java.awt.Window,
    drag: DragState,
) {
    val offset = drag.cursorOffset
    val pointer = MouseInfo.getPointerInfo()?.location
    if (offset != null && pointer != null) {
        val x = pointer.x - offset.x
        val y = pointer.y - offset.y
        moveWindow(window, x, y, noRedraw = false)
        window.setLocation(x, y)
        window.repaint()
    }
    drag.cursorOffset = null
}

/** 窗口四边与四角的调整大小感应区；无边框窗口不提供系统边框，由这里接管。 */
@Composable
internal fun DesktopWindowResizeBorders(window: Frame) {
    Box(Modifier.fillMaxSize()) {
        ResizeHandle(window, Modifier.fillMaxHeight().width(RESIZE_BAND).align(Alignment.CenterStart), -1, 0)
        ResizeHandle(window, Modifier.fillMaxHeight().width(RESIZE_BAND).align(Alignment.CenterEnd), 1, 0)
        ResizeHandle(window, Modifier.fillMaxWidth().height(RESIZE_BAND).align(Alignment.TopCenter), 0, -1)
        ResizeHandle(window, Modifier.fillMaxWidth().height(RESIZE_BAND).align(Alignment.BottomCenter), 0, 1)
        ResizeHandle(window, Modifier.size(12.dp).align(Alignment.TopStart), -1, -1)
        ResizeHandle(window, Modifier.size(12.dp).align(Alignment.TopEnd), 1, -1)
        ResizeHandle(window, Modifier.size(12.dp).align(Alignment.BottomStart), -1, 1)
        ResizeHandle(window, Modifier.size(12.dp).align(Alignment.BottomEnd), 1, 1)
    }
}

/**
 * 单个调整大小感应区。
 *
 * @param horizontal -1 拖左边、1 拖右边、0 不影响宽度
 * @param vertical -1 拖上边、1 拖下边、0 不影响高度
 */
@Composable
private fun ResizeHandle(
    window: Frame,
    modifier: Modifier,
    horizontal: Int,
    vertical: Int,
) {
    Box(
        modifier.pointerInput(horizontal, vertical) {
            detectDragGestures { change, drag ->
                change.consume()
                var x = window.x
                var y = window.y
                var w = window.width
                var h = window.height
                if (horizontal < 0) {
                    val dx = drag.x.toInt().coerceAtMost(w - MIN_WINDOW_WIDTH)
                    x += dx
                    w -= dx
                } else if (horizontal > 0) {
                    w = (w + drag.x.toInt()).coerceAtLeast(MIN_WINDOW_WIDTH)
                }
                if (vertical < 0) {
                    val dy = drag.y.toInt().coerceAtMost(h - MIN_WINDOW_HEIGHT)
                    y += dy
                    h -= dy
                } else if (vertical > 0) {
                    h = (h + drag.y.toInt()).coerceAtLeast(MIN_WINDOW_HEIGHT)
                }
                window.setBounds(x, y, w, h)
            }
        },
    )
}
