package notifyrelay.desktop.overlay

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import notifyrelay.domain.feature.media.MediaSessionSnapshot
import notifyrelay.domain.feature.notification.superisland.replica.ReplicaExpiry
import notifyrelay.overlay.api.BalanceSnapshot
import notifyrelay.overlay.api.BatteryDevice
import notifyrelay.overlay.api.KeyboardStateSnapshot

/** 媒体业务标识。 */
internal const val BUSINESS_MEDIA = "media"

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

    /** 通知卡片，按到达顺序入队；收起后转图标态，超时移除。 */
    val notifications = mutableStateListOf<NotificationEntry>()

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

    /**
     * 超级岛更新。
     *
     * 内容未变时保留原有的 [SuperIslandEntry.updatedAt]：对端会周期性重发同一份
     * 报文，若每次都当作新内容就会不断推迟超时回收，条目永远不会被清理。
     * 只有标题、正文、paramV2 或图片真正变化才算一次更新。
     */
    fun putSuperIsland(entry: SuperIslandEntry) {
        val previous = superIslands[entry.sourceKey]
        superIslands[entry.sourceKey] =
            if (previous != null && previous.hasSameContent(entry)) {
                entry.copy(updatedAt = previous.updatedAt)
            } else {
                entry
            }
    }

    /** 超级岛移除。 */
    fun removeSuperIsland(sourceKey: String) {
        superIslands.remove(sourceKey)
    }

    /**
     * 清理超过展示时限的超级岛。
     *
     * 过期判据与时限都取自公共层 [ReplicaExpiry] / [ReplicaTiming]，
     * 与 Android 端同一口径。
     *
     * @return 被移除的条数
     */
    fun pruneSuperIslands(now: Long = System.currentTimeMillis()): Int {
        val expired =
            superIslands
                .filterValues { entry ->
                    ReplicaExpiry.isExpired(
                        updatedAtMs = entry.updatedAt,
                        isMedia = entry.business == BUSINESS_MEDIA,
                        nowMs = now,
                    )
                }.keys
                .toList()
        expired.forEach { superIslands.remove(it) }
        return expired.size
    }

    /**
     * 距最近一次条目过期还有多少毫秒；无条目时为 null。
     *
     * 供回收循环按时唤醒：固定周期轮询会让实际存活最长翻倍，
     * 因为条目到达时刻与轮询相位无关。
     */
    fun nextSuperIslandExpiryInMs(now: Long = System.currentTimeMillis()): Long? =
        superIslands.values.minOfOrNull { entry ->
            ReplicaExpiry.remainingMs(
                updatedAtMs = entry.updatedAt,
                isMedia = entry.business == BUSINESS_MEDIA,
                nowMs = now,
            )
        }

    /**
     * 是否存在正在播放的媒体。
     *
     * 频谱与收起态进度环按时间连续推进，场景录制据此判断是否需要每帧刷新。
     */
    fun hasPlayingMedia(): Boolean = mediaSessions.values.any { it.isPlaying }

    /**
     * 入队一条通知卡片。
     *
     * 同一 id 已在队列里时替换而非追加：对端可能重发同一条通知，
     * 追加会让同一通知占据多个卡片位并各自计时，收起的表现随之错乱。
     */
    fun addNotification(entry: NotificationEntry) {
        val index = notifications.indexOfFirst { it.id == entry.id }
        if (index >= 0) {
            notifications[index] = entry
        } else {
            notifications.add(entry)
        }
    }

    /** 移除一条通知卡片。 */
    fun removeNotification(entry: NotificationEntry) {
        notifications.remove(entry)
    }

    /** 清空全部通知卡片。 */
    fun clearNotifications() {
        notifications.clear()
    }

    // ======== 扩展点数据（余额 / 设备电池 / 键盘 / 时间）========

    /** 当前余额快照；未启动或未配置时为 [BalanceSnapshot] 的默认值。 */
    val balance = mutableStateOf(BalanceSnapshot())

    /** 电池设备快照；空集合表示无设备。 */
    val batteryDevices = mutableStateOf<List<BatteryDevice>>(emptyList())

    /** 当前按键状态。 */
    val keyboardState = mutableStateOf(KeyboardStateSnapshot.EMPTY)

    /** 最近一次映射提示文案；为空时不显示。 */
    val keyboardHint = mutableStateOf("")

    /** 最近一次映射提示的入队时刻（毫秒），用于超时清理。 */
    val keyboardHintAt = mutableStateOf(0L)

    /** 当前时间浮窗文案；未启动时为空串。 */
    val clockText = mutableStateOf("")

    /** 写入余额快照。 */
    fun putBalance(snapshot: BalanceSnapshot) {
        balance.value = snapshot
    }

    /** 写入电池设备快照。 */
    fun putBatteryDevices(devices: List<BatteryDevice>) {
        batteryDevices.value = devices
    }

    /** 写入按键状态。 */
    fun putKeyboardState(snapshot: KeyboardStateSnapshot) {
        keyboardState.value = snapshot
    }

    /** 写入映射提示并记录时刻。 */
    fun putKeyboardHint(text: String) {
        keyboardHint.value = text
        keyboardHintAt.value = System.currentTimeMillis()
    }

    /** 清空映射提示。 */
    fun clearKeyboardHint() {
        keyboardHint.value = ""
    }

    /** 写入时间浮窗文案。 */
    fun putClockText(text: String) {
        clockText.value = text
    }

    /** 清空全部扩展点数据（停止时调用）。 */
    fun clearFeatures() {
        balance.value = BalanceSnapshot()
        batteryDevices.value = emptyList()
        keyboardState.value = KeyboardStateSnapshot.EMPTY
        keyboardHint.value = ""
        clockText.value = ""
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
    /** 业务标识（如 media / miui_flashlight / battery）。 */
    val business: String? = null,
    /** 最近一次内容变化时刻（毫秒），用于超时回收。 */
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /**
     * 判断两份条目是否为同一份内容。
     *
     * [updatedAt] 不参与比较：它表达的是「内容何时变化」，不是内容本身。
     */
    fun hasSameContent(other: SuperIslandEntry): Boolean =
        title == other.title &&
            text == other.text &&
            paramV2Raw == other.paramV2Raw &&
            business == other.business &&
            pics == other.pics
}

/** 一条通知卡片数据。 */
internal data class NotificationEntry(
    val id: Long,
    val appName: String,
    val title: String,
    val text: String,
    val deviceName: String,
    /** 来源应用图标（PNG/JPEG 原始字节）；缺失时为 null，占位不绘制。 */
    val iconPng: ByteArray? = null,
    /** 入队时刻（毫秒）。 */
    val receivedAt: Long = System.currentTimeMillis(),
) {
    /** 卡片首行的来源文案。 */
    val sourceLabel: String get() = appName + " · " + deviceName
}
