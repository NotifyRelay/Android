package github.xzynine.superislandui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import notifyrelay.base.log.Logger
import notifyrelay.base.util.measureTime

/**
 * 位图工具：文本与环形进度转换为位图。
 *
 * 全部用 Compose 的 [ImageBitmap] 与 Canvas 绘制，不含平台分叉；
 * 结果为 [ImageBitmap]，Android 侧在需要系统位图时用 `asAndroidBitmap()` 转换。
 */
object BitmapUtils {
    private const val TAG = "BitmapUtils"

    private const val DEFAULT_FONT_SIZE = 40f
    private const val FONT_SIZE_SCALE = 2f
    private const val EQUIVALENT_LENGTH_BASE = 2f
    private const val EQUIVALENT_LENGTH_STEP = 0.1f
    private const val MIN_FONT_SCALE = 0.5f
    private const val MIN_TEXT_SCALE_X = 0.5f

    private const val MAX_BITMAP_SIZE = 500
    private const val MAX_TEXT_LENGTH_FOR_ALBUM = 6

    private const val HEIGHT_PADDING = 10
    private const val ALBUM_HEIGHT_PADDING = 10
    private const val LEFT_PADDING = 10f
    private const val ALBUM_DRAW_X = 5f
    private const val ALBUM_DRAW_Y = 5f
    private const val ALBUM_TEXT_SPACING = 15f
    private const val ALBUM_FIXED_PADDING = 20
    private const val ALBUM_FIXED_PADDING_F = 20f

    private const val PROGRESS_SIZE = 100
    private const val PROGRESS_STROKE_WIDTH = 10f

    private const val MAX_CACHE_SIZE = 10

    private const val DEFAULT_UNREACH_COLOR = "#888888"
    private const val DEFAULT_REACH_COLOR = "#00FF00"
    private const val FALLBACK_UNREACH_ARGB = 0xFF888888.toInt()
    private const val FALLBACK_REACH_ARGB = 0xFF00FF00.toInt()

    /** 绘制用的固定密度：位图尺寸与字号都以像素计，与设备无关。 */
    private val bitmapDensity = Density(density = 1f, fontScale = 1f)

    private val cacheLock = SynchronizedObject()

    // Bitmap 缓存池，支持不同尺寸的 bitmap 复用
    private val bitmapCache = mutableMapOf<Int, MutableList<ImageBitmap>>()

