package notifyrelay.shared.ui.port

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow

/**
 * 剪贴板同步端口。
 *
 * 发送端的去重、防循环、频率限制与大小阈值均由 Rust core 承担，
 * 平台侧只负责系统剪贴板读写与开关持久化。
 */
interface ClipboardSyncPort {
    /** 同步开关是否开启。 */
    val enabled: StateFlow<Boolean>

    /** 监视是否已启动（系统剪贴板不可用时为 false）。 */
    val watching: StateFlow<Boolean>

    /** 最近一次同步结果，用于状态展示。 */
    val lastResult: StateFlow<String?>

    /** 设置同步开关。 */
    fun setEnabled(enabled: Boolean)

    /** 立即把当前剪贴板内容同步一次（忽略「内容未改变」判定）。 */
    fun syncNow()
}

/** 由宿主提供实现。 */
val LocalClipboardSyncPort = staticCompositionLocalOf<ClipboardSyncPort?> { null }
