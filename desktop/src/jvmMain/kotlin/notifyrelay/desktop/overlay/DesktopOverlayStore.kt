package notifyrelay.desktop.overlay

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import notifyrelay.domain.feature.media.MediaSessionSnapshot
import notifyrelay.overlay.api.BalanceSnapshot
import notifyrelay.overlay.api.KeyboardStateSnapshot
import notifyrelay.overlay.api.LogiBatteryDevice

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

    /** 超级岛更新。 */
    fun putSuperIsland(entry: SuperIslandEntry) {
        superIslands[entry.sourceKey] = entry
    }

    /** 超级岛移除。 */
    fun removeSuperIsland(sourceKey: String) {
        superIslands.remove(sourceKey)
    }

    /** 清理超过存活时长没有更新的超级岛；媒体类存活更长（与 Android 轮询周期一致）。 */
    fun pruneSuperIslands(
        nonMediaTimeoutMs: Long,
        mediaTimeoutMs: Long,
        now: Long = System.currentTimeMillis(),
    ) {
        val expired =
            superIslands
                .filterValues { entry ->
                    val timeout = if (entry.business == BUSINESS_MEDIA) mediaTimeoutMs else nonMediaTimeoutMs
                    now - entry.updatedAt > timeout
                }.keys
                .toList()
        expired.forEach { superIslands.remove(it) }
    }

    /** 入队一条通知卡片。 */
    fun addNotification(entry: NotificationEntry) {
        notifications.add(entry)
    }

    /** 移除一条通知卡片。 */
    fun removeNotification(entry: NotificationEntry) {
        notifications.remove(entry)
    }

    /** 清空全部通知卡片。 */
    fun clearNotifications() {
        notifications.clear()
    }

    // ======== 扩展点数据（余额 / 罗技电池 / 键盘 / 时间）========

    /** 当前余额快照；未启动或未配置时为 [BalanceSnapshot] 的默认值。 */
    val balance = mutableStateOf(BalanceSnapshot())

    /** 罗技电池设备快照；空集合表示无设备。 */
    val logiDevices = mutableStateOf<List<LogiBatteryDevice>>(emptyList())

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

    /** 写入罗技设备快照。 */
    fun putLogiDevices(devices: List<LogiBatteryDevice>) {
        logiDevices.value = devices
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
        logiDevices.value = emptyList()
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
    /** 业务标识（如 media / miui_flashlight / logi_battery）。 */
    val business: String? = null,
    /** 最近更新时间（毫秒），用于超时回收。 */
    val updatedAt: Long = System.currentTimeMillis(),
)

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
