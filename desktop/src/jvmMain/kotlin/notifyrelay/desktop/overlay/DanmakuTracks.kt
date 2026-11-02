package notifyrelay.desktop.overlay

import notifyrelay.desktop.platform.DesktopOverlaySettings
import kotlin.random.Random

/**
 * 弹幕轨道分配。
 *
 * 逐条对应 C# `OverlayRenderService.Danmaku.cs` 的 `TryAssignTrack`：
 * - 轨道高度 `max(FontSize + 24, FontSize × 1.5)`
 * - 轨道数 `(可用高度 - 顶部偏移) / 轨道高度`
 * - 可用轨道数 `总轨道数 × DisplayAreaPercent`
 * - 收集全部可分配轨道后**随机取一条**（与 C# 的 `_rand.Next(candidates.Count)` 一致）
 *
 * 长度单位与 C# 一致：全部按**物理像素**计（C# 侧窗口 DPI 固定 96，
 * 36px 字号在 1080 高的屏幕上得到 60px 轨道、约 18 条）。
 */
internal object DanmakuTracks {
    /** 字号（像素）：对应 C# `FontSize => 36 * FontSizePercent / 100`。 */
    val fontSizePx: Float get() = DesktopOverlaySettings.danmakuFontSize

    /** 速度（像素/秒）：对应 C# `PixelsPerSecond => max(1, Speed) * 60`。 */
    val pixelsPerSecond: Float get() = DesktopOverlaySettings.danmakuSpeed

    /** 显示区域百分比：对应 C# `DisplayAreaPercent`，默认 100。 */
    val displayAreaPercent: Float get() = DesktopOverlaySettings.danmakuAreaPercent

    /** 轨道高度（像素）。 */
    val trackHeightPx: Float get() = maxOf(fontSizePx + TRACK_PADDING, fontSizePx * 1.5f)

    /** 随机源：与 C# 的 `_rand` 同语义。 */
    private val random = Random.Default

    /** 轨道额外留白（像素）：对应 C# `+ 24`。 */
    private const val TRACK_PADDING = 24f

    /** 同轨道前一条弹幕右侧未进入该距离前不分配（像素）。 */
    private const val MIN_GAP_PX = 100f

    /** 完全离屏后的额外余量（像素）。 */
    private const val EXIT_MARGIN_PX = 50f

    /** 一条已分配轨道的弹幕。 */
    data class Assigned(
        val trackIndex: Int,
        val trackY: Float,
        val spawnX: Float,
    )

    /**
     * 为一条弹幕分配轨道。
     *
     * @param width 轨道区域宽度（像素）
     * @param topOffset 顶部卡片占用高度（像素），轨道从该高度之下开始
     * @param areaHeight 可滚动区域高度（像素）
     * @param now 当前时刻（毫秒）
     * @param occupied 同屏弹幕的轨道占用信息
     * @return 分配成功返回轨道位置；无可用轨道返回 null
     */
    fun assign(
        width: Float,
        topOffset: Float,
        areaHeight: Float,
        now: Long,
        occupied: List<Occupancy>,
    ): Assigned? {
        val trackHeight = trackHeightPx
        val available = maxOf(trackHeight, areaHeight - topOffset)
        val totalTracks = maxOf(1, (available / trackHeight).toInt())
        val activeTracks = (totalTracks * (displayAreaPercent / 100f)).toInt().coerceIn(1, totalTracks)

        // 收集全部可分配轨道，最后随机取一条（C# 同款：避免弹幕呈对角线分布）
        val candidates = mutableListOf<Int>()
        for (index in 0 until activeTracks) {
            val blocked =
                occupied.any { other ->
                    if (other.trackIndex != index) return@any false
                    // 与 C# 一致：位置 = 起点 - 已行驶距离，右边缘 = 位置 + 宽度
                    val rightEdge = other.spawnX - (now - other.spawnAt) / 1000f * other.speed + other.width
                    if (rightEdge > width - MIN_GAP_PX) return@any true
                    val speedNew = pixelsPerSecond
                    val speedOld = other.speed
                    if (speedNew > speedOld) {
                        val catchUpTime = (width - rightEdge) / (speedNew - speedOld)
                        val exitTime = rightEdge / speedOld
                        if (catchUpTime < exitTime) return@any true
                    }
                    false
                }
            if (!blocked) candidates.add(index)
        }
        if (candidates.isEmpty()) return null

        val track = candidates[random.nextInt(candidates.size)]
        return Assigned(
            trackIndex = track,
            trackY = topOffset + track * trackHeight,
            spawnX = width,
        )
    }

    /** 同屏弹幕的轨道占用信息（尺寸与速度均为像素）。 */
    data class Occupancy(
        val trackIndex: Int,
        val trackY: Float,
        val spawnX: Float,
        val spawnAt: Long,
        val width: Float,
        val speed: Float = pixelsPerSecond,
    )

    /**
     * 按 C# `SpawnPending` 的方式批量分配：只要还有待分配条目且轨道可分配就持续分配。
     *
     * @param width 轨道区域宽度（像素）
     * @param topOffset 顶部卡片占用高度（像素）
     * @param areaHeight 可滚动区域高度（像素）
     * @param now 当前时刻（毫秒）
     * @param pending 待分配条目（按入队顺序）
     * @param occupied 已占用轨道
     * @param widthOf 估算单条宽度（像素）
     * @return 本次成功分配的 (id → 占用信息)，按入队顺序
     */
    fun spawnPending(
        width: Float,
        topOffset: Float,
        areaHeight: Float,
        now: Long,
        pending: List<Long>,
        occupied: MutableList<Occupancy>,
        widthOf: (Long) -> Float,
    ): List<Pair<Long, Occupancy>> {
        val assigned = mutableListOf<Pair<Long, Occupancy>>()
        for (id in pending) {
            val track =
                assign(
                    width = width,
                    topOffset = topOffset,
                    areaHeight = areaHeight,
                    now = now,
                    occupied = occupied,
                ) ?: break
            val occupancy =
                Occupancy(
                    trackIndex = track.trackIndex,
                    trackY = track.trackY,
                    spawnX = track.spawnX,
                    spawnAt = now,
                    width = widthOf(id),
                )
            occupied.add(occupancy)
            assigned.add(id to occupancy)
        }
        return assigned
    }

    /** 弹幕是否已完全离屏。 */
    fun isExpired(
        spawnX: Float,
        spawnAt: Long,
        width: Float,
        now: Long,
    ): Boolean = spawnX - (now - spawnAt) / 1000f * pixelsPerSecond < -width - EXIT_MARGIN_PX
}
