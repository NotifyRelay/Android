package notifyrelay.base.log

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
 * 日志文件写入与保留策略的统一入口。
 *
 * 落盘细节由各目标源集实现：Android 与 JVM 均基于 JDK 的
 * java.util.logging.FileHandler，它自带按大小滚动（limit/count）
 * 与分片编号，无需自行实现滚动逻辑。
 */
expect object LogFileStore {
    /**
     * 安装日志文件通道。重复调用只生效一次。
     *
     * @param directory 日志根目录
     * @param maxFileBytes 单个分片字节上限，超出即切下一个分片
     * @param shardCount 保留的分片数量上限
     * @param retentionDays 分片保留天数，超期分片会被清理
     */
    fun install(
        directory: String,
        maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
        shardCount: Int = DEFAULT_SHARD_COUNT,
        retentionDays: Int = DEFAULT_RETENTION_DAYS,
    )

    /** 写入一行日志（由日志通道调用）。 */
    fun write(line: String)

    /** 当前日志存储信息；未安装时返回 null。 */
    fun storageInfo(): LogStorageInfo?

    /** 清理超期分片，返回删除的文件数。 */
    fun purgeExpired(): Int
}

/** 单分片默认上限：2 MiB。 */
const val DEFAULT_MAX_FILE_BYTES: Long = 2L * 1024 * 1024

/** 默认分片数量上限。 */
const val DEFAULT_SHARD_COUNT: Int = 8

/** 默认保留天数。 */
const val DEFAULT_RETENTION_DAYS: Int = 15
