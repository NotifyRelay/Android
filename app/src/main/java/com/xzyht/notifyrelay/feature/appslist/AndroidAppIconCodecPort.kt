package com.xzyht.notifyrelay.feature.appslist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import com.xzyht.notifyrelay.nativecore.device.appsync.IconCodecPort
import notifyrelay.base.util.image.toBitmapOrDefault
import notifyrelay.base.util.image.toPngByteArray

/**
 * 图标编解码端口（Android）。
 *
 * 位图编解码用 Android 图形栈；本机图标来源取 [AppIconRepository]
 * （其内部含 PackageManager 取值与落库副作用）。
 */
class AndroidAppIconCodecPort(
    private val context: Context,
) : IconCodecPort {
    override fun decodePng(bytes: ByteArray): Any? = notifyrelay.base.util.decodeImageBitmap(bytes)

    override fun encodePng(platformBitmap: Any): ByteArray? = (platformBitmap as? Bitmap)?.toPngByteArray()

    override suspend fun localIconBytes(packageName: String): ByteArray? {
        val fromRepository = AppIconRepository.getAppIconAsync(context, packageName)
        if (fromRepository != null) return fromRepository.toPngByteArray()

        return try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val drawable = packageManager.getApplicationIcon(appInfo)
            val bitmap =
                if (drawable is BitmapDrawable) {
                    drawable.bitmap
                } else {
                    drawable.toBitmapOrDefault(96)
                }
            bitmap.toPngByteArray()
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            null
        }
    }
}
