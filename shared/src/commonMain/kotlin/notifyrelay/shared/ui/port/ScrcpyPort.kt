package notifyrelay.shared.ui.port

import androidx.compose.runtime.staticCompositionLocalOf
import notifyrelay.data.config.ScrcpySettings

/**
 * scrcpy 平台能力端口。
 *
 * 路径选择、按设备配置读写与投屏进程启停均经本接口；实现位于平台侧
 * （桌面端调外部 scrcpy 可执行文件）。
 */
interface ScrcpyPort {
    /** 已配置的 scrcpy 可执行文件路径。 */
    fun scrcpyPath(): String

    /** 设置 scrcpy 路径；同目录存在 adb 时一并记录。 */
    fun setScrcpyPath(path: String)

    /** 已配置的 adb 路径。 */
    fun adbPath(): String

    /** 设置 adb 路径。 */
    fun setAdbPath(path: String)

    /** 弹出文件选择器选择 scrcpy 可执行文件；取消时返回 null。 */
    fun pickScrcpyExecutable(): String?

    /** 读取配置：投屏参数为全局，解锁配置按 [deviceUuid]（可为空）。 */
    fun loadSettings(deviceUuid: String?): ScrcpySettings

    /** 保存配置：投屏参数写全局，解锁配置写 [deviceUuid]（可为空）。 */
    fun saveSettings(
        deviceUuid: String?,
        settings: ScrcpySettings,
    )

    /** 启动指定设备的投屏；[startApp] 非空时启动后拉起该应用。 */
    fun launch(
        deviceUuid: String,
        serial: String,
        startApp: String = "",
    ): Boolean

    /** 停止指定设备的投屏。 */
    fun stop(serial: String)

    /** 指定设备是否正在投屏。 */
    fun isRunning(serial: String): Boolean
}

/** 由宿主提供实现。 */
val LocalScrcpyPort = staticCompositionLocalOf<ScrcpyPort?> { null }
