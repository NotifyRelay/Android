package notifyrelay.desktop.platform

import notifyrelay.data.Storage
import java.awt.GraphicsEnvironment
import java.awt.Rectangle

/**
 * 主窗口位置与尺寸的持久化。
 *
 * 只记录非最大化时的窗口矩形与最大化标记；启动时据此还原，
 * 最大化状态下的还原尺寸取自最大化前的记录。
 */
object DesktopWindowPlacement {
    private const val KEY_BOUNDS = "desktop_window_bounds"

    private const val KEY_MAXIMIZED = "desktop_window_maximized"

    /** 读取上次的非最大化窗口矩形；未记录或数据无效时返回 null。 */
    fun loadBounds(): Rectangle? {
        val raw = Storage.getString(KEY_BOUNDS, "")
        if (raw.isEmpty()) return null
        val values = raw.split(',').map { it.trim().toIntOrNull() ?: return null }
        if (values.size != 4 || values[2] <= 0 || values[3] <= 0) return null
        return Rectangle(values[0], values[1], values[2], values[3])
    }

    /** 保存非最大化窗口矩形。 */
    fun saveBounds(bounds: Rectangle) {
        Storage.putString(KEY_BOUNDS, bounds.x.toString() + "," + bounds.y + "," + bounds.width + "," + bounds.height)
    }

    /** 读取上次是否以最大化退出。 */
    fun loadMaximized(): Boolean = Storage.getBoolean(KEY_MAXIMIZED, false)

    /** 保存最大化标记。 */
    fun saveMaximized(maximized: Boolean) {
        Storage.putBoolean(KEY_MAXIMIZED, maximized)
    }

    /** 把窗口矩形限制在屏幕内，避免保存的坐标落在已断开的显示器上。 */
    fun clampToScreen(bounds: Rectangle): Rectangle {
        val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val width = bounds.width.coerceIn(MIN_WIDTH, screen.width)
        val height = bounds.height.coerceIn(MIN_HEIGHT, screen.height)
        val x = bounds.x.coerceIn(screen.x - width + VISIBLE_MARGIN, screen.x + screen.width - VISIBLE_MARGIN)
        val y = bounds.y.coerceIn(screen.y, screen.y + screen.height - VISIBLE_MARGIN)
        return Rectangle(x, y, width, height)
    }

    /** 还原时允许的最小宽度。 */
    private const val MIN_WIDTH = 720

    /** 还原时允许的最小高度。 */
    private const val MIN_HEIGHT = 480

    /** 至少保留在屏幕内的像素。 */
    private const val VISIBLE_MARGIN = 120
}
