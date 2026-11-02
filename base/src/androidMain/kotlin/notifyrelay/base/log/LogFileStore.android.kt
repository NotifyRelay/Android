package notifyrelay.base.log

import java.io.File
import java.util.logging.FileHandler
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Android 侧日志文件通道。
 *
 * 落盘与分片完全交给 JDK 的 [FileHandler]：
 * - `limit` 为单个分片字节上限，写出后自动切到下一个分片；
 * - `count` 为保留的分片数量，写满后自动覆盖最旧分片；
 * - 分片命名为 `app.log.0`、`app.log.1`…，即按大小分片而非改名备份。
 *
 * 保留天数清理在安装时执行一次，避免启动期产生额外 IO。
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
                // FileHandler 从 app.log.0 开始编号，写满 limit 后切到 app.log.1，
                // 达到 count 后覆盖最旧分片，即按大小分片而非改名备份。
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

    /** 当前日志分片（含锁文件排除）。 */
    private fun shardFiles(dir: File): List<File> =
        dir
            .listFiles { f -> f.isFile && f.name.startsWith(FILE_PATTERN) && !f.name.endsWith(".lck") }
            ?.toList()
            .orEmpty()

    /** 供日志通道输出。 */
    internal fun emit(formatted: String) = write(formatted)
}
