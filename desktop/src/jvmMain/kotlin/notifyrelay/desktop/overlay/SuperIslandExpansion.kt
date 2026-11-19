package notifyrelay.desktop.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import notifyrelay.domain.feature.notification.superisland.replica.ReplicaTiming

/**
 * 单条超级岛的展开状态。
 *
 * 展开与收起由 [OverlayClock] 按墙钟推进，与通知卡片同一套计时；
 * 组合树只读是展开，不自行 `delay`。
 */
internal class SuperIslandExpansionState {
    /** 最近一次展开时刻；null 表示处于收起态。 */
    private var expandedAtMs: Long? = null

    /** 最近一次展开所对应的内容标识；内容变化时重新展开。 */
    private var expandedFor: String? = null

    /** 折叠态可观察标记，供组合树在变化时重组。 */
    private val revision = mutableStateMapOf<String, Long>()

    /**
     * 当前是否处于展开态。
     *
     * 内容变化或首次访问时自动展开；摘要类业务永不展开。
     *
     * @param summaryOnly 摘要类业务（如手电筒）不展开
     * @param contentKey 当前内容标识；变化即视为新内容并重新展开
     */
    fun isExpanded(
        summaryOnly: Boolean,
        contentKey: String,
    ): Boolean {
        if (summaryOnly) return false
        // 读 revision 建立快照观察：收起时 tick 改写它，组合树据此重组
        revision["v"]
        if (expandedFor != contentKey) {
            // 新内容：重新展开并重置计时。这里只推进一次，重复读取不会反复重置
            expandedFor = contentKey
            expandedAtMs = OverlayClock.now()
            return true
        }
        return expandedAtMs != null
    }

    /** 用户点击：重新展开并重置计时。 */
    fun expand() {
        expandedAtMs = OverlayClock.now()
        revision["v"] = System.nanoTime()
    }

    /** 由 [OverlayClock] 每周期调用：到点收起并通知组合树。 */
    fun tick(now: Long) {
        val at = expandedAtMs ?: return
        if (now - at < ReplicaTiming.EXPANDED_DURATION_MS) return
        expandedAtMs = null
        revision["v"] = now
    }
}

/**
 * 各超级岛展开状态的进程级持有者。
 *
 * 与通知卡片同理：状态不能挂在组合树，录制器重建会把它清掉。
 */
internal object SuperIslandExpansionHolder {
    private val states = mutableMapOf<String, SuperIslandExpansionState>()

    init {
        OverlayClock.register { now ->
            val snapshot = synchronized(this) { states.values.toList() }
            snapshot.forEach { it.tick(now) }
        }
    }

    /** 取得指定来源的展开状态，不存在时创建。 */
    @Synchronized
    fun stateFor(sourceKey: String): SuperIslandExpansionState = states.getOrPut(sourceKey) { SuperIslandExpansionState() }

    /** 条目消失时释放状态，避免按来源无限累积。 */
    @Synchronized
    fun release(sourceKey: String) {
        states.remove(sourceKey)
    }
}
