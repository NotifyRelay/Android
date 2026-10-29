package notifyrelay.desktop.platform

import notifyrelay.data.config.ScrcpySettings
import notifyrelay.data.config.ScrcpySettingsStore
import notifyrelay.shared.ui.port.ScrcpyPort
import javax.swing.SwingUtilities

/**
 * 桌面端 scrcpy 端口。
 *
 * 路径选择经 Swing 文件选择器（投递到事件派发线程同步等待结果），
 * 配置读写与进程启停委托 [ScrcpySettingsStore] 与 [DesktopScrcpyLauncher]。
 */
internal object DesktopScrcpyPort : ScrcpyPort {
    override fun scrcpyPath(): String = DesktopScrcpyLauncher.scrcpyPath()

    override fun setScrcpyPath(path: String) = DesktopScrcpyLauncher.setScrcpyPath(path)

    override fun adbPath(): String = DesktopScrcpyLauncher.adbPath()

    override fun setAdbPath(path: String) = DesktopScrcpyLauncher.setAdbPath(path)

    override fun pickScrcpyExecutable(): String? {
        var picked: String? = null
        val task =
            Runnable {
                // 现代 COM 文件对话框（IFileOpenDialog）：系统当前控件，且由系统托管窗口；
                // 传入已设置的路径作为初始定位，方便用户继续选择
                picked = ComFileOpenDialog.open("选择 scrcpy 可执行文件", initialPath = DesktopScrcpyLauncher.scrcpyPath())
            }
        if (SwingUtilities.isEventDispatchThread()) {
            task.run()
        } else {
            SwingUtilities.invokeAndWait(task)
        }
        picked?.let {
            DesktopScrcpyLauncher.setScrcpyPath(it)
            DesktopToastHost.show("已设置 scrcpy 路径", DesktopToastHost.Duration.SHORT)
        }
        return picked
    }

    override fun loadSettings(deviceUuid: String?): ScrcpySettings = ScrcpySettingsStore.load(deviceUuid)

    override fun saveSettings(
        deviceUuid: String?,
        settings: ScrcpySettings,
    ) = ScrcpySettingsStore.save(deviceUuid, settings)

    override fun launch(
        deviceUuid: String,
        serial: String,
        startApp: String,
    ): Boolean = DesktopScrcpyLauncher.launch(deviceUuid, serial, startApp)

    override fun stop(serial: String) = DesktopScrcpyLauncher.stop(serial)

    override fun isRunning(serial: String): Boolean = DesktopScrcpyLauncher.isRunning(serial)
}
