package notifyrelay.base.log

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 把日志目录下的全部分片打包为单个归档文件（JDK 目标共用实现）。
 *
 * 用于「导出日志」：用户只需回传一个文件即可完整还原现场。
 * 目录无分片或写入失败时返回 false，由调用方按导出失败提示。
 *
 * @param sourceDirectory 日志目录路径。
 * @param targetArchive 目标归档文件路径。
 * @return 归档是否成功。
 */
actual fun zipLogShards(
    sourceDirectory: String,
    targetArchive: String,
): Boolean =
    runCatching {
        val dir = File(sourceDirectory)
        val shards = dir.listFiles { f -> f.isFile && f.name.startsWith("app.log") }?.toList().orEmpty()
        if (shards.isEmpty()) return false
        val target = File(targetArchive)
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            shards.forEach { file ->
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        true
    }.getOrDefault(false)
