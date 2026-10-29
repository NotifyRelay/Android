package notifyrelay.desktop.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import github.xzynine.superislandui.floating.bigisland.components.MediaIslandCompose
import github.xzynine.superislandui.model.components.MediaSessionData
import notifyrelay.base.log.Logger
import notifyrelay.domain.feature.media.MediaSessionSnapshot
import org.jetbrains.skia.EncodedImageFormat

/**
 * 叠加层位图的离屏渲染。
 *
 * 媒体卡等界面在本机用 Compose 绘制后编码为 PNG，再交给叠加层按坐标合成；
 * 叠加层只负责窗口与合成，不再维护一套自己的界面。
 */
internal object DesktopOverlayRenderer {
    private const val TAG = "DesktopOverlayRender"

    /** 媒体卡画布宽度（像素）。 */
    const val MEDIA_WIDTH = 420

    /** 媒体卡画布高度（像素）。 */
    const val MEDIA_HEIGHT = 96

    /**
     * 渲染媒体卡。
     *
     * @return PNG 字节；渲染失败时返回 null
     */
    fun renderMediaCard(
        snapshot: MediaSessionSnapshot,
        deviceName: String,
    ): ByteArray? =
        render(MEDIA_WIDTH, MEDIA_HEIGHT) {
            MediaIslandCompose(
                mediaSession =
                    MediaSessionData(
                        packageName = snapshot.packageName.orEmpty(),
                        appName = deviceName,
                        title = snapshot.title.orEmpty(),
                        text = snapshot.artist.orEmpty(),
                        coverUrl = snapshot.coverUrl,
                        deviceName = deviceName,
                    ),
                isExpanded = true,
            )
        }

    /**
     * 把一段 Compose 内容离屏渲染成 PNG。
     *
     * @param width 画布宽（像素）
     * @param height 画布高（像素）
     */
    fun render(
        width: Int,
        height: Int,
        content: @Composable () -> Unit,
    ): ByteArray? {
        if (width <= 0 || height <= 0) return null
        return try {
            val scene = ImageComposeScene(width, height, Density(1f)) { content() }
            try {
                val image = scene.render()
                image.encodeToData(EncodedImageFormat.PNG)?.bytes
            } finally {
                scene.close()
            }
        } catch (e: Throwable) {
            Logger.w(TAG, "离屏渲染失败", e)
            null
        }
    }
}
