package notifyrelay.desktop.overlay.scene

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertTrue

/** 验证位图被抽出为独立资源，文档只留引用。 */
class ImageExtractTest {
    @Test
    fun imagesAreExtracted() {
        val s = Surface.makeRasterN32Premul(96, 96)
        s.canvas.clear(0xFF3366FF.toInt())
        val png = s.makeImageSnapshot().encodeToData()!!.bytes
        s.close()
        val bmp =
            org.jetbrains.skia.Image
                .makeFromEncoded(png)
                .toComposeImageBitmap()

        val recorder =
            OverlaySceneRecorder(
                widthDp = 200,
                heightDp = 60,
                coroutineContext = kotlinx.coroutines.Dispatchers.Default,
            ) {
                Box {
                    Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(24.dp))
                }
            }
        val recorded = recorder.record(System.nanoTime())
        recorder.close()

        println("DOC_LEN=" + recorded.document.length)
        println("IMAGE_COUNT=" + recorded.images.size)
        println("IMAGE_IDS=" + recorded.images.keys.joinToString(","))
        println("HAS_BASE64_IN_DOC=" + recorded.document.contains("base64"))
        recorded.images.forEach { (id, bytes) -> println("IMG " + id + " = " + bytes.size + " bytes") }
        Regex("xlink:href=\"([^\"]+)\"").findAll(recorded.document).forEach { println("HREF: " + it.groupValues[1]) }

        assertTrue(recorded.document.length < 2000, "抽出后文档应很小")
        assertTrue(recorded.images.isNotEmpty(), "应抽出位图")
    }
}
