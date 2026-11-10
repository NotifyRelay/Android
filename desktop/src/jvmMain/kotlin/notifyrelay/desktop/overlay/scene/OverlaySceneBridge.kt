package notifyrelay.desktop.overlay.scene

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import notifyrelay.base.log.Logger
import notifyrelay.desktop.overlay.DesktopOverlayStore
import notifyrelay.desktop.overlay.OverlayContent
import notifyrelay.desktop.platform.DesktopOverlaySettings
import java.awt.Rectangle

/**
 * 把叠加层内容录制为矢量场景并经命名管道推给 Gamebar。
 *
 * 与叠加层窗口共用同一份组合树（[OverlayContent]），因此两处呈现不会漂移。
 * 场景只在有客户端订阅时录制；点击经 [OverlaySceneRecorder.sendPointer]
 * 送回 Compose 自己的输入分发，新增控件无需在此登记动作。
 */
internal class OverlaySceneBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val server = NamedPipeSceneServer()

    /** 录制与指针注入共用一把锁：Compose 场景不是线程安全的。 */
    private val sceneLock = Mutex()

    private var recorder: OverlaySceneRecorder? = null

    /** 已推送的场景版本，单调递增。 */
    private var revision = 0L

    /** 已推送内容的指纹，用于跳过无变化的帧。 */
    private var lastFingerprint: String? = null

    /** 最近一次录制尺寸；订阅尺寸变化时重建录制场景。 */
    private var recordedWidth = 0
    private var recordedHeight = 0

    /** 叠加层当前的可交互区域，键与场景里的区域 key 一致。 */
    private val regions = SnapshotStateMap<String, Rectangle>()

    /** 启动服务端与录制循环。 */
    fun start() {
        server.clickListener = ::onClick
        server.start()
        scope.launch { publishLoop() }
        Logger.i(TAG, "矢量场景服务已启动 管道=" + SCENE_PIPE_NAME)
    }

    /** 停止服务端并释放录制场景。 */
    fun close() {
        scope.cancel()
        server.close()
        recorder?.close()
        recorder = null
        Logger.i(TAG, "矢量场景服务已停止")
    }

    /**
     * 处理一次点击。
     *
     * 只把按下/抬起送回 Compose 的输入分发：动作由既有 clickable 执行，
     * 因此这里不解释任何业务语义。抬起后立即安排一次重录，
     * 让点击引起的状态变化尽快反映到下一次场景。
     */
    private fun onClick(
        key: String,
        phase: String,
        x: Float?,
        y: Float?,
    ) {
        // 无坐标时退回区域中心：保证旧客户端仍能触发动作
        val px = x ?: centerOf(key)?.first ?: return
        val py = y ?: centerOf(key)?.second ?: return

        scope.launch {
            sceneLock.withLock {
                val target = recorder ?: return@withLock
                runCatching {
                    target.sendPointer(
                        pressed = phase == PHASE_DOWN,
                        xDp = px,
                        yDp = py,
                        timestampMs = System.currentTimeMillis(),
                    )
                }.onFailure { Logger.w(TAG, "注入指针事件失败 key=" + key, it) }
            }
            // 抬起后内容可能已变（例如卡片展开），立刻重录一次
            if (phase == PHASE_UP) {
                lastFingerprint = null
            }
        }
    }

    /** 区域中心坐标；用于客户端未上报坐标时的退化处理。 */
    private fun centerOf(key: String): Pair<Float, Float>? {
        val rect = regions[key] ?: return null
        return (rect.x + rect.width / 2f) to (rect.y + rect.height / 2f)
    }

    /**
     * 录制循环。
     *
     * 无客户端订阅时不录制；订阅尺寸变化时重建录制场景，
     * 因为 Compose 场景的尺寸在构造时固定，改了就必须重建。
     * 内容无变化且无非静止态时按空闲周期轮询，避免静止场景持续占用 CPU。
     */
    private suspend fun publishLoop() {
        while (true) {
            delay(if (hasAnimatedContent()) ACTIVE_FRAME_MS else IDLE_FRAME_MS)
            val current = server.viewport()
            if (!server.isSubscribed() || current == null) {
                releaseRecorder()
                continue
            }
            sceneLock.withLock {
                runCatching { publishFrame(current) }
                    .onFailure { Logger.w(TAG, "录制并推送场景失败", it) }
            }
        }
    }

    private fun releaseRecorder() {
        if (recorder == null) return
        recorder?.close()
        recorder = null
        recordedWidth = 0
        recordedHeight = 0
        lastFingerprint = null
    }

    private suspend fun publishFrame(current: ClientViewport) {
        val widthPx = current.widthDp.toInt().coerceAtLeast(1)
        val heightPx = current.heightDp.toInt().coerceAtLeast(1)

        if (recorder == null || recordedWidth != widthPx || recordedHeight != heightPx) {
            recorder?.close()
            recorder =
                OverlaySceneRecorder(
                    widthDp = widthPx,
                    heightDp = heightPx,
                    coroutineContext = scope.coroutineContext,
                    content = overlayContent(widthPx, heightPx),
                )
            recordedWidth = widthPx
            recordedHeight = heightPx
            lastFingerprint = null
        }

        val recorded = recorder!!.record(System.nanoTime())
        val document = recorded.document

        // 内容无变化时不推：静止场景只有内容变化才会重录
        val regionSnapshot = regions.toMap()
        val fingerprint = fingerprintOf(document, regionSnapshot)
        if (fingerprint == lastFingerprint) return
        lastFingerprint = fingerprint

        revision += 1

        // 先发图片再发场景：接收端解析场景时已有对应位图可查。
        // 每帧完整发送：不依赖接收端缓存命中，避免任何一侧状态不一致导致的缺图。
        recorded.images.forEach { (id, bytes) ->
            server.publishImage(id, bytes)
        }

        server.publish(
            SceneSnapshot(
                revision = revision,
                widthDp = widthPx.toFloat(),
                heightDp = heightPx.toFloat(),
                document = document,
                regions =
                    regionSnapshot.entries
                        .sortedBy { it.key }
                        .map { (key, rect) ->
                            SceneRegion(
                                key = key,
                                x = rect.x.toFloat(),
                                y = rect.y.toFloat(),
                                width = rect.width.toFloat(),
                                height = rect.height.toFloat(),
                            )
                        },
            ),
        )
    }

    /** 叠加层内容；与窗口共用同一份组合树。 */
    private fun overlayContent(
        widthPx: Int,
        heightPx: Int,
    ): @Composable () -> Unit =
        {
            Box {
                OverlayContent(
                    interactiveEnabled = DesktopOverlaySettings.interactiveEnabled,
                    spectrumEnabled = DesktopOverlaySettings.spectrumEnabled,
                    interactiveRegions = regions,
                    contentSize = IntSize(widthPx, heightPx),
                    onContentSizeChanged = {},
                    modifier = Modifier,
                )
            }
        }

    /**
     * 判断当前是否存在需要连续刷新的内容。
     *
     * 时钟按秒变化、滚动文本与频谱按帧推进、进度环随播放进度前进，
     * 这些都只在叠加层有内容时出现。因此只要存在可见内容就按动画周期录制：
     * 用「是否有内容」判定比逐项枚举可靠，新增动态元素也不会漏掉。
     */
    private fun hasAnimatedContent(): Boolean =
        DesktopOverlayStore.clockText.value.isNotEmpty() ||
            DesktopOverlayStore.mediaSessions.isNotEmpty() ||
            DesktopOverlayStore.superIslands.isNotEmpty() ||
            DesktopOverlayStore.notifications.isNotEmpty() ||
            DesktopOverlayStore.keyboardHint.value.isNotEmpty() ||
            (DesktopOverlaySettings.spectrumEnabled && DesktopOverlayStore.hasPlayingMedia())

    private fun fingerprintOf(
        document: String,
        regionSnapshot: Map<String, Rectangle>,
    ): String {
        val regionPart =
            regionSnapshot.entries.sortedBy { it.key }.joinToString("|") { (k, r) ->
                k + "@" + r.x + "," + r.y + "," + r.width + "," + r.height
            }
        return document.hashCode().toString() + "#" + regionPart
    }

    private companion object {
        private const val TAG = "OverlaySceneBridge"

        /** 存在动画内容时的录制周期。 */
        private const val ACTIVE_FRAME_MS = 16L

        /** 空闲时的录制周期：仅用于发现状态变化。 */
        private const val IDLE_FRAME_MS = 120L

        private const val PHASE_UP = "up"
        private const val PHASE_DOWN = "down"
    }
}
