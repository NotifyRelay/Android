package notifyrelay.desktop.overlay.scene

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlin.test.Test

/**
 * 复现叠加层的真实组合形态：LaunchedEffect 计时 + 由录制方写入的帧脉冲。
 */
class OverlayTimingTest {
    @Test
    fun realShapeTiming() {
        val frameTick = mutableLongStateOf(0L)
        var ticks = 0

        val recorder =
            OverlaySceneRecorder(
                widthDp = 64,
                heightDp = 64,
                coroutineContext = Dispatchers.Default,
            ) {
                Box(Modifier.size(64.dp)) {
                    // 与 OverlayContent 一致：读帧脉冲
                    frameTick.longValue
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(200)
                            ticks++
                        }
                    }
                }
            }

        val deadline = System.currentTimeMillis() + 3_000
        var frames = 0
        while (System.currentTimeMillis() < deadline && ticks < 5) {
            frameTick.longValue = frameTick.longValue + 1
            recorder.render(System.nanoTime()) {}
            frames++
            Thread.sleep(16)
        }
        recorder.close()
        println("REAL frames=" + frames + " ticks=" + ticks)
    }

    @Test
    fun timingWithoutFrameTickRead() {
        var ticks = 0
        val recorder =
            OverlaySceneRecorder(
                widthDp = 64,
                heightDp = 64,
                coroutineContext = Dispatchers.Default,
            ) {
                Box(Modifier.size(64.dp)) {
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(200)
                            ticks++
                        }
                    }
                }
            }

        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline && ticks < 5) {
            recorder.render(System.nanoTime()) {}
            Thread.sleep(16)
        }
        recorder.close()
        println("NOTICK ticks=" + ticks)
    }
}
