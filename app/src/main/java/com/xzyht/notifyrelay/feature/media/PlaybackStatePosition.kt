package com.xzyht.notifyrelay.feature.media

import android.media.session.PlaybackState
import android.os.SystemClock
import notifyrelay.base.util.PlaybackPosition

/**
 * 把安卓媒体会话的播放态接到公共的位置推算上。
 *
 * PlaybackState 只提供快照位置、快照时刻（[PlaybackState.lastPositionUpdateTime]，单调时钟域）
 * 与播放速率；这里换算出与本地当前时刻同域的采样时刻，再交给 [PlaybackPosition.predict]。
 */
internal object PlaybackStatePosition {
    /**
     * 当前播放位置（毫秒）；无法判定时返回 -1。
     *
     * @param state 媒体会话播放态
     * @param nowEpochMs 当前时刻（epoch 毫秒）
     */
    fun estimate(
        state: PlaybackState?,
        nowEpochMs: Long,
    ): Long {
        if (state == null) return -1L
        val playing = state.state == PlaybackState.STATE_PLAYING
        // lastPositionUpdateTime 是 elapsedRealtime 域：换算成同一读数下的 epoch 时刻
        val reportedAtEpoch =
            nowEpochMs - (SystemClock.elapsedRealtime() - state.lastPositionUpdateTime).coerceAtLeast(0L)
        return PlaybackPosition.predict(
            positionMs = state.position,
            reportedAtMs = reportedAtEpoch,
            playing = playing,
            speed = state.playbackSpeed,
            nowMs = nowEpochMs,
        )
    }
}
