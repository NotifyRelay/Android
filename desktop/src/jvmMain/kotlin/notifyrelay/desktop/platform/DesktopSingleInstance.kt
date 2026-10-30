package notifyrelay.desktop.platform

import dev.nucleusframework.core.runtime.SingleInstanceManager
import notifyrelay.base.log.Logger

/**
 * 桌面端单实例守卫。
 *
 * 使用 Nucleus core-runtime 的 [SingleInstanceManager]：首个实例持有锁文件；
 * 后续实例写入恢复请求文件后退出，主实例收到请求后显示窗口。
 */
object DesktopSingleInstance {
    private const val TAG = "DesktopSingle"

    /** 锁标识：与打包后的应用目录区分开，避免与其它程序共用锁 */
    private const val LOCK_IDENTIFIER = "notifyrelay.desktop"

    /** 请求已有实例显示窗口；由宿主在窗口创建后设置。 */
    @Volatile
    var onActivate: (() -> Unit)? = null

    /**
     * 取得单实例所有权。
     *
     * @return true 表示本进程是首个实例；false 表示已有实例在运行且已被请求显示窗口
     */
    fun acquire(): Boolean {
        SingleInstanceManager.configuration =
            SingleInstanceManager.Configuration(lockIdentifier = LOCK_IDENTIFIER)
        return SingleInstanceManager.isSingleInstance(
            onRestoreRequest = {
                Logger.i(TAG, "收到二次启动请求")
                onActivate?.invoke()
            },
        )
    }
}
