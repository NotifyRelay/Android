package notifyrelay.desktop.overlay.scene

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证点击经指针事件注入后能触发 Compose 的 clickable。
 *
 * 这是接收端点击能否真正落到既有动作上的前提：
 * 若注入不生效，Gamebar 点了也不会有任何反应。
 */
@OptIn(InternalComposeUiApi::class)
class PointerDispatchTest {
    @Test
    fun clickableReceivesInjectedPointer() {
        var clicks = 0
        val scene =
            ImageComposeScene(
                width = 200,
                height = 100,
                density = Density(1f),
                coroutineContext = kotlinx.coroutines.Dispatchers.Unconfined,
            ) {
                Box(
                    Modifier
                        .size(200.dp, 100.dp)
                        .background(Color(0xFF202020))
                        .clickable { clicks++ },
                )
            }

        // 先渲染一帧，确保布局与点击区域已建立
        scene.render(System.nanoTime()).close()

        scene.sendPointerEvent(
            eventType = PointerEventType.Press,
            position = Offset(100f, 50f),
            timeMillis = System.currentTimeMillis(),
            type = PointerType.Mouse,
            buttons = PointerButtons(isPrimaryPressed = true),
        )
        scene.sendPointerEvent(
            eventType = PointerEventType.Release,
            position = Offset(100f, 50f),
            timeMillis = System.currentTimeMillis() + 50,
            type = PointerType.Mouse,
            buttons = PointerButtons(),
        )
        scene.render(System.nanoTime()).close()

        println("CLICKS=" + clicks)
        scene.close()
        assertEquals(1, clicks, "注入的按下+抬起应触发一次 clickable")
    }
}
