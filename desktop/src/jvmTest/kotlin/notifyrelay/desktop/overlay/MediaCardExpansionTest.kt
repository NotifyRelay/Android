package notifyrelay.desktop.overlay

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 媒体卡展开截止：同曲只计一次，换曲与用户点开才重新计时。 */
class MediaCardExpansionTest {
    private val card = "device-1"

    @Test
    fun startsExpandedBeforeRegistration() {
        // 卡片刚出现时登记尚未发生，应显示为展开，不能先闪一帧收起态
        assertTrue(MediaCardExpansion.isExpanded(card, "song-a", 1_000L))
        MediaCardExpansion.forget(card)
    }

    @Test
    fun collapsesAfterDeadline() {
        MediaCardExpansion.forget(card)
        MediaCardExpansion.begin(card, "song-a", 1_000L, 3_000L)
        assertTrue(MediaCardExpansion.isExpanded(card, "song-a", 3_999L))
        assertFalse(MediaCardExpansion.isExpanded(card, "song-a", 4_001L))
        MediaCardExpansion.forget(card)
    }

    @Test
    fun repeatedBeginDoesNotExtendDeadline() {
        // 宿主每帧重组都会调用 begin：重复调用不得把收起时刻往后推
        MediaCardExpansion.forget(card)
        MediaCardExpansion.begin(card, "song-a", 1_000L, 3_000L)
        repeat(5) { MediaCardExpansion.begin(card, "song-a", 3_500L, 3_000L) }
        assertFalse(MediaCardExpansion.isExpanded(card, "song-a", 4_001L), "重复登记不应续期")
        MediaCardExpansion.forget(card)
    }

    @Test
    fun collapsedCardStaysCollapsedAfterRecreation() {
        // 组合整体重建后会在已收起时再次 begin：同曲不得重新展开
        MediaCardExpansion.forget(card)
        MediaCardExpansion.begin(card, "song-a", 1_000L, 3_000L)
        MediaCardExpansion.begin(card, "song-a", 9_000L, 3_000L)
        assertFalse(MediaCardExpansion.isExpanded(card, "song-a", 9_001L), "重建不应把卡片拉回展开")
        MediaCardExpansion.forget(card)
    }

    @Test
    fun newTrackExpandsAgain() {
        MediaCardExpansion.forget(card)
        MediaCardExpansion.begin(card, "song-a", 1_000L, 3_000L)
        MediaCardExpansion.begin(card, "song-b", 9_000L, 3_000L)
        assertTrue(MediaCardExpansion.isExpanded(card, "song-b", 9_001L))
        MediaCardExpansion.forget(card)
    }

    @Test
    fun userClickExtends() {
        MediaCardExpansion.forget(card)
        MediaCardExpansion.begin(card, "song-a", 1_000L, 3_000L)
        MediaCardExpansion.extend(card, "song-a", 4_000L, 3_000L)
        assertTrue(MediaCardExpansion.isExpanded(card, "song-a", 6_999L), "点开应从点开时刻重新计时")
        MediaCardExpansion.forget(card)
    }

    @Test
    fun forgetDropsState() {
        MediaCardExpansion.begin(card, "song-a", 1_000L, 3_000L)
        MediaCardExpansion.forget(card)
        assertTrue(MediaCardExpansion.isExpanded(card, "song-a", 9_000L), "移除后按新卡片处理")
    }
}
