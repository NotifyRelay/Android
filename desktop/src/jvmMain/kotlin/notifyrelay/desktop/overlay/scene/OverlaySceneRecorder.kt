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
        val raw = out.toString(UTF_8)
        return extractImages(injectAnimations(raw))
    }

    /**
     * 把哨兵色标记的动态元素改写成 SMIL 动画。
     *
     * 时钟、滚动文本与频谱原本都要靠逐帧重推场景来表达，
     * 接收端每帧重新解析整份文档，动画因此很卡。
     * 改为把「随时间如何变化」写进文档，由接收端按自己的时钟推进：
     * JVM 只在内容变化时推一次，静止期间零开销。
     */
    private fun injectAnimations(svg: String): String {
        var result = svg
        result = rewriteSpectrum(result)
        result = rewriteScroll(result)
        return result
    }

    /** 频谱条：哨兵色矩形改为正弦形态的位移动画。 */
    private fun rewriteSpectrum(svg: String): String =
        SPECTRUM_RECT.replace(svg) { match ->
            val band = match.groupValues[1].toInt(16)
            if (band !in 0 until SPECTRUM_BAND_COUNT) return@replace match.value
            val x = match.groupValues[2].toDoubleOrNull() ?: return@replace match.value
            val y = match.groupValues[3].toDoubleOrNull() ?: return@replace match.value
            val w = match.groupValues[4].toDoubleOrNull() ?: return@replace match.value
            val h = match.groupValues[5].toDoubleOrNull() ?: return@replace match.value

            val center = y + h / 2
            val regionHeight = SPECTRUM_REGION_HEIGHT
            val ys = StringBuilder()
            val heights = StringBuilder()
            val times = StringBuilder()
            for (k in 0..SPECTRUM_KEYFRAMES) {
                val frac = k.toDouble() / SPECTRUM_KEYFRAMES
                val phase = frac * 2 * Math.PI + band * SPECTRUM_PHASE_STEP
                val osc = SPECTRUM_OSC_BASE + SPECTRUM_OSC_SWING * kotlin.math.sin(phase)
                val dist = kotlin.math.min(kotlin.math.abs(band - 1), kotlin.math.abs(band - 3)).toDouble()
                val baseFactor = 1 - dist * SPECTRUM_PEAK_DECAY
                val barH =
                    kotlin.math
                        .max(1.0, regionHeight * baseFactor * osc)
                val top = center - barH / 2
                if (k > 0) {
                    ys.append(';')
                    heights.append(';')
                    times.append(';')
                }
                ys.append("%.2f".format(java.util.Locale.ROOT, top))
                heights.append("%.2f".format(java.util.Locale.ROOT, barH))
                times.append("%.3f".format(java.util.Locale.ROOT, frac))
            }
            val dur = "%.4f".format(java.util.Locale.ROOT, 1.0 / SPECTRUM_PHASE_PER_SEC)

            "<rect x=\"" + x + "\" width=\"" + w + "\" fill=\"white\">" +
                "<animate attributeName=\"y\" values=\"" + ys + "\" keyTimes=\"" + times +
                "\" dur=\"" + dur + "s\" repeatCount=\"indefinite\"/>" +
                "<animate attributeName=\"height\" values=\"" + heights + "\" keyTimes=\"" + times +
                "\" dur=\"" + dur + "s\" repeatCount=\"indefinite\"/>" +
                "</rect>"
        }

    /** 滚动文本：哨兵色的 <text> 改为水平往返位移动画。 */
    private fun rewriteScroll(svg: String): String =
        SCROLL_TEXT.replace(svg) { match ->
            val before = match.groupValues[1]
            val tx = match.groupValues[2].toDoubleOrNull() ?: return@replace match.value
            val ty = match.groupValues[3].toDoubleOrNull() ?: return@replace match.value
            val after = match.groupValues[4]
            val body = match.groupValues[5]

            // 初始平移必须并入动画取值：外层 animateTransform 会整体覆盖内层 transform，
            // 若从 0 0 开始，文本会被拉回原点而不是留在原布局位置。
            val start =
                "%.2f".format(java.util.Locale.ROOT, tx) + " " +
                    "%.2f".format(java.util.Locale.ROOT, ty)
            val end =
                "%.2f".format(java.util.Locale.ROOT, tx - SCROLL_WIDTH) + " " +
                    "%.2f".format(java.util.Locale.ROOT, ty)

            // 哨兵色只用于识别，落到文档里要换回正常颜色
            val attrs = (before + after).replace(SCROLL_SENTINEL_FILL, "fill=\"white\"")

            "<g><animateTransform attributeName=\"transform\" type=\"translate\" " +
                "values=\"" + start + ";" + end + ";" + start + "\" keyTimes=\"0;0.5;1\" " +
                "dur=\"" + SCROLL_PERIOD_SEC + "s\" repeatCount=\"indefinite\"/>" +
                "<text " + attrs + ">" + body + "</text></g>"
        }

    /**
     * 抽出位图数据，并把渲染所需的引用改成可解析形式。
     *
     * SVGCanvas 会把图片写成 <defs><image id="..." xlink:href="data:...">，
     * 再以 <use> 引用。内联数据占据场景体积的绝大部分，故抽出后场景只留引用；
     * 同时 Skia 不渲染 <use> 对 <image> 的间接引用（实测画不出内容），
     * 因此这里把 <use> 就地替换成 <image>，几何属性沿用原定义。
     */
    private fun extractImages(svg: String): RecordedScene {
        val images = LinkedHashMap<String, ByteArray>()
        val geom = HashMap<String, String>()

        // 先摘掉 defs 内的位图定义（连同 base64），文档里只留引用
        val withoutDefs =
            DEF_IMAGE.replace(svg) { match ->
                val id = match.groupValues[1]
                geom[id] = match.groupValues[2]
                runCatching {
                    images[id] =
                        java.util.Base64
                            .getDecoder()
                            .decode(match.groupValues[3])
                }
                ""
            }

        val rewritten =
            USE_IMAGE.replace(withoutDefs) { match ->
                val id = match.groupValues[2]
                val attrs = geom[id]
                if (attrs == null) {
                    match.value
                } else {
                    "<image " + match.groupValues[1] + attrs + " xlink:href=\"" + IMAGE_URI_PREFIX + id + "\"/>"
                }
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

        /**
         * 位图引用的 URI 前缀。
         *
         * 不能直接用 imageId 本身：元素 id 与 href 同值时形成自引用，
         * 渲染器会把 <image> 当作引用自身，最终什么也画不出来。
         */
        private const val IMAGE_URI_PREFIX = "nrimg:"

        /** 频谱哨兵色蓝色的起始值；与 SceneSentinels 保持一致。 */
        private const val SPECTRUM_SENTINEL_BASE = 0x10

        /** 频谱条数量。 */
        private const val SPECTRUM_BAND_COUNT = 5

        /** 每周期采样数：16 段足以让正弦曲线看起来平滑。 */
        private const val SPECTRUM_KEYFRAMES = 16

        /** 频谱区域高度（dp）；与绘制时使用的区域尺寸一致。 */
        private const val SPECTRUM_REGION_HEIGHT = 80.0

        /** 震荡速度：每秒相位。 */
        private const val SPECTRUM_PHASE_PER_SEC = 3.5

        /** 每条独立相位偏移。 */
        private const val SPECTRUM_PHASE_STEP = 1.1

        /** 震荡下限与摆幅。 */
        private const val SPECTRUM_OSC_BASE = 0.55

        private const val SPECTRUM_OSC_SWING = 0.45

        /** 离波峰每远一格的衰减。 */
        private const val SPECTRUM_PEAK_DECAY = 0.35

        /** 滚动文本哨兵色的字面量；改写时替换回正常颜色。 */
        private const val SCROLL_SENTINEL_FILL = "fill=\"#F10240\""

        /** 滚动文本的位移幅度。 */
        private const val SCROLL_WIDTH = 100.0

        /** 滚动文本的一个往返周期（秒）。 */
        private const val SCROLL_PERIOD_SEC = 6.0

        /** 匹配哨兵色频谱条：条序（颜色末位）、x、y、width、height。 */
        private val SPECTRUM_RECT =
            Regex(
                """<rect fill="#F1023([0-9A-Fa-f])" x="([^"]*)" y="([^"]*)" width="([^"]*)" height="([^"]*)"/>""",
            )

        /**
         * 匹配哨兵色滚动文本。
         *
         * 分组：1=平移 X，2=平移 Y，3=除 transform 外的其余属性，4=文本内容。
         * 其余属性必须原样保留：字号、字体与逐字定位都在其中，丢了文本就无法正常排版。
         */
        private val SCROLL_TEXT =
            Regex(
                """<text ([^>]*?)transform="translate\(([^ ]+) ([^)]+)\)"([^>]*)>([\s\S]*?)</text>""",
            )

        /**
         * 匹配 <defs> 内的位图定义：捕获 id、base64 与其余几何属性。
         *
         * 末尾的闭合引号必须一并吃掉，否则替换后属性边界错乱。
         */
        private val DEF_IMAGE =
            Regex(
                """<image\s+id="([^"]*)"([^>]*?)xlink:href="data:image/[a-z]+;base64,([A-Za-z0-9+/=]+)"""",
            )

        /** 匹配 <use ... xlink:href="#img_N"/>，用于改写成直接绘制的 <image>。 */
        private val USE_IMAGE =
            Regex("""<use\s+([^>]*?)xlink:href="#([^"]*)"\s*/>""")

        private val SCENE_FIELD: Field =
            ImageComposeScene::class.java
                .getDeclaredField("scene")
                .apply { isAccessible = true }
    }
}
