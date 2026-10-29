package notifyrelay.desktop.overlay

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import notifyrelay.domain.feature.media.MediaSessionSnapshot

/**
 * 叠加层显示状态。
 *
 * 数据层写入，窗口读取并渲染；双方不直接持有对方实例。
 */
internal object DesktopOverlayStore {
    /** 各设备的媒体会话，键为设备 uuid；本机用 [LOCAL_KEY]。 */
    val mediaSessions = mutableStateMapOf<String, MediaSessionSnapshot>()

    /** 超级岛条目，键为 sourceKey。 */
    val superIslands = mutableStateMapOf<String, SuperIslandEntry>()

    /** 待显示弹幕（按入队顺序）。 */
    val danmaku = mutableStateListOf<DanmakuEntry>()

    /** 本机会话的键。 */
    const val LOCAL_KEY = "local"

    /** 媒体会话更新。 */
    fun putMedia(
        deviceUuid: String,
        snapshot: MediaSessionSnapshot,
    ) {
        mediaSessions[deviceUuid] = snapshot
    }

    /** 媒体会话移除。 */
    fun removeMedia(deviceUuid: String) {
        mediaSessions.remove(deviceUuid)
    }

    /** 超级岛更新。 */
    fun putSuperIsland(entry: SuperIslandEntry) {
        superIslands[entry.sourceKey] = entry
    }

    /** 超级岛移除。 */
    fun removeSuperIsland(sourceKey: String) {
        superIslands.remove(sourceKey)
    }

    /** 入队一条弹幕。 */
    fun addDanmaku(entry: DanmakuEntry) {
        danmaku.add(entry)
    }

    /** 移除已结束的弹幕。 */
    fun removeDanmaku(entry: DanmakuEntry) {
        danmaku.remove(entry)
    }
}

/** 一条超级岛显示数据。 */
internal data class SuperIslandEntry(
    val sourceKey: String,
    val deviceName: String,
    val title: String?,
    val text: String?,
    val paramV2Raw: String?,
    val pics: Map<String, String>?,
)

/** 一条弹幕显示数据。 */
internal data class DanmakuEntry(
    val id: Long,
    val appName: String,
    val title: String,
    val text: String,
    val deviceName: String,
    /** 入队时刻（毫秒）。 */
    val spawnAt: Long = System.currentTimeMillis(),
)
