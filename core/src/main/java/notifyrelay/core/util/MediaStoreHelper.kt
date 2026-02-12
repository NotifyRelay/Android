package notifyrelay.core.util

import android.content.Context
import android.media.MediaScannerConnection
import android.webkit.MimeTypeMap
import notifyrelay.base.util.Logger
import java.io.File

object MediaStoreHelper {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    private val VIDEO_EXTENSIONS = setOf("mp4", "avi", "mov", "wmv", "flv", "mkv")
    private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "ogg", "flac", "aac", "m4a")

    /**
     * 在现代 Android 上推荐使用 MediaScannerConnection.scanFile() 来让新文件可见。
     * 该方法会通知媒体扫描器扫描指定路径，避免直接写入已废弃的 DATA 列或发送受保护广播。
     */
    fun indexFile(context: Context, file: File) {
        try {
            val mime = getMimeType(file)
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime)) { _, _ ->
                // 扫描完成回调，可按需记录日志
            }
        } catch (e: Exception) {
            Logger.e("MediaStoreHelper", "Failed to index file: ${file.absolutePath}", e)
        }
    }

    private fun getMimeType(file: File): String? {
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    private fun File.isImage(): Boolean = extension.lowercase() in IMAGE_EXTENSIONS
    private fun File.isVideo(): Boolean = extension.lowercase() in VIDEO_EXTENSIONS
    private fun File.isAudio(): Boolean = extension.lowercase() in AUDIO_EXTENSIONS
}