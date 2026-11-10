package notifyrelay.desktop.overlay.scene

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sun.jna.Pointer
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test

/**
 * 用像素内容哈希判断动画是否推进。
 *
 * 不用「非零字节数」：元素平移时该计数基本不变，会把运动误判为静止。
 */
class AnimationProgressTest {
    private fun sample(
        label: String,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        val recorder =
            OverlaySceneRecorder(
                widthDp = 100,
                heightDp = 40,
                coroutineContext = Dispatchers.Default,
            ) { content() }

        val hashes = mutableListOf<Int>()
        repeat(12) {
            recorder.render(System.nanoTime()) { frame ->
                val buf = ByteArray(frame.byteCount)
                Pointer(frame.address).read(0, buf, 0, frame.byteCount)
                hashes.add(buf.contentHashCode())
            }
            Thread.sleep(40)
        }
        recorder.close()
        println(label + " => distinct_hash=" + hashes.distinct().size + "  " + hashes.take(6).joinToString(","))
    }

    @Test
    fun scrollAnimationAdvances() {
        sample("SCROLL") {
            val a = androidx.compose.runtime.remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                a.animateTo(60f, animationSpec = tween(800, easing = LinearEasing))
            }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.offset(x = a.value.dp).size(20.dp).background(Color.Red))
            }
        }
    }
}
