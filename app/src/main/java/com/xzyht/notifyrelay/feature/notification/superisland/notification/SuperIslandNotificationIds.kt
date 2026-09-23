package com.xzyht.notifyrelay.feature.notification.superisland.notification

/**
 * 超级岛相关通知 ID 的唯一推导来源。
 *
 * **为什么需要本文件**：原先 Live Updates 与复刻通道分别以
 * `hashCode().and(0xffff) + 10000` 和 `hashCode().and(0xffff) + 20000` 就地推导 ID，
 * 且公式散落在 5 个文件中各自硬编码。由于 `and(0xffff)` 的取值域是 `0..65535`，
 * 两个通道实际落在 `[10000, 75535]` 与 `[20000, 85535]`，**区间重叠 `[20000, 75535]`**——
 * 即注释所称的「刻意错开」并不成立，不同通道可能命中同一通知 ID 而互相覆盖通知栏条目。
 *
 * **修正方式**：保留完整 16 位哈希空间（`0xffff`，不做空间压缩以免提高碰撞率），
 * 将两个通道基址间距扩大到大于 65535，使取值区间真正互不重叠：
 * - Live Updates：`[100_000, 165_535]`
 * - 复刻通道：  `[200_000, 265_535]`
 * - 列表模式：  固定 `30_000`（单条聚合通知，天然落在上述两区间之外，保持不变）
 *
 * 注意：本变更会改变推导出的具体 ID 值，升级后旧版本遗留的通知无法再按新 ID 取消，
 * 会保留至其自身超时（30s）被清理。
 */
internal object SuperIslandNotificationIds {
    /** 哈希掩码：保留完整 16 位空间，两个通道基址间距（100_000）大于该空间上限 65535。 */
    private const val HASH_MASK = 0xFFFF

    private const val LIVE_UPDATES_BASE = 100_000
    private const val REPLICA_BASE = 200_000

    /** Live Updates 通道的通知 ID。 */
    fun liveUpdates(sourceId: String): Int = (sourceId.hashCode() and HASH_MASK) + LIVE_UPDATES_BASE

    /** 复刻通道的通知 ID。 */
    fun replica(key: String): Int = (key.hashCode() and HASH_MASK) + REPLICA_BASE
}
