package notifyrelay.domain.feature.media.lyric

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 单曲识别：歌词滚动不算换曲，换曲才重置状态并给出新身份。
 */
class SongRecognizerTest {
    private fun recognize(
        recognizer: SongRecognizer,
        title: String,
        artist: String = "歌手",
        durationMs: Long = 180_000L,
        mediaId: String = "",
        positionMs: Long = 1_000L,
        nowMs: Long = 1_000L,
    ): SongRecognition = recognizer.observe("com.music", title, artist, durationMs, mediaId, positionMs, nowMs)

    @Test
    fun firstObservationIsNewSong() {
        val r = recognize(SongRecognizer(), "歌名")
        assertTrue(r.firstObservation)
        assertFalse(r.songSwitched, "首次观测不算切换")
        assertEquals("180000", r.songKey, "时长已知时以时长作身份")
    }

    @Test
    fun lyricScrollingKeepsSongKey() {
        val recognizer = SongRecognizer()
        val first = recognize(recognizer, "第一句歌词")
        val second = recognize(recognizer, "第二句歌词")
        assertEquals(first.songKey, second.songKey)
        assertFalse(second.songSwitched, "标题变化不代表换歌")
    }

    @Test
    fun durationChangeIsSongSwitch() {
        val recognizer = SongRecognizer()
        val first = recognize(recognizer, "歌词")
        val second = recognize(recognizer, "歌词", durationMs = 200_000L)
        assertTrue(second.songSwitched)
        assertEquals("200000", second.songKey)
        assertEquals(LyricState.PENDING_LYRIC, second.state, "换歌后歌词状态重置")
        assertEquals(200_000L, second.durationMs, "结果带出本次观测的时长")
    }

    @Test
    fun mediaIdOutranksDuration() {
        val r = recognize(SongRecognizer(), "歌名", mediaId = "queue-7")
        assertEquals("queue-7", r.songKey)
    }

    @Test
    fun repeatedFieldChangeConfirmsLyricState() {
        val recognizer = SongRecognizer()
        recognize(recognizer, "第一句", nowMs = 1_000L)
        recognize(recognizer, "第二句", nowMs = 1_100L)
        val third = recognize(recognizer, "第三句", nowMs = 1_200L)
        assertEquals(LyricState.LYRIC, third.state)
        assertEquals(LyricField.TITLE, third.lyricField)
        assertEquals("第三句", third.lyricText)
        assertEquals("第三句", third.displayTitle)
    }

    @Test
    fun lyricInArtistFieldIsSwappedToTitle() {
        val recognizer = SongRecognizer()
        recognize(recognizer, "歌名", artist = "第一句", nowMs = 1_000L)
        recognize(recognizer, "歌名", artist = "第二句", nowMs = 1_100L)
        val third = recognize(recognizer, "歌名", artist = "第三句", nowMs = 1_200L)
        assertEquals(LyricState.LYRIC, third.state)
        assertEquals(LyricField.ARTIST, third.lyricField)
        assertEquals("第三句", third.displayTitle, "歌词换到主位")
        assertEquals("歌名", third.displayArtist)
        assertEquals("第三句", third.lyricText)
    }

    @Test
    fun positionAdvanceWithoutLyricIsInstrumental() {
        val recognizer = SongRecognizer()
        recognize(recognizer, "歌名", positionMs = 1_000L, nowMs = 1_000L)
        // 位置推进超过自适应阈值（新源保守 6s）且无歌词滚动
        val result = recognize(recognizer, "歌名", positionMs = 8_000L, nowMs = 2_000L)
        assertEquals(LyricState.INSTRUMENTAL, result.state)
        assertNull(result.lyricText)
    }

    @Test
    fun forgetDropsLearnedState() {
        val recognizer = SongRecognizer()
        recognize(recognizer, "歌名")
        recognizer.forget("com.music")
        assertNull(recognizer.songKeyOf("com.music"))
        assertNull(recognizer.stateOf("com.music"))
    }
}
