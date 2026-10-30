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
 * 全部活跃通知交给 [listener]，不做启动跳过。本采集器只按通知 id 拦截本进程内的重复回调，
 * 内容级重复（含重启后重新枚举到的历史通知）由上层已入库检定承担。
 */
internal object DesktopNotificationCollector {
    private const val TAG = "DesktopNotif"

    /** 轮询间隔：与 Windows 端一致（10 秒）。 */
    private const val POLL_INTERVAL_MS = 10_000L

    /** 字段分隔符，与 Rust 侧编码一致。 */
    private const val FIELD_SEP = '\u0001'

    /** 记录分隔符，与 Rust 侧编码一致。 */
    private const val RECORD_SEP = '\u0002'

    /** 本进程内已回调的通知 id，避免同一通知在后续轮询中重复回调。 */
    private val seenIds = ConcurrentHashMap.newKeySet<String>()

    /** 已回调 id 的上限：超过后整体清空（重新回调同样由上层已入库检定拦截）。 */
    private const val SEEN_IDS_LIMIT = 4096

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
        /** 应用图标原始字节；采集不到时为 null。 */
        val iconPng: ByteArray? = null,
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
     * 读取一次并回调未见过的通知。
     *
     * 只按通知 id 拦截本进程内的重复回调；内容级重复由上层已入库检定判定。
     */
    private fun dispatchNew(onNew: (Item) -> Unit) {
        val items = readAll() ?: return
        if (seenIds.size > SEEN_IDS_LIMIT) seenIds.clear()
        items.forEach { item ->
            if (!seenIds.add(item.id)) return@forEach
            runCatching { onNew(item) }
                .onFailure { Logger.w(TAG, "处理通知失败", it) }
        }
    }

    /** 解码 base64 图标字节；失败返回 null。 */
    private fun decodeBase64(text: String): ByteArray? =
        runCatching {
            java.util.Base64
                .getDecoder()
                .decode(text)
        }.getOrNull()

    /** 停止采集。 */
    fun stop() {
        if (!running) return
        running = false
        pollThread?.interrupt()
        pollThread = null
        seenIds.clear()
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
                // 第 6 字段为应用图标 base64（Rust 侧 DisplayInfo.GetLogo），缺失时为空串
                iconPng = f.getOrNull(5)?.takeIf { it.isNotBlank() }?.let { decodeBase64(it) },
            )
        }
    }
}
