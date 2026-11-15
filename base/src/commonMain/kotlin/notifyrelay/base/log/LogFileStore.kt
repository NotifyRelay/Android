package notifyrelay.base.log

import java.io.File
import java.util.logging.FileHandler
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * 日志文件存储的公开信息，供关于页展示。
 *
 * @property directory 日志目录的绝对路径
 * @property fileCount 当前日志分片数量
 * @property totalBytes 全部分片占用的字节数
 * @property retentionDays 保留天数
 * @property maxFileBytes 单个分片的字节上限
 */
data class LogStorageInfo(
    val directory: String,
    val fileCount: Int,
    val totalBytes: Long,
    val retentionDays: Int,
    val maxFileBytes: Long,
)

/**
 * 日志文件写入与保留策略。
 *
 * 模块只有 Android 与 JVM 两个目标，两者都跑在 JVM 上，落盘与分片没有平台差异，
 * 因此放在 commonMain 而不拆 expect/actual。
 *
 * 滚动交给 JDK 的 [FileHandler]：`limit` 是单分片字节上限，`count` 是保留分片数，
 * 分片命名形如 `app.log.0`、`app.log.1`，写满后覆盖最旧分片而非改名备份。
 * 保留天数清理只在安装时执行一次，避免启动期额外 IO。
 */
object LogFileStore {
    private const val FILE_PATTERN = "app.log"

    private var logger: Logger? = null
    private var handler: FileHandler? = null
    private var root: File? = null
    private var retention: Int = DEFAULT_RETENTION_DAYS
    private var shardLimit: Long = DEFAULT_MAX_FILE_BYTES

    /**
     * 安装日志文件通道。重复调用只生效一次。
     *
     * @param directory 日志根目录
     * @param maxFileBytes 单个分片字节上限，超出即切下一个分片
     * @param shardCount 保留的分片数量上限
     * @param retentionDays 分片保留天数，超期分片会被清理
     */
    @Synchronized
    fun install(
        directory: String,
        maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
        shardCount: Int = DEFAULT_SHARD_COUNT,
        retentionDays: Int = DEFAULT_RETENTION_DAYS,
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

    /** 写入一行日志（由日志通道调用）。 */
    fun write(line: String) {
        logger?.log(Level.INFO, line)
    }

    /** 当前日志存储信息；未安装时返回 null。 */
    fun storageInfo(): LogStorageInfo? {
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

    /** 清理超期分片，返回删除的文件数。 */
    fun purgeExpired(): Int {
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

/** 单分片默认上限：2 MiB。 */
const val DEFAULT_MAX_FILE_BYTES: Long = 2L * 1024 * 1024

/** 默认分片数量上限。 */
const val DEFAULT_SHARD_COUNT: Int = 8

/** 默认保留天数。 */
const val DEFAULT_RETENTION_DAYS: Int = 15
