package notifyrelay.base.util

/**
 * 播放位置推算。
 *
 * 平台给的位置多是「快照 + 上报时刻」：播放器只在状态变更时上报，播放中该值不随时间前进。
 * 直接把它当「此刻位置」会上报旧值，接收端每轮重算基准都退回旧位置。
 * 本文件把两类来源统一到同一条推算规则：
 * - 平台能给出快照时刻与速率时用 [predict]（安卓 PlaybackState 即如此）；
 * - 只能轮询读数、没有速率时用 [PlaybackPositionTracker]，在读数变化处建基准、按经过时间推进
 *   （桌面 SMTC 只能如此）。
 */
object PlaybackPosition {
    /**
     * 由快照推算 [nowMs] 时刻的播放位置。
     *
     * @param positionMs 快照位置（毫秒）
     * @param reportedAtMs 快照时刻（与 [nowMs] 同一时钟）
     * @param playing 快照时刻是否在播放
     * @param speed 播放速率；非正按 1 处理
     * @param nowMs 推算时刻
     * @return 推算位置；快照位置未知（负数）时返回 -1
     */
    fun predict(
        positionMs: Long,
        reportedAtMs: Long,
        playing: Boolean,
        speed: Float,
        nowMs: Long,
    ): Long {
        if (positionMs < 0L) return -1L
        val base = positionMs.coerceAtLeast(0L)
        if (!playing) return base
        val rate = speed.takeIf { it > 0f } ?: 1f
        val elapsed = (nowMs - reportedAtMs).coerceAtLeast(0L)
        return (base + (elapsed * rate).toLong()).coerceAtLeast(0L)
    }
}

/**
 * 轮询来源的位置推算。
 *
 * 读数变化（跳转、暂停定位、换曲）时以该次读数为新基准；读数未变化时按经过时间线性推进。
 * 跨进程拿不到播放速率，按常速推进。同一实例只服务一个会话。
 */
class PlaybackPositionTracker {
    private var anchorPositionMs = -1L
    private var anchorAtMs = 0L
    private var lastReportedPositionMs = -1L

    /**
     * 推算 [nowMs] 时刻的播放位置。
     *
     * @param reportedPositionMs 本轮读到的位置（毫秒）；未知为负数
     * @param playing 本轮读到的播放态
     * @param nowMs 本轮读数时刻（毫秒）
     * @return 推算位置；读数未知时返回 -1
     */
    fun estimate(
        reportedPositionMs: Long,
        playing: Boolean,
        nowMs: Long,
    ): Long {
        if (reportedPositionMs < 0L) {
            reset()
            return -1L
        }

        // 读数变化或本轮未在播放：以本次读数为准重建基准
        if (reportedPositionMs != lastReportedPositionMs || !playing || anchorPositionMs < 0L) {
            anchorPositionMs = reportedPositionMs
            anchorAtMs = nowMs
            lastReportedPositionMs = reportedPositionMs
        }

        if (!playing) return anchorPositionMs
        return (anchorPositionMs + (nowMs - anchorAtMs)).coerceAtLeast(0L)
    }

    /** 会话结束或不可读时清空基准。 */
    fun reset() {
        anchorPositionMs = -1L
        anchorAtMs = 0L
        lastReportedPositionMs = -1L
    }
}
