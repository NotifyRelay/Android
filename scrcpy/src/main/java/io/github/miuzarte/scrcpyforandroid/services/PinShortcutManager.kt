package io.github.miuzarte.scrcpyforandroid.services

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.drawable.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import io.github.miuzarte.scrcpyforandroid.MainActivity
import notifyrelay.data.config.ScrcpyDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring

object PinShortcutManager {

    fun createPinnedShortcut(
        context: Context,
        deviceName: String,
        deviceIp: String,
        devicePort: Int = ScrcpyDefaults.ADB_PORT
    ): Boolean {
        val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
            ?: return false

        if (!shortcutManager.isRequestPinShortcutSupported) {
            return false
        }

        val iconBitmap = createIconBitmapFromImageVector(MiuixIcons.ScreenMirroring)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setClass(context, MainActivity::class.java)
            putExtra("shortcut_device_ip", deviceIp)
            putExtra("shortcut_device_port", devicePort)
            putExtra("shortcut_device_name", deviceName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        val shortcut = ShortcutInfo.Builder(context, "pinned_${deviceIp}_$devicePort")
            .setShortLabel(deviceName)
            .setLongLabel(deviceName)
            .setIcon(Icon.createWithBitmap(iconBitmap))
            .setIntent(intent)
            .build()

        return try {
            shortcutManager.requestPinShortcut(shortcut, null)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun createIconBitmapFromImageVector(imageVector: ImageVector): Bitmap {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        
        val viewportWidth = imageVector.viewportWidth
        val viewportHeight = imageVector.viewportHeight
        val scale = size / maxOf(viewportWidth, viewportHeight)
        val offsetX = (size - viewportWidth * scale) / 2f
        val offsetY = (size - viewportHeight * scale) / 2f
        
        for (i in 0 until imageVector.root.size) {
            val node = imageVector.root[i]
            if (node !is VectorPath) continue
            
            val pathNodes: List<PathNode> = node.pathData
            val path = Path()
            
            pathNodes.forEach { pathNode ->
                when (pathNode) {
                    is PathNode.MoveTo -> path.moveTo(offsetX + pathNode.x * scale, offsetY + pathNode.y * scale)
                    is PathNode.LineTo -> path.lineTo(offsetX + pathNode.x * scale, offsetY + pathNode.y * scale)
                    is PathNode.CurveTo -> path.cubicTo(
                        offsetX + pathNode.x1 * scale, offsetY + pathNode.y1 * scale,
                        offsetX + pathNode.x2 * scale, offsetY + pathNode.y2 * scale,
                        offsetX + pathNode.x3 * scale, offsetY + pathNode.y3 * scale
                    )
                    is PathNode.QuadTo -> path.quadTo(
                        offsetX + pathNode.x1 * scale, offsetY + pathNode.y1 * scale,
                        offsetX + pathNode.x2 * scale, offsetY + pathNode.y2 * scale
                    )
                    is PathNode.Close -> path.close()
                    is PathNode.RelativeMoveTo -> path.rMoveTo(pathNode.dx * scale, pathNode.dy * scale)
                    is PathNode.RelativeLineTo -> path.rLineTo(pathNode.dx * scale, pathNode.dy * scale)
                    is PathNode.RelativeCurveTo -> path.rCubicTo(
                        pathNode.dx1 * scale, pathNode.dy1 * scale,
                        pathNode.dx2 * scale, pathNode.dy2 * scale,
                        pathNode.dx3 * scale, pathNode.dy3 * scale
                    )
                    is PathNode.RelativeQuadTo -> path.rQuadTo(
                        pathNode.dx1 * scale, pathNode.dy1 * scale,
                        pathNode.dx2 * scale, pathNode.dy2 * scale
                    )
                    else -> {}
                }
            }
            
            canvas.drawPath(path, paint)
        }
        
        return bitmap
    }
}
