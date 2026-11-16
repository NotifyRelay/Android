package com.xzyht.notifyrelay.feature.notification.superisland.replica

import com.xzyht.notifyrelay.feature.notification.superisland.store.SuperIslandSourceStore
import notifyrelay.base.log.Logger
import notifyrelay.domain.feature.notification.superisland.replica.ReplicaTiming
import java.util.concurrent.ConcurrentHashMap

/**
 * 超级岛复刻通道的两组**时效屏蔽**登记表。
 *
 * - `closedSources`：来源被关闭后的屏蔽窗口，长度见 [ReplicaTiming.CLOSED_SOURCE_TTL_MS]。
 *   窗口必须覆盖来源刷新周期，否则关闭后的第一次刷新就把条目重新拉起；
 * - `blockedInstanceIds`：会话级屏蔽，窗口内被重复访问则续期，用于用户主动隐藏后的短期静默。
 *
 * 这两组状态在 [SuperIslandSourceStore.clearAllMappings] 中**刻意不被清理**：
 * 通道切换不应解除用户刚刚表达过的关闭意图，两者仍按各自 TTL 自然过期。
 */
internal object ReplicaTtlRegistry {
    private const val TAG = "超级岛映射管理"

    /**
     * @param atMs 关闭时刻
     * @param contentKey 关闭时该来源正在展示的内容标识；null 表示当时没有展示记录
     */
    private data class ClosedMark(
        val atMs: Long,
        val contentKey: String?,
    )

    private val closedSources = ConcurrentHashMap<String, ClosedMark>()

    private val blockedInstanceIds = ConcurrentHashMap<String, Long>()

    /**
     * 一次展示内容的标识。
     *
     * 标题、正文与载荷共同决定一次展示的身份：来源被刷新周期反复驱动时内容不变，
     * 来源真正换了内容（换曲、换通知文案）时标识随之改变。
     */
    fun contentKeyOf(
        title: String?,
        text: String?,
        paramV2Raw: String?,
    ): String = title.orEmpty() + "\u0001" + text.orEmpty() + "\u0001" + paramV2Raw.orEmpty()

    fun isInstanceBlocked(instanceId: String?): Boolean {
        if (instanceId.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        val ts = blockedInstanceIds[instanceId] ?: return false
        if (now - ts > ReplicaTiming.BLOCKED_INSTANCE_TTL_MS) {
            blockedInstanceIds.remove(instanceId)
            Logger.i(TAG, "超级岛: 屏蔽过期，自动移除 instanceId=$instanceId")
            return false
        }
        blockedInstanceIds[instanceId] = now
        return true
    }

    fun blockInstance(instanceId: String?) {
        if (instanceId.isNullOrBlank()) return
        blockedInstanceIds[instanceId] = System.currentTimeMillis()
        Logger.i(TAG, "超级岛: 会话级屏蔽 instanceId=$instanceId")
    }

    fun removeBlockedInstance(instanceId: String) {
        blockedInstanceIds.remove(instanceId)
    }

    /**
     * 该来源的这一份内容当前是否仍处于关闭后的屏蔽窗口内。
     *
     * 判据是内容身份而非单纯时间：来源在窗口内重新投递**同一份**内容时继续挡住
     * （来源通知消失后仍有在途的展示请求），换了内容则说明来源重新开始，屏蔽随即解除。
     *
     * @param contentKey 本次要展示的内容标识；null 表示未知，按时间窗处理
     */
    fun isSourceRecentlyClosed(
        sourceId: String,
        contentKey: String? = null,
    ): Boolean {
        val mark = closedSources[sourceId] ?: return false
        if (System.currentTimeMillis() - mark.atMs >= ReplicaTiming.CLOSED_SOURCE_TTL_MS) {
            closedSources.remove(sourceId)
            return false
        }
        if (mark.contentKey != null && contentKey != null && contentKey != mark.contentKey) {
            closedSources.remove(sourceId)
            return false
        }
        return true
    }

    /**
     * 登记关闭。
     *
     * @param contentKey 关闭时该来源正在展示的内容标识
     */
    fun markSourceClosed(
        sourceId: String,
        contentKey: String? = null,
    ) {
        closedSources[sourceId] = ClosedMark(System.currentTimeMillis(), contentKey)
    }

    /**
     * 清除关闭标记。
     *
     * 只有**不受来源刷新驱动**的显式展示路径会调用（如通道切换重建）：
     * 这些路径表达的是「现在就展示」，与关闭意图同源，不能互相否决。
     */
    fun removeClosedSource(sourceId: String) {
        closedSources.remove(sourceId)
    }
}
