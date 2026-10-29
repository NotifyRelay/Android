package notifyrelay.desktop.platform

import notifyrelay.base.log.Logger
import wtf.magma.audio.SMTCLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * 桌面端本机通知采集器（WinRT `UserNotificationListener`，经 smtc-rust 桥）。
 *
 * 实测非打包进程下 `RequestAccessAsync` 亦可返回 `Allowed`（需用户在
 * 系统设置 → 通知中允许），故不依赖稀疏 MSIX 包标识。
 *
 * 采集为轮询式，**与 Android `onListenerConnected` 同策略**：首轮即把当前
 * 全部活跃通知交给 [listener]，不做启动跳过；重复由上层「已处理缓存 + 落库去重」承担
 * （对应 Android 的 `NotificationProcessedCache` 与 `addNotification` 返回 false 不转发）。
 */
internal object DesktopNotificationCollector {
    private const val TAG = "DesktopNotif"

    /** 轮询间隔：与 Windows 端一致（10 秒）。 */
    private const val POLL_INTERVAL_MS = 10_000L

    /** 已处理通知的 TTL（毫秒）：与 Android `NotificationProcessedCache` 同量级。 */
    private const val PROCESSED_TTL_MS = 60_000L

    /** 字段分隔符，与 Rust 侧编码一致。 */
    private const val FIELD_SEP = '\u0001'

    /** 记录分隔符，与 Rust 侧编码一致。 */
    private const val RECORD_SEP = '\u0002'

    /** 已处理通知 id → 处理时刻，用于 TTL 去重（对应 Android `NotificationProcessedCache`）。 */
    private val processed = ConcurrentHashMap<String, Long>()

    @Volatile
    private var pollThread: Thread? = null

    @Volatile
    private var running = false

    /** 一条本机通知。 */
    data class Item(
        val id: String,
        val appName: String,
        val appPackage: String,
        val title: String,
        val text: String,
    )

    /** 采集是否可用（原生库加载成功）。 */
    val available: Boolean get() = SMTCLoader.available

    /**
     * 启动轮询采集。
     *
     * 首轮即回调整体活跃通知，与 Android 启动时同步一致。
     *
     * @param onNew 通知的回调（在采集线程调用）；已处理且未过 TTL 的不会重复回调
     */
    fun start(onNew: (Item) -> Unit) {
        if (!available) {
            Logger.w(TAG, "通知采集库不可用")
            return
        }
        if (running) return
        running = true
        pollThread =
            Thread({
                Logger.i(TAG, "通知采集已启动")
                while (running) {
                    dispatchNew(onNew)
                    try {
                        Thread.sleep(POLL_INTERVAL_MS)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
                Logger.i(TAG, "通知采集已停止")
            }, "notif-collector").apply {
                isDaemon = true
                start()
            }
    }

    /**
     * 读取一次并回调未处理的通知。
     *
     * 与 Android 一致：过滤已处理项后立刻标记，避免同一通知在一轮内重复。
     */
    private fun dispatchNew(onNew: (Item) -> Unit) {
        val items = readAll() ?: return
        val now = System.currentTimeMillis()
        // 清理过期条目，避免映射无限增长
        processed.entries.removeIf { now - it.value > PROCESSED_TTL_MS }
        items.forEach { item ->
            val last = processed[item.id]
            if (last != null && now - last <= PROCESSED_TTL_MS) return@forEach
            processed[item.id] = now
            runCatching { onNew(item) }
                .onFailure { Logger.w(TAG, "处理通知失败", it) }
        }
    }

    /** 停止采集。 */
    fun stop() {
        if (!running) return
        running = false
        pollThread?.interrupt()
        pollThread = null
        processed.clear()
    }

    /**
     * 读取当前全部通知。
     *
     * @return 通知列表；读取失败时返回 null
     */
    fun readAll(): List<Item>? {
        if (!available) return null
        val raw = runCatching { SMTCLoader.listNotifications() }.getOrNull() ?: return null
        if (raw.startsWith("!")) {
            Logger.w(TAG, "读取通知失败: " + raw.removePrefix("!"))
            return null
        }
        if (raw.isEmpty()) return emptyList()
        return raw.split(RECORD_SEP).mapNotNull { record ->
            if (record.isEmpty()) return@mapNotNull null
            val f = record.split(FIELD_SEP)
            if (f.size < 5) return@mapNotNull null
            Item(
                id = f[0],
                appName = f[1],
                appPackage = f[2],
                title = f[3],
                text = f[4],
            )
        }
    }
}
