package notifyrelay.base.log

import java.io.File
import java.util.logging.FileHandler
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * JVM 侧日志文件通道，与 Android 侧共用同一套分片语义。
 *
 * 分片与滚动由 JDK 的 [FileHandler] 提供：`limit` 控制单分片大小，
 * `count` 控制保留分片数量，命名形如 `app.log.0`、`app.log.1`。
 */
actual object LogFileStore {
    private const val FILE_PATTERN = "app.log"

    private var logger: Logger? = null
    private var handler: FileHandler? = null
    private var root: File? = null
    private var retention: Int = DEFAULT_RETENTION_DAYS
    private var shardLimit: Long = DEFAULT_MAX_FILE_BYTES

    @Synchronized
    actual fun install(
        directory: String,
        maxFileBytes: Long,
        shardCount: Int,
        retentionDays: Int,
    ) {
        if (logger != null) return
        retention = retentionDays
        shardLimit = maxFileBytes
        val dir = File(directory).apply { mkdirs() }
        root = dir

        val h =
            try {
                FileHandler(dir.resolve(FILE_PATTERN).absolutePath, maxFileBytes.toInt(), shardCount, true)
            } catch (_: Throwable) {
                return
            }
        h.formatter =
            object : Formatter() {
                override fun format(record: LogRecord): String = record.message + "\n"
            }
        h.level = Level.ALL

        val lg = Logger.getLogger("notifyrelay.file")
        lg.useParentHandlers = false
        lg.level = Level.ALL
        lg.addHandler(h)

        handler = h
        logger = lg
        purgeExpired()
    }

    actual fun write(line: String) {
        logger?.log(Level.INFO, line)
    }

    actual fun storageInfo(): LogStorageInfo? {
        val dir = root ?: return null
        val files = shardFiles(dir)
        return LogStorageInfo(
            directory = dir.absolutePath,
            fileCount = files.size,
            totalBytes = files.sumOf { it.length() },
            retentionDays = retention,
            maxFileBytes = shardLimit,
        )
    }

    actual fun purgeExpired(): Int {
        val dir = root ?: return 0
        val deadline = System.currentTimeMillis() - retention.toLong() * 24 * 60 * 60 * 1000
        var removed = 0
        shardFiles(dir).forEach { file ->
            if (file.lastModified() < deadline && file.delete()) removed++
        }
        return removed
    }

    private fun shardFiles(dir: File): List<File> =
        dir
            .listFiles { f -> f.isFile && f.name.startsWith(FILE_PATTERN) && !f.name.endsWith(".lck") }
            ?.toList()
            .orEmpty()

    internal fun emit(formatted: String) = write(formatted)
}
