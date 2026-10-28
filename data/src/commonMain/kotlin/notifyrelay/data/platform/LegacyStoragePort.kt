package notifyrelay.data.platform

/**
 * 旧存储迁移所需的平台能力。
 *
 * 旧版本把通知记录、应用清单与超级岛历史写在应用私有目录的 JSON 文件里，
 * 迁移到 Room 时需要「列出/读取/删除这些文件」「枚举已安装应用」以及
 * 「向外部可见目录写文件」。这些能力在 Android 与 JVM 上的实现完全不同
 * （filesDir / getExternalFilesDir vs 指定目录、PackageManager vs 无此概念），
 * 故抽象为端口，由各平台在启动时注入。
 */
interface LegacyStoragePort {
    /**
     * 读取指定设备的旧通知记录文件内容。
     *
     * @param device 设备标识（"本机" 表示本地设备）
     * @return 文件文本内容；文件不存在时返回 null
     */
    fun readNotificationRecords(device: String): String?

    /** 列出全部旧通知记录文件名。 */
    fun listNotificationRecordFiles(): List<String>

    /** 读取指定名称的旧文件内容；不存在返回 null。 */
    fun readFile(name: String): String?

    /** 删除指定名称的旧文件；返回是否成功。 */
    fun deleteFile(name: String): Boolean

    /**
     * 枚举本机已安装应用，用于旧应用清单迁移。
     *
     * @return 包名、显示名、是否系统应用、PNG 图标字节（可为 null）
     */
    fun installedApps(): List<LegacyInstalledApp>

    /**
     * 写入对外可见目录下的文件（供外部工具读取）。
     *
     * Android 对应 `getExternalFilesDir`；JVM 对应调用方指定的目录。
     *
     * @return 是否写入成功
     */
    fun writeExternalFile(
        name: String,
        content: String,
    ): Boolean
}

/** 旧应用清单迁移所需的单个应用信息。 */
data class LegacyInstalledApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val iconPngBytes: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LegacyInstalledApp) return false
        return packageName == other.packageName
    }

    override fun hashCode(): Int = packageName.hashCode()
}

/** [LegacyStoragePort] 的注册点：由平台在启动时注入实现。 */
object LegacyStoragePortRegistry {
    @Volatile
    var current: LegacyStoragePort? = null
        private set

    fun install(port: LegacyStoragePort) {
        current = port
    }
}
