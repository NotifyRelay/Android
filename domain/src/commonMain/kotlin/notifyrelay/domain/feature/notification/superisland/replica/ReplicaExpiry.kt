package notifyrelay.domain.feature.notification.superisland.replica

/**
 * 超级岛条目的存活判定（两端唯一实现）。
 *
 * 展示侧的「该消失了」只有一条口径：从最近一次**内容变化**起算，超过该业务类型的时限即过期。
 * 判据只看内容变化时刻而非报文到达时刻——来源会周期性重发同一份内容，
 * 若把重发也算作更新，条目永远不会过期。
 *
 * 时限取值见 [ReplicaTiming.viewTtlMs]。
 */
object ReplicaExpiry {
    /**
     * 下一条目是否已过展示时限。
     *
     * @param updatedAtMs 最近一次内容变化的时刻（毫秒）
     * @param isMedia 是否媒体类业务
     * @param nowMs 当前时刻（毫秒）
     */
    fun isExpired(
        updatedAtMs: Long,
        isMedia: Boolean,
        nowMs: Long,
    ): Boolean = nowMs - updatedAtMs > ReplicaTiming.viewTtlMs(isMedia)

    /**
     * 距离该条目过期还剩多少毫秒；已过期时为 0。
     *
     * 供单循环驱动方计算下次唤醒时刻，避免固定周期轮询带来的相位误差。
     */
    fun remainingMs(
        updatedAtMs: Long,
        isMedia: Boolean,
        nowMs: Long,
    ): Long = (updatedAtMs + ReplicaTiming.viewTtlMs(isMedia) - nowMs).coerceAtLeast(0L)
}
