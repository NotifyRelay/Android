package notifyrelay.desktop.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.OutputWStream
import org.jetbrains.skia.Rect
import org.jetbrains.skia.svg.SVGCanvas
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 验证同一个持久 ComposeScene 可以连续录制多帧。
 *
 * 场景若每帧重建，叠加层里的展开/收起与计时状态会被反复重置，
 * 界面将无法表达任何过渡；因此这里验证状态可以跨帧保留。
 */
@OptIn(InternalComposeUiApi::class)
class SvgRecordingTest {
    private var counter by mutableIntStateOf(0)

    private fun capture(
        scene: ComposeScene,
        w: Int,
        h: Int,
    ): String {
        val out = ByteArrayOutputStream()
        OutputWStream(out).use { stream ->
            val canvas = SVGCanvas.make(Rect.makeWH(w.toFloat(), h.toFloat()), stream)
            try {
                scene.measureAndLayout()
                scene.draw(canvas.asComposeCanvas())
            } finally {
                // SVGCanvas 持有原生资源，必须在输出流关闭前释放
                canvas.close()
            }
        }
        return out.toString("UTF-8")
    }

    @Test
    fun persistentSceneRendersMultipleFrames() {
        val imageScene =
            ImageComposeScene(
                width = 120,
                height = 40,
                density = Density(1f),
                coroutineContext = kotlinx.coroutines.Dispatchers.Unconfined,
            ) {
                Box(Modifier.size((20 + counter * 10).dp, 40.dp).background(Color(0xFF00FF00)))
            }
        val field = ImageComposeScene::class.java.getDeclaredField("scene").apply { isAccessible = true }
        val inner = field.get(imageScene) as ComposeScene

        val first = capture(inner, 120, 40)
        counter = 3
        // 让重组跑一次再抓第二帧
        imageScene.render(System.nanoTime()).close()
        val second = capture(inner, 120, 40)
        imageScene.close()

        println("FRAME1_LEN=" + first.length)
        println("FRAME2_LEN=" + second.length)
        println("FRAME1=" + first.replace("\n", " ").replace("\t", " "))
        println("FRAME2=" + second.replace("\n", " ").replace("\t", " "))
        assertTrue(second.contains("50"), "第二帧应反映状态变化（宽度 20+3*10=50），实际：" + second)
    }
}