    /**
     * 将文本转换为位图。
     *
     * @param text 要转换的文本
     * @param forceFontSize 强制字体大小，null 表示使用默认大小
     * @param albumBitmap 专辑图，可为 null
     * @param textMeasurer 文本度量器；由调用方注入（commonMain 无法自建字体解析器）
     * @return 生成的位图，失败返回 null
     */
    fun textToBitmap(
        text: String,
        textMeasurer: TextMeasurer,
        forceFontSize: Float? = null,
        albumBitmap: ImageBitmap? = null,
    ): ImageBitmap? =
        measureTime(TAG, "textToBitmap") {
            try {
                if (text.isBlank()) {
                    Logger.w(TAG, "文本为空，无法生成位图")
                    return@measureTime null
                }

                val measurer = textMeasurer
                val fontSize = (forceFontSize ?: DEFAULT_FONT_SIZE) * FONT_SIZE_SCALE

                // 字符权重缓存
                val charWeightCache = mutableMapOf<Char, Float>()

                fun calculateEquivalentLength(target: String): Float {
                    var length = 0f
                    for (char in target) {
                        length += charWeightCache.getOrPut(char) { if (char in 'a'..'z') 0.5f else 1f }
                    }
                    return length
                }

                val textEquivalentLength = calculateEquivalentLength(text)
                var currentFontSize = fontSize
                if (textEquivalentLength > EQUIVALENT_LENGTH_BASE) {
                    val scaleFactor = 1.0f - (textEquivalentLength - EQUIVALENT_LENGTH_BASE) * EQUIVALENT_LENGTH_STEP
                    currentFontSize = fontSize * scaleFactor.coerceAtLeast(MIN_FONT_SCALE)
                }

                val metrics = measurer.measureTextMetrics(text, currentFontSize, 1f)
                val baseline = -metrics.ascent
                var textWidth = metrics.width
                val height = (baseline + metrics.descent + HEIGHT_PADDING).toInt()

                // 计算真实的专辑图宽度
                var scaledAlbumWidth = 0
                val albumHeight = height - ALBUM_HEIGHT_PADDING
                if (albumBitmap != null) {
                    val albumSourceHeight = albumBitmap.height
                    if (albumHeight > 0 && albumSourceHeight > 0) {
                        val scaleFactor = albumHeight.toFloat() / albumSourceHeight
                        scaledAlbumWidth = (albumBitmap.width * scaleFactor).toInt()
                    }
                }

                val widthWithAlbum = (textWidth + scaledAlbumWidth + ALBUM_FIXED_PADDING).toInt()
                val widthWithoutAlbum = (textWidth + LEFT_PADDING).toInt()

                if (widthWithoutAlbum <= 0 || height <= 0) {
                    Logger.w(TAG, "文本位图尺寸无效，width=$widthWithoutAlbum, height=$height")
                    return@measureTime null
                }

                val shouldAddAlbum =
                    albumBitmap != null &&
                        scaledAlbumWidth > 0 &&
                        widthWithAlbum <= MAX_BITMAP_SIZE &&
                        text.length <= MAX_TEXT_LENGTH_FOR_ALBUM

                var finalWidth = if (shouldAddAlbum) widthWithAlbum else widthWithoutAlbum
                finalWidth = finalWidth.coerceAtMost(MAX_BITMAP_SIZE)
                val finalHeight = height.coerceAtMost(MAX_BITMAP_SIZE)

                // 确保文本能完全显示在 finalWidth 内
                val availableTextWidth =
                    if (shouldAddAlbum) {
                        finalWidth - scaledAlbumWidth - ALBUM_FIXED_PADDING_F
                    } else {
                        finalWidth - LEFT_PADDING
                    }

                // 如果文本宽度超过可用宽度，水平缩放文字（变为窄体）
                var textScaleX = 1f
                if (textWidth > availableTextWidth) {
                    textScaleX = (availableTextWidth / textWidth).coerceAtLeast(MIN_TEXT_SCALE_X)
                    textWidth = measurer.measureTextMetrics(text, currentFontSize, textScaleX).width
                }

                val image = getOrCreateBitmap(finalWidth, finalHeight)
                Canvas(image).drawInto(finalWidth, finalHeight) {
                    if (shouldAddAlbum) {
                        drawImage(
                            image = albumBitmap,
                            srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                            srcSize =
                                androidx.compose.ui.unit
                                    .IntSize(albumBitmap.width, albumBitmap.height),
                            dstOffset =
                                androidx.compose.ui.unit
                                    .IntOffset(ALBUM_DRAW_X.toInt(), ALBUM_DRAW_Y.toInt()),
                            dstSize =
                                androidx.compose.ui.unit
                                    .IntSize(scaledAlbumWidth, albumHeight),
                        )
                    }
                    val textX = if (shouldAddAlbum) scaledAlbumWidth + ALBUM_TEXT_SPACING else LEFT_PADDING
                    val layout =
                        measurer.measure(
                            text = text,
                            style =
                                TextStyle(
                                    fontSize = currentFontSize.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                ),
                            constraints =
                                androidx.compose.ui.unit
                                    .Constraints(maxWidth = Int.MAX_VALUE, maxHeight = Int.MAX_VALUE),
                        )
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(textX, baseline - metrics.ascent + ALBUM_DRAW_Y),
                    )
                }

                Logger.d(TAG, "生成文本位图成功，尺寸: ${finalWidth}x$finalHeight")
                return@measureTime image
            } catch (e: IllegalArgumentException) {
                Logger.w(TAG, "生成文本位图失败: ${e.message}")
                return@measureTime null
            } catch (e: IllegalStateException) {
                Logger.w(TAG, "生成文本位图失败: ${e.message}")
                return@measureTime null
            }
        }

    /**
     * 将进度数据转换为环形进度圈位图。
     *
     * @param progress 进度值 (0-100)
     * @param colorReach 已达到部分的颜色
     * @param colorUnReach 未达到部分的颜色
     * @param isCCW 是否逆时针绘制
     * @return 生成的位图，失败返回 null
     */
    fun progressToBitmap(
        progress: Int,
        colorReach: String? = null,
        colorUnReach: String? = null,
        isCCW: Boolean = false,
    ): ImageBitmap? =
        measureTime(TAG, "progressToBitmap") {
            try {
                if (progress !in 0..100) {
                    Logger.w(TAG, "进度值无效，progress=$progress")
                    return@measureTime null
                }

                val bitmap = getOrCreateBitmap(PROGRESS_SIZE, PROGRESS_SIZE)
                val colorUnReachArg = resolveColor(colorUnReach, DEFAULT_UNREACH_COLOR, FALLBACK_UNREACH_ARGB, "未达到部分")
                val colorReachArg = resolveColor(colorReach, DEFAULT_REACH_COLOR, FALLBACK_REACH_ARGB, "已达到部分")

                Canvas(bitmap).drawInto(PROGRESS_SIZE, PROGRESS_SIZE) {
                    val size = PROGRESS_SIZE.toFloat()
                    val stroke = PROGRESS_STROKE_WIDTH
                    val radius = (size - stroke) / 2f
                    val sweepAngle = (progress / 100f) * 360f
                    val startAngle = if (isCCW) 90f else -90f

                    drawCircle(
                        color = Color(colorUnReachArg),
                        radius = radius,
                        center = Offset(size / 2f, size / 2f),
                        style = Stroke(width = stroke),
                    )
                    drawArc(
                        color = Color(colorReachArg),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset((size - radius * 2) / 2f, (size - radius * 2) / 2f),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = stroke),
                    )
                }

                Logger.d(TAG, "生成进度位图成功，进度: $progress%")
                return@measureTime bitmap
            } catch (e: Exception) {
                Logger.w(TAG, "生成进度位图失败: ${e.message}", e)
                return@measureTime null
            }
        }

    /** 度量文本的 ascent / descent / width（像素）。 */
    private fun TextMeasurer.measureTextMetrics(
        text: String,
        fontSize: Float,
        textScaleX: Float,
    ): TextMetrics {
        val layout =
            measure(
                text = text,
                style = TextStyle(fontSize = fontSize.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
                constraints =
                    androidx.compose.ui.unit
                        .Constraints(maxWidth = Int.MAX_VALUE, maxHeight = Int.MAX_VALUE),
            )
        val firstLine = layout.getLineBaseline(0).let { if (it > 0) it else layout.size.height.toFloat() }
        val ascent = if (layout.lineCount > 0) layout.getLineBaseline(0) - layout.getLineTop(0) else 0f
        return TextMetrics(
            ascent = -ascent,
            descent = layout.size.height - ascent,
            width = layout.size.width * textScaleX,
        )
    }

    /** 文本度量结果。ascent 为负值（基线上方），descent 为正值。 */
    data class TextMetrics(
        val ascent: Float,
        val descent: Float,
        val width: Float,
    )

    private fun resolveColor(
        text: String?,
        defaultText: String,
        fallbackArgb: Int,
        label: String,
    ): Int {
        if (text != null) {
            val parsed = parseColorOrNull(text)
            if (parsed != null) return parsed
            Logger.w(TAG, "解析$label 颜色失败: $text")
        }
        return parseColorOrNull(defaultText) ?: fallbackArgb
    }

    /** 解析颜色文本（`#RRGGBB` / `#AARRGGBB`）；失败返回 null。 */
    private fun parseColorOrNull(text: String): Int? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("#")) return null
        val hex = trimmed.substring(1)
        val value =
            when (hex.length) {
                6 -> "FF" + hex
                8 -> hex
                3 -> "FF" + hex.map { "" + it + it }.joinToString("")
                else -> return null
            }
        return runCatching { value.toLong(16).toInt() }.getOrNull()
    }

    private fun getOrCreateBitmap(
        width: Int,
        height: Int,
    ): ImageBitmap =
        synchronized(cacheLock) {
            val key = width * 10000 + height
            val cached = bitmapCache[key]
            if (cached != null && cached.isNotEmpty()) {
                return@synchronized cached.removeAt(cached.size - 1)
            }
            val newBitmap = ImageBitmap(width, height)
            val list = bitmapCache.getOrPut(key) { mutableListOf() }
            list.add(newBitmap)
            if (list.size > MAX_CACHE_SIZE) {
                list.removeAt(0)
            }
            newBitmap
        }

    /** 清理位图缓存池。 */
    fun clearCache() {
        synchronized(cacheLock) {
            bitmapCache.clear()
        }
    }

    /** 在画布上执行绘制；[width] / [height] 为位图尺寸。 */
    private inline fun Canvas.drawInto(
        width: Int,
        height: Int,
        block: DrawScope.() -> Unit,
    ) {
        CanvasDrawScope().draw(
            density = bitmapDensity,
            layoutDirection = LayoutDirection.Ltr,
            canvas = this,
            size = Size(width.toFloat(), height.toFloat()),
            block = block,
        )
    }
}
