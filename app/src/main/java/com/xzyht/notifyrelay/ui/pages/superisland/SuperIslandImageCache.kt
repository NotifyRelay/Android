package com.xzyht.notifyrelay.ui.pages.superisland

import android.graphics.Bitmap
import androidx.core.graphics.scale
import kotlin.math.max
import kotlin.math.roundToInt

internal object SuperIslandImageCache {
    private const val MAX_CACHE_SIZE = 32
    private val cache =
        object : LinkedHashMap<String, Bitmap>(MAX_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > MAX_CACHE_SIZE
        }

    fun get(key: String): Bitmap? =
        synchronized(this) {
            val cached = cache[key]
            if (cached != null && cached.isRecycled) {
                cache.remove(key)
                return@synchronized null
            }
            cached
        }

    fun put(
        key: String,
        bitmap: Bitmap,
    ): Bitmap {
        if (bitmap.isRecycled) return bitmap
        val normalized = normalizeBitmap(bitmap)
        synchronized(this) {
            cache[key] = normalized
        }
        return normalized
    }

    private fun normalizeBitmap(source: Bitmap): Bitmap {
        if (source.isRecycled) return source
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return source

        var working = source
        val largestSide = max(width, height)
        if (largestSide > SUPER_ISLAND_IMAGE_MAX_DIMENSION) {
            val scale = SUPER_ISLAND_IMAGE_MAX_DIMENSION.toFloat() / largestSide.toFloat()
            val targetWidth = max(1, (width * scale).roundToInt())
            val targetHeight = max(1, (height * scale).roundToInt())
            working = source.scale(targetWidth, targetHeight)
        }

        if (working.config == Bitmap.Config.HARDWARE) {
            working.copy(Bitmap.Config.ARGB_8888, false)?.let { working = it }
        }
        if (working !== source && !source.isRecycled) {
            try {
                source.recycle()
            } catch (_: Exception) {
            }
        }
        return working
    }
}
