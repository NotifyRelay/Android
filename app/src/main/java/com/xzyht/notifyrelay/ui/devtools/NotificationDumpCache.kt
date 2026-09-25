package com.xzyht.notifyrelay.ui.devtools

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import notifyrelay.base.util.Logger
import java.io.File

/**
 * 通知字段转储页导出文件的**集中缓存目录**与分享出口。
 *
 * 设计：
 * - 所有导出 JSON 统一落在 `externalCacheDir/notification_dump/`（外部缓存目录）。
 *   选外部缓存而非内部缓存的原因：**文件管理器与部分第三方应用可直接浏览定位**该路径，
 *   符合「分享后仍能去文件管理器找到文件」的诉求；同时它仍是缓存目录，
 *   系统在存储紧张时可直接回收，无需应用参与。
 * - 分享走 [FileProvider]（authority `${applicationId}.notificationdump`）授予临时读权限，
 *   拉起系统分享面板；**不做「另存为」**，由用户在选择器里自行决定目标。
 * - 目录内容**不自动清理**，由页面上的「清理导出缓存」按钮显式清理（见 [clearAll]）。
 */
internal object NotificationDumpCache {
    private const val TAG = "NotificationDumpCache"

    /** 缓存子目录名，与 `res/xml/notification_dump_paths.xml` 中的 `path` 必须一致。 */
    const val DIR_NAME = "notification_dump"

    /** FileProvider authority，与 `AndroidManifest.xml` 中的声明必须一致。 */
    private const val AUTHORITY_SUFFIX = ".notificationdump"

    /**
     * 缓存目录；`externalCacheDir` 不可用时（外部存储未挂载）回退到内部 `cacheDir`，
     * 保证导出功能不因存储状态直接失败。
     */
    fun dir(context: Context): File {
        val base = context.externalCacheDir ?: context.cacheDir
        return File(base, DIR_NAME).apply { if (!exists()) mkdirs() }
    }

    /**
     * 把 [content] 写入缓存目录下的 [fileName]，返回可分享的 [File]。
     *
     * 同名文件直接覆盖：同一应用的通知反复导出时不会堆积副本。
     */
    suspend fun write(
        context: Context,
        fileName: String,
        content: String,
    ): File =
        withContext(Dispatchers.IO) {
            val file = File(dir(context), sanitizeFileName(fileName))
            file.writeText(content, Charsets.UTF_8)
            Logger.i(TAG, "已写入导出文件: ${file.absolutePath} (${file.length()} bytes)")
            file
        }

    /**
     * 构造分享该文件的 [Intent]（`ACTION_SEND` + 临时读权限）。
     *
     * @return 分享 Intent；构造失败（如 FileProvider 未正确声明）时返回 `null`。
     */
    fun buildShareIntent(
        context: Context,
        file: File,
    ): Intent? =
        try {
            val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "构造分享 Intent 失败", e)
            null
        }

    /** 当前缓存占用字节数。 */
    fun sizeBytes(context: Context): Long =
        try {
            dir(context).listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            Logger.w(TAG, "统计导出缓存大小失败: ${e.message}")
            0L
        }

    /** 缓存内文件数量。 */
    fun fileCount(context: Context): Int =
        try {
            dir(context).listFiles()?.count { it.isFile } ?: 0
        } catch (e: Exception) {
            Logger.w(TAG, "统计导出缓存文件数失败: ${e.message}")
            0
        }

    /**
     * 清空整个导出缓存目录。
     *
     * 只删本目录内的文件（[DIR_NAME] 专属目录，非共享缓存根），不触碰其它缓存。
     *
     * @return 是否全部删除成功。
     */
    suspend fun clearAll(context: Context): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val target = dir(context)
                val failed = target.listFiles()?.count { !it.delete() } ?: 0
                if (failed > 0) {
                    Logger.w(TAG, "清理导出缓存：$failed 个文件删除失败")
                    false
                } else {
                    Logger.i(TAG, "已清空导出缓存目录: ${target.absolutePath}")
                    true
                }
            } catch (e: Exception) {
                Logger.e(TAG, "清理导出缓存失败", e)
                false
            }
        }

    /** 把大小格式化为便于阅读的文本（B / KB / MB）。 */
    fun formatSize(bytes: Long): String =
        when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }

    /**
     * 去掉文件名中的路径分隔符与非法字符，避免 `../` 越出缓存目录或写入失败。
     */
    private fun sanitizeFileName(fileName: String): String {
        val cleaned =
            fileName
                .replace('/', '_')
                .replace('\\', '_')
                .replace(Regex("[\\x00-\\x1f]"), "")
        return cleaned.ifBlank { "notification_dump.json" }
    }
}
