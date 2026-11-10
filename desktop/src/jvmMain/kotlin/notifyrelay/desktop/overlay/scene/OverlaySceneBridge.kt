package notifyrelay.desktop.overlay.scene

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import com.sun.jna.Pointer
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

    /** 上一帧的像素摘要；用于跳过无变化的帧。 */
    private var lastPixelHash: Long = 0L

    /** 摘要计算用的复用缓冲；按帧尺寸增长，不随帧数累积。 */
    private var hashBuffer = ByteArray(0)

    /** 最近一次录制尺寸；订阅尺寸变化时重建录制场景。 */
    private var recordedWidth = 0
    private var recordedHeight = 0

    /** 内容实际占用的高度（dp）；由组合树测量后写入。 */
    private var contentHeightDp = 0

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
                lastPixelHash = 0L
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
            val animated = hasAnimatedContent()
            delay(if (animated) ACTIVE_FRAME_MS else IDLE_FRAME_MS)
            val current = server.viewport()
            if (!server.isSubscribed() || current == null) {
                releaseRecorder()
                continue
            }
            sceneLock.withLock {
                runCatching { publishFrame(current, animated) }
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
    }

    /**
     * 渲染并推送一帧。
     *
     * 动画态逐帧推送：动画由内容本身驱动，比较像素只会浪费一次读回。
     * 空闲态先比较内容：画面静止时不再重复占用 CPU 与管道带宽。
     */
    private suspend fun publishFrame(
        current: ClientViewport,
        animated: Boolean,
    ) {
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
            lastPixelHash = 0L
        }

        val regionSnapshot = regions.toMap()
        val payload =
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
                }

        // 像素地址只在回调内有效，写出必须在回调里完成
        recorder!!.render(System.nanoTime()) { frame ->
            val byteCount = frame.byteCount
            if (byteCount <= 0) return@render

            if (!animated) {
                if (hashBuffer.size < byteCount) {
                    hashBuffer = ByteArray(byteCount)
                }
                val digest = hashPixels(frame)
                if (digest == lastPixelHash) return@render
                lastPixelHash = digest
            }

            revision += 1
            // 用实际帧尺寸而不是请求尺寸：两者不一致时接收端会算错行距，画面错位
            // 内容高度只用于接收端收缩窗口，不参与像素尺寸
            val visibleHeight = contentHeightDp.coerceAtMost(frame.height).coerceAtLeast(1)
            if (!server.sendFrame(revision, frame.width, frame.height, visibleHeight, payload, frame)) {
                Logger.w(TAG, "推送帧失败 revision=" + revision)
            }
        }
    }

    /**
     * 像素内容摘要。
     *
     * 静止场景不重复推送：不比较内容就只能按固定周期盲推，
     * 画面完全静止时仍在白耗 CPU 与管道带宽。
     *
     * 必须整块参与：采样会漏掉时钟这类小范围变化。
     * 本方法只在非动画态调用，此时推送周期本来就低。
     */
    private fun hashPixels(frame: RasterFrame): Long {
        val size = frame.byteCount
        var hash = FNV_OFFSET

        // 分块读入复用缓冲：一次整块读回比逐字节跨 JNA 调用快得多
        var offset = 0
        while (offset < size) {
            val chunk = minOf(HASH_CHUNK, size - offset)
            Pointer(frame.address + offset).read(0, hashBuffer, 0, chunk)
            for (i in 0 until chunk) {
                hash = (hash xor (hashBuffer[i].toInt().and(0xFF)).toLong()) * FNV_PRIME
            }
            offset += chunk
        }
        return hash
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
                    onContentSizeChanged = { size -> contentHeightDp = size.height },
                    modifier = Modifier,
                )
            }
        }

    /**
     * 判断当前是否需要按动画周期录制。
     *
     * 位图路径下所有动画都由本机渲染，接收端不会自行推进，
     * 因此这里必须覆盖全部会随时间变化的内容：
     * 超级岛的展开与自动收起、媒体卡的收起、滚动文本、频谱、时钟、播放进度。
     *
     * 只要叠加层上有可见内容就按动画周期录制：
     * 用「是否有内容」判定比逐项枚举可靠，新增动态元素也不会漏掉。
     * 无内容时降到空闲周期，静止场景不持续占用 CPU。
     */
    private fun hasAnimatedContent(): Boolean =
        DesktopOverlayStore.clockText.value.isNotEmpty() ||
            DesktopOverlayStore.mediaSessions.isNotEmpty() ||
            DesktopOverlayStore.superIslands.isNotEmpty() ||
            DesktopOverlayStore.notifications.isNotEmpty() ||
            DesktopOverlayStore.keyboardState.value.pressedKeys
                .isNotEmpty() ||
            DesktopOverlayStore.keyboardHint.value.isNotEmpty()

    private companion object {
        private const val TAG = "OverlaySceneBridge"

        /** 存在动画内容时的录制周期。 */
        private const val ACTIVE_FRAME_MS = 16L

        /** 空闲时的录制周期：仅用于发现状态变化。 */
        private const val IDLE_FRAME_MS = 120L

        private const val PHASE_UP = "up"
        private const val PHASE_DOWN = "down"

        /** 像素摘要的装载基数与乘子（FNV-1a 64 位）。 */
        private const val FNV_OFFSET = -3750763034362895579L
        private const val FNV_PRIME = 1099511628211L

        /** 摘要的分块长度。 */
        private const val HASH_CHUNK = 64 * 1024
    }
}
