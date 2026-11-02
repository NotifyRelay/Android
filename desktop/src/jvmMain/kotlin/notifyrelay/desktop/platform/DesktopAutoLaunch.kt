package notifyrelay.desktop.platform

import dev.nucleusframework.autolaunch.AutoLaunch
import dev.nucleusframework.autolaunch.AutoLaunchResult
import notifyrelay.base.log.Logger

/**
 * 开机自启。
 *
 * 由 autolaunch 模块按实际打包格式解析后端（MSIX 启动任务 / Win32 注册表 / systemd 用户服务等）；
 * 用户已在系统设置中关闭时不再强制开启。
 */
internal object DesktopAutoLaunch {
    private const val TAG = "DesktopAutoLaunch"

    /** 预加载原生后端（后台线程调用，避免首次加载阻塞界面）。 */
    fun preload() {
        runCatching { AutoLaunch.preload() }
    }

    /** 当前是否已启用开机自启。 */
    fun isEnabled(): Boolean = runCatching { AutoLaunch.isEnabled() }.getOrDefault(false)

    /** 设置开机自启；返回设置后的实际状态。 */
    fun setEnabled(enabled: Boolean): Boolean {
        val result =
            runCatching {
                if (enabled) AutoLaunch.enable() else AutoLaunch.disable()
            }.getOrElse { AutoLaunchResult.ERROR }
        if (result != AutoLaunchResult.OK && result != AutoLaunchResult.UNCHANGED) {
            Logger.w(TAG, "设置开机自启未生效: " + result)
        }
        return isEnabled()
    }
}
