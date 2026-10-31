package notifyrelay.desktop.overlay.feature

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import notifyrelay.base.log.Logger
import notifyrelay.desktop.platform.DesktopOverlaySettings
import notifyrelay.overlay.api.BalanceHistoryItem
import notifyrelay.overlay.api.BalanceSnapshot
import notifyrelay.overlay.api.BalanceSource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/**
 * DeepSeek 余额数据源。
 *
 * 轮询经 JDK 的 [HttpClient] 访问 `api.deepseek.com/user/balance`；
 * 历史经 [DesktopBalanceHistoryStore] 落盘，与 Windows `DeepSeekBalanceService` 同口径：
 * 余额未变时只刷新末条时间，变化时追加并计算变化量，超出上限时合并最旧的同向连续项。
 */
class DesktopBalanceSource(
    private val tokenProvider: () -> String = { DesktopOverlaySettings.balanceApiToken },
    private val intervalProvider: () -> Long = { DesktopOverlaySettings.balanceIntervalMs },
    private val persist: Boolean = true,
) : BalanceSource {
    private companion object {
        const val TAG = "DesktopBalance"

        /** 余额接口地址。 */
        const val ENDPOINT = "https://api.deepseek.com/user/balance"

        /** 历史条数上限；超出后合并最旧的同向连续项。 */
        const val MAX_HISTORY = 500

        /** 余额变化判定阈值：差值小于该值视为未变。 */
        const val CHANGE_EPSILON = 0.0001
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val http =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    private val listeners = CopyOnWriteArrayList<(BalanceSnapshot) -> Unit>()

    private val history = CopyOnWriteArrayList<BalanceHistoryItem>()

    private var pollJob: Job? = null

    @Volatile
    private var currentBalance: Double? = null

    @Volatile
    private var polling = false

    init {
        loadHistory()
    }

    override val snapshot: BalanceSnapshot
        get() = BalanceSnapshot(balance = currentBalance, change = history.lastOrNull()?.change ?: 0.0, isPolling = polling)

    override fun history(): List<BalanceHistoryItem> = history.toList()

    override val isConfigured: Boolean get() = tokenProvider().isNotBlank()

    /** 启动轮询；未配置凭据时保持停止。 */
    override fun start() {
        if (polling) return
        if (!isConfigured) {
            Logger.d(TAG, "未配置凭据，不启动余额轮询")
            return
        }
        polling = true
        pollJob =
            scope.launch {
                while (isActive) {
                    runCatching { fetchOnce() }
                        .onFailure { Logger.w(TAG, "余额查询失败", it) }
                    delay(intervalProvider().coerceAtLeast(10_000L))
                }
            }
        notifyListeners()
    }

    /** 停止轮询。 */
    override fun stop() {
        pollJob?.cancel()
        pollJob = null
        polling = false
        notifyListeners()
    }

    /**
     * 立即查询一次余额。
     *
     * 成功时写入历史并广播；凭据缺失或请求失败返回 null。
     */
    override suspend fun fetchOnce(): Double? {
        val token = tokenProvider()
        if (token.isBlank()) return null

        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            Logger.w(TAG, "余额接口返回状态 " + response.statusCode())
            return null
        }

        val balance = parseBalance(response.body()) ?: return null
        currentBalance = balance
        appendHistory(balance)
        notifyListeners()
        return balance
    }

    override fun clearHistory() {
        history.clear()
        if (persist) DesktopBalanceHistoryStore.save(emptyList())
        currentBalance = null
        notifyListeners()
    }

    override fun observe(listener: (BalanceSnapshot) -> Unit): AutoCloseable {
        listeners.add(listener)
        listener(snapshot)
        return AutoCloseable { listeners.remove(listener) }
    }

    /**
     * 解析余额响应。
     *
     * 优先取 `CNY` 币种的总余额；无该币种时取首条；`is_available=false` 时余额记 0。
     */
    private fun parseBalance(body: String): Double? {
        val obj = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null

        if (obj.stringOrNull("is_available") == "false") return 0.0

        val infos = obj["balance_infos"] as? JsonArray ?: return null
        if (infos.isEmpty()) return null

        val cny = infos.firstOrNull { (it as? JsonObject)?.stringOrNull("currency") == "CNY" } ?: infos.first()
        return (cny as? JsonObject)?.stringOrNull("total_balance")?.toDoubleOrNull()
    }

    /** 写入历史：余额未变时只刷新末条时间，变化时追加并计算变化量。 */
    private fun appendHistory(balance: Double) {
        val last = history.lastOrNull()
        if (last != null && abs(last.balance - balance) < CHANGE_EPSILON) {
            history[history.size - 1] = last.copy(time = System.currentTimeMillis())
        } else {
            val change = if (last == null) 0.0 else balance - last.balance
            history.add(
                BalanceHistoryItem(
                    time = System.currentTimeMillis(),
                    balance = balance,
                    change = change,
                    changeType = changeTypeOf(last != null, change),
                ),
            )
        }
        trimHistory()
        if (persist) DesktopBalanceHistoryStore.save(history.toList())
    }

    /** 变化类型：首条为 init，其余按符号。 */
    private fun changeTypeOf(
        hasPrevious: Boolean,
        change: Double,
    ): String =
        when {
            !hasPrevious -> "init"
            change > 0 -> "+"
            change < 0 -> "-"
            else -> "="
        }

    /**
     * 裁剪到上限。
     *
     * 先合并最旧的同向连续项（合并后条数减少，仍超限则丢弃最旧一条），
     * 直至不超上限；合并点的余额取该组最后一条。
     */
    private fun trimHistory() {
        while (history.size > MAX_HISTORY) {
            val firstType = history[0].changeType
            val runLength = history.takeWhile { it.changeType == firstType }.size
            if (runLength >= 2) {
                val group = history.take(runLength)
                val merged =
                    group.last().copy(
                        change = group.sumOf { it.change },
                        mergeCount = group.sumOf { it.mergeCount },
                    )
                repeat(runLength) { history.removeAt(0) }
                history.add(0, merged)
                // 合并后仍超限时丢弃最旧一条，避免同向只有一项时死循环
                if (history.size > MAX_HISTORY && runLength == 1) history.removeAt(0)
            } else {
                history.removeAt(0)
            }
        }
    }

    private fun notifyListeners() {
        val current = snapshot
        listeners.forEach { listener ->
            runCatching { listener(current) }
                .onFailure { Logger.w(TAG, "余额快照回调失败", it) }
        }
    }

    /** 从持久化恢复历史；恢复后立即广播，使叠加层不必等待首次轮询。 */
    private fun loadHistory() {
        if (!persist) return
        val restored = DesktopBalanceHistoryStore.load()
        if (restored.isEmpty()) return
        history.addAll(restored)
        currentBalance = restored.lastOrNull()?.balance
        notifyListeners()
    }

    /** 读取 JSON 对象的字符串字段；缺失或非字符串时返回空串。 */
    private fun JsonObject.stringOrNull(field: String): String? = (this[field] as? JsonPrimitive)?.content
}
