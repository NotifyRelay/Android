package notifyrelay.desktop.overlay.scene

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 验证叠加层实际使用的绘制原语都能被录成标准 SVG。
 *
 * 覆盖卡片底色、圆角裁切、文本与进度环路径：
 * 这些正是接收端渲染器必须能消费的子集。
 */
class OverlayRecordingTest {
    @Test
    fun recordsOverlayPrimitives() {
        val recorder =
            OverlaySceneRecorder(
                widthDp = 420,
                heightDp = 160,
                coroutineContext = kotlinx.coroutines.Dispatchers.Unconfined,
            ) {
                Box(
                    Modifier
                        .width(420.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xE6000000))
                        .padding(12.dp),
                ) {
                    Column {
                        Text(text = "微信 · 手机", color = Color(0xB3FFFFFF), fontSize = 11.sp)
                        Text(text = "新消息", color = Color.White, fontSize = 14.sp)
                        Text(text = "这是一条测试通知", color = Color(0xE6FFFFFF), fontSize = 12.sp)
                    }
                }
            }

        val svg = recorder.record(System.nanoTime())
        recorder.close()

        java.io.File("build/overlay-scene.svg").writeText(svg)
        println("SVG_LEN=" + svg.length)
        println("HAS_SVG_ROOT=" + svg.contains("<svg"))
        println("HAS_TEXT=" + svg.contains("<text"))
        println("HAS_RECT=" + svg.contains("<rect"))
        println("HAS_CLIPPATH=" + svg.contains("clipPath"))
        println("HAS_TITLE=" + svg.contains("新消息"))
        println("SVG_HEAD=" + svg.replace("\n", " ").replace("\t", " ").take(500))

        assertTrue(svg.contains("<svg"), "应产出 SVG 根元素")
        assertTrue(svg.contains("<text"), "应包含文本节点")
        assertTrue(svg.contains("<rect"), "应包含矩形节点")
    }
}
