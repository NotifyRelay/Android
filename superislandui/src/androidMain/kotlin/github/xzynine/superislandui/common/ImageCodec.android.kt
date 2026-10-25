package github.xzynine.superislandui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap

internal actual val imageCodec: ImageCodec = AndroidImageCodec()

private class AndroidImageCodec : ImageCodec {
    override fun parseColorOrNull(text: String): Int? =
        try {
            Color.parseColor(text)
        } catch (_: IllegalArgumentException) {
            null
        }

    override fun measureText(
        text: String,
        fontSize: Float,
        textScaleX: Float,
    ): TextMetrics {
        val paint = textPaint(fontSize, textScaleX)
        return TextMetrics(
            ascent = paint.ascent(),
            descent = paint.descent(),
            width = paint.measureText(text),
        )
    }

    override fun createBitmap(
        width: Int,
        height: Int,
    ): Bitmap = createBitmap(width, height)

    override fun bitmapWidth(bitmap: Bitmap): Int = bitmap.width

    override fun bitmapHeight(bitmap: Bitmap): Int = bitmap.height

    override fun isRecycled(bitmap: Bitmap): Boolean = bitmap.isRecycled

    override fun recycle(bitmap: Bitmap) {
        bitmap.recycle()
    }

    override fun eraseToTransparent(bitmap: Bitmap) {
        bitmap.eraseColor(Color.TRANSPARENT)
    }

    override fun scaleBitmap(
        bitmap: Bitmap,
        width: Int,
        height: Int,
    ): Bitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)

    override fun drawTextBitmap(
        target: Bitmap,
        text: String,
        fontSize: Float,
        textScaleX: Float,
        baseline: Float,
        album: Bitmap?,
        albumWidth: Int,
        albumHeight: Int,
        textX: Float,
    ) {
        val canvas = Canvas(target)
        if (album != null && albumWidth > 0 && albumHeight > 0) {
            val scaledAlbumBitmap = scaleBitmap(album, albumWidth, albumHeight)
            try {
                canvas.drawBitmap(scaledAlbumBitmap, ALBUM_DRAW_X, ALBUM_DRAW_Y, null)
            } finally {
                scaledAlbumBitmap.recycle()
            }
        }
        canvas.drawText(text, textX, baseline, textPaint(fontSize, textScaleX))
    }

    override fun drawProgressBitmap(
        target: Bitmap,
        progress: Int,
        colorReachArgb: Int,
        colorUnReachArgb: Int,
        isCCW: Boolean,
    ) {
        val canvas = Canvas(target)
        val size = target.width
        val sweepAngle = (progress / 100f) * 360f
        val startAngle = if (isCCW) 90f else -90f

        val unreachPaint = progressPaint(colorUnReachArgb)
        val centerX = size / 2f
        val centerY = size / 2f
        val radius = (size - unreachPaint.strokeWidth) / 2f

        canvas.drawArc(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius,
            startAngle,
            360f,
            false,
            unreachPaint,
        )

        val reachPaint = progressPaint(colorReachArgb)
        canvas.drawArc(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius,
            startAngle,
            sweepAngle,
            false,
            reachPaint,
        )
    }

    private fun textPaint(
        fontSize: Float,
        textScaleX: Float,
    ): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
            typeface = Typeface.DEFAULT_BOLD
            textSize = fontSize
            this.textScaleX = textScaleX
        }

    private fun progressPaint(colorArgb: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = PROGRESS_STROKE_WIDTH
            color = colorArgb
        }

    private companion object {
        private const val ALBUM_DRAW_X = 5f
        private const val ALBUM_DRAW_Y = 5f
        private const val PROGRESS_STROKE_WIDTH = 10f
    }
}
