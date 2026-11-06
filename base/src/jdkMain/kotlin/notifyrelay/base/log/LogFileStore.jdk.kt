package notifyrelay.base.log

import java.io.File
import java.util.logging.FileHandler
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * 日志文件通道（JDK 目标共用实现）。
 *
 * Android 与桌面都跑在 JVM 上，落盘与分片语义没有平台差异，因此只有这一份实现。
 *
 * 滚动交给 JDK 的 [FileHandler]：`limit` 是单分片字节上限，`count` 是保留分片数，
 * 分片命名形如 `app.log.0`、`app.log.1`，写满后覆盖最旧分片而非改名备份。
 * 保留天数清理只在安装时执行一次，避免启动期额外 IO。
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
                // 达到 count 后覆盖最旧分片。
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

    /** 当前日志分片（排除 FileHandler 的锁文件）。 */
    private fun shardFiles(dir: File): List<File> =
        dir
            .listFiles { f -> f.isFile && f.name.startsWith(FILE_PATTERN) && !f.name.endsWith(".lck") }
            ?.toList()
            .orEmpty()
}
