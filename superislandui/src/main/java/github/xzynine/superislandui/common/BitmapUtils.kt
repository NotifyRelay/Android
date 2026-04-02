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
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
    }
    
    private val progressPaintUnReach = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        color = "#888888".toColorInt()
    }
    
    private val progressPaintReach = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        color = "#00FF00".toColorInt()
    }
    
    private var reusableBitmap: Bitmap? = null
    private var reusableBitmapWidth = 0
    private var reusableBitmapHeight = 0
    
    /**
     * 将文本转换为位图
     * @param text 要转换的文本
     * @param forceFontSize 强制字体大小，null 表示使用默认大小
     * @param albumBitmap 专辑图 bitmap，可为 null
     * @return 生成的位图，失败返回 null
     */
    fun textToBitmap(text: String, forceFontSize: Float? = null, albumBitmap: Bitmap? = null): Bitmap? {
        try {
            if (text.isBlank()) {
                Logger.w(TAG, "文本为空，无法生成位图")
                return null
            }
            
            val fontSize = (forceFontSize ?: 40f) * 2f

            fun calculateEquivalentLength(text: String): Float {
                var length = 0f
                for (char in text) {
                    length += if (char in 'a'..'z') 0.5f else 1f
                }
                return length
            }
            
            var currentFontSize = fontSize
            textPaint.textSize = currentFontSize
            
            val textEquivalentLength = calculateEquivalentLength(text)
            
            if (textEquivalentLength > 2) {
                val scaleFactor = 1.0f - (textEquivalentLength - 2) * 0.1f
                currentFontSize = fontSize * scaleFactor.coerceAtLeast(0.5f)
                textPaint.textSize = currentFontSize
            }
            
            val baseline = -textPaint.ascent()
            val textWidth = textPaint.measureText(text)
            val albumWidth = if (albumBitmap != null) (fontSize * 1.5f).toInt() else 0
            
            val widthWithAlbum = (textWidth + albumWidth + 20).toInt()
            val widthWithoutAlbum = (textWidth + 10).toInt()
            val height = (baseline + textPaint.descent() + 10).toInt()
            
            if (widthWithoutAlbum <= 0 || height <= 0) {
                Logger.w(TAG, "文本位图尺寸无效，width=$widthWithoutAlbum, height=$height")
                return null
            }

            val maxSize = 500
            
            val shouldAddAlbum = albumBitmap != null && widthWithAlbum <= maxSize
            
            val finalWidth = if (shouldAddAlbum) widthWithAlbum.coerceAtMost(maxSize) else widthWithoutAlbum.coerceAtMost(maxSize)
            val finalHeight = height.coerceAtMost(maxSize)
            
            val image = getOrCreateBitmap(finalWidth, finalHeight)
            val canvas = Canvas(image)
            
            var textX = 10f
            if (shouldAddAlbum) {
                val albumHeight = height - 10
                val scaleFactor = albumHeight.toFloat() / albumBitmap!!.height
                val scaledWidth = (albumBitmap.width * scaleFactor).toInt()
                
                val scaledAlbumBitmap = Bitmap.createScaledBitmap(
                    albumBitmap, 
                    scaledWidth, 
                    albumHeight, 
                    true
                )
                canvas.drawBitmap(scaledAlbumBitmap, 5f, 5f, null)
                textX = scaledWidth + 15f
            }
            
            canvas.drawText(text, textX, baseline + 5f, textPaint)
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
            if (progress !in 0..100) {
                Logger.w(TAG, "进度值无效，progress=$progress")
                return null
            }
            
            val size = 100
            val bitmap = getOrCreateBitmap(size, size)
            val canvas = Canvas(bitmap)
            
            val sweepAngle = (progress / 100f) * 360f
            val startAngle = if (isCCW) 90f else -90f
            
            progressPaintUnReach.color = colorUnReach?.let { 
                try {
                    it.toColorInt()
                } catch (e: Exception) {
                    Logger.w(TAG, "解析未达到部分颜色失败: ${e.message}")
                    "#888888".toColorInt()
                }
            } ?: "#888888".toColorInt()
            
            val centerX = size / 2f
            val centerY = size / 2f
            val radius = (size - progressPaintUnReach.strokeWidth) / 2f
            
            canvas.drawArc(
                centerX - radius, centerY - radius,
                centerX + radius, centerY + radius,
                startAngle, 360f, false, progressPaintUnReach
            )
            
            progressPaintReach.color = colorReach?.let { 
                try {
                    it.toColorInt()
                } catch (e: Exception) {
                    Logger.w(TAG, "解析已达到部分颜色失败: ${e.message}")
                    "#00FF00".toColorInt()
                }
            } ?: "#00FF00".toColorInt()
            
            canvas.drawArc(
                centerX - radius, centerY - radius,
                centerX + radius, centerY + radius,
                startAngle, sweepAngle, false, progressPaintReach
            )
            
            Logger.d(TAG, "生成进度位图成功，进度: $progress%")
            return bitmap
        } catch (e: Exception) {
            Logger.w(TAG, "生成进度位图失败: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    private fun getOrCreateBitmap(width: Int, height: Int): Bitmap {
        val existing = reusableBitmap
        if (existing != null && !existing.isRecycled && 
            reusableBitmapWidth == width && reusableBitmapHeight == height) {
            // 复用前完全清除内容
            existing.eraseColor(Color.TRANSPARENT)
            return existing
        }
        
        reusableBitmap?.recycle()
        reusableBitmap = createBitmap(width, height)
        reusableBitmapWidth = width
        reusableBitmapHeight = height
        return reusableBitmap!!
    }
    
    fun releaseResources() {
        reusableBitmap?.recycle()
        reusableBitmap = null
        reusableBitmapWidth = 0
        reusableBitmapHeight = 0
    }
}
