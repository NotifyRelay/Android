package github.xzynine.superislandui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import notifyrelay.base.util.Logger


/**
 * 位图工具类，用于处理文本到位图的转换等操作
 */
object BitmapUtils {
    private const val TAG = "BitmapUtils"
    
    /**
     * 将文本转换为位图
     * @param text 要转换的文本
     * @param forceFontSize 强制字体大小，null 表示使用默认大小
     * @param albumBitmap 专辑图 bitmap，可为 null
     * @return 生成的位图，失败返回 null
     */
    fun textToBitmap(text: String, forceFontSize: Float? = null, albumBitmap: Bitmap? = null): Bitmap? {
        try {
            // 检查文本是否为空
            if (text.isBlank()) {
                Logger.w(TAG, "文本为空，无法生成位图")
                return null
            }
            
            val fontSize = (forceFontSize ?: 40f) * 2f // 将基础字号调到当前的2倍

            // 查找第一行的分割点（等价字符长度不超过7）
            fun findSplitPoint(text: String): Int {
                var equivalentLength = 0f
                for (i in text.indices) {
                    val char = text[i]
                    val charLength = if (char in 'a'..'z') 0.5f else 1f
                    if (equivalentLength + charLength > 7) {
                        return i
                    }
                    equivalentLength += charLength
                }
                return text.length
            }
            
            // 计算文本的等价长度
            fun calculateEquivalentLength(text: String): Float {
                var length = 0f
                for (char in text) {
                    length += if (char in 'a'..'z') 0.5f else 1f
                }
                return length
            }
            
            // 仅处理单行文本，通过字体缩放来避免超限
            var currentFontSize = fontSize
            var paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = currentFontSize
                color = Color.WHITE
                textAlign = Paint.Align.LEFT
                typeface = Typeface.DEFAULT_BOLD
            }
            
            // 计算文本等价长度
            val textEquivalentLength = calculateEquivalentLength(text)
            
            // 如果文本等价长度超过2，开始逐渐缩小字体
            if (textEquivalentLength > 2) {
                // 根据等价长度计算缩小比例
                val scaleFactor = 1.0f - (textEquivalentLength - 2) * 0.1f
                currentFontSize = fontSize * scaleFactor.coerceAtLeast(0.5f)
                
                paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = currentFontSize
                    color = Color.WHITE
                    textAlign = Paint.Align.LEFT
                    typeface = Typeface.DEFAULT_BOLD
                }
            }
            
            val baseline = -paint.ascent() // ascent() 为负值
            // 计算文本宽度
            val textWidth = paint.measureText(text)
            // 计算专辑图占用的宽度（大约1.5等价字符）
            val albumWidth = if (albumBitmap != null) (fontSize * 1.5f).toInt() else 0
            
            // 计算添加专辑图后的宽度
            val widthWithAlbum = (textWidth + albumWidth + 20).toInt()
            // 计算不添加专辑图的宽度
            val widthWithoutAlbum = (textWidth + 10).toInt()
            val height = (baseline + paint.descent() + 10).toInt()
            
            // 空或无效尺寸的安全检查
            if (widthWithoutAlbum <= 0 || height <= 0) {
                Logger.w(TAG, "文本位图尺寸无效，width=$widthWithoutAlbum, height=$height")
                return null
            }

            // 确保尺寸在合理范围内
            val maxSize = 500
            
            // 检查是否超过位图限额
            val shouldAddAlbum = albumBitmap != null && widthWithAlbum <= maxSize
            
            val finalWidth = if (shouldAddAlbum) widthWithAlbum.coerceAtMost(maxSize) else widthWithoutAlbum.coerceAtMost(maxSize)
            val finalHeight = height.coerceAtMost(maxSize)
            
            val image = createBitmap(finalWidth, finalHeight)
            val canvas = Canvas(image)
            
            // 绘制专辑图（如果有且未超限）
            var textX = 10f
            if (shouldAddAlbum) {
                // 计算专辑图的缩放比例，保持原始宽高比
                val albumHeight = height - 10
                val scaleFactor = albumHeight.toFloat() / albumBitmap.height
                val scaledWidth = (albumBitmap.width * scaleFactor).toInt()
                
                // 缩放专辑图到合适大小，保持宽高比
                val scaledAlbumBitmap = Bitmap.createScaledBitmap(
                    albumBitmap, 
                    scaledWidth, 
                    albumHeight, 
                    true
                )
                canvas.drawBitmap(scaledAlbumBitmap, 5f, 5f, null)
                textX = scaledWidth + 15f
            }
            
            // 绘制文本
            canvas.drawText(text, textX, baseline + 5f, paint)
            Logger.d(TAG, "生成文本位图成功，尺寸: ${finalWidth}x${finalHeight}")
            return image
        } catch (e: Exception) {
            Logger.w(TAG, "生成文本位图失败: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * 将进度数据转换为环形进度圈位图
     * @param progress 进度值 (0-100)
     * @param colorReach 已达到部分的颜色
     * @param colorUnReach 未达到部分的颜色
     * @param isCCW 是否逆时针绘制
     * @return 生成的位图，失败返回 null
     */
    fun progressToBitmap(progress: Int, colorReach: String? = null, colorUnReach: String? = null, isCCW: Boolean = false): Bitmap? {
        try {
            // 检查进度值是否有效
            if (progress !in 0..100) {
                Logger.w(TAG, "进度值无效，progress=$progress")
                return null
            }
            
            val size = 100 // 位图大小
            val bitmap = createBitmap(size, size)
            val canvas = Canvas(bitmap)
            
            // 背景透明
            canvas.drawColor(Color.TRANSPARENT)
            
            // 计算进度角度
            val sweepAngle = (progress / 100f) * 360f
            val startAngle = if (isCCW) 90f else -90f
            
            // 绘制未达到的部分
            val paintUnReach = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 10f
                color = colorUnReach?.let { 
                    try {
                        it.toColorInt()
                    } catch (e: Exception) {
                        Logger.w(TAG, "解析未达到部分颜色失败: ${e.message}")
                        "#888888".toColorInt() // 默认灰色
                    }
                } ?: "#888888".toColorInt() // 默认灰色
            }
            
            val centerX = size / 2f
            val centerY = size / 2f
            val radius = (size - paintUnReach.strokeWidth) / 2f
            
            // 绘制未达到的圆环
            canvas.drawArc(
                centerX - radius, centerY - radius,
                centerX + radius, centerY + radius,
                startAngle, 360f, false, paintUnReach
            )
            
            // 绘制已达到的部分
            val paintReach = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 10f
                color = colorReach?.let { 
                    try {
                        it.toColorInt()
                    } catch (e: Exception) {
                        Logger.w(TAG, "解析已达到部分颜色失败: ${e.message}")
                        "#00FF00".toColorInt() // 默认绿色
                    }
                } ?: "#00FF00".toColorInt() // 默认绿色
            }
            
            // 绘制已达到的圆弧
            canvas.drawArc(
                centerX - radius, centerY - radius,
                centerX + radius, centerY + radius,
                startAngle, sweepAngle, false, paintReach
            )
            
            Logger.d(TAG, "生成进度位图成功，进度: $progress%")
            return bitmap
        } catch (e: Exception) {
            Logger.w(TAG, "生成进度位图失败: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
}
