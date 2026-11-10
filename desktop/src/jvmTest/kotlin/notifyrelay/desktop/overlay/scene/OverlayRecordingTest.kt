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
import com.sun.jna.Pointer
import top.yukonga.miuix.kmp.basic.Text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证叠加层实际使用的绘制原语能录成位图。
 *
 * 覆盖卡片底色、圆角裁切与文本：这些是接收端必须能消费的内容。
 */
class OverlayRecordingTest {
    @Test
    fun recordsOverlayPrimitives() {
        val width = 420
        val height = 160
        val recorder =
            OverlaySceneRecorder(
                widthDp = width,
                heightDp = height,
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

        var frameWidth = 0
        var frameHeight = 0
        var stride = 0
        var nonZero = 0
        recorder.render(System.nanoTime()) { frame ->
            frameWidth = frame.width
            frameHeight = frame.height
            stride = frame.stride
            val sample = ByteArray(frame.byteCount)
            Pointer(frame.address).read(0, sample, 0, frame.byteCount)
            nonZero = sample.count { it.toInt() != 0 }
        }
        recorder.close()

        println("FRAME=" + frameWidth + "x" + frameHeight + " stride=" + stride + " nonZero=" + nonZero)

        assertEquals(width, frameWidth, "帧宽应与录制尺寸一致")
        assertEquals(height, frameHeight, "帧高应与录制尺寸一致")
        // BGRA 每像素四字节，行距不得小于像素宽度
        assertTrue(stride >= width * 4, "行距应至少容纳一行像素")
        assertTrue(nonZero > 0, "画面应有内容，不能是空白帧")
    }
}
