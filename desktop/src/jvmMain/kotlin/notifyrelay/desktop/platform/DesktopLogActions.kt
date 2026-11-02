package notifyrelay.desktop.platform

import notifyrelay.base.log.LogFileStore
import notifyrelay.base.log.zipLogShards
import java.awt.Desktop
import java.io.File

/**
 * 桌面端日志动作：打开目录与导出归档。
 *
 * 与关于页的公共区块对接，平台差异只体现在这里。
 */
object DesktopLogActions {
    /** 应用图标（用于关于页展示）。 */
    fun appIcon(): java.awt.image.BufferedImage? = runCatching { AppIcon.images().maxByOrNull { it.width } }.getOrNull()

    /** 在系统文件管理器中打开日志目录。 */
    fun openDirectory(): Boolean =
        runCatching {
            val info = LogFileStore.storageInfo() ?: return false
            val dir = File(info.directory)
            if (!dir.exists()) return false
            Desktop.getDesktop().open(dir)
            true
        }.getOrDefault(false)

    /** 导出全部分片到桌面，返回归档路径。 */
    fun exportArchive(): String? =
        runCatching {
            val info = LogFileStore.storageInfo() ?: return null
            val home = File(System.getProperty("user.home"))
            val desktop = File(home, "Desktop").let { if (it.isDirectory) it else home }
            val target = File(desktop, "notifyrelay-logs-" + System.currentTimeMillis() + ".zip")
            if (zipLogShards(info.directory, target.absolutePath)) target.absolutePath else null
        }.getOrNull()
}
