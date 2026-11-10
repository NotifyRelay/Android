package notifyrelay.desktop.overlay.scene

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.xzynine.superislandui.common.AutoScrollText
import notifyrelay.desktop.overlay.OverlaySpectrum
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 动态元素的动画改写。
 *
 * 频谱与滚动文本必须被改写成 SMIL，由接收端自行推进；
 * 若哨兵色失效或导出标记未提供，改写会静默不发生，动画退化回逐帧重推。
 */
class SceneAnimationRewriteTest {
    private fun record(): String {
        val export = SceneExport()
        val recorder =
            OverlaySceneRecorder(
                widthDp = 200,
                heightDp = 100,
                coroutineContext = kotlinx.coroutines.Dispatchers.Default,
            ) {
                ProvideSceneExport(export) {
                    Box {
                        Row {
                            OverlaySpectrum(positionMs = 0L, playing = true, widthDp = 28f, heightDp = 80f)
                            Box(Modifier.width(120.dp)) {
                                AutoScrollText(
                                    text = "超长的歌曲标题需要滚动显示",
                                    style = TextStyle(fontSize = 12.sp),
                                    color = export.scrollColor,
                                    baseSpeedPxPerSec = 100f,
                                    pauseMillis = 0,
                                    endPauseMillis = 0,
                                )
                            }
                        }
                    }
                }
            }
        val recorded = recorder.record(System.nanoTime())
        recorder.close()
        return recorded.document
    }

    @Test
    fun dynamicElementsBecomeAnimations() {
        val document = record()

        // 频谱每根条两个动画，滚动文本一个位移动画
        val animations = Regex("<animate ").findAll(document).count()
        assertTrue(animations >= 10, "频谱条应改写成位移动画，实际 $animations 个")

        assertTrue(document.contains("animateTransform"), "滚动文本应改写成位移动画")

        // 哨兵色只用于识别，不允许残留在交给接收端的文档里
        assertFalse(document.contains("#F1023"), "文档中残留了频谱哨兵色")
        assertFalse(document.contains("#F10240"), "文档中残留了滚动文本哨兵色")
    }

    @Test
    fun scrollKeepsLayoutAttributes() {
        val document = record()

        // 字号与逐字定位必须保留，否则文本无法正常排版
        val scrollBlock = Regex("<g><animateTransform[\\s\\S]{0,600}?</g>").find(document)?.value
        assertTrue(scrollBlock != null, "未找到滚动文本的动画块")
        assertTrue(scrollBlock!!.contains("font-size"), "滚动文本丢失了字号")
        assertTrue(scrollBlock.contains("font-family"), "滚动文本丢失了字体")
    }
}
