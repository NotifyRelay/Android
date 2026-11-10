package notifyrelay.desktop.overlay.scene

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.skia.OutputWStream
import org.jetbrains.skia.Rect
import org.jetbrains.skia.svg.SVGCanvas
import java.io.ByteArrayOutputStream
import java.lang.reflect.Field

/**
 * 把叠加层的 Compose 合成结果录制为 SVG。
 *
 * 场景对象长期存活：叠加层的展开/收起、计时与滚动状态都保存在 Compose 的
 * 记忆槽里，每帧重建会让这些状态反复归零。因此本类持有一个
 * [ImageComposeScene]，逐帧只推进重组并把绘制命令录进 [SVGCanvas]。
 *
 * 密度固定为 1：这样 dp 数值、场景坐标与 SVG 单位三者一致，
 * 可点击区域可以直接用布局坐标表达，接收端只需按窗口尺寸整体缩放，
 * 不存在像素与 dp 之间的取整换算。
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

    /**
     * 推进一帧并录成 SVG 文本。
     *
     * 先经 [ImageComposeScene.render] 驱动重组与布局，再向 SVG 画布绘制；
     * 顺序不可颠倒，否则录到的是上一帧的布局结果。
     */
    fun record(nanoTime: Long): RecordedScene {
        // 触发重组与布局；返回的位图不使用，绘制改经 SVGCanvas 完成
        imageScene.render(nanoTime).close()

        val out = ByteArrayOutputStream()
        OutputWStream(out).use { stream ->
            val skiaCanvas =
                SVGCanvas.make(Rect.makeWH(widthDp.toFloat(), heightDp.toFloat()), stream)
            composeScene.draw(skiaCanvas.asComposeCanvas())
            skiaCanvas.close()
        }
        return extractImages(out.toString(UTF_8))
    }

    /**
     * 把内联的位图数据抽出来，文档里改写成对 imageId 的引用。
     *
     * SVGCanvas 会把图片写成 <image id="..." xlink:href="data:image/png;base64,...">，
     * 内联数据通常占据场景体积的绝大部分。抽出后场景只剩引用，
     * 图片本体经独立报文完整传输一次。
     */
    private fun extractImages(svg: String): RecordedScene {
        val images = LinkedHashMap<String, ByteArray>()
        val rewritten =
            IMAGE_HREF.replace(svg) { match ->
                val id = match.groupValues[1]
                val base64 = match.groupValues[2]
                runCatching {
                    images[id] =
                        java.util.Base64
                            .getDecoder()
                            .decode(base64)
                }
                " id=\"" + id + "\" xlink:href=\"" + id + "\""
            }
        return RecordedScene(document = rewritten, images = images)
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
        imageScene.close()
    }

    /** 一次录制的产物：文档与随附位图。 */
    internal class RecordedScene(
        val document: String,
        /** imageId 到 PNG 字节；文档中以同名 id 引用。 */
        val images: Map<String, ByteArray>,
    )

    private companion object {
        private const val UTF_8 = "UTF-8"

        /** 匹配 <image ... id="X" ... xlink:href="data:image/png;base64,YYY">。 */
        private val IMAGE_HREF =
            Regex("""\s+id="([^"]*)"(?:[^>]*?)xlink:href="data:image/[a-z]+;base64,([A-Za-z0-9+/=]+)""")

        private val SCENE_FIELD: Field =
            ImageComposeScene::class.java
                .getDeclaredField("scene")
                .apply { isAccessible = true }
    }
}
