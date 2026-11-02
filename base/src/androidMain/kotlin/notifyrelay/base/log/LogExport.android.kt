package notifyrelay.base.log

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

actual fun zipLogShards(
    sourceDirectory: String,
    targetArchive: String,
): Boolean =
    runCatching {
        val dir = File(sourceDirectory)
        val shards =
            dir.listFiles { f -> f.isFile && f.name.startsWith("app.log") }?.toList().orEmpty()
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
