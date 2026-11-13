package notifyrelay.desktop.overlay

import androidx.compose.runtime.mutableStateMapOf

/**
 * 媒体卡展开截止时刻。
 *
 * 展开态与收起态由「当前时刻是否早于截止时刻」决定，而不是由组合内的延时协程决定：
 * 叠加层内容随帧时钟重组，录制场景也可能因画布变化重建，延时协程一旦随之重启，
 * 卡片就再也收不起来。截止时刻存在组合之外，重建只是重读同一份事实。
 *
 * 按卡片（设备）持有，换曲键单独记录：同一张卡换歌才重置截止时刻，
 * 同曲内的歌词滚动与重组都不影响。
 */
internal object MediaCardExpansion {
    /** 一张卡的展开状态：换曲键 + 截止时刻。 */
    private data class State(
        val trackKey: String,
        val untilMs: Long,
    )

    private val states = mutableStateMapOf<String, State>()

    /**
     * 为卡片开启一轮展开。
     *
     * 同一首歌只计时一次：换曲键未变时直接返回，不因重组或组合整体重建而重新计时。
     * 否则收起后被重建的宿主再次拉起，卡片会回到展开态，表现为永不收起。
     * 需要重新展开的路径只有换曲与本对象的 [extend]。
     *
     * @param cardKey 卡片键（设备 uuid）
     * @param trackKey 换曲键（歌曲身份）
     * @param durationMs 本轮的展开时长
     */
    fun begin(
        cardKey: String,
        trackKey: String,
        nowMs: Long,
        durationMs: Long,
    ) {
        if (cardKey.isEmpty()) return
        val current = states[cardKey]
        if (current != null && current.trackKey == trackKey) return
        states[cardKey] = State(trackKey, nowMs + durationMs)
    }

    /** 用户点开：从当前时刻重新计时。 */
    fun extend(
        cardKey: String,
        trackKey: String,
        nowMs: Long,
        durationMs: Long,
    ) {
        if (cardKey.isEmpty()) return
        states[cardKey] = State(trackKey, nowMs + durationMs)
    }

    /**
     * [nowMs] 时刻是否处于展开态。
     *
     * 无记录或尚未登记本曲时按展开处理：卡片刚出现应为展开态，
     * 登记发生在首次组合之后，按收起处理会闪一帧。
     */
    fun isExpanded(
        cardKey: String,
        trackKey: String,
        nowMs: Long,
    ): Boolean {
        val current = states[cardKey] ?: return true
        if (current.trackKey != trackKey) return true
        return current.untilMs > nowMs
    }

    /** 卡片移除时丢弃其状态。 */
    fun forget(cardKey: String) {
        states.remove(cardKey)
    }
}
