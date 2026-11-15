package notifyrelay.desktop.overlay.scene

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Pixmap
import java.lang.reflect.Field

/**
 * 把叠加层的 Compose 合成结果录制为位图。
 *
 * 场景对象长期存活：叠加层的展开/收起、计时与滚动状态都保存在 Compose 的
 * 记忆槽里，每帧重建会让这些状态反复归零。因此本类持有一个
 * [ImageComposeScene]，逐帧只推进重组。
 *
 * 密度固定为 1：这样 dp 数值、场景坐标与像素三者一致，
 * 可点击区域可以直接用布局坐标表达，接收端只需按窗口尺寸整体缩放。
 *
 * 像素缓冲按尺寸分配一次后复用：位图路径的每帧分配会成为主要开销。
 */
@OptIn(InternalComposeUiApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
internal class OverlaySceneRecorder(
    private val widthDp: Int,
    private val heightDp: Int,
    coroutineContext: kotlin.coroutines.CoroutineContext,
    content: @androidx.compose.runtime.Composable () -> Unit,
) : AutoCloseable {
    private val imageScene =
        ImageComposeScene(
            width = widthDp,
            height = heightDp,
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            coroutineContext = coroutineContext,
            content = content,
        )

    /** 内部 [ComposeScene] 未公开，反射取一次并缓存。 */
    private val composeScene: ComposeScene =
        SCENE_FIELD.get(imageScene) as ComposeScene

    /** 复用位图与其原生视图；尺寸变化时重建。 */
    private var bitmap: Bitmap? = null
    private var pixmap: Pixmap? = null

    /** 当前帧的像素行距与总字节数。 */
    private var stride = 0

    /**
     * 推进一帧并把像素交给 [consume]。
     *
     * 像素地址只在 [consume] 执行期间有效，因此写出必须在回调内完成。
     * 先经 [ImageComposeScene.render] 驱动重组与布局，再取像素；
     * 顺序不可颠倒，否则取到的是上一帧的布局结果。
     *
     * 优先走 [Image.peekPixels]：图像本身是光栅背衬时直接得到它的内存，
     * 整条路径没有像素拷贝；只有图像不可直接访问时才退回位图读回。
     */
    fun render(
        nanoTime: Long,
        consume: (RasterFrame) -> Unit,
    ) {
        val image = imageScene.render(nanoTime)
        try {
            val direct = runCatching { image.peekPixels() }.getOrNull()
            if (direct != null && direct.addr != 0L) {
                try {
                    val view =
                        RasterFrame(
                            width = image.width,
                            height = image.height,
                            stride = direct.rowBytes,
                            address = direct.addr,
                        )
                    consume(view)
                } finally {
                    direct.close()
                }
                return
            }

            ensureTarget(image.width, image.height)
            val target = bitmap
            val cached = pixmap
            if (target == null || cached == null) {
                return
            }

            if (!image.readPixels(target)) {
                return
            }

            consume(
                RasterFrame(
                    width = image.width,
                    height = image.height,
                    stride = stride,
                    address = cached.addr,
                ),
            )
        } finally {
            image.close()
        }
    }

    /** 按帧尺寸准备复用位图；尺寸未变时直接沿用。 */
    private fun ensureTarget(
        width: Int,
        height: Int,
    ) {
        val current = bitmap
        if (current != null && current.imageInfo.width == width && current.imageInfo.height == height) {
            return
        }

        pixmap?.close()
        pixmap = null
        current?.close()

        val info =
            ImageInfo(
                ColorInfo(ColorType.BGRA_8888, ColorAlphaType.PREMUL, ColorSpace.sRGB),
                width,
                height,
            )
        val next = Bitmap().apply { allocPixels(info) }
        bitmap = next
        stride = next.rowBytes
        pixmap = next.peekPixels()
    }

    /**
     * 注入一次指针事件。
     *
     * 点击不在 JVM 侧另建一套 key 派发：把坐标送回 Compose 的输入分发，
     * 既有的 clickable 与手势处理自动生效，新增控件无需在此登记。
     *
     * @param pressed true 为按下，false 为抬起
     * @param xDp 场景坐标 X（dp）
     * @param yDp 场景坐标 Y（dp）
     */
    fun sendPointer(
        pressed: Boolean,
        xDp: Float,
        yDp: Float,
        timestampMs: Long,
    ) {
        imageScene.sendPointerEvent(
            eventType = if (pressed) PointerEventType.Press else PointerEventType.Release,
            position = Offset(xDp, yDp),
            timeMillis = timestampMs,
            type = PointerType.Mouse,
            buttons = if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons(),
        )
    }

    override fun close() {
        pixmap?.close()
        pixmap = null
        bitmap?.close()
        bitmap = null
        imageScene.close()
    }

    private companion object {
        private val SCENE_FIELD: Field =
            ImageComposeScene::class.java
                .getDeclaredField("scene")
                .apply { isAccessible = true }
    }
}

/**
 * 一帧位图。
 *
 * [address] 是 BGRA 像素的原生地址，只在该帧的消费回调期间有效。
 */
internal class RasterFrame(
    val width: Int,
    val height: Int,
    val stride: Int,
    val address: Long,
) {
    /** 本帧像素占用的字节数。 */
    val byteCount: Int get() = stride * height
}
