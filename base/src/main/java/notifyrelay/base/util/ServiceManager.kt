package notifyrelay.base.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 服务管理工具类
 *
 * 负责启动/检测与通知转发相关的后台服务，并在无法启动时返回明确的错误信息，
 * 便于上层进行提示或引导用户到系统设置放行。
 */
object ServiceManager {
    /**
     * 当自动启动或后台运行被系统限制时，给出的友好提示信息。
     */
    private const val AUTO_START_ERROR_MESSAGE = "服务无法启动，可能因系统自启动/后台运行权限被拒绝。请前往系统设置手动允许自启动、后台运行和电池优化白名单，否则通知转发将无法正常工作。"

    /**
     * 通知监听服务的全限定类名。
     *
     * 以字符串常量持有而非 `::class.java`：`:base` 位于依赖链底端，不能反向依赖 `:app`，
     * 故无法在此引用 `NotifyRelayNotificationListenerService` 类。
     * 该类在 `AndroidManifest.xml` 中注册为
     * `.feature.notification.service.NotifyRelayNotificationListenerService`，
     * 包名变更时此常量必须同步更新。
     */
    private const val NOTIFICATION_LISTENER_SERVICE_CLASS =
        "com.xzyht.notifyrelay.feature.notification.service.NotifyRelayNotificationListenerService"

    /**
     * 启动通知监听服务。
     *
     * @param context 应用或组件上下文，用于调用 startService。
     * @return 启动请求是否成功（不代表系统已实际在前台运行，仅表示调用未抛出异常）
     */
    fun startNotificationListenerService(context: Context): Boolean =
        try {
            val cn = ComponentName(context, NOTIFICATION_LISTENER_SERVICE_CLASS)
            val restartIntent = Intent()
            restartIntent.component = cn
            restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startService(restartIntent)
            true
        } catch (e: Exception) {
            Logger.e("ServiceManager", "启动通知监听服务失败", e)
            false
        }

    /**
     * 启动所有必要的后台服务（当前仅包括通知监听服务；剪贴板监控服务已移除，
     * 剪贴板同步由手动通知点击与 Fcitx5 广播两种入口触发）。
     *
     * @param context 用于启动服务的上下文。
     * @return Pair 第一个元素表示通知监听服务是否成功发起启动请求；
     *         第二个元素为可选的错误提示字符串，当存在启动失败且需要提示用户时返回该字符串，否则为 null。
     */
    fun startAllServices(context: Context): Pair<Boolean, String?> {
        var serviceStarted = false
        var errorMessage: String? = null

        // 启动通知监听服务
        try {
            val notificationStarted = startNotificationListenerService(context)
            if (!notificationStarted) {
                if (errorMessage == null) {
                    errorMessage = AUTO_START_ERROR_MESSAGE
                }
            } else {
                serviceStarted = true
            }
        } catch (e: Exception) {
            Logger.e("ServiceManager", "启动所有服务时发生异常", e)
            if (errorMessage == null) {
                errorMessage = AUTO_START_ERROR_MESSAGE
            }
        }

        return Pair(serviceStarted, errorMessage)
    }
}
