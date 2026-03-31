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
import io.github.miuzarte.scrcpyforandroid.pages.ShortcutLaunchActivity
import notifyrelay.data.config.ScrcpyDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring

object DynamicShortcutManager {
    private const val MAX_SHORTCUTS = 4

    fun updateShortcuts(context: Context) {
        val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
            ?: return

        val onlineDevices = loadOnlineDevicesFromApp(context)
            .filter { it.deviceType?.lowercase() != "pc" }
            .take(MAX_SHORTCUTS)

        val iconBitmap = createIconBitmapFromImageVector(MiuixIcons.ScreenMirroring)

        val shortcuts = onlineDevices.map { device ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setClass(context, ShortcutLaunchActivity::class.java)
                putExtra("shortcut_device_ip", device.ip)
                putExtra("shortcut_device_port", ScrcpyDefaults.ADB_PORT)
                putExtra("shortcut_device_name", device.displayName)
                putExtra("shortcut_device_uuid", device.uuid)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            ShortcutInfo.Builder(context, "device_${device.uuid}")
                .setShortLabel(device.displayName)
                .setIcon(Icon.createWithBitmap(iconBitmap))
                .setIntent(intent)
                .build()
        }

        try {
            shortcutManager.dynamicShortcuts = shortcuts
        } catch (_: Exception) {}
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
